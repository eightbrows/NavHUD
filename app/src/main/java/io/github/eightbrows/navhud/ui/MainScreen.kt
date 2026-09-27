package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.ProfileSize
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudFormat
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudViewport
import java.time.ZoneId

private val Caption get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HudColors.Caption)
private val Value get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val ButtonText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HudColors.Scale)

/**
 * メイン画面（§6.1〜6.4）。NavState だけを見て描く。
 * 上から: 上部バー / 情報欄 / 地図（右端に操作列、下端に横並びの WP ボタン列を重ねる）/ 標高プロファイル / 下部パネル。
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
    onEndPan: () -> Unit,
    onOpenSettings: () -> Unit,
    onViewport: (HudViewport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    // NO FIX 中は最後の値をグレーで出し続ける
    val valueColor = if (state.noFix) HudColors.Stale else HudColors.Scale
    val showReplay = state.sourceKind == SourceKind.REPLAY
    val stripHeight = if (wpUi.showButtons) WpStripHeight else 0.dp

    // 地図に重ねる帯: 右の操作列と、下の WP ボタン列。地図の表示枠はこれを除いた領域（方位目盛りはその縁）
    val density = LocalDensity.current
    val reserved = with(density) { HudInsets(right = SideColumnWidth.toPx(), bottom = stripHeight.toPx()) }

    Column(modifier.fillMaxSize().background(HudColors.Background)) {
        TopBar(state, wpUi.showButtons, onCycleSource, onToggleWpButtons, onToggleDisplay, onOpenSettings, onOpenDebug)
        InfoStrip(state, zone, valueColor, onCycleRate)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HudCanvas(state, Modifier.fillMaxSize(), reserved, onViewport, onPan)
            // 右の操作列: 上に ＋ / RNG / −（PAN 中は「現在地」も）、下に REPLAY の ▶ / FILE
            SideColumn(
                state, replay, onZoomIn, onZoomOut, onToggleAutoRange, onEndPan, onPickTrack, onTogglePlay, showReplay,
                Modifier.align(Alignment.TopEnd).padding(bottom = stripHeight),
            )
            if (wpUi.showButtons) {
                WpStrip(state, onOpenWpSettings, onToggleReached, onPanToWaypoint, Modifier.align(Alignment.BottomStart))
            }
            // 案内の枠: 地図の表示枠の中央に置き、その幅で折り返す
            val permissionMissing = state.sourceKind == SourceKind.LIVE &&
                (live.permission == LocationPermission.DENIED || live.permission == LocationPermission.APPROXIMATE_ONLY)
            if (permissionMissing || state.noFix) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(end = SideColumnWidth, bottom = stripHeight)
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
        profileHeight(state.settings.profileSize)?.let { h ->
            ProfileView(state, Modifier.fillMaxWidth().height(h).border(0.5.dp, HudColors.Frame))
        }
        BottomPanel(state, zone, valueColor, onToggleSourceKind)
    }
}

/** 右の操作列の幅（ボタン 52dp ＋ 余白） */
private val SideColumnWidth = 60.dp
private val SideButtonSize = 52.dp

/** 横並びの WP ボタン列の高さ */
private val WpStripHeight = 48.dp
private val WpSettingsWidth = 64.dp
private val WpButtonGap = 6.dp

/** 標高プロファイルの高さ（OFF なら null） */
private fun profileHeight(size: ProfileSize): Dp? = when (size) {
    ProfileSize.OFF -> null
    ProfileSize.SMALL -> 56.dp
    ProfileSize.MEDIUM -> 88.dp
    ProfileSize.LARGE -> 128.dp
}

/**
 * 右の操作列。頻繁に押すので大きめ（52dp 角）。上から ＋ / 縮尺の表示（タップで AUTO の ON / OFF）/ −。
 * PAN 中は縮尺の表示が「PAN」になり、その下に「現在地」（現在地の表示に戻る）。REPLAY のときは下に ▶ / FILE。
 */
