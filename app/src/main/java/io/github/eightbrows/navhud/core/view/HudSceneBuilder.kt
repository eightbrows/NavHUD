package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavState
import kotlin.math.hypot

/**
 * NavState と描画領域から、Canvas が描くもの（HudScene）を座標つきで作る。Android に依存しない。
 * ARC（§6.2）と North Up（§6.3）をここで作り分ける。
 */
object HudSceneBuilder {

    /**
     * @param reserved 画面外の矢印を置かない帯（WP ボタン列、リプレイ操作）[px]。矢印はその内側に置く
     */
    fun build(state: NavState, rect: HudRect, m: HudMetrics = HudMetrics(), reserved: HudInsets = HudInsets()): HudScene {
        val arrowFrame = HudRect(
            rect.left + reserved.left,
            rect.top + reserved.top,
            rect.right - reserved.right,
            rect.bottom - reserved.bottom,
        ).inset(m.edgeInset)
        val s = state.settings
        val headingDeg = state.heading.deg?.toDouble()
        val noFix = state.noFix

        val arcs = mutableListOf<Arc>()
        val segments = mutableListOf<Segment>()
        val labels = mutableListOf<Label>()
        val pointers = mutableListOf<Pointer>()

        val proj: HudProjection
        val ownShipAngle: Float?
        when (s.displayMode) {
            DisplayMode.ARC -> {
                // 方位がなければ北を上にする
                proj = HudGeometry.arcProjection(rect, s.arcRangeM, headingDeg ?: 0.0, m.arcOriginFromBottom)
                ownShipAngle = headingDeg?.let { 0f }
                buildArcScale(proj, rect, s.ringIntervalM, m, arcs, segments, labels, pointers, headingDeg != null)
            }
            DisplayMode.NORTH_UP -> {
                proj = HudGeometry.northUpProjection(rect, s.arcRangeM, m.northUpMargin)
                ownShipAngle = headingDeg?.toFloat()
                buildCompassCard(proj, s.arcRangeM, s.ringIntervalM, headingDeg, m, arcs, segments, labels, pointers)
            }
        }

        val (wpMarks, arrows) = buildWaypoints(state, proj, arrowFrame, m, segments)

        val scene = HudScene(
            rect = rect,
            arcs = arcs,
            segments = segments,
            labels = labels,
            wpMarks = wpMarks,
            arrows = arrows,
            pointers = pointers,
            ownShip = OwnShip(proj.origin, ownShipAngle, Ink.OWNSHIP),
        )
        return if (noFix) scene.stale() else scene
    }

    /** ARC: 前方 180° の距離環、縁に置く方位目盛り、30° ごとの方位線、ラバーライン、上部中央の三角。 */
    private fun buildArcScale(
        proj: HudProjection,
        rect: HudRect,
        ringIntervalM: Double,
        m: HudMetrics,
        arcs: MutableList<Arc>,
        segments: MutableList<Segment>,
        labels: MutableList<Label>,
        pointers: MutableList<Pointer>,
        hasHeading: Boolean,
    ) {
        val o = proj.origin
        // 画面の上の角まで届く距離環を描く（左右ははみ出して切れる）
        val maxPx = maxOf(hypot(o.x - rect.left, o.y - rect.top), hypot(rect.right - o.x, o.y - rect.top))
        addRings(proj, ringIntervalM, maxPx.toDouble(), -90f, 180f, -45.0, arcs, labels)

        for (b in 0 until 360 step 10) {
            val a = proj.screenAngle(b.toDouble())
            if (a < -90.0 || a > 90.0) continue
            val edge = HudGeometry.rayToRect(o, a, rect)
            val major = b % 30 == 0
            val inner = HudGeometry.pointAt(edge, a + 180, if (major) m.tickMajor else m.tickMinor)
            segments += Segment(edge, inner, Ink.SCALE)
            if (major) {
                segments += Segment(o, inner, Ink.BEARING_LINE)
                labels += Label(
                    HudFormat.compassLabel(b),
                    HudGeometry.pointAt(edge, a + 180, m.tickMajor + m.labelGap),
                    Ink.SCALE,
                )
            }
        }
        // ラバーライン（機首方位 = 画面の上）と上部中央の三角マーカー
        val pointerTip = P(rect.centerX, rect.top + m.tickMajor + m.labelGap * 2 + 4)
        if (hasHeading) segments += Segment(o, pointerTip, Ink.OWNSHIP)
        pointers += Pointer(pointerTip, 0f, m.pointerSize, Ink.OWNSHIP)
    }

