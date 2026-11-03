package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 軌跡と WP リストのクリア（20261102-D01）: クリアしたあとは、読み込んでいないときと同じ。 */
class ClearTrackTest {

    private val lat0 = TestGeo.LAT0
    private val lon0 = TestGeo.LON0

    private fun engine(kind: SourceKind) = NavEngine(NavSettings(), ZoneId.of("Asia/Tokyo"), kind).apply { setIdleCenter(35.6852, 139.7528) }

    private fun fix(t: Long, northM: Double = 0.0) = Fix(timeMs = t, lat = TestGeo.lat(northM), lon = lon0)

    private val wps = listOf(Waypoint("A", TestGeo.lat(500.0), lon0), Waypoint("B", TestGeo.lat(1_000.0), lon0))

    @Test
    fun clearingTheTrackInReplayLooksLikeNoTrackWasLoaded() {
        val e = engine(SourceKind.REPLAY)
        e.setWaypoints(wps)
        e.setReplayTrack(listOf(TrackPoint(lat0, lon0, 1_000), TrackPoint(TestGeo.lat(100.0), lon0, 11_000)))
        e.onFix(fix(1_000), 1_000)
        val before = e.onFix(fix(2_000, 20.0), 2_000)
        assertNotNull(before.fix)
        assertEquals(2, before.replayTrack.size)

        val after = e.clearReplayTrack()
        // 自機の位置・時刻・軌跡がない（NO FIX）。地図は位置がないときの中心
        assertNull(after.fix)
        assertNull(after.nowMs)
        assertTrue(after.noFix)
        assertTrue(after.replayTrack.isEmpty())
        assertEquals(35.6852 to 139.7528, after.idleCenter)
        // 縮尺は、起動して何も読んでいないときと同じ位置がないあいだの縮尺（R1 10 km の段）
        assertEquals(io.github.eightbrows.navhud.core.Tuning.IDLE_RANGE_KM * 1000, after.rangeM, 0.0)
        // WP リストはそのまま
        assertEquals(wps.map { it.name }, after.waypoints.map { it.name })
        assertEquals(0, after.nextWpIndex)
    }

    @Test
    fun clearingTheTrackInLiveKeepsTheLivePosition() {
        val e = engine(SourceKind.LIVE)
        e.setReplayTrack(listOf(TrackPoint(lat0, lon0, 1_000), TrackPoint(TestGeo.lat(100.0), lon0, 11_000)))
        e.onFix(fix(1_000), 1_000)
        val after = e.clearReplayTrack()
        assertTrue(after.replayTrack.isEmpty())
        // LIVE の位置は消さない
        assertNotNull(after.fix)
    }

    @Test
    fun clearingTheWaypointListLeavesNoWaypoints() {
        val e = engine(SourceKind.LIVE)
        e.setWaypoints(wps)
        e.onFix(fix(1_000), 1_000)
        e.toggleReached(0)
        val after = e.setWaypoints(emptyList())
        assertTrue(after.waypoints.isEmpty())
        assertNull(after.nextWpIndex)
        assertNull(after.etaMs)
    }
}
