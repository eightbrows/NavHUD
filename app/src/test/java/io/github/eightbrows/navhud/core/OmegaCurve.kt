package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * テスト用の合成の道。まっすぐ・左カーブ・右カーブをつないで作り、1 秒ごとの Fix（速度と進行方位つき）にする。
 * 座標は基準点（TestGeo）から北・東へ何 m。カーブ（とその手前 30 m）は curveMps、ほかは straightMps で走る。
 */
class TestRoad(
    startHeadingDeg: Double = 0.0,
    private val straightMps: Double = 14.0,
    private val curveMps: Double = 8.0,
    private val startMs: Long = 1_786_000_000_000,
) {
    /** 道の上の点: 北 m・東 m・進行方位 [°]・その点までの道のり [m]・カーブの中か */
    data class Point(val northM: Double, val eastM: Double, val headingDeg: Double, val alongM: Double, val inCurve: Boolean)

    private val pts = mutableListOf<Point>()
    private var n = 0.0
    private var e = 0.0
    private var h = startHeadingDeg
    private var along = 0.0

    init {
        add(false)
    }

    /** 1 m ごとに刻んだ道 */
    val path: List<Point> get() = pts

    private fun add(curve: Boolean) {
        pts += Point(n, e, (h % 360 + 360) % 360, along, curve)
    }

    fun straight(lenM: Double): TestRoad {
        var left = lenM
        while (left > 1e-9) {
            val d = minOf(STEP_M, left)
            val r = Math.toRadians(h)
            n += d * cos(r); e += d * sin(r); along += d; left -= d
            add(false)
        }
        return this
    }

    fun left(radiusM: Double, angleDeg: Double): TestRoad = arc(radiusM, angleDeg, -1)

    fun right(radiusM: Double, angleDeg: Double): TestRoad = arc(radiusM, angleDeg, +1)

    private fun arc(radius: Double, angleDeg: Double, turnRight: Int): TestRoad {
        var left = Math.toRadians(angleDeg) * radius
        while (left > 1e-9) {
            val d = minOf(STEP_M, left)
            val dh = Math.toDegrees(d / radius) * turnRight
            // 弦の向き = 始めの向き + 回る角度の半分
            val r = Math.toRadians(h + dh / 2)
            val chord = 2 * radius * sin(Math.toRadians(abs(dh)) / 2)
            n += chord * cos(r); e += chord * sin(r); h += dh; along += d; left -= d
            add(true)
        }
        return this
    }

    /** 今の位置から、進行方向の右へ rightM（左は −）・前へ aheadM の地点（北 m・東 m） */
    fun offset(rightM: Double, aheadM: Double = 0.0): Pair<Double, Double> {
        val f = Math.toRadians(h)
        val r = Math.toRadians(h + 90)
        return (n + aheadM * cos(f) + rightM * cos(r)) to (e + aheadM * sin(f) + rightM * sin(r))
    }

    /** 道のり along [m] の点（1 m 刻みの近い方） */
    fun at(alongM: Double): Point = pts[(alongM / STEP_M).toInt().coerceIn(0, pts.lastIndex)]

    /** 1 秒ごとの Fix */
    fun fixes(): List<Fix> {
        val out = mutableListOf<Fix>()
        var a = 0.0
        var t = startMs
        val end = pts.last().alongM
        while (a <= end) {
            val p = at(a)
            // カーブの中か、30 m 先がカーブなら、カーブの速度
            val v = if (p.inCurve || at(a + 30.0).inCurve) curveMps else straightMps
            out += Fix(
                timeMs = t, lat = TestGeo.lat(p.northM), lon = TestGeo.lon(p.eastM),
                speedMps = v.toFloat(), bearingDeg = p.headingDeg.toFloat(),
            )
            a += v
            t += 1_000
        }
        return out
    }

    companion object {
        private const val STEP_M = 1.0

        fun waypoint(name: String, at: Pair<Double, Double>): Waypoint = Waypoint(name, TestGeo.lat(at.first), TestGeo.lon(at.second))
    }
}

/**
 * Ω 形のカーブ: まっすぐ → 入口のカーブ（左）→ 本体のカーブ（右。中心に WP）→ 出口のカーブ（左）→ まっすぐ。
 * 入口・出口で向きを変える角度を turnDeg とすると、本体は 2 × turnDeg 回る（90° より大きいと、くびれのある Ω 形）。
 */
class OmegaCurve(
    /** 本体のカーブの半径 [m]（中心に WP を置く） */
    radiusM: Double = 50.0,
    /** 入口・出口のカーブの半径 [m] */
    filletM: Double = 50.0,
    /** 入口・出口で向きを変える角度 [°] */
    turnDeg: Double = 120.0,
    leadInM: Double = 600.0,
    leadOutM: Double = 600.0,
) {
    val road = TestRoad()

    /** 本体のカーブの中心（北 m・東 m） */
    val center: Pair<Double, Double>

    init {
        road.straight(leadInM).left(filletM, turnDeg)
        center = road.offset(rightM = radiusM)
        road.right(radiusM, 2 * turnDeg).left(filletM, turnDeg)
        // 出口の先に置く次の WP の位置を取るため、まっすぐの手前で覚えておく
        road.straight(leadOutM)
    }

    /** 本体のカーブの中心に置く WP */
    fun waypoint(name: String = "カーブ"): Waypoint = TestRoad.waypoint(name, center)

    /** 道の終わりから、最後の向きのまま aheadM 先に置く WP（次の WP 用） */
    fun waypointAhead(name: String, aheadM: Double): Waypoint = TestRoad.waypoint(name, road.offset(0.0, aheadM))
}
