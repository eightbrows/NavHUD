package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.ColorTheme
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.ProfileSize
import io.github.eightbrows.navhud.core.nav.RangeAuto
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.view.HudFormat
import java.util.Locale
import kotlin.math.roundToInt

private val SectionTitle get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val RowLabel get() = TextStyle(fontSize = 14.sp, color = HudColors.Scale)
private val RowNote get() = TextStyle(fontSize = 12.sp, color = HudColors.ScaleDim)
private val ValueText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = HudColors.Scale)

/**
 * 設定画面。NavSettings のすべての値と INPUT を変えられる。変えたらすぐ画面に反映し、保存する。
 * セクション: 表示 / 方位 / 縮尺 / WP / 測位・入力 / その他。
 */
@Composable
fun SettingsScreen(
    settings: NavSettings,
    input: SourceKind,
    onChange: ((NavSettings) -> NavSettings) -> Unit,
    onInput: (SourceKind) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = settings
    Column(
        modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudButton("← 戻る", onBack)
            Text("設定", style = SectionTitle.copy(fontSize = 18.sp))
        }
        Text("変更はすぐ画面に反映され、保存されます。", style = RowNote)

        Section("表示") {
            Choice("表示モード", listOf("ARC" to DisplayMode.ARC, "North Up" to DisplayMode.NORTH_UP), s.displayMode) { v ->
                onChange { it.copy(displayMode = v) }
            }
            Choice(
                "色テーマ（目盛り・距離環・文字・自機）",
                listOf("白" to ColorTheme.WHITE, "緑" to ColorTheme.GREEN, "琥珀" to ColorTheme.AMBER),
                s.colorTheme,
            ) { v -> onChange { it.copy(colorTheme = v) } }
            Choice(
                "ARC の自機の位置",
                listOf("標準" to OwnshipPosition.STANDARD, "高め" to OwnshipPosition.HIGH),
                s.ownshipPosition,
                note = "WP ボタン列の上端から 標準 110dp / 高め 170dp。高めにすると後方の WP や矢印に余裕ができます",
            ) { v -> onChange { it.copy(ownshipPosition = v) } }
            Choice(
                "標高プロファイル",
                listOf("OFF" to ProfileSize.OFF, "小" to ProfileSize.SMALL, "中" to ProfileSize.MEDIUM, "大" to ProfileSize.LARGE),
                s.profileSize,
                note = "WP ボタン列の下。現在地から次の目標、その先の目標までの標高",
            ) { v -> onChange { it.copy(profileSize = v) } }
            Stepper("標高オフセット", "%.0f m".format(Locale.US, s.altOffsetM), note = "標高 = GPS の楕円体高 − これ") { d ->
                onChange { it.copy(altOffsetM = (it.altOffsetM + d).coerceIn(-200.0, 200.0)) }
            }
        }

        Section("方位") {
            Choice(
                "方位ソース",
                listOf("GPS" to SourceMode.GPS, "HYBRID" to SourceMode.HYBRID, "COMPASS" to SourceMode.COMPASS),
                s.sourceMode,
                note = "車は GPS、歩行は HYBRID か COMPASS",
            ) { v -> onChange { it.copy(sourceMode = v) } }
            Stepper(
                "保持に入る速度", kmh(s.holdEnterSpeedMps),
                note = "これ未満で GPS 方位を保持（HLD）",
            ) { d ->
                onChange {
                    val v = (it.holdEnterSpeedMps + d * STEP_MPS).round1().coerceIn(0.5f, 10f)
                    it.copy(holdEnterSpeedMps = v, holdExitSpeedMps = maxOf(it.holdExitSpeedMps, (v + STEP_MPS).round1()))
                }
            }
            Stepper("保持を解く速度", kmh(s.holdExitSpeedMps), note = "これを超えたら GPS 方位に戻る") { d ->
                onChange {
                    val v = (it.holdExitSpeedMps + d * STEP_MPS).round1().coerceIn((it.holdEnterSpeedMps + STEP_MPS).round1(), 15f)
                    it.copy(holdExitSpeedMps = v)
                }
            }
            Stepper("GPS の水平精度の上限", "%.0f m".format(Locale.US, s.maxGpsAccM)) { d ->
                onChange { it.copy(maxGpsAccM = (it.maxGpsAccM + d).coerceIn(3f, 100f)) }
            }
            Stepper("GPS の方位の精度の上限", "%.0f°".format(Locale.US, s.maxGpsBearingAccDeg), note = "値を出している端末のみ") { d ->
                onChange { it.copy(maxGpsBearingAccDeg = (it.maxGpsBearingAccDeg + d * 5).coerceIn(5f, 90f)) }
            }
        }

        Section("縮尺") {
            Label("使う段（1つ以上）")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (km in RangeAuto.ALL_STEPS_KM) {
                    val on = km in s.rangeStepsKm
                    Chip(HudFormat.rangeStep(km * 1000), on) {
                        onChange {
                            val steps = if (on) it.rangeStepsKm - km else (it.rangeStepsKm + km).sorted()
                            if (steps.isEmpty()) it else it.copy(rangeStepsKm = steps)
                        }
                    }
                }
            }
            Choice(
                "起動時の縮尺",
                s.rangeStepsKm.map { HudFormat.rangeStep(it * 1000) to it },
                s.rangeStepsKm.minByOrNull { kotlin.math.abs(it - s.initialRangeKm) },
            ) { v -> onChange { it.copy(initialRangeKm = v) } }
            Toggle("起動時に AUTO", s.autoRange, note = "次の WP が画面に収まる最小の段を自動で選ぶ") { v ->
                onChange { it.copy(autoRange = v) }
            }
            Stepper("AUTO で狭めるまでの時間", "${s.autoRangeZoomInDelaySec} 秒", note = "広げる方向はすぐ切り替える") { d ->
                onChange { it.copy(autoRangeZoomInDelaySec = (it.autoRangeZoomInDelaySec + d).coerceIn(0, 30)) }
            }
            Stepper("PAN から現在地へ戻るまで", "${s.panReturnSec} 秒", note = "地図をドラッグしたあと、操作がないまま この時間で戻る") { d ->
                onChange { it.copy(panReturnSec = (it.panReturnSec + d * 5).coerceIn(NavSettings.PAN_RETURN_SEC_RANGE)) }
            }
        }

        Section("WP") {
            Choice(
                "到達半径（全体）",
                NavSettings.REACH_RADIUS_CHOICES_M.map { "%.0f m".format(Locale.US, it) to it },
                s.reachRadiusM,
                note = "WP ごとの到達半径があればそちらを使う",
            ) { v -> onChange { it.copy(reachRadiusM = v) } }
            Toggle("通過判定", s.passDetection, note = "最接近したあと離れていったら到達にする") { v ->
                onChange { it.copy(passDetection = v) }
            }
            Stepper("通過判定: 最接近距離の上限", "%.0f m".format(Locale.US, s.passMaxApproachM), note = "WP ごとの到達半径 × 3 の方が大きければそちら") { d ->
                onChange { it.copy(passMaxApproachM = (it.passMaxApproachM + d * 50).coerceIn(50.0, 2000.0)) }
            }
            Stepper("通過判定: 離れたとみなす距離", "+%.0f m".format(Locale.US, s.passDepartM)) { d ->
                onChange { it.copy(passDepartM = (it.passDepartM + d * 10).coerceIn(10.0, 500.0)) }
            }
            Stepper("通過判定: 離れた状態が続く時間", "${s.passHoldSec} 秒") { d ->
                onChange { it.copy(passHoldSec = (it.passHoldSec + d).coerceIn(1, 60)) }
            }
            Stepper("WP ボタン列に見せる数", "${s.wpButtonsMax} 個", note = "自機の下に横並び。超える分は左右にスクロール") { d ->
                onChange { it.copy(wpButtonsMax = (it.wpButtonsMax + d).coerceIn(NavSettings.WP_BUTTONS_MAX_RANGE)) }
            }
            Stepper("HUD に描く WP の数", "${s.hudWpCount} 個", note = "次の WP から先。直前に到達した WP は1つだけ薄く残す") { d ->
                onChange { it.copy(hudWpCount = (it.hudWpCount + d).coerceIn(NavSettings.HUD_WP_COUNT_RANGE)) }
            }
        }

        Section("測位・入力") {
            Choice("INPUT", listOf("LIVE（GPS）" to SourceKind.LIVE, "REPLAY（track.csv）" to SourceKind.REPLAY), input) { v ->
                onInput(v)
            }
            Stepper("NO FIX とみなす時間", "${s.noFixTimeoutSec} 秒") { d ->
                onChange { it.copy(noFixTimeoutSec = (it.noFixTimeoutSec + d).coerceIn(3, 120)) }
            }
            Choice("RATE の窓", NavSettings.RATE_WINDOW_CHOICES_SEC.map { "$it 秒" to it }, s.rateWindowSec) { v ->
                onChange { it.copy(rateWindowSec = v) }
            }
        }

        Section("その他") {
            Toggle("画面常時点灯", s.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            Toggle("前回の WP リストを自動で開く", s.autoOpenLastList, note = "起動時の選択を出さずに、最後に読み込んだか書き出したリストを開く") { v ->
                onChange { it.copy(autoOpenLastList = v) }
            }
            Row(Modifier.padding(top = 6.dp)) { HudButton("設定を初期値に戻す", onReset) }
        }
    }
}

