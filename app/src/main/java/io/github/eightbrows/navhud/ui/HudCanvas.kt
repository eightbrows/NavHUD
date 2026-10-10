package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudScene
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import io.github.eightbrows.navhud.core.view.HudViewport
import io.github.eightbrows.navhud.core.view.Ink
import io.github.eightbrows.navhud.core.view.P
import io.github.eightbrows.navhud.core.view.PinchSteps
import io.github.eightbrows.navhud.core.view.Segment
import io.github.eightbrows.navhud.core.view.WpMark

/** HUD の図。座標は core（HudSceneBuilder）で計算済みのものを描くだけ。 */
@Composable
fun HudCanvas(
    state: NavState,
    modifier: Modifier = Modifier,
    reserved: HudInsets = HudInsets(),
    onViewport: (HudViewport) -> Unit = {},
    onPan: (Float, Float) -> Unit = { _, _ -> },
    /** ピンチ: 縮尺を変える段の数（正: 詳細へ = ＋、負: 広域へ = −） */
    onPinch: (Int) -> Unit = {},
    /** 数値の表示の上下の範囲 [px]。WP の名前・矢印の文字を重ねない */
    numberBands: List<ClosedFloatingPointRange<Float>> = emptyList(),
    /** ボタン類（上部バー・操作列・WP ボタン列・再生の操作列）の矩形 [px]。WP の名前・矢印の文字を重ねない */
    buttonBoxes: List<HudRect> = emptyList(),
) {
    val density = LocalDensity.current.density
    val metrics = remember(density) { HudMetrics().scaled(density) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // 描画の枠と重ねた表示の大きさを AUTO 縮尺の判定に渡す（矢印と AUTO の枠に次の WP が収まる最小の段）
    LaunchedEffect(size, metrics, reserved) {
        if (size != IntSize.Zero) {
            onViewport(HudViewport(HudRect(0f, 0f, size.width.toFloat(), size.height.toFloat()), metrics, reserved))
        }
    }
    val scene = remember(state, size, metrics, reserved, numberBands, buttonBoxes) {
        if (size == IntSize.Zero) null
        else HudSceneBuilder.build(state, HudRect(0f, 0f, size.width.toFloat(), size.height.toFloat()), metrics, reserved, numberBands, buttonBoxes)
    }
    val textMeasurer = rememberTextMeasurer()
    val panHandler by rememberUpdatedState(onPan)
    val pinchHandler by rememberUpdatedState(onPinch)
    // 今のタッチ（最初の指を置いてから全部離すまで）で指が2本以上になったか。なったら、そのタッチの間はドラッグで PAN しない
    val touch = remember { TouchState() }
    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            // 2本指のピンチで縮尺を1段ずつ変える（§6.12。段の数は core の PinchSteps）。下のドラッグより先に（Initial で）見て、
            // 指が2本以上の間はその動きを消費する。1本に戻っても、指を全部離すまでドラッグ（PAN）はしない。
            // 指が1本だけのタッチでは何も消費しないので、1本指の操作は今まで通り
            .pointerInput(Unit) {
                awaitEachGesture {
                    touch.pinched = false
                    val pinch = PinchSteps()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            touch.pinched = true
                            // 開き具合: 指の重心から各指までの平均の距離（置いたばかりの指も、今の位置で数える）
                            val steps = pinch.update(PinchSteps.span(down.map { P(it.position.x, it.position.y) }))
                            if (steps != 0) pinchHandler(steps)
                            event.changes.forEach { it.consume() }
                        } else {
                            pinch.reset()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // ドラッグで地図を平行移動（PAN）。指の動きの量 [px] をそのまま渡す
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    if (!touch.pinched) panHandler(drag.x, drag.y)
                }
            },
    ) {
        scene?.let { drawScene(it, textMeasurer, density, state.settings.buttonOpacityPct / 100f, ringLabelStyle(state.settings.ringLabelScalePct)) }
    }
}

/** 1回のタッチの間の状態（ピンチとドラッグで共有） */
private class TouchState {
    var pinched = false
}

private val LabelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = Tuning.LABEL_SP.sp)

/** WP の文字: 地図上の名前・方位 / 画面外の矢印の距離・名前・方位 */
private val WpLabelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = Tuning.WP_LABEL_SP.sp)
private val WpArrowLabelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = Tuning.WP_ARROW_LABEL_SP.sp)

/** 距離環の数字: Tuning.RING_LABEL_SP に設定の大きさ（ringLabelScalePct）を掛ける */
private fun ringLabelStyle(scalePct: Int) = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (Tuning.RING_LABEL_SP * scalePct / 100f).sp)

private fun P.o() = Offset(x, y)

