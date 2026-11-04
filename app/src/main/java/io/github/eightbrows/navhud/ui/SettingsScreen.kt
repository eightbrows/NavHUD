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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.AglNegative
import io.github.eightbrows.navhud.core.nav.ColorTheme
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.LandmarkItem
import io.github.eightbrows.navhud.core.nav.LanguageMode
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.ProfileSize
import io.github.eightbrows.navhud.core.nav.RangeAuto
import io.github.eightbrows.navhud.core.nav.ReachProfile
import io.github.eightbrows.navhud.core.nav.TrackColor
import io.github.eightbrows.navhud.core.nav.TravelMode
import io.github.eightbrows.navhud.core.nav.editReach
import io.github.eightbrows.navhud.core.nav.selectTravelMode
import io.github.eightbrows.navhud.core.view.HudFormat
import io.github.eightbrows.navhud.core.view.SettingKey
import io.github.eightbrows.navhud.core.view.SettingsOutline
import io.github.eightbrows.navhud.core.view.SettingsSectionId
import io.github.eightbrows.navhud.source.AppPermission
import io.github.eightbrows.navhud.source.PermissionStatus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val SectionTitle get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = HudColors.Scale)
private val RowLabel get() = TextStyle(fontSize = 14.sp, color = HudColors.Scale)
private val RowNote get() = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, color = HudColors.ScaleDim)
private val ValueText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = HudColors.Scale)

/**
 * 設定画面。NavSettings のすべての値を変えられる。変えたらすぐ画面に反映し、保存する。
 * セクション（20261104-D02 で作り直した）: 測位と方位 / 縮尺 / WP / 画面 / 距離環と軌跡 / 地図 / ランドマーク / その他 / 権限・情報・データの出典。
 * 並びと中身の項目は core の SettingsOutline。各セクションは枠で囲み、見出しを押すと開く・閉じる（閉じたセクションは覚える）。
 * いちばん上に目次（押すとそのセクションを開いてそこへ）と「▼ 全て開く」「▲ 全て閉じる」。各セクションの見出しの右に「▲ 目次」。
 * 「設定を初期値に戻す」はいちばん下（セクションの外。全部閉じていても押せる）。
 * INPUT（LIVE / REPLAY）は上部バーで切り替える（20261104-D02 で設定画面からは外した）。
 */
