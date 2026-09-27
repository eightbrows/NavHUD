package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailTest {

    private val mPerDegLat = 111_195.0
    private val lat0 = 33.5

    /** 北へ stepM ずつ n 点、1 秒ごと。 */
    private fun line(n: Int, stepM: Double) = (0 until n).map { Fix(timeMs = it * 1000L, lat = lat0 + it * stepM / mPerDegLat, lon = 133.0) }

    @Test
    fun decimateKeepsTenMeterSpacingAndEnds() {
        // 3 m 間隔の 100 点（297 m）→ 12 m ごと（4 点に1つ）と最後の点
        val d = Trail.decimate(line(100, 3.0))
        assertEquals(0L, d.first().timeMs)
        assertEquals(99_000L, d.last().timeMs)
        assertEquals(26, d.size)
        assertTrue(d.zipWithNext().dropLast(1).all { (a, b) -> io.github.eightbrows.navhud.core.geo.Geo.distanceM(a.lat, a.lon, b.lat, b.lon) >= 10.0 })
        assertEquals(emptyList<TrackPoint>(), Trail.decimate(emptyList()))
    }

    @Test
    fun playedCount() {
        val pts = Trail.decimate(line(100, 20.0))
        assertEquals(0, Trail.playedCount(pts, null))
        assertEquals(0, Trail.playedCount(pts, -1))
        assertEquals(11, Trail.playedCount(pts, 10_000))
        assertEquals(11, Trail.playedCount(pts, 10_999))
        assertEquals(100, Trail.playedCount(pts, 999_999))
    }

    @Test
    fun liveTrailThinsAndCaps() {
        val t = LiveTrail(minStepM = 5.0, maxPoints = 10)
        // 2 m ずつ動く → 5 m 以上離れたときだけ足す（0, 6, 12 … m）
        line(20, 2.0).forEach { t.add(it) }
        assertEquals(listOf(0L, 3_000L, 6_000L, 9_000L, 12_000L, 15_000L, 18_000L), t.points.map { it.timeMs })
        // 上限を超えたら古い方から捨てる
        line(40, 10.0).drop(2).forEach { t.add(it) }
        assertEquals(10, t.points.size)
        assertEquals(39_000L, t.points.last().timeMs)
        t.clear()
        assertTrue(t.points.isEmpty())
    }

    @Test
    fun engineKeepsLiveTrailOnlyInLive() {
        val live = NavEngine(NavSettings(), sourceKind = SourceKind.LIVE)
        line(30, 10.0).forEach { live.onFix(it, it.timeMs) }
        assertEquals(30, live.state.liveTrail.size)
        val replay = NavEngine(NavSettings(), sourceKind = SourceKind.REPLAY)
        line(30, 10.0).forEach { replay.onFix(it, it.timeMs) }
        assertTrue(replay.state.liveTrail.isEmpty())
        // REPLAY のトラック全体は setReplayTrack で渡す
        replay.setReplayTrack(Trail.decimate(line(30, 12.0)))
        assertEquals(30, replay.state.replayTrack.size)
    }
}
