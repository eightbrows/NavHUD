package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.MeasuredScreen
import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.ReachReason
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 到達後の縮小のタイミング（§6.1 の AUTO）: 到達した WP を通り過ぎるまで縮尺を変えず、通り過ぎてから待つ秒数（既定 10 秒）たって
 * AUTO の規則で決める。真横通過・手動は到達したときから数え、シークは待たない。
 * 道は南北の直線で、北へ 10 m/s。A は北 405m（Fix の位置と重ならず、境目の計算にならない所）、次の B は北 5km
 * （AUTO の上限 1km の段より遠い = 待ちが終われば広げたい）。
 * 画面はエミュレータで測った寸法（LIVE）。
 */
class AutoHoldAfterPassTest {

    private val viewport = HudViewport(MeasuredScreen.RECT, HudMetrics().scaled(MeasuredScreen.DENSITY), MeasuredScreen.LIVE)

    private fun engine(kind: SourceKind = SourceKind.LIVE, s: NavSettings = NavSettings()) =
        NavEngine(s, ZoneId.of("Asia/Tokyo"), kind).apply { setViewport(viewport) }

    private fun wps(northM: Double = 405.0, eastM: Double = 0.0) = listOf(
        Waypoint("A", TestGeo.lat(northM), TestGeo.lon(eastM)),
        Waypoint("B", TestGeo.lat(5_000.0), TestGeo.LON0),
    )

    /** t 秒目の Fix: 北へ 10 m/s（stopAtM を超えたら、そこで停車 = 速度 0・方位なし）。 */
    private fun fix(t: Long, stopAtM: Double = Double.MAX_VALUE): Fix {
        val n = t * 10.0
        return if (n < stopAtM) {
            Fix(timeMs = t * 1_000, lat = TestGeo.lat(n), lon = TestGeo.LON0, speedMps = 10f, bearingDeg = 0f)
        } else {
            Fix(timeMs = t * 1_000, lat = TestGeo.lat(stopAtM), lon = TestGeo.LON0, speedMps = 0f, bearingDeg = null)
        }
    }

    /** 0〜seconds 秒の各秒の縮尺と、A に到達した秒・理由。 */
    private data class Run(val range: List<Double>, val reachedAt: Long?, val reason: ReachReason?)

    private fun drive(e: NavEngine, seconds: Long, stopAtM: Double = Double.MAX_VALUE): Run {
        var reachedAt: Long? = null
        var reason: ReachReason? = null
        val range = (0..seconds).map { t ->
            val s = e.onFix(fix(t, stopAtM), t * 1_000)
            if (reachedAt == null && s.waypoints[0].reached) {
                reachedAt = t
                reason = s.waypoints[0].reach?.reason
            }
            s.rangeM
        }
        return Run(range, reachedAt, reason)
    }

    @Test
    fun arrivalWaitsUntilPassedThenTenSeconds() {
        // 到着半径（30m）で 38 秒目（380m、WP まで 25m）に到達。40〜41 秒目の間に WP の横を通り、42 秒目（WP から 15m）に
        // いちばん近づいた距離（0m）から 10m 以上離れて「通り過ぎた」。通り過ぎるまで（38〜41 秒）と、そこから 10 秒（42〜51 秒）は
        // 縮尺を変えず、52 秒目から B へ向けて広げる
        val e = engine().apply { setWaypoints(wps()) }
        val r = drive(e, 70)
        assertEquals(38L, r.reachedAt)
        assertEquals(ReachReason.ARRIVAL, r.reason)
        val atReach = r.range[38]
        assertTrue(atReach < 1_000.0)
        for (t in 38..51) assertEquals("t=$t", atReach, r.range[t], 0.0)
        assertTrue(r.range[52] > atReach)
        // 前（D10）は到達から 10 秒（48 秒目）で変わっていた。今は通り過ぎてから数える
        assertEquals(atReach, r.range[48], 0.0)
    }

    @Test
    fun sidePassCountsFromTheReach() {
        // 道から 50m 東の A: 真横通過で 44 秒目に到達（真横の 400m を過ぎ、いちばん近づいた 50m から +10m）。
        // 真横通過は到達したときに通り過ぎているので、そこから 10 秒（44〜53 秒）は変えず、54 秒目から広げる
        val e = engine().apply { setWaypoints(wps(northM = 400.0, eastM = 50.0)) }
        val r = drive(e, 70)
        assertEquals(44L, r.reachedAt)
        assertEquals(ReachReason.SIDE, r.reason)
        val atReach = r.range[44]
        for (t in 44..53) assertEquals("t=$t", atReach, r.range[t], 0.0)
        assertTrue(r.range[54] > atReach)
    }

    @Test
    fun stoppedAtTheWaypointKeepsTheRange() {
        // A の上（405m）で停車: 38 秒目に到着半径で到達したあと、通り過ぎないので縮尺はずっとそのまま（上限なし）
        val e = engine().apply { setWaypoints(wps()) }
        val r = drive(e, 300, stopAtM = 405.0)
        assertEquals(38L, r.reachedAt)
        val atReach = r.range[38]
        for (t in 38..300) assertEquals("t=$t", atReach, r.range[t], 0.0)
    }

    @Test
    fun manualReachStartsCountingAtOnce() {
        // 停車したまま（原点）、A（北 405m）を見ている。60 秒目の Fix のあと WP ボタンで A を到達にする:
        // 通り過ぎるのを待たず、そこから 10 秒（60〜69 秒）は変えず、70 秒目から B へ向けて広げる
        val e = engine().apply { setWaypoints(wps()) }
        val stopped = { t: Long -> Fix(timeMs = t * 1_000, lat = TestGeo.LAT0, lon = TestGeo.LON0, speedMps = 0f) }
        val range = (0..90L).map { t ->
            val s = e.onFix(stopped(t), t * 1_000)
            if (t == 60L) e.toggleReached(0).rangeM else s.rangeM
        }
        val atToggle = range[60]
        assertTrue(atToggle < 1_000.0)
        for (t in 60..69) assertEquals("t=$t", atToggle, range[t], 0.0)
        assertTrue(range[70] > atToggle)
    }

    @Test
    fun seekDoesNotWait() {
        // REPLAY: 38 秒目に到着半径で到達して、通り過ぎるのを待っている。39 秒目に（その場へ）シークすると、待たずにすぐ縮尺を決め直す
        val e = engine(SourceKind.REPLAY).apply { setWaypoints(wps()) }
        val r = drive(e, 38)
        assertEquals(38L, r.reachedAt)
        val atReach = r.range[38]
        e.seekReset(toStart = false)
        val after = e.onFix(fix(39), 39_000).rangeM
        assertNotEquals(atReach, after, 0.0)
        assertEquals(1_000.0, after, 0.0)
    }

    @Test
    fun passedUsesTheSideDepartDistanceOfTheTravelMode() {
        // 通り過ぎた判定の「離れる距離」は今の移動手段の値（カスタム1 で +50m）: 46 秒目（460m、WP から 55m）に通り過ぎ、56 秒目から広げる。
        // 真横通過をオフにしていても、通り過ぎた判定は同じ条件で行う
        val s = NavSettings().selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(sidePass = false, sidePassDepartM = 50.0) }
        val e = engine(s = s).apply { setWaypoints(wps()) }
        val r = drive(e, 70)
        assertEquals(38L, r.reachedAt)
        val atReach = r.range[38]
        for (t in 38..55) assertEquals("t=$t", atReach, r.range[t], 0.0)
        assertTrue(r.range[56] > atReach)
    }
}
