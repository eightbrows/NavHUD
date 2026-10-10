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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.ColorTheme
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.ProfileSize
import io.github.eightbrows.navhud.core.nav.RangeAuto
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TrackColor
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
    // 数値を設定する項目の補足文の最後に書く初期値（NavSettings の既定値。到達の判定は自動車の値）
    val def = remember { NavSettings() }
    Column(
        modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudButton(stringResource(R.string.back), onBack)
            Text(stringResource(R.string.settings_title), style = SectionTitle.copy(fontSize = 18.sp))
        }
        Text(stringResource(R.string.settings_note), style = RowNote)

        Section(stringResource(R.string.sec_display)) {
            Choice(stringResource(R.string.display_mode), listOf("ARC" to DisplayMode.ARC, "North Up" to DisplayMode.NORTH_UP), s.displayMode) { v ->
                onChange { it.copy(displayMode = v) }
            }
            val colors = listOf(
                stringResource(R.string.color_white) to ColorTheme.WHITE,
                stringResource(R.string.color_green) to ColorTheme.GREEN,
                stringResource(R.string.color_amber) to ColorTheme.AMBER,
            )
            Choice(stringResource(R.string.ui_color), colors, s.uiTheme) { v -> onChange { it.copy(uiTheme = v) } }
            Choice(stringResource(R.string.map_color), colors, s.mapTheme) { v -> onChange { it.copy(mapTheme = v) } }
            PercentSlider(
                stringResource(R.string.button_opacity), s.buttonOpacityPct,
                note = withDefault(stringResource(R.string.button_opacity_note), pct(def.buttonOpacityPct)),
            ) { v -> onChange { it.copy(buttonOpacityPct = v) } }
            PercentSlider(
                stringResource(R.string.numbers_opacity), s.numbersOpacityPct,
                note = withDefault(stringResource(R.string.numbers_opacity_note), pct(def.numbersOpacityPct)),
            ) { v -> onChange { it.copy(numbersOpacityPct = v) } }
            Choice(
                stringResource(R.string.track_color),
                listOf(
                    stringResource(R.string.color_white) to TrackColor.WHITE,
                    stringResource(R.string.color_green) to TrackColor.GREEN,
                    stringResource(R.string.color_amber) to TrackColor.AMBER,
                    stringResource(R.string.color_cyan) to TrackColor.CYAN,
                    stringResource(R.string.color_yellow) to TrackColor.YELLOW,
                    stringResource(R.string.color_blue) to TrackColor.BLUE,
                ),
                s.trackColor,
                note = stringResource(R.string.track_color_note),
            ) { v -> onChange { it.copy(trackColor = v) } }
            PercentSlider(
                stringResource(R.string.track_brightness), s.trackBrightnessPct,
                note = withDefault(stringResource(R.string.track_brightness_note), pct(def.trackBrightnessPct)),
                choices = NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT,
            ) { v -> onChange { it.copy(trackBrightnessPct = v) } }
            PercentSlider(
                stringResource(R.string.ring_label_size), s.ringLabelScalePct,
                note = withDefault(stringResource(R.string.ring_label_size_note), pct(def.ringLabelScalePct)),
                choices = NavSettings.RING_LABEL_SCALE_CHOICES_PCT,
            ) { v -> onChange { it.copy(ringLabelScalePct = v) } }
            Choice(
                stringResource(R.string.ownship_position),
                listOf(
                    stringResource(R.string.position_standard) to OwnshipPosition.STANDARD,
                    stringResource(R.string.position_high) to OwnshipPosition.HIGH,
                    stringResource(R.string.position_higher) to OwnshipPosition.HIGHER,
                ),
                s.ownshipPosition,
                note = stringResource(R.string.ownship_position_note),
            ) { v -> onChange { it.copy(ownshipPosition = v) } }
            Choice(
                stringResource(R.string.profile),
                listOf(
                    "OFF" to ProfileSize.OFF,
                    stringResource(R.string.size_small) to ProfileSize.SMALL,
                    stringResource(R.string.size_medium) to ProfileSize.MEDIUM,
                    stringResource(R.string.size_large) to ProfileSize.LARGE,
                ),
                s.profileSize,
                note = stringResource(R.string.profile_note),
            ) { v -> onChange { it.copy(profileSize = v) } }
            Stepper(stringResource(R.string.alt_offset), "%.0f m".format(Locale.US, s.altOffsetM), note = withDefault(stringResource(R.string.alt_offset_note), meters(def.altOffsetM))) { d ->
                onChange { it.copy(altOffsetM = (it.altOffsetM + d).coerceIn(NavSettings.ALT_OFFSET_M_RANGE)) }
            }
        }

        Section(stringResource(R.string.sec_heading)) {
            Choice(
                stringResource(R.string.heading_source),
                listOf("GPS" to SourceMode.GPS, "HYBRID" to SourceMode.HYBRID, "COMPASS" to SourceMode.COMPASS),
                s.sourceMode,
                note = stringResource(R.string.heading_source_note),
            ) { v -> onChange { it.copy(sourceMode = v) } }
            Stepper(
                stringResource(R.string.hold_enter), kmh(s.holdEnterSpeedMps),
                note = withDefault(stringResource(R.string.hold_enter_note), kmhShort(def.holdEnterSpeedMps)),
            ) { d ->
                onChange {
                    val v = (it.holdEnterSpeedMps + d * STEP_MPS).round1().coerceIn(NavSettings.HOLD_ENTER_SPEED_MPS_RANGE)
                    it.copy(holdEnterSpeedMps = v, holdExitSpeedMps = maxOf(it.holdExitSpeedMps, (v + STEP_MPS).round1()))
                }
            }
            Stepper(stringResource(R.string.hold_exit), kmh(s.holdExitSpeedMps), note = withDefault(stringResource(R.string.hold_exit_note), kmhShort(def.holdExitSpeedMps))) { d ->
                onChange {
                    val v = (it.holdExitSpeedMps + d * STEP_MPS).round1().coerceIn((it.holdEnterSpeedMps + STEP_MPS).round1(), NavSettings.HOLD_EXIT_SPEED_MAX_MPS)
                    it.copy(holdExitSpeedMps = v)
                }
            }
            Stepper(stringResource(R.string.max_gps_acc), "%.0f m".format(Locale.US, s.maxGpsAccM), note = withDefault(null, meters(def.maxGpsAccM))) { d ->
                onChange { it.copy(maxGpsAccM = (it.maxGpsAccM + d).coerceIn(NavSettings.MAX_GPS_ACC_M_RANGE)) }
            }
            Stepper(stringResource(R.string.max_gps_bearing_acc), "%.0f°".format(Locale.US, s.maxGpsBearingAccDeg), note = withDefault(stringResource(R.string.max_gps_bearing_acc_note), "%.0f°".format(Locale.US, def.maxGpsBearingAccDeg))) { d ->
                onChange { it.copy(maxGpsBearingAccDeg = (it.maxGpsBearingAccDeg + d * 5).coerceIn(NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE)) }
            }
        }

        Section(stringResource(R.string.sec_range)) {
            // 初期値: すべての段なら「すべて」、そうでなければ いちばん狭い段〜いちばん広い段
            val defSteps = if (def.rangeStepsKm.size == RangeAuto.ALL_STEPS_KM.size) {
                stringResource(R.string.default_all)
            } else {
                HudFormat.rangeLabel(def.rangeStepsKm.first() * 1000) + "–" + HudFormat.rangeLabel(def.rangeStepsKm.last() * 1000)
            }
            Label(stringResource(R.string.range_steps), note = withDefault(null, defSteps))
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
                stringResource(R.string.initial_range),
                s.rangeStepsKm.map { HudFormat.rangeLabel(it * 1000) to it },
                s.rangeStepsKm.minByOrNull { kotlin.math.abs(it - s.initialRangeKm) },
                note = withDefault(null, HudFormat.rangeLabel(def.initialRangeKm * 1000)),
            ) { v -> onChange { it.copy(initialRangeKm = v) } }
            // AUTO の下限・上限: 使う段から選ぶ（下限 ≤ 上限）。表示は1つ目の距離環の距離
            val (lo, hi) = RangeAuto.limitsKm(s.rangeStepsKm, s.autoMinRangeKm, s.autoMaxRangeKm)
            Choice(
                stringResource(R.string.auto_min),
                s.rangeStepsKm.filter { it <= hi }.map { HudFormat.rangeLabel(it * 1000) to it },
                lo,
                note = withDefault(stringResource(R.string.auto_min_note), HudFormat.rangeLabel(def.autoMinRangeKm * 1000)),
            ) { v -> onChange { it.copy(autoMinRangeKm = v) } }
            Choice(
                stringResource(R.string.auto_max),
                s.rangeStepsKm.filter { it >= lo }.map { HudFormat.rangeLabel(it * 1000) to it },
                hi,
                note = withDefault(stringResource(R.string.auto_max_note), HudFormat.rangeLabel(def.autoMaxRangeKm * 1000)),
            ) { v -> onChange { it.copy(autoMaxRangeKm = v) } }
            Toggle(stringResource(R.string.auto_on_start), s.autoRange, note = stringResource(R.string.auto_on_start_note)) { v ->
                onChange { it.copy(autoRange = v) }
            }
            Stepper(stringResource(R.string.zoom_in_delay), seconds(s.autoRangeZoomInDelaySec), note = withDefault(stringResource(R.string.zoom_in_delay_note), seconds(def.autoRangeZoomInDelaySec))) { d ->
                onChange { it.copy(autoRangeZoomInDelaySec = (it.autoRangeZoomInDelaySec + d).coerceIn(NavSettings.AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE)) }
            }
            Stepper(
                stringResource(R.string.hold_after_wp), seconds(s.autoHoldAfterWpSec),
                note = withDefault(stringResource(R.string.hold_after_wp_note), seconds(def.autoHoldAfterWpSec)),
            ) { d ->
                onChange {
                    val r = NavSettings.AUTO_HOLD_AFTER_WP_SEC_RANGE
                    it.copy(autoHoldAfterWpSec = (it.autoHoldAfterWpSec + d).coerceIn(r.first, r.last))
                }
            }
            // 今の段での例: 「今の段 R1 500m: WP まで 650m 以内」（R1 = 1つ目の距離環 = 段の 1/2）
            val hasNarrower = s.rangeStepsKm.any { it >= lo && it < rangeM / 1000 - 1e-9 }
            val example = if (hasNarrower) {
                stringResource(R.string.zoom_in_example, HudFormat.rangeLabel(rangeM), HudFormat.rangeStep(s.autoZoomInDistRatio * rangeM / 2))
            } else {
                stringResource(R.string.zoom_in_example_none, HudFormat.rangeLabel(rangeM))
            }
            Stepper(
                stringResource(R.string.zoom_in_ratio), stringResource(R.string.zoom_in_ratio_value, "%.1f".format(Locale.US, s.autoZoomInDistRatio)),
                note = withDefault(stringResource(R.string.zoom_in_ratio_note, example), stringResource(R.string.zoom_in_ratio_value, "%.1f".format(Locale.US, def.autoZoomInDistRatio))),
            ) { d ->
                onChange {
                    val c = NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES
                    val i = c.indexOfFirst { v -> kotlin.math.abs(v - it.autoZoomInDistRatio) < 1e-9 }.coerceAtLeast(0)
                    it.copy(autoZoomInDistRatio = c[(i + d).coerceIn(0, c.lastIndex)])
                }
            }
            Stepper(stringResource(R.string.pan_return), seconds(s.panReturnSec), note = withDefault(stringResource(R.string.pan_return_note), seconds(def.panReturnSec))) { d ->
                onChange { it.copy(panReturnSec = (it.panReturnSec + d * 5).coerceIn(NavSettings.PAN_RETURN_SEC_RANGE)) }
            }
        }

        Section(stringResource(R.string.sec_wp)) {
            // 到達判定の値: 自動車は推奨値で固定（表示だけ）、カスタム1〜3 は枠ごとに保存する
            val custom = s.travelMode != TravelMode.CAR
            fun judge(f: (ReachProfile) -> ReachProfile) = onChange { it.editReach(f) }
            Choice(
                stringResource(R.string.travel_mode),
                listOf(
                    stringResource(R.string.travel_car) to TravelMode.CAR,
                    stringResource(R.string.travel_custom1) to TravelMode.CUSTOM1,
                    stringResource(R.string.travel_custom2) to TravelMode.CUSTOM2,
                    stringResource(R.string.travel_custom3) to TravelMode.CUSTOM3,
                ),
                s.travelMode,
                note = stringResource(if (custom) R.string.travel_custom_note else R.string.travel_car_note),
            ) { v -> onChange { it.selectTravelMode(v) } }
            if (custom) HudButton(stringResource(R.string.reset_to_car), { onChange { it.editReach { ReachProfile.CAR } } })
            Choice(
                stringResource(R.string.arrival_radius),
                NavSettings.REACH_RADIUS_CHOICES_M.map { "%.0f m".format(Locale.US, it) to it },
                s.reachRadiusM,
                note = withDefault(stringResource(R.string.arrival_radius_note), meters(def.reachRadiusM)),
                enabled = custom,
            ) { v -> judge { it.copy(reachRadiusM = v) } }
            Toggle(stringResource(R.string.side_pass), s.sidePass, note = stringResource(R.string.side_pass_note), enabled = custom) { v ->
                judge { it.copy(sidePass = v) }
            }
            Stepper(
                stringResource(R.string.side_pass_max),
                stringResource(R.string.within_meters, "%.0f".format(Locale.US, s.sidePassMaxM)),
                note = withDefault(null, stringResource(R.string.within_meters, "%.0f".format(Locale.US, def.sidePassMaxM))),
                enabled = custom,
            ) { d ->
                judge { it.copy(sidePassMaxM = (it.sidePassMaxM + d * 10).coerceIn(NavSettings.SIDE_PASS_MAX_M_RANGE)) }
            }
            Stepper(stringResource(R.string.side_pass_depart), "+%.0f m".format(Locale.US, s.sidePassDepartM), note = withDefault(stringResource(R.string.side_pass_depart_note), "+%.0f m".format(Locale.US, def.sidePassDepartM)), enabled = custom) { d ->
                judge { it.copy(sidePassDepartM = (it.sidePassDepartM + d * 5).coerceIn(NavSettings.SIDE_PASS_DEPART_M_RANGE)) }
            }
            Toggle(stringResource(R.string.pass_detection), s.passDetection, note = stringResource(R.string.pass_detection_note), enabled = custom) { v ->
                judge { it.copy(passDetection = v) }
            }
            Stepper(stringResource(R.string.pass_max), "%.0f m".format(Locale.US, s.passMaxApproachM), note = withDefault(stringResource(R.string.pass_max_note), meters(def.passMaxApproachM)), enabled = custom) { d ->
                judge { it.copy(passMaxApproachM = (it.passMaxApproachM + d * 50).coerceIn(NavSettings.PASS_MAX_APPROACH_M_RANGE)) }
            }
            Stepper(stringResource(R.string.pass_depart), "+%.0f m".format(Locale.US, s.passDepartM), note = withDefault(null, "+%.0f m".format(Locale.US, def.passDepartM)), enabled = custom) { d ->
                judge { it.copy(passDepartM = (it.passDepartM + d * 10).coerceIn(NavSettings.PASS_DEPART_M_RANGE)) }
            }
            Stepper(stringResource(R.string.pass_hold), seconds(s.passHoldSec), note = withDefault(null, seconds(def.passHoldSec)), enabled = custom) { d ->
                judge { it.copy(passHoldSec = (it.passHoldSec + d).coerceIn(NavSettings.PASS_HOLD_SEC_RANGE)) }
            }
            Stepper(stringResource(R.string.wp_buttons), count(s.wpButtonsMax), note = withDefault(stringResource(R.string.wp_buttons_note), count(def.wpButtonsMax))) { d ->
                onChange { it.copy(wpButtonsMax = (it.wpButtonsMax + d).coerceIn(NavSettings.WP_BUTTONS_MAX_RANGE)) }
            }
            Stepper(stringResource(R.string.hud_wp_count), count(s.hudWpCount), note = withDefault(stringResource(R.string.hud_wp_count_note), count(def.hudWpCount))) { d ->
                onChange { it.copy(hudWpCount = (it.hudWpCount + d).coerceIn(NavSettings.HUD_WP_COUNT_RANGE)) }
            }
        }

        Section(stringResource(R.string.sec_input)) {
            Choice("INPUT", listOf(stringResource(R.string.input_live) to SourceKind.LIVE, stringResource(R.string.input_replay) to SourceKind.REPLAY), input) { v ->
                onInput(v)
            }
            Stepper(stringResource(R.string.no_fix_timeout), seconds(s.noFixTimeoutSec), note = withDefault(null, seconds(def.noFixTimeoutSec))) { d ->
                onChange { it.copy(noFixTimeoutSec = (it.noFixTimeoutSec + d).coerceIn(NavSettings.NO_FIX_TIMEOUT_SEC_RANGE)) }
            }
            Choice(stringResource(R.string.rate_window), NavSettings.RATE_WINDOW_CHOICES_SEC.map { seconds(it) to it }, s.rateWindowSec, note = withDefault(null, seconds(def.rateWindowSec))) { v ->
                onChange { it.copy(rateWindowSec = v) }
            }
        }

        Section(stringResource(R.string.sec_other)) {
            Toggle(stringResource(R.string.keep_screen_on), s.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            Toggle(stringResource(R.string.auto_open_last), s.autoOpenLastList, note = stringResource(R.string.auto_open_last_note)) { v ->
                onChange { it.copy(autoOpenLastList = v) }
            }
            Row(Modifier.padding(top = 6.dp)) { HudButton(stringResource(R.string.reset_settings), onReset) }
        }
    }
}

