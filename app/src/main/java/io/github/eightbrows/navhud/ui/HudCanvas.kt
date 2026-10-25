package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.view.HudScene
import io.github.eightbrows.navhud.core.view.LineDash
import io.github.eightbrows.navhud.core.view.MapLabel
import io.github.eightbrows.navhud.core.view.MapMark
import io.github.eightbrows.navhud.core.view.MarkShape
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import io.github.eightbrows.navhud.core.view.HudViewport
import io.github.eightbrows.navhud.core.view.Ink
import io.github.eightbrows.navhud.core.view.P
import io.github.eightbrows.navhud.core.view.Polyline
import io.github.eightbrows.navhud.core.view.PinchSteps
import io.github.eightbrows.navhud.core.view.TouchPoint
import io.github.eightbrows.navhud.core.view.TwoFingerTap
import io.github.eightbrows.navhud.core.view.Segment
import io.github.eightbrows.navhud.core.view.WpMark
import kotlin.math.roundToInt

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
    /** 背景地図（§8）。null なら描かない */
    baseMap: BaseMap? = null,
    /** 地図のダブルタップ（20261023-D02）: タップした所 [px]。そこを基準に 1段詳細にする */
    onDoubleTap: (Float, Float) -> Unit = { _, _ -> },
    /** 地図の 2 本指のタップ（20261024-D09。core の TwoFingerTap で見分ける）: − と同じ */
    onTwoFingerTap: () -> Unit = {},
    /**
     * 地図の操作（ドラッグの PAN・ピンチ・ダブルタップ・2 本指のタップ）を受け付ける所 [px]（20261024-D01）。上部バーと数値の欄より下、
     * WP ボタン列より上、再生の操作列より右。ここの外は地図の操作を受け付けず、重ねたボタンが受け取る。null は全体
     */
    gestureArea: HudRect? = null,
    /** gestureArea の中でも、地図の操作を始めない所 [px]（右の操作列。ボタンの間のすき間で地図が動かないように） */
    gestureBlocked: List<HudRect> = emptyList(),
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
    val scene = remember(state, size, metrics, reserved, numberBands, buttonBoxes, baseMap) {
        if (size == IntSize.Zero) null
        else HudSceneBuilder.build(state, HudRect(0f, 0f, size.width.toFloat(), size.height.toFloat()), metrics, reserved, numberBands, buttonBoxes, baseMap)
    }
    val textMeasurer = rememberTextMeasurer()
    val panHandler by rememberUpdatedState(onPan)
    val pinchHandler by rememberUpdatedState(onPinch)
    val doubleTapHandler by rememberUpdatedState(onDoubleTap)
    val twoFingerTapHandler by rememberUpdatedState(onTwoFingerTap)
    // 今のタッチ（最初の指を置いてから全部離すまで）で指が2本以上になったか。なったら、そのタッチの間はドラッグで PAN しない
    val touch = remember { TouchState() }
    val blockedNow by rememberUpdatedState(gestureBlocked)
    Box(modifier.clipToBounds().onSizeChanged { size = it }) {
        Canvas(Modifier.fillMaxSize()) {
            scene?.let { drawScene(it, textMeasurer, density, state.settings.buttonOpacityPct / 100f, ringLabelStyle(state.settings.ringLabelScalePct)) }
        }
        // 地図の操作を受け付ける所（20261024-D01）: 描画の枠の全体ではなく、この枠だけに指の操作を付ける。
        // 上部バー・数値の欄・WP ボタン列などの上には地図の操作の部品がないので、そこのボタンは Android の標準の広さ（48dp）で押せる
        val area = gestureArea ?: HudRect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        if (area.width > 0f && area.height > 0f) {
            val left = area.left
            val top = area.top
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(LocalDensity.current) { area.width.toDp() }, with(LocalDensity.current) { area.height.toDp() })
                    .gestures(touch, { blockedNow.any { b -> b.contains(P(it.x + left, it.y + top)) } }, pinchHandler, panHandler, { twoFingerTapHandler() }) { x, y -> doubleTapHandler(x + left, y + top) },
            )
        }
    }
}

/**
 * 地図の操作（PAN・ピンチ・ダブルタップ・2 本指のタップ）。blocked(最初の指の位置) が true のタッチは、地図の操作にしない（全部消費して、下の 2 つにも渡さない）。
 * 位置はこの部品の中の座標
 */
