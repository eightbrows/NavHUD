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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.ColorTheme
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.LanguageMode
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
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
import io.github.eightbrows.navhud.source.AppPermission
import io.github.eightbrows.navhud.source.PermissionStatus
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SectionTitle get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val RowLabel get() = TextStyle(fontSize = 14.sp, color = HudColors.Scale)
private val RowNote get() = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, color = HudColors.ScaleDim)
private val ValueText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = HudColors.Scale)

/**
 * 設定画面。NavSettings のすべての値と INPUT を変えられる。変えたらすぐ画面に反映し、保存する。
 * セクション: 表示 / 方位 / 縮尺 / WP / 測位・入力 / 地図レイヤ / その他 / 言語 / 権限 / 情報。
 * いちばん上に、セクションの並びから作る目次（押すとそのセクションへ）。各セクションの見出しの右に「▲ 目次」（目次へ戻る）。
 * 下の3つ（言語・権限・情報）は NavSettings の外（§6.9）: 言語は端末かアプリが別に保存し、権限と情報は見るだけ。
 */
@Composable
fun SettingsScreen(
    settings: NavSettings,
    input: SourceKind,
    /** 今の縮尺の段 [m]（「詳細へ切り替える距離」の例に使う） */
    rangeM: Double,
    /** 今の表示言語の選択 */
    language: LanguageMode,
    /** 権限の今の状態（画面に戻ってきたときに読み直したもの） */
    permissions: List<PermissionStatus>,
    /** バージョン（appVersionName） */
    versionName: String,
    onChange: ((NavSettings) -> NavSettings) -> Unit,
    onInput: (SourceKind) -> Unit,
    onReset: () -> Unit,
    onLanguage: (LanguageMode) -> Unit,
    /** 端末の、このアプリの設定画面を開く */
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = settings
    // 数値を設定する項目の補足文の最後に書く初期値（NavSettings の既定値。到達の判定は自動車の値）
    val def = remember { NavSettings() }
    // セクションの並び（上の目次は、この並びから作る）
    val sections = listOf(
        SettingsSection(stringResource(R.string.sec_display)) {
            Choice(stringResource(R.string.display_mode), listOf("ARC" to DisplayMode.ARC, "North Up" to DisplayMode.NORTH_UP), s.displayMode, default = def.displayMode) { v ->
                onChange { it.copy(displayMode = v) }
            }
            val colors = listOf(
                stringResource(R.string.color_white) to ColorTheme.WHITE,
                stringResource(R.string.color_green) to ColorTheme.GREEN,
                stringResource(R.string.color_amber) to ColorTheme.AMBER,
            )
            Choice(stringResource(R.string.ui_color), colors, s.uiTheme, default = def.uiTheme) { v -> onChange { it.copy(uiTheme = v) } }
            Choice(stringResource(R.string.map_color), colors, s.mapTheme, default = def.mapTheme) { v -> onChange { it.copy(mapTheme = v) } }
            PercentSlider(
                stringResource(R.string.button_opacity), s.buttonOpacityPct,
                note = stringResource(R.string.button_opacity_note), defaultText = pct(def.buttonOpacityPct),
            ) { v -> onChange { it.copy(buttonOpacityPct = v) } }
            PercentSlider(
                stringResource(R.string.numbers_opacity), s.numbersOpacityPct,
                note = stringResource(R.string.numbers_opacity_note), defaultText = pct(def.numbersOpacityPct),
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
                default = def.trackColor,
            ) { v -> onChange { it.copy(trackColor = v) } }
            PercentSlider(
                stringResource(R.string.track_brightness), s.trackBrightnessPct,
                note = stringResource(R.string.track_brightness_note), defaultText = pct(def.trackBrightnessPct),
                choices = NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT,
            ) { v -> onChange { it.copy(trackBrightnessPct = v) } }
            PercentSlider(
                stringResource(R.string.ring_label_size), s.ringLabelScalePct,
                note = stringResource(R.string.ring_label_size_note), defaultText = pct(def.ringLabelScalePct),
                choices = NavSettings.RING_LABEL_SCALE_CHOICES_PCT,
            ) { v -> onChange { it.copy(ringLabelScalePct = v) } }
            Choice(
                stringResource(R.string.ownship_position),
                listOf(
                    // 左が一番下の段、右が中央: 後4 / 後3 / 後2 / 後1 / 中央（英語は Aft 4 … Aft 1 / Center）。
                    // 「後」の数字が大きいほど、自機を後ろ（下）に置く。保存値の名前は変えない
                    stringResource(R.string.position_aft, "4") to OwnshipPosition.STANDARD,
                    stringResource(R.string.position_aft, "3") to OwnshipPosition.HIGH,
                    stringResource(R.string.position_aft, "2") to OwnshipPosition.HIGHER,
                    stringResource(R.string.position_aft, "1") to OwnshipPosition.NEAR_CENTER,
                    stringResource(R.string.position_center) to OwnshipPosition.CENTER,
                ),
                s.ownshipPosition,
                note = stringResource(R.string.ownship_position_note),
                default = def.ownshipPosition,
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
                default = def.profileSize,
            ) { v -> onChange { it.copy(profileSize = v) } }
            Stepper(stringResource(R.string.alt_offset), "%.0f".format(Locale.US, s.altOffsetM), note = stringResource(R.string.alt_offset_note), defaultText = meters(def.altOffsetM)) { d ->
                onChange { it.copy(altOffsetM = (it.altOffsetM + d).coerceIn(NavSettings.ALT_OFFSET_M_RANGE)) }
            }
        },

        SettingsSection(stringResource(R.string.sec_heading)) {
            Choice(
                stringResource(R.string.heading_source),
                listOf("GPS" to SourceMode.GPS, "HYBRID" to SourceMode.HYBRID, "COMPASS" to SourceMode.COMPASS),
                s.sourceMode,
                note = stringResource(R.string.heading_source_note),
                default = def.sourceMode,
            ) { v -> onChange { it.copy(sourceMode = v) } }
            // 保持の速度: km/h の整数（1〜36）、1 km/h 刻み。解く速度は入る速度より 1 km/h 以上大きい。保存は m/s
            val kmhRange = NavSettings.HOLD_SPEED_KMH_RANGE
            Stepper(
                stringResource(R.string.hold_enter), kmh(NavSettings.holdSpeedKmh(s.holdEnterSpeedMps)),
                note = stringResource(R.string.hold_enter_note), defaultText = kmh(NavSettings.holdSpeedKmh(def.holdEnterSpeedMps)),
            ) { d ->
                onChange {
                    val enter = (NavSettings.holdSpeedKmh(it.holdEnterSpeedMps) + d).coerceIn(kmhRange.first, kmhRange.last - 1)
                    val exit = maxOf(NavSettings.holdSpeedKmh(it.holdExitSpeedMps), enter + 1)
                    it.copy(holdEnterSpeedMps = NavSettings.kmhToMps(enter), holdExitSpeedMps = NavSettings.kmhToMps(exit))
                }
            }
            Stepper(
                stringResource(R.string.hold_exit), kmh(NavSettings.holdSpeedKmh(s.holdExitSpeedMps)),
                note = stringResource(R.string.hold_exit_note), defaultText = kmh(NavSettings.holdSpeedKmh(def.holdExitSpeedMps)),
            ) { d ->
                onChange {
                    val enter = NavSettings.holdSpeedKmh(it.holdEnterSpeedMps)
                    val exit = (NavSettings.holdSpeedKmh(it.holdExitSpeedMps) + d).coerceIn(enter + 1, kmhRange.last)
                    it.copy(holdExitSpeedMps = NavSettings.kmhToMps(exit))
                }
            }
            // 決まった値から選ぶ（＋ / − で次の値へ）: 3 / 5 / 10 / 15 / 20 / 30 / 50 / 100 m
            Stepper(stringResource(R.string.max_gps_acc), meters(s.maxGpsAccM), defaultText = meters(def.maxGpsAccM)) { d ->
                onChange { it.copy(maxGpsAccM = stepChoice(NavSettings.MAX_GPS_ACC_CHOICES_M, it.maxGpsAccM, d)) }
            }
            Stepper(stringResource(R.string.max_gps_bearing_acc), "%.0f°".format(Locale.US, s.maxGpsBearingAccDeg), note = stringResource(R.string.max_gps_bearing_acc_note), defaultText = "%.0f°".format(Locale.US, def.maxGpsBearingAccDeg)) { d ->
                onChange { it.copy(maxGpsBearingAccDeg = (it.maxGpsBearingAccDeg + d * 5).coerceIn(NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE)) }
            }
        },

        SettingsSection(stringResource(R.string.sec_range)) {
            // 初期値の段のボタンは塗り、使う段（選んでいる段）は水色の枠と点（20261021-D01）
            Label(stringResource(R.string.range_steps))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (km in RangeAuto.ALL_STEPS_KM) {
                    val on = km in s.rangeStepsKm
                    Chip(r1Label(km * 1000), on, isDefault = km in def.rangeStepsKm) {
                        onChange {
                            val steps = if (on) it.rangeStepsKm - km else (it.rangeStepsKm + km).sorted()
                            if (steps.isEmpty()) it else it.copy(rangeStepsKm = steps)
                        }
                    }
                }
            }
            Choice(
                stringResource(R.string.initial_range),
                s.rangeStepsKm.map { r1Label(it * 1000) to it },
                s.rangeStepsKm.minByOrNull { kotlin.math.abs(it - s.initialRangeKm) },
                default = def.initialRangeKm,
            ) { v -> onChange { it.copy(initialRangeKm = v) } }
            // AUTO の詳細の限度・広域の限度: 使う段から選ぶ（詳細の限度は広域の限度と同じか、それより詳細）。表示は1つ目の距離環の距離
            val (lo, hi) = RangeAuto.limitsKm(s.rangeStepsKm, s.autoMinRangeKm, s.autoMaxRangeKm)
            Choice(
                stringResource(R.string.auto_min),
                s.rangeStepsKm.filter { it <= hi }.map { r1Label(it * 1000) to it },
                lo,
                note = stringResource(R.string.auto_min_note), default = def.autoMinRangeKm,
            ) { v -> onChange { it.copy(autoMinRangeKm = v) } }
            Choice(
                stringResource(R.string.auto_max),
                s.rangeStepsKm.filter { it >= lo }.map { r1Label(it * 1000) to it },
                hi,
                note = stringResource(R.string.auto_max_note), default = def.autoMaxRangeKm,
            ) { v -> onChange { it.copy(autoMaxRangeKm = v) } }
            Toggle(stringResource(R.string.auto_on_start), s.autoRange, note = stringResource(R.string.auto_on_start_note), default = def.autoRange) { v ->
                onChange { it.copy(autoRange = v) }
            }
            Stepper(stringResource(R.string.zoom_in_delay), seconds(s.autoRangeZoomInDelaySec), note = stringResource(R.string.zoom_in_delay_note), defaultText = seconds(def.autoRangeZoomInDelaySec)) { d ->
                onChange { it.copy(autoRangeZoomInDelaySec = (it.autoRangeZoomInDelaySec + d).coerceIn(NavSettings.AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE)) }
            }
            Stepper(
                stringResource(R.string.hold_after_wp), seconds(s.autoHoldAfterWpSec),
                note = stringResource(R.string.hold_after_wp_note), defaultText = seconds(def.autoHoldAfterWpSec),
            ) { d ->
                // 決まった値から選ぶ: 0 / 5 / 10 / 15 / 20 / 30 / 60 秒
                onChange { it.copy(autoHoldAfterWpSec = stepChoice(NavSettings.AUTO_HOLD_AFTER_WP_CHOICES_SEC, it.autoHoldAfterWpSec, d)) }
            }
            // 今の段での例: 「今の段 R1 500m: WP まで 650m 以内」（R1 = 1つ目の距離環 = 段の 1/2）
            val hasNarrower = s.rangeStepsKm.any { it >= lo && it < rangeM / 1000 - 1e-9 }
            val example = if (hasNarrower) {
                stringResource(R.string.zoom_in_example, r1Label(rangeM), HudFormat.ringLabel(s.autoZoomInDistRatio * rangeM / 2))
            } else {
                stringResource(R.string.zoom_in_example_none, r1Label(rangeM))
            }
            Stepper(
                stringResource(R.string.zoom_in_ratio), stringResource(R.string.zoom_in_ratio_value, "%.1f".format(Locale.US, s.autoZoomInDistRatio)),
                note = stringResource(R.string.zoom_in_ratio_note, example), defaultText = stringResource(R.string.zoom_in_ratio_value, "%.1f".format(Locale.US, def.autoZoomInDistRatio)),
            ) { d ->
                onChange {
                    val c = NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES
                    val i = c.indexOfFirst { v -> kotlin.math.abs(v - it.autoZoomInDistRatio) < 1e-9 }.coerceAtLeast(0)
                    it.copy(autoZoomInDistRatio = c[(i + d).coerceIn(0, c.lastIndex)])
                }
            }
            Stepper(stringResource(R.string.pan_return), seconds(s.panReturnSec), note = stringResource(R.string.pan_return_note), defaultText = seconds(def.panReturnSec)) { d ->
                onChange { it.copy(panReturnSec = (it.panReturnSec + d * 5).coerceIn(NavSettings.PAN_RETURN_SEC_RANGE)) }
            }
        },

        SettingsSection(stringResource(R.string.sec_wp)) {
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
                default = def.travelMode,
            ) { v -> onChange { it.selectTravelMode(v) } }
            if (custom) HudButton(stringResource(R.string.reset_to_car), { onChange { it.editReach { ReachProfile.CAR } } })
            Choice(
                stringResource(R.string.arrival_radius),
                NavSettings.REACH_RADIUS_CHOICES_M.map { "%.0f".format(Locale.US, it) to it },
                s.reachRadiusM,
                note = stringResource(R.string.arrival_radius_note), default = def.reachRadiusM,
                enabled = custom,
            ) { v -> judge { it.copy(reachRadiusM = v) } }
            Toggle(stringResource(R.string.side_pass), s.sidePass, note = stringResource(R.string.side_pass_note), enabled = custom, default = def.sidePass) { v ->
                judge { it.copy(sidePass = v) }
            }
            Stepper(
                stringResource(R.string.side_pass_max),
                stringResource(R.string.within_meters, "%.0f".format(Locale.US, s.sidePassMaxM)),
                defaultText = stringResource(R.string.within_meters, "%.0f".format(Locale.US, def.sidePassMaxM)),
                enabled = custom,
            ) { d ->
                judge { it.copy(sidePassMaxM = (it.sidePassMaxM + d * 10).coerceIn(NavSettings.SIDE_PASS_MAX_M_RANGE)) }
            }
            Stepper(stringResource(R.string.side_pass_depart), "+%.0f".format(Locale.US, s.sidePassDepartM), note = stringResource(R.string.side_pass_depart_note), defaultText = "+%.0f".format(Locale.US, def.sidePassDepartM), enabled = custom) { d ->
                judge { it.copy(sidePassDepartM = (it.sidePassDepartM + d * 5).coerceIn(NavSettings.SIDE_PASS_DEPART_M_RANGE)) }
            }
            Toggle(stringResource(R.string.pass_detection), s.passDetection, note = stringResource(R.string.pass_detection_note), enabled = custom, default = def.passDetection) { v ->
                judge { it.copy(passDetection = v) }
            }
            Stepper(stringResource(R.string.pass_max), "%.0f".format(Locale.US, s.passMaxApproachM), note = stringResource(R.string.pass_max_note), defaultText = meters(def.passMaxApproachM), enabled = custom) { d ->
                judge { it.copy(passMaxApproachM = (it.passMaxApproachM + d * 50).coerceIn(NavSettings.PASS_MAX_APPROACH_M_RANGE)) }
            }
            Stepper(stringResource(R.string.pass_depart), "+%.0f".format(Locale.US, s.passDepartM), defaultText = "+%.0f".format(Locale.US, def.passDepartM), enabled = custom) { d ->
                judge { it.copy(passDepartM = (it.passDepartM + d * 10).coerceIn(NavSettings.PASS_DEPART_M_RANGE)) }
            }
            Stepper(stringResource(R.string.pass_hold), seconds(s.passHoldSec), defaultText = seconds(def.passHoldSec), enabled = custom) { d ->
                judge { it.copy(passHoldSec = (it.passHoldSec + d).coerceIn(NavSettings.PASS_HOLD_SEC_RANGE)) }
            }
            Stepper(stringResource(R.string.wp_buttons), count(s.wpButtonsMax), note = stringResource(R.string.wp_buttons_note), defaultText = count(def.wpButtonsMax)) { d ->
                onChange { it.copy(wpButtonsMax = (it.wpButtonsMax + d).coerceIn(NavSettings.WP_BUTTONS_MAX_RANGE)) }
            }
            Stepper(stringResource(R.string.hud_wp_count), count(s.hudWpCount), note = stringResource(R.string.hud_wp_count_note), defaultText = count(def.hudWpCount)) { d ->
                onChange { it.copy(hudWpCount = (it.hudWpCount + d).coerceIn(NavSettings.HUD_WP_COUNT_RANGE)) }
            }
        },

        SettingsSection(stringResource(R.string.sec_input)) {
            Choice("INPUT", listOf(stringResource(R.string.input_live) to SourceKind.LIVE, stringResource(R.string.input_replay) to SourceKind.REPLAY), input, default = SourceKind.LIVE) { v ->
                onInput(v)
            }
            // 決まった値から選ぶ: 3 / 5 / 10 / 15 / 20 / 30 / 60 / 120 秒
            Stepper(stringResource(R.string.no_fix_timeout), seconds(s.noFixTimeoutSec), defaultText = seconds(def.noFixTimeoutSec)) { d ->
                onChange { it.copy(noFixTimeoutSec = stepChoice(NavSettings.NO_FIX_TIMEOUT_CHOICES_SEC, it.noFixTimeoutSec, d)) }
            }
            Choice(stringResource(R.string.rate_window), NavSettings.RATE_WINDOW_CHOICES_SEC.map { seconds(it) to it }, s.rateWindowSec, default = def.rateWindowSec) { v ->
                onChange { it.copy(rateWindowSec = v) }
            }
        },

        SettingsSection(stringResource(R.string.sec_map_layers)) {
            // 背景地図（§8）: まとめて消すスイッチ、地図全体の線の太さ・明るさ、項目ごとの「どの縮尺まで描くか」
            // （背景地図が OFF のときは、下の項目は押せない。値は残る）
            Text(stringResource(R.string.map_layers_note), style = RowNote)
            Toggle(stringResource(R.string.base_map), s.baseMap, note = stringResource(R.string.base_map_note), default = def.baseMap) { v ->
                onChange { it.copy(baseMap = v) }
            }
            val levels = NavSettings.MAP_LINE_LEVELS.map { it.toString() to it }
            Choice(stringResource(R.string.map_line_width), levels, s.mapLineWidth, note = stringResource(R.string.map_line_width_note), enabled = s.baseMap, default = def.mapLineWidth) { v ->
                onChange { it.copy(mapLineWidth = v) }
            }
            Choice(stringResource(R.string.map_line_brightness), levels, s.mapLineBrightness, note = stringResource(R.string.map_line_brightness_note), enabled = s.baseMap, default = def.mapLineBrightness) { v ->
                onChange { it.copy(mapLineBrightness = v) }
            }
            val ranges = listOf(
                stringResource(R.string.map_range_off) to MapRange.OFF,
                stringResource(R.string.map_range_10) to MapRange.UPTO_10KM,
                stringResource(R.string.map_range_25) to MapRange.UPTO_25KM,
                stringResource(R.string.map_range_50) to MapRange.UPTO_50KM,
                stringResource(R.string.map_range_all) to MapRange.ALL,
            )
            for (item in MAP_ITEM_ORDER) {
                Choice(stringResource(mapItemLabel(item)), ranges, s.mapRangeOf(item), enabled = s.baseMap, default = def.mapRangeOf(item), compact = true) { v ->
                    onChange { it.withMapRange(item, v) }
                }
            }
        },

        SettingsSection(stringResource(R.string.sec_other)) {
            Toggle(stringResource(R.string.keep_screen_on), s.keepScreenOn, default = def.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            Toggle(stringResource(R.string.auto_open_last), s.autoOpenLastList, note = stringResource(R.string.auto_open_last_note), default = def.autoOpenLastList) { v ->
                onChange { it.copy(autoOpenLastList = v) }
            }
            Row(Modifier.padding(top = 2.dp)) { HudButton(stringResource(R.string.reset_settings), onReset) }
        },

        SettingsSection(stringResource(R.string.sec_language)) {
            // 選ぶと画面が作り直されて、すぐに切り替わる。「設定を初期値に戻す」では変えない（NavSettings の外）
            Choice(
                stringResource(R.string.app_language),
                listOf(
                    stringResource(R.string.language_system) to LanguageMode.SYSTEM,
                    stringResource(R.string.language_japanese) to LanguageMode.JAPANESE,
                    stringResource(R.string.language_english) to LanguageMode.ENGLISH,
                ),
                language,
                note = stringResource(R.string.app_language_note),
                default = LanguageMode.SYSTEM,
                onSelect = onLanguage,
            )
        },

        SettingsSection(stringResource(R.string.sec_permissions)) {
            Text(stringResource(R.string.permissions_note), style = RowNote)
            for (p in permissions) {
                val (label, note) = when (p.permission) {
                    AppPermission.FINE_LOCATION -> R.string.perm_fine to R.string.perm_fine_note
                    AppPermission.COARSE_LOCATION -> R.string.perm_coarse to R.string.perm_coarse_note
                    AppPermission.NOTIFICATIONS -> R.string.perm_notifications to R.string.perm_notifications_note
                }
                PermissionRow(stringResource(label), stringResource(note), p.granted, onOpenAppSettings)
            }
        },

        SettingsSection(stringResource(R.string.sec_about)) {
            InfoRow(stringResource(R.string.about_app), stringResource(R.string.about_app_value))
            InfoRow(stringResource(R.string.about_version), versionName)
            InfoRow(stringResource(R.string.about_license), "Apache License 2.0", "$REPO_URL/blob/main/LICENSE")
            InfoRow(stringResource(R.string.about_website), "eightbrows.github.io", WEBSITE_URL)
            InfoRow(stringResource(R.string.about_cheatsheet), "docs/cheatsheet.pdf", "$REPO_URL/blob/main/docs/cheatsheet.pdf")
            InfoRow(stringResource(R.string.about_formats), "docs/formats.pdf", "$REPO_URL/blob/main/docs/formats.pdf")
            // 背景地図の出典（§8）
            SourceNote(stringResource(R.string.about_basemap_source, BASEMAP_SOURCE_URL), BASEMAP_SOURCE_URL)
            Text(stringResource(R.string.about_links_note), style = RowNote)
        },
    )
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // セクションの見出しの位置（Column の中の y [px]）。目次を押したら、そこまでスクロールする
    val tops = remember { mutableMapOf<Int, Int>() }
    Column(
        modifier
            .fillMaxSize()
            .background(HudColors.Background)
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudButton(stringResource(R.string.back), onBack)
            Text(stringResource(R.string.settings_title), style = SectionTitle.copy(fontSize = 18.sp))
        }
        Text(stringResource(R.string.settings_note), style = RowNote)
        MarkLegend()
        Toc(sections.map { it.title }) { i -> tops[i]?.let { y -> scope.launch { scroll.animateScrollTo(y) } } }
        for ((i, sec) in sections.withIndex()) {
            Section(
                sec.title,
                onToc = { scope.launch { scroll.animateScrollTo(0) } },
                modifier = Modifier.onGloballyPositioned { tops[i] = it.positionInParent().y.roundToInt() },
                content = sec.content,
            )
        }
    }
}

