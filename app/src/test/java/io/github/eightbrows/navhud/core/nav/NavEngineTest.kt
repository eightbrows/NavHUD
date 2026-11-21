package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestSettings
import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
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
        // 方位の出し方は GPS（20261121-D01。前の「方位の求め方」の初期値も GPS）、方位精度の上限 20°、GS は今の点の速さ、ETA は 10 秒の平均
        assertEquals(HeadingMethod.GPS, s.headingMethod)
        assertEquals(20f, s.maxGpsBearingAccDeg)
        assertEquals(1, s.gsAverageSec)
        assertEquals(10, s.etaAverageSec)
        // 保持に入る / 解く速度は 4 / 6 km/h（20261119-D01 から。D04〜は 7 / 11 km/h、前は 2.0 / 3.0 m/s）
        assertEquals(4 / 3.6f, s.holdEnterSpeedMps, 1e-6f)
        assertEquals(6 / 3.6f, s.holdExitSpeedMps, 1e-6f)
        // 真横通過の判定を始める速さは 11 km/h（20261119-D01。前は保持を解く速度を使っていた）
        assertEquals(11, s.sidePassMinKmh)
        assertEquals(15f, s.maxGpsAccM)
        assertEquals(20f, s.maxGpsBearingAccDeg)
        assertEquals(10, s.noFixTimeoutSec)
        assertEquals(36.0, s.altOffsetM, 0.0)
        // D03 から: 今使っている端末の設定値を初期値にした（§6.9）
        // 20261025-D01 から: 実機の設定を初期値にした。使う段は全部の 12 段（前は R1 25m〜25km の 10 段）（表示モードは 20261025-D03 で ARC に戻した）、
        // ARC の自機の位置は下から4段目、線の太さ 2・線の明るさ 3・ランドマークの明るさ 3、軌跡の明るさ 100%
        assertEquals(RangeAuto.ALL_STEPS_KM, s.rangeStepsKm)
        assertEquals(DisplayMode.ARC, s.displayMode)
        assertEquals(OwnshipPosition.NEAR_CENTER, s.ownshipPosition)
        assertEquals(2, s.mapLineWidth)
        assertEquals(3, s.mapLineBrightness)
        assertEquals(3, s.landmarkBrightness)
        assertEquals(5, s.wpButtonsMax)
        assertEquals(10, s.hudWpCount)
        assertEquals(ProfileSize.OFF, s.profileSize)
        assertEquals(30, s.panReturnSec)
        assertEquals(0.5, s.initialRangeKm, 0.0)
        assertEquals(0.5, s.autoMaxRangeKm, 0.0)
        assertEquals(125, s.ringLabelScalePct)
        assertEquals(100, s.trackBrightnessPct)
        assertTrue(s.autoOpenLastList)
        assertTrue(s.autoRange)
        assertTrue(s.rangeStepsKm.all { it in RangeAuto.ALL_STEPS_KM })
        assertTrue(s.reachRadiusM in NavSettings.REACH_RADIUS_CHOICES_M)
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
            // V/S は Fix の時刻（GPS の時刻）で計算したまま（60 秒分そろっている）
            assertNotNull("skew $skew", s.verticalSpeedMpm)
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
        assertEquals((t0 + 60_000 + 100_000).toDouble(), s.etaMs!!.toDouble(), 100.0)
    }

    @Test
    fun etaFromShortHistory() {
        val e = engine()
        e.setWaypoints(listOf(Waypoint("N", TestGeo.lat(2000.0, 33.0), 133.0)))
        // 走り始めて 9 秒: ETA の平均速度（10 秒）はまだなく、ETA も出さない（V/S は 20261108-D01 から 8 秒で出る。高さが一定なので 0）
        for (s in 0..9) e.onFix(fix(t0 + s * 1000L, lat = TestGeo.lat(10.0 * s, 33.0)), t0 + s * 1000L)
        assertEquals(0.0, e.state.verticalSpeedMpm!!, 1e-9)
        assertNull(e.state.etaMs)
        // 10 秒分たまれば、その平均速度（10 m/s）で ETA。残り 1900m → 190 秒後
        val s = e.onFix(fix(t0 + 10_000, lat = TestGeo.lat(100.0, 33.0)), t0 + 10_000)
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
    fun timeGoingBackwardsClearsHistory() {
        val e = engine()
        for (s in 0..60) e.onFix(fix(t0 + s * 1000L), t0 + s * 1000L)
        assertNotNull(e.state.verticalSpeedMpm)
        val s = e.onFix(fix(t0 - 100_000), t0 - 100_000)
        assertNull(s.verticalSpeedMpm)
        assertEquals(t0 - 100_000, s.fix!!.timeMs)
    }

    @Test
    fun resetPositionKeepsWaypointsAndSettings() {
        val e = engine(NavSettings(gsAverageSec = 5))
        e.setWaypoints(listOf(Waypoint("A", 33.1, 133.0)))
        e.onFix(fix(t0), t0)
        val s = e.resetPosition()
        assertNull(s.fix)
        assertNull(s.nowMs)
        assertTrue(s.noFix)
        assertEquals(1, s.waypoints.size)
        assertEquals(5, s.settings.gsAverageSec)
    }

    @Test
    fun switchSourceResetsHistoryButKeepsReached() {
        val e = engine(NavSettings(reachRadiusM = 50.0))
        e.setWaypoints(listOf(Waypoint("A", 33.0, 133.0), Waypoint("B", 33.1, 133.0)))
        for (s in 0..60) e.onFix(fix(t0 + s * 1000L, lat = TestGeo.lat(10.0 * s, 33.0)), t0 + s * 1000L)
        assertTrue(e.state.waypoints[0].reached)
        assertNotNull(e.state.verticalSpeedMpm)

        val live = e.switchSource(SourceKind.LIVE, playing = true)
        assertEquals(SourceKind.LIVE, live.sourceKind)
        assertTrue(live.playing)
        // V/S の履歴・位置・時刻はリセット、WP の到達状態は残る
        assertNull(live.verticalSpeedMpm)
        assertNull(live.fix)
        assertNull(live.nowMs)
        assertTrue(live.noFix)
        assertTrue(live.waypoints[0].reached)
        assertEquals(1, live.nextWpIndex)
    }

    @Test
    fun rangeFollowsNextWaypointAndManualZoomTurnsAutoOff() {
        val e = engine()
        assertEquals(1_000.0, e.state.rangeM, 0.0)
        assertTrue(e.state.rangeAuto)
        // 次の WP が 3.9km 先 → AUTO の広域の限度（1km の段）で止まる（画面が分からないので距離で判定）
        e.setWaypoints(listOf(Waypoint("A", TestGeo.lat(3_900.0, 33.0), 133.0)))
        assertEquals(1_000.0, e.onFix(fix(t0), t0).rangeM, 0.0)
        // ＋ で 500m の段、AUTO は OFF
        val z = e.zoomIn()
        assertEquals(500.0, z.rangeM, 0.0)
        assertFalse(z.rangeAuto)
        // AUTO を戻すと、すぐ1段広域にして 1km の段
        assertEquals(1_000.0, e.toggleAutoRange().rangeM, 0.0)
        // 広域の限度を 5km の段にすると、1段ずつ広域にする（2km → 5km）
        assertEquals(2_000.0, e.updateSettings(e.settings.copy(autoMaxRangeKm = 5.0)).rangeM, 0.0)
        assertEquals(5_000.0, e.onTick(t0 + 1_000).rangeM, 0.0)
    }

    @Test
    fun pinchStepsActLikeThePlusAndMinusButtons() {
        // ピンチ（§6.12）: 段の数だけ動く。AUTO は OFF（＋ / − と同じ）。20261024-D04 から、使う段（100 m〜20 km の段）に入れていない段も含めた
        // 全段（50 m〜200 km の段 = R1 25 m〜100 km）を 1 段ずつ動き、全段の端で止まる
        val e = engine()
        assertEquals(1_000.0, e.state.rangeM, 0.0)
        assertTrue(e.state.rangeAuto)
        val inTwo = e.zoomBy(2)
        assertEquals(200.0, inTwo.rangeM, 0.0)
        assertFalse(inTwo.rangeAuto)
        assertEquals(50.0, e.zoomBy(5).rangeM, 0.0)
        assertEquals(100.0, e.zoomBy(-1).rangeM, 0.0)
        assertEquals(200_000.0, e.zoomBy(-20).rangeM, 0.0)
        assertEquals(200_000.0, e.zoomBy(0).rangeM, 0.0)
        // 使う段にない段（200 km）から ＋: 詳細の向きでいちばん近い使う段（20 km）
        assertEquals(20_000.0, e.zoomIn().rangeM, 0.0)
        // 使う段にない段（50 m）から −: 広域の向きでいちばん近い使う段（100 m）。50 m から ＋ は、それより詳細な使う段がないので動かない
        e.zoomBy(10)
        assertEquals(50.0, e.state.rangeM, 0.0)
        assertEquals(50.0, e.zoomIn().rangeM, 0.0)
        assertEquals(100.0, e.zoomOut().rangeM, 0.0)
        // ほかの設定を変えても、ピンチで選んだ使う段にない段のまま（段が変わったときだけ近い段に合わせる）
        e.zoomBy(-20)
        assertEquals(200_000.0, e.updateSettings(e.settings.copy(panReturnSec = 20)).rangeM, 0.0)
        assertEquals(20_000.0, e.updateSettings(e.settings.copy(rangeStepsKm = listOf(0.1, 1.0, 20.0))).rangeM, 0.0)
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

    /** 20261119-D03: 方位を止める / 戻す速度は到達判定のプリセットの値。プリセットを切り替えると、次の Fix からその値で保持する */
    @Test
    fun holdSpeedsFollowThePreset() {
        // 8 km/h で北へ走り続ける
        val v = 8f / 3.6f
        val custom = NavSettings().selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(holdEnterKmh = 9, holdExitKmh = 20) }
        val car = custom.selectTravelMode(TravelMode.CAR)
        val e = NavEngine(car, jst, SourceKind.REPLAY)
        var t = t0
        fun step(): Heading {
            t += 1_000
            return e.onFix(fix(t, lat = 33.0 + (t - t0) / 1_000.0 * v / 111_195.0, speed = v, bearing = 0f).copy(bearingAccDeg = 2f), t).heading
        }
        // 自動車（4 / 6 km/h）: 8 km/h は GPS の向きをそのまま使う
        repeat(3) { step() }
        assertEquals(HeadingSrc.GPS, step().src)
        assertFalse(step().held)
        // カスタム1（9 / 20 km/h）に切り替えると、次の Fix から保持（8 km/h は 9 km/h 未満）
        e.updateSettings(custom.selectTravelMode(TravelMode.CUSTOM1))
        assertTrue(step().held)
        // 自動車に戻すと、次の Fix から GPS の向き（8 km/h は 6 km/h より速い）
        e.updateSettings(car)
        assertFalse(step().held)
    }

    /** 20261121-D01: 画面の GS は移動のプリセットの「GS の平均」秒の平均（1 秒は今の点の速さ）。到達などほかの計算には使わない */
    @Test
    fun groundSpeedAverage() {
        val speeds = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val one = engine(NavSettings())
        val five = engine(NavSettings(gsAverageSec = 5))
        var a: NavState? = null
        var b: NavState? = null
        speeds.forEachIndexed { i, v ->
            val f = fix(t0 + i * 1000L, speed = v)
            a = one.onFix(f, f.timeMs)
            b = five.onFix(f, f.timeMs)
        }
        assertEquals(7f, a!!.groundSpeedMps!!, 1e-6f)
        // 直前 5 秒（3〜7）の平均
        assertEquals(5f, b!!.groundSpeedMps!!, 1e-6f)
        // 速さのない点は数えない。全部ないなら null
        val e = engine(NavSettings(gsAverageSec = 3))
        e.onFix(fix(t0, speed = 2f), t0)
        assertEquals(2f, e.onFix(fix(t0 + 1000, speed = null), t0 + 1000).groundSpeedMps!!, 1e-6f)
        val n = engine(NavSettings(gsAverageSec = 3))
        assertNull(n.onFix(fix(t0, speed = null), t0).groundSpeedMps)
        // 止まったら、平均の秒数のあいだは 0 にならない（遅れはそのまま）
        val s = engine(NavSettings(gsAverageSec = 5))
        for (i in 0..9) s.onFix(fix(t0 + i * 1000L, speed = 5f), t0 + i * 1000L)
        assertEquals(4f, s.onFix(fix(t0 + 10_000, speed = 0f), t0 + 10_000).groundSpeedMps!!, 1e-6f)
        for (i in 11..14) s.onFix(fix(t0 + i * 1000L, speed = 0f), t0 + i * 1000L)
        assertEquals(0f, s.state.groundSpeedMps!!, 1e-6f)
        // 巻き戻し・切り替えで履歴を捨てる
        assertEquals(9f, s.onFix(fix(t0 - 100_000, speed = 9f), t0 - 100_000).groundSpeedMps!!, 1e-6f)
    }

    /** 20261121-D01: ETA の平均速度の秒数は移動のプリセットの値（自動車 10 秒・歩行 60 秒）。走り始めは、ある分（最低 10 秒） */
    @Test
    fun etaAverageSeconds() {
        fun run(s: NavSettings): List<Long?> {
            val e = engine(s)
            e.setWaypoints(listOf(Waypoint("FAR", 34.0, 133.0)))
            // 60 秒は 2 m/s、そのあと 10 秒は 10 m/s
            return (0..70).map { i ->
                val v = if (i <= 60) 2f else 10f
                val f = fix(t0 + i * 1000L, speed = v)
                e.onFix(f, f.timeMs).etaMs?.let { it - f.timeMs }
            }
        }
        val ten = run(NavSettings())
        val sixty = run(NavSettings(etaAverageSec = 60))
        // 10 秒たつまでは出さない（どちらも同じ。走り始めの決まり）
        assertNull(ten[9])
        assertNull(sixty[9])
        assertEquals(ten[10], sixty[10])
        assertEquals(ten[30], sixty[30])
        // 速くなったあと: 10 秒の平均はすぐ 10 m/s 近く、60 秒の平均はゆっくり
        assertTrue(ten[70]!! < sixty[70]!! / 2)
        val dist = io.github.eightbrows.navhud.core.geo.Geo.distanceM(33.0, 133.0, 34.0, 133.0)
        // 10 秒の窓: 最初の 1 秒は 2→10 m/s の台形（平均 6）、あとの 9 秒は 10 m/s → 平均 9.6 m/s
        assertEquals(dist / 9.6 * 1000, ten[70]!!.toDouble(), 1000.0)
    }

    /** 20261121-D01: 方位の出し方「GPS ＋ 位置」で、GPS の方位が使えないとき TRK。自動車（GPS）は位置から求める向きを使わない */
    @Test
    fun trackHeadingOnlyWithGpsTrack() {
        // 東へ 1.5 m/s で歩く（GPS の方位は北を指すが、方位精度 40° で使えない）
        fun walk(s: NavSettings): NavState {
            val e = engine(s)
            var st: NavState? = null
            for (i in 0..12) {
                val f = Fix(
                    timeMs = t0 + i * 1000L, lat = 33.0, lon = TestGeo.lon(1.5 * i, 33.0, 133.0), speedMps = 1.5f,
                    bearingDeg = 0f, bearingAccDeg = 40f, horizAccM = 4f,
                )
                st = e.onFix(f, f.timeMs)
            }
            return st!!
        }
        val walk = walk(NavSettings().selectTravelMode(TravelMode.WALK))
        assertEquals(HeadingSrc.TRACK, walk.heading.src)
        assertEquals(90.0, walk.heading.deg!!.toDouble(), 0.5)
        // 自動車（GPS）: GPS の方位は方位精度 40° で一度も使えないので、向きなし（位置から求める向きは使わない）
        val car = walk(NavSettings())
        assertEquals(HeadingSrc.NONE, car.heading.src)
        // 時刻が戻ったら位置から求める向きも捨てる
        val e = engine(NavSettings().selectTravelMode(TravelMode.WALK))
        for (i in 0..12) e.onFix(Fix(timeMs = t0 + i * 1000L, lat = 33.0, lon = TestGeo.lon(1.5 * i, 33.0, 133.0), speedMps = 1.5f, horizAccM = 4f), t0 + i * 1000L)
        assertEquals(HeadingSrc.TRACK, e.state.heading.src)
        assertEquals(HeadingSrc.NONE, e.onFix(Fix(timeMs = t0 - 1000, lat = 33.0, lon = 133.0, speedMps = 0f), t0 - 1000).heading.src)
    }
}
