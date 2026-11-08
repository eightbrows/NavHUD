package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.ProfileSize
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.replay.ReplaySpeed
import io.github.eightbrows.navhud.core.view.HudFormat
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudViewport
import io.github.eightbrows.navhud.core.view.MainLayout
import io.github.eightbrows.navhud.core.view.NumberField
import io.github.eightbrows.navhud.core.view.NumberLayout
import io.github.eightbrows.navhud.core.view.NumberRows
import io.github.eightbrows.navhud.core.view.RangeButtonFace
import io.github.eightbrows.navhud.core.view.TopBarFit
import io.github.eightbrows.navhud.core.view.WpStripLayout
import java.time.ZoneId
import kotlin.math.roundToInt

private val Caption get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HudColors.Caption)
private val Value get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val ButtonText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HudColors.Scale)

/**
 * メイン画面（§6.1〜6.4）。NavState だけを見て描く。
 * 地図はステータスバーの下から画面の下端まで、左右いっぱいに描き、ほかの表示はすべて地図の上に重ねる:
 * 上から 上部バー（HDG ｜ まん中に NO FIX ｜ ARC ｜ ⚙）・数値（4行）、右に操作列（回避枠の縦中央）、
 * 左に軌跡の操作の列（20261108-D02。数値の下。LIVE は ⇄ だけ、REPLAY は ⇄ / FILE / スライダー / 再生の操作で WP ボタン列の上まで）、
 * 下は WP ボタン列（いつも出す）・標高プロファイル。読み込みの知らせと NO FIX の案内は WP ボタン列のすぐ上。
 * ボタン類と標高プロファイルは半透明の地、数値は箱なしで文字に黒の縁取り。
 */