@Composable
private fun SideColumn(
    state: NavState,
    replay: ReplayUiState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleAutoRange: () -> Unit,
    onEndPan: () -> Unit,
    onPickTrack: () -> Unit,
    onTogglePlay: () -> Unit,
    showReplay: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(SideColumnWidth).fillMaxHeight().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        if (showReplay) {
            val play = when {
                replay.finished -> "END"
                state.playing -> "❚❚"
                else -> "▶"
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SideButton(play, onTogglePlay, enabled = replay.ready && !replay.finished, height = 40.dp)
                SideButton("FILE", onPickTrack, height = 36.dp)
            }
        }
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
    fontSize: TextUnit = 12.sp,
) {
    val c = if (enabled) color else HudColors.WpReached
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(SideButtonSize)
            .height(height)
            .border(1.dp, c, shape)
            .background(if (inverted) c else HudColors.Background, shape)
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
            .background(HudColors.Background.copy(alpha = 0.85f))
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        val visible = state.settings.wpButtonsMax.coerceAtLeast(1)
        val listWidth = maxWidth - WpSettingsWidth - WpButtonGap
        val itemWidth = (listWidth - WpButtonGap * (visible - 1)) / visible
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        val stepPx = with(density) { (itemWidth + WpButtonGap).toPx() }
        val itemPx = with(density) { itemWidth.toPx() }
        val viewPx = with(density) { listWidth.toPx() }
        val next = state.nextWpIndex
        LaunchedEffect(next, visible, state.waypoints.size, viewPx) {
            if (next == null) return@LaunchedEffect
            // 次の WP を中央へ
            val target = next * stepPx - (viewPx - itemPx) / 2
            scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue))
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
    onToggleDisplay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, HudColors.Frame)
            // バーの空いている所を長押しすると開発用画面
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenDebug() }) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 方位ソースの選択（HYBRID / GPS / COMPASS）
        HudButton("HDG ${state.sourceMode.name}", onCycleSource)
        // WP ボタン列の表示/非表示（出ているときは反転）
        HudButton("WP", onToggleWpButtons, inverted = showWpButtons)
        Spacer(Modifier.weight(1f))
        HudButton(if (state.settings.displayMode == DisplayMode.ARC) "ARC" else "N-UP", onToggleDisplay)
        // 設定画面（開発用画面は、バーの空いている所の長押しのまま）
        HudButton("⚙", onOpenSettings)
    }
}

@Composable
private fun InfoStrip(
    state: NavState,
    zone: ZoneId,
    valueColor: Color,
    onCycleRate: () -> Unit,
) {
    val fix = state.fix
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row {
            Cell("TIME", HudFormat.time(state.nowMs, zone), HudColors.Scale)
            Cell("ALT", HudFormat.altitude(state.altM), valueColor)
            Cell(
                "RATE ${state.settings.rateWindowSec}s",
                HudFormat.rate(state.rate),
                valueColor,
                Modifier.clickable(onClick = onCycleRate),
                weight = 1.4f,
            )
        }
        Row {
            Cell("LAT/LON", HudFormat.latLon(fix?.lat, fix?.lon), valueColor)
        }
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
    Column(Modifier.weight(weight).padding(vertical = 2.dp)) {
        Text("HDG", style = Caption, maxLines = 1)
        Text(
            buildAnnotatedString {
                append(HudFormat.bearing(state.heading.deg))
                if (src.isNotEmpty()) withStyle(small) { append(" $src") }
                if (marks.isNotEmpty()) {
                    // コンパス自体の状態なので、NO FIX 中も黄色のまま
                    withStyle(small.copy(color = HudColors.Caution)) { append(" $marks") }
                }
            },
            style = Value.copy(color = valueColor),
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomPanel(state: NavState, zone: ZoneId, valueColor: Color, onToggleSourceKind: () -> Unit) {
    val next = state.nextWpIndex?.let { state.waypoints[it] }
    val deadline = state.deadlineCountdownSec
    val deadlineColor = when {
        deadline == null -> valueColor
        deadline < 0 -> HudColors.Warning
        else -> valueColor
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, HudColors.Frame)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row {
            HeadingCell(state, valueColor, weight = 2f)
            Cell("GS", HudFormat.speedKmh(state.groundSpeedMps), valueColor)
        }
        Row {
            Cell("NEXT ${next?.name ?: ""}".trim(), nextText(state), if (next != null && !state.noFix) HudColors.Active else valueColor, weight = 2f)
            Cell("ETA", HudFormat.time(state.etaMs, zone), valueColor)
        }
        Row {
            Cell("TGT", HudFormat.countdown(state.targetCountdownSec), valueColor)
            Cell("DDL", HudFormat.countdown(deadline), deadlineColor)
            // タップで LIVE ⇔ REPLAY
            Cell(
                "INPUT ⇄",
                if (state.sourceKind == SourceKind.LIVE) "LIVE" else "REPLAY " + if (state.playing) "▶" else "❚❚",
                HudColors.Scale,
                Modifier
                    .border(1.dp, HudColors.Frame, RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleSourceKind)
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

private fun nextText(state: NavState): String {
    if (state.nextWpBearingDeg == null || state.nextWpDistanceM == null) return HudFormat.NONE
    return "${HudFormat.bearing(state.nextWpBearingDeg)} ${HudFormat.distance(state.nextWpDistanceM)}"
}

@Composable
private fun RowScope.Cell(caption: String, value: String, color: Color, modifier: Modifier = Modifier, weight: Float = 1f) {
    Column(modifier.weight(weight).padding(vertical = 2.dp)) {
        Text(caption, style = Caption, maxLines = 1)
        Text(value, style = Value.copy(color = color), maxLines = 1)
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
) {
    val color = if (enabled) HudColors.Scale else HudColors.WpReached
    Box(
        (if (fill) Modifier.fillMaxWidth() else Modifier)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .background(if (inverted) color else HudColors.Background, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ButtonText.copy(color = if (inverted) HudColors.Background else color, fontSize = if (small) 12.sp else 13.sp))
    }
}