@Composable
fun SettingsScreen(
    settings: NavSettings,
    /** 今の表示言語の選択 */
    language: LanguageMode,
    /** 権限の今の状態（画面に戻ってきたときに読み直したもの） */
    permissions: List<PermissionStatus>,
    /** バージョン（appVersionName） */
    versionName: String,
    onChange: ((NavSettings) -> NavSettings) -> Unit,
    onReset: () -> Unit,
    onLanguage: (LanguageMode) -> Unit,
    /** 端末の、このアプリの設定画面を開く */
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 前に閉じたときのスクロール位置 [px]（20261023-D01。SettingsStore に保存。「設定を初期値に戻す」では戻さない） */
    initialScrollY: Int = 0,
    /** スクロールが止まったとき・画面を閉じたときに、今のスクロール位置を渡す（保存する） */
    onScrollY: (Int) -> Unit = {},
    /** 前に閉じたときの、閉じていたセクション（20261104-D02。SettingsStore に保存。「設定を初期値に戻す」では変えない） */
    initialClosed: Set<SettingsSectionId> = emptySet(),
    /** セクションを開いた・閉じたときに、閉じているセクションを渡す（保存する） */
    onClosedChange: (Set<SettingsSectionId>) -> Unit = {},
) {
    val s = settings
    // 数値を設定する項目の名前の行に書く初期値（NavSettings の既定値。到達の判定は自動車の値）
    val def = remember { NavSettings() }
    val latestOnClosed by rememberUpdatedState(onClosedChange)
    var closed by remember { mutableStateOf(initialClosed) }
    fun setClosed(v: Set<SettingsSectionId>) {
        if (v != closed) { closed = v; latestOnClosed(v) }
    }

    // 1 項目を描く（項目の並びは SettingsOutline.SECTIONS）
    val item: @Composable (SettingKey) -> Unit = { key ->
        val custom = s.travelMode != TravelMode.CAR
        fun judge(f: (ReachProfile) -> ReachProfile) = onChange { it.editReach(f) }
        val colors = listOf(
            stringResource(R.string.color_white) to ColorTheme.WHITE,
            stringResource(R.string.color_green) to ColorTheme.GREEN,
            stringResource(R.string.color_amber) to ColorTheme.AMBER,
        )
        val levels = NavSettings.MAP_LINE_LEVELS.map { it.toString() to it }
        val (lo, hi) = RangeAuto.limitsKm(s.rangeStepsKm, s.autoMinRangeKm, s.autoMaxRangeKm)
        when (key) {
            // ---- 測位と方位 ----
            SettingKey.HEADING_SOURCE -> Choice(
                stringResource(R.string.heading_source),
                listOf("GPS" to SourceMode.GPS, "HYBRID" to SourceMode.HYBRID, "COMPASS" to SourceMode.COMPASS),
                s.sourceMode,
                note = stringResource(R.string.heading_source_note),
                default = def.sourceMode,
            ) { v -> onChange { it.copy(sourceMode = v) } }
            SettingKey.RATE_WINDOW -> Choice(
                stringResource(R.string.rate_window), NavSettings.RATE_WINDOW_CHOICES_SEC.map { seconds(it) to it }, s.rateWindowSec,
                note = stringResource(R.string.rate_window_note), default = def.rateWindowSec,
            ) { v -> onChange { it.copy(rateWindowSec = v) } }
            // 方位を止める・戻す速度: km/h の整数（1〜36）、1 km/h 刻み。戻す速度は止める速度より 1 km/h 以上大きい。保存は m/s
            SettingKey.HOLD_ENTER -> Stepper(
                stringResource(R.string.hold_enter), kmh(NavSettings.holdSpeedKmh(s.holdEnterSpeedMps)),
                note = stringResource(R.string.hold_enter_note), defaultText = kmh(NavSettings.holdSpeedKmh(def.holdEnterSpeedMps)),
            ) { d ->
                onChange {
                    val r = NavSettings.HOLD_SPEED_KMH_RANGE
                    val enter = (NavSettings.holdSpeedKmh(it.holdEnterSpeedMps) + d).coerceIn(r.first, r.last - 1)
                    val exit = maxOf(NavSettings.holdSpeedKmh(it.holdExitSpeedMps), enter + 1)
                    it.copy(holdEnterSpeedMps = NavSettings.kmhToMps(enter), holdExitSpeedMps = NavSettings.kmhToMps(exit))
                }
            }
            SettingKey.HOLD_EXIT -> Stepper(
                stringResource(R.string.hold_exit), kmh(NavSettings.holdSpeedKmh(s.holdExitSpeedMps)),
                note = stringResource(R.string.hold_exit_note), defaultText = kmh(NavSettings.holdSpeedKmh(def.holdExitSpeedMps)),
            ) { d ->
                onChange {
                    val enter = NavSettings.holdSpeedKmh(it.holdEnterSpeedMps)
                    val exit = (NavSettings.holdSpeedKmh(it.holdExitSpeedMps) + d).coerceIn(enter + 1, NavSettings.HOLD_SPEED_KMH_RANGE.last)
                    it.copy(holdExitSpeedMps = NavSettings.kmhToMps(exit))
                }
            }
            // 決まった値から選ぶ（＋ / − で次の値へ）: 3 / 5 / 10 / 15 / 20 / 30 / 50 / 100 m
            SettingKey.MAX_GPS_ACC -> Stepper(
                stringResource(R.string.max_gps_acc), meters(s.maxGpsAccM),
                note = stringResource(R.string.max_gps_acc_note), defaultText = meters(def.maxGpsAccM),
            ) { d -> onChange { it.copy(maxGpsAccM = stepChoice(NavSettings.MAX_GPS_ACC_CHOICES_M, it.maxGpsAccM, d)) } }
            SettingKey.MAX_GPS_BEARING_ACC -> Stepper(
                stringResource(R.string.max_gps_bearing_acc), deg(s.maxGpsBearingAccDeg),
                note = stringResource(R.string.max_gps_bearing_acc_note), defaultText = deg(def.maxGpsBearingAccDeg),
            ) { d -> onChange { it.copy(maxGpsBearingAccDeg = (it.maxGpsBearingAccDeg + d * 5).coerceIn(NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE)) } }
            // 決まった値から選ぶ: 3 / 5 / 10 / 15 / 20 / 30 / 60 / 120 秒
            SettingKey.NO_FIX_TIMEOUT -> Stepper(
                stringResource(R.string.no_fix_timeout), seconds(s.noFixTimeoutSec),
                note = stringResource(R.string.no_fix_timeout_note), defaultText = seconds(def.noFixTimeoutSec),
            ) { d -> onChange { it.copy(noFixTimeoutSec = stepChoice(NavSettings.NO_FIX_TIMEOUT_CHOICES_SEC, it.noFixTimeoutSec, d)) } }
            SettingKey.ALT_OFFSET -> Stepper(
                stringResource(R.string.alt_offset), "%.0f".format(Locale.US, s.altOffsetM),
                note = stringResource(R.string.alt_offset_note), defaultText = meters(def.altOffsetM),
            ) { d -> onChange { it.copy(altOffsetM = (it.altOffsetM + d).coerceIn(NavSettings.ALT_OFFSET_M_RANGE)) } }

            // ---- 縮尺（段は 6 列のマス。表示は自機から 1 つ目の距離環の距離）----
            SettingKey.RANGE_STEPS -> RangeGrid(
                stringResource(R.string.range_steps), stringResource(R.string.range_steps_note), RangeAuto.ALL_STEPS_KM,
                isOn = { km -> s.rangeStepsKm.any { abs(it - km) < 1e-9 } }, isDefault = { km -> def.rangeStepsKm.any { abs(it - km) < 1e-9 } },
            ) { km -> onChange { it.copy(rangeStepsKm = SettingsOutline.toggleRangeStep(it.rangeStepsKm, km)) } }
            SettingKey.INITIAL_RANGE -> {
                val now = s.rangeStepsKm.minByOrNull { abs(it - s.initialRangeKm) }
                RangeGrid(
                    stringResource(R.string.initial_range), null, s.rangeStepsKm,
                    isOn = { it == now }, isDefault = { abs(it - def.initialRangeKm) < 1e-9 },
                ) { km -> onChange { it.copy(initialRangeKm = km) } }
            }
            SettingKey.AUTO_ON_START -> Toggle(stringResource(R.string.auto_on_start), s.autoRange, default = def.autoRange) { v ->
                onChange { it.copy(autoRange = v) }
            }
            // 詳細の下限は使う段のうち広域の上限と同じか、それより詳細（少ないので、ほかの選ぶ項目と同じボタンの列）
            SettingKey.AUTO_MIN -> Choice(
                stringResource(R.string.auto_min), s.rangeStepsKm.filter { it <= hi }.map { r1Label(it * 1000) to it }, lo, default = def.autoMinRangeKm,
            ) { v -> onChange { it.copy(autoMinRangeKm = v) } }
            // 広域の上限は詳細の下限から上（6 列のマス。選べない段のマスは空ける）
            SettingKey.AUTO_MAX -> RangeGrid(
                stringResource(R.string.auto_max), stringResource(R.string.auto_max_note), s.rangeStepsKm.filter { it >= lo },
                isOn = { it == hi }, isDefault = { abs(it - def.autoMaxRangeKm) < 1e-9 },
            ) { km -> onChange { it.copy(autoMaxRangeKm = km) } }
            SettingKey.ZOOM_IN_DELAY -> Stepper(
                stringResource(R.string.zoom_in_delay), seconds(s.autoRangeZoomInDelaySec),
                note = stringResource(R.string.zoom_in_delay_note), defaultText = seconds(def.autoRangeZoomInDelaySec),
            ) { d -> onChange { it.copy(autoRangeZoomInDelaySec = (it.autoRangeZoomInDelaySec + d).coerceIn(NavSettings.AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE)) } }
            // 決まった値から選ぶ: 0 / 5 / 10 / 15 / 20 / 30 / 60 秒
            SettingKey.HOLD_AFTER_WP -> Stepper(
                stringResource(R.string.hold_after_wp), seconds(s.autoHoldAfterWpSec), defaultText = seconds(def.autoHoldAfterWpSec),
            ) { d -> onChange { it.copy(autoHoldAfterWpSec = stepChoice(NavSettings.AUTO_HOLD_AFTER_WP_CHOICES_SEC, it.autoHoldAfterWpSec, d)) } }
            SettingKey.ZOOM_IN_RATIO -> Stepper(
                stringResource(R.string.zoom_in_ratio), stringResource(R.string.zoom_in_ratio_value, "%.1f".format(Locale.US, s.autoZoomInDistRatio)),
                note = stringResource(R.string.zoom_in_ratio_note),
                defaultText = stringResource(R.string.zoom_in_ratio_value, "%.1f".format(Locale.US, def.autoZoomInDistRatio)),
            ) { d ->
                onChange {
                    val c = NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES
                    val i = c.indexOfFirst { v -> abs(v - it.autoZoomInDistRatio) < 1e-9 }.coerceAtLeast(0)
                    it.copy(autoZoomInDistRatio = c[(i + d).coerceIn(0, c.lastIndex)])
                }
            }
            SettingKey.PAN_RETURN -> Stepper(stringResource(R.string.pan_return), seconds(s.panReturnSec), defaultText = seconds(def.panReturnSec)) { d ->
                onChange { it.copy(panReturnSec = (it.panReturnSec + d * 5).coerceIn(NavSettings.PAN_RETURN_SEC_RANGE)) }
            }

            // ---- WP（到達の判定の値: 自動車は推奨値で固定（表示だけ）、カスタム1〜3 は枠ごとに保存する）----
            SettingKey.TRAVEL_MODE -> Choice(
                stringResource(R.string.travel_mode),
                listOf(
                    stringResource(R.string.travel_car) to TravelMode.CAR,
                    stringResource(R.string.travel_custom1) to TravelMode.CUSTOM1,
                    stringResource(R.string.travel_custom2) to TravelMode.CUSTOM2,
                    stringResource(R.string.travel_custom3) to TravelMode.CUSTOM3,
                ),
                s.travelMode,
                note = stringResource(R.string.travel_mode_note),
                default = def.travelMode,
            ) { v -> onChange { it.selectTravelMode(v) } }
            SettingKey.WP_BUTTONS -> Stepper(stringResource(R.string.wp_buttons), count(s.wpButtonsMax), defaultText = count(def.wpButtonsMax)) { d ->
                onChange { it.copy(wpButtonsMax = (it.wpButtonsMax + d).coerceIn(NavSettings.WP_BUTTONS_MAX_RANGE)) }
            }
            SettingKey.HUD_WP_COUNT -> Stepper(stringResource(R.string.hud_wp_count), count(s.hudWpCount), defaultText = count(def.hudWpCount)) { d ->
                onChange { it.copy(hudWpCount = (it.hudWpCount + d).coerceIn(NavSettings.HUD_WP_COUNT_RANGE)) }
            }
            SettingKey.ARRIVAL_RADIUS -> {
                // カスタムのときだけ、到達の判定の値を自動車の値に戻すボタン（細かい調整のいちばん上）
                if (custom) HudButton(stringResource(R.string.reset_to_car), { onChange { it.editReach { ReachProfile.CAR } } })
                Choice(
                    stringResource(R.string.arrival_radius),
                    NavSettings.REACH_RADIUS_CHOICES_M.map { "%.0f".format(Locale.US, it) to it },
                    s.reachRadiusM,
                    default = def.reachRadiusM,
                    enabled = custom,
                ) { v -> judge { it.copy(reachRadiusM = v) } }
            }
            SettingKey.SIDE_PASS -> Toggle(stringResource(R.string.side_pass), s.sidePass, note = stringResource(R.string.side_pass_note), enabled = custom, default = def.sidePass) { v ->
                judge { it.copy(sidePass = v) }
            }
            SettingKey.SIDE_PASS_MAX -> Stepper(
                stringResource(R.string.side_pass_max),
                stringResource(R.string.within_meters, "%.0f".format(Locale.US, s.sidePassMaxM)),
                defaultText = stringResource(R.string.within_meters, "%.0f".format(Locale.US, def.sidePassMaxM)),
                enabled = custom,
            ) { d -> judge { it.copy(sidePassMaxM = (it.sidePassMaxM + d * 10).coerceIn(NavSettings.SIDE_PASS_MAX_M_RANGE)) } }
            SettingKey.SIDE_PASS_DEPART -> Stepper(
                stringResource(R.string.side_pass_depart), "+%.0f".format(Locale.US, s.sidePassDepartM),
                defaultText = "+%.0f".format(Locale.US, def.sidePassDepartM), enabled = custom,
            ) { d -> judge { it.copy(sidePassDepartM = (it.sidePassDepartM + d * 5).coerceIn(NavSettings.SIDE_PASS_DEPART_M_RANGE)) } }
            SettingKey.PASS_DETECTION -> Toggle(
                stringResource(R.string.pass_detection), s.passDetection, note = stringResource(R.string.pass_detection_note), enabled = custom, default = def.passDetection,
            ) { v -> judge { it.copy(passDetection = v) } }
            SettingKey.PASS_MAX -> Stepper(
                stringResource(R.string.pass_max), "%.0f".format(Locale.US, s.passMaxApproachM), defaultText = meters(def.passMaxApproachM), enabled = custom,
            ) { d -> judge { it.copy(passMaxApproachM = (it.passMaxApproachM + d * 50).coerceIn(NavSettings.PASS_MAX_APPROACH_M_RANGE)) } }
            SettingKey.PASS_DEPART -> Stepper(
                stringResource(R.string.pass_depart), "+%.0f".format(Locale.US, s.passDepartM), defaultText = "+%.0f".format(Locale.US, def.passDepartM), enabled = custom,
            ) { d -> judge { it.copy(passDepartM = (it.passDepartM + d * 10).coerceIn(NavSettings.PASS_DEPART_M_RANGE)) } }
            SettingKey.PASS_HOLD -> Stepper(stringResource(R.string.pass_hold), seconds(s.passHoldSec), defaultText = seconds(def.passHoldSec), enabled = custom) { d ->
                judge { it.copy(passHoldSec = (it.passHoldSec + d).coerceIn(NavSettings.PASS_HOLD_SEC_RANGE)) }
            }

            // ---- 画面 ----
            SettingKey.DISPLAY_MODE -> Choice(
                stringResource(R.string.display_mode), listOf("ARC" to DisplayMode.ARC, "North Up" to DisplayMode.NORTH_UP), s.displayMode, default = def.displayMode,
            ) { v -> onChange { it.copy(displayMode = v) } }
            SettingKey.UI_COLOR -> Choice(stringResource(R.string.ui_color), colors, s.uiTheme, note = stringResource(R.string.ui_color_note), default = def.uiTheme) { v ->
                onChange { it.copy(uiTheme = v) }
            }
            SettingKey.MAP_COLOR -> Choice(stringResource(R.string.map_color), colors, s.mapTheme, note = stringResource(R.string.map_color_note), default = def.mapTheme) { v ->
                onChange { it.copy(mapTheme = v) }
            }
            SettingKey.BUTTON_OPACITY -> Choice(
                stringResource(R.string.button_opacity), percentChoices(NavSettings.OPACITY_CHOICES_PCT), s.buttonOpacityPct, default = def.buttonOpacityPct,
            ) { v -> onChange { it.copy(buttonOpacityPct = v) } }
            SettingKey.NUMBERS_OPACITY -> Choice(
                stringResource(R.string.numbers_opacity), percentChoices(NavSettings.OPACITY_CHOICES_PCT), s.numbersOpacityPct,
                note = stringResource(R.string.numbers_opacity_note), default = def.numbersOpacityPct,
            ) { v -> onChange { it.copy(numbersOpacityPct = v) } }
            SettingKey.OWNSHIP_POSITION -> Choice(
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
            SettingKey.PROFILE -> Choice(
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
            // 地上高の負の値（20261025-D08）: 0 にする（初期値）/ 負のまま出す（警告の色）
            SettingKey.AGL_NEGATIVE -> Choice(
                stringResource(R.string.agl_negative),
                listOf(stringResource(R.string.agl_negative_zero) to AglNegative.ZERO, stringResource(R.string.agl_negative_show) to AglNegative.SHOW),
                s.aglNegative,
                note = stringResource(R.string.agl_negative_note),
                default = def.aglNegative,
            ) { v -> onChange { it.copy(aglNegative = v) } }

            // ---- 距離環と軌跡 ----
            // 距離環の明るさ [%]（20261103-D01・D02 は段、20261104-D01 から %）。距離環の線だけ
            SettingKey.RING_BRIGHTNESS -> Choice(
                stringResource(R.string.ring_brightness), percentChoices(NavSettings.RING_BRIGHTNESS_CHOICES_PCT), s.ringBrightnessPct, default = def.ringBrightnessPct,
            ) { v -> onChange { it.copy(ringBrightnessPct = v) } }
            SettingKey.RING_LABEL_SIZE -> Choice(
                stringResource(R.string.ring_label_size), percentChoices(NavSettings.RING_LABEL_SCALE_CHOICES_PCT), s.ringLabelScalePct, default = def.ringLabelScalePct,
            ) { v -> onChange { it.copy(ringLabelScalePct = v) } }
            SettingKey.TRACK_COLOR -> Choice(
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
            SettingKey.TRACK_BRIGHTNESS -> Choice(
                stringResource(R.string.track_brightness), percentChoices(NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT), s.trackBrightnessPct, default = def.trackBrightnessPct,
            ) { v -> onChange { it.copy(trackBrightnessPct = v) } }

            // ---- 地図（§8）: まとめて消すスイッチ、線の太さ・明るさ、項目ごとの「どの縮尺まで描くか」の表 ----
            // 背景地図が OFF のときは、下の項目は押せない（値は残る）
            SettingKey.BASE_MAP -> {
                Text(stringResource(R.string.map_layers_note), style = RowNote)
                Toggle(stringResource(R.string.base_map), s.baseMap, default = def.baseMap) { v -> onChange { it.copy(baseMap = v) } }
            }
            SettingKey.MAP_LINE_WIDTH -> Choice(stringResource(R.string.map_line_width), levels, s.mapLineWidth, enabled = s.baseMap, default = def.mapLineWidth) { v ->
                onChange { it.copy(mapLineWidth = v) }
            }
            SettingKey.MAP_LINE_BRIGHTNESS -> Choice(stringResource(R.string.map_line_brightness), levels, s.mapLineBrightness, enabled = s.baseMap, default = def.mapLineBrightness) { v ->
                onChange { it.copy(mapLineBrightness = v) }
            }
            SettingKey.MAP_TABLE -> {
                val rows = SettingsOutline.MAP_TABLE_ROWS
                RangeTable(
                    labels = rows.map { stringResource(mapItemLabel(it)) },
                    current = rows.map { s.mapRangeOf(it) },
                    defaults = rows.map { def.mapRangeOf(it) },
                    enabled = s.baseMap,
                ) { i, v -> onChange { it.withMapRange(rows[i], v) } }
            }

            // ---- ランドマーク（20261021-D04）: 空港・山頂の記号。背景地図のスイッチによらず押せて、描く ----
            SettingKey.LANDMARK_BRIGHTNESS -> {
                Text(stringResource(R.string.landmarks_note), style = RowNote)
                Choice(stringResource(R.string.landmark_brightness), levels, s.landmarkBrightness, default = def.landmarkBrightness) { v ->
                    onChange { it.copy(landmarkBrightness = v) }
                }
            }
            SettingKey.LANDMARK_TABLE -> {
                val rows = SettingsOutline.LANDMARK_TABLE_ROWS
                RangeTable(
                    labels = rows.map { stringResource(landmarkLabel(it)) },
                    current = rows.map { s.landmarkRangeOf(it) },
                    defaults = rows.map { def.landmarkRangeOf(it) },
                    enabled = true,
                ) { i, v -> onChange { it.withLandmarkRange(rows[i], v) } }
            }

            // ---- その他 ----
            SettingKey.KEEP_SCREEN_ON -> Toggle(stringResource(R.string.keep_screen_on), s.keepScreenOn, default = def.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            SettingKey.AUTO_OPEN_LAST -> Toggle(stringResource(R.string.auto_open_last), s.autoOpenLastList, default = def.autoOpenLastList) { v ->
                onChange { it.copy(autoOpenLastList = v) }
            }
            // 選ぶと画面が作り直されて、すぐに切り替わる。「設定を初期値に戻す」では変えない（NavSettings の外）
            SettingKey.APP_LANGUAGE -> Choice(
                stringResource(R.string.app_language),
                listOf(
                    stringResource(R.string.language_system) to LanguageMode.SYSTEM,
                    stringResource(R.string.language_japanese) to LanguageMode.JAPANESE,
                    stringResource(R.string.language_english) to LanguageMode.ENGLISH,
                ),
                language,
                default = LanguageMode.SYSTEM,
                onSelect = onLanguage,
            )

            // ---- 権限・情報・データの出典（中身は 20261104-D01 までと同じ。NavSettings の外: 見るだけ）----
            SettingKey.PERMISSIONS -> {
                SubHeading(stringResource(R.string.sec_permissions), first = true)
                Text(stringResource(R.string.permissions_note), style = RowNote)
                for (p in permissions) {
                    val (label, note) = when (p.permission) {
                        AppPermission.FINE_LOCATION -> R.string.perm_fine to R.string.perm_fine_note
                        AppPermission.COARSE_LOCATION -> R.string.perm_coarse to R.string.perm_coarse_note
                        AppPermission.NOTIFICATIONS -> R.string.perm_notifications to R.string.perm_notifications_note
                    }
                    PermissionRow(stringResource(label), stringResource(note), p.granted, onOpenAppSettings)
                }
            }
            SettingKey.ABOUT -> {
                SubHeading(stringResource(R.string.sec_about))
                InfoRow(stringResource(R.string.about_app), stringResource(R.string.about_app_value))
                InfoRow(stringResource(R.string.about_version), versionName)
                InfoRow(stringResource(R.string.about_license), "Apache License 2.0", "$REPO_URL/blob/main/LICENSE")
                InfoRow(stringResource(R.string.about_website), "eightbrows.github.io", WEBSITE_URL)
                // WP エディタ（20261102-D01）: WP設定の画面の「WP エディタ」と同じページ。アドレスが長いので、項目名の下にアドレス
                InfoLinkBlock(stringResource(R.string.about_wp_editor), WP_EDITOR_URL)
                InfoRow(stringResource(R.string.about_cheatsheet), "docs/cheatsheet.pdf", "$REPO_URL/blob/main/docs/cheatsheet.pdf")
                InfoRow(stringResource(R.string.about_formats), "docs/formats.pdf", "$REPO_URL/blob/main/docs/formats.pdf")
                Text(stringResource(R.string.about_links_note), style = RowNote)
            }
            // データの出典（20261023-D01。20261024-D02 で短くした）: データごとに、タイトル・組織名・URL・ライセンス・使用箇所・「〜をもとに作成」の文。
            // OurAirports はパブリックドメインなので、「〜をもとに作成」の文はない
            SettingKey.SOURCES -> {
                SubHeading(stringResource(R.string.sec_sources))
                Text(stringResource(R.string.sources_note), style = RowNote)
                SourceBlock(
                    stringResource(R.string.src_title_basemap), stringResource(R.string.src_org_basemap), BASEMAP_SOURCE_URL,
                    stringResource(R.string.src_license_basemap), stringResource(R.string.src_use_basemap),
                    stringResource(R.string.about_basemap_source, BASEMAP_SOURCE_URL),
                )
                SourceBlock(
                    stringResource(R.string.src_title_peak), stringResource(R.string.src_org_peak), PEAK_SOURCE_URL,
                    stringResource(R.string.src_license_peak), stringResource(R.string.src_use_peak),
                    stringResource(R.string.about_peak_source),
                )
                SourceBlock(
                    stringResource(R.string.src_title_airport), stringResource(R.string.src_org_airport), AIRPORT_SOURCE_URL,
                    stringResource(R.string.src_license_airport), stringResource(R.string.src_use_airport),
                    stringResource(R.string.about_airport_source, AIRPORT_SOURCE_URL),
                )
                // 地上高の最高標高（国土数値情報「標高・傾斜度5次メッシュ（G04-d）」。20261025-D07）
                SourceBlock(
                    stringResource(R.string.src_title_elevation), stringResource(R.string.src_org_elevation), ELEVATION_SOURCE_URL,
                    stringResource(R.string.src_license_elevation), stringResource(R.string.src_use_elevation),
                    stringResource(R.string.about_elevation_source),
                )
                SourceBlock(
                    stringResource(R.string.src_title_ourairports), stringResource(R.string.src_org_ourairports), OURAIRPORTS_URL,
                    stringResource(R.string.src_license_ourairports), stringResource(R.string.src_use_ourairports), null,
                )
            }
        }
    }

    // スクロール位置は、前に閉じたときの位置から始め、止まったとき・閉じたときに保存する（目次で飛んだあとも、止まったときに保存される）。
    // 閉じたセクションも同時に戻すので、本文の高さは前に閉じたときと同じになり、同じ位置に戻る
    val scroll = rememberScrollState(initialScrollY)
    val latestOnScrollY by rememberUpdatedState(onScrollY)
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.filter { !it }.collect { latestOnScrollY(scroll.value) }
    }
    DisposableEffect(scroll) { onDispose { latestOnScrollY(scroll.value) } }
    val scope = rememberCoroutineScope()
    // セクションの枠の上端の位置（Column の中の y [px]）。目次を押したら、そこまでスクロールする
    val tops = remember { mutableMapOf<SettingsSectionId, Int>() }
    val sections = SettingsOutline.SECTIONS
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
        Toc(
            titles = sections.map { sectionTitle(it.id) },
            onJump = { i ->
                val id = sections[i].id
                // 閉じていれば開いてから、そのセクションへ（開いた中身が描かれて本文が伸びてから、スクロールする）
                setClosed(SettingsOutline.openForJump(closed, id))
                scope.launch {
                    withFrameNanos { }
                    withFrameNanos { }
                    tops[id]?.let { scroll.animateScrollTo(it) }
                }
            },
            onOpenAll = { setClosed(SettingsOutline.openAll()) },
            onCloseAll = { setClosed(SettingsOutline.closeAll()) },
        )
        for (sec in sections) {
            Section(
                sectionTitle(sec.id),
                open = sec.id !in closed,
                onToggle = { setClosed(SettingsOutline.toggle(closed, sec.id)) },
                onToc = { scope.launch { scroll.animateScrollTo(0) } },
                modifier = Modifier.onGloballyPositioned { tops[sec.id] = it.positionInParent().y.roundToInt() },
            ) {
                // 縮尺の見出しのすぐ下に 1 回だけ: 「自動縮尺」は画面の AUTO のこと
                if (sec.id == SettingsSectionId.RANGE) Text(stringResource(R.string.range_auto_meaning), style = RowNote)
                for (k in sec.items) item(k)
                if (sec.fine.isNotEmpty()) {
                    SubHeading(stringResource(if (sec.id == SettingsSectionId.WP) R.string.settings_fine_reach else R.string.settings_fine))
                    for (k in sec.fine) item(k)
                }
            }
        }
        // 設定を初期値に戻す（いちばん下。言語・閉じたセクション・スクロール位置は変えない）
        Row(Modifier.padding(top = 4.dp, bottom = 8.dp)) { HudButton(stringResource(R.string.reset_settings), onReset) }
    }
}

@Composable
private fun sectionTitle(id: SettingsSectionId): String = stringResource(
    when (id) {
        SettingsSectionId.POSITION_HEADING -> R.string.sec_position_heading
        SettingsSectionId.RANGE -> R.string.sec_range
        SettingsSectionId.WP -> R.string.sec_wp
        SettingsSectionId.DISPLAY -> R.string.sec_display
        SettingsSectionId.RINGS_TRACK -> R.string.sec_rings_track
        SettingsSectionId.MAP -> R.string.sec_map_layers
        SettingsSectionId.LANDMARKS -> R.string.sec_landmarks
        SettingsSectionId.OTHER -> R.string.sec_other
        SettingsSectionId.INFO -> R.string.sec_info
    },
)

/** GitHub のリポジトリ（「情報」の欄のライセンス・チートシート・ファイル形式の説明のリンク先）。ファイルは main ブランチのものを開く */
private const val REPO_URL = "https://github.com/eightbrows/NavHUD"

/** 公式サイト（GpsLogger の「情報」の欄と同じ） */
private const val WEBSITE_URL = "https://eightbrows.github.io/"

/** 地図・ランドマークの表の列の見出し: OFF ／ 〜5k ／ 〜10k ／ 〜25k ／ 〜50k ／ 〜100k（SettingsOutline.TABLE_COLUMNS の順） */
@Composable
private fun mapRangeLabel(r: MapRange): String = stringResource(
    when (r) {
        MapRange.OFF -> R.string.map_range_off
        MapRange.UPTO_5KM -> R.string.map_range_5
        MapRange.UPTO_10KM -> R.string.map_range_10
        MapRange.UPTO_25KM -> R.string.map_range_25
        MapRange.UPTO_50KM -> R.string.map_range_50
        MapRange.UPTO_100KM -> R.string.map_range_100
    },
)

private fun landmarkLabel(item: LandmarkItem): Int = when (item) {
    LandmarkItem.AIRPORT_CIVIL_LARGE -> R.string.landmark_airport_civil_large
    LandmarkItem.AIRPORT_CIVIL_SMALL -> R.string.landmark_airport_civil_small
    LandmarkItem.AIRPORT_MIL_LARGE -> R.string.landmark_airport_mil_large
    LandmarkItem.AIRPORT_MIL_SMALL -> R.string.landmark_airport_mil_small
    LandmarkItem.AIRPORT_AREA -> R.string.landmark_airport_area
    LandmarkItem.AIRPORT_CODE -> R.string.landmark_airport_code
    LandmarkItem.RUNWAY -> R.string.landmark_runway
    LandmarkItem.RUNWAY_EXT -> R.string.landmark_runway_ext
    LandmarkItem.PEAK_HIGH -> R.string.landmark_peak_high
    LandmarkItem.PEAK_MID -> R.string.landmark_peak_mid
    LandmarkItem.PEAK_LOW -> R.string.landmark_peak_low
}

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
}

/** 山頂の出典（国土地理院「日本の主な山岳標高（1003山）」のページ。CSV はこのページから取る） */
private const val PEAK_SOURCE_URL = "https://www.gsi.go.jp/kihonjohochousa/kihonjohochousa41139.html"

/** 空港の略号と自衛隊・米軍の飛行場の出典（OurAirports のデータのページ。パブリックドメイン） */
private const val OURAIRPORTS_URL = "https://ourairports.com/data/"

/** 空港の出典（国土数値情報「空港データ」のページ。2020 年版もこのページから取る） */
private const val AIRPORT_SOURCE_URL = "https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-C28-2021.html"

/** 地上高の最高標高の出典（国土数値情報「標高・傾斜度5次メッシュ（G04-d）」のページ。2011 年度版。20261025-D07） */
private const val ELEVATION_SOURCE_URL = "https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-G04-d.html"

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

private fun deg(d: Float) = "%.0f°".format(Locale.US, d)

@Composable
private fun seconds(sec: Int) = stringResource(R.string.value_seconds, sec.toString())

private fun pct(p: Int) = "$p%"

/** % の選ぶボタン（「70%」など）。20261103-D02 */
private fun percentChoices(values: List<Int>): List<Pair<String, Int>> = values.map { pct(it) to it }

// 設定画面の距離は、m は数字だけ、km は「k」（距離環の文字と同じ書き方。20261020-D02）
private fun meters(m: Number) = "%.0f".format(Locale.US, m.toDouble())

/** 縮尺の段 [m] の表示: 自機から 1 つ目の距離環の距離（段の 1/2）を、距離環の文字と同じ書き方で（25 / 250 / 2.5k / 100k） */
private fun r1Label(rangeM: Double) = HudFormat.ringLabel(rangeM / 2)

@Composable
private fun count(n: Int) = stringResource(R.string.value_count, n.toString())

/**
 * 目次: セクションの見出しを、選択肢と同じ形のボタンで並べる（折り返す）。押すと、そのセクションを開いて見出しまでスクロールする。
 * 「目次」の右に「▼ 全て開く」「▲ 全て閉じる」（20261104-D02）
 */
@Composable
private fun Toc(titles: List<String>, onJump: (Int) -> Unit, onOpenAll: () -> Unit, onCloseAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_toc), style = RowNote, modifier = Modifier.weight(1f))
            Chip(stringResource(R.string.settings_open_all), on = false, compact = true, onClick = onOpenAll)
            Chip(stringResource(R.string.settings_close_all), on = false, compact = true, onClick = onCloseAll)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val mixed = mixedScripts(titles)
            titles.forEachIndexed { i, t -> Chip(t, on = false, cjkHeight = mixed) { onJump(i) } }
        }
    }
}