private fun Modifier.gestures(
    touch: TouchState,
    blocked: (Offset) -> Boolean,
    pinchHandler: (Int) -> Unit,
    panHandler: (Float, Float) -> Unit,
    twoFingerTapHandler: () -> Unit,
    doubleTapHandler: (Float, Float) -> Unit,
): Modifier = this
    // 2本指のピンチで縮尺を1段ずつ変える（§6.12。段の数は core の PinchSteps）。下のドラッグより先に（Initial で）見て、
    // 指が2本以上の間はその動きを消費する。1本に戻っても、指を全部離すまでドラッグ（PAN）はしない。
    // 指が1本だけのタッチでは何も消費しないので、1本指の操作は今まで通り。
    // 2 本指のタップ（20261024-D09）: 同じタッチの指の動きを TwoFingerTap に渡し、指を全部離したときにタップだったら − と同じにする
    .pointerInput(Unit) {
        awaitEachGesture {
            touch.pinched = false
            val pinch = PinchSteps()
            val tap = TwoFingerTap(viewConfiguration.touchSlop)
            var first = true
            var skip = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // 地図の操作を始めない所（右の操作列のすき間など）で置いた指のタッチは、離すまで全部消費する
                if (first) {
                    skip = event.changes.firstOrNull { it.pressed }?.let { blocked(it.position) } ?: false
                    first = false
                }
                if (skip) {
                    event.changes.forEach { it.consume() }
                    continue
                }
                tap.onEvent(event.changes.maxOf { it.uptimeMillis }, event.changes.map { TouchPoint(it.id.value, it.position.x, it.position.y, it.pressed) })
                val down = event.changes.filter { it.pressed }
                if (down.size >= 2) {
                    touch.pinched = true
                    // 開き具合: 指の重心から各指までの平均の距離（置いたばかりの指も、今の位置で数える）
                    val steps = pinch.update(PinchSteps.span(down.map { P(it.position.x, it.position.y) }))
                    if (steps != 0) {
                        tap.onPinchStep()
                        pinchHandler(steps)
                    }
                    event.changes.forEach { it.consume() }
                } else {
                    pinch.reset()
                }
            } while (event.changes.any { it.pressed })
            if (!skip && tap.isTap) twoFingerTapHandler()
        }
    }
    // ドラッグで地図を平行移動（PAN）。指の動きの量 [px] をそのまま渡す
    .pointerInput(Unit) {
        detectDragGestures { change, drag ->
            change.consume()
            if (!touch.pinched) panHandler(drag.x, drag.y)
        }
    }
    // ダブルタップ（20261023-D02）: 1本指で 2 回タップ。ドラッグ（PAN）・ピンチで指が動いた・2本になったタッチは、
    // 上の 2 つがその動きを消費するので、タップにならない（途中で取り消される）。1 回のタップには何も割り当てていない
    .pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { if (!touch.pinched) doubleTapHandler(it.x, it.y) })
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

/**
 * 飛行機の記号（上から見た形。機首が上）の右半分の点。記号の半分の大きさを 1 とした位置（下が +）。
 * 機首 → 胴体 → 主翼 → 胴体 → 水平尾翼 → 尾の中央。左半分は左右を入れ替えて逆の順にたどる
 */
private val PLANE_RIGHT_HALF = floatArrayOf(
    0f, -1.5f, 0.22f, -1.2f, 0.22f, -0.35f, 1.5f, 0.25f, 1.5f, 0.55f, 0.22f, 0.2f,
    0.22f, 0.9f, 0.65f, 1.2f, 0.65f, 1.45f, 0f, 1.3f,
)

/** 飛行機の記号の輪郭（右半分 → 左半分）の x, y の並び。描くたびに作らないよう、前もって作っておく */
private val PLANE_OUTLINE: FloatArray = run {
    val n = PLANE_RIGHT_HALF.size / 2
    val left = (n - 2 downTo 1).flatMap { listOf(-PLANE_RIGHT_HALF[2 * it], PLANE_RIGHT_HALF[2 * it + 1]) }
    PLANE_RIGHT_HALF + left.toFloatArray()
}

/**
 * 白抜きの飛行機（軍用の空港。20261024-D01）の右半分の点。輪郭だけを描くので、細い所が線で埋まらないよう、胴体・翼・尾を太くした形
 * （胴体の幅 0.72、翼の付け根の幅 0.8。PLANE_RIGHT_HALF は 0.44・0.6）
 */
