package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.geo.Geo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** 画面座標 [px]。x は右、y は下が正。 */
data class P(val x: Float, val y: Float) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(k: Float) = P(x * k, y * k)
}

/** 描画領域 [px]。 */
data class HudRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    fun contains(p: P): Boolean = p.x in left..right && p.y in top..bottom

    fun inset(d: Float) = HudRect(left + d, top + d, right - d, bottom - d)
}

/**
 * 地図（自機を原点とする東m・北m）から画面座標への変換。
 * @param origin 自機の画面位置
 * @param pxPerM 縮尺
 * @param upDeg 画面の上方向の方位（Heading Up は機首方位、North Up は 0）
 */
data class HudProjection(val origin: P, val pxPerM: Double, val upDeg: Double) {

    fun toScreen(en: EN): P {
        val s = Geo.toScreen(en, upDeg)
        return P((origin.x + s.right * pxPerM).toFloat(), (origin.y - s.fwd * pxPerM).toFloat())
    }

    /** 真方位 → 画面上の角度（上が 0、時計回り、-180..180）。 */
    fun screenAngle(bearingDeg: Double): Double = Geo.angleDiff(upDeg, bearingDeg)
}

object HudGeometry {

    /** 画面上の角度（上が 0、時計回り）の単位ベクトル。 */
    fun dir(angleDeg: Double): P {
        val r = Math.toRadians(angleDeg)
        return P(sin(r).toFloat(), (-cos(r)).toFloat())
    }

    fun pointAt(origin: P, angleDeg: Double, distPx: Float): P = origin + dir(angleDeg) * distPx

    /** from から見た to の画面上の角度（上が 0、時計回り、-180..180）。 */
    fun angleOf(from: P, to: P): Double =
        Math.toDegrees(atan2((to.x - from.x).toDouble(), (from.y - to.y).toDouble()))

    /**
     * origin から angleDeg 方向に伸ばした線と、rect の縁との交点。origin は rect の内側（縁上も可）にあること。
     */
    fun rayToRect(origin: P, angleDeg: Double, rect: HudRect): P {
        val d = dir(angleDeg)
        var t = Float.MAX_VALUE
        if (d.x > EPS) t = min(t, (rect.right - origin.x) / d.x)
        if (d.x < -EPS) t = min(t, (rect.left - origin.x) / d.x)
        if (d.y > EPS) t = min(t, (rect.bottom - origin.y) / d.y)
        if (d.y < -EPS) t = min(t, (rect.top - origin.y) / d.y)
        val p = origin + d * t.coerceAtLeast(0f)
        // 丸め誤差で縁からはみ出さないようにする
        return P(p.x.coerceIn(rect.left, rect.right), p.y.coerceIn(rect.top, rect.bottom))
    }

    /** 画面上の距離 [px]。 */
    fun dist(a: P, b: P): Float = hypot(b.x - a.x, b.y - a.y)

    private const val EPS = 1e-6f

}
