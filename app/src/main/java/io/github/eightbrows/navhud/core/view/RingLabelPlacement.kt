package io.github.eightbrows.navhud.core.view

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * 距離環の数字の位置（§6.1）。角度は画面が基準（上が 0、時計回り）で、ARC・North Up・PAN とも同じ決まり。
 * - 各距離環の、中心から見て左上（315°）・右上（45°）・左下（225°）・右下（135°）の4か所に置く（前方と後方、左右の両側）。
 * - その点が枠（frame）の外なら、距離環に沿って枠の中心に近い側へ回し、距離環が枠に入る最初の点で止める（4か所とも同じ決まり）。
 * - 距離環が枠に全く入らないなら出さない。同じ点に寄ったものは1つにまとめる。
 * frame には、文字が画面からはみ出さないよう、描画の枠を文字の半分の大きさだけ内側に寄せたものを渡す。
 * 数値欄・ボタン・ほかの文字との重なりは見ない。
 */
object RingLabelPlacement {

    /** 置き始める角度: 左上・右上（前方）、左下・右下（後方） */
    const val LEFT_DEG = 315.0
    const val RIGHT_DEG = 45.0
    const val BACK_LEFT_DEG = 225.0
    const val BACK_RIGHT_DEG = 135.0

    /** 置く順番（前方の左・右、後方の左・右） */
    val START_DEGS = listOf(LEFT_DEG, RIGHT_DEG, BACK_LEFT_DEG, BACK_RIGHT_DEG)

    /** 2つの点がこれより近ければ1つにまとめる [px] */
    private const val SAME_POINT_PX = 1f

    /**
     * 半径 radius の距離環の数字の位置（左上・右上・左下・右下の順。出さないものは除き、同じ点に寄ったものは先の1つだけ）。
     */
    fun anchors(center: P, radius: Float, frame: HudRect): List<P> {
        if (!(radius > 0f)) return emptyList()
        val out = mutableListOf<P>()
        for (deg in START_DEGS) {
            val p = anchor(center, radius, deg, frame) ?: continue
            if (out.none { HudGeometry.dist(it, p) < SAME_POINT_PX }) out += p
        }
        return out
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