    /** North Up: 全周の距離環、最外周の外側のコンパスカード、30° ごとの方位線、ラバーライン、機首方位の三角。 */
    private fun buildCompassCard(
        proj: HudProjection,
        rangeM: Double,
        ringIntervalM: Double,
        headingDeg: Double?,
        m: HudMetrics,
        arcs: MutableList<Arc>,
        segments: MutableList<Segment>,
        labels: MutableList<Label>,
        pointers: MutableList<Pointer>,
    ) {
        val o = proj.origin
        val outer = (rangeM * proj.pxPerM).toFloat()
        addRings(proj, ringIntervalM, outer.toDouble() + 0.5, 0f, 360f, -45.0, arcs, labels)

        for (b in 0 until 360 step 10) {
            val a = b.toDouble()
            val major = b % 30 == 0
            val p0 = HudGeometry.pointAt(o, a, outer)
            val p1 = HudGeometry.pointAt(o, a, outer + if (major) m.tickMajor else m.tickMinor)
            segments += Segment(p0, p1, Ink.SCALE)
            if (major) {
                segments += Segment(o, p0, Ink.BEARING_LINE)
                labels += Label(HudFormat.compassLabel(b), HudGeometry.pointAt(o, a, outer + m.tickMajor + m.labelGap), Ink.SCALE)
            }
        }
        if (headingDeg != null) {
            segments += Segment(o, HudGeometry.pointAt(o, headingDeg, outer), Ink.OWNSHIP)
            // 目盛りの内側から外向きに機首方位を指す
            pointers += Pointer(HudGeometry.pointAt(o, headingDeg, outer - 2), headingDeg.toFloat(), m.pointerSize, Ink.OWNSHIP)
        }
    }

    /** interval ごとの距離環を maxPx まで。文字は labelAngle の位置。 */
    private fun addRings(
        proj: HudProjection,
        intervalM: Double,
        maxPx: Double,
        startDeg: Float,
        sweepDeg: Float,
        labelAngle: Double,
        arcs: MutableList<Arc>,
        labels: MutableList<Label>,
    ) {
        if (intervalM <= 0) return
        var k = 1
        while (true) {
            val rM = intervalM * k
            val rPx = rM * proj.pxPerM
            if (rPx > maxPx || k > 50) break
            arcs += Arc(proj.origin, rPx.toFloat(), startDeg, sweepDeg, Ink.SCALE)
            labels += Label(HudFormat.ringKm(rM), HudGeometry.pointAt(proj.origin, labelAngle, rPx.toFloat() + 10f), Ink.SCALE_DIM, small = true)
            k++
        }
    }