@Composable
fun MainScreen(
    state: NavState,
    replay: ReplayUiState,
    onPickTrack: () -> Unit,
    onTogglePlay: () -> Unit,
    onCycleSource: () -> Unit,
    onToggleDisplay: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenWpSettings: () -> Unit,
    onToggleReached: (Int) -> Unit,
    onPanToWaypoint: (Int) -> Unit,
    live: LiveUiState,
    onToggleSourceKind: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleAutoRange: () -> Unit,
    onPan: (Float, Float) -> Unit,
    onPinch: (Int) -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onSeek: (Long) -> Unit,
    onEndPan: () -> Unit,
    onOpenSettings: () -> Unit,
    onViewport: (HudViewport) -> Unit,
    modifier: Modifier = Modifier,
    /** 背景地図（§8）。読み終わるまでは null */
    baseMap: BaseMap? = null,
    /** 地図のダブルタップ（20261023-D02）: タップした所 [px] を基準に 1段詳細にする */
    onDoubleTap: (Float, Float) -> Unit = { _, _ -> },
    /** 地図の 2 本指のタップ（20261024-D09）: − と同じ */
    onTwoFingerTap: () -> Unit = {},
    /** 読み込んだ結果などのひとこと（20261103-D01）。数秒で消える。重なったら行を分けて全部出す。空なら出さない */
    notices: List<UiText> = emptyList(),
) {
    val zone = ZoneId.systemDefault()
    // NO FIX 中は最後の値をグレーで出し続ける
    val valueColor = if (state.noFix) HudColors.Stale else HudColors.Scale
    // 左の列が WP ボタン列の上までの高さを使うか（REPLAY）
    val trackFills = MainLayout.trackColumnFillsHeight(state.sourceKind)

    // 重ねた表示の大きさ [px]（上: 上部バー・数値、下: WP ボタン列から下 = WP ボタン列・標高プロファイル・ナビゲーションバー）
    var boxW by remember { mutableIntStateOf(0) }
    var boxPx by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }
    var topBarPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    var stripPx by remember { mutableIntStateOf(0) }
    var trackPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topDp = with(density) { topPx.toDp() }
    val bottomDp = with(density) { bottomPx.toDp() }
    // 右の操作列（＋ / RNG / −）は回避枠の縦中央。PAN 中の「現在地」は − のすぐ下に足す（＋ / RNG / − は動かさない）
    val sidePx = with(density) { SideColumnWidth.toPx() }
    val groupPx = with(density) { SideGroupHeight.toPx() }
    val sideTop = topPx + ((boxPx - bottomPx - topPx) - groupPx) / 2
    val sideBottom = sideTop + groupPx + if (state.pan != null) with(density) { SidePanExtra.toPx() } else 0f
    // 左の列（軌跡の操作）: 左端の「戻る」ジェスチャーの範囲（システムの値。3ボタンのナビゲーションなら 0）より
    // さらに内側。幅は右の操作列と同じ。REPLAY では地図は左の列の右から（矢印の文字・AUTO の判定の枠の左端）
    val gestureLeftPx = WindowInsets.systemGestures.getLeft(density, LocalLayoutDirection.current)
    val trackLeftPx = gestureLeftPx + with(density) { Tuning.REPLAY_COLUMN_EDGE_MARGIN_DP.dp.toPx() }
    val trackRightPx = trackLeftPx + sidePx
    val w = boxW.toFloat()
    val stripTop = (boxPx - bottomPx).toFloat()
    val trackRect = HudRect(trackLeftPx, topPx.toFloat(), trackRightPx, if (trackFills) stripTop else (topPx + trackPx).toFloat())
    val sideRect = HudRect(w - sidePx, sideTop, w, sideBottom)
    // 地図は描画の枠（この画面の全体）に描く。回避枠 = 上（上部バー・数値）と下（WP ボタン列から下）を除き、
    // 右は操作列のある高さの範囲だけ欠いた部分。左は REPLAY のとき左の列の右から（LIVE の ⇄ は数値のすぐ下の 1 つだけなので欠かない）
    val reserved = HudInsets(left = if (trackFills) trackRightPx else 0f, top = topPx.toFloat(), right = sidePx, bottom = bottomPx.toFloat(), rightSpan = sideTop..sideBottom)
    // WP の名前・矢印の文字を重ねない所: 数値の表示（上下の範囲）と、ボタン類（上部バー・右の操作列・WP ボタン列・左の列）
    val numberBands = listOf(topBarPx.toFloat()..topPx.toFloat())
    val buttonBoxes = listOf(
        HudRect(0f, 0f, w, topBarPx.toFloat()),
        sideRect,
        HudRect(0f, stripTop, w, stripTop + stripPx),
        trackRect,
    )
    // 地図の操作（PAN・ピンチ・ダブルタップ・2 本指のタップ）を受け付ける所: 上部バーと数値の欄（4 行）より下、WP ボタン列より上。
    // 左の列と右の操作列の所では、地図の操作を始めない（20261108-D02。core の MainLayout）
    val gestures = MainLayout.gestures(w, topPx.toFloat(), stripTop, trackRect, sideRect)

    Box(modifier.fillMaxSize().background(HudColors.Background).onSizeChanged { boxW = it.width; boxPx = it.height }) {
        HudCanvas(state, Modifier.fillMaxSize(), reserved, onViewport, onPan, onPinch, numberBands, buttonBoxes, baseMap, onDoubleTap = onDoubleTap, onTwoFingerTap = onTwoFingerTap, gestureArea = gestures.area, gestureBlocked = gestures.blocked)
        // 上: 上部バーと数値（4行）
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { topPx = it.height }) {
            Box(Modifier.onSizeChanged { topBarPx = it.height }) {
                TopBar(state, onCycleSource, onToggleDisplay, onOpenSettings, onOpenDebug)
            }
            NumbersPanel(state, zone, valueColor)
        }
        // 下: WP ボタン列（いつも出す。20261108-D02）・標高プロファイル・ナビゲーションバー
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().onSizeChanged { bottomPx = it.height }) {
            Box(Modifier.onSizeChanged { stripPx = it.height }) { WpStrip(state, onOpenWpSettings, onToggleReached, onPanToWaypoint) }
            profileHeight(state.settings.profileSize)?.let { h ->
                ProfileView(state, Modifier.fillMaxWidth().height(h))
            }
            Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        // 右の操作列: ＋ / RNG / −（PAN 中は「現在地」も）。回避枠の縦中央
        SideColumn(
            state, onZoomIn, onZoomOut, onToggleAutoRange, onEndPan,
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, sideTop.roundToInt()) },
        )
        // 左の列（軌跡の操作）: 数値の下から。REPLAY は WP ボタン列の上まで
        TrackColumn(
            state, replay, onToggleSourceKind, onPickTrack, onTogglePlay, onSlower, onFaster, onSeek,
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(trackLeftPx.roundToInt(), topPx) }
                .width(SideColumnWidth)
                .then(if (trackFills) Modifier.height(with(density) { (boxPx - bottomPx - topPx).coerceAtLeast(0).toDp() }) else Modifier)
                .onSizeChanged { trackPx = it.height },
        )
        // 位置情報の権限の案内: 回避枠の中央に置き、その幅で折り返す
        val permissionMissing = state.sourceKind == SourceKind.LIVE &&
            (live.permission == LocationPermission.DENIED || live.permission == LocationPermission.APPROXIMATE_ONLY)
        if (permissionMissing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = topDp, end = SideColumnWidth, bottom = bottomDp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                PermissionBox(live.permission, onRequestPermission, onOpenAppSettings, onToggleSourceKind)
            }
        }
        // WP ボタン列のすぐ上（20261108-D02）: NO FIX のときの案内（前は NO FIX の枠の中）と、読み込んだ結果などのひとこと（20261103-D01）。
        // ひとことと同じ文の案内は、ひとことが出ている間は出さない（同じ文を 2 つ並べない）
        val noticeTexts = notices.map { it.asString() }
        val hint = if (state.noFix && !permissionMissing) noFixHint(state, replay, live)?.takeIf { it !in noticeTexts } else null
        val boxes = listOfNotNull(
            hint?.let { it to HudColors.Caution },
            noticeTexts.takeIf { it.isNotEmpty() }?.let { it.joinToString("\n") to HudColors.Scale },
        )
        if (boxes.isNotEmpty() && boxW > 0) {
            NoticeArea(boxes, left = trackRightPx, width = w, bottom = stripTop, sideRect = sideRect)
        }
    }
}

/**
 * WP ボタン列のすぐ上の知らせ（20261108-D02）: 左の列の右から画面の右端までの幅に出し、長い文は折り返す。
 * その幅で右の操作列（− / 縮尺 / ＋）の高さまで届くときは、右の操作列の左までの幅にする（core の MainLayout.noticeRight）。
 * 上から 案内（黄色）、ひとこと（UI の色）。
 */
@Composable
private fun NoticeArea(boxes: List<Pair<String, Color>>, left: Float, width: Float, bottom: Float, sideRect: HudRect) {
    val density = LocalDensity.current
    val tm = rememberTextMeasurer()
    val m = with(density) { Tuning.NOTICE_MARGIN_H_DP.dp.toPx() }
    val padH = with(density) { (NoticePadH + 1.dp).toPx() * 2 }
    val padV = with(density) { (NoticePadV + 1.dp).toPx() * 2 }
    val gap = with(density) { Tuning.NOTICE_GAP_DP.dp.toPx() }
    val bottomPx = bottom - with(density) { Tuning.NOTICE_MARGIN_BOTTOM_DP.dp.toPx() }
    val l = left + m
    val r = MainLayout.noticeRight(l, width - m, bottomPx, sideRect.copy(left = sideRect.left - m)) { wPx ->
        val inner = (wPx - padH).roundToInt().coerceAtLeast(1)
        boxes.sumOf { (t, _) -> tm.measure(t, NoticeStyle, constraints = Constraints(maxWidth = inner)).size.height.toDouble() + padV }.toFloat() +
            gap * (boxes.size - 1)
    }
    Box(
        Modifier
            .offset { IntOffset(l.roundToInt(), 0) }
            .width(with(density) { (r - l).coerceAtLeast(0f).toDp() })
            .height(with(density) { bottomPx.coerceAtLeast(0f).toDp() }),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tuning.NOTICE_GAP_DP.dp)) {
            boxes.forEach { (t, c) -> NoticeBox(t, c) }
        }
    }
}

