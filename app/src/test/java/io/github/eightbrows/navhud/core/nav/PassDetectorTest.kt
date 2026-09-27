package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class PassDetectorTest {

    private val lat0 = 33.5
    private val lon0 = 133.0
    private val mLat = 111_195.0
    private val mLon = mLat * cos(Math.toRadians(lat0))
    private val s = NavSettings()

    /** 北へ northM、東へ eastM の Fix（時刻 t 秒）。 */
    private fun fix(t: Int, northM: Double, eastM: Double = 0.0) =
        Fix(timeMs = t * 1000L, lat = lat0 + northM / mLat, lon = lon0 + eastM / mLon)

    private fun wp(northM: Double, eastM: Double) = Waypoint("W", lat0 + northM / mLat, lon0 + eastM / mLon)

    /** 北へ 10m/s で走ったとき、通過と判定された時刻（秒）。なければ null。 */
    private fun passTime(w: Waypoint, track: List<Fix>): Int? {
        val d = PassDetector()
        return track.firstOrNull { d.update(it, w, "k", s) }?.let { (it.timeMs / 1000).toInt() }
    }

    private val straight = (0..300).map { fix(it, it * 10.0) }

    @Test
    fun passesWaypointBesideTheRoad() {
        // 道から東へ 150m、北 1000m。最接近は t=100 で 150m。
        // 200m（=150+50）以上離れるのは t=114（√(140²+150²)≈205）、そこから 5 秒 → t=119
        assertEquals(119, passTime(wp(1_000.0, 150.0), straight))
    }

    @Test
    fun waypointRadiusWidensMaxApproach() {
        // 道から 400m の WP: 全体の上限 300m では通過しない。WP の半径 200m（× 3 = 600m）なら通過
        assertEquals(null, passTime(wp(1_000.0, 400.0), straight))
        assertEquals(true, passTime(wp(1_000.0, 400.0).copy(radiusM = 200.0), straight) != null)
        // 半径が小さければ全体の上限のまま
        assertEquals(300.0, PassDetector.maxApproachM(wp(0.0, 0.0).copy(radiusM = 50.0), s), 0.0)
        assertEquals(600.0, PassDetector.maxApproachM(wp(0.0, 0.0).copy(radiusM = 200.0), s), 0.0)
    }

    @Test
    fun farWaypointIsNotPassed() {
        assertEquals(null, passTime(wp(1_000.0, 400.0), straight))
    }

    @Test
    fun passingDuringGapIsDetected() {
        // 0..90 秒は 0..900m、そのあと欠損して 150 秒で 1500m。WP は欠損区間のちょうど脇 30m
        val gap = (0..90).map { fix(it, it * 10.0) } + (150..170).map { fix(it, 1_500.0 + (it - 150) * 10.0) }
        val w = wp(1_200.0, 30.0)
        // Fix の点だけなら最接近は 300m 以上
        assertTrue(gap.minOf { io.github.eightbrows.navhud.core.geo.Geo.distanceM(it.lat, it.lon, w.lat, w.lon) } > 299.0)
        // 線分で見れば 30m。欠損後の最初の Fix（t=150）から 5 秒で通過
        assertEquals(155, passTime(w, gap))
    }

    @Test
    fun jitterWhileStoppedIsNotAPass() {
        // WP から 100m の所で停止し、位置が ±5m ぶれる
        val stopped = (0..120).map { fix(it, 900.0 + if (it % 2 == 0) 5.0 else -5.0) }
        assertEquals(null, passTime(wp(1_000.0, 0.0), stopped))
    }

    @Test
    fun mustStayAwayForHoldTime() {
        // 最接近 150m のあと 250m まで離れるが、3 秒で戻ってくる → 通過にならない
        val d = PassDetector()
        val w = wp(0.0, 150.0)
        val t = listOf(fix(0, 0.0), fix(1, 200.0), fix(2, 200.0), fix(3, 200.0), fix(4, 200.0), fix(5, 0.0), fix(6, 0.0))
        assertTrue(t.none { d.update(it, w, "k", s) })
    }

    @Test
    fun changingTargetResetsTheRecord() {
        val d = PassDetector()
        val a = wp(1_000.0, 150.0)
        straight.take(110).forEach { d.update(it, a, "A", s) }
        assertEquals(150.0, d.closestM, 1.0)
        // 次の WP が変わったら最接近の記録は捨てる
        d.update(straight[110], wp(3_000.0, 0.0), "B", s)
        assertEquals(1_900.0, d.closestM, 1.0)
    }

    @Test
    fun segmentDistance() {
        val w = wp(0.0, 0.0)
        assertEquals(30.0, PassDetector.segmentDistanceM(fix(0, -100.0, 30.0), fix(1, 100.0, 30.0), w), 0.01)
        // 線分の外側なら端点までの距離
        assertEquals(100.0, PassDetector.segmentDistanceM(fix(0, 100.0, 0.0), fix(1, 200.0, 0.0), w), 0.01)
        assertEquals(50.0, PassDetector.segmentDistanceM(fix(0, 0.0, 50.0), fix(1, 0.0, 50.0), w), 0.01)
    }

    @Test
    fun engineMarksReachedAndCanBeTurnedOff() {
        fun run(settings: NavSettings): NavState {
            val e = NavEngine(settings)
            e.setWaypoints(listOf(wp(1_000.0, 150.0), wp(2_500.0, 0.0)))
            straight.take(130).forEach { e.onFix(it, it.timeMs) }
            return e.state
        }
        val on = run(NavSettings())
        assertTrue(on.waypoints[0].reached)
        assertEquals(1, on.nextWpIndex)
        val off = run(NavSettings(passDetection = false))
        assertFalse(off.waypoints[0].reached)
        assertEquals(0, off.nextWpIndex)
    }
}
