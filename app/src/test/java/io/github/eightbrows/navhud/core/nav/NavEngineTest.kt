package io.github.eightbrows.navhud.core.nav

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
    private val mPerDegLat = 111_195.0

    /** 2026-08-14 10:00:00 JST */
    private val t0 = Instant.parse("2026-08-14T01:00:00Z").toEpochMilli()

    private fun engine(s: NavSettings = NavSettings()) = NavEngine(s, jst, SourceKind.REPLAY)

    private fun fix(t: Long, lat: Double = 33.0, alt: Double? = 1000.0, speed: Float? = 10f, bearing: Float? = 0f) =
        Fix(timeMs = t, lat = lat, lon = 133.0, altRawM = alt, speedMps = speed, bearingDeg = bearing, horizAccM = 5f)

    @Test
    fun initialStateIsLostWithoutTime() {
        val s = engine().state
        assertNull(s.nowMs)
        assertTrue(s.positionLost)
        assertEquals(Heading.NONE, s.heading)
    }

    @Test
    fun defaultSettings() {
        val s = NavSettings()
        assertEquals(SourceMode.HYBRID, s.sourceMode)
        assertEquals(1.4f, s.minGpsSpeedMps)
        assertEquals(15f, s.maxGpsAccM)
        assertEquals(10, s.lostTimeoutSec)
        assertEquals(36.0, s.altOffsetM, 0.0)
        assertEquals(2_000.0, s.arcRangeM, 0.0)
        assertEquals(1_000.0, s.ringIntervalM, 0.0)
        assertTrue(s.reachRadiusM in NavSettings.REACH_RADIUS_CHOICES_M)
        assertTrue(s.rateWindowSec in NavSettings.RATE_WINDOW_CHOICES_SEC)
    }

    @Test
    fun fixValuesAndAltitudeOffset() {
        val s = engine().onFix(fix(t0, alt = 1000.0, speed = 12.5f), t0)
        assertEquals(t0, s.nowMs)
        assertFalse(s.positionLost)
        assertEquals(964.0, s.altM!!, 1e-9)
        assertEquals(12.5f, s.groundSpeedMps)
        assertEquals(Heading(0f, HeadingSrc.GPS), s.heading)
    }

    @Test
    fun tickAloneUpdatesPositionLost() {
        val e = engine()
        e.onFix(fix(t0), t0)
        assertFalse(e.onTick(t0 + 10_000).positionLost)
        assertTrue(e.onTick(t0 + 10_001).positionLost)
        // 最後の値は残る（画面はグレーで出し続ける）
        assertNotNull(e.state.fix)
        assertNotNull(e.state.altM)
    }

    @Test
    fun lostTimeoutFollowsSettings() {
        val e = engine(NavSettings(lostTimeoutSec = 30))
        e.onFix(fix(t0), t0)
        assertFalse(e.onTick(t0 + 30_000).positionLost)
        assertTrue(e.onTick(t0 + 30_001).positionLost)
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
        for (s in 0..60) e.onFix(fix(t0 + s * 1000L, lat = 33.0 + 10.0 * s / mPerDegLat), t0 + s * 1000L)
        val lastLat = 33.0 + 600 / mPerDegLat
        e.setWaypoints(listOf(Waypoint("N", lastLat + 1000 / mPerDegLat, 133.0)))
        val s = e.state
        assertEquals(0, s.nextWpIndex)
        assertEquals(0.0, s.nextWpBearingDeg!!, 1e-6)
        assertEquals(1000.0, s.nextWpDistanceM!!, 0.5)
        assertEquals(10.0, s.rate!!.avgSpeedMps, 1e-9)
        assertEquals((t0 + 60_000 + 100_000).toDouble(), s.etaMs!!.toDouble(), 100.0)
    }

    @Test
    fun autoReachAdvancesNextWaypoint() {
        val e = engine(NavSettings(reachRadiusM = 50.0))
        e.setWaypoints(listOf(Waypoint("A", 33.0, 133.0), Waypoint("B", 33.1, 133.0)))
        assertEquals(0, e.state.nextWpIndex)
        val s = e.onFix(fix(t0, lat = 33.0 + 30 / mPerDegLat), t0)
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
    fun hybridUsesCompassWhenLostOrStopped() {
        val e = engine()
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
        assertTrue(s.positionLost)
        assertEquals(1, s.waypoints.size)
        assertEquals(SourceMode.GPS, s.sourceMode)
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
