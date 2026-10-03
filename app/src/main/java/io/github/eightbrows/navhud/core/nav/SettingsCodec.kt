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
        "autoZoomInDistRatio" to s.autoZoomInDistRatio.toString(),
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
        // カスタム1 として読み、保存してあった到達判定の値をカスタム1 に移す（カスタム2・3 は自動車の値で始める）
        val savedMode = str("travelMode")
        val travelMode = when {
            savedMode == "CUSTOM" -> TravelMode.CUSTOM1
            savedMode != null -> TravelMode.entries.firstOrNull { it.name == savedMode } ?: d.travelMode
            m.isEmpty() -> d.travelMode
            else -> TravelMode.CUSTOM1
        }
        val flat = ReachProfile(
            reachRadiusM = dbl("reachRadiusM", d.reachRadiusM),
            sidePass = bool("sidePass", d.sidePass),
            sidePassMaxM = dbl("sidePassMaxM", d.sidePassMaxM),
            sidePassDepartM = dbl("sidePassDepartM", d.sidePassDepartM),
            passDetection = bool("passDetection", d.passDetection),
            passMaxApproachM = dbl("passMaxApproachM", d.passMaxApproachM),
            passDepartM = dbl("passDepartM", d.passDepartM),
            passHoldSec = int("passHoldSec", d.passHoldSec),
        )
        fun profile(n: Int): ReachProfile {
            val pre = "custom$n."
            // 枠の保存がない（前の版）: カスタム1 には保存してあった値（移行）、カスタム2・3 は自動車の値
            val base = if (n == 1 && travelMode == TravelMode.CUSTOM1 && m.keys.none { it.startsWith("custom") }) flat else ReachProfile.CAR
            return ReachProfile(
                reachRadiusM = dbl("${pre}reachRadiusM", base.reachRadiusM),
                sidePass = bool("${pre}sidePass", base.sidePass),
                sidePassMaxM = dbl("${pre}sidePassMaxM", base.sidePassMaxM),
                sidePassDepartM = dbl("${pre}sidePassDepartM", base.sidePassDepartM),
                passDetection = bool("${pre}passDetection", base.passDetection),
                passMaxApproachM = dbl("${pre}passMaxApproachM", base.passMaxApproachM),
                passDepartM = dbl("${pre}passDepartM", base.passDepartM),
                passHoldSec = int("${pre}passHoldSec", base.passHoldSec),
            )
        }
        val customReach = (1..3).map(::profile)

        return NavSettings(
            sourceMode = enum("sourceMode", SourceMode.entries.toTypedArray(), d.sourceMode),
            holdEnterSpeedMps = f("holdEnterSpeedMps", d.holdEnterSpeedMps),
            holdExitSpeedMps = f("holdExitSpeedMps", d.holdExitSpeedMps),
            maxGpsAccM = f("maxGpsAccM", d.maxGpsAccM),
            maxGpsBearingAccDeg = f("maxGpsBearingAccDeg", d.maxGpsBearingAccDeg),
            travelMode = travelMode,
            customReach = customReach,
            reachRadiusM = dbl("reachRadiusM", d.reachRadiusM),
            sidePass = bool("sidePass", d.sidePass),
            sidePassMaxM = dbl("sidePassMaxM", d.sidePassMaxM),
            sidePassDepartM = dbl("sidePassDepartM", d.sidePassDepartM),
            passDetection = bool("passDetection", d.passDetection),
            passMaxApproachM = dbl("passMaxApproachM", d.passMaxApproachM),
            passDepartM = dbl("passDepartM", d.passDepartM),
            passHoldSec = int("passHoldSec", d.passHoldSec),
            rateWindowSec = int("rateWindowSec", d.rateWindowSec).takeIf { it in NavSettings.RATE_WINDOW_CHOICES_SEC } ?: d.rateWindowSec,
            noFixTimeoutSec = int("noFixTimeoutSec", d.noFixTimeoutSec),
            altOffsetM = dbl("altOffsetM", d.altOffsetM),
            displayMode = enum("displayMode", DisplayMode.entries.toTypedArray(), d.displayMode),
            rangeStepsKm = steps,
            initialRangeKm = dbl("initialRangeKm", d.initialRangeKm),
            autoRange = bool("autoRange", d.autoRange),
            autoRangeZoomInDelaySec = int("autoRangeZoomInDelaySec", d.autoRangeZoomInDelaySec),
            // AUTO の下限・上限: 段の一覧にない値は既定値（使う段への寄せは RangeAuto.limitsKm）
            autoMinRangeKm = dbl("autoMinRangeKm", d.autoMinRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: d.autoMinRangeKm,
            autoMaxRangeKm = dbl("autoMaxRangeKm", d.autoMaxRangeKm).takeIf { it in RangeAuto.ALL_STEPS_KM } ?: d.autoMaxRangeKm,
            autoHoldAfterWpSec = int("autoHoldAfterWpSec", d.autoHoldAfterWpSec).takeIf { it in NavSettings.AUTO_HOLD_AFTER_WP_SEC_RANGE }
                ?: d.autoHoldAfterWpSec,
            autoZoomInDistRatio = dbl("autoZoomInDistRatio", d.autoZoomInDistRatio)
                .takeIf { v -> NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES.any { kotlin.math.abs(it - v) < 1e-9 } } ?: d.autoZoomInDistRatio,
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
        ).selectTravelMode(travelMode)
    }
}
