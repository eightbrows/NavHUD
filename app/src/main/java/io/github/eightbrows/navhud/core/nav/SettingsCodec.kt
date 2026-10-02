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
        "reachRadiusM" to s.reachRadiusM.toString(),
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
        "wpButtonsMax" to s.wpButtonsMax.toString(),
        "hudWpCount" to s.hudWpCount.toString(),
        "uiTheme" to s.uiTheme.name,
        "mapTheme" to s.mapTheme.name,
        "ownshipPosition" to s.ownshipPosition.name,
        "profileSize" to s.profileSize.name,
        "panReturnSec" to s.panReturnSec.toString(),
        "autoOpenLastList" to s.autoOpenLastList.toString(),
        "keepScreenOn" to s.keepScreenOn.toString(),
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

        return NavSettings(
            sourceMode = enum("sourceMode", SourceMode.entries.toTypedArray(), d.sourceMode),
            holdEnterSpeedMps = f("holdEnterSpeedMps", d.holdEnterSpeedMps),
            holdExitSpeedMps = f("holdExitSpeedMps", d.holdExitSpeedMps),
            maxGpsAccM = f("maxGpsAccM", d.maxGpsAccM),
            maxGpsBearingAccDeg = f("maxGpsBearingAccDeg", d.maxGpsBearingAccDeg),
            reachRadiusM = dbl("reachRadiusM", d.reachRadiusM),
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
        )
    }
}
