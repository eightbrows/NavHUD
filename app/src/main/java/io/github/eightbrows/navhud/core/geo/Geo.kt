package io.github.eightbrows.navhud.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 平面座標（東m, 北m）。 */
data class EN(val e: Double, val n: Double)

/** 画面座標（右m, 前方m）。 */
data class Screen(val right: Double, val fwd: Double)

object Geo {
    const val EARTH_RADIUS_M = 6_371_000.0

    /** 大圏距離（haversine）[m]。 */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /** 初期方位（真北基準 0..360）。 */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalize360(Math.toDegrees(atan2(y, x)))
    }

    /** from→to の最短回転 (-180, 180]。 */
    fun angleDiff(fromDeg: Double, toDeg: Double): Double {
        val d = normalize360(toDeg - fromDeg)
        return if (d > 180.0) d - 360.0 else d
    }

    fun normalize360(deg: Double): Double {
        val d = deg % 360.0
        return if (d < 0) d + 360.0 else d
    }

    /** 自機(originLat, originLon)を原点とする正距円筒近似。数十km以内で使う。 */
    fun toEN(originLat: Double, originLon: Double, lat: Double, lon: Double): EN {
        val dLon = angleDiff(originLon, lon)
        val e = Math.toRadians(dLon) * EARTH_RADIUS_M * cos(Math.toRadians((originLat + lat) / 2))
        val n = Math.toRadians(lat - originLat) * EARTH_RADIUS_M
        return EN(e, n)
    }

    /** 上方向を upDeg とする回転。Heading Up は upDeg = 機首方位、North Up は 0。 */
    fun toScreen(p: EN, upDeg: Double): Screen {
        val h = Math.toRadians(upDeg)
        return Screen(
            right = p.e * cos(h) - p.n * sin(h),
            fwd = p.e * sin(h) + p.n * cos(h),
        )
    }
}