/** GitHub のリポジトリ（「情報」の欄のライセンス・チートシート・ファイル形式の説明のリンク先）。ファイルは main ブランチのものを開く */
private const val REPO_URL = "https://github.com/eightbrows/NavHUD"

/** 公式サイト（GpsLogger の「情報」の欄と同じ） */
private const val WEBSITE_URL = "https://eightbrows.github.io/"

/** 設定画面の地図レイヤの項目の並び: 線（海岸線・境・道路・鉄道・水・フェリー）→ 点（空港） */
private val MAP_ITEM_ORDER = listOf(
    MapItem.COASTLINE, MapItem.PREF_BOUNDARY, MapItem.MUNI_BOUNDARY, MapItem.MOTORWAY, MapItem.OTHER_MOTORWAY, MapItem.NATIONAL_ROAD, MapItem.PREF_ROAD,
    MapItem.SHINKANSEN, MapItem.RAILWAY, MapItem.WATER, MapItem.FERRY, MapItem.AIRPORT,
)

private fun mapItemLabel(item: MapItem): Int = when (item) {
    MapItem.COASTLINE -> R.string.map_layer_coastline
    MapItem.PREF_BOUNDARY -> R.string.map_layer_pref_boundary
    MapItem.MUNI_BOUNDARY -> R.string.map_layer_muni_boundary
    MapItem.MOTORWAY -> R.string.map_item_motorway
    MapItem.OTHER_MOTORWAY -> R.string.map_item_other_motorway
    MapItem.NATIONAL_ROAD -> R.string.map_item_national_road
    MapItem.PREF_ROAD -> R.string.map_item_pref_road
    MapItem.SHINKANSEN -> R.string.map_item_shinkansen
    MapItem.RAILWAY -> R.string.map_layer_railway
    MapItem.WATER -> R.string.map_item_water
    MapItem.FERRY -> R.string.map_layer_ferry
    MapItem.AIRPORT -> R.string.map_layer_airport
}

