package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.PassDetector
import io.github.eightbrows.navhud.core.nav.SourceKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos

/**
 * ステップ5: track.csv で通過判定を確かめる。道から外した WP でも通過で到達になる。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class PassSampleTest {

    companion object {
    }

    private val fixes: List<Fix>
        get() = SampleTrack.fixes()

    /** fixes[i] から進行方向の右（+）/ 左（−）へ offsetM ずらした地点。 */
    private fun besideTrack(i: Int, offsetM: Double): Waypoint {
        val f = fixes[i]
        val course = Geo.bearingDeg(fixes[i - 5].lat, fixes[i - 5].lon, fixes[i + 5].lat, fixes[i + 5].lon)
        val a = Math.toRadians(course + 90)
        val lat = TestGeo.lat(offsetM * cos(a), f.lat)
        val lon = TestGeo.lon(offsetM * kotlin.math.sin(a), f.lat, f.lon)
        return Waypoint("OFF", lat, lon)
    }

    /** トラック全体での、Fix の点との最短距離と、線分との最短距離。 */
    private fun closest(w: Waypoint): Pair<Double, Double> {
        val point = fixes.minOf { Geo.distanceM(it.lat, it.lon, w.lat, w.lon) }
        val seg = fixes.zipWithNext().minOf { (a, b) -> PassDetector.segmentDistanceM(a, b, w) }
        return point to seg
    }

    /** トラック全体を流して、WP が到達になったか。 */
    private fun reached(w: Waypoint, settings: NavSettings): Boolean {
        val e = NavEngine(settings, ZoneId.of("Asia/Tokyo"), SourceKind.REPLAY)
        e.setWaypoints(listOf(w))
        fixes.forEach { e.onFix(it, it.timeMs) }
        return e.state.waypoints.single().reached
    }

    // 前の版の判定（到達半径 100m と通過判定）を確かめる。真横通過は切る
    private val radius100 = NavSettings(reachRadiusM = 100.0, sidePass = false)

    @Test
    fun waypointOffTheRoadIsReachedByPassing() {
        // トラックの中ほどで、道から 120〜250m 外した地点（どの Fix からも 110m 以上離れる所を選ぶ）
        val mid = fixes.size / 2
        val w = listOf(150.0, -150.0, 200.0, -200.0, 130.0, -130.0)
            .flatMap { off -> listOf(mid, mid + 300, mid - 300).map { besideTrack(it, off) } }
            .first { closest(it).let { (point, seg) -> point > 110.0 && seg < 250.0 } }

        assertFalse("到達半径 100m だけでは到達しない", reached(w, radius100.copy(passDetection = false)))
        assertTrue("通過判定で到達になる", reached(w, radius100))
    }

    @Test
    fun waypointFarFromTheRoadIsNotReached() {
        val mid = fixes.size / 2
        val w = listOf(1_000.0, -1_000.0, 1_500.0, -1_500.0)
            .map { besideTrack(mid, it) }
            .first { closest(it).second > 400.0 }
        assertFalse(reached(w, radius100))
    }

    @Test
    fun waypointInsideTheLongGapIsReachedByPassing() {
        // 62秒欠損（01:06:52Z → 01:07:54Z）の直線のちょうど中間
        val a = fixes.single { it.timeMs == Instant.parse("2026-08-14T01:06:52Z").toEpochMilli() }
        val b = fixes.single { it.timeMs == Instant.parse("2026-08-14T01:07:54Z").toEpochMilli() }
        val w = Waypoint("GAP", (a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
        assertTrue("どの Fix からも到達半径より遠い", closest(w).first > 110.0)

        assertFalse(reached(w, radius100.copy(passDetection = false)))
        assertTrue(reached(w, radius100))
    }
}
