package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.ReachInfo
import io.github.eightbrows.navhud.core.model.ReachReason
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.view.HudFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 到達の理由・時刻・最接近の記録（§5.4。WP 一覧に出す）。道は南北の直線で、北 500m・東 lateralM の WP を通る。
 */
class ReachReasonTest {

    private val lon0 = TestGeo.LON0
    private val jst = ZoneId.of("Asia/Tokyo")
    private val t0 = Instant.parse("2026-08-13T23:00:00Z").toEpochMilli()

    private fun wp(northM: Double, eastM: Double, radiusM: Double? = null) =
        Waypoint("W", TestGeo.lat(northM), TestGeo.lon(eastM), radiusM = radiusM)

    private fun fix(t: Long, northM: Double, speed: Float? = 15f, bearing: Float? = 0f) =
        Fix(timeMs = t0 + t * 1_000, lat = TestGeo.lat(northM), lon = lon0, speedMps = speed, bearingDeg = bearing)

    /** Fix を流して、到達したときの記録を返す。 */
    private fun reachOf(w: Waypoint, fixes: List<Fix>): ReachInfo? {
        val e = NavEngine(NavSettings(), jst, SourceKind.REPLAY)
        e.setWaypoints(listOf(w))
        for (f in fixes) e.onFix(f, f.timeMs).waypoints.single().reach?.let { return it }
        return null
    }

    private fun drive(seconds: Long = 60, speed: Double = 15.0) = (0..seconds).map { t -> fix(t, t * speed, speed = speed.toFloat()) }

    @Test
    fun radius() {
        // WP ごとの半径 200m: 21 秒目（約 187m）に「半径」。最接近はそのときまでの最短距離
        val r = reachOf(wp(500.0, 30.0, radiusM = 200.0), drive())!!
        assertEquals(ReachReason.RADIUS, r.reason)
        assertEquals(t0 + 21_000, r.timeMs)
        assertEquals(187.4, r.closestM!!, 1.0)
        assertEquals("半径 08:00:21 最接近 187 m", HudFormat.reach(r, jst))
    }

    @Test
    fun arrival() {
        // 道の上の WP の 25m 手前で停車: 「到着」、最接近 25m
        val fixes = (0..40L).map { t -> val n = minOf(t * 15.0, 475.0); if (n < 475.0) fix(t, n) else fix(t, n, speed = 0f, bearing = null) }
        val r = reachOf(wp(500.0, 0.0), fixes)!!
        assertEquals(ReachReason.ARRIVAL, r.reason)
        assertEquals(t0 + 32_000, r.timeMs)
        assertEquals(25.0, r.closestM!!, 0.5)
    }

    @Test
    fun sidePass() {
        // 道から 30m: 36 秒目に「真横」、最接近 30m（真横を通る線分との距離）
        val r = reachOf(wp(500.0, 30.0), drive())!!
        assertEquals(ReachReason.SIDE, r.reason)
        assertEquals(t0 + 36_000, r.timeMs)
        assertEquals(30.0, r.closestM!!, 0.5)
        assertEquals("真横 08:00:36 最接近 30 m", HudFormat.reach(r, jst))
    }

    @Test
    fun fallbackPass() {
        // 2 m/s で道から 50m の横を通る（真横は 250 秒目）: 予備の「通過」、最接近 50m
        val slow = (0..400L).map { t -> fix(t, 250.0 + (t - 250) * 2.0, speed = 2f) }
        val r = reachOf(wp(250.0, 50.0), slow)!!
        assertEquals(ReachReason.PASS, r.reason)
        assertEquals(t0 + 299_000, r.timeMs)
        assertEquals(50.0, r.closestM!!, 0.5)
    }

    @Test
    fun manualToggle() {
        // WP ボタンで到達にすると「手動」（時刻だけ）。未到達に戻すと消える
        val e = NavEngine(NavSettings(), jst, SourceKind.REPLAY)
        e.setWaypoints(listOf(wp(500.0, 30.0)))
        e.onFix(fix(0, 0.0), t0)
        e.onTick(t0 + 5_000)
        val r = e.toggleReached(0).waypoints.single().reach!!
        assertEquals(ReachReason.MANUAL, r.reason)
        assertEquals(t0 + 5_000, r.timeMs)
        assertNull(r.closestM)
        assertEquals("手動 08:00:05", HudFormat.reach(r, jst))
        assertNull(e.toggleReached(0).waypoints.single().reach)
    }

    @Test
    fun forwardSeek() {
        // 前方へのシークで飛ばした区間で真横を通った: 「シーク・真横」。時刻は飛ばした区間の Fix の時刻
        val e = NavEngine(NavSettings(), jst, SourceKind.REPLAY)
        e.setWaypoints(listOf(wp(500.0, 30.0)))
        val all = drive()
        e.onFix(all[0], all[0].timeMs)
        e.seekReset(toStart = false, passed = all.subList(1, 50))
        val r = e.onFix(all[50], all[50].timeMs).waypoints.single().reach!!
        assertEquals(ReachReason.SIDE, r.reason)
        assertTrue(r.viaSeek)
        assertEquals(t0 + 36_000, r.timeMs)
        assertEquals("シーク・真横 08:00:36 最接近 30 m", HudFormat.reach(r, jst))
        // 先頭まで戻したら、到達と一緒に理由も消える
        e.seekReset(toStart = true)
        assertNull(e.state.waypoints.single().reach)
    }
}