/** 背景地図の出典（国土地理院「地球地図日本」のページ） */
private const val BASEMAP_SOURCE_URL = "https://www.gsi.go.jp/kankyochiri/gm_jpn.html"

/**
 * 決まった値から選ぶ項目の ＋ / −: 今の値（選べる値にないときは、それ以上で最初の値）から、d つ隣の値にする（端では止まる）。
 */
private fun <T : Comparable<T>> stepChoice(choices: List<T>, current: T, d: Int): T {
    val i = choices.indexOfFirst { it >= current }.let { if (it < 0) choices.lastIndex else it }
    return choices[(i + d).coerceIn(0, choices.lastIndex)]
}

// 数値は端末の言語によらず同じ書き方（Locale.US）で文字にしてから、言語ごとの単位の書き方に入れる
private fun kmh(kmh: Int) = "$kmh km/h"

@Composable
private fun seconds(sec: Int) = stringResource(R.string.value_seconds, sec.toString())

private fun pct(p: Int) = "$p%"


// 設定画面の距離は、m は数字だけ、km は「k」（距離環の文字と同じ書き方。20261020-D02）
private fun meters(m: Number) = "%.0f".format(Locale.US, m.toDouble())

/** 縮尺の段 [m] の表示: 自機から 1 つ目の距離環の距離（段の 1/2）を、距離環の文字と同じ書き方で（25 / 250 / 2.5k / 100k） */
private fun r1Label(rangeM: Double) = HudFormat.ringLabel(rangeM / 2)