private val FAT_PLANE_RIGHT_HALF = floatArrayOf(
    0f, -1.5f, 0.36f, -1.1f, 0.36f, -0.45f, 1.5f, 0.2f, 1.5f, 0.65f, 0.36f, 0.35f,
    0.36f, 0.85f, 0.75f, 1.15f, 0.75f, 1.5f, 0f, 1.38f,
)

/** 白抜きの飛行機の輪郭の x, y の並び */
private val FAT_PLANE_OUTLINE: FloatArray = run {
    val n = FAT_PLANE_RIGHT_HALF.size / 2
    val left = (n - 2 downTo 1).flatMap { listOf(-FAT_PLANE_RIGHT_HALF[2 * it], FAT_PLANE_RIGHT_HALF[2 * it + 1]) }
    FAT_PLANE_RIGHT_HALF + left.toFloatArray()
}

/** 飛行機の記号の幅（翼の端から端まで）。PLANE_RIGHT_HALF の単位で 3（-1.5〜1.5） */
private const val PLANE_SPAN = 3f

/** 正三角形の高さ ÷ 底辺 */
private const val TRIANGLE_HEIGHT = 0.866f

/**
 * 背景地図の空港の敷地（20261022-D01）: 輪を 1 つの Path にまとめ、半透明で 1 回で塗る（重なっても濃くならない。穴は偶奇の規則で抜ける）。
 * 色はランドマークの明るさの段（brightness）を HudColors.landmark で掛ける
 */
private fun DrawScope.drawMapAreas(areas: List<Polyline>) {
    var i0 = 0
    while (i0 < areas.size) {
        val first = areas[i0]
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        var i = i0
        while (i < areas.size && areas[i].ink == first.ink && areas[i].brightness == first.brightness) {
            val pts = areas[i].points
            path.moveTo(pts[0].x, pts[0].y)
            for (k in 1 until pts.size) path.lineTo(pts[k].x, pts[k].y)
            path.close()
            i++
        }
        drawPath(path, HudColors.landmark(first.ink, first.brightness))
        i0 = i
    }
}

/**
 * 背景地図の滑走路と延長線（20261024-D04）: 滑走路は実線（端は切りっぱなし）、延長線は短い破線。色はランドマークの明るさの段（brightness）を掛ける。
 * 数が少ない（全国で 132 本）ので、1 本ずつ描く
 */
private fun DrawScope.drawMapRunways(lines: List<Polyline>, density: Float) {
    if (lines.isEmpty()) return
    val dash = PathEffect.dashPathEffect(floatArrayOf(Tuning.BASEMAP_RUNWAY_EXT_DASH_DP * density, Tuning.BASEMAP_RUNWAY_EXT_DASH_DP * density))
    for (l in lines) {
        val a = l.points.first()
        val b = l.points.last()
        drawLine(
            HudColors.landmark(l.ink, l.brightness), Offset(a.x, a.y), Offset(b.x, b.y),
            strokeWidth = l.widthDp * density, cap = StrokeCap.Butt, pathEffect = if (l.dash == LineDash.SHORT_DASH) dash else null,
        )
    }
}

/** 空港の略号の文字（20261022-D01）: 記号の右上に、黒のふち（HUD の文字と同じ Tuning.TEXT_OUTLINE_DP）を付けて描く */
private fun DrawScope.drawMapLabels(labels: List<MapLabel>, tm: TextMeasurer, density: Float) {
    if (labels.isEmpty()) return
    val outline = Stroke(Tuning.TEXT_OUTLINE_DP * density, join = StrokeJoin.Round)
    for (l in labels) {
        val layout = tm.measure(l.text, MapCodeStyle)
        val off = l.offsetDp * density
        val topLeft = Offset(l.at.x + off, l.at.y - off - layout.size.height)
        drawText(layout, color = HudColors.Background, topLeft = topLeft, drawStyle = outline)
        // 塗りは Fill を明示する（省くと、前の呼び出しの線の描き方が残って、文字が太い線になる）
        drawText(layout, color = HudColors.landmark(l.ink, l.brightness), topLeft = topLeft, drawStyle = Fill)
    }
}

/** 空港の略号の文字 */
private val MapCodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = Tuning.BASEMAP_AIRPORT_CODE_SP.sp)

