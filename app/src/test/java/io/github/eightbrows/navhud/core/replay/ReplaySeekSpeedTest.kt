package io.github.eightbrows.navhud.core.replay

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySeekSpeedTest {

    private val lon0 = TestGeo.LON0
    private val t0 = 1_000_000L

    /**
     * 北へ 10 m/s、1 秒ごと。60..90 秒は欠損（30 秒の穴 → NO FIX）。WP は 500 m 先の真上（到達）と、1500 m 先の東 150 m（通過判定）。
     */
    private val track: List<Fix> = (0..200).filter { it !in 61..89 }.map { s ->
        Fix(timeMs = t0 + s * 1000L, lat = TestGeo.lat(10.0 * s), lon = lon0, speedMps = 10f, bearingDeg = 0f, horizAccM = 5f, altRawM = 500.0 + s)
    }
    private val wps = listOf(
        Waypoint("A", TestGeo.lat(500.0), lon0),
        Waypoint("B", TestGeo.lat(1_500.0), TestGeo.lon(150.0)),
        Waypoint("C", TestGeo.lat(5_000.0), lon0),
    )

    @Test
    fun clockSpeed() {
        var real = 0L
        val c = ReplayClock { real }
        c.seek(t0)
        c.play()
        real = 1_000
        assertEquals(t0 + 1_000, c.nowMs())
        c.setSpeed(30)
        real = 2_000
        // 変えた時点からは 30 倍
        assertEquals(t0 + 1_000 + 30_000, c.nowMs())
        c.pause()
        real = 5_000
        assertEquals(t0 + 31_000, c.nowMs())
    }

    @Test
    fun playerSeek() {
        val p = ReplayPlayer(track)
        // 先頭より前 → null で、最初から出し直す
        assertNull(p.seek(t0 - 1).last)
        assertEquals(t0, p.due(t0).single().timeMs)
        // 欠損の途中（75 秒）→ その前の最後の Fix（60 秒）を返し、次は 90 秒から
        // 前方へ: 飛ばした Fix（先頭はもう出したので 1..60 秒の 60 点）も返す
        val fwd = p.seek(t0 + 75_000)
        assertEquals(t0 + 60_000, fwd.last!!.timeMs)
        assertEquals(60, fwd.passed.size)
        assertEquals(t0 + 60_000, fwd.passed.last().timeMs)
        assertTrue(p.due(t0 + 89_999).isEmpty())
        assertEquals(t0 + 90_000, p.due(t0 + 90_000).single().timeMs)
        // 最後まで
        assertEquals(t0 + 200_000, p.seek(t0 + 999_999).last!!.timeMs)
        assertTrue(p.finished)
        // 戻せる（後方へは飛ばした Fix なし）
        assertTrue(p.seek(t0 + 10_000).passed.isEmpty())
        assertFalse(p.finished)
        assertEquals(t0 + 200_000, p.endMs)
    }

    /** 実時間を 50 ms ずつ進めて、倍速 speed で最後まで流す。NO FIX になった瞬間があったかも返す。 */
    private fun run(speed: Int): Pair<NavState, Boolean> {
        var real = 0L
        val clock = ReplayClock { real }
        val player = ReplayPlayer(track)
        clock.seek(player.startMs!!)
        clock.setSpeed(speed)
        clock.play()
        val e = NavEngine(NavSettings(), sourceKind = SourceKind.REPLAY)
        e.setWaypoints(wps)
        var sawNoFix = false
        while (!player.finished) {
            real += 50
            val due = player.due(clock.nowMs())
            val s = if (due.isEmpty()) e.onTick(clock.nowMs()) else e.onFixes(due, clock.nowMs())
            if (s.noFix && s.fix != null) sawNoFix = true
        }
        return e.state to sawNoFix
    }

    @Test
    fun resultsDoNotDependOnSpeed() {
        val (x1, noFix1) = run(1)
        val (x30, noFix30) = run(30)
        // 到達（A は半径、B は通過判定）・RATE・最後の位置が同じ。欠損中の NO FIX もどちらでも出る
        assertEquals(listOf(true, true, false), x1.waypoints.map { it.reached })
        assertEquals(x1.waypoints, x30.waypoints)
        assertEquals(x1.rate, x30.rate)
        assertEquals(x1.fix, x30.fix)
        assertTrue(noFix1)
        assertTrue(noFix30)
    }

    @Test
    fun seekResetsHistoryButKeepsReachedUnlessBackToStart() {
        val e = NavEngine(NavSettings(), sourceKind = SourceKind.REPLAY)
        e.setWaypoints(wps)
        e.onFixes(track.take(61), track[60].timeMs)
        assertTrue(e.state.waypoints[0].reached)
        assertTrue(e.state.rate != null)
        // 少し先へシーク: RATE は消え、到達は残る
        e.seekReset(toStart = false)
        val after = e.onFix(track[100], track[100].timeMs)
        assertNull(after.rate)
        assertTrue(after.waypoints[0].reached)
        // 先頭まで戻す: すべて未到達
        e.seekReset(toStart = true)
        val start = e.onFix(track[0], track[0].timeMs)
        assertEquals(List(3) { false }, start.waypoints.map { it.reached })
    }

    @Test
    fun onFixesIsTheSameAsOneByOne() {
        val a = NavEngine(NavSettings(), sourceKind = SourceKind.REPLAY).apply { setWaypoints(wps) }
        val b = NavEngine(NavSettings(), sourceKind = SourceKind.REPLAY).apply { setWaypoints(wps) }
        track.take(150).forEach { a.onFix(it, it.timeMs) }
        b.onFixes(track.take(150), track[149].timeMs)
        assertEquals(a.state.waypoints, b.state.waypoints)
        assertEquals(a.state.rate, b.state.rate)
        assertEquals(a.state.heading, b.state.heading)
    }

    @Test
    fun autoRangeDecidesRightAfterSeek() {
        // 画面の大きさが分からないので距離で判定: 次の WP A（500m 先）は、先頭では 1km の段（500 ≤ 900）
        // AUTO の広域の限度は 10km の段（起動時の 10km が範囲の中）
        val e = NavEngine(NavSettings(initialRangeKm = 10.0, autoMaxRangeKm = 10.0), sourceKind = SourceKind.REPLAY)
        e.setWaypoints(wps)
        e.onFix(track[0], track[0].timeMs)
        assertEquals(10_000.0, e.state.rangeM, 0.0) // 詳細にする方向は 5 秒待つ
        // シークしたら、時計が進まなくても（一時停止中）すぐ決め直す
        e.seekReset(toStart = true)
        assertEquals(1_000.0, e.onFix(track[0], track[0].timeMs).rangeM, 0.0)
    }

    @Test
    fun forwardSeekReachesWaypointsOnTheSkippedPart() {
        val e = NavEngine(NavSettings(), sourceKind = SourceKind.REPLAY)
        e.setWaypoints(wps)
        val player = ReplayPlayer(track)
        e.onFixes(player.due(t0), t0)
        assertEquals(0, e.state.nextWpIndex)
        // 0 秒 → 200 秒（2000 m）へ前方シーク: A（500 m、半径）と B（1500 m の東 150 m、通過判定）を通った
        val r = player.seek(t0 + 200_000)
        e.seekReset(toStart = false, passed = r.passed)
        val s = e.onFix(r.last!!, r.last!!.timeMs)
        assertEquals(listOf(true, true, false), s.waypoints.map { it.reached })
        // 次の WP はシーク先より先の C
        assertEquals(2, s.nextWpIndex)
        // 記録はリセット（RATE なし）
        assertNull(s.rate)
        // 後方へ（100 秒）: 到達状態は残す
        val back = player.seek(t0 + 100_000)
        assertTrue(back.passed.isEmpty())
        e.seekReset(toStart = false, passed = back.passed)
        assertEquals(listOf(true, true, false), e.onFix(back.last!!, back.last!!.timeMs).waypoints.map { it.reached })
    }

    @Test
    fun forwardSeekStoppingBeforeAWaypointDoesNotReachIt() {
        // 0 → 30 秒（300 m）: A（500 m）の 200 m 手前なので到達しない（半径 100 m、通過判定も離れていない）
        val e = NavEngine(NavSettings(), sourceKind = SourceKind.REPLAY)
        e.setWaypoints(wps)
        val player = ReplayPlayer(track)
        e.onFixes(player.due(t0), t0)
        val r = player.seek(t0 + 30_000)
        e.seekReset(toStart = false, passed = r.passed)
        assertEquals(0, e.onFix(r.last!!, r.last!!.timeMs).nextWpIndex)
    }
}
