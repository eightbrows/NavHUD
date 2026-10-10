package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestSettings
import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class NavEngineTest {

    private val jst = ZoneId.of("Asia/Tokyo")

    /** 2026-08-14 10:00:00 JST */
    private val t0 = Instant.parse("2026-08-14T01:00:00Z").toEpochMilli()

    private fun engine(s: NavSettings = TestSettings.BEFORE_D03) = NavEngine(s, jst, SourceKind.REPLAY)

    private fun fix(t: Long, lat: Double = 33.0, alt: Double? = 1000.0, speed: Float? = 10f, bearing: Float? = 0f) =
        Fix(timeMs = t, lat = lat, lon = 133.0, altRawM = alt, speedMps = speed, bearingDeg = bearing, horizAccM = 5f)

    @Test
    fun initialStateIsNoFixWithoutTime() {
        val s = engine().state
        assertNull(s.nowMs)
        assertTrue(s.noFix)
        assertEquals(Heading.NONE, s.heading)
    }

    @Test
    fun defaultSettings() {
        val s = NavSettings()
        assertEquals(SourceMode.GPS, s.sourceMode)
        assertEquals(2.0f, s.holdEnterSpeedMps)
        assertEquals(3.0f, s.holdExitSpeedMps)
        assertEquals(15f, s.maxGpsAccM)
        assertEquals(20f, s.maxGpsBearingAccDeg)
        assertEquals(10, s.noFixTimeoutSec)
        assertEquals(36.0, s.altOffsetM, 0.0)
        // D03 から: 今使っている端末の設定値を初期値にした（§6.9）
        assertEquals(RangeAuto.ALL_STEPS_KM, s.rangeStepsKm)
        assertEquals(5, s.wpButtonsMax)
        assertEquals(10, s.hudWpCount)
        assertEquals(ProfileSize.OFF, s.profileSize)
        assertEquals(30, s.panReturnSec)
        assertEquals(0.5, s.initialRangeKm, 0.0)
        assertEquals(0.5, s.autoMaxRangeKm, 0.0)
        assertEquals(10, s.rateWindowSec)
        assertEquals(125, s.ringLabelScalePct)
        assertEquals(75, s.trackBrightnessPct)
        assertTrue(s.autoOpenLastList)
        assertTrue(s.autoRange)
        assertTrue(s.rangeStepsKm.all { it in RangeAuto.ALL_STEPS_KM })
        assertTrue(s.reachRadiusM in NavSettings.REACH_RADIUS_CHOICES_M)
        assertTrue(s.rateWindowSec in NavSettings.RATE_WINDOW_CHOICES_SEC)
    }

    @Test
    fun fixValuesAndAltitudeOffset() {
        val s = engine().onFix(fix(t0, alt = 1000.0, speed = 12.5f), t0)
        assertEquals(t0, s.nowMs)
        assertFalse(s.noFix)
        assertEquals(964.0, s.altM!!, 1e-9)
        assertEquals(12.5f, s.groundSpeedMps)
        assertEquals(Heading(0f, HeadingSrc.GPS), s.heading)
    }

    @Test
    fun tickAloneUpdatesNoFix() {
        val e = engine()
        e.onFix(fix(t0), t0)
        assertFalse(e.onTick(t0 + 10_000).noFix)
        assertTrue(e.onTick(t0 + 10_001).noFix)
        // 最後の値は残る（画面はグレーで出し続ける）
        assertNotNull(e.state.fix)
        assertNotNull(e.state.altM)
    }

    @Test
    fun noFixTimeoutFollowsSettings() {
        val e = engine(NavSettings(noFixTimeoutSec = 30))
        e.onFix(fix(t0), t0)
        assertFalse(e.onTick(t0 + 30_000).noFix)
        assertTrue(e.onTick(t0 + 30_001).noFix)
    }

    @Test
    fun liveNoFixUsesReceiveTimeWhenClockIsOff() {
        // 端末の時計が GPS の時刻より 60 秒 遅れている / 進んでいる。LIVE の NO FIX は受け取った時刻で決める
        for (skew in listOf(-60_000L, 60_000L)) {
            val e = NavEngine(NavSettings(), jst, SourceKind.LIVE)
            // GPS の時刻で 1 秒ごと（北へ 10 m/s）。受け取った時刻 = GPS の時刻 + skew
            var s = e.state
            for (i in 0..60) s = e.onFix(fix(t0 + i * 1_000L, lat = TestGeo.lat(10.0 * i, 33.0)), t0 + i * 1_000L + skew)
            assertFalse("skew $skew", s.noFix)
            // RATE は Fix の時刻（GPS の時刻）で計算したまま
            assertNotNull("skew $skew", s.rate)
            val last = t0 + 60_000L + skew
            assertFalse("skew $skew", e.onTick(last + 10_000).noFix)
            assertTrue("skew $skew", e.onTick(last + 10_001).noFix)
            // 次の Fix を受け取れば、すぐ NO FIX ではなくなる
            assertFalse("skew $skew", e.onFix(fix(t0 + 75_000, lat = TestGeo.lat(750.0, 33.0)), last + 15_000).noFix)
        }
    }

    @Test
    fun replayNoFixUsesTrackTime() {
        // REPLAY は今のまま: 「今（トラックの時計）− 最後の Fix の時刻」。受け取った時刻は使わない
        val e = engine()
        e.onFix(fix(t0), t0 + 5_000)
        assertFalse(e.onTick(t0 + 10_000).noFix)
        assertTrue(e.onTick(t0 + 10_001).noFix)
    }

    @Test
    fun tickAloneUpdatesCountdown() {
        val e = engine()
        e.setWaypoints(listOf(Waypoint("A", 33.1, 133.0, targetTime = LocalTime.of(10, 10), deadlineTime = LocalTime.of(10, 5))))
        assertEquals(600L, e.onTick(t0).targetCountdownSec)
        val later = e.onTick(t0 + 360_000)
        assertEquals(240L, later.targetCountdownSec)
        assertEquals(-60L, later.deadlineCountdownSec)
    }

    @Test
    fun nextWaypointBearingDistanceAndEta() {
        val e = engine()
        // 北へ 10m/s で 61 秒
        for (s in 0..60) e.onFix(fix(t0 + s * 1000L, lat = TestGeo.lat(10.0 * s, 33.0)), t0 + s * 1000L)
        val lastLat = TestGeo.lat(600.0, 33.0)
        e.setWaypoints(listOf(Waypoint("N", TestGeo.lat(1000.0, lastLat), 133.0)))
        val s = e.state
        assertEquals(0, s.nextWpIndex)
        assertEquals(0.0, s.nextWpBearingDeg!!, 1e-6)
        assertEquals(1000.0, s.nextWpDistanceM!!, 0.5)
        assertEquals(10.0, s.rate!!.avgSpeedMps, 1e-9)
        assertEquals((t0 + 60_000 + 100_000).toDouble(), s.etaMs!!.toDouble(), 100.0)
    }

    @Test
    fun etaFromShortHistory() {
        val e = engine()
        e.setWaypoints(listOf(Waypoint("N", TestGeo.lat(2000.0, 33.0), 133.0)))
        // 走り始めて 9 秒: RATE（60 秒）はまだなく、ETA も出さない
        for (s in 0..9) e.onFix(fix(t0 + s * 1000L, lat = TestGeo.lat(10.0 * s, 33.0)), t0 + s * 1000L)
        assertNull(e.state.rate)
        assertNull(e.state.etaMs)
        // 10 秒分たまれば、その平均速度（10 m/s）で ETA。残り 1900m → 190 秒後
        val s = e.onFix(fix(t0 + 10_000, lat = TestGeo.lat(100.0, 33.0)), t0 + 10_000)
        assertNull(s.rate)
        assertEquals((t0 + 10_000 + 190_000).toDouble(), s.etaMs!!.toDouble(), 100.0)
    }

    @Test
    fun autoReachAdvancesNextWaypoint() {
        val e = engine(NavSettings(reachRadiusM = 50.0))
        e.setWaypoints(listOf(Waypoint("A", 33.0, 133.0), Waypoint("B", 33.1, 133.0)))
        assertEquals(0, e.state.nextWpIndex)
        val s = e.onFix(fix(t0, lat = TestGeo.lat(30.0, 33.0)), t0)
        assertTrue(s.waypoints[0].reached)
        assertEquals(1, s.nextWpIndex)
    }

    @Test
    fun manualToggle() {
        val e = engine()
        e.setWaypoints(listOf(Waypoint("A", 33.0, 133.0), Waypoint("B", 33.1, 133.0, enabled = false)))
        // A を到達済みにすると、B は無効なので次の目標はない
        assertNull(e.toggleReached(0).nextWpIndex)
        assertTrue(e.state.waypoints[0].reached)
        assertFalse(e.toggleReached(1).waypoints[1].reached)
        assertFalse(e.toggleReached(0).waypoints[0].reached)
    }

    @Test
    fun hybridUsesCompassWhenNoFixOrStopped() {
        val e = engine(NavSettings(sourceMode = SourceMode.HYBRID))
        e.onCompass(45f, t0)
        assertEquals(Heading(90f, HeadingSrc.GPS), e.onFix(fix(t0, bearing = 90f), t0).heading)
        // 欠損中は古い Fix の方位を使わない
        assertEquals(Heading(45f, HeadingSrc.COMPASS), e.onTick(t0 + 20_000).heading)
        // 停止（方位なし）
        assertEquals(Heading(45f, HeadingSrc.COMPASS), e.onFix(fix(t0 + 21_000, speed = 0f, bearing = null), t0 + 21_000).heading)
    }

    @Test
    fun sourceModeChange() {
        val e = engine()
        e.onCompass(45f, t0)
        e.onFix(fix(t0, bearing = 90f), t0)
        val s = e.setSourceMode(SourceMode.COMPASS)
        assertEquals(SourceMode.COMPASS, s.sourceMode)
        assertEquals(Heading(45f, HeadingSrc.COMPASS), s.heading)
        assertEquals(Heading(90f, HeadingSrc.GPS), e.setSourceMode(SourceMode.GPS).heading)
    }

    @Test
    fun timeGoingBackwardsClearsHistory() {
        val e = engine()
        for (s in 0..60) e.onFix(fix(t0 + s * 1000L), t0 + s * 1000L)
        assertNotNull(e.state.rate)
        val s = e.onFix(fix(t0 - 100_000), t0 - 100_000)
        assertNull(s.rate)
        assertEquals(t0 - 100_000, s.fix!!.timeMs)
    }

    @Test
    fun resetPositionKeepsWaypointsAndSettings() {
        val e = engine(NavSettings(sourceMode = SourceMode.GPS))
        e.setWaypoints(listOf(Waypoint("A", 33.1, 133.0)))
        e.onFix(fix(t0), t0)
        val s = e.resetPosition()
        assertNull(s.fix)
        assertNull(s.nowMs)
        assertTrue(s.noFix)
        assertEquals(1, s.waypoints.size)
        assertEquals(SourceMode.GPS, s.sourceMode)
    }

    @Test
    fun switchSourceResetsHistoryButKeepsReached() {
        val e = engine(NavSettings(reachRadiusM = 50.0))
        e.setWaypoints(listOf(Waypoint("A", 33.0, 133.0), Waypoint("B", 33.1, 133.0)))
        for (s in 0..60) e.onFix(fix(t0 + s * 1000L, lat = TestGeo.lat(10.0 * s, 33.0)), t0 + s * 1000L)
        assertTrue(e.state.waypoints[0].reached)
        assertNotNull(e.state.rate)

        val live = e.switchSource(SourceKind.LIVE, playing = true)
        assertEquals(SourceKind.LIVE, live.sourceKind)
        assertTrue(live.playing)
        // RATE の履歴・位置・時刻はリセット、WP の到達状態は残る
        assertNull(live.rate)
        assertNull(live.fix)
        assertNull(live.nowMs)
        assertTrue(live.noFix)
        assertTrue(live.waypoints[0].reached)
        assertEquals(1, live.nextWpIndex)
    }

    @Test
    fun compassQualityIsExposedOnlyWithACompassValue() {
        val e = engine(NavSettings(sourceMode = SourceMode.HYBRID))
        assertNull(e.state.compass)
        val q = io.github.eightbrows.navhud.core.sensor.CompassQuality(lowAccuracy = true, declinationUnknown = true)
        val s = e.onCompass(45f, t0, q)
        assertEquals(q, s.compass)
        assertEquals(Heading(45f, HeadingSrc.COMPASS), s.heading)
        // コンパスがなくなれば null
        assertNull(e.onCompass(null, null).compass)
        // 時刻を渡さなければ時刻は進めない
        assertEquals(t0, e.state.nowMs)
    }

    @Test
    fun rangeFollowsNextWaypointAndManualZoomTurnsAutoOff() {
        val e = engine()
        assertEquals(1_000.0, e.state.rangeM, 0.0)
        assertTrue(e.state.rangeAuto)
        // 次の WP が 3.9km 先 → AUTO の上限（1km の段）で止まる（画面が分からないので距離で判定）
        e.setWaypoints(listOf(Waypoint("A", TestGeo.lat(3_900.0, 33.0), 133.0)))
        assertEquals(1_000.0, e.onFix(fix(t0), t0).rangeM, 0.0)
        // ＋ で 500m の段、AUTO は OFF
        val z = e.zoomIn()
        assertEquals(500.0, z.rangeM, 0.0)
        assertFalse(z.rangeAuto)
        // AUTO を戻すと、すぐ1段広げて 1km の段
        assertEquals(1_000.0, e.toggleAutoRange().rangeM, 0.0)
        // 上限を 5km の段にすると、1段ずつ広げる（2km → 5km）
        assertEquals(2_000.0, e.updateSettings(e.settings.copy(autoMaxRangeKm = 5.0)).rangeM, 0.0)
        assertEquals(5_000.0, e.onTick(t0 + 1_000).rangeM, 0.0)
    }

    @Test
    fun pinchStepsActLikeThePlusAndMinusButtons() {
        // ピンチ（§6.12）: 段の数だけ ＋ / − と同じ。AUTO は OFF、使う段の最小・最大（100m・20km の段）より先には行かない
        val e = engine()
        assertEquals(1_000.0, e.state.rangeM, 0.0)
        assertTrue(e.state.rangeAuto)
        val inTwo = e.zoomBy(2)
        assertEquals(200.0, inTwo.rangeM, 0.0)
        assertFalse(inTwo.rangeAuto)
        assertEquals(100.0, e.zoomBy(5).rangeM, 0.0)
        assertEquals(200.0, e.zoomBy(-1).rangeM, 0.0)
        assertEquals(20_000.0, e.zoomBy(-10).rangeM, 0.0)
        assertEquals(20_000.0, e.zoomBy(0).rangeM, 0.0)
    }

    @Test
    fun heldGpsHeadingAfterStopping() {
        val e = engine()
        e.onFix(fix(t0, speed = 10f, bearing = 90f), t0)
        val s = e.onFix(fix(t0 + 1_000, speed = 0f, bearing = null), t0 + 1_000)
        assertEquals(Heading(90f, HeadingSrc.GPS, held = true), s.heading)
    }

    @Test
    fun sourceKindAndPlaying() {
        val e = engine()
        assertEquals(SourceKind.REPLAY, e.state.sourceKind)
        assertFalse(e.state.playing)
        assertTrue(e.setPlaying(true).playing)
        val live = e.setSource(SourceKind.LIVE, playing = true)
        assertEquals(SourceKind.LIVE, live.sourceKind)
    }
}
