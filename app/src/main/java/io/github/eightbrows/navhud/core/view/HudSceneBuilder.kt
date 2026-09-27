package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.PanView
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.Trail
import io.github.eightbrows.navhud.core.nav.TrackPoint
import kotlin.math.hypot

/**
 * NavState と描画領域から、Canvas が描くもの（HudScene）を座標つきで作る。Android に依存しない。
 * ARC（§6.2）と North Up（§6.3）をここで作り分ける。
 */
object HudSceneBuilder {

    /**
     * @param rect 描画の枠（地図の全体）。距離環・方位線・方位目盛りはこの枠に描き、重ねた部品の下にもかかってよい
     * @param reserved 地図の上に重ねた部品（右の操作列、リプレイの帯、WP ボタン列）[px]。これを除いた「避ける枠」の内側に、
     *   画面外の矢印・WP の印と名前を置く。AUTO 縮尺の「収まるか」も避ける枠で判定する
     */
    fun build(state: NavState, rect: HudRect, m: HudMetrics = HudMetrics(), reserved: HudInsets = HudInsets()): HudScene {
        val avoid = avoidFrame(rect, reserved)
        val arrowFrame = avoid.inset(m.edgeInset)
        val s = state.settings
        val headingDeg = state.heading.deg?.toDouble()
        val noFix = state.noFix
        val fix = state.fix
        val pan = state.pan
        // 縮尺と、距離環の間隔（縮尺の 1/2）
        val range = state.rangeM
        val ringInterval = range / 2

        val arcs = mutableListOf<Arc>()
        val segments = mutableListOf<Segment>()
        val labels = mutableListOf<Label>()
        val pointers = mutableListOf<Pointer>()

        val proj = projection(s, rect, avoid, range, headingDeg, m, pan)
        // 地図の基準の地点: 通常は自機（proj.origin に描く）、PAN は PAN の中心
        val ref = if (pan != null) pan.lat to pan.lon else fix?.let { it.lat to it.lon }
        val ownAt: P? = when {
            pan == null -> proj.origin
            fix != null -> proj.toScreen(Geo.toEN(pan.lat, pan.lon, fix.lat, fix.lon))
            else -> null
        }
        val ownShipAngle: Float? = when {
            headingDeg == null -> null
            pan != null -> proj.screenAngle(headingDeg).toFloat()
            s.displayMode == DisplayMode.ARC -> 0f
            else -> headingDeg.toFloat()
        }
        when {
            pan != null -> buildPanScale(proj, rect, rect, ringInterval, ownAt, m, arcs, segments, labels)
            s.displayMode == DisplayMode.ARC ->
                buildArcScale(proj, rect, rect, ringInterval, m, arcs, segments, labels, pointers, headingDeg != null)
            else -> buildCompassCard(proj, range, ringInterval, headingDeg, m, arcs, segments, labels, pointers)
        }

        // 距離環の文字が方位目盛りの文字に重なるなら、距離環の文字を出さない（目盛りを優先）
        val compassBoxes = labels.filter { !it.small }.map { labelBox(it, m) }
        labels.removeAll { l -> l.small && compassBoxes.any { it.overlaps(labelBox(l, m)) } }

        // 上部の三角（機首方位の印）も文字を置かない所にする
        val pointerBoxes = pointers.map { Box(P(it.tip.x, it.tip.y + it.sizePx / 2), it.sizePx * 0.7f, it.sizePx * 0.7f) }
        // ARC の上部中央の三角（方位マーカー）とラバーラインの周り: 画面外の矢印（三角）を置かず、横にずらす
        val markerZone = pointers.firstOrNull()?.takeIf { pan == null && s.displayMode == DisplayMode.ARC }?.let { p ->
            val top = p.tip.y
            val bottom = if (headingDeg != null) proj.origin.y else p.tip.y + p.sizePx
            Box(P(p.tip.x, (top + bottom) / 2), p.sizePx * 2f, (bottom - top) / 2 + p.sizePx)
        }
        val (wpMarks, arrows) = buildWaypoints(
            state, proj, ref, ownAt, arrowFrame, m, segments, labels, pointerBoxes, listOfNotNull(markerZone),
        )

        val scene = HudScene(
            rect = rect,
            arcs = arcs,
            segments = segments,
            labels = labels,
            wpMarks = wpMarks,
            arrows = arrows,
            pointers = pointers,
            ownShip = ownAt?.let { OwnShip(it, ownShipAngle, Ink.OWNSHIP) },
            trails = buildTrails(state, proj, ref, ownAt),
        )
        return if (noFix) scene.stale() else scene
    }