private fun DrawScope.drawScene(scene: HudScene, tm: TextMeasurer, density: Float, buttonAlpha: Float, ringStyle: TextStyle) {
    val thin = Tuning.LINE_THIN_DP * density
    val bold = Tuning.LINE_BOLD_DP * density
    val dash = PathEffect.dashPathEffect(floatArrayOf(Tuning.DASH_ON_DP * density, Tuning.DASH_OFF_DP * density))
    val longDash = PathEffect.dashPathEffect(floatArrayOf(Tuning.ACTIVE_DASH_ON_DP * density, Tuning.ACTIVE_DASH_OFF_DP * density))

    drawRect(HudColors.Background)

    for (a in scene.arcs) {
        drawArc(
            color = HudColors.ofMap(a.ink),
            // core の角度は「上が 0」、Canvas は「右が 0」
            startAngle = a.startDeg - 90f,
            sweepAngle = a.sweepDeg,
            useCenter = false,
            topLeft = Offset(a.center.x - a.radius, a.center.y - a.radius),
            size = Size(a.radius * 2, a.radius * 2),
            style = Stroke(thin),
        )
    }
    // 軌跡（WP の線・印より下）
    for (t in scene.trails) {
        val path = Path().apply {
            moveTo(t.points[0].x, t.points[0].y)
            for (i in 1 until t.points.size) lineTo(t.points[i].x, t.points[i].y)
        }
        drawPath(path, HudColors.ofMap(t.ink), style = Stroke(t.widthDp * density, join = StrokeJoin.Round))
    }
    fun segment(s: Segment) = drawLine(
        color = HudColors.ofMap(s.ink),
        start = s.a.o(),
        end = s.b.o(),
        strokeWidth = if (s.bold) bold else thin,
        pathEffect = when {
            s.longDash -> longDash
            s.dashed -> dash
            else -> null
        },
    )
    // WP の印（ひし形）と文字（名前。次の WP は 距離・名前・方位）。文字は core が決めた位置（null なら描かない）
    fun wpMark(w: WpMark) {
        val c = HudColors.ofMap(w.ink)
        val r = Tuning.WP_MARK_DP * density
        val path = Path().apply {
            moveTo(w.at.x, w.at.y - r)
            lineTo(w.at.x + r, w.at.y)
            lineTo(w.at.x, w.at.y + r)
            lineTo(w.at.x - r, w.at.y)
            close()
        }
        drawPath(path, c, style = Stroke(bold, pathEffect = if (w.dashed) dash else null))
        w.nameAt?.let { drawLines(tm, w.lines, it, w.linePx, c, WpLabelStyle) }
    }

    // 線: 方位目盛り・方位線・ラバーライン・WP を結ぶ線（次の WP への線は、あとで上に描く）
    for (s in scene.baseSegments) segment(s)
    for (l in scene.labels) {
        // 距離環の数字は UI の色で、不透明度はボタンと同じ
        val c = HudColors.ofMap(l.ink).let { if (l.ink == Ink.RING_LABEL) it.copy(alpha = it.alpha * buttonAlpha) else it }
        drawLabel(tm, l.text, l.at, c, if (l.small) ringStyle else LabelStyle)
    }

    // 次の WP 以外の WP の印と名前
    for (w in scene.otherWpMarks) wpMark(w)
    // 方位の三角（ARC の上部の方位マーカー、North Up の機首方位の三角）
    for (p in scene.pointers) triangle(p.tip, p.angleDeg, p.sizePx, HudColors.ofMap(p.ink), filled = false, stroke = bold)
    // 次の WP の情報は、方位の三角・方位目盛りの文字・距離環の数字より上に描く（§6.1）:
    // 自機からの線 → 印と文字（距離・名前・方位）→ 画面外のときの文字（三角は描かない）
    for (s in scene.nextWpSegments) segment(s)
    for (w in scene.nextWpMarks) wpMark(w)
    for (a in scene.arrows) drawLines(tm, a.lines, a.textAt, a.linePx, HudColors.ofMap(a.ink), WpArrowLabelStyle)

    val own = scene.ownShip ?: return
    val oc = HudColors.ofMap(own.ink)
    if (own.angleDeg == null) {
        drawCircle(oc, radius = Tuning.OWN_SHIP_CIRCLE_DP * density, center = own.at.o(), style = Stroke(bold))
    } else {
        // 自機: 先端が機首方位を向く三角
        rotate(own.angleDeg, pivot = own.at.o()) {
            val s = Tuning.OWN_SHIP_DP * density
            val path = Path().apply {
                moveTo(own.at.x, own.at.y - s * 1.3f)
                lineTo(own.at.x + s * 0.8f, own.at.y + s * 0.8f)
                lineTo(own.at.x, own.at.y + s * 0.3f)
                lineTo(own.at.x - s * 0.8f, own.at.y + s * 0.8f)
                close()
            }
            drawPath(path, oc, style = Stroke(bold))
        }
    }
}

/** 先端 tip が angleDeg（上が 0、時計回り）を向く三角形。 */
private fun DrawScope.triangle(tip: P, angleDeg: Float, size: Float, color: Color, filled: Boolean = true, stroke: Float = 0f) {
    rotate(angleDeg, pivot = tip.o()) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + size * 0.6f, tip.y + size)
            lineTo(tip.x - size * 0.6f, tip.y + size)
            close()
        }
        if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(stroke))
    }
}

/** 複数行の文字の塊を、中心を at に合わせて描く（各行は横の中央ぞろえ、行の間隔は linePx）。 */
private fun DrawScope.drawLines(tm: TextMeasurer, lines: List<String>, at: P, linePx: Float, color: Color, style: TextStyle) {
    val top = at.y - (lines.size - 1) * linePx / 2
    lines.forEachIndexed { k, line -> drawLabel(tm, line, P(at.x, top + k * linePx), color, style) }
}

/** 文字の中心を at に合わせて描く。 */
internal fun DrawScope.drawLabel(tm: TextMeasurer, text: String, at: P, color: Color, style: TextStyle) {
    val layout = tm.measure(text, style.copy(color = color))
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
