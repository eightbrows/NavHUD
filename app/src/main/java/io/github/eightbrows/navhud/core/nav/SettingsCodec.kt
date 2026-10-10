package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.SourceMode

/**
 * NavSettings ⇔ 文字列のマップ（SharedPreferences に保存するため）。Android に依存しない。
 * 読めない値・ない値は既定値にする（アプリの更新で項目が増減しても落ちない）。
 */
object SettingsCodec {

    fun encode(s: NavSettings): Map<String, String> = mapOf(
        "sourceMode" to s.sourceMode.name,
        "holdEnterSpeedMps" to s.holdEnterSpeedMps.toString(),
        "holdExitSpeedMps" to s.holdExitSpeedMps.toString(),
        "maxGpsAccM" to s.maxGpsAccM.toString(),
        "maxGpsBearingAccDeg" to s.maxGpsBearingAccDeg.toString(),
        "travelMode" to s.travelMode.name,
        "reachRadiusM" to s.reachRadiusM.toString(),
        "sidePass" to s.sidePass.toString(),
        "sidePassMaxM" to s.sidePassMaxM.toString(),
        "sidePassDepartM" to s.sidePassDepartM.toString(),
        "passDetection" to s.passDetection.toString(),
        "passMaxApproachM" to s.passMaxApproachM.toString(),
        "passDepartM" to s.passDepartM.toString(),
        "passHoldSec" to s.passHoldSec.toString(),
        "rateWindowSec" to s.rateWindowSec.toString(),
        "noFixTimeoutSec" to s.noFixTimeoutSec.toString(),
        "altOffsetM" to s.altOffsetM.toString(),
        "displayMode" to s.displayMode.name,
        "rangeStepsKm" to s.rangeStepsKm.joinToString(","),
        "initialRangeKm" to s.initialRangeKm.toString(),
        "autoRange" to s.autoRange.toString(),
        "autoRangeZoomInDelaySec" to s.autoRangeZoomInDelaySec.toString(),
        "autoMinRangeKm" to s.autoMinRangeKm.toString(),
        "autoMaxRangeKm" to s.autoMaxRangeKm.toString(),
        "autoHoldAfterWpSec" to s.autoHoldAfterWpSec.toString(),
        // D10 で意味が変わった（1つ詳細側の段の R1 → 今の段の R1）ので、前の版のキー autoZoomInDistRatio とは別のキーにする
        KEY_ZOOM_IN_RATIO to s.autoZoomInDistRatio.toString(),
        "wpButtonsMax" to s.wpButtonsMax.toString(),
        "hudWpCount" to s.hudWpCount.toString(),
        "uiTheme" to s.uiTheme.name,
        "mapTheme" to s.mapTheme.name,
        "ownshipPosition" to s.ownshipPosition.name,
        "profileSize" to s.profileSize.name,
        "panReturnSec" to s.panReturnSec.toString(),
        "autoOpenLastList" to s.autoOpenLastList.toString(),
        "keepScreenOn" to s.keepScreenOn.toString(),
        "buttonOpacityPct" to s.buttonOpacityPct.toString(),
        "numbersOpacityPct" to s.numbersOpacityPct.toString(),
        "trackColor" to s.trackColor.name,
        "trackBrightnessPct" to s.trackBrightnessPct.toString(),
        "ringLabelScalePct" to s.ringLabelScalePct.toString(),
    ) + s.customReach.withIndex().flatMap { (i, p) -> encodeProfile("custom${i + 1}.", p).toList() }.toMap()

    private fun encodeProfile(prefix: String, p: ReachProfile): Map<String, String> = mapOf(
        "${prefix}reachRadiusM" to p.reachRadiusM.toString(),
        "${prefix}sidePass" to p.sidePass.toString(),
        "${prefix}sidePassMaxM" to p.sidePassMaxM.toString(),
        "${prefix}sidePassDepartM" to p.sidePassDepartM.toString(),
        "${prefix}passDetection" to p.passDetection.toString(),
        "${prefix}passMaxApproachM" to p.passMaxApproachM.toString(),
        "${prefix}passDepartM" to p.passDepartM.toString(),
        "${prefix}passHoldSec" to p.passHoldSec.toString(),
    )

    /** 設定の項目のキー（今の版と、前の版の colorTheme）。移行の判定に使う */
    private val SETTINGS_KEYS: Set<String> by lazy { encode(NavSettings()).keys + "colorTheme" + OLD_KEY_ZOOM_IN_RATIO }

    /** AUTO で詳細へ切り替える距離の倍率（今の段の R1 が基準。D10 から） */
    private const val KEY_ZOOM_IN_RATIO = "autoZoomInR1Ratio"

    /** 前の版の倍率（1つ詳細側の段の R1 が基準）。意味が違うので読まない（初期値にする） */
    private const val OLD_KEY_ZOOM_IN_RATIO = "autoZoomInDistRatio"

