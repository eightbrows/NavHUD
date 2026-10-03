package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * WP の到達判定（§5.4）: WP ごとの半径 → 到着半径 → 真横通過 → 通過判定（予備）。
 * 道は南北の直線（経度一定）。北へ走り、北 500m・東 lateralM の WP を通る（真横は北 500m）。設定は自動車の推奨値。
 */
class ReachJudgeTest {

    private fun wp(northM: Double, eastM: Double, radiusM: Double? = null) =
        Waypoint("W", TestGeo.lat(northM), TestGeo.lon(eastM), radiusM = radiusM)

    private fun fix(t: Long, northM: Double, eastM: Double = 0.0, speed: Float? = 15f, bearing: Float? = 0f) =
        Fix(timeMs = t * 1_000, lat = TestGeo.lat(northM), lon = TestGeo.lon(eastM), speedMps = speed, bearingDeg = bearing)

    /** 1 秒ごとの Fix を流し、WP が到達になった秒を返す（ならなければ null）。 */
    private fun reachedAt(w: Waypoint, fixes: List<Fix>, s: NavSettings = NavSettings()): Long? {
        val e = NavEngine(s, ZoneId.of("Asia/Tokyo"), SourceKind.LIVE)
        e.setWaypoints(listOf(w))
        for (f in fixes) {
            if (e.onFix(f, f.timeMs).waypoints.single().reached) return f.timeMs / 1_000
        }
        return null
    }

    /** 15 m/s で北へ（真横は 33.3 秒目）。 */
    private fun driveNorth(seconds: Long = 60, speed: Double = 15.0, bearing: Float? = 0f, sp: Float? = speed.toFloat()) =
        (0..seconds).map { t -> fix(t, t * speed, speed = sp, bearing = bearing) }

    @Test
    fun carPresetValues() {
        val s = NavSettings()
        assertEquals(TravelMode.CAR, s.travelMode)
        assertEquals(30.0, s.reachRadiusM, 0.0)
        assertTrue(s.sidePass)
        assertEquals(150.0, s.sidePassMaxM, 0.0)
        assertEquals(10.0, s.sidePassDepartM, 0.0)
        assertTrue(s.passDetection)
        assertEquals(300.0, s.passMaxApproachM, 0.0)
        assertEquals(50.0, s.passDepartM, 0.0)
        assertEquals(5, s.passHoldSec)
        // 自動車を選ぶと推奨値が入る
        assertEquals(NavSettings(), NavSettings(travelMode = TravelMode.CUSTOM1, reachRadiusM = 100.0, sidePass = false).selectTravelMode(TravelMode.CAR))
    }

    @Test
    fun roadsideWaypoint30mIsReachedAbeam() {
        // 道から 30m の WP: 到着半径（30m）には入らない（495m で 30.4m）。真横（33.3 秒）を過ぎて 40m 離れた 36 秒目に到達
        val t = reachedAt(wp(500.0, 30.0), driveNorth())!!
        assertEquals(36L, t)
        // 手前（真横より前）では到達しない
        assertNull(reachedAt(wp(500.0, 30.0), driveNorth(33)))
        // 前の版（到達半径 100m、真横通過なし）では、手前 95m（27 秒目）で到達していた
        assertEquals(27L, reachedAt(wp(500.0, 30.0), driveNorth(), NavSettings(reachRadiusM = 100.0, sidePass = false)))
    }

    @Test
    fun roadsideWaypoint80mIsReachedAbeam() {
        // 道から 80m の WP: 真横を過ぎて 90m 離れた 37 秒目に到達（真横の 3.7 秒後）
        assertEquals(37L, reachedAt(wp(500.0, 80.0), driveNorth()))
        assertNull(reachedAt(wp(500.0, 80.0), driveNorth(33)))
    }

    @Test
    fun stoppingBeforeTheWaypointReachesAt30m() {
        // 道の上の WP（北 500m）の 25m 手前で停車（速度 0・方位なし）: 到着半径 30m で到達
        val fixes = (0..40L).map { t ->
            val n = minOf(t * 15.0, 475.0)
            if (n < 475.0) fix(t, n) else fix(t, n, speed = 0f, bearing = null)
        }
        assertEquals(32L, reachedAt(wp(500.0, 0.0), fixes))
        // 35m 手前で停車したら到達しない
        val far = (0..60L).map { t -> val n = minOf(t * 15.0, 465.0); fix(t, n, speed = if (n < 465.0) 15f else 0f, bearing = if (n < 465.0) 0f else null) }
        assertNull(reachedAt(wp(500.0, 0.0), far))
    }