    /** WP の線・印・画面外の矢印と、自機から次の WP への線。 */
    private fun buildWaypoints(
        state: NavState,
        proj: HudProjection,
        inner: HudRect,
        m: HudMetrics,
        segments: MutableList<Segment>,
    ): Pair<List<WpMark>, List<EdgeArrow>> {
        val fix = state.fix ?: return emptyList<WpMark>() to emptyList()
        val wps = state.waypoints
        val next = state.nextWpIndex
        val pts = wps.map { proj.toScreen(Geo.toEN(fix.lat, fix.lon, it.lat, it.lon)) }

        // 登録順に結ぶ。無効 WP に触れる区間はグレーの破線、到達済みへの区間は暗め
        for (i in 0 until wps.size - 1) {
            val a = wps[i]
            val b = wps[i + 1]
            val (ink, dashed) = when {
                !a.enabled || !b.enabled -> Ink.WP_DISABLED to true
                b.reached -> Ink.WP_REACHED to false
                else -> Ink.WP to false
            }
            segments += Segment(pts[i], pts[i + 1], ink, dashed)
        }
        // 自機から次の WP への線（マゼンタ）
        if (next != null) segments += Segment(proj.origin, pts[next], Ink.ACTIVE, bold = true)

        // inner: 矢印を置く枠（ボタン列・リプレイ操作の帯を除いた内側）
        val marks = mutableListOf<WpMark>()
        val arrows = mutableListOf<EdgeArrow>()
        wps.forEachIndexed { i, wp ->
            val ink = wpInk(wp, i == next)
            if (inner.contains(pts[i])) {
                marks += WpMark(pts[i], wp.name, ink, dashed = !wp.enabled)
            } else if (wp.enabled && !wp.reached) {
                // 画面外: 表示枠の縁に方位方向の矢印と距離。文字は矢印の内側（自機側）
                val a = HudGeometry.angleOf(proj.origin, pts[i])
                val at = HudGeometry.rayToRect(proj.origin, a, inner)
                val dist = Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon)
                val text = "${wp.name} ${HudFormat.distance(dist)}"
                arrows += EdgeArrow(
                    at = at,
                    angleDeg = a.toFloat(),
                    text = text,
                    // 自機への線（マゼンタ）と重ならないよう、線と直角に少しずらす
                    textAt = placeArrowText(
                        text,
                        HudGeometry.pointAt(HudGeometry.pointAt(at, a + 180, m.arrowTextGap), a + 90, m.arrowLabelLine * 0.75f),
                        a, inner, arrows, m,
                    ),
                    ink = ink,
                )
            }
        }
        return marks to arrows
    }

    /**
     * 矢印の文字の位置。枠からはみ出さないよう横位置を詰め、先に置いた文字と重なるなら自機側へ1行ずつずらす（最大3回）。
     * 凝った配置計算はしない。
     */
    private fun placeArrowText(text: String, start: P, angleDeg: Double, frame: HudRect, placed: List<EdgeArrow>, m: HudMetrics): P {
        val half = textHalfWidth(text, m)
        fun clamp(p: P) = P(
            p.x.coerceIn(frame.left + half, maxOf(frame.left + half, frame.right - half)),
            p.y.coerceIn(frame.top + m.arrowLabelLine / 2, maxOf(frame.top, frame.bottom - m.arrowLabelLine / 2)),
        )
        var p = clamp(start)
        repeat(3) {
            val hit = placed.any { o ->
                kotlin.math.abs(o.textAt.x - p.x) < half + textHalfWidth(o.text, m) &&
                    kotlin.math.abs(o.textAt.y - p.y) < m.arrowLabelLine
            }
            if (!hit) return p
            p = clamp(HudGeometry.pointAt(p, angleDeg + 180, m.arrowLabelLine))
        }
        return p
    }

    /** 文字の幅の半分の目安。全角（日本語など）は半角2文字分として数える。 */
    internal fun textHalfWidth(text: String, m: HudMetrics): Float =
        text.sumOf { c -> if (c.code >= 0x2E80) 2 else 1 }.toInt() * m.labelCharWidth / 2

    private fun wpInk(wp: Waypoint, isNext: Boolean): Ink = when {
        isNext -> Ink.ACTIVE
        !wp.enabled -> Ink.WP_DISABLED
        wp.reached -> Ink.WP_REACHED
        else -> Ink.WP
    }

    /** NO FIX 中: 自機と位置に依存するもの（次の WP、自機の線）をグレーにする。 */
    private fun HudScene.stale(): HudScene {
        fun Ink.s() = if (this == Ink.ACTIVE || this == Ink.OWNSHIP) Ink.STALE else this
        return copy(
            segments = segments.map { it.copy(ink = it.ink.s()) },
            wpMarks = wpMarks.map { it.copy(ink = it.ink.s()) },
            arrows = arrows.map { it.copy(ink = it.ink.s()) },
            pointers = pointers.map { it.copy(ink = it.ink.s()) },
            ownShip = ownShip.copy(ink = Ink.STALE),
        )
    }
}