/**
 * 背景地図の点（§8。ランドマークの記号。名前は描かない）: 空港の飛行機・山頂の三角（20261022-D01 で丸をなくした）。
 * 背景の線の上でも形が分かるよう、先に黒のふち（太い線）を描いてから塗る（20261021-D01）。
 * 空港は 20261024-D01 で民間・軍用に分けた: 民間（大）は塗りつぶしにまわりの丸い輪、民間（小）は塗りつぶし、
 * 軍用は白抜き（内側を背景の黒で塗り、輪郭だけ色の線。外側に黒のふち）。
 * 色は線の明るさの段によらず、ランドマークの明るさの段（brightness）だけで変わる。同じ色・形・明るさが続く記号を 1 つの Path にまとめて 1 回で描く
 * （記号ごとに物を作らない。記号が多いときに遅くなるため）
 */
private fun DrawScope.drawMapMarks(marks: List<MapMark>, density: Float) {
    var i0 = 0
    while (i0 < marks.size) {
        val first = marks[i0]
        val path = Path()
        // 民間（大）の丸い輪
        val rings = Path()
        var i = i0
        while (i < marks.size) {
            val m = marks[i]
            if (m.ink != first.ink || m.shape != first.shape || m.brightness != first.brightness) break
            i++
            val x = m.at.x
            val y = m.at.y
            when (m.shape) {
                MarkShape.PLANE, MarkShape.PLANE_RING, MarkShape.PLANE_HOLLOW -> {
                    val r = m.sizeDp / PLANE_SPAN * density
                    val o = if (m.shape == MarkShape.PLANE_HOLLOW) FAT_PLANE_OUTLINE else PLANE_OUTLINE
                    path.moveTo(x + o[0] * r, y + o[1] * r)
                    for (k in 2 until o.size step 2) path.lineTo(x + o[k] * r, y + o[k + 1] * r)
                    path.close()
                    if (m.shape == MarkShape.PLANE_RING) {
                        val rr = Tuning.BASEMAP_AIRPORT_RING_RADIUS_DP * density
                        rings.addOval(Rect(x - rr, y - rr, x + rr, y + rr))
                    }
                }
                MarkShape.TRIANGLE -> {
                    // 頂点が上。at は高さの真ん中
                    val half = m.sizeDp / 2 * density
                    val h = m.sizeDp * TRIANGLE_HEIGHT * density
                    path.moveTo(x, y - h / 2)
                    path.lineTo(x + half, y + h / 2)
                    path.lineTo(x - half, y + h / 2)
                    path.close()
                }
            }
        }
        val color = HudColors.landmark(first.ink, first.brightness)
        val outline = when (first.shape) {
            MarkShape.TRIANGLE -> Tuning.BASEMAP_PEAK_OUTLINE_DP
            else -> Tuning.BASEMAP_AIRPORT_OUTLINE_DP
        } * density
        when (first.shape) {
            MarkShape.PLANE_HOLLOW -> {
                // 白抜き: 輪郭の線（幅 line）の外側に黒のふち、内側は背景の黒で塗る（下の線が透けて形が崩れないように）
                val line = Tuning.BASEMAP_AIRPORT_HOLLOW_LINE_DP * density
                drawPath(path, HudColors.Background, style = Stroke(line + outline * 2, join = StrokeJoin.Round))
                drawPath(path, HudColors.Background)
                drawPath(path, color, style = Stroke(line, join = StrokeJoin.Round))
            }
            else -> {
                if (first.shape == MarkShape.PLANE_RING) {
                    val ringLine = Tuning.BASEMAP_AIRPORT_RING_LINE_DP * density
                    drawPath(rings, HudColors.Background, style = Stroke(ringLine + outline * 2))
                    drawPath(rings, color, style = Stroke(ringLine))
                }
                drawPath(path, HudColors.Background, style = Stroke(outline * 2, join = StrokeJoin.Round))
                drawPath(path, color)
            }
        }
        i0 = i
    }
}