/** 速度の刻み（0.1 m/s ≒ 0.36 km/h） */
private const val STEP_MPS = 0.1f

// 数値は端末の言語によらず同じ書き方（Locale.US）で文字にしてから、言語ごとの単位の書き方に入れる
@Composable
private fun kmh(mps: Float) =
    stringResource(R.string.value_kmh_mps, "%.1f".format(Locale.US, mps * 3.6f), "%.1f".format(Locale.US, mps))

@Composable
private fun seconds(sec: Int) = stringResource(R.string.value_seconds, sec.toString())

private fun pct(p: Int) = "$p%"

/** 初期値の表示用: km/h だけ（補足文が長くならないように、m/s は付けない） */
private fun kmhShort(mps: Float) = "%.1f km/h".format(Locale.US, mps * 3.6f)

private fun meters(m: Number) = "%.0f m".format(Locale.US, m.toDouble())

/**
 * 補足文の最後に初期値を書き足す（例「…。初期値 150%」）。補足文がない項目は、初期値だけの補足文にする。
 * 行は増やさず、補足文の文の続きに入れる。
 */
@Composable
private fun withDefault(note: String?, default: String): String {
    val d = stringResource(R.string.default_value, default)
    val n = note?.trim()?.trimEnd('。', '.')
    return if (n.isNullOrEmpty()) d else stringResource(R.string.note_with_default, n, d)
}

@Composable
private fun count(n: Int) = stringResource(R.string.value_count, n.toString())

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