@Composable
private fun count(n: Int) = stringResource(R.string.value_count, n.toString())

/** 設定画面のセクション（見出しと中身）。この並びの順に、目次と本文を作る */
private class SettingsSection(val title: String, val content: @Composable () -> Unit)

/** 目次: セクションの見出しを、選択肢と同じ形のボタンで並べる（折り返す）。押すと、そのセクションの見出しまでスクロールする */
@Composable
private fun Toc(titles: List<String>, onJump: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_toc), style = RowNote)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            titles.forEachIndexed { i, t -> Chip(t, on = false) { onJump(i) } }
        }
    }
}

/** セクションの枠。見出しの右に「▲ 目次」（押すと、いちばん上の目次に戻る） */
@Composable
private fun Section(title: String, onToc: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(0.5.dp, HudColors.Frame, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = SectionTitle, modifier = Modifier.weight(1f))
            Chip(stringResource(R.string.back_to_toc), on = false, compact = true, onClick = onToc)
        }
        content()
    }
}

/**
 * 項目の名前の行: 名前と「（初期値：…）」を 1 行に並べる（幅が足りないときだけ、初期値を次の行に送る）。
 * defaultText は初期値の値の文字（null なら初期値を書かない）。
 */
@Composable
private fun NameLine(text: String, defaultText: String?, enabled: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = if (enabled) RowLabel else RowLabel.copy(color = HudColors.WpReached), modifier = Modifier.align(Alignment.CenterVertically))
        defaultText?.let { Text(stringResource(R.string.default_value, it), style = RowNote, modifier = Modifier.align(Alignment.CenterVertically)) }
    }
}