/**
 * セクションの枠（全体を線で囲む）。見出しの左に ▼（開いている）/ ▶（閉じている）、押すと開く・閉じる（20261104-D02）。
 * 見出しの右に「▲ 目次」（押すと、いちばん上の目次に戻る）。閉じているときは見出しの行だけ
 */
@Composable
private fun Section(title: String, open: Boolean, onToggle: () -> Unit, onToc: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, HudColors.WpReached, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (open) "▼" else "▶", style = SectionTitle.copy(fontSize = 12.sp, color = HudColors.ScaleDim))
                Text(title, style = SectionTitle)
            }
            Chip(stringResource(R.string.back_to_toc), on = false, compact = true, onClick = onToc)
        }
        if (open) content()
    }
}

/** セクションの中の小見出し（「細かい調整」・権限・情報・データの出典）。右に細い線。たたまない */
@Composable
private fun SubHeading(text: String, first: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 0.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = SectionTitle.copy(fontSize = 13.sp, color = HudColors.ScaleDim))
        Box(Modifier.weight(1f).height(1.dp).background(HudColors.WpReached))
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

/** 情報の、アドレスの長いリンク（20261102-D01）: 項目名の下に URL（下線と ↗。押すとブラウザで開く。データの出典の URL と同じ） */
@Composable
private fun InfoLinkBlock(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(label)
        Text(
            "$url ↗",
            style = RowLabel.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline),
            modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
        )
    }
}