    fun decode(m: Map<String, String?>): NavSettings {
        val d = NavSettings()
        fun str(k: String) = m[k]?.trim()
        fun f(k: String, def: Float) = str(k)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: def
        fun dbl(k: String, def: Double) = str(k)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: def
        fun int(k: String, def: Int) = str(k)?.toIntOrNull() ?: def
        fun bool(k: String, def: Boolean) = str(k)?.toBooleanStrictOrNull() ?: def
        fun <E : Enum<E>> enum(k: String, values: Array<E>, def: E) = values.firstOrNull { it.name == str(k) } ?: def

        val oldTheme = ColorTheme.entries.firstOrNull { it.name == str("colorTheme") }

        val steps = str("rangeStepsKm")
            ?.split(',')
            ?.mapNotNull { it.trim().toDoubleOrNull() }
            ?.filter { it in RangeAuto.ALL_STEPS_KM }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: d.rangeStepsKm

        // 移動手段: 保存がなければ既定（自動車）。前の版の「CUSTOM」と、移動手段の項目がない設定（D06 より前）は
        // カスタム1 として読み、保存してあった到達判定の値をカスタム1 に移す（カスタム2・3 は自動車の値で始める）。
        // 同じ保存先にある設定以外の項目（INPUT など）だけなら、前の版の設定ではない
        val savedMode = str("travelMode")
        val travelMode = when {
            savedMode == "CUSTOM" -> TravelMode.CUSTOM1
            savedMode != null -> TravelMode.entries.firstOrNull { it.name == savedMode } ?: d.travelMode
            m.keys.none { it in SETTINGS_KEYS } -> d.travelMode
            else -> TravelMode.CUSTOM1
        }
        // 到達判定の値（キーの頭 pre）。範囲外の値は base の値にする
        fun reach(pre: String, base: ReachProfile) = ReachProfile(
            reachRadiusM = dbl("${pre}reachRadiusM", base.reachRadiusM).takeIf { it in NavSettings.REACH_RADIUS_CHOICES_M } ?: base.reachRadiusM,
            sidePass = bool("${pre}sidePass", base.sidePass),
            sidePassMaxM = dbl("${pre}sidePassMaxM", base.sidePassMaxM).inOr(NavSettings.SIDE_PASS_MAX_M_RANGE, base.sidePassMaxM),
            sidePassDepartM = dbl("${pre}sidePassDepartM", base.sidePassDepartM).inOr(NavSettings.SIDE_PASS_DEPART_M_RANGE, base.sidePassDepartM),
            passDetection = bool("${pre}passDetection", base.passDetection),
            passMaxApproachM = dbl("${pre}passMaxApproachM", base.passMaxApproachM).inOr(NavSettings.PASS_MAX_APPROACH_M_RANGE, base.passMaxApproachM),
            passDepartM = dbl("${pre}passDepartM", base.passDepartM).inOr(NavSettings.PASS_DEPART_M_RANGE, base.passDepartM),
            passHoldSec = int("${pre}passHoldSec", base.passHoldSec).inOr(NavSettings.PASS_HOLD_SEC_RANGE, base.passHoldSec),
        )
        val flat = reach("", ReachProfile.CAR)
        fun profile(n: Int): ReachProfile {
            // 枠の保存がない（前の版）: カスタム1 には保存してあった値（移行）、カスタム2・3 は自動車の値
            val base = if (n == 1 && travelMode == TravelMode.CUSTOM1 && m.keys.none { it.startsWith("custom") }) flat else ReachProfile.CAR
            return reach("custom$n.", base)
        }
        val customReach = (1..3).map(::profile)

        // 保持の速度: 保存は m/s。一番近い km/h の整数（1〜36）に合わせる（前の版の 2.0 / 3.0 m/s は 7 / 11 km/h）。
        // 入る速度は 1〜35、解く速度は 入る速度 + 1〜36（解く速度は入る速度より大きいこと）
        val kmh = NavSettings.HOLD_SPEED_KMH_RANGE
        val enterKmh = NavSettings.holdSpeedKmh(f("holdEnterSpeedMps", d.holdEnterSpeedMps)).coerceAtMost(kmh.last - 1)
        val exitKmh = NavSettings.holdSpeedKmh(f("holdExitSpeedMps", d.holdExitSpeedMps)).coerceIn(enterKmh + 1, kmh.last)
        val holdEnter = NavSettings.kmhToMps(enterKmh)
        val holdExit = NavSettings.kmhToMps(exitKmh)

        return NavSettings(
            sourceMode = enum("sourceMode", SourceMode.entries.toTypedArray(), d.sourceMode),
            holdEnterSpeedMps = holdEnter,
            holdExitSpeedMps = holdExit,
            // 選べる値にない数は、一番近い値に読み替える（数でなければ既定値）
            maxGpsAccM = NavSettings.nearestFloat(NavSettings.MAX_GPS_ACC_CHOICES_M, f("maxGpsAccM", d.maxGpsAccM)),
            maxGpsBearingAccDeg = f("maxGpsBearingAccDeg", d.maxGpsBearingAccDeg).inOr(NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE, d.maxGpsBearingAccDeg),
            travelMode = travelMode,
            customReach = customReach,
            reachRadiusM = flat.reachRadiusM,
            sidePass = flat.sidePass,
            sidePassMaxM = flat.sidePassMaxM,
            sidePassDepartM = flat.sidePassDepartM,
            passDetection = flat.passDetection,
            passMaxApproachM = flat.passMaxApproachM,
            passDepartM = flat.passDepartM,
            passHoldSec = flat.passHoldSec,
            rateWindowSec = int("rateWindowSec", d.rateWindowSec).takeIf { it in NavSettings.RATE_WINDOW_CHOICES_SEC } ?: d.rateWindowSec,
            noFixTimeoutSec = NavSettings.nearestInt(NavSettings.NO_FIX_TIMEOUT_CHOICES_SEC, int("noFixTimeoutSec", d.noFixTimeoutSec)),
            altOffsetM = dbl("altOffsetM", d.altOffsetM).inOr(NavSettings.ALT_OFFSET_M_RANGE, d.altOffsetM),
            displayMode = enum("displayMode", DisplayMode.entries.toTypedArray(), d.displayMode),
            rangeStepsKm = steps,
            // 起動時の縮尺: 段の一覧にない値は既定値（使う段にない段は、設定画面ではいちばん近い段に見える）
            initialRangeKm = dbl("initialRangeKm", d.initialRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: d.initialRangeKm,
            autoRange = bool("autoRange", d.autoRange),
            autoRangeZoomInDelaySec = int("autoRangeZoomInDelaySec", d.autoRangeZoomInDelaySec)
                .inOr(NavSettings.AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE, d.autoRangeZoomInDelaySec),
            // AUTO の詳細の限度・広域の限度: 段の一覧にない値は既定値（使う段への寄せは RangeAuto.limitsKm）
            autoMinRangeKm = dbl("autoMinRangeKm", d.autoMinRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: d.autoMinRangeKm,
            autoMaxRangeKm = dbl("autoMaxRangeKm", d.autoMaxRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: d.autoMaxRangeKm,
            autoHoldAfterWpSec = NavSettings.nearestInt(NavSettings.AUTO_HOLD_AFTER_WP_CHOICES_SEC, int("autoHoldAfterWpSec", d.autoHoldAfterWpSec)),
            // 倍率: 1.0〜2.0 の 0.1 刻み。上限を超えていた値（前の版の 3.0 まで）は 2.0 として読む
            autoZoomInDistRatio = NavSettings.nearestZoomInDistRatio(dbl(KEY_ZOOM_IN_RATIO, d.autoZoomInDistRatio)),
            wpButtonsMax = int("wpButtonsMax", d.wpButtonsMax).coerceIn(NavSettings.WP_BUTTONS_MAX_RANGE),
            hudWpCount = int("hudWpCount", d.hudWpCount).coerceIn(NavSettings.HUD_WP_COUNT_RANGE),
            // 色: 古い版の「色テーマ」（1つ）が保存されていれば、UI・地図の両方の初期値として引き継ぐ
            uiTheme = enum("uiTheme", ColorTheme.entries.toTypedArray(), oldTheme ?: d.uiTheme),
            mapTheme = enum("mapTheme", ColorTheme.entries.toTypedArray(), oldTheme ?: d.mapTheme),
            ownshipPosition = enum("ownshipPosition", OwnshipPosition.entries.toTypedArray(), d.ownshipPosition),
            profileSize = enum("profileSize", ProfileSize.entries.toTypedArray(), d.profileSize),
            panReturnSec = int("panReturnSec", d.panReturnSec).coerceIn(NavSettings.PAN_RETURN_SEC_RANGE),
            autoOpenLastList = bool("autoOpenLastList", d.autoOpenLastList),
            keepScreenOn = bool("keepScreenOn", d.keepScreenOn),
            // 不透明度: 20〜100 の 10 刻み以外は既定値
            buttonOpacityPct = int("buttonOpacityPct", d.buttonOpacityPct).takeIf { it in NavSettings.OPACITY_CHOICES_PCT } ?: d.buttonOpacityPct,
            numbersOpacityPct = int("numbersOpacityPct", d.numbersOpacityPct).takeIf { it in NavSettings.OPACITY_CHOICES_PCT } ?: d.numbersOpacityPct,
            // 読み込んだ軌跡の色: 保存がない（前の版）・読めない名前なら既定の白（前の版と同じ見た目）
            trackColor = enum("trackColor", TrackColor.entries.toTypedArray(), d.trackColor),
            // 読み込んだ軌跡の明るさ: 25 / 50 / 75 / 100 以外は既定値
            trackBrightnessPct = int("trackBrightnessPct", d.trackBrightnessPct).takeIf { it in NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT }
                ?: d.trackBrightnessPct,
            // 距離環の数字の大きさ: 選べる値（100〜200 の 25 刻み）にない数は一番近い段に読み替える（前の版の 250 は 200）。
            // 数でなければ既定値
            ringLabelScalePct = str("ringLabelScalePct")?.toIntOrNull()?.let(NavSettings::nearestRingLabelScalePct) ?: d.ringLabelScalePct,
        ).selectTravelMode(travelMode)
    }

    /** 範囲内ならその値、範囲外なら def */
    private fun <T : Comparable<T>> T.inOr(range: ClosedRange<T>, def: T): T = if (this in range) this else def
}