/** 右の操作列の幅（ボタン 52dp ＋ 余白） */
private val SideColumnWidth = Tuning.SIDE_COLUMN_WIDTH_DP.dp
private val SideButtonSize = Tuning.SIDE_BUTTON_DP.dp
private val SideButtonGap = Tuning.SIDE_BUTTON_GAP_DP.dp
private val SidePaddingV = Tuning.SIDE_COLUMN_PADDING_V_DP.dp

/** 操作列の ＋ / RNG / − の組の高さ（上下の余白を含む）と、PAN 中に足す「現在地」の分 */
private val SideGroupHeight = SideButtonSize * 3 + SideButtonGap * 2 + SidePaddingV * 2
private val SidePanExtra = SideButtonGap + SideButtonSize

/** 横並びの WP ボタン列の高さ */
private val WpStripHeight = Tuning.WP_STRIP_HEIGHT_DP.dp
private val WpSettingsWidth = Tuning.WP_SETTINGS_WIDTH_DP.dp
private val WpButtonGap = Tuning.WP_BUTTON_GAP_DP.dp

/** 標高プロファイルの高さ（OFF なら null） */
private fun profileHeight(size: ProfileSize): Dp? = when (size) {
    ProfileSize.OFF -> null
    ProfileSize.SMALL -> Tuning.PROFILE_HEIGHT_SMALL_DP.dp
    ProfileSize.MEDIUM -> Tuning.PROFILE_HEIGHT_MEDIUM_DP.dp
    ProfileSize.LARGE -> Tuning.PROFILE_HEIGHT_LARGE_DP.dp
}

/**
 * 左の列（軌跡の操作。20261108-D02 から。数値の欄の下。縮尺の操作列とは逆の左側）。並びは core の MainLayout.trackControls:
 * LIVE は ⇄ LIVE だけ。REPLAY は上から ⇄ REPLAY、FILE（ファイルの画面を開く）、縦の再生位置のスライダー（残りの高さいっぱい。
 * 下が始まり、上が終わり。指を離したときにシークする）、再生 / 一時停止（▶ / ❚❚、終わりは END）、
 * 倍速の ＋ / ×N / −（×1 / ×2 / ×5 / ×10 / ×30。端ではグレー。§6.7）。よく押す倍速のボタンを、指に近い一番下に置く。
 * 不透明度はボタンの不透明度（設定。右の操作列と同じ）。
 */
