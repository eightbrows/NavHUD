package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.PanView
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TrackPoint
import io.github.eightbrows.navhud.core.nav.Trail
import kotlin.math.hypot

/**
 * NavState と描画領域から、Canvas が描くもの（HudScene）を座標つきで作る。Android に依存しない。
 * ARC（§6.2）と North Up（§6.3）をここで作り分ける。
 */
object HudSceneBuilder {

    /**
     * @param rect 描画の枠（地図の Canvas 全体）。距離環・方位線・トラック・WP の線と印はこの枠に描く
     * @param reserved 地図の上に重ねた表示（上: 上部バー・数値、右: 操作列、下: WP ボタン列・プロファイル、左: 再生の操作列）[px]。
     *   回避枠・矢印と AUTO の枠（TargetFrame）はここから作る
     * @param numberBands 数値の表示の上下の範囲 [px]（左右は画面いっぱい）。WP の名前・矢印の文字はここに重ねない
     * @param buttonBoxes ボタン類（上部バー・操作列・WP ボタン列・再生の操作列）の矩形 [px]。WP の名前・矢印の文字はここに重ねない
     */
    fun build(
        state: NavState,
        rect: HudRect,
        m: HudMetrics = HudMetrics(),
        reserved: HudInsets = HudInsets(),
        numberBands: List<ClosedFloatingPointRange<Float>> = emptyList(),
        buttonBoxes: List<HudRect> = emptyList(),
    ): HudScene {
        val avoid = avoidFrame(rect, reserved)
        val arrowFrame = arrowFrame(rect, reserved, m)
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
            // 方位目盛りは上端は回避枠、左右は画面の縁に沿わせる。距離環は描画の枠の四隅まで
            pan != null -> buildPanScale(proj, tickFrame(rect, avoid), rect, ringInterval, ownAt, m, ringSp(s), arcs, segments, labels)
            s.displayMode == DisplayMode.ARC ->
                buildArcScale(proj, tickFrame(rect, avoid), rect, ringInterval, m, ringSp(s), arcs, segments, labels, pointers, headingDeg != null)
            else -> buildCompassCard(proj, rect, range, ringInterval, headingDeg, m, ringSp(s), arcs, segments, labels, pointers)
        }

        // 数値の表示・ボタン類の範囲（WP の名前・矢印の文字も重ねない）
        val keepOut = numberBands.map { Box(P(rect.centerX, (it.start + it.endInclusive) / 2), rect.width / 2, (it.endInclusive - it.start) / 2) } +
            buttonBoxes.map { Box(P(it.centerX, it.centerY), it.width / 2, it.height / 2) }
        // North Up: 方位サークルの文字が数値・ボタン類に重なるなら出さない（目盛りの線は描く）
        if (pan == null && s.displayMode == DisplayMode.NORTH_UP) {
            labels.removeAll { l -> !l.small && keepOut.any { it.overlaps(labelBox(l, m)) } }
        }

        // 距離環の数字は、数値・ボタン・WP の文字・方位の文字と重なっても消さない（§6.1）

