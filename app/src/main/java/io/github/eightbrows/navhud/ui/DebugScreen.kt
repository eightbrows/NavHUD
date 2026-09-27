package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.NavState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color(0xFFE0E0E0))
private val Stale = Mono.copy(color = Color(0xFF808080))
private val Warn = Mono.copy(color = Color(0xFFFFB300))
private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** 開発用画面（ステップ3の確認用画面）。NavState の値を文字で並べる。メイン画面の上部バーの長押しで開く。 */
@Composable
fun DebugScreen(
    state: NavState,
    replay: ReplayUiState,
    onPickTrack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSourceMode: (SourceMode) -> Unit,
    onToggleWp: (Int) -> Unit,
    onClose: () -> Unit,
    canLoadTemporaryWaypoints: Boolean,
    onLoadTemporaryWaypoints: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    // NO FIX 中は最後の値をグレーで出し続ける（§6.1）
    val value = if (state.noFix) Stale else Mono

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose) { Text("メイン画面へ") }
            // WP リストが空のときだけ、トラックから仮 WP を入れられる
            OutlinedButton(onClick = onLoadTemporaryWaypoints, enabled = canLoadTemporaryWaypoints) { Text("仮 WP を入れる") }
            OutlinedButton(onClick = onPickTrack) { Text("track.csv を選ぶ") }
            Button(onClick = onTogglePlay, enabled = replay.ready && !replay.finished) {
                Text(
                    when {
                        replay.finished -> "再生終了"
                        state.playing -> "一時停止"
                        else -> "再生"
                    },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceMode.entries.forEach { m ->
                if (m == state.sourceMode) {
                    Button(onClick = { onSourceMode(m) }) { Text(m.name) }
                } else {
                    OutlinedButton(onClick = { onSourceMode(m) }) { Text(m.name) }
                }
            }
        }

        Line("FILE", replay.fileName ?: "（未選択）", Mono)
        if (replay.fileName != null) Line("", "${replay.fixCount} 点 / スキップ ${replay.skippedLines} 行", Mono)
        if (replay.loading) Line("", "読み込み中…", Warn)
        replay.message?.let { Line("", it, Warn) }

        Line("INPUT", "${state.sourceKind}  ${if (state.playing) "PLAY" else "PAUSE"}", Mono)
        Line("TIME", state.nowMs?.let { local(it, zone) } ?: "---", Mono)
        if (state.noFix) Line("", "NO FIX", Warn)
        val fix = state.fix
        Line("FIX", fix?.let { local(it.timeMs, zone) } ?: "---", value)
        Line("LAT/LON", fix?.let { "%.5f, %.5f".format(Locale.US, it.lat, it.lon) } ?: "---", value)
        Line("ALT", state.altM?.let { "%.0f m".format(Locale.US, it) } ?: "---", value)
        Line("GS", state.groundSpeedMps?.let { "%.1f km/h".format(Locale.US, it * 3.6) } ?: "---", value)
        Line("HDG", state.heading.deg?.let { "%03.0f°".format(Locale.US, it) } ?: "---", value)
        Line("HDG SRC", "${state.heading.src}  (mode ${state.sourceMode})", value)
        val rate = state.rate
        Line(
            "RATE",
            rate?.let {
                "%ds  %.0f m  %+.0f m  %.1f km/h".format(
                    Locale.US, it.windowSec, it.distanceM, it.altDiffM ?: 0.0, it.avgSpeedMps * 3.6,
                )
            } ?: "---",
            value,
        )

        val next = state.nextWpIndex
        Line("NEXT WP", next?.let { state.waypoints[it].name } ?: "---", value)
        Line(
            "BRG/DIST",
            if (state.nextWpBearingDeg != null && state.nextWpDistanceM != null) {
                "%03.0f°  %.2f km".format(Locale.US, state.nextWpBearingDeg, state.nextWpDistanceM / 1000)
            } else {
                "---"
            },
            value,
        )
        Line("ETA", state.etaMs?.let { local(it, zone) } ?: "---", value)
        Line("TARGET", state.targetCountdownSec?.let(::countdown) ?: "---", value)
        Line(
            "DEADLINE",
            state.deadlineCountdownSec?.let(::countdown) ?: "---",
            if ((state.deadlineCountdownSec ?: 0) < 0) Warn else value,
        )

        Text("WAYPOINTS（タップで到達済みを切り替え）", style = Mono, modifier = Modifier.padding(top = 8.dp))
        state.waypoints.forEachIndexed { i, wp ->
            val mark = when {
                i == next -> "▶"
                wp.reached -> "✓"
                else -> " "
            }
            val times = listOfNotNull(
                wp.targetTime?.let { "目標 $it" },
                wp.deadlineTime?.let { "締切 $it" },
            ).joinToString("  ")
            OutlinedButton(onClick = { onToggleWp(i) }, enabled = wp.enabled) {
                Text("$mark ${wp.name}  %.5f, %.5f  $times".format(Locale.US, wp.lat, wp.lon))
            }
        }
    }
}

@Composable
private fun Line(label: String, text: String, style: TextStyle) {
    Row {
        Text(label, style = Mono.copy(color = Color(0xFF4FC3F7)), modifier = Modifier.width(96.dp))
        Text(text, style = style)
    }
}

private fun local(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).format(TimeFmt)

private fun countdown(sec: Long): String {
    val a = abs(sec)
    return "%s%d:%02d:%02d".format(Locale.US, if (sec < 0) "-" else "+", a / 3600, a / 60 % 60, a % 60)
}