@Composable
private fun TrackColumn(
    state: NavState,
    replay: ReplayUiState,
    onToggleSourceKind: () -> Unit,
    onPickTrack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = replay.startMs
    val end = replay.endMs
    var dragging by remember { mutableStateOf<Float?>(null) }
    val a = state.settings.buttonOpacityPct / 100f
    val trackH = Tuning.TRACK_BUTTON_HEIGHT_DP.dp
    val speedH = Tuning.REPLAY_SPEED_BUTTON_HEIGHT_DP.dp
    Column(
        modifier.padding(vertical = SidePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SideButtonGap),
    ) {
        for (c in MainLayout.trackControls(state.sourceKind)) when (c) {
            // タップで LIVE ⇔ REPLAY（2 行: ⇄ と今の入力）
            MainLayout.TrackControl.SOURCE -> {
                val kind = if (state.sourceKind == SourceKind.LIVE) "LIVE" else "REPLAY"
                SideButton("⇄\n$kind", onToggleSourceKind, height = trackH, alpha = a)
            }
            // ファイルの画面（軌跡）を開く
            MainLayout.TrackControl.FILE -> SideButton("FILE", onPickTrack, height = trackH, alpha = a)
            // 再生位置のスライダー: FILE のすぐ下から、再生のボタンのすぐ上まで
            MainLayout.TrackControl.SEEK -> if (start != null && end != null && end > start) {
                val now = (state.nowMs ?: start).coerceIn(start, end)
                val frac = dragging ?: ((now - start).toFloat() / (end - start))
                VerticalSeekBar(
                    fraction = frac,
                    onDrag = { dragging = it },
                    onRelease = {
                        dragging?.let { onSeek(start + ((end - start) * it).toLong()) }
                        dragging = null
                    },
                    alpha = a,
                    modifier = Modifier.weight(1f).width(SideButtonSize),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            MainLayout.TrackControl.PLAY -> {
                val play = when {
                    replay.finished -> "END"
                    state.playing -> "❚❚"
                    else -> "▶"
                }
                SideButton(play, onTogglePlay, enabled = replay.ready && !replay.finished, fontSize = 16.sp, alpha = a)
            }
            // 倍速（一番下）: ＋ で1段速く、− で1段遅く。×N は表示だけ
            MainLayout.TrackControl.FASTER ->
                SideButton("＋", onFaster, enabled = ReplaySpeed.faster(replay.speed) != null, height = speedH, fontSize = 20.sp, alpha = a)
            MainLayout.TrackControl.SPEED -> OutlinedText(AnnotatedString("×${replay.speed}"), Value.copy(fontSize = 14.sp), Modifier.alpha(a))
            MainLayout.TrackControl.SLOWER ->
                SideButton("−", onSlower, enabled = ReplaySpeed.slower(replay.speed) != null, height = speedH, fontSize = 20.sp, alpha = a)
        }
    }
}

/**
 * 縦の再生位置のスライダー: 下が始まり（0）、上が終わり（1）。つまみを上へ動かすと先へ進む。触った所へすぐ動き、
 * 動かしている間は onDrag、指を離したら onRelease（そこでシークする）。幅いっぱいが触れる範囲（線は細く、つまみは大きめ）。
 */
@Composable
private fun VerticalSeekBar(
    fraction: Float,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val density = LocalDensity.current
    val thumbR = with(density) { (Tuning.REPLAY_SEEK_THUMB_DP / 2).dp.toPx() }
    val track = with(density) { Tuning.REPLAY_SEEK_TRACK_DP.dp.toPx() }
    val on = HudColors.Scale
    val off = HudColors.WpReached
    Canvas(
        modifier.pointerInput(Unit) {
            // つまみの中心が動く範囲（上下につまみの半径の余白）で、y → 0〜1（下が 0）
            fun fracAt(y: Float): Float {
                val span = (size.height - thumbR * 2).coerceAtLeast(1f)
                return (1f - (y - thumbR) / span).coerceIn(0f, 1f)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                drag(fracAt(down.position.y))
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    c.consume()
                    drag(fracAt(c.position.y))
                }
                release()
            }
        },
    ) {
        val x = size.width / 2
        val top = thumbR
        val bottom = size.height - thumbR
        val y = bottom - (bottom - top) * fraction.coerceIn(0f, 1f)
        // 線: 終わりまでの残りは暗いグレー、始まりから今までは UI の色。つまみは UI の色の丸
        drawLine(off.copy(alpha = off.alpha * alpha), Offset(x, top), Offset(x, bottom), strokeWidth = track, cap = StrokeCap.Round)
        drawLine(on.copy(alpha = on.alpha * alpha), Offset(x, y), Offset(x, bottom), strokeWidth = track, cap = StrokeCap.Round)
        drawCircle(on.copy(alpha = on.alpha * alpha), radius = thumbR, center = Offset(x, y))
    }
}

/**
 * 右の操作列（回避枠の縦中央）。頻繁に押すので大きめ（52dp 角）。上から −（広域へ）/ 縮尺の表示（自機から1つ目の距離環の距離。
 * タップで AUTO の ON / OFF）/ ＋（詳細へ）。PAN 中は縮尺の表示が「PAN」になり、一番下（＋ のすぐ下）に「現在地」（現在地の表示に戻る）。
 */
@Composable
private fun SideColumn(
    state: NavState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleAutoRange: () -> Unit,
    onEndPan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(SideColumnWidth).padding(vertical = SidePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SideButtonGap),
    ) {
        val a = state.settings.buttonOpacityPct / 100f
        SideButton("−", onZoomOut, fontSize = 22.sp, alpha = a)
        RangeButton(state, onToggleAutoRange, alpha = a)
        SideButton("＋", onZoomIn, fontSize = 22.sp, alpha = a)
        if (state.pan != null) SideButton(stringResource(R.string.side_here), onEndPan, color = HudColors.Caution, inverted = true, alpha = a)
    }
}

/**
 * 縮尺のボタン（操作列の真ん中、§6.1）: 運転席からセンターコンソールの画面で読めるよう、中は R1 の距離（例「500m」）だけを
 * 太く大きく出す（ボタンの幅に収まる大きさ。上限 Tuning.RANGE_BUTTON_MAX_SP）。タップで AUTO の ON / OFF。
 * AUTO は塗りつぶし（ボタンの不透明度そのまま）に黒の文字とボタンの色の縁取り、手動は枠だけ（文字に黒の縁取り）。
 * PAN 中は今までどおり黄色で「PAN」と距離（§6.10）。
 */
