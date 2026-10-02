package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import io.github.eightbrows.navhud.core.view.WpStripLayout
import java.time.ZoneId
import kotlin.math.roundToInt

private val Caption get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HudColors.Caption)
private val Value get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val ButtonText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HudColors.Scale)

/**
 * メイン画面（§6.1〜6.4）。NavState だけを見て描く。
 * 地図はステータスバーの下から画面の下端まで、左右いっぱいに描き、ほかの表示はすべて地図の上に重ねる:
 * 上から 上部バー・数値（4行）、右に操作列（回避枠の縦中央）、下は WP ボタン列・標高プロファイル・再生の帯（REPLAY のみ）。
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

    // 重ねた表示の大きさ [px]（上: 上部バー・数値、下: WP ボタン列から下 = WP ボタン列・標高プロファイル・再生の帯・
    // ナビゲーションバー）
    var boxW by remember { mutableIntStateOf(0) }
    var boxPx by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }
    var topBarPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    var stripPx by remember { mutableIntStateOf(0) }
    var bandPx by remember { mutableIntStateOf(0) }
    var navPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topDp = with(density) { topPx.toDp() }
    val bottomDp = with(density) { bottomPx.toDp() }
    // 右の操作列（＋ / RNG / −）は回避枠の縦中央。PAN 中の「現在地」は − のすぐ下に足す（＋ / RNG / − は動かさない）
    val sidePx = with(density) { SideColumnWidth.toPx() }
    val groupPx = with(density) { SideGroupHeight.toPx() }
    val sideTop = topPx + ((boxPx - bottomPx - topPx) - groupPx) / 2
    val sideBottom = sideTop + groupPx + if (state.pan != null) with(density) { SidePanExtra.toPx() } else 0f
    // 地図は描画の枠（この画面の全体）に描く。回避枠 = 上（上部バー・数値）と下（WP ボタン列から下）を除き、
    // 右は操作列のある高さの範囲だけ欠いた部分
    val reserved = HudInsets(top = topPx.toFloat(), right = sidePx, bottom = bottomPx.toFloat(), rightSpan = sideTop..sideBottom)
    // WP の名前・矢印の文字を重ねない所: 数値の表示（上下の範囲）と、ボタン類（上部バー・操作列・WP ボタン列・再生の帯）
    val numberBands = listOf(topBarPx.toFloat()..topPx.toFloat())
    val w = boxW.toFloat()
    val stripTop = (boxPx - bottomPx).toFloat()
    val buttonBoxes = listOfNotNull(
        HudRect(0f, 0f, w, topBarPx.toFloat()),
        HudRect(w - sidePx, sideTop, w, sideBottom),
        HudRect(0f, stripTop, w, stripTop + stripPx).takeIf { wpUi.showButtons },
        HudRect(0f, (boxPx - navPx - bandPx).toFloat(), w, (boxPx - navPx).toFloat()).takeIf { showReplay },
    )

    Box(modifier.fillMaxSize().background(HudColors.Background).onSizeChanged { boxW = it.width; boxPx = it.height }) {
        HudCanvas(state, Modifier.fillMaxSize(), reserved, onViewport, onPan, numberBands, buttonBoxes)
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
        // 下: WP ボタン列・標高プロファイル・再生の帯（REPLAY のみ、画面の一番下）・ナビゲーションバー
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().onSizeChanged { bottomPx = it.height }) {
            if (wpUi.showButtons) {
                Box(Modifier.onSizeChanged { stripPx = it.height }) { WpStrip(state, onOpenWpSettings, onToggleReached, onPanToWaypoint) }
            }
            profileHeight(state.settings.profileSize)?.let { h ->
                ProfileView(state, Modifier.fillMaxWidth().height(h))
            }
            if (showReplay) {
                ReplayBand(
                    state, replay, zone, onTogglePlay, onSlower, onFaster, onSeek,
                    Modifier.fillMaxWidth().height(ReplayBandHeight).onSizeChanged { bandPx = it.height },
                )
            }
            Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).onSizeChanged { navPx = it.height })
        }
        // 右の操作列: ＋ / RNG / −（PAN 中は「現在地」も）。回避枠の縦中央
        SideColumn(
            state, onZoomIn, onZoomOut, onToggleAutoRange, onEndPan,
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, sideTop.roundToInt()) },
        )
        // 案内の枠: 回避枠の中央に置き、その幅で折り返す
        val permissionMissing = state.sourceKind == SourceKind.LIVE &&
            (live.permission == LocationPermission.DENIED || live.permission == LocationPermission.APPROXIMATE_ONLY)
        if (permissionMissing || state.noFix) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = topDp, end = SideColumnWidth, bottom = bottomDp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (permissionMissing) {
                    PermissionBox(live.permission, onRequestPermission, onOpenAppSettings, onToggleSourceKind)
                } else {
                    NoFixBox(noFixHint(state, replay, live))
                }
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

/** 再生の帯（再生 / 一時停止・倍速・シーク）の高さ */
private val ReplayBandHeight = Tuning.REPLAY_BAND_HEIGHT_DP.dp