private fun DrawScope.drawScene(scene: HudScene, tm: TextMeasurer, density: Float, buttonAlpha: Float, ringStyle: TextStyle) {
    val thin = Tuning.LINE_THIN_DP * density
    val bold = Tuning.LINE_BOLD_DP * density
    val dash = PathEffect.dashPathEffect(floatArrayOf(Tuning.DASH_ON_DP * density, Tuning.DASH_OFF_DP * density))
    val longDash = PathEffect.dashPathEffect(floatArrayOf(Tuning.ACTIVE_DASH_ON_DP * density, Tuning.ACTIVE_DASH_OFF_DP * density))

    drawRect(HudColors.Background)

    // 背景地図（いちばん下。距離環・方位の目盛り・軌跡・WP の線より下。§8）。線はレイヤの描く順に並んでいる。点は線の上
    val dashes = mapOf(
        LineDash.DOT to PathEffect.dashPathEffect(floatArrayOf(Tuning.BASEMAP_PREF_DASH_ON_DP * density, Tuning.BASEMAP_PREF_DASH_OFF_DP * density)),
        LineDash.FINE_DOT to PathEffect.dashPathEffect(floatArrayOf(Tuning.BASEMAP_FINE_DOT_ON_DP * density, Tuning.BASEMAP_FINE_DOT_OFF_DP * density)),
        LineDash.DASH to PathEffect.dashPathEffect(floatArrayOf(Tuning.BASEMAP_DASH_ON_DP * density, Tuning.BASEMAP_DASH_OFF_DP * density)),
        LineDash.LONG_DASH to PathEffect.dashPathEffect(floatArrayOf(Tuning.BASEMAP_LONG_DASH_ON_DP * density, Tuning.BASEMAP_LONG_DASH_OFF_DP * density)),
    )
    // 線はレイヤの順に並んでいるので、同じ見た目（色・太さ・線の種類）が続く線を 1 つの Path にまとめて 1 回で描く（線が多いときの描く手間を減らす）
    var i0 = 0
    while (i0 < scene.baseMap.size) {
        val first = scene.baseMap[i0]
        val path = Path()
        var i = i0
        while (i < scene.baseMap.size) {
            val l = scene.baseMap[i]
            if (l.ink != first.ink || l.widthDp != first.widthDp || l.dash != first.dash || l.brightness != first.brightness) break
            path.moveTo(l.points[0].x, l.points[0].y)
            for (k in 1 until l.points.size) path.lineTo(l.points[k].x, l.points[k].y)
            i++
        }
        drawPath(path, HudColors.ofMap(first.ink, first.brightness), style = Stroke(first.widthDp * density, join = StrokeJoin.Round, pathEffect = dashes[first.dash]))
        i0 = i
    }
    // ランドマーク（線の上）: 空港の敷地 → 記号 → 空港の略号（20261022-D01）
    drawMapAreas(scene.baseMapAreas)
    // 滑走路と延長線（20261024-D04）: 敷地の上、記号の下
    drawMapRunways(scene.baseMapRunways, density)
    drawMapMarks(scene.baseMapMarks, density)
    drawMapLabels(scene.baseMapLabels, tm, density)

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

    // PAN のときの地図の中心の十字（20261023-D01）: 自機より下、ほかの全部より上
    scene.crosshair?.let { drawCrosshair(it, density) }

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

/**
 * PAN のときの地図の中心の十字（20261023-D01）。中心を Tuning.CROSSHAIR_GAP_DP だけあけた 4 本の細い線（腕の外の端は中心から
 * Tuning.CROSSHAIR_ARM_DP）。色は UI の色（テーマの基本色）、背景の線の上でも見えるよう、先に黒のふちを描く。
 * 自機（三角・丸）・WP の印（丸・ひし形）と違い、線だけで、中心が抜けている
 */
private fun DrawScope.drawCrosshair(at: P, density: Float) {
    val gap = Tuning.CROSSHAIR_GAP_DP * density
    val arm = Tuning.CROSSHAIR_ARM_DP * density
    val path = Path().apply {
        moveTo(at.x - arm, at.y); lineTo(at.x - gap, at.y)
        moveTo(at.x + gap, at.y); lineTo(at.x + arm, at.y)
        moveTo(at.x, at.y - arm); lineTo(at.x, at.y - gap)
        moveTo(at.x, at.y + gap); lineTo(at.x, at.y + arm)
    }
    val w = Tuning.CROSSHAIR_LINE_DP * density
    drawPath(path, HudColors.Background, style = Stroke(w + 2 * Tuning.CROSSHAIR_OUTLINE_DP * density, cap = StrokeCap.Round))
    drawPath(path, HudColors.Scale, style = Stroke(w, cap = StrokeCap.Butt))
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