/**
 * データの出典の 1 つ分（20261023-D01。20261024-D02 で短くした）: タイトル（データの名前。太字）・組織名・URL（下線。押すとブラウザで開く）・
 * ライセンス（規約の名前と、最新の規約を参照してほしい一文）・使用箇所（このアプリで使っている所）・「〜をもとに作成」の文 attribution（なければ書かない）
 */
@Composable
private fun SourceBlock(title: String, org: String, url: String, license: String, use: String, attribution: String?) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = RowLabel.copy(fontWeight = FontWeight.Bold))
        Text(org, style = RowLabel.copy(fontSize = 13.sp))
        Text(
            "$url ↗",
            style = RowLabel.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline),
            modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
        )
        Text(stringResource(R.string.src_license_fmt, license), style = RowNote)
        Text(stringResource(R.string.src_use_fmt, use), style = RowNote)
        attribution?.let { Text(it, style = RowNote) }
    }
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
    onSelect: (T) -> Unit,
) {
    val mixed = mixedScripts(options.map { it.first })
    val buttons: @Composable () -> Unit = {
        // ボタンが 2 行以上になるときも、各行を右端にそろえる（20261104-D01）
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((text, value) in options) {
                Chip(text, value == selected, enabled, isDefault = default != null && value == default, cjkHeight = mixed) { onSelect(value) }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // 名前は左、ボタンの列は右端。収まらないときは名前を上の行（左）に、ボタンの列をその下の行（右端）に置く（20261104-D01）
        NameAndButtons(name = { NameLine(label, null, enabled) }, buttons = buttons)
        note?.let { Text(it, style = RowNote) }
    }
}

/**
 * 名前とボタンの列: 1 行に収まれば同じ行に置き、ボタンの列を右端に揃える。
 * 収まらなければ、ボタンの列を名前の下の行の右端に置く（20261104-D01。前は項目によって左に寄せていた）。
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
                b.placeRelative(width - b.width, n.height + rowGap)
            }
        }
    }
}

/** 縮尺の段のマスの幅（6 列。「2.5k」「100k」が入る幅） */
private val RangeCellWidth = 46.dp

/**
 * 縮尺の段を選ぶ 6 列のマス（20261104-D02）: 名前は上の行の左、マスはその下の行に右寄せ。
 * 上の行が R1 25 / 50 / 100 / 250 / 500 / 1k、下の行が 2.5k / 5k / 10k / 25k / 50k / 100k。choicesKm にない段のマスは空ける（列を揃える）。
 * ボタンの見た目は選ぶボタンと同じ（初期値は塗り、選んでいる段は水色の枠と点）
 */
@Composable
private fun RangeGrid(
    label: String, note: String?, choicesKm: List<Double>,
    isOn: (Double) -> Boolean, isDefault: (Double) -> Boolean, onTap: (Double) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label, note)
        Column(Modifier.align(Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in SettingsOutline.rangeGridSlots(choicesKm).chunked(SettingsOutline.RANGE_GRID_COLUMNS)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (km in row) {
                        Box(Modifier.width(RangeCellWidth)) {
                            if (km != null) Chip(r1Label(km * 1000), isOn(km), compact = true, isDefault = isDefault(km), fillWidth = true) { onTap(km) }
                        }
                    }
                }
            }
        }
    }
}