/** 速度の刻み（0.1 m/s ≒ 0.36 km/h） */
private const val STEP_MPS = 0.1f

private fun kmh(mps: Float) = "%.1f km/h（%.1f m/s）".format(Locale.US, mps * 3.6f, mps)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, HudColors.Frame, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = SectionTitle)
        content()
    }
}

@Composable
private fun Label(text: String, note: String? = null) {
    Column {
        Text(text, style = RowLabel)
        note?.let { Text(it, style = RowNote) }
    }
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<String, T>>, selected: T?, note: String? = null, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label, note)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((text, value) in options) Chip(text, value == selected) { onSelect(value) }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .border(1.dp, if (on) HudColors.Scale else HudColors.WpDisabled, shape)
            .background(if (on) HudColors.Scale else HudColors.Background, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = ValueText.copy(color = if (on) HudColors.Background else HudColors.Scale, fontSize = 13.sp))
    }
}

@Composable
private fun Stepper(label: String, value: String, note: String? = null, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { Label(label, note) }
        HudButton("−", { onStep(-1) })
        Text(value, style = ValueText, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        HudButton("＋", { onStep(1) })
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Label(label, note) }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HudColors.Background,
                checkedTrackColor = HudColors.Scale,
                checkedBorderColor = HudColors.Scale,
                uncheckedThumbColor = HudColors.ScaleDim,
                uncheckedTrackColor = HudColors.Background,
                uncheckedBorderColor = HudColors.WpDisabled,
            ),
        )
    }
}

/** 0.1 刻みに丸める（足し算の誤差をためない） */
private fun Float.round1() = (this * 10).roundToInt() / 10f