        // 上部の三角（機首方位の印）も文字を置かない所にする
        val pointerBoxes = pointers.map { Box(P(it.tip.x, it.tip.y + it.sizePx / 2), it.sizePx * 0.7f, it.sizePx * 0.7f) }
        val (wpMarks, arrows) = buildWaypoints(
            state, proj, ref, ownAt, arrowFrame, targetFrame(rect, reserved), rect, m, segments, labels, pointerBoxes,
            keepOut, textFrame(rect, reserved),
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
     * ARC: 自機は描画の枠（画面）の横の中央、高さは arcOriginY（回避枠の下端 = WP ボタン列の上端から、設定の5段）。
     *   基準の距離環が描画の枠（画面）の左右端に接する。方位がなければ北を上にする。
     * North Up: 自機は、横は描画の枠（画面）の中央、縦は回避枠の中央。縮尺の距離環の半径は
     *   min(画面の幅の半分, 回避枠の高さの半分) − 余白。右の操作列と重なってよい（方位サークルはその1つ外側の距離環）。
     * PAN: 縮尺は同じで、PAN の中心を（横は画面の中央、縦は回避枠の中央）に置き、上を PAN の向きにする。
     * @param rect 描画の枠（地図の Canvas 全体）
     * @param avoid 回避枠（avoidFrame）
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
            DisplayMode.ARC -> HudProjection(P(rect.centerX, arcOriginY(s.ownshipPosition, avoid, m)), rect.width / 2.0 / rangeM, headingDeg ?: 0.0)
            DisplayMode.NORTH_UP -> {
                val half = minOf(rect.width / 2, avoid.height / 2)
                val radius = (half - m.northUpEdgeMargin).coerceAtLeast(m.northUpEdgeMargin)
                HudProjection(P(rect.centerX, avoid.centerY), radius / rangeM, 0.0)
            }
        }
        return if (pan == null) base else HudProjection(P(rect.centerX, avoid.centerY), base.pxPerM, pan.upDeg)
    }

    /**
     * ARC の自機の高さ [px]: 回避枠の下端（WP ボタン列の上端）から、設定の段の分だけ上。5段:
     * 1〜3段目は決まった高さ（24 / 84 / 144dp）、5段目（中央）は回避枠の縦中央（North Up の自機と同じ高さ）、
     * 4段目は 3段目と 5段目のちょうど中間。回避枠が低くて中央が 3段目より下になるときは、4・5段目も 3段目と同じ高さにする
     * （上の段が下の段より下にならない）。
     * 自機の記号（半高 ownShipClear）が上部の方位マーカー（三角）にかかる高さなら、かからない一番高い位置にする
     * （上の数値・方位目盛りの文字はマーカーより上にある）。ただし標準より下にはしない（回避枠がとても低いとき）。
     */
    internal fun arcOriginY(position: OwnshipPosition, avoid: HudRect, m: HudMetrics): Float {
        val center = maxOf(avoid.height / 2, m.arcOriginFromBottomHigher)
        val up = when (position) {
            OwnshipPosition.STANDARD -> m.arcOriginFromBottom
            OwnshipPosition.HIGH -> m.arcOriginFromBottomHigh
            OwnshipPosition.HIGHER -> m.arcOriginFromBottomHigher
            OwnshipPosition.NEAR_CENTER -> (m.arcOriginFromBottomHigher + center) / 2
            OwnshipPosition.CENTER -> center
        }
        val highest = arcMarkerTipY(avoid.top, m) + m.pointerSize + m.ownShipClear
        return maxOf(avoid.bottom - up, minOf(highest, avoid.bottom - m.arcOriginFromBottom))
    }

    /** ARC の上部の方位マーカー（三角）の先端の高さ: 回避枠の上端から、方位目盛り・文字の分だけ下。三角は先端から下へ pointerSize */
    internal fun arcMarkerTipY(avoidTop: Float, m: HudMetrics): Float = avoidTop + m.tickMajor + m.labelGap * 2 + 4

    /**
     * 回避枠の上下: 描画の枠から、上の表示（上部バー・数値）と下の表示（WP ボタン列から下）を除いた部分。
     * 右の操作列は、それがある高さの範囲だけ欠く形で、TargetFrame の切り欠き（notch）で扱う。
     * ARC の自機の高さ、方位目盛りの上端、North Up・PAN の中心の高さと North Up の半径に使う。
     */
    fun avoidFrame(rect: HudRect, reserved: HudInsets): HudRect = HudRect(
        rect.left + reserved.left,
        rect.top + reserved.top,
        rect.right,
        rect.bottom - reserved.bottom,
    )

    /**
     * 画面外の矢印と AUTO の判定の枠（TargetFrame）: 上は数値の下端、左・下は描画の枠の左端・下端、
     * 右は操作列のある高さの範囲（reserved.rightSpan）だけ操作列の左端（それより上・下は描画の枠の右端）。
     */
    fun targetFrame(rect: HudRect, reserved: HudInsets): TargetFrame {
        val outer = HudRect(rect.left + reserved.left, rect.top + reserved.top, rect.right, rect.bottom)
        val notch = if (reserved.right > 0f) {
            val span = reserved.rightSpan ?: ((rect.top + reserved.top)..(rect.bottom - reserved.bottom))
            HudRect(rect.right - reserved.right, span.start, rect.right, span.endInclusive)
        } else {
            null
        }
        return TargetFrame(outer, notch)
    }

    /**
     * 画面外の矢印を置く枠: targetFrame の下端だけを WP ボタン列の上端（下に重ねた表示の上端）にして、edgeInset だけ内側。
     * 矢印と文字を WP ボタン列・標高プロファイル・再生の操作列の下に置かない。AUTO の判定の枠は targetFrame のまま。
     */
    fun arrowFrame(rect: HudRect, reserved: HudInsets, m: HudMetrics): TargetFrame = textFrame(rect, reserved).inset(m.edgeInset)

    /**
     * 文字が見える範囲: targetFrame の下端だけを WP ボタン列の上端（下に重ねた表示の上端）にしたもの。上は数値の下端、
     * 左右は描画の枠（左に重ねた操作があればその右）、操作列のある高さの範囲は操作列の左。次の WP の文字はここに必ず入れる（§6.1）。
     */
    fun textFrame(rect: HudRect, reserved: HudInsets): TargetFrame {
        val t = targetFrame(rect, reserved)
        return TargetFrame(t.outer.copy(bottom = rect.bottom - reserved.bottom), t.notch)
    }

    /** 方位目盛りを沿わせる枠: 上は回避枠の上端（数値の下端）、左右は描画の枠（画面）の縁。操作列の下に入ってよい。 */
    private fun tickFrame(rect: HudRect, avoid: HudRect) = HudRect(rect.left, avoid.top, rect.right, rect.bottom)

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
        ringSp: Float,
        arcs: MutableList<Arc>,
        segments: MutableList<Segment>,
        labels: MutableList<Label>,
    ) {
        if (ownAt != null) {
            val corners = listOf(P(rect.left, rect.top), P(rect.right, rect.top), P(rect.left, rect.bottom), P(rect.right, rect.bottom))
            val maxPx = corners.maxOf { HudGeometry.dist(ownAt, it) }
            addRings(proj.copy(origin = ownAt), ringIntervalM, maxPx.toDouble(), rect, m, ringSp, arcs, labels)
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
     * ARC: 全周の距離環、描画の枠（画面）の縁に置く方位目盛り（1周分。自機の後ろ側も）、30° ごとの方位線、ラバーライン、
     * 上部中央の三角。距離環と後ろ側の目盛り・文字は、重ねた部品（WP 列・プロファイル・再生の操作列）の下にかかってよい。
     */
    private fun buildArcScale(
        proj: HudProjection,
        frame: HudRect,
        rect: HudRect,
        ringIntervalM: Double,
        m: HudMetrics,
        ringSp: Float,
        arcs: MutableList<Arc>,
        segments: MutableList<Segment>,
        labels: MutableList<Label>,
        pointers: MutableList<Pointer>,
        hasHeading: Boolean,
    ) {
        val o = proj.origin
        // 描画の枠の四隅に届くまで距離環を描く（はみ出す分は切れる）
        val maxPx = farthestCornerPx(o, rect)
        // 全周（後ろ側も。下に重ねた表示の下も地図として見えるため）
        addRings(proj, ringIntervalM, maxPx.toDouble(), rect, m, ringSp, arcs, labels)

        for (b in 0 until 360 step 10) {
            val a = proj.screenAngle(b.toDouble())
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
        // ラバーライン（機首方位 = 画面の上。描画の枠の上端まで）と、回避枠の上端の三角マーカー（自機の真上）
        val pointerTip = P(o.x, arcMarkerTipY(frame.top, m))
        if (hasHeading) segments += Segment(o, P(o.x, rect.top), Ink.OWNSHIP)
        pointers += Pointer(pointerTip, 0f, m.pointerSize, Ink.OWNSHIP)
    }

    /**
     * North Up: 全周の距離環、方位サークル（縮尺の距離環の1つ外側の距離環）の内側の目盛りと文字（円から内側へ 長い目盛り・
     * 短い目盛り・文字の順。画面からはみ出した分は切れる）、30° ごとの方位線、描画の枠の端までのラバーライン、
     * 縮尺の距離環の上の機首方位の三角。
     */
    private fun buildCompassCard(
        proj: HudProjection,
        rect: HudRect,
        rangeM: Double,
        ringIntervalM: Double,
        headingDeg: Double?,
        m: HudMetrics,
        ringSp: Float,
        arcs: MutableList<Arc>,
        segments: MutableList<Segment>,
        labels: MutableList<Label>,
        pointers: MutableList<Pointer>,
    ) {
        val o = proj.origin
        val outer = (rangeM * proj.pxPerM).toFloat()
        // 方位サークル: 縮尺の距離環の1つ外側の距離環（AUTO の判定は縮尺の距離環のまま）
        val card = ((rangeM + ringIntervalM) * proj.pxPerM).toFloat()
        // 縮尺の距離環より外も、描画の枠の四隅に届くまで同じ間隔で描き足す
        addRings(proj, ringIntervalM, maxOf(outer + 0.5f, farthestCornerPx(o, rect)).toDouble(), rect, m, ringSp, arcs, labels)

        for (b in 0 until 360 step 10) {
            val a = b.toDouble()
            val major = b % 30 == 0
            // 目盛りと文字はサークルの内側
            val p0 = HudGeometry.pointAt(o, a, card)
            val p1 = HudGeometry.pointAt(o, a, card - if (major) m.tickMajor else m.tickMinor)
            segments += Segment(p0, p1, Ink.SCALE)
            if (major) {
                segments += Segment(o, p1, Ink.BEARING_LINE)
                labels += Label(HudFormat.compassLabel(b), HudGeometry.pointAt(o, a, card - m.tickMajor - m.labelGap), Ink.SCALE)
            }
        }
        if (headingDeg != null) {
            // ラバーラインは描画の枠の端まで。機首方位の三角は縮尺の距離環の上（位置は前のまま）
            segments += Segment(o, HudGeometry.rayToRect(o, headingDeg, rect), Ink.OWNSHIP)
            pointers += Pointer(HudGeometry.pointAt(o, headingDeg, outer), headingDeg.toFloat(), m.pointerSize, Ink.OWNSHIP)
        }
    }

    /**
     * interval ごとの全周の距離環を maxPx まで（中心は proj.origin）。距離環の数字は RingLabelPlacement の位置
     * （左上・右上・左下・右下の4か所。外なら距離環に沿って画面の中へ。文字が画面からはみ出さないよう、文字の半分だけ内側の枠で決める）。
     * @param ringSp 距離環の数字の文字の大きさ [sp]（Tuning.RING_LABEL_SP × 設定の大きさ）
     */
    private fun addRings(
        proj: HudProjection,
        intervalM: Double,
        maxPx: Double,
        rect: HudRect,
        m: HudMetrics,
        ringSp: Float,
        arcs: MutableList<Arc>,
        labels: MutableList<Label>,
    ) {
        if (intervalM <= 0) return
        var k = 1
        while (true) {
            val rM = intervalM * k
            val rPx = rM * proj.pxPerM
            if (rPx > maxPx || k > MAX_RINGS) break
            arcs += Arc(proj.origin, rPx.toFloat(), 0f, 360f, Ink.SCALE)
            // 25 / 250 / 1k / 2.5k。方位目盛りより小さく薄い色（small / SCALE_DIM）
            val text = HudFormat.ringLabel(rM)
            val box = textBlockBox(listOf(text), P(0f, 0f), ringSp, m)
            val frame = HudRect(rect.left + box.hw, rect.top + box.hh, rect.right - box.hw, rect.bottom - box.hh)
            for (p in RingLabelPlacement.anchors(proj.origin, rPx.toFloat(), frame)) {
                labels += Label(text, p, Ink.RING_LABEL, small = true)
            }
            k++
        }
    }

    /** 距離環の数字の文字の大きさ [sp]（Tuning.RING_LABEL_SP × 設定の大きさ）。 */
    private fun ringSp(s: NavSettings): Float = Tuning.RING_LABEL_SP * s.ringLabelScalePct / 100f

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
            listOfNotNull(Polyline(all, Ink.TRACK, Tuning.TRACK_LINE_DP), done.takeIf { it.size >= 2 }?.let { Polyline(it, Ink.TRACK_DONE, Tuning.TRACK_DONE_LINE_DP) })
        } else {
            val pts = state.liveTrail.map(::screen) + tail
            listOfNotNull(pts.takeIf { it.size >= 2 }?.let { Polyline(it, Ink.TRACK_DONE, Tuning.LIVE_TRAIL_LINE_DP) })
        }
    }

    /** WP の線・印・画面外の矢印と、自機から次の WP への線。labels（方位目盛り・距離環の文字）には文字を重ねない。 */
    private fun buildWaypoints(
        state: NavState,
        proj: HudProjection,
        ref: Pair<Double, Double>?,
        ownAt: P?,
        inner: TargetFrame,
        seen: TargetFrame,
        drawFrame: HudRect,
        m: HudMetrics,
        segments: MutableList<Segment>,
        labels: List<Label>,
        pointerBoxes: List<Box> = emptyList(),
        numberBoxes: List<Box> = emptyList(),
        visibleFrame: TargetFrame = TargetFrame(drawFrame, null),
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
        // 自機から次の WP への方位線（マゼンタの長い破線。ラバーラインと重なっても、すき間から自機の線が見える）
        if (next != null && ownAt != null) segments += Segment(ownAt, pts[next], Ink.ACTIVE, bold = true, longDash = true)
        // 次の WP の距離と方位（自機から。方位は HDG と同じ真北基準の3桁）
        val nextBearing = next?.let { HudFormat.bearing(Geo.bearingDeg(fix.lat, fix.lon, wps[it].lat, wps[it].lon)) }
        val nextDistance = next?.let { HudFormat.distance(Geo.distanceM(fix.lat, fix.lon, wps[it].lat, wps[it].lon)) }

        // inner: 矢印を置く枠（TargetFrame の縁から edgeInset 内側）
        // 文字を置かない所: 自機の記号、方位目盛り・距離環の文字、画面内の WP の印と名前、数値の表示、操作列
        // （先に置いた矢印の文字も加えていく）。自機が見えていなければ（PAN）、自機の記号は避けなくてよい
        val ownShipBox = Box(ownAt ?: P(-1e6f, -1e6f), m.ownShipClear, m.ownShipClear)
        val obstacles = mutableListOf(ownShipBox)
        // 距離環の数字（small）は縦の線の上に並ぶので避けない（重なってよい）。方位目盛りの文字と上部の三角は避ける
        obstacles += labels.filter { !it.small }.map { labelBox(it, m) } + pointerBoxes
        obstacles += numberBoxes
        inner.notch?.let { n ->
            obstacles += Box(P((n.left + n.right) / 2, (n.top + n.bottom) / 2), (n.right - n.left) / 2, (n.bottom - n.top) / 2)
        }
        // 先に画面内の WP（印と名前）を置き、矢印の文字はそれも避ける
        val marks = mutableListOf<WpMark>()
        // 印は描画の枠の中なら描く（重ねた表示の下でも）
        val inFrame = shown.filter { drawFrame.contains(pts[it]) }
        // 文字: 次の WP は 距離・名前・方位 の3行（画面内でも矢印でも同じ）、ほかは名前だけ。重ねた数値の表示・ボタン類と重なるなら出さない。
        // ただし次の WP の文字は消さない: 置ける所がなければ基準の位置（印の上）に重ねて出し、見える範囲（visibleFrame）に寄せる。
        // 矢印を出す次の WP（重ねた表示の下・描画の枠の外）は、矢印の文字で出す
        // 文字どうしが重なるなら、次の WP の文字を残し、ほかはルートの順に先に置いた文字を残す（もう一方は印と線だけ）。
        // 重なりは行数分の高さの箱で判定する
        fun linesOf(i: Int) = if (i == next) listOfNotNull(nextDistance, wps[i].name, nextBearing) else listOf(wps[i].name)
        val names = HashMap<Int, P?>()
        val nameBoxes = mutableListOf<Box>()
        for (i in inFrame.sortedBy { if (it == next) -1 else inFrame.indexOf(it) }) {
            val wp = wps[i]
            val lines = linesOf(i)
            val placed = placeWpLines(lines, pts[i], ownShipBox, m, allowBelow = !wp.reached)
                ?.takeIf { p -> (numberBoxes + nameBoxes).none { it.overlaps(wpTextBox(lines, p, m)) } }
            val nameAt = when {
                i == next && seen.contains(pts[i]) -> keepVisible(wpTextBox(lines, placed ?: wpLinesAbove(lines, pts[i], m), m), visibleFrame)
                // 矢印を出す次の WP: 文字は矢印の文字だけ（印の文字と2つ出さない）
                i == next -> null
                else -> placed
            }
            names[i] = nameAt
            if (nameAt != null) nameBoxes += wpTextBox(lines, nameAt, m)
        }
        val wpLinePx = lineHeight(Tuning.WP_LABEL_SP, m)
        for (i in inFrame) {
            val wp = wps[i]
            val nameAt = names[i]
            val lines = linesOf(i)
            marks += WpMark(pts[i], wp.name, wpInk(wp, i == next), dashed = !wp.enabled, nameAt = nameAt, lines = lines, linePx = wpLinePx, next = i == next)
            obstacles += Box(pts[i], m.pointerSize * 0.6f, m.pointerSize * 0.6f)
            if (nameAt != null) obstacles += wpTextBox(lines, nameAt, m)
        }
        val arrows = mutableListOf<EdgeArrow>()
        for (i in shown) {
            val wp = wps[i]
            val ink = wpInk(wp, i == next)
            // 矢印は次の WP（マゼンタ）の分だけ。見えている所（TargetFrame の中。下に重ねた表示の下も含む）なら印だけで、
            // 上の重ねた表示・操作列の下か、描画の枠の外にあるときだけ矢印を出す
            if (seen.contains(pts[i])) {
                continue
            } else if (i == next) {
                // 矢印の枠の縁の、方位方向の点（三角は描かない）。文字はその内側（自機側）
                val a = HudGeometry.angleOf(proj.origin, pts[i])
                val at = inner.rayHit(proj.origin, a)
                // 3行: 距離・名前・方位（画面内の次の WP と同じ）
                val lines = linesOf(i)
                val box0 = arrowTextBox(lines, P(0f, 0f), m)
                // 自機への線（マゼンタ）と重ならないよう、線と直角に、文字の塊が線にかからない所までずらす
                val r = Math.toRadians(a)
                val clear = box0.hw * kotlin.math.abs(kotlin.math.cos(r)).toFloat() + box0.hh * kotlin.math.abs(kotlin.math.sin(r)).toFloat() +
                    m.arrowLabelLine * 0.25f
                // 置ける所がなければ、基準の位置（線の上の縁の点）に重ねて出す。どちらも見える範囲に寄せる
                val placed = placeArrowText(
                    lines,
                    HudGeometry.pointAt(HudGeometry.pointAt(at, a + 180, m.arrowTextGap), a + 90, clear),
                    a, inner.outer, obstacles, m,
                )
                val textAt = keepVisible(arrowTextBox(lines, placed ?: at, m), visibleFrame)
                obstacles += arrowTextBox(lines, textAt, m)
                arrows += EdgeArrow(
                    at = at, lines = lines, textAt = textAt, ink = ink,
                    linePx = lineHeight(Tuning.WP_ARROW_LABEL_SP, m),
                )
            }
        }
        return marks to arrows
    }

    /** 方位目盛り・距離環の文字の占める矩形の目安。 */
    private fun labelBox(l: Label, m: HudMetrics) =
        Box(l.at, textHalfWidth(l.text, m) * (if (l.small) 1f else LABEL_WIDTH_RATIO) + 2f, m.compassLabelHalf)

    /** 文字などの占める矩形（中心と半幅・半高）[px]。 */
    internal data class Box(val c: P, val hw: Float, val hh: Float) {
        fun overlaps(o: Box): Boolean =
            kotlin.math.abs(c.x - o.c.x) < hw + o.hw && kotlin.math.abs(c.y - o.c.y) < hh + o.hh
    }

    /** WP の名前（1行）の位置。placeWpLines の1行の場合。 */
    internal fun placeWpName(name: String, at: P, ownShip: Box, m: HudMetrics, allowBelow: Boolean = true): P? =
        placeWpLines(listOf(name), at, ownShip, m, allowBelow)

    /**
     * WP の文字の塊（lines）の中心: 印の上（いちばん下の行の中心が印から wpNameOffset）。自機の記号と重なるなら下
     * （いちばん上の行が印から wpNameOffset）、それでも重なるなら null（文字を描かない）。
     * 到達済みの WP（allowBelow = false）は下へ逃がさず、重なるなら描かない（PAN 中も同じ）。
     */
    internal fun placeWpLines(lines: List<String>, at: P, ownShip: Box, m: HudMetrics, allowBelow: Boolean = true): P? {
        val d = wpLinesOffset(lines, m)
        return listOfNotNull(P(at.x, at.y - d), P(at.x, at.y + d).takeIf { allowBelow })
            .firstOrNull { !wpTextBox(lines, it, m).overlaps(ownShip) }
    }

    /** WP の文字の塊の中心と印の縦の距離（いちばん下の行（上に置くとき）の中心が印から wpNameOffset） */
    private fun wpLinesOffset(lines: List<String>, m: HudMetrics): Float = m.wpNameOffset + (lines.size - 1) * lineHeight(Tuning.WP_LABEL_SP, m) / 2

    /** WP の文字の基準の位置: 印の上（placeWpLines の最初の候補） */
    internal fun wpLinesAbove(lines: List<String>, at: P, m: HudMetrics): P = P(at.x, at.y - wpLinesOffset(lines, m))

    /**
     * 文字の塊 b を、見える範囲 frame（数値の下・WP ボタン列の上・画面の左右の中。操作列の所は除く）に入るよう寄せた中心。
     * 次の WP の文字を消さないために使う（§6.1）。中に入っていれば動かさない。枠より大きい文字は枠の左上に合わせる。
     */
    internal fun keepVisible(b: Box, frame: TargetFrame): P {
        val o = frame.outer
        var x = b.c.x.coerceIn(o.left + b.hw, maxOf(o.left + b.hw, o.right - b.hw))
        val y = b.c.y.coerceIn(o.top + b.hh, maxOf(o.top + b.hh, o.bottom - b.hh))
        // 操作列（右の切り欠き）にかかるなら、その左へ
        frame.notch?.let { n ->
            if (x + b.hw > n.left && y + b.hh > n.top && y - b.hh < n.bottom) x = maxOf(o.left + b.hw, n.left - b.hw)
        }
        return P(x, y)
    }

    /**
     * 矢印の文字の塊の位置。枠からはみ出さないよう詰め、文字を置かない所（自機・方位目盛り・先に置いた文字）と重なるなら、
     * 自機側・線と直角の両側へ、塊の高さずつずらした候補を順に試す（最大3つ分）。どれも重なるなら null（呼ぶ側が基準の位置に置く）。
     */
    private fun placeArrowText(lines: List<String>, start: P, angleDeg: Double, frame: HudRect, obstacles: List<Box>, m: HudMetrics): P? {
        val b = arrowTextBox(lines, start, m)
        fun clamp(p: P) = P(
            p.x.coerceIn(frame.left + b.hw, maxOf(frame.left + b.hw, frame.right - b.hw)),
            p.y.coerceIn(frame.top + b.hh, maxOf(frame.top + b.hh, frame.bottom - b.hh)),
        )
        val step = b.hh * 2
        val candidates = sequence {
            yield(start)
            for (n in 1..Tuning.ARROW_TEXT_MAX_SHIFT_LINES) {
                yield(HudGeometry.pointAt(start, angleDeg + 180, step * n))
                yield(HudGeometry.pointAt(start, angleDeg + 90, step * n))
                yield(HudGeometry.pointAt(start, angleDeg - 90, step * n))
            }
        }.map(::clamp)
        return candidates.firstOrNull { p -> obstacles.none { it.overlaps(b.copy(c = p)) } }
    }

    /** 文字の幅の半分の目安（HUD_TEXT_METRICS_SP の文字で）。全角（日本語など）は半角2文字分として数える。 */
    internal fun textHalfWidth(text: String, m: HudMetrics): Float =
        text.sumOf { c -> if (c.code >= 0x2E80) 2 else 1 }.toInt() * m.labelCharWidth / 2

    /** 文字の大きさ sp の1行の高さ [px]。 */
    internal fun lineHeight(sp: Float, m: HudMetrics): Float = m.arrowLabelLine * sp / Tuning.HUD_TEXT_METRICS_SP

    /** 文字の大きさ sp の複数行の塊（中心 at）の箱。幅はいちばん長い行、高さは行数分。 */
    internal fun textBlockBox(lines: List<String>, at: P, sp: Float, m: HudMetrics): Box {
        val k = sp / Tuning.HUD_TEXT_METRICS_SP
        val hw = (lines.maxOfOrNull { textHalfWidth(it, m) } ?: 0f) * k
        return Box(at, hw, lines.size * lineHeight(sp, m) / 2)
    }

    /** 地図上の WP の文字（名前・方位）の箱。 */
    internal fun wpTextBox(lines: List<String>, at: P, m: HudMetrics) = textBlockBox(lines, at, Tuning.WP_LABEL_SP, m)

    /** 画面外の矢印の文字（距離・名前・方位）の箱。 */
    internal fun arrowTextBox(lines: List<String>, at: P, m: HudMetrics) = textBlockBox(lines, at, Tuning.WP_ARROW_LABEL_SP, m)

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

    /** 距離環の数の上限（描画の枠の四隅まで描くときの安全のため） */
    private const val MAX_RINGS = 200

    /** o から描画の枠の一番遠い角までの距離 [px]。 */
    private fun farthestCornerPx(o: P, rect: HudRect): Float = listOf(
        P(rect.left, rect.top), P(rect.right, rect.top), P(rect.left, rect.bottom), P(rect.right, rect.bottom),
    ).maxOf { HudGeometry.dist(o, it) }

    /** 方位目盛りの文字（13sp）と、labelCharWidth を測った文字（11sp）の幅の比 */
    private const val LABEL_WIDTH_RATIO = Tuning.LABEL_SP / Tuning.HUD_TEXT_METRICS_SP
}