/** 表のマスの幅と高さ（押しやすさのため高さは 36dp 以上） */
private val TableCellWidth = 34.dp
private val TableCellHeight = 36.dp

/**
 * 地図・ランドマークの「どの縮尺まで描くか」の表（20261104-D02）。行が項目、列が OFF / 〜5k / 〜10k / 〜25k / 〜50k / 〜100k
 * （列の見出しは表の上に 1 回だけ）。マスは丸のボタン: 今の値は水色の丸（中を塗る）、初期値のマスは灰色の塗り。
 * 項目の名前は左の残りの幅で折り返す。enabled が false（背景地図が OFF）のときは暗くして押せない（値は残る）
 */
@Composable
private fun RangeTable(labels: List<String>, current: List<MapRange>, defaults: List<MapRange>, enabled: Boolean, onSelect: (Int, MapRange) -> Unit) {
    val head = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (enabled) HudColors.ScaleDim else HudColors.WpReached)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f))
            for (c in SettingsOutline.TABLE_COLUMNS) {
                Box(Modifier.width(TableCellWidth), contentAlignment = Alignment.Center) {
                    Text(mapRangeLabel(c), style = head, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
                }
            }
        }
        for (i in labels.indices) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(HudColors.Frame))
            Row(Modifier.fillMaxWidth().heightIn(min = TableCellHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    labels[i],
                    style = RowLabel.copy(fontSize = 13.sp, lineHeight = 16.sp, color = if (enabled) HudColors.Scale else HudColors.WpReached),
                    modifier = Modifier.weight(1f).padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
                )
                for (cell in SettingsOutline.tableRow(current[i], defaults[i])) {
                    RadioCell(cell.selected, cell.isDefault, enabled) { onSelect(i, cell.column) }
                }
            }
        }
    }
}

