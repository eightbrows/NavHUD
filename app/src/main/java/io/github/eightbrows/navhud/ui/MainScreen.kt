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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudFormat
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
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    // LOST 中は最後の値をグレーで出し続ける
    val valueColor = if (state.positionLost) HudColors.Stale else HudColors.Scale

    Column(modifier.fillMaxSize().background(HudColors.Background)) {
        TopBar(state, onCycleSource, onToggleDisplay, onOpenDebug)
        InfoStrip(state, zone, valueColor, onCycleRate)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HudCanvas(state, Modifier.fillMaxSize())
            if (state.positionLost) LostBox(replay, Modifier.align(Alignment.Center))
            if (state.sourceKind == SourceKind.REPLAY) {
                ReplayControls(state, replay, onPickTrack, onTogglePlay, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        BottomPanel(state, zone, valueColor)
    }
}

@Composable
private fun TopBar(state: NavState, onCycleSource: () -> Unit, onToggleDisplay: () -> Unit, onOpenDebug: () -> Unit) {
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
        // WP メニューはステップ5（今は枠だけ）
        HudButton("WP", onClick = {}, enabled = false)
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

@Composable
private fun BottomPanel(state: NavState, zone: ZoneId, valueColor: Color) {
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
            Cell("SRC", state.heading.src.name, valueColor)
        }
        Row {
            Cell("NEXT ${next?.name ?: ""}".trim(), nextText(state), if (next != null && !state.positionLost) HudColors.Active else valueColor, weight = 2f)
            Cell("ETA", HudFormat.time(state.etaMs, zone), valueColor)
        }
        Row {
            Cell("TGT", HudFormat.countdown(state.targetCountdownSec), valueColor)
            Cell("DDL", HudFormat.countdown(deadline), deadlineColor)
            Cell(
                state.sourceKind.name,
                if (state.sourceKind == SourceKind.LIVE) "LIVE" else if (state.playing) "PLAY" else "PAUSE",
                HudColors.Scale,
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
private fun LostBox(replay: ReplayUiState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(HudColors.Background)
            .border(2.dp, HudColors.Warning)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("POSITION LOST", style = Value.copy(color = HudColors.Warning, fontWeight = FontWeight.Bold, fontSize = 18.sp))
        val hint = when {
            replay.loading -> "読み込み中…"
            replay.message != null -> replay.message
            replay.fileName == null -> "FILE で track.csv を選んでください"
            else -> null
        }
        hint?.let { Text(it, style = Caption.copy(color = HudColors.Caution), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ReplayControls(
    state: NavState,
    replay: ReplayUiState,
    onPickTrack: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HudButton("FILE", onPickTrack, small = true)
        HudButton(
            when {
                replay.finished -> "END"
                state.playing -> "❚❚"
                else -> "▶"
            },
            onTogglePlay,
            enabled = replay.ready && !replay.finished,
            small = true,
        )
    }
}

@Composable
private fun HudButton(text: String, onClick: () -> Unit, enabled: Boolean = true, small: Boolean = false) {
    val color = if (enabled) HudColors.Scale else HudColors.WpReached
    Box(
        Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .background(HudColors.Background, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 5.dp),
    ) {
        Text(text, style = ButtonText.copy(color = color, fontSize = if (small) 12.sp else 13.sp))
    }
}