    /**
     * 地図 → 画面の変換。表示モードと ARC の自機の位置（設定）で決まる。AUTO 縮尺の判定（HudViewport）も同じものを使う。
     * ARC: 自機は描画の枠の横の中央、高さは避ける枠の下端（WP ボタン列・リプレイの帯の上端）から標準 / 高め。
     *   基準の距離環が描画の枠（画面）の左右端に接する。方位がなければ北を上にする。
     * North Up: 自機は横は描画の枠の中央、縦は避ける枠の中央。最外周の距離環は、画面の幅と避ける枠の高さの小さい方に収める。
     * PAN: 縮尺は同じで、PAN の中心を（横は描画の枠の中央、縦は避ける枠の中央）に置き、上を PAN の向きにする。
     * @param rect 描画の枠
     * @param avoid 避ける枠（avoidFrame）
     */
    fun projection(
        s: NavSettings,
        rect: HudRect,
        avoid: HudRect,
        rangeM: Double,
        headingDeg: Double?,
        m: HudMetrics,
        pan: PanView? = null,
    ): HudProjection {
        val base = when (s.displayMode) {
            DisplayMode.ARC -> {
                val up = if (s.ownshipPosition == OwnshipPosition.HIGH) m.arcOriginFromBottomHigh else m.arcOriginFromBottom
                HudProjection(P(rect.centerX, avoid.bottom - up), rect.width / 2.0 / rangeM, headingDeg ?: 0.0)
            }
            DisplayMode.NORTH_UP -> {
                val radius = minOf(rect.width, avoid.height) / 2 - m.northUpMargin
                HudProjection(P(rect.centerX, avoid.centerY), radius / rangeM, 0.0)
            }
        }
        return if (pan == null) base else HudProjection(P(rect.centerX, avoid.centerY), base.pxPerM, pan.upDeg)
    }

    /** 避ける枠: 描画の枠から、地図の上に重ねた部品（右の操作列、リプレイの帯、WP ボタン列）を除いた領域。 */
    fun avoidFrame(rect: HudRect, reserved: HudInsets): HudRect = HudRect(
        rect.left + reserved.left,
        rect.top + reserved.top,
        rect.right - reserved.right,
        rect.bottom - reserved.bottom,
    )

    /** 画面外の矢印を置く枠: 避ける枠から edgeInset だけ内側。WP の印もこの内側だけに描く。 */
    fun arrowFrame(rect: HudRect, reserved: HudInsets, m: HudMetrics): HudRect = avoidFrame(rect, reserved).inset(m.edgeInset)

    /**
     * PAN 中の目盛り: 自機を中心に全周の距離環（画面の一番遠い角まで）と、描画の枠の縁に置く方位目盛り
     * （PAN の中心から各方位へ引いた線と縁の交点。向きは PAN の向きで固定）。ラバーライン・方位線は描かない。
     */
    private fun buildPanScale(
        proj: HudProjection,
        frame: HudRect,
        rect: HudRect,
        ringIntervalM: Double,
        ownAt: P?,
        m: HudMetrics,
        arcs: MutableList<Arc>,
        segments: MutableList<Segment>,
        labels: MutableList<Label>,
    ) {
        if (ownAt != null) {
            val corners = listOf(P(rect.left, rect.top), P(rect.right, rect.top), P(rect.left, rect.bottom), P(rect.right, rect.bottom))
            val maxPx = corners.maxOf { HudGeometry.dist(ownAt, it) }
            addRings(proj.copy(origin = ownAt), ringIntervalM, maxPx.toDouble(), 0f, 360f, -45.0, arcs, labels)
        }
        val c = proj.origin
        for (b in 0 until 360 step 10) {
            val a = proj.screenAngle(b.toDouble())
            val edge = HudGeometry.rayToRect(c, a, frame)
            val major = b % 30 == 0
            segments += Segment(edge, HudGeometry.pointAt(edge, a + 180, if (major) m.tickMajor else m.tickMinor), Ink.SCALE)
            if (major) {
                labels += Label(HudFormat.compassLabel(b), HudGeometry.pointAt(edge, a + 180, m.tickMajor + m.labelGap), Ink.SCALE)
            }
        }
    }

