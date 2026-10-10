package io.github.eightbrows.navhud.core.view

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * 距離環の数字の位置（§6.1）。角度は画面が基準（上が 0、時計回り）で、ARC・North Up・PAN とも同じ決まり。
 * - 各距離環の、中心から見て左上（315°）と右上（45°）の点に置く（左右の両側）。
 * - その点が枠（frame）の外なら、距離環に沿って枠の中心に近い側へ回し、距離環が枠に入る最初の点で止める。
 * - 距離環が枠に全く入らないなら、その側は出さない。左右が同じ点に寄ったら1つにまとめる。
 * frame には、文字が画面からはみ出さないよう、描画の枠を文字の半分の大きさだけ内側に寄せたものを渡す。
 * 数値欄・ボタン・ほかの文字との重なりは見ない。
 */
object RingLabelPlacement {

    /** 置き始める角度: 左上・右上 */
    const val LEFT_DEG = 315.0
    const val RIGHT_DEG = 45.0

    /** 左右の点がこれより近ければ1つにまとめる [px] */
    private const val SAME_POINT_PX = 1f

    /** 半径 radius の距離環の数字の位置（左・右の順。出さない側は除く）。 */
    fun anchors(center: P, radius: Float, frame: HudRect): List<P> {
        if (!(radius > 0f)) return emptyList()
        val left = anchor(center, radius, LEFT_DEG, frame)
        val right = anchor(center, radius, RIGHT_DEG, frame)
        return when {
            left != null && right != null && HudGeometry.dist(left, right) < SAME_POINT_PX -> listOf(left)
            else -> listOfNotNull(left, right)
        }
    }

    /**
     * 角度 startDeg から置く1つの数字の位置。その点が枠の中ならそのまま。外なら、枠の中心の方向へ近い回り方で距離環をたどり、
     * 枠の縁と交わる最初の点（そこから枠に入る）。距離環と枠の縁が交わらなければ null。
     */
    internal fun anchor(center: P, radius: Float, startDeg: Double, frame: HudRect): P? {
        val start = HudGeometry.pointAt(center, startDeg, radius)
        if (frame.contains(start)) return start
        val crossings = crossingAngles(center, radius, frame)
        if (crossings.isEmpty()) return null
        // 枠の中心に近い側へ回る（中心の方向との角度の差が正なら時計回り）
        val toCenter = HudGeometry.angleOf(center, P(frame.centerX, frame.centerY))
        val dir = if (signedDiff(toCenter, startDeg) >= 0) 1 else -1
        // その回り方で、最初に出会う交点（外から入る点）
        val a = crossings.minBy { ((dir * (it - startDeg)) % 360.0 + 360.0) % 360.0 }
        val p = HudGeometry.pointAt(center, a, radius)
        // 計算の誤差で縁からわずかに出ないよう、枠の中に寄せる
        return P(p.x.coerceIn(frame.left, frame.right), p.y.coerceIn(frame.top, frame.bottom))
    }

    /** 距離環（中心 c・半径 r）が枠の4辺と交わる点の角度 [°]（上が 0、時計回り、0〜360）。辺の範囲の中のものだけ。 */
    internal fun crossingAngles(c: P, r: Float, frame: HudRect): List<Double> {
        val out = mutableListOf<Double>()
        fun norm(a: Double) = (a % 360.0 + 360.0) % 360.0
        // 画面の点 = (cx + r sin a, cy − r cos a)
        // 縦の辺 x = X: sin a = (X − cx) / r
        for (x in listOf(frame.left, frame.right)) {
            val s = (x - c.x) / r.toDouble()
            if (abs(s) > 1.0) continue
            val a1 = Math.toDegrees(asin(s))
            for (a in listOf(a1, 180.0 - a1)) {
                val y = c.y - r * cos(Math.toRadians(a))
                if (y >= frame.top - EPS && y <= frame.bottom + EPS) out += norm(a)
            }
        }
        // 横の辺 y = Y: cos a = (cy − Y) / r
        for (y in listOf(frame.top, frame.bottom)) {
            val k = (c.y - y) / r.toDouble()
            if (abs(k) > 1.0) continue
            val a1 = Math.toDegrees(acos(k))
            for (a in listOf(a1, -a1)) {
                val x = c.x + r * sin(Math.toRadians(a))
                if (x >= frame.left - EPS && x <= frame.right + EPS) out += norm(a)
            }
        }
        return out
    }

    /** to − from を −180〜180 に */
    private fun signedDiff(to: Double, from: Double): Double = ((to - from) % 360.0 + 540.0) % 360.0 - 180.0

    private const val EPS = 1e-3
}