    @Test
    fun slowOrNoBearingUsesTheFallbackPassDetection() {
        // 2 m/s（HLD を解く速度 3.0 m/s 未満）で、道から 50m の WP の横を通る（真横は 250 秒目）。真横通過は使わない
        val slow = (0..400L).map { t -> fix(t, 250.0 + (t - 250) * 2.0, speed = 2f, bearing = 0f) }
        val w = wp(250.0 + 0.0, 50.0)
        val t = reachedAt(w, slow)!!
        // 予備の通過判定: 最接近 50m から +50m（100m。真横から 86.6m 先なので 294 秒目に初めて超える）離れて 5 秒続いた 299 秒目
        assertEquals(299L, t)
        // 15 m/s でも方位がない（null）なら、真横通過は使わず予備の判定（真横から 86.6m 先を初めて超える 40 秒目 → 5 秒後の 45 秒目）
        assertEquals(45L, reachedAt(wp(500.0, 50.0), driveNorth(bearing = null)))
    }

    @Test
    fun hairpinApproachWithin150mIsTakenAsPassed() {
        // 北へ 480m まで走って（WP は道の上の北 600m、120m 手前）ヘアピンで南へ折り返す。
        // 折り返すと WP が後ろになり、いちばん近づいた 120m から 130m 離れたところで、真横通過として到達になる（今の規則）
        val fixes = (0..80L).map { t ->
            if (t <= 32) fix(t, t * 15.0, bearing = 0f) else fix(t, 480.0 - (t - 32) * 15.0, eastM = 20.0, bearing = 180f)
        }
        assertEquals(33L, reachedAt(wp(600.0, 0.0), fixes))
        // 折り返しが 200m 手前（真横通過の 150m の外）なら真横通過は使えないが、予備の通過判定（最接近 ≤ 300m、+50m、5 秒）で到達する
        val far = (0..80L).map { t ->
            if (t <= 26) fix(t, t * 15.0, bearing = 0f) else fix(t, 390.0 - (t - 26) * 15.0, eastM = 20.0, bearing = 180f)
        }
        assertEquals(35L, reachedAt(wp(600.0, 0.0), far))
    }

    @Test
    fun perWaypointRadiusComesFirst() {
        // 道から 30m の WP に半径 200m: 200m 以内に入った 21 秒目（北 315m、約 187m）で到達（真横を待たない）
        assertEquals(21L, reachedAt(wp(500.0, 30.0, radiusM = 200.0), driveNorth()))
        // 半径 10m なら半径では到達せず、真横通過（36 秒目）
        assertEquals(36L, reachedAt(wp(500.0, 30.0, radiusM = 10.0), driveNorth()))
    }

    /** 真横通過だけを見る: Fix を順に入れ、真横通過になった秒を返す（ならなければ null）。 */
    private fun sidePassAt(w: Waypoint, fixes: List<Fix>, s: NavSettings = NavSettings()): Long? {
        val d = SidePassDetector()
        return fixes.firstOrNull { d.update(it, w, "W", s) }?.let { it.timeMs / 1_000 }
    }

    @Test
    fun sidePassUsesBearingOnlyWithinAccuracyLimit() {
        // 道から 80m の WP（真横を過ぎて 90m 離れた 37 秒目）。方位の精度が上限（20°）を超えたら真横通過は使わない
        val s = NavSettings()
        val w = wp(500.0, 80.0)
        fun drive(acc: Float?) = driveNorth().map { it.copy(bearingAccDeg = acc) }
        assertEquals(37L, sidePassAt(w, drive(null)))
        assertEquals(37L, sidePassAt(w, drive(s.maxGpsBearingAccDeg)))
        assertNull(sidePassAt(w, drive(s.maxGpsBearingAccDeg + 0.5f)))
    }

    @Test
    fun sidePassSpeedLimitIsInclusive() {
        // 速度がちょうど HLD を解く速度（3.0 m/s）なら使う。それ未満は使わない（位置は 15 m/s で進めたまま、速度の値だけ変える）
        val s = NavSettings()
        val w = wp(500.0, 80.0)
        fun drive(v: Float) = driveNorth().map { it.copy(speedMps = v) }
        assertEquals(37L, sidePassAt(w, drive(s.holdExitSpeedMps)))
        assertNull(sidePassAt(w, drive(s.holdExitSpeedMps - 0.1f)))
    }
}