/** 地図に重ねる部品の背景（半透明の黒。下の距離環・方位線が透けて見える） */
private val OverlayBackground get() = HudColors.Background.copy(alpha = Tuning.OVERLAY_ALPHA)

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
 * 再生の帯（§6.7、REPLAY のときだけ画面の一番下・ナビゲーションバーの上）。左から 再生 / 一時停止（▶ / ❚❚、終わりは END）、
 * 倍速の [−] ×N [＋]（×1 / ×2 / ×5 / ×10 / ×30。端ではグレー）、再生位置のスライダー、経過 / 全体の時間。
 * スライダーは指を離したときにシークし、動かしている間は行き先の時刻を出す。
 */
@Composable
private fun ReplayBand(
    state: NavState,
    replay: ReplayUiState,
    zone: ZoneId,
    onTogglePlay: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = replay.startMs
    val end = replay.endMs
    var dragging by remember { mutableStateOf<Float?>(null) }
    val buttonH = Tuning.REPLAY_BAND_BUTTON_HEIGHT_DP.dp
    Row(
        modifier.background(OverlayBackground).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val play = when {
            replay.finished -> "END"
            state.playing -> "❚❚"
            else -> "▶"
        }
        SideButton(
            play, onTogglePlay, enabled = replay.ready && !replay.finished,
            height = buttonH, width = Tuning.REPLAY_PLAY_BUTTON_WIDTH_DP.dp, fontSize = 14.sp,
        )
        // 倍速: − で1段遅く、＋ で1段速く。×N は表示だけ
        val speedW = Tuning.REPLAY_SPEED_BUTTON_WIDTH_DP.dp
        SideButton("−", onSlower, enabled = ReplaySpeed.slower(replay.speed) != null, height = buttonH, width = speedW, fontSize = 16.sp)
        Text("×${replay.speed}", style = Value.copy(fontSize = 14.sp), maxLines = 1)
        SideButton("＋", onFaster, enabled = ReplaySpeed.faster(replay.speed) != null, height = buttonH, width = speedW, fontSize = 16.sp)
        if (start != null && end != null && end > start) {
            val now = (state.nowMs ?: start).coerceIn(start, end)
            val frac = dragging ?: ((now - start).toFloat() / (end - start))
            Slider(
                value = frac,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSeek(start + ((end - start) * it).toLong()) }
                    dragging = null
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = HudColors.Scale,
                    activeTrackColor = HudColors.Scale,
                    inactiveTrackColor = HudColors.WpReached,
                ),
            )
            val label = dragging?.let { HudFormat.time(start + ((end - start) * it).toLong(), zone) }
                ?: "${HudFormat.elapsed(now - start)} / ${HudFormat.elapsed(end - start)}"
            Text(label, style = Caption.copy(fontSize = 12.sp), maxLines = 1)
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 右の操作列（回避枠の縦中央）。頻繁に押すので大きめ（52dp 角）。上から ＋ / 縮尺の表示（タップで AUTO の ON / OFF）/ −。
 * PAN 中は縮尺の表示が「PAN」になり、− のすぐ下に「現在地」（現在地の表示に戻る）。
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
        modifier.width(SideColumnWidth).background(OverlayBackground).padding(vertical = SidePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SideButtonGap),
    ) {
        SideButton("＋", onZoomIn, fontSize = 22.sp)
        val top = when {
            state.pan != null -> "PAN"
            state.rangeAuto -> "AUTO"
            else -> "RNG"
        }
        SideButton(
            "$top\n${HudFormat.rangeStep(state.rangeM)}",
            onToggleAutoRange,
            inverted = state.rangeAuto && state.pan == null,
            color = if (state.pan != null) HudColors.Caution else HudColors.Scale,
        )
        SideButton("−", onZoomOut, fontSize = 22.sp)
        if (state.pan != null) SideButton("現在地", onEndPan, color = HudColors.Caution, inverted = true)
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
) {
    val c = if (enabled) color else HudColors.WpReached
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(width)
            .height(height)
            .border(1.dp, c, shape)
            .background(if (inverted) c else OverlayBackground, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = ButtonText.copy(color = if (inverted) HudColors.Background else c, fontSize = fontSize, lineHeight = fontSize * 1.15f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
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
            .background(OverlayBackground)
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
        Row(horizontalArrangement = Arrangement.spacedBy(WpButtonGap)) {
            WpStripButton("WP設定", WpSettingsWidth, HudColors.Scale, inverted = false, enabled = true, onTap = onOpenSettings)
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
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .border(1.dp, color, shape)
            .background(if (inverted) color else HudColors.Background, shape)
            .pointerInput(enabled, onTap, onLongPress) {
                detectTapGestures(
                    onTap = { if (enabled) onTap() },
                    onLongPress = onLongPress?.let { f -> { f() } },
                )
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = ButtonText.copy(color = if (inverted) HudColors.Background else color, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        // 方位ソースの選択（HYBRID / GPS / COMPASS）
        HudButton("HDG ${state.sourceMode.name}", onCycleSource)
        // WP ボタン列の表示/非表示（出ているときは反転）
        HudButton("WP", onToggleWpButtons, inverted = showWpButtons)
        // INPUT（タップで LIVE ⇔ REPLAY）
        HudButton("⇄ " + if (state.sourceKind == SourceKind.LIVE) "LIVE" else "REPLAY", onToggleSourceKind, small = true)
        // track.csv を選ぶ（REPLAY のときだけ）
        if (showReplay) HudButton("FILE", onPickTrack, small = true)
        Spacer(Modifier.weight(1f))
        HudButton(if (state.settings.displayMode == DisplayMode.ARC) "ARC" else "N-UP", onToggleDisplay)
        // 設定画面。長押しで開発用画面
        HudButton("⚙", onOpenSettings, onLongClick = onOpenDebug)
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
private fun RowScope.HeadingCell(state: NavState, valueColor: Color, weight: Float) {
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
    )
}

/**
 * 数値の表示（上部バーの下、4行）。見出しは値の左に小さく並べる。箱なしで、文字に黒の縁取り。
 * 1行目: TIME / ALT / RATE（タップで窓の切替）、2行目: HDG / GS / ETA、
 * 3行目: NEXT（名前は入りきらなければ …。方位と距離は必ず出す）/ TGT / DDL、4行目: LAT/LON。
 */
@Composable
private fun NumbersPanel(state: NavState, zone: ZoneId, valueColor: Color, onCycleRate: () -> Unit) {
    val fix = state.fix
    val next = state.nextWpIndex?.let { state.waypoints[it] }
    val deadline = state.deadlineCountdownSec
    val deadlineColor = when {
        deadline == null -> valueColor
        deadline < 0 -> HudColors.Warning
        else -> valueColor
    }
    val nextColor = if (next != null && !state.noFix) HudColors.Active else valueColor
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = Tuning.NUMBERS_PADDING_V_DP.dp),
        verticalArrangement = Arrangement.spacedBy(Tuning.NUMBERS_ROW_GAP_DP.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InlineCell("TIME", AnnotatedString(HudFormat.time(state.nowMs, zone)), HudColors.Scale, 1f)
            InlineCell("ALT", AnnotatedString(HudFormat.altitude(state.altM)), valueColor, 0.85f)
            InlineCell(
                "RATE ${state.settings.rateWindowSec}s",
                AnnotatedString(HudFormat.rate(state.rate)),
                valueColor,
                1.55f,
                modifier = Modifier.clickable(onClick = onCycleRate),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeadingCell(state, valueColor, weight = 1.25f)
            InlineCell("GS", AnnotatedString(HudFormat.speedKmh(state.groundSpeedMps)), valueColor, 1f)
            InlineCell("ETA", AnnotatedString(HudFormat.time(state.etaMs, zone)), valueColor, 1.15f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InlineCell("NEXT", AnnotatedString(nextText(state)), nextColor, 1.9f, name = next?.name)
            InlineCell("TGT", AnnotatedString(HudFormat.countdown(state.targetCountdownSec)), valueColor, 1f)
            InlineCell("DDL", AnnotatedString(HudFormat.countdown(deadline)), deadlineColor, 1f)
        }
        Row {
            InlineCell("LAT/LON", AnnotatedString(HudFormat.latLon(fix?.lat, fix?.lon)), valueColor, 1f)
        }
    }
}

private fun nextText(state: NavState): String {
    if (state.nextWpBearingDeg == null || state.nextWpDistanceM == null) return HudFormat.NONE
    return "${HudFormat.bearing(state.nextWpBearingDeg)} ${HudFormat.distance(state.nextWpDistanceM)}"
}

/**
 * 数値のセル: 見出しを値の左に小さく並べる。name（NEXT の WP の名前）は、入りきらなければ … で省く
 * （値の方を先に場所を取る）。
 */
@Composable
private fun RowScope.InlineCell(
    caption: String,
    value: AnnotatedString,
    color: Color,
    weight: Float,
    name: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.weight(weight), verticalAlignment = Alignment.Bottom) {
        OutlinedText(AnnotatedString(caption), Caption, Modifier.padding(end = 4.dp, bottom = 2.dp))
        if (name != null) {
            OutlinedText(
                AnnotatedString(name),
                Caption.copy(color = color, fontSize = 13.sp),
                Modifier.weight(1f, fill = false).padding(end = 4.dp, bottom = 1.dp),
            )
        }
        OutlinedText(value, Value.copy(color = color))
    }
}

/**
 * 数値の表示（上部バーの下の4行）の文字。地図の上に箱なしで重ねるので、黒の縁取りを下に描いて線や環の上でも読めるようにする。
 * 縁取りの太さは Tuning.TEXT_OUTLINE_DP。入りきらなければ … で省く。
 */
@Composable
private fun OutlinedText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    val w = with(LocalDensity.current) { Tuning.TEXT_OUTLINE_DP.dp.toPx() }
    // 縁取りの層は文字の色を指定しない（部分ごとの色も外して黒にする）
    val plain = AnnotatedString(
        text.text,
        text.spanStyles.map { AnnotatedString.Range(it.item.copy(color = Color.Unspecified), it.start, it.end) },
    )
    Box(modifier) {
        Text(
            plain,
            style = style.copy(color = Color.Black, drawStyle = Stroke(width = w, join = StrokeJoin.Round)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
private fun NoFixBox(hint: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(HudColors.Background)
            .border(2.dp, HudColors.Warning)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NO FIX", style = Value.copy(color = HudColors.Warning, fontWeight = FontWeight.Bold, fontSize = 18.sp))
        hint?.let { Text(it, style = Caption.copy(color = HudColors.Caution), textAlign = TextAlign.Center) }
    }
}

/** NO FIX の枠に添える案内。 */
private fun noFixHint(state: NavState, replay: ReplayUiState, live: LiveUiState): String? =
    if (state.sourceKind == SourceKind.LIVE) {
        when {
            live.permission == LocationPermission.UNKNOWN -> "位置情報の許可を待っています"
            !live.gpsEnabled -> "端末の位置情報（GPS）がオフです。設定でオンにしてください"
            state.fix == null -> "GPS を受信しています…"
            else -> null
        }
    } else {
        when {
            replay.loading -> "読み込み中…"
            replay.message != null -> replay.message
            replay.fileName == null -> "FILE で track.csv を選んでください"
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
        Text("位置情報を使えません", style = Value.copy(color = HudColors.Caution, fontWeight = FontWeight.Bold, fontSize = 17.sp))
        Text(
            if (permission == LocationPermission.APPROXIMATE_ONLY) {
                "「おおよその位置」だけが許可されています。GPS で走行位置を出すには「正確な位置」の許可が必要です。"
            } else {
                "LIVE で現在地を表示するには、位置情報の許可が必要です。許可しなくても REPLAY（track.csv の再生）は使えます。"
            },
            style = Caption.copy(color = HudColors.Scale, fontSize = 13.sp),
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("許可する", onRequest)
            HudButton("設定を開く", onOpenSettings)
        }
        HudButton("REPLAY に切り替え", onUseReplay)
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
) {
    val color = if (enabled) HudColors.Scale else HudColors.WpReached
    Box(
        (if (fill) Modifier.fillMaxWidth() else Modifier)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            // 半透明の地（地図の上に重ねるため。設定画面など黒い画面では黒に見える）
            .background(if (inverted) color else OverlayBackground, RoundedCornerShape(4.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ButtonText.copy(color = if (inverted) HudColors.Background else color, fontSize = if (small) 12.sp else 13.sp))
    }
}

