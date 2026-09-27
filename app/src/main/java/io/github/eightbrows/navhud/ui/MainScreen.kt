package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.ScreenSide
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudFormat
import io.github.eightbrows.navhud.core.view.HudInsets
import java.time.ZoneId

private val Caption = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HudColors.Caption)
private val Value = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val ButtonText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HudColors.Scale)

/** メイン画面（§6.1〜6.3）。NavState だけを見て描く。 */
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
    live: LiveUiState,
    onToggleSourceKind: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    // LOST 中は最後の値をグレーで出し続ける
    val valueColor = if (state.positionLost) HudColors.Stale else HudColors.Scale
    val buttonsRight = state.settings.wpButtonsSide == ScreenSide.RIGHT
    val showReplay = state.sourceKind == SourceKind.REPLAY

    // 画面外の矢印を置かない帯。ボタン列を出しているときはリプレイ操作もボタン列の下に入るので、その側の帯だけ。
    // ボタン列を隠しているときは、リプレイ操作のある下端の帯
    val density = LocalDensity.current
    val reserved = with(density) {
        val column = if (wpUi.showButtons) WpColumnWidth.toPx() else 0f
        HudInsets(
            left = if (!buttonsRight) column else 0f,
            right = if (buttonsRight) column else 0f,
            bottom = if (showReplay && !wpUi.showButtons) ReplayBandHeight.toPx() else 0f,
        )
    }

    Column(modifier.fillMaxSize().background(HudColors.Background)) {
        TopBar(state, wpUi.showButtons, onCycleSource, onToggleWpButtons, onToggleDisplay, onOpenDebug)
        InfoStrip(state, zone, valueColor, onCycleRate)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HudCanvas(state, Modifier.fillMaxSize(), reserved)
            if (wpUi.showButtons) {
                WpButtonColumn(
                    state,
                    onOpenWpSettings,
                    onToggleReached,
                    Modifier.align(if (buttonsRight) Alignment.TopEnd else Alignment.TopStart),
                ) {
                    if (showReplay) ReplayControls(state, replay, onPickTrack, onTogglePlay, stacked = true)
                }
            } else if (showReplay) {
                ReplayControls(
                    state, replay, onPickTrack, onTogglePlay,
                    Modifier.align(if (buttonsRight) Alignment.BottomEnd else Alignment.BottomStart).padding(8.dp),
                )
            }
            // 案内の枠はボタン列より前面に描く
            val permissionMissing = state.sourceKind == SourceKind.LIVE &&
                (live.permission == LocationPermission.DENIED || live.permission == LocationPermission.APPROXIMATE_ONLY)
            val center = Modifier.align(Alignment.Center).padding(horizontal = 16.dp)
            if (permissionMissing) {
                PermissionBox(live.permission, onRequestPermission, onOpenAppSettings, onToggleSourceKind, center)
            } else if (state.positionLost) {
                LostBox(lostHint(state, replay, live), center)
            }
        }
        BottomPanel(state, zone, valueColor, onToggleSourceKind)
    }
}

private val WpColumnWidth = 84.dp
private val ReplayBandHeight = 46.dp

/**
 * WP ボタン列（§6.4）。最上部に固定の「WP設定」、その下は下から上へ WP1, WP2…（スクロール可能）。
 * タップで到達済みを個別に切り替える（到達済みは色反転）。無効 WP はグレーで押せない。
 */
@Composable
private fun WpButtonColumn(
    state: NavState,
    onOpenSettings: () -> Unit,
    onToggleReached: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .width(WpColumnWidth)
            .fillMaxHeight()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WpColumnButton("WP設定", onOpenSettings, HudColors.Scale, inverted = false, enabled = true)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in state.waypoints.indices.reversed()) {
                    val wp = state.waypoints[i]
                    val color = when {
                        !wp.enabled -> HudColors.WpDisabled
                        i == state.nextWpIndex -> HudColors.Active
                        else -> HudColors.Wp
                    }
                    WpColumnButton(wp.name, { onToggleReached(i) }, color, inverted = wp.reached && wp.enabled, enabled = wp.enabled)
                }
            }
        }
        bottom()
    }
}

@Composable
private fun WpColumnButton(text: String, onClick: () -> Unit, color: Color, inverted: Boolean, enabled: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, color, shape)
            .background(if (inverted) color else HudColors.Background, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
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
        HudButton("SRC ${state.sourceMode.name}", onCycleSource)
        // WP ボタン列の表示/非表示（出ているときは反転）
        HudButton("WP", onToggleWpButtons, inverted = showWpButtons)
        Spacer(Modifier.weight(1f))
        HudButton(if (state.settings.displayMode == DisplayMode.ARC) "ARC" else "N-UP", onToggleDisplay)
    }
}

@Composable
private fun InfoStrip(state: NavState, zone: ZoneId, valueColor: Color, onCycleRate: () -> Unit) {
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
            Cell("LAT/LON", HudFormat.latLon(fix?.lat, fix?.lon), valueColor, weight = 3.4f)
        }
    }
}

/** SRC の表示。コンパスのときは CAL（精度が低い）・MAG（偏角が分からず磁北のまま）の印を付ける。 */
private fun srcText(state: NavState): String {
    val src = state.heading.src
    val q = state.compass
    if (src != HeadingSrc.COMPASS || q == null) return src.name
    return listOfNotNull("COMPASS", "MAG".takeIf { q.declinationUnknown }, "CAL".takeIf { q.lowAccuracy }).joinToString(" ")
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
            Cell("HDG", HudFormat.bearing(state.heading.deg), valueColor)
            Cell("GS", HudFormat.speedKmh(state.groundSpeedMps), valueColor)
            val marked = state.compass?.let { it.lowAccuracy || it.declinationUnknown } == true && state.heading.src == HeadingSrc.COMPASS
            Cell("SRC", srcText(state), if (marked && !state.positionLost) HudColors.Caution else valueColor)
        }
        Row {
            Cell("NEXT ${next?.name ?: ""}".trim(), nextText(state), if (next != null && !state.positionLost) HudColors.Active else valueColor, weight = 2f)
            Cell("ETA", HudFormat.time(state.etaMs, zone), valueColor)
        }
        Row {
            Cell("TGT", HudFormat.countdown(state.targetCountdownSec), valueColor)
            Cell("DDL", HudFormat.countdown(deadline), deadlineColor)
            // タップで LIVE ⇔ REPLAY
            Cell(
                "SOURCE ⇄",
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
private fun LostBox(hint: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(HudColors.Background)
            .border(2.dp, HudColors.Warning)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("POSITION LOST", style = Value.copy(color = HudColors.Warning, fontWeight = FontWeight.Bold, fontSize = 18.sp))
        hint?.let { Text(it, style = Caption.copy(color = HudColors.Caution), textAlign = TextAlign.Center) }
    }
}

/** POSITION LOST の枠に添える案内。 */
private fun lostHint(state: NavState, replay: ReplayUiState, live: LiveUiState): String? =
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
private fun ReplayControls(
    state: NavState,
    replay: ReplayUiState,
    onPickTrack: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val play = when {
        replay.finished -> "END"
        state.playing -> "❚❚"
        else -> "▶"
    }
    val playEnabled = replay.ready && !replay.finished
    if (stacked) {
        // ボタン列の一番下に、列の幅いっぱいで縦に並べる
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudButton(play, onTogglePlay, enabled = playEnabled, small = true, fill = true)
            HudButton("FILE", onPickTrack, small = true, fill = true)
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HudButton("FILE", onPickTrack, small = true)
            HudButton(play, onTogglePlay, enabled = playEnabled, small = true)
        }
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