/** 項目の名前の行（名前と初期値）と、その下の補足文 */
@Composable
private fun Label(text: String, note: String? = null, enabled: Boolean = true, defaultText: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        NameLine(text, defaultText, enabled)
        note?.let { Text(it, style = RowNote) }
    }
}

/** 権限の1行: 名前と補足文、右に今の状態（未許可は警告の色）。押すと、端末のこのアプリの設定画面が開く。 */
@Composable
private fun PermissionRow(label: String, note: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) { Label(label, note) }
        Text(
            stringResource(if (granted) R.string.perm_status_granted else R.string.perm_status_denied),
            style = ValueText.copy(color = if (granted) HudColors.Scale else HudColors.Warning),
            textAlign = TextAlign.End,
        )
    }
}

/** 情報の1行: 左に項目名、右に値。url があればリンク（下線と ↗ を付け、押すとブラウザで開く）。 */
@Composable
private fun InfoRow(label: String, value: String, url: String? = null) {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            // 開けるアプリ（ブラウザ）がない端末では何もしない
            .then(if (url == null) Modifier else Modifier.clickable { runCatching { uriHandler.openUri(url) } }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) { Label(label) }
        if (url == null) {
            Text(value, style = ValueText, textAlign = TextAlign.End)
        } else {
            Text(value, style = ValueText.copy(textDecoration = TextDecoration.Underline), textAlign = TextAlign.End)
            Text("↗", style = ValueText)
        }
    }
}

