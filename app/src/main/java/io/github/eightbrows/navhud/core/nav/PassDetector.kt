package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import kotlin.math.hypot

/**
 * 通過判定（§5.4 のオプション）: 次の WP に最接近したあと、離れていったら到達とみなす。
 * 最接近距離は、隣り合う Fix を結んだ線分と WP の最短距離で求める（欠損中に通過しても拾える）。
 */
class PassDetector {

    /** 追いかけている WP（次の WP が変わったらリセットする）。 */
    private var targetKey: Any? = null
    private var minDistM = Double.POSITIVE_INFINITY
    private var prev: Fix? = null
    private var farSinceMs: Long? = null

    /** 今までの最接近距離（テスト・デバッグ用）。 */
    val closestM: Double get() = minDistM

    fun reset() {
        targetKey = null
        minDistM = Double.POSITIVE_INFINITY
        prev = null
        farSinceMs = null
    }

    /**
     * Fix を1つ入れて、通過したら true を返す。
     * @param key 次の WP を見分けるキー（番号と座標など）。変わったら記録をリセットする
     */
    fun update(fix: Fix, wp: Waypoint, key: Any, s: NavSettings): Boolean {
        if (key != targetKey) {
            reset()
            targetKey = key
        }
        val p = prev
        prev = fix
        val now = Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon)
        val seg = if (p == null) now else segmentDistanceM(p, fix, wp)
        if (seg < minDistM) minDistM = seg

        if (minDistM > s.passMaxApproachM) {
            farSinceMs = null
            return false
        }
        if (now >= minDistM + s.passDepartM) {
            val since = farSinceMs ?: fix.timeMs.also { farSinceMs = it }
            return fix.timeMs - since >= s.passHoldSec * 1000L
        }
        farSinceMs = null
        return false
    }

    companion object {
        /** WP から線分 a→b への最短距離 [m]（WP を原点とする平面近似）。 */
        fun segmentDistanceM(a: Fix, b: Fix, wp: Waypoint): Double {
            val pa = Geo.toEN(wp.lat, wp.lon, a.lat, a.lon)
            val pb = Geo.toEN(wp.lat, wp.lon, b.lat, b.lon)
            val dx = pb.e - pa.e
            val dy = pb.n - pa.n
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(pa.e * dx + pa.n * dy) / len2).coerceIn(0.0, 1.0)
            return hypot(pa.e + t * dx, pa.n + t * dy)
        }
    }
}
