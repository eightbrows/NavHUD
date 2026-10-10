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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.ProfileSize
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.replay.ReplaySpeed
import io.github.eightbrows.navhud.core.view.HudFormat
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudViewport
import io.github.eightbrows.navhud.core.view.NumberField
import io.github.eightbrows.navhud.core.view.NumberRows
import io.github.eightbrows.navhud.core.view.RangeButtonFace
import io.github.eightbrows.navhud.core.view.WpStripLayout
import java.time.ZoneId
import kotlin.math.roundToInt

private val Caption get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HudColors.Caption)
private val Value get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val ButtonText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HudColors.Scale)

/**
 * メイン画面（§6.1〜6.4）。NavState だけを見て描く。
 * 地図はステータスバーの下から画面の下端まで、左右いっぱいに描き、ほかの表示はすべて地図の上に重ねる:
 * 上から 上部バー・数値（4行）、右に操作列（回避枠の縦中央）、左に再生の操作列（REPLAY のみ。数値の下から WP ボタン列の上まで）、
 * 下は WP ボタン列・標高プロファイル。
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
    onCycleRate: () -> Unit,
    onOpenDebug: () -> Unit,
    wpUi: WaypointUiState,
    onToggleWpButtons: () -> Unit,
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
) {
    val zone = ZoneId.systemDefault()
    // NO FIX 中は最後の値をグレーで出し続ける
    val valueColor = if (state.noFix) HudColors.Stale else HudColors.Scale
    val showReplay = state.sourceKind == SourceKind.REPLAY

    // 重ねた表示の大きさ [px]（上: 上部バー・数値、下: WP ボタン列から下 = WP ボタン列・標高プロファイル・ナビゲーションバー）
    var boxW by remember { mutableIntStateOf(0) }
    var boxPx by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }
    var topBarPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    var stripPx by remember { mutableIntStateOf(0) }
    var navPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topDp = with(density) { topPx.toDp() }
    val bottomDp = with(density) { bottomPx.toDp() }
    // 右の操作列（＋ / RNG / −）は回避枠の縦中央。PAN 中の「現在地」は − のすぐ下に足す（＋ / RNG / − は動かさない）
    val sidePx = with(density) { SideColumnWidth.toPx() }
    val groupPx = with(density) { SideGroupHeight.toPx() }
    val sideTop = topPx + ((boxPx - bottomPx - topPx) - groupPx) / 2
    val sideBottom = sideTop + groupPx + if (state.pan != null) with(density) { SidePanExtra.toPx() } else 0f
    // 左の再生の操作列（REPLAY のみ）: 左端の「戻る」ジェスチャーの範囲（システムの値。3ボタンのナビゲーションなら 0）より
    // さらに内側。幅は右の操作列と同じ。地図は左の操作列の右から（矢印の文字・AUTO の判定の枠の左端）
    val gestureLeftPx = WindowInsets.systemGestures.getLeft(density, LocalLayoutDirection.current)
    val replayLeftPx = gestureLeftPx + with(density) { Tuning.REPLAY_COLUMN_EDGE_MARGIN_DP.dp.toPx() }
    val replayRightPx = if (showReplay) replayLeftPx + sidePx else 0f
    // 地図は描画の枠（この画面の全体）に描く。回避枠 = 上（上部バー・数値）と下（WP ボタン列から下）を除き、
    // 右は操作列のある高さの範囲だけ欠いた部分
    val reserved = HudInsets(left = replayRightPx, top = topPx.toFloat(), right = sidePx, bottom = bottomPx.toFloat(), rightSpan = sideTop..sideBottom)
    // WP の名前・矢印の文字を重ねない所: 数値の表示（上下の範囲）と、ボタン類（上部バー・右の操作列・WP ボタン列・左の再生の操作列）
    val numberBands = listOf(topBarPx.toFloat()..topPx.toFloat())
    val w = boxW.toFloat()
    val stripTop = (boxPx - bottomPx).toFloat()
    val buttonBoxes = listOfNotNull(
        HudRect(0f, 0f, w, topBarPx.toFloat()),
        HudRect(w - sidePx, sideTop, w, sideBottom),
        HudRect(0f, stripTop, w, stripTop + stripPx).takeIf { wpUi.showButtons },
        HudRect(replayLeftPx, topPx.toFloat(), replayRightPx, stripTop).takeIf { showReplay },
    )

    Box(modifier.fillMaxSize().background(HudColors.Background).onSizeChanged { boxW = it.width; boxPx = it.height }) {
        HudCanvas(state, Modifier.fillMaxSize(), reserved, onViewport, onPan, onPinch, numberBands, buttonBoxes)
        // 上: 上部バーと数値（4行）
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { topPx = it.height }) {
            Box(Modifier.onSizeChanged { topBarPx = it.height }) {
                TopBar(
                    state, wpUi.showButtons, onCycleSource, onToggleWpButtons, onToggleSourceKind, onPickTrack, showReplay,
                    onToggleDisplay, onOpenSettings, onOpenDebug,
                )
            }
            NumbersPanel(state, zone, valueColor, onCycleRate)
        }
        // 下: WP ボタン列・標高プロファイル・ナビゲーションバー
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().onSizeChanged { bottomPx = it.height }) {
            if (wpUi.showButtons) {
                Box(Modifier.onSizeChanged { stripPx = it.height }) { WpStrip(state, onOpenWpSettings, onToggleReached, onPanToWaypoint) }
            }
            profileHeight(state.settings.profileSize)?.let { h ->
                ProfileView(state, Modifier.fillMaxWidth().height(h))
            }
            Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).onSizeChanged { navPx = it.height })
        }
        // 右の操作列: ＋ / RNG / −（PAN 中は「現在地」も）。回避枠の縦中央
        SideColumn(
            state, onZoomIn, onZoomOut, onToggleAutoRange, onEndPan,
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, sideTop.roundToInt()) },
        )
        // 左の再生の操作列（REPLAY のみ）: 数値の下から WP ボタン列の上まで
        if (showReplay) {
            ReplayColumn(
                state, replay, onTogglePlay, onSlower, onFaster, onSeek,
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(replayLeftPx.roundToInt(), topPx) }
                    .width(SideColumnWidth)
                    .height(with(density) { (boxPx - bottomPx - topPx).coerceAtLeast(0).toDp() }),
            )
        }
        // 案内の枠: 位置情報の権限は回避枠の中央に置き、その幅で折り返す。NO FIX は数値の欄のすぐ下に左寄せ（地図の中央を空ける）
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
        } else if (state.noFix) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = topDp + Tuning.NO_FIX_MARGIN_TOP_DP.dp,
                        // 左の再生の操作列があれば、その右
                        start = with(density) { replayRightPx.toDp() } + Tuning.NO_FIX_MARGIN_START_DP.dp,
                        end = SideColumnWidth,
                        bottom = bottomDp,
                    ),
                contentAlignment = Alignment.TopStart,
            ) {
                NoFixBox(noFixHint(state, replay, live))
            }
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
 * 左の再生の操作列（§6.7、REPLAY のときだけ。数値の下から WP ボタン列の上まで。縮尺の操作列とは逆の左側）。
 * 上から 縦の再生位置のスライダー（残りの高さいっぱい。下が始まり、上が終わり。指を離したときにシークする）、
 * 再生 / 一時停止（▶ / ❚❚、終わりは END）、倍速の ＋ / ×N / −（×1 / ×2 / ×5 / ×10 / ×30。端ではグレー）。
 * よく押す倍速のボタンを、指に近い一番下に置く。
 * 不透明度はボタンの不透明度（設定。右の操作列と同じ）。
 */