/** 出典の文（§8）。文の中のアドレスに下線を付け、押すとブラウザで開く。 */
@Composable
private fun SourceNote(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val at = text.indexOf(url)
    val annotated = buildAnnotatedString {
        append(text)
        if (at >= 0) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), at, at + url.length)
    }
    Text(annotated, style = RowLabel.copy(fontSize = 13.sp), modifier = Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(url) } })
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<Pair<String, T>>,
    selected: T?,
    note: String? = null,
    enabled: Boolean = true,
    /** 初期値（選ぶ項目）。そのボタンを塗る。null なら塗らない */
    default: T? = null,
    /** 小さな選択肢（地図レイヤの項目）。名前とボタンの列をいつも同じ行に置く（名前は左で折り返し、ボタンの列は右端に揃う） */
    compact: Boolean = false,
    onSelect: (T) -> Unit,
) {
    val buttons: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((text, value) in options) {
                Chip(text, value == selected, enabled, compact, isDefault = default != null && value == default) { onSelect(value) }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (compact) {
            // 地図レイヤ: どの項目もボタンの列が同じ位置（右端）に並ぶ
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NameLine(label, null, enabled) }
                buttons()
            }
        } else {
            NameAndButtons(name = { NameLine(label, null, enabled) }, buttons = buttons)
        }
        note?.let { Text(it, style = RowNote) }
    }
}

