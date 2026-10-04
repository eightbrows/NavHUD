package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import kotlin.math.abs

/**
 * 真横通過（§5.4）: 道路脇の WP でも、真横に来た時点を通過にする。次をすべて満たしたら到達:
 * - 速度 ≥ 方位を使う速度（HLD を解く速度）で、GPS 方位がある（方位の精度が分かれば上限以内）。HLD 中は使わない
 * - WP までの距離 ≤ 真横通過の距離（sidePassMaxM）
 * - WP への方位と進行方位の差 ≥ 90°（WP が真横か後ろ）
 * - いちばん近づいた距離から sidePassDepartM 以上離れた
 * いちばん近づいた距離は、隣り合う Fix を結んだ線分と WP の最短距離で求める（欠損中に通過しても拾える）。
 */
class SidePassDetector {

    /** 追いかけている WP（次の WP が変わったらリセットする）。 */
    private var targetKey: Any? = null
    private var minDistM = Double.POSITIVE_INFINITY
    private var prev: Fix? = null

    fun reset() {
        targetKey = null
        minDistM = Double.POSITIVE_INFINITY
        prev = null
    }

    /**
     * Fix を1つ入れて、真横を通過したら true を返す。
     * @param key 次の WP を見分けるキー（番号と座標など）。変わったら記録をリセットする
     */
    fun update(fix: Fix, wp: Waypoint, key: Any, s: NavSettings): Boolean =
        track(fix, wp, key, s) { now -> s.sidePass && now <= s.sidePassMaxM }

    /**
     * 到達した WP を「通り過ぎた」か（§6.1 の AUTO の待機）。真横通過と同じ条件（速度・方位・WP が真横か後ろ・
     * いちばん近づいた距離から sidePassDepartM 以上離れた）で、真横通過のオン / オフと距離の上限（sidePassMaxM）は使わない。
     */
    fun updatePassed(fix: Fix, wp: Waypoint, key: Any, s: NavSettings): Boolean = track(fix, wp, key, s) { true }

    /** いちばん近づいた距離を追い、enabled（今の WP までの距離を受け取る）と共通の条件を満たしたら true。 */
    private inline fun track(fix: Fix, wp: Waypoint, key: Any, s: NavSettings, enabled: (nowM: Double) -> Boolean): Boolean {
        if (key != targetKey) {
            reset()
            targetKey = key
        }
        val p = prev
        prev = fix
        val now = Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon)
        val seg = if (p == null) now else PassDetector.segmentDistanceM(p, fix, wp)
        if (seg < minDistM) minDistM = seg
        if (!enabled(now)) return false
        // 走行中で、GPS 方位が使えるとき（HLD を解く速度以上）
        val speed = fix.speedMps ?: return false
        val course = fix.bearingDeg ?: return false
        if (speed < s.holdExitSpeedMps) return false
        fix.bearingAccDeg?.let { if (it > s.maxGpsBearingAccDeg) return false }
        // WP が真横か後ろ
        val toWp = Geo.bearingDeg(fix.lat, fix.lon, wp.lat, wp.lon)
        if (abs(Geo.angleDiff(course.toDouble(), toWp)) < 90.0) return false
        return now >= minDistM + s.sidePassDepartM
    }
}