@Composable
private fun RangeButton(state: NavState, onClick: () -> Unit, alpha: Float) {
    val face = RangeButtonFace.of(state.rangeM, state.rangeAuto, state.pan != null)
    if (face.pan) {
        SideButton(face.text, onClick, color = HudColors.Caution, alpha = alpha)
        return
    }
    val label = face.text
    val auto = face.filled
    val c = HudColors.Scale
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = ButtonText.copy(fontWeight = FontWeight.Bold, fontSize = Tuning.RANGE_BUTTON_MAX_SP.sp)
    // ボタンの幅（左右の余白を除く）に収まる大きさ
    val fontSize = with(density) {
        val avail = (SideButtonSize - Tuning.RANGE_BUTTON_TEXT_PAD_DP.dp * 2).toPx()
        val w = tm.measure(label, base).size.width.coerceAtLeast(1)
        (Tuning.RANGE_BUTTON_MAX_SP * minOf(1f, avail / w)).sp
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(SideButtonSize)
            .height(SideButtonSize)
            .border(1.dp, c.copy(alpha = c.alpha * alpha), shape)
            .then(if (auto) Modifier.background(c.copy(alpha = c.alpha * alpha), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OverlayButtonText(label, c, inverted = auto, alpha = alpha, style = base.copy(fontSize = fontSize, lineHeight = fontSize * 1.1f))
    }
}

@Composable
private fun SideButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    inverted: Boolean = false,
    color: Color = HudColors.Scale,
    height: Dp = SideButtonSize,
    width: Dp = SideButtonSize,
    fontSize: TextUnit = 12.sp,
    /** ボタンの不透明度（設定）。押せないとき（グレー）も同じ */
    alpha: Float = 1f,
) {
    val c = if (enabled) color else HudColors.WpReached
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(width)
            .height(height)
            .overlayButton(c, inverted, alpha, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val style = ButtonText.copy(fontSize = fontSize, lineHeight = fontSize * 1.15f)
        OverlayButtonText(text, c, inverted, alpha, style, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/**
 * 地図に重ねるボタンの枠と塗り（§6.1）。背景は塗らず枠だけ。ON（反転）は塗るが、塗りの不透明度は
 * ボタンの不透明度 × Tuning.BUTTON_ON_FILL_ALPHA（裏の地図の線が透けて見える）。枠もボタンの不透明度で薄くする。
 */
private fun Modifier.overlayButton(color: Color, inverted: Boolean, alpha: Float, shape: Shape): Modifier =
    border(1.dp, color.copy(alpha = color.alpha * alpha), shape)
        .then(if (inverted) Modifier.background(color.copy(alpha = color.alpha * alpha * Tuning.BUTTON_ON_FILL_ALPHA), shape) else Modifier)

/**
 * 地図に重ねるボタンの文字。ふだんは黒の縁取りを付けた文字。ON（反転）は黒の文字に、塗りが薄くても読めるよう
 * ボタンの色の細い縁取り（Tuning.BUTTON_ON_TEXT_OUTLINE_DP）。全体をボタンの不透明度で薄くする。
 */
@Composable
private fun OverlayButtonText(
    text: String,
    color: Color,
    inverted: Boolean,
    alpha: Float,
    style: TextStyle,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    val m = Modifier.alpha(alpha)
    if (inverted) {
        OutlinedText(
            AnnotatedString(text), style.copy(color = HudColors.Background), m, maxLines, textAlign,
            outlineColor = color, outlineWidth = Tuning.BUTTON_ON_TEXT_OUTLINE_DP.dp,
        )
    } else {
        OutlinedText(AnnotatedString(text), style.copy(color = color), m, maxLines, textAlign)
    }
}

/**
 * 横並びの WP ボタン列（§6.4。20261108-D02 からいつも出す）。左端に固定の「WP」（WP設定の画面を開く）、その右は左から WP1, WP2…
 * （左右にスクロール。WP リストがないときは「WP」だけ。core の WpStripLayout.items）。
 * ボタンの幅は「見せる数」で決まる。次の WP が変わったら、それが中央に来るよう自動でスクロールする。
 * タップ = 到達済みの切替（到達済みは色反転、無効 WP はグレーで押せない）。長押し = その WP を地図の中心に（PAN）。
 */
@Composable
private fun WpStrip(
    state: NavState,
    onOpenSettings: () -> Unit,
    onToggleReached: (Int) -> Unit,
    onPanTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(WpStripHeight)
            .padding(horizontal = 6.dp, vertical = Tuning.WP_STRIP_PADDING_V_DP.dp),
    ) {
        val visible = state.settings.wpButtonsMax.coerceAtLeast(1)
        val listWidth = maxWidth - WpSettingsWidth - WpButtonGap
        val itemWidth = (listWidth - WpButtonGap * (visible - 1)) / visible
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        val stepPx = with(density) { (itemWidth + WpButtonGap).toPx() }
        val viewPx = with(density) { listWidth.toPx() }
        val next = state.nextWpIndex
        LaunchedEffect(next, visible, state.waypoints.size, viewPx) {
            // 次の WP を左から2番目へ（1番目は1つ前の WP。次の WP が最初なら左詰め）
            val first = WpStripLayout.firstIndex(next) ?: return@LaunchedEffect
            scroll.animateScrollTo((first * stepPx).toInt().coerceIn(0, scroll.maxValue))
        }
        val a = state.settings.buttonOpacityPct / 100f
        val items = WpStripLayout.items(state.waypoints.size)
        Row(horizontalArrangement = Arrangement.spacedBy(WpButtonGap)) {
            WpStripButton(stringResource(R.string.wp_settings_button), WpSettingsWidth, HudColors.Scale, inverted = false, enabled = true, alpha = a, onTap = onOpenSettings)
            // WP リストがなければ「WP」だけ
            if (items.size > 1) Row(
                Modifier.width(listWidth).horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(WpButtonGap),
            ) {
                for (item in items) {
                    if (item !is WpStripLayout.Item.Wp) continue
                    val i = item.index
                    val wp = state.waypoints[i]
                    val color = when {
                        !wp.enabled -> HudColors.WpDisabled
                        i == next -> HudColors.Active
                        else -> HudColors.Wp
                    }
                    WpStripButton(
                        wp.name, itemWidth, color,
                        inverted = wp.reached && wp.enabled,
                        enabled = wp.enabled,
                        alpha = a,
                        onTap = { onToggleReached(i) },
                        onLongPress = { onPanTo(i) },
                    )
                }
                // 最後のほうでも次の WP を2番目に置けるよう、右端に空きを足す
                val slots = WpStripLayout.trailingSlots(visible)
                if (slots > 0) Spacer(Modifier.width((itemWidth + WpButtonGap) * slots - WpButtonGap))
            }
        }
    }
}

@Composable
private fun WpStripButton(
    text: String,
    width: Dp,
    color: Color,
    inverted: Boolean,
    enabled: Boolean,
    /** ボタンの不透明度（設定） */
    alpha: Float,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .overlayButton(color, inverted, alpha, shape)
            .pointerInput(enabled, onTap, onLongPress) {
                detectTapGestures(
                    onTap = { if (enabled) onTap() },
                    onLongPress = onLongPress?.let { f -> { f() } },
                )
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        OverlayButtonText(text, color, inverted, alpha, ButtonText.copy(fontSize = 12.sp))
    }
}

/**
 * 上部バー（20261108-D02）: 「HDG ○○ ｜（まん中）｜ ARC ｜ ⚙」。まん中には位置がないとき（NO FIX）だけ、赤い枠の「NO FIX」。
 * ボタンは全部同じ見た目（高さ Tuning.TOP_BAR_BUTTON_HEIGHT_DP・角・枠・文字の大きさ）。NO FIX の枠も同じ高さ。
 * NO FIX は上部バーのまん中に置き、左右のボタンにかかるときはその間のまん中に寄せる（core の MainLayout.centerStart）。
 */
@Composable
private fun TopBar(
    state: NavState,
    onCycleSource: () -> Unit,
    onToggleDisplay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val hdg = MainLayout.headingLabel(state.sourceMode)
    val mode = MainLayout.displayLabel(state.settings.displayMode)
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 入りきらないときだけ、文字を全部同じ倍率で小さくする（20261025-D09。余白・枠・すき間は縮めない）。
        // NO FIX があるものとし、方位ソースと表示モードはいちばん長い文字で見積もる（NO FIX や方位ソースが変わっても大きさを変えない）
        val scale = with(density) {
            val labels = MainLayout.topBarLabels(state.sourceMode, state.settings.displayMode)
                .map { if (it == hdg) MainLayout.LONGEST_HEADING else if (it == mode) MainLayout.LONGEST_DISPLAY else it }
            val textDp = labels.sumOf { tm.measure(it, hudButtonStyle(false)).size.width.toDp().value.toDouble() }.toFloat()
            // ボタンごとに内側の左右の余白と枠（左右 1dp。NO FIX は 2dp）、丸めの分 1dp。ボタンの間のすき間と、上部バーの左右の余白
            val fixedDp = labels.size * (hudButtonPadH(false).value * Tuning.TOP_BAR_BUTTON_PAD_SCALE * 2 + 3f) + Tuning.TOP_BAR_NO_FIX_BORDER_DP * 2 +
                Tuning.TOP_BAR_GAP_DP * (labels.size - 1) + Tuning.TOP_BAR_PADDING_H_DP * 2
            TopBarFit.textScale(constraints.maxWidth.toDp().value, textDp, fixedDp)
        }
        val pad = Tuning.TOP_BAR_BUTTON_PAD_SCALE
        val h = Tuning.TOP_BAR_BUTTON_HEIGHT_DP.dp
        val gap = Tuning.TOP_BAR_GAP_DP.dp
        // ボタンの不透明度（設定）
        val a = state.settings.buttonOpacityPct / 100f
        // 空いている所は地図に渡さない（上部バーの下で地図の操作を受け付ける）
        Layout(
            content = {
                // 方位ソースの選択（HYBRID / GPS / COMPASS）
                HudButton(hdg, onCycleSource, overlayAlpha = a, padScale = pad, textScale = scale, height = h)
                // まん中: NO FIX のときだけ
                Box { if (state.noFix) NoFixBadge(scale, h, pad) }
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    HudButton(mode, onToggleDisplay, overlayAlpha = a, padScale = pad, textScale = scale, height = h)
                    // 設定画面。長押しで開発用画面
                    HudButton(MainLayout.SETTINGS_LABEL, onOpenSettings, onLongClick = onOpenDebug, overlayAlpha = a, padScale = pad, textScale = scale, height = h)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tuning.TOP_BAR_PADDING_H_DP.dp, vertical = 6.dp),
        ) { measurables, constraints ->
            val barW = constraints.maxWidth
            val gapPx = gap.toPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            // 右の表示モードと ⚙ の幅を先に取る（⚙ は押しつぶされない）
            val right = measurables[2].measure(loose)
            val left = measurables[0].measure(loose.copy(maxWidth = (barW - right.width - gapPx.roundToInt()).coerceAtLeast(0)))
            val center = measurables[1].measure(loose)
            val height = maxOf(left.height, right.height, center.height)
            layout(barW, height) {
                left.place(0, (height - left.height) / 2)
                right.place(barW - right.width, (height - right.height) / 2)
                val x = MainLayout.centerStart(barW.toFloat(), left.width.toFloat(), (barW - right.width).toFloat(), center.width.toFloat(), gapPx)
                center.place(x.roundToInt(), (height - center.height) / 2)
            }
        }
    }
}

/** 上部バーのまん中の NO FIX（20261108-D02。前は数値の欄の下の枠）: 赤い枠に赤い太字。警告なので不透明度は 100% */
@Composable
private fun NoFixBadge(textScale: Float, height: Dp, padScale: Float) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .height(height)
            .background(HudColors.Background, shape)
            .border(Tuning.TOP_BAR_NO_FIX_BORDER_DP.dp, HudColors.Warning, shape)
            .padding(horizontal = hudButtonPadH(false) * padScale),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            MainLayout.NO_FIX_LABEL,
            style = hudButtonStyle(false, textScale).copy(color = HudColors.Warning, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** HDG の値の横に付ける、実際に使っている方位ソース（GPS / CMP、GPS 方位を保持中は HLD）。方位がなければ空。 */
private fun headingSourceLabel(state: NavState): String = when (state.heading.src) {
    HeadingSrc.GPS -> if (state.heading.held) "HLD" else "GPS"
    HeadingSrc.COMPASS -> "CMP"
    HeadingSrc.NONE -> ""
}

/** コンパスの印: MAG（偏角が分からず磁北のまま）、CAL（精度が低い）。コンパスを使っているときだけ。 */
private fun compassMarks(state: NavState): String {
    val q = state.compass
    if (state.heading.src != HeadingSrc.COMPASS || q == null) return ""
    return listOfNotNull("MAG".takeIf { q.declinationUnknown }, "CAL".takeIf { q.lowAccuracy }).joinToString(" ")
}

/**
 * 数値の表示（上部バーの下、4行。20261025-D09 から。左の列 = 時刻と WP、右 = 自機の値）。見出しは値の左に小さく並べる。箱なしで、文字に黒の縁取り。
 * 1行目: TIME ｜ LAT/LON、2行目: ETA ｜ GS・HDG、3行目: TGT ｜ ALT・V/S、4行目: DDL ｜ AGL（20261104-D03。前は 3 行目が ALT・AGL、4 行目が RATE）。
 * 次の WP の名前・方位・距離は地図上の WP の文字で見る（§6.1）。
 * 幅は欄ごとのいちばん長くなる値（NumberRows.SAMPLES）を先に取り、左の列は全部の行で同じ幅（core の NumberLayout）。
 * 幅が足りない画面では、全部の行の文字を同じ倍率で縮める。
 * 不透明度は設定（numbersOpacityPct）。TIME はいつも UI の色。警告の表示（締切超過の DDL、CAL / MAG の付いた HDG、
 * 負のまま出す設定の負の AGL）は常に 100%。
 */
@Composable
private fun NumbersPanel(state: NavState, zone: ZoneId, valueColor: Color) {
    val alpha = state.settings.numbersOpacityPct / 100f
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = Tuning.NUMBERS_PADDING_V_DP.dp),
    ) {
        val gap = Tuning.NUMBERS_CELL_GAP_DP
        // 欄ごとの要る幅 [dp]（見出し + すき間 + 値 + 小さな文字。丸めの分を少し足す）
        val needs = remember(density) {
            NumberRows.ROWS.map { row ->
                row.map { f ->
                    val s = NumberRows.SAMPLES.getValue(f)
                    val px = tm.measure(s.caption, Caption).size.width + tm.measure(s.value, Value).size.width +
                        (if (s.small.isEmpty()) 0 else tm.measure(s.small, Caption).size.width)
                    with(density) { px.toDp().value } + Tuning.NUMBERS_CAPTION_PAD_DP + 1f
                }
            }
        }
        val fit = NumberLayout.fit(needs, with(density) { constraints.maxWidth.toDp().value }, gap)
        Column(verticalArrangement = Arrangement.spacedBy(Tuning.NUMBERS_ROW_GAP_DP.dp)) {
            NumberRows.ROWS.forEachIndexed { i, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    row.forEachIndexed { j, field ->
                        NumberFieldCell(state, zone, field, valueColor, alpha, Modifier.width(fit.widths[i][j].dp), fit.scale)
                    }
                }
            }
        }
    }
}