/**
 * 名前とボタンの列: 1 行に収まれば同じ行に置き、ボタンの列を右端に揃える。
 * 収まらなければ、ボタンの列を名前の下の行に（左に寄せて）置く。
 */
@Composable
private fun NameAndButtons(name: @Composable () -> Unit, buttons: @Composable () -> Unit) {
    Layout(content = { Box { name() }; Box { buttons() } }) { measurables, constraints ->
        val gap = 10.dp.roundToPx()
        val rowGap = 4.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val n = measurables[0].measure(loose)
        val b = measurables[1].measure(loose)
        val width = constraints.maxWidth
        if (n.width + gap + b.width <= width) {
            val h = maxOf(n.height, b.height)
            layout(width, h) {
                n.placeRelative(0, (h - n.height) / 2)
                b.placeRelative(width - b.width, (h - b.height) / 2)
            }
        } else {
            layout(width, n.height + rowGap + b.height) {
                n.placeRelative(0, 0)
                b.placeRelative(0, n.height + rowGap)
            }
        }
    }
}

/** 選んでいる値の印の点（水色。塗ったボタンの上でも見えるよう、背景の色のふちを付ける） */
@Composable
private fun SelectedDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .background(HudColors.Background, CircleShape)
            .padding(1.5.dp)
            .background(color, CircleShape),
    )
}