    /**
     * ARC: 前方 180° の距離環、描画の枠（画面）の縁に置く方位目盛り、30° ごとの方位線、ラバーライン、上部中央の三角。
     * 距離環は描画領域（rect）の上の角まで描き、帯の下にもかかってよい。
     */
    private fun buildArcScale(
        proj: HudProjection,
        frame: HudRect,
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
            val edge = HudGeometry.rayToRect(o, a, frame)
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
        val pointerTip = P(frame.centerX, frame.top + m.tickMajor + m.labelGap * 2 + 4)
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
            // 25 / 250 / 1k / 2.5k。方位目盛りより小さく薄い色（small / SCALE_DIM）
            labels += Label(HudFormat.ringLabel(rM), HudGeometry.pointAt(proj.origin, labelAngle, rPx.toFloat() + 10f), Ink.SCALE_DIM, small = true)
            k++
        }
    }

    /**
     * 軌跡（§6.7）。REPLAY はトラック全体を暗い細線、再生済みの部分（今の Fix まで）をテーマの薄い色で重ねる。
     * LIVE は起動してからの軌跡を細線で。どちらも最後は自機の位置につなぐ。
     */
    private fun buildTrails(state: NavState, proj: HudProjection, ref: Pair<Double, Double>?, ownAt: P?): List<Polyline> {
        val (refLat, refLon) = ref ?: return emptyList()
        fun screen(p: TrackPoint) = proj.toScreen(Geo.toEN(refLat, refLon, p.lat, p.lon))
        val tail = listOfNotNull(ownAt.takeIf { state.fix != null })
        return if (state.sourceKind == SourceKind.REPLAY) {
            val track = state.replayTrack
            if (track.size < 2) return emptyList()
            val all = track.map(::screen)
            val done = all.take(Trail.playedCount(track, state.fix?.timeMs)) + tail
            listOfNotNull(Polyline(all, Ink.TRACK, 1f), done.takeIf { it.size >= 2 }?.let { Polyline(it, Ink.TRACK_DONE, 2f) })
        } else {
            val pts = state.liveTrail.map(::screen) + tail
            listOfNotNull(pts.takeIf { it.size >= 2 }?.let { Polyline(it, Ink.TRACK_DONE, 1.5f) })
        }
    }

    /** WP の線・印・画面外の矢印と、自機から次の WP への線。labels（方位目盛り・距離環の文字）には文字を重ねない。 */
    private fun buildWaypoints(
        state: NavState,
        proj: HudProjection,
        ref: Pair<Double, Double>?,
        ownAt: P?,
        inner: HudRect,
        m: HudMetrics,
        segments: MutableList<Segment>,
        labels: List<Label>,
        pointerBoxes: List<Box> = emptyList(),
        arrowKeepOut: List<Box> = emptyList(),
    ): Pair<List<WpMark>, List<EdgeArrow>> {
        // ref: 地図の基準の地点（通常は自機、PAN は PAN の中心）。矢印の距離は自機から
        val fix = state.fix ?: return emptyList<WpMark>() to emptyList()
        val (refLat, refLon) = ref ?: return emptyList<WpMark>() to emptyList()
        val wps = state.waypoints
        val next = state.nextWpIndex
        val pts = wps.map { proj.toScreen(Geo.toEN(refLat, refLon, it.lat, it.lon)) }

        // 描く WP: 次の WP から先の hudWpCount 個と、直前に到達した WP を1つ（薄く）
        val shown = visibleWaypoints(wps, next, state.settings.hudWpCount)

        // 描く WP どうしを登録順に結ぶ。無効 WP に触れる区間はグレーの破線、到達済みへの区間は暗め
        for ((i, j) in shown.zipWithNext()) {
            val a = wps[i]
            val b = wps[j]
            val (ink, dashed) = when {
                !a.enabled || !b.enabled -> Ink.WP_DISABLED to true
                a.reached || b.reached -> Ink.WP_REACHED to false
                else -> Ink.WP to false
            }
            segments += Segment(pts[i], pts[j], ink, dashed)
        }
        // 自機から次の WP への線（マゼンタ）
        if (next != null && ownAt != null) segments += Segment(ownAt, pts[next], Ink.ACTIVE, bold = true)

        // inner: 矢印を置く枠（ボタン列・リプレイ操作の帯を除いた内側）
        // 文字を置かない所: 自機の記号、方位目盛り・距離環の文字、画面内の WP の印と名前（先に置いた矢印の文字も加えていく）
        // 自機が見えていなければ（PAN）、自機の記号は避けなくてよい
        val ownShipBox = Box(ownAt ?: P(-1e6f, -1e6f), m.ownShipClear, m.ownShipClear)
        val obstacles = mutableListOf(ownShipBox)
        val labelBoxes = labels.map { labelBox(it, m) } + pointerBoxes
        obstacles += labelBoxes
        // 先に画面内の WP（印と名前）を置き、矢印の文字はそれも避ける
        val marks = mutableListOf<WpMark>()
        for (i in shown) {
            val wp = wps[i]
            if (!inner.contains(pts[i])) continue
            val nameAt = placeWpName(wp.name, pts[i], ownShipBox, m, allowBelow = !wp.reached)
            marks += WpMark(pts[i], wp.name, wpInk(wp, i == next), dashed = !wp.enabled, nameAt = nameAt)
            obstacles += Box(pts[i], m.pointerSize * 0.6f, m.pointerSize * 0.6f)
            if (nameAt != null) obstacles += Box(nameAt, textHalfWidth(wp.name, m) * LABEL_WIDTH_RATIO, m.arrowLabelLine / 2)
        }
        val arrows = mutableListOf<EdgeArrow>()
        for (i in shown) {
            val wp = wps[i]
            val ink = wpInk(wp, i == next)
            if (inner.contains(pts[i])) {
                continue
            } else if (wp.enabled && !wp.reached) {
                // 画面外: 表示枠の縁に方位方向の矢印と距離。文字は矢印の内側（自機側）
                val a = HudGeometry.angleOf(proj.origin, pts[i])
                // 三角が方位目盛りの文字や、ARC の方位マーカー・ラバーラインの周りに来るなら、縁に沿ってずらす
                val at = slideArrow(HudGeometry.rayToRect(proj.origin, a, inner), inner, labelBoxes + arrowKeepOut, m)
                val dist = Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon)
                val text = "${wp.name} ${HudFormat.distance(dist)}"
                val textAt = placeArrowText(
                    text,
                    // 自機への線（マゼンタ）と重ならないよう、線と直角に少しずらす
                    HudGeometry.pointAt(HudGeometry.pointAt(at, a + 180, m.arrowTextGap), a + 90, m.arrowLabelLine * 0.75f),
                    // 矢印そのものにも重ねない
                    a, inner, obstacles + Box(at, m.pointerSize, m.pointerSize), m,
                )
                obstacles += Box(textAt, textHalfWidth(text, m), m.arrowLabelLine / 2)
                arrows += EdgeArrow(at = at, angleDeg = a.toFloat(), text = text, textAt = textAt, ink = ink)
            }
        }
        return marks to arrows
    }

    /**
     * 画面外の矢印（三角）の位置: 方位目盛り・距離環の文字（boxes）に重なるなら、枠の縁に沿って
     * 1段（三角の大きさ）ずつ両側へずらして、重ならない所を探す（最大4段）。見つからなければ元の位置。
     */
    internal fun slideArrow(at: P, frame: HudRect, boxes: List<Box>, m: HudMetrics): P {
        val size = m.pointerSize
        fun free(p: P) = boxes.none { it.overlaps(Box(p, size * 0.6f, size * 0.6f)) }
        if (free(at)) return at
        // 左右の縁なら縦に、上下の縁なら横にずらす
        val onSide = kotlin.math.abs(at.x - frame.left) < 0.5f || kotlin.math.abs(at.x - frame.right) < 0.5f
        for (k in 1..4) {
            for (sign in listOf(1f, -1f)) {
                val d = sign * k * size
                val p = if (onSide) {
                    P(at.x, (at.y + d).coerceIn(frame.top, frame.bottom))
                } else {
                    P((at.x + d).coerceIn(frame.left, frame.right), at.y)
                }
                if (free(p)) return p
            }
        }
        return at
    }

    /** 方位目盛り・距離環の文字の占める矩形の目安。 */
    private fun labelBox(l: Label, m: HudMetrics) =
        Box(l.at, textHalfWidth(l.text, m) * (if (l.small) 1f else LABEL_WIDTH_RATIO) + 2f, m.compassLabelHalf)

    /** 文字などの占める矩形（中心と半幅・半高）[px]。 */
    internal data class Box(val c: P, val hw: Float, val hh: Float) {
        fun overlaps(o: Box): Boolean =
            kotlin.math.abs(c.x - o.c.x) < hw + o.hw && kotlin.math.abs(c.y - o.c.y) < hh + o.hh
    }

    /**
     * WP の名前の位置: 印の上。自機の記号と重なるなら下、それでも重なるなら null（名前を描かない）。
     * 到達済みの WP（allowBelow = false）は下へ逃がさず、重なるなら描かない（PAN 中も同じ）。
     */
    internal fun placeWpName(name: String, at: P, ownShip: Box, m: HudMetrics, allowBelow: Boolean = true): P? {
        val hw = textHalfWidth(name, m) * LABEL_WIDTH_RATIO
        val above = P(at.x, at.y - m.wpNameOffset)
        return listOfNotNull(above, P(at.x, at.y + m.wpNameOffset).takeIf { allowBelow })
            .firstOrNull { !Box(it, hw, m.arrowLabelLine / 2).overlaps(ownShip) }
    }

    /**
     * 矢印の文字の位置。枠からはみ出さないよう詰め、文字を置かない所（自機・方位目盛り・先に置いた文字）と重なるなら、
     * 自機側・線と直角の両側へ1行ずつずらした候補を順に試す（最大3行）。どれも重なるなら最初の位置。
     */
    private fun placeArrowText(text: String, start: P, angleDeg: Double, frame: HudRect, obstacles: List<Box>, m: HudMetrics): P {
        val half = textHalfWidth(text, m)
        fun clamp(p: P) = P(
            p.x.coerceIn(frame.left + half, maxOf(frame.left + half, frame.right - half)),
            p.y.coerceIn(frame.top + m.arrowLabelLine / 2, maxOf(frame.top, frame.bottom - m.arrowLabelLine / 2)),
        )
        val line = m.arrowLabelLine
        val candidates = sequence {
            yield(start)
            for (n in 1..3) {
                yield(HudGeometry.pointAt(start, angleDeg + 180, line * n))
                yield(HudGeometry.pointAt(start, angleDeg + 90, line * n))
                yield(HudGeometry.pointAt(start, angleDeg - 90, line * n))
            }
        }.map(::clamp)
        return candidates.firstOrNull { p -> obstacles.none { it.overlaps(Box(p, half, line / 2)) } } ?: clamp(start)
    }

    /** 文字の幅の半分の目安。全角（日本語など）は半角2文字分として数える。 */
    internal fun textHalfWidth(text: String, m: HudMetrics): Float =
        text.sumOf { c -> if (c.code >= 0x2E80) 2 else 1 }.toInt() * m.labelCharWidth / 2

    /**
     * HUD に描く WP の番号（登録順）。次の WP から先の目標（有効かつ未到達）count 個と、その直前に到達した WP を1つ。
     * 目標の間にある無効・到達済みの WP は数えずに描く（グレー・暗め）。次の WP がなければ（全部到達）、最後に到達した WP だけ。
     */
    fun visibleWaypoints(wps: List<Waypoint>, next: Int?, count: Int): List<Int> {
        val end = next ?: wps.size
        val prev = (end - 1 downTo 0).firstOrNull { wps[it].enabled && wps[it].reached }
        val ahead = mutableListOf<Int>()
        if (next != null) {
            var targets = 0
            for (i in next until wps.size) {
                if (targets >= count.coerceAtLeast(1)) break
                ahead += i
                if (wps[i].enabled && !wps[i].reached) targets++
            }
        }
        return listOfNotNull(prev) + ahead
    }

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
            ownShip = ownShip?.copy(ink = Ink.STALE),
        )
    }

    /** 方位目盛り・WP の名前（13sp）と、矢印の文字（11sp、labelCharWidth の基準）の幅の比 */
    private const val LABEL_WIDTH_RATIO = 13f / 11f
}