/** 数値の欄 1 つ（見出し・値・色・不透明度・タップ）。幅は modifier、文字の倍率は textScale */
@Composable
private fun NumberFieldCell(
    state: NavState,
    zone: ZoneId,
    field: NumberField,
    valueColor: Color,
    alpha: Float,
    modifier: Modifier,
    textScale: Float,
) {
    val n = Modifier.alpha(alpha)
    val c = field.caption
    when (field) {
        // TIME はいつも UI の色（NO FIX でもグレーにしない）
        NumberField.TIME -> NumberCell(c, AnnotatedString(HudFormat.time(state.nowMs, zone)), HudColors.Scale, modifier.then(n), textScale)
        NumberField.ETA -> NumberCell(c, AnnotatedString(HudFormat.time(state.etaMs, zone)), valueColor, modifier.then(n), textScale)
        NumberField.TGT -> NumberCell(c, AnnotatedString(HudFormat.countdown(state.targetCountdownSec)), valueColor, modifier.then(n), textScale)
        // 締切を過ぎたら警告なので 100%
        NumberField.DDL -> {
            val d = state.deadlineCountdownSec
            val over = d != null && d < 0
            NumberCell(c, AnnotatedString(HudFormat.countdown(d)), if (over) HudColors.Warning else valueColor, if (over) modifier else modifier.then(n), textScale)
        }
        NumberField.LAT_LON -> NumberCell(c, AnnotatedString(HudFormat.latLon(state.fix?.lat, state.fix?.lon)), valueColor, modifier.then(n), textScale)
        NumberField.GS -> NumberCell(c, AnnotatedString(HudFormat.speedKmh(state.groundSpeedMps)), valueColor, modifier.then(n), textScale)
        NumberField.HDG -> {
            // 値の横に実際のソース（小さく）と、コンパスの印（黄色）。コンパスの印は警告なので、出ているときはセルごと 100%
            val src = headingSourceLabel(state)
            val marks = compassMarks(state)
            val small = SpanStyle(fontSize = Caption.fontSize * textScale)
            val value = buildAnnotatedString {
                append(HudFormat.bearing(state.heading.deg))
                if (src.isNotEmpty()) withStyle(small) { append(" $src") }
                // コンパス自体の状態なので、NO FIX 中も黄色のまま
                if (marks.isNotEmpty()) withStyle(small.copy(color = HudColors.Caution)) { append(" $marks") }
            }
            NumberCell(c, value, valueColor, if (marks.isNotEmpty()) modifier else modifier.then(n), textScale)
        }
        NumberField.ALT -> NumberCell(c, AnnotatedString(HudFormat.altitude(state.altM)), valueColor, modifier.then(n), textScale)
        // 地上高（20261025-D08）: 設定「地上高の負の値」が「負のまま出す」で 0 未満なら、DDL を過ぎたときと同じ警告の色・100%
        NumberField.AGL -> {
            val agl = HudFormat.agl(state.aglM, state.settings.aglNegative)
            NumberCell(c, AnnotatedString(agl.text), if (agl.warning) HudColors.Warning else valueColor, if (agl.warning) modifier else modifier.then(n), textScale)
        }
        // V/S（20261104-D03。前の RATE。タップで窓を切り替える決まりはなくした）
        NumberField.VS -> NumberCell(c, AnnotatedString(HudFormat.verticalSpeed(state.verticalSpeedMpm)), valueColor, modifier.then(n), textScale)
    }
}