/** 表の 1 マス: 丸（今の値は水色の太い輪と中の点）。初期値のマスは灰色で塗る */
@Composable
private fun RadioCell(selected: Boolean, isDefault: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val mark = if (enabled) HudColors.SettingsSelected else HudColors.SettingsSelectedDim
    val ring = if (enabled) HudColors.WpDisabled else HudColors.WpReached
    Box(
        Modifier.size(TableCellWidth, TableCellHeight).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isDefault) {
            Box(
                Modifier
                    .size(TableCellWidth - 4.dp, TableCellHeight - 6.dp)
                    .background(if (enabled) HudColors.SettingsDefaultCell else HudColors.SettingsDefaultCellDim, RoundedCornerShape(4.dp)),
            )
        }
        Box(
            Modifier.size(18.dp).border(if (selected) 2.dp else 1.dp, if (selected) mark else ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).background(mark, CircleShape))
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
private fun Chip(
    text: String, on: Boolean, enabled: Boolean = true, compact: Boolean = false, isDefault: Boolean = false,
    /** 日本語の字体の文字のボタンと同じ並びにあるとき true: 英字だけのボタンも、日本語の字体の文字と同じ高さにする（20261024-D08） */
    cjkHeight: Boolean = false,
    /** 親の幅いっぱいにする（縮尺の段のマス。文字は真ん中） */
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    val c = if (enabled) HudColors.Scale else HudColors.WpReached
    val mark = if (enabled) HudColors.SettingsSelected else HudColors.SettingsSelectedDim
    val frame = when {
        on -> mark
        isDefault -> c
        else -> HudColors.WpDisabled
    }
    Box(if (fillWidth) Modifier.fillMaxWidth() else Modifier) {
        Box(
            (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .border(if (on) SelectedFrame else 1.dp, frame, shape)
                .background(if (isDefault) defaultFill(c) else HudColors.Background, shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val style = ValueText.copy(color = if (isDefault) HudColors.Background else c, fontSize = if (compact) 12.sp else 13.sp)
            // 日本語の画面では、英字だけのボタン（OFF・WP など）が、「〜5k」「小」など日本語の字体の文字を含むボタンより低く、上に寄っていた
            // （日本語の字体のほうが行が高い）。日本語の字体の行と同じ高さ・同じベースラインに置いて揃える（20261024-D08。英語の並びは英字だけなので変わらない）
            if (cjkHeight && !hasCjk(text)) CjkLineText(text, style) else Text(text, style = style)
        }
        if (on) SelectedDot(mark, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
    }
}

/**
 * 英字だけの文字を、日本語の字体の文字を含む行と同じ高さ・同じベースラインで描く（20261024-D08）。
 * 見えない「〜」（日本語の字体で描かれる）を測って、その行の高さとベースラインに合わせる（「〜」は描かない。幅は文字の幅のまま）
 */
@Composable
private fun CjkLineText(text: String, style: TextStyle) {
    Layout(content = { Text("〜", style = style); Text(text, style = style) }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val ref = measurables[0].measure(loose)
        val t = measurables[1].measure(loose)
        val top = (ref[FirstBaseline] - t[FirstBaseline]).coerceAtLeast(0)
        layout(t.width, maxOf(ref.height, top + t.height)) { t.placeRelative(0, top) }
    }
}

/** 日本語の字体で描く文字（かな・漢字・全角の記号など。U+3000 から）を含むか（20261024-D08） */
private fun hasCjk(s: String): Boolean = s.any { it >= '　' }

/** 選択肢の並びに、日本語の字体の文字を含むものと含まないものが混ざっているか（20261024-D08） */
private fun mixedScripts(texts: List<String>): Boolean = texts.any(::hasCjk) && !texts.all(::hasCjk)

/**
 * 設定画面のいちばん上の、印の説明（「塗り・灰色のマス ＝ 初期値」「水色の枠・水色の丸 ＝ 選んでいる値」）。
 * 見本はボタン・表のマスと同じ描き方の小さな形
 */
@Composable
private fun MarkLegend() {
    val shape = RoundedCornerShape(3.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(18.dp, 12.dp).background(defaultFill(HudColors.Scale), shape))
            Box(Modifier.size(14.dp).background(HudColors.SettingsDefaultCell, shape))
            Text(stringResource(R.string.settings_mark_default), style = RowNote)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box {
                Box(Modifier.padding(top = 3.dp, end = 3.dp).size(18.dp, 12.dp).border(2.dp, HudColors.SettingsSelected, shape))
                SelectedDot(HudColors.SettingsSelected, Modifier.align(Alignment.TopEnd))
            }
            Box(Modifier.size(12.dp).border(2.dp, HudColors.SettingsSelected, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(5.dp).background(HudColors.SettingsSelected, CircleShape))
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

/**
 * 初期値のボタンの塗り（20261024-D04）: テーマの色の明るさに Tuning.SETTINGS_DEFAULT_FILL_FACTOR を掛けた色（少し暗くして、
 * 選んでいる値の水色の枠と見分けやすく、文字（黒）も読めるように）
 */
private fun defaultFill(c: Color): Color {
    val k = Tuning.SETTINGS_DEFAULT_FILL_FACTOR
    return Color(red = c.red * k, green = c.green * k, blue = c.blue * k, alpha = c.alpha)
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