@Composable
private fun ReplayColumn(
    state: NavState,
    replay: ReplayUiState,
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
    Column(
        modifier.padding(vertical = SidePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SideButtonGap),
    ) {
        // 再生位置のスライダー: 数値のすぐ下から、再生のボタンのすぐ上まで
        if (start != null && end != null && end > start) {
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
        val play = when {
            replay.finished -> "END"
            state.playing -> "❚❚"
            else -> "▶"
        }
        SideButton(play, onTogglePlay, enabled = replay.ready && !replay.finished, fontSize = 16.sp, alpha = a)
        // 倍速（一番下）: ＋ で1段速く、− で1段遅く。×N は表示だけ
        val speedH = Tuning.REPLAY_SPEED_BUTTON_HEIGHT_DP.dp
        SideButton("＋", onFaster, enabled = ReplaySpeed.faster(replay.speed) != null, height = speedH, fontSize = 20.sp, alpha = a)
        OutlinedText(AnnotatedString("×${replay.speed}"), Value.copy(fontSize = 14.sp), Modifier.alpha(a))
        SideButton("−", onSlower, enabled = ReplaySpeed.slower(replay.speed) != null, height = speedH, fontSize = 20.sp, alpha = a)
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
 * 横並びの WP ボタン列（§6.4）。左端に固定の「WP設定」、その右は左から WP1, WP2…（左右にスクロール）。
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
        Row(horizontalArrangement = Arrangement.spacedBy(WpButtonGap)) {
            WpStripButton(stringResource(R.string.wp_settings_button), WpSettingsWidth, HudColors.Scale, inverted = false, enabled = true, alpha = a, onTap = onOpenSettings)
            Row(
                Modifier.width(listWidth).horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(WpButtonGap),
            ) {
                for ((i, wp) in state.waypoints.withIndex()) {
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

@Composable
private fun TopBar(
    state: NavState,
    showWpButtons: Boolean,
    onCycleSource: () -> Unit,
    onToggleWpButtons: () -> Unit,
    onToggleSourceKind: () -> Unit,
    onPickTrack: () -> Unit,
    showReplay: Boolean,
    onToggleDisplay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Row(
        // 空いている所は地図に渡す（ドラッグで PAN）
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ボタンの不透明度（設定）
        val a = state.settings.buttonOpacityPct / 100f
        // 方位ソースの選択（HYBRID / GPS / COMPASS）
        HudButton("HDG ${state.sourceMode.name}", onCycleSource, overlayAlpha = a)
        // WP ボタン列の表示/非表示（出ているときは反転）
        HudButton("WP", onToggleWpButtons, inverted = showWpButtons, overlayAlpha = a)
        // INPUT（タップで LIVE ⇔ REPLAY）
        HudButton("⇄ " + if (state.sourceKind == SourceKind.LIVE) "LIVE" else "REPLAY", onToggleSourceKind, small = true, overlayAlpha = a)
        // track.csv を選ぶ（REPLAY のときだけ）
        if (showReplay) HudButton("FILE", onPickTrack, small = true, overlayAlpha = a)
        Spacer(Modifier.weight(1f))
        HudButton(if (state.settings.displayMode == DisplayMode.ARC) "ARC" else "N-UP", onToggleDisplay, overlayAlpha = a)
        // 設定画面。長押しで開発用画面
        HudButton("⚙", onOpenSettings, onLongClick = onOpenDebug, overlayAlpha = a)
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

/** HDG 欄: 値の横に実際のソース（小さく）と、コンパスの印（黄色）。 */
@Composable
private fun RowScope.HeadingCell(state: NavState, valueColor: Color, weight: Float, alpha: Float) {
    val src = headingSourceLabel(state)
    val marks = compassMarks(state)
    val small = SpanStyle(fontSize = 11.sp)
    InlineCell(
        "HDG",
        buildAnnotatedString {
            append(HudFormat.bearing(state.heading.deg))
            if (src.isNotEmpty()) withStyle(small) { append(" $src") }
            if (marks.isNotEmpty()) {
                // コンパス自体の状態なので、NO FIX 中も黄色のまま
                withStyle(small.copy(color = HudColors.Caution)) { append(" $marks") }
            }
        },
        valueColor,
        weight,
        // コンパスの印（CAL / MAG）は警告なので、出ているときはセルごと 100%
        modifier = Modifier.alpha(if (marks.isNotEmpty()) 1f else alpha),
    )
}

/**
 * 数値の表示（上部バーの下、4行）。見出しは値の左に小さく並べる。箱なしで、文字に黒の縁取り。
 * 1行目: TIME / ALT / RATE（タップで窓の切替）、2行目: HDG / GS / ETA、3行目: TGT / DDL、4行目: LAT/LON。
 * 次の WP の名前・方位・距離は地図上の WP の文字で見る（§6.1）。
 * 不透明度は設定（numbersOpacityPct）。警告の表示（締切超過の DDL、CAL / MAG の付いた HDG）は常に 100%。
 */
@Composable
private fun NumbersPanel(state: NavState, zone: ZoneId, valueColor: Color, onCycleRate: () -> Unit) {
    val fix = state.fix
    val deadline = state.deadlineCountdownSec
    val deadlineColor = when {
        deadline == null -> valueColor
        deadline < 0 -> HudColors.Warning
        else -> valueColor
    }
    val n = Modifier.alpha(state.settings.numbersOpacityPct / 100f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = Tuning.NUMBERS_PADDING_V_DP.dp),
        verticalArrangement = Arrangement.spacedBy(Tuning.NUMBERS_ROW_GAP_DP.dp),
    ) {
        // 1行目（TIME / ALT / RATE）は切れない幅を先に取る。2行目から下は NumberRows の順に重みで並べる
        FirstNumbersRow(state, zone, valueColor, state.settings.numbersOpacityPct / 100f, onCycleRate)
        for (row in NumberRows.ROWS.drop(1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tuning.NUMBERS_CELL_GAP_DP.dp)) {
                for (field in row) {
                    val c = field.caption
                    when (field) {
                        NumberField.HDG -> HeadingCell(state, valueColor, weight = 1.25f, alpha = state.settings.numbersOpacityPct / 100f)
                        NumberField.GS -> InlineCell(c, AnnotatedString(HudFormat.speedKmh(state.groundSpeedMps)), valueColor, 1f, modifier = n)
                        NumberField.ETA -> InlineCell(c, AnnotatedString(HudFormat.time(state.etaMs, zone)), valueColor, 1.15f, modifier = n)
                        NumberField.TGT -> InlineCell(c, AnnotatedString(HudFormat.countdown(state.targetCountdownSec)), valueColor, 1f, modifier = n)
                        // 締切を過ぎたら警告なので 100%
                        NumberField.DDL -> InlineCell(
                            c, AnnotatedString(HudFormat.countdown(deadline)), deadlineColor, 1f,
                            modifier = if (deadline != null && deadline < 0) Modifier else n,
                        )
                        NumberField.LAT_LON -> InlineCell(c, AnnotatedString(HudFormat.latLon(fix?.lat, fix?.lon)), valueColor, 1f, modifier = n)
                        // 1行目の欄は FirstNumbersRow で出す
                        NumberField.TIME, NumberField.ALT, NumberField.RATE -> Unit
                    }
                }
            }
        }
    }
}

/**
 * 数値の1行目（TIME / ALT / RATE）。どの画面幅でも切れないよう、各欄にいちばん長くなる値（Tuning.NUMBERS_ROW1_SAMPLES）の
 * 幅を先に取り、余りを前の比率（Tuning.NUMBERS_ROW1_WEIGHTS）で分ける。それでも入りきらない狭い画面では、1行目の文字を縮める。
 */
@Composable
private fun FirstNumbersRow(state: NavState, zone: ZoneId, valueColor: Color, alpha: Float, onCycleRate: () -> Unit) {
    val n = Modifier.alpha(alpha)
    val tm = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = Tuning.NUMBERS_CELL_GAP_DP.dp
        val samples = Tuning.NUMBERS_ROW1_SAMPLES
        val weights = Tuning.NUMBERS_ROW1_WEIGHTS
        // 各欄の要る幅 [px]（見出し + すき間 + 値。丸めの分を少し足す）
        val need = with(density) {
            samples.map { (cap, value) ->
                tm.measure(cap, Caption).size.width + Tuning.NUMBERS_CAPTION_PAD_DP.dp.toPx() + tm.measure(value, Value).size.width + 2f
            }
        }
        val avail = constraints.maxWidth - with(density) { gap.toPx() } * (samples.size - 1)
        val scale = minOf(1f, avail / need.sum())
        val spare = (avail - need.sum() * scale).coerceAtLeast(0f)
        val widths = need.mapIndexed { i, w -> with(density) { (w * scale + spare * weights[i] / weights.sum()).toDp() } }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            NumberCell(NumberField.TIME.caption, AnnotatedString(HudFormat.time(state.nowMs, zone)), HudColors.Scale, n.width(widths[0]), scale)
            NumberCell(NumberField.ALT.caption, AnnotatedString(HudFormat.altitude(state.altM)), valueColor, n.width(widths[1]), scale)
            NumberCell(
                "${NumberField.RATE.caption} ${state.settings.rateWindowSec}s",
                AnnotatedString(HudFormat.rate(state.rate)),
                valueColor,
                Modifier.width(widths[2]).clickable(onClick = onCycleRate).then(n),
                scale,
            )
        }
    }
}

/** 数値のセル（重みで幅を決める）。 */
@Composable
private fun RowScope.InlineCell(
    caption: String,
    value: AnnotatedString,
    color: Color,
    weight: Float,
    modifier: Modifier = Modifier,
) = NumberCell(caption, value, color, modifier.weight(weight))

/**
 * 数値のセル: 見出しを値の左に小さく並べる。幅は modifier で決める。textScale は文字の倍率（1行目が入りきらない狭い画面だけ 1 未満）。
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
@Composable
private fun NoFixBox(hint: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(HudColors.Background)
            .border(2.dp, HudColors.Warning)
            .padding(horizontal = Tuning.NO_FIX_PADDING_H_DP.dp, vertical = Tuning.NO_FIX_PADDING_V_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NO FIX", style = Value.copy(color = HudColors.Warning, fontWeight = FontWeight.Bold, fontSize = 18.sp))
        hint?.let { Text(it, style = Caption.copy(color = HudColors.Caution), textAlign = TextAlign.Center) }
    }
}

/** NO FIX の枠に添える案内。 */
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
) {
    val color = if (enabled) HudColors.Scale else HudColors.WpReached
    val shape = RoundedCornerShape(4.dp)
    Box(
        (if (fill) Modifier.fillMaxWidth() else Modifier)
            .then(
                if (overlayAlpha != null) {
                    Modifier.overlayButton(color, inverted, overlayAlpha, shape)
                } else {
                    // 背景は塗らない。状態を示す反転のときだけ塗る
                    Modifier.border(1.dp, color, shape).then(if (inverted) Modifier.background(color, shape) else Modifier)
                },
            )
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        val style = ButtonText.copy(fontSize = if (small) 12.sp else 13.sp)
        when {
            overlayAlpha != null -> OverlayButtonText(text, color, inverted, overlayAlpha, style)
            inverted -> Text(text, style = style.copy(color = HudColors.Background))
            else -> OutlinedText(AnnotatedString(text), style.copy(color = color))
        }
    }
}