/**
 * 数値のセル: 見出しを値の左に小さく並べる。幅は modifier で決める。textScale は文字の倍率（入りきらない画面だけ 1 未満。全部の行で同じ）。
 */
@Composable
private fun NumberCell(caption: String, value: AnnotatedString, color: Color, modifier: Modifier, textScale: Float = 1f) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        OutlinedText(
            AnnotatedString(caption),
            Caption.copy(fontSize = Caption.fontSize * textScale),
            Modifier.padding(end = Tuning.NUMBERS_CAPTION_PAD_DP.dp, bottom = 2.dp),
        )
        OutlinedText(value, Value.copy(color = color, fontSize = Value.fontSize * textScale))
    }
}

/**
 * 数値の表示（上部バーの下の4行）とボタンの文字。地図の上に箱なしで重ねるので、黒の縁取りを下に描いて線や環の上でも読めるようにする。
 * 縁取りの太さは Tuning.TEXT_OUTLINE_DP。入りきらなければ … で省く。
 */
@Composable
private fun OutlinedText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    outlineColor: Color = Color.Black,
    outlineWidth: Dp = Tuning.TEXT_OUTLINE_DP.dp,
) {
    val w = with(LocalDensity.current) { outlineWidth.toPx() }
    // 縁取りの層は文字の色を指定しない（部分ごとの色も外して縁取りの色にする）
    val plain = AnnotatedString(
        text.text,
        text.spanStyles.map { AnnotatedString.Range(it.item.copy(color = Color.Unspecified), it.start, it.end) },
    )
    Box(modifier) {
        Text(
            plain,
            style = style.copy(color = outlineColor, drawStyle = Stroke(width = w, join = StrokeJoin.Round)),
            maxLines = maxLines,
            textAlign = textAlign,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }
}
/** 知らせの文字と、枠の内側の余白（左右・上下） */
private val NoticeStyle get() = Caption.copy(fontSize = 13.sp, textAlign = TextAlign.Center)
private val NoticePadH = 10.dp
private val NoticePadV = 6.dp

/**
 * WP ボタン列のすぐ上の知らせ: 黒の地に細い枠と文字（色は color）。読み込んだ結果などのひとこと（20261103-D01）は UI の色、
 * NO FIX のときの案内（20261108-D02。前は NO FIX の枠の中）は黄色。長い文は折り返す
 */
@Composable
private fun NoticeBox(text: String, color: Color) {
    Text(
        text,
        style = NoticeStyle.copy(color = color),
        modifier = Modifier
            .background(HudColors.Background.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = NoticePadH, vertical = NoticePadV),
    )
}

/** NO FIX のときの案内（WP ボタン列のすぐ上に出す）。 */
@Composable
private fun noFixHint(state: NavState, replay: ReplayUiState, live: LiveUiState): String? =
    if (state.sourceKind == SourceKind.LIVE) {
        when {
            live.permission == LocationPermission.UNKNOWN -> stringResource(R.string.hint_waiting_permission)
            !live.gpsEnabled -> stringResource(R.string.hint_gps_off)
            state.fix == null -> stringResource(R.string.hint_acquiring_gps)
            else -> null
        }
    } else {
        when {
            replay.loading -> stringResource(R.string.loading)
            replay.message != null -> replay.message.asString()
            replay.fileName == null -> stringResource(R.string.hint_choose_track)
            else -> null
        }
    }

/** 位置情報の権限が拒否されたときの案内（§6.8）。リプレイは使える。 */
@Composable
private fun PermissionBox(
    permission: LocationPermission,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(HudColors.Background)
            .border(2.dp, HudColors.Caution)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.perm_title), style = Value.copy(color = HudColors.Caution, fontWeight = FontWeight.Bold, fontSize = 17.sp))
        Text(
            if (permission == LocationPermission.APPROXIMATE_ONLY) {
                stringResource(R.string.perm_approximate)
            } else {
                stringResource(R.string.perm_denied)
            },
            style = Caption.copy(color = HudColors.Scale, fontSize = 13.sp),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton(stringResource(R.string.perm_allow), onRequest)
            HudButton(stringResource(R.string.perm_open_settings), onOpenSettings)
        }
        HudButton(stringResource(R.string.perm_switch_replay), onUseReplay)
    }
}