/** 選んでいるボタンの水色の枠の太さ（仮。20261021-D01） */
private val SelectedFrame = 2.5.dp

/**
 * 選ぶボタン（20261021-D01 で初期値と選んでいる値の見た目を入れ替えた）。
 * - isDefault（初期値）: テーマの色で塗る（反転表示）
 * - on（今選んでいる）: 水色の太い枠と、右上の水色の点
 * - 初期値を選んでいるとき: 塗り＋水色の太い枠＋点
 * 押せないとき（自動車の固定値・背景地図が OFF）は、塗りと文字をグレー、水色を暗い水色にする
 */
@Composable
private fun Chip(text: String, on: Boolean, enabled: Boolean = true, compact: Boolean = false, isDefault: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val c = if (enabled) HudColors.Scale else HudColors.WpReached
    val mark = if (enabled) HudColors.SettingsSelected else HudColors.SettingsSelectedDim
    val frame = when {
        on -> mark
        isDefault -> c
        else -> HudColors.WpDisabled
    }
    Box {
        Box(
            Modifier
                .border(if (on) SelectedFrame else 1.dp, frame, shape)
                .background(if (isDefault) c else HudColors.Background, shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = 4.dp),
        ) {
            Text(text, style = ValueText.copy(color = if (isDefault) HudColors.Background else c, fontSize = if (compact) 12.sp else 13.sp))
        }
        if (on) SelectedDot(mark, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
    }
}

/** 設定画面のいちばん上の、ボタンの見た目の説明（「■ 塗り ＝ 初期値　□● 水色の枠 ＝ 選んでいる値」）。見本はボタンと同じ描き方の小さな四角 */
@Composable
private fun MarkLegend() {
    val shape = RoundedCornerShape(3.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(18.dp, 12.dp).background(HudColors.Scale, shape))
            Text(stringResource(R.string.settings_mark_default), style = RowNote)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box {
                Box(Modifier.padding(top = 3.dp, end = 3.dp).size(18.dp, 12.dp).border(2.dp, HudColors.SettingsSelected, shape))
                SelectedDot(HudColors.SettingsSelected, Modifier.align(Alignment.TopEnd))
            }
            Text(stringResource(R.string.settings_mark_selected), style = RowNote)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, note: String? = null, defaultText: String? = null, enabled: Boolean = true, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) { Label(label, note, enabled, defaultText) }
        HudButton("−", { onStep(-1) }, enabled = enabled, small = true)
        Text(
            value,
            style = if (enabled) ValueText else ValueText.copy(color = HudColors.WpReached),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        HudButton("＋", { onStep(1) }, enabled = enabled, small = true)
    }
}

/** 不透明度などの % を、スライダーで 20〜100% の 10% 刻みに選ぶ。 */
@Composable
private fun PercentSlider(
    label: String,
    pct: Int,
    note: String? = null,
    defaultText: String? = null,
    choices: List<Int> = NavSettings.OPACITY_CHOICES_PCT,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Label(label, note, defaultText = defaultText) }
            Text("$pct%", style = ValueText, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp))
        }
        // スライダーは高さを詰める（つまみと目盛りが収まる高さ）
        Slider(
            modifier = Modifier.height(32.dp),
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
private fun SettingsSwitch(on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
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

@Composable
private fun Toggle(
    label: String,
    on: Boolean,
    note: String? = null,
    enabled: Boolean = true,
    /** 初期値（ON / OFF）。名前の行に「（初期値：ON）」と書く。null なら書かない */
    default: Boolean? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Label(label, note, enabled, default?.let { if (it) "ON" else "OFF" }) }
        SettingsSwitch(on, enabled, onChange)
    }
}

