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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import io.github.eightbrows.navhud.core.nav.TravelMode
import io.github.eightbrows.navhud.core.nav.ReachProfile
import io.github.eightbrows.navhud.core.nav.editReach
import io.github.eightbrows.navhud.core.nav.selectTravelMode
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
    /** 今の縮尺の段 [m]（「狭め始める距離」の例に使う） */
    rangeM: Double,
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
            val colors = listOf("白" to ColorTheme.WHITE, "緑" to ColorTheme.GREEN, "琥珀" to ColorTheme.AMBER)
            Choice("UI の色（ボタン・数値・標高プロファイル）", colors, s.uiTheme) { v -> onChange { it.copy(uiTheme = v) } }
            Choice("地図の色（距離環・目盛り・自機・WP・軌跡）", colors, s.mapTheme) { v -> onChange { it.copy(mapTheme = v) } }
            PercentSlider(
                "ボタンの不透明度", s.buttonOpacityPct,
                note = "地図に重ねるボタン（上部バー・操作列・WP ボタン列・再生の帯）。ON の塗りはさらに半分",
            ) { v -> onChange { it.copy(buttonOpacityPct = v) } }
            PercentSlider(
                "数値の不透明度（お試し）", s.numbersOpacityPct,
                note = "上の4行の数値。警告の表示（締切超過・CAL / MAG）は常に 100%",
            ) { v -> onChange { it.copy(numbersOpacityPct = v) } }
            PercentSlider(
                "読み込んだ軌跡の明るさ", s.trackBrightnessPct,
                note = "REPLAY のトラック全体の線（グレー）。走った跡の線は変わらない",
                choices = NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT,
            ) { v -> onChange { it.copy(trackBrightnessPct = v) } }
            Choice(
                "ARC の自機の位置",
                listOf("標準" to OwnshipPosition.STANDARD, "高め" to OwnshipPosition.HIGH),
                s.ownshipPosition,
                note = "WP ボタン列の上端から 標準 24dp / 高め 84dp（LIVE・REPLAY とも）。高めにすると後方の WP や矢印に余裕ができます",
            ) { v -> onChange { it.copy(ownshipPosition = v) } }
            Choice(
                "標高プロファイル",
                listOf("OFF" to ProfileSize.OFF, "小" to ProfileSize.SMALL, "中" to ProfileSize.MEDIUM, "大" to ProfileSize.LARGE),
                s.profileSize,
                note = "WP ボタン列の下。現在地から次の目標、その先の目標までの標高",
            ) { v -> onChange { it.copy(profileSize = v) } }
            Stepper("標高オフセット", "%.0f m".format(Locale.US, s.altOffsetM), note = "標高 = GPS の楕円体高 − これ") { d ->
                onChange { it.copy(altOffsetM = (it.altOffsetM + d).coerceIn(NavSettings.ALT_OFFSET_M_RANGE)) }
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
                    val v = (it.holdEnterSpeedMps + d * STEP_MPS).round1().coerceIn(NavSettings.HOLD_ENTER_SPEED_MPS_RANGE)
                    it.copy(holdEnterSpeedMps = v, holdExitSpeedMps = maxOf(it.holdExitSpeedMps, (v + STEP_MPS).round1()))
                }
            }
            Stepper("保持を解く速度", kmh(s.holdExitSpeedMps), note = "これを超えたら GPS 方位に戻る") { d ->
                onChange {
                    val v = (it.holdExitSpeedMps + d * STEP_MPS).round1().coerceIn((it.holdEnterSpeedMps + STEP_MPS).round1(), NavSettings.HOLD_EXIT_SPEED_MAX_MPS)
                    it.copy(holdExitSpeedMps = v)
                }
            }
            Stepper("GPS の水平精度の上限", "%.0f m".format(Locale.US, s.maxGpsAccM)) { d ->
                onChange { it.copy(maxGpsAccM = (it.maxGpsAccM + d).coerceIn(NavSettings.MAX_GPS_ACC_M_RANGE)) }
            }
            Stepper("GPS の方位の精度の上限", "%.0f°".format(Locale.US, s.maxGpsBearingAccDeg), note = "値を出している端末のみ") { d ->
                onChange { it.copy(maxGpsBearingAccDeg = (it.maxGpsBearingAccDeg + d * 5).coerceIn(NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE)) }
            }
        }

        Section("縮尺") {
            Label("使う段（1つ以上。表示は自機から1つ目の距離環の距離）")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (km in RangeAuto.ALL_STEPS_KM) {
                    val on = km in s.rangeStepsKm
                    Chip(HudFormat.rangeLabel(km * 1000), on) {
                        onChange {
                            val steps = if (on) it.rangeStepsKm - km else (it.rangeStepsKm + km).sorted()
                            if (steps.isEmpty()) it else it.copy(rangeStepsKm = steps)
                        }
                    }
                }
            }
            Choice(
                "起動時の縮尺",
                s.rangeStepsKm.map { HudFormat.rangeLabel(it * 1000) to it },
                s.rangeStepsKm.minByOrNull { kotlin.math.abs(it - s.initialRangeKm) },
            ) { v -> onChange { it.copy(initialRangeKm = v) } }
            // AUTO の下限・上限: 使う段から選ぶ（下限 ≤ 上限）。表示は1つ目の距離環の距離
            val (lo, hi) = RangeAuto.limitsKm(s.rangeStepsKm, s.autoMinRangeKm, s.autoMaxRangeKm)
            Choice(
                "AUTO の下限",
                s.rangeStepsKm.filter { it <= hi }.map { HudFormat.rangeLabel(it * 1000) to it },
                lo,
                note = "これより狭くしない",
            ) { v -> onChange { it.copy(autoMinRangeKm = v) } }
            Choice(
                "AUTO の上限",
                s.rangeStepsKm.filter { it >= lo }.map { HudFormat.rangeLabel(it * 1000) to it },
                hi,
                note = "これより広くしない。次の WP が入らなければ画面の端の矢印で示す",
            ) { v -> onChange { it.copy(autoMaxRangeKm = v) } }
            Toggle("起動時に AUTO", s.autoRange, note = "次の WP が画面に収まる段を、下限〜上限の中で自動で選ぶ") { v ->
                onChange { it.copy(autoRange = v) }
            }
            Stepper("AUTO で狭めるまでの時間", "${s.autoRangeZoomInDelaySec} 秒", note = "広げる方向はすぐ切り替える") { d ->
                onChange { it.copy(autoRangeZoomInDelaySec = (it.autoRangeZoomInDelaySec + d).coerceIn(NavSettings.AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE)) }
            }
            Stepper(
                "WP を通り過ぎてから縮尺を変えるまで", "${s.autoHoldAfterWpSec} 秒",
                note = "到達した WP を通り過ぎるまでと、通り過ぎてからこの秒数は縮尺を変えない（真横通過・手動は到達してから数える。矢印は出す）",
            ) { d ->
                onChange {
                    val r = NavSettings.AUTO_HOLD_AFTER_WP_SEC_RANGE
                    it.copy(autoHoldAfterWpSec = (it.autoHoldAfterWpSec + d).coerceIn(r.first, r.last))
                }
            }
            // 今の段での例: 「今の段 R1 500m: WP まで 650m 以内」（R1 = 1つ目の距離環 = 段の 1/2）
            val hasNarrower = s.rangeStepsKm.any { it >= lo && it < rangeM / 1000 - 1e-9 }
            val example = if (hasNarrower) {
                "今の段 R1 ${HudFormat.rangeLabel(rangeM)}: WP まで ${HudFormat.rangeStep(s.autoZoomInDistRatio * rangeM / 2)} 以内"
            } else {
                "今の段 R1 ${HudFormat.rangeLabel(rangeM)} より狭い段はありません（AUTO の下限）"
            }
            Stepper(
                "AUTO で狭め始める距離", "%.1f 倍".format(Locale.US, s.autoZoomInDistRatio),
                note = "今の1つ目の距離環の何倍以内で狭め始める。$example",
            ) { d ->
                onChange {
                    val c = NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES
                    val i = c.indexOfFirst { v -> kotlin.math.abs(v - it.autoZoomInDistRatio) < 1e-9 }.coerceAtLeast(0)
                    it.copy(autoZoomInDistRatio = c[(i + d).coerceIn(0, c.lastIndex)])
                }
            }
            Stepper("PAN から現在地へ戻るまで", "${s.panReturnSec} 秒", note = "地図をドラッグしたあと、操作がないまま この時間で戻る") { d ->
                onChange { it.copy(panReturnSec = (it.panReturnSec + d * 5).coerceIn(NavSettings.PAN_RETURN_SEC_RANGE)) }
            }
        }

        Section("WP") {
            // 到達判定の値: 自動車は推奨値で固定（表示だけ）、カスタム1〜3 は枠ごとに保存する
            val custom = s.travelMode != TravelMode.CAR
            fun judge(f: (ReachProfile) -> ReachProfile) = onChange { it.editReach(f) }
            Choice(
                "移動手段",
                listOf("自動車" to TravelMode.CAR, "カスタム1" to TravelMode.CUSTOM1, "カスタム2" to TravelMode.CUSTOM2, "カスタム3" to TravelMode.CUSTOM3),
                s.travelMode,
                note = if (custom) "下の値はこの枠に保存される（ほかの枠・自動車に切り替えても残る）"
                else "自動車は推奨値で固定（変えるときはカスタム1〜3 を選ぶ）",
            ) { v -> onChange { it.selectTravelMode(v) } }
            if (custom) HudButton("自動車の値に戻す", { onChange { it.editReach { ReachProfile.CAR } } })
            Choice(
                "到着半径（全体）",
                NavSettings.REACH_RADIUS_CHOICES_M.map { "%.0f m".format(Locale.US, it) to it },
                s.reachRadiusM,
                note = "入ったら到達（停車・目的地そのものへ行く場合）。WP ごとの到達半径があればそちらを使う",
                enabled = custom,
            ) { v -> judge { it.copy(reachRadiusM = v) } }
            Toggle("真横通過", s.sidePass, note = "走行中に WP が真横か後ろになり、いちばん近づいた距離から離れたら到達", enabled = custom) { v ->
                judge { it.copy(sidePass = v) }
            }
            Stepper("真横通過: WP までの距離", "%.0f m 以内".format(Locale.US, s.sidePassMaxM), enabled = custom) { d ->
                judge { it.copy(sidePassMaxM = (it.sidePassMaxM + d * 10).coerceIn(NavSettings.SIDE_PASS_MAX_M_RANGE)) }
            }
            Stepper("真横通過: 離れたとみなす距離", "+%.0f m".format(Locale.US, s.sidePassDepartM), note = "いちばん近づいた距離から", enabled = custom) { d ->
                judge { it.copy(sidePassDepartM = (it.sidePassDepartM + d * 5).coerceIn(NavSettings.SIDE_PASS_DEPART_M_RANGE)) }
            }
            Toggle("通過判定（予備）", s.passDetection, note = "方位が取れない場面用。最接近したあと離れていったら到達にする", enabled = custom) { v ->
                judge { it.copy(passDetection = v) }
            }
            Stepper("通過判定: 最接近距離の上限", "%.0f m".format(Locale.US, s.passMaxApproachM), note = "WP ごとの到達半径 × 3 の方が大きければそちら", enabled = custom) { d ->
                judge { it.copy(passMaxApproachM = (it.passMaxApproachM + d * 50).coerceIn(NavSettings.PASS_MAX_APPROACH_M_RANGE)) }
            }
            Stepper("通過判定: 離れたとみなす距離", "+%.0f m".format(Locale.US, s.passDepartM), enabled = custom) { d ->
                judge { it.copy(passDepartM = (it.passDepartM + d * 10).coerceIn(NavSettings.PASS_DEPART_M_RANGE)) }
            }
            Stepper("通過判定: 離れた状態が続く時間", "${s.passHoldSec} 秒", enabled = custom) { d ->
                judge { it.copy(passHoldSec = (it.passHoldSec + d).coerceIn(NavSettings.PASS_HOLD_SEC_RANGE)) }
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
                onChange { it.copy(noFixTimeoutSec = (it.noFixTimeoutSec + d).coerceIn(NavSettings.NO_FIX_TIMEOUT_SEC_RANGE)) }
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
private fun Label(text: String, note: String? = null, enabled: Boolean = true) {
    Column {
        Text(text, style = if (enabled) RowLabel else RowLabel.copy(color = HudColors.WpReached))
        note?.let { Text(it, style = RowNote) }
    }
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<Pair<String, T>>,
    selected: T?,
    note: String? = null,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label, note, enabled)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((text, value) in options) Chip(text, value == selected, enabled) { onSelect(value) }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    // 押せないとき（自動車の固定値など）はグレー
    val c = if (enabled) HudColors.Scale else HudColors.WpReached
    Box(
        Modifier
            .border(1.dp, if (on) c else HudColors.WpDisabled, shape)
            .background(if (on) c else HudColors.Background, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = ValueText.copy(color = if (on) HudColors.Background else c, fontSize = 13.sp))
    }
}

@Composable
private fun Stepper(label: String, value: String, note: String? = null, enabled: Boolean = true, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { Label(label, note, enabled) }
        HudButton("−", { onStep(-1) }, enabled = enabled)
        Text(
            value,
            style = if (enabled) ValueText else ValueText.copy(color = HudColors.WpReached),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        HudButton("＋", { onStep(1) }, enabled = enabled)
    }
}

/** 不透明度などの % を、スライダーで 20〜100% の 10% 刻みに選ぶ。 */
@Composable
private fun PercentSlider(
    label: String,
    pct: Int,
    note: String? = null,
    choices: List<Int> = NavSettings.OPACITY_CHOICES_PCT,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Label(label, note) }
            Text("$pct%", style = ValueText, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp))
        }
        Slider(
            value = pct.toFloat(),
            onValueChange = { v -> choices.minByOrNull { kotlin.math.abs(it - v) }?.takeIf { it != pct }?.let(onChange) },
            valueRange = choices.first().toFloat()..choices.last().toFloat(),
            steps = choices.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = HudColors.Scale,
                activeTrackColor = HudColors.Scale,
                inactiveTrackColor = HudColors.WpDisabled,
                activeTickColor = HudColors.Background,
                inactiveTickColor = HudColors.ScaleDim,
            ),
        )
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, note: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Label(label, note, enabled) }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            enabled = enabled,
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