/** HudButton の内側の左右の余白（倍率 1 のとき） */
private fun hudButtonPadH(small: Boolean) = if (small) 8.dp else 10.dp

/** HudButton の文字（textScale は上部バーが入りきらないときの倍率） */
private fun hudButtonStyle(small: Boolean, textScale: Float = 1f) =
    ButtonText.copy(fontSize = (if (small) 12.sp else 13.sp) * textScale)

@Composable
internal fun HudButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    small: Boolean = false,
    inverted: Boolean = false,
    fill: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** 地図に重ねるボタン（上部バー）ならボタンの不透明度（設定）。null は設定画面などのボタン（不透明度は変えない） */
    overlayAlpha: Float? = null,
    /** 内側の左右の余白の倍率（上部バーは Tuning.TOP_BAR_BUTTON_PAD_SCALE） */
    padScale: Float = 1f,
    /** 文字の倍率（上部バーが入りきらないときだけ 1 より小さい。TopBarFit） */
    textScale: Float = 1f,
    /** 決まった高さ（上部バーは Tuning.TOP_BAR_BUTTON_HEIGHT_DP でそろえる）。null は文字の高さと余白から */
    height: Dp? = null,
) {
    val color = if (enabled) HudColors.Scale else HudColors.WpReached
    val shape = RoundedCornerShape(4.dp)
    Box(
        (if (fill) Modifier.fillMaxWidth() else Modifier)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .then(
                if (overlayAlpha != null) {
                    Modifier.overlayButton(color, inverted, overlayAlpha, shape)
                } else {
                    // 背景は塗らない。状態を示す反転のときだけ塗る
                    Modifier.border(1.dp, color, shape).then(if (inverted) Modifier.background(color, shape) else Modifier)
                },
            )
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = hudButtonPadH(small) * padScale, vertical = if (small) 3.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = hudButtonStyle(small, textScale)
        when {
            overlayAlpha != null -> OverlayButtonText(text, color, inverted, overlayAlpha, style)
            inverted -> Text(text, style = style.copy(color = HudColors.Background))
            else -> OutlinedText(AnnotatedString(text), style.copy(color = color))
        }
    }
}

