package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.SourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun roundTripOfEveryValue() {
        // 既定値とすべて違う値にする
        val p1 = ReachProfile(50.0, false, 100.0, 15.0, false, 250.0, 40.0, 4)
        val p2 = ReachProfile(200.0, false, 200.0, 20.0, false, 450.0, 80.0, 8)
        val p3 = ReachProfile(100.0, false, 300.0, 30.0, false, 500.0, 60.0, 10)
        val s = NavSettings(
            sourceMode = SourceMode.HYBRID,
            holdEnterSpeedMps = 1.5f,
            holdExitSpeedMps = 4.2f,
            maxGpsAccM = 20f,
            maxGpsBearingAccDeg = 30f,
            rateWindowSec = 30,
            noFixTimeoutSec = 15,
            altOffsetM = 35.5,
            displayMode = DisplayMode.NORTH_UP,
            rangeStepsKm = listOf(0.05, 2.0, 50.0),
            initialRangeKm = 2.0,
            autoRange = false,
            autoRangeZoomInDelaySec = 8,
            wpButtonsMax = 6,
            hudWpCount = 4,
            uiTheme = ColorTheme.AMBER,
            mapTheme = ColorTheme.WHITE,
            ownshipPosition = OwnshipPosition.HIGH,
            profileSize = ProfileSize.LARGE,
            panReturnSec = 30,
            autoOpenLastList = true,
            keepScreenOn = false,
            buttonOpacityPct = 40,
            numbersOpacityPct = 60,
            autoMinRangeKm = 0.2,
            autoMaxRangeKm = 5.0,
            autoHoldAfterWpSec = 20,
            autoZoomInDistRatio = 3.0,
            // 移動手段: カスタム2 を選んでいて、3つの枠はどれも自動車の値と違う
            travelMode = TravelMode.CUSTOM2,
            customReach = listOf(p1, p2, p3),
        ).withReachValues(p2)
        val d = NavSettings()
        // 念のため、ほんとうに全項目が既定値と違うことを確かめる（項目を足したらここも足す）
        assertEquals(SettingsCodec.encode(d).keys, SettingsCodec.encode(s).keys)
        SettingsCodec.encode(d).forEach { (k, v) -> assert(SettingsCodec.encode(s)[k] != v) { "既定値と同じ: $k" } }

        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun autoLimitsDefaultsAndInvalidValues() {
        // 既定: 下限 100m の段（R1 50m）、上限 1km の段（R1 500m）
        assertEquals(0.1, NavSettings().autoMinRangeKm, 0.0)
        assertEquals(1.0, NavSettings().autoMaxRangeKm, 0.0)
        // 段の一覧にない値・読めない値は既定値
        val s = SettingsCodec.decode(mapOf("autoMinRangeKm" to "0.3", "autoMaxRangeKm" to "x"))
        assertEquals(0.1, s.autoMinRangeKm, 0.0)
        assertEquals(1.0, s.autoMaxRangeKm, 0.0)
        assertEquals(0.05, SettingsCodec.decode(mapOf("autoMinRangeKm" to "0.05")).autoMinRangeKm, 0.0)
        // WP 通過後の待機（既定 10 秒、0〜60）と、狭め始める距離（既定 2.0、1.0〜4.0 の 0.5 刻み）
        assertEquals(10, NavSettings().autoHoldAfterWpSec)
        assertEquals(2.0, NavSettings().autoZoomInDistRatio, 0.0)
        assertEquals(0, SettingsCodec.decode(mapOf("autoHoldAfterWpSec" to "0")).autoHoldAfterWpSec)
        assertEquals(10, SettingsCodec.decode(mapOf("autoHoldAfterWpSec" to "61")).autoHoldAfterWpSec)
        assertEquals(1.5, SettingsCodec.decode(mapOf("autoZoomInDistRatio" to "1.5")).autoZoomInDistRatio, 0.0)
        assertEquals(2.0, SettingsCodec.decode(mapOf("autoZoomInDistRatio" to "1.2")).autoZoomInDistRatio, 0.0)
        assertEquals(2.0, SettingsCodec.decode(mapOf("autoZoomInDistRatio" to "4.5")).autoZoomInDistRatio, 0.0)
        assertEquals(listOf(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0), NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES)
    }

    @Test
    fun opacityDefaultsAndInvalidValues() {
        // 既定: ボタン 70%、数値 100%（Tuning.kt）
        val d = NavSettings()
        assertEquals(70, d.buttonOpacityPct)
        assertEquals(100, d.numbersOpacityPct)
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80, 90, 100), NavSettings.OPACITY_CHOICES_PCT)
        // 20〜100 の 10 刻みなら読む
        val ok = SettingsCodec.decode(mapOf("buttonOpacityPct" to "20", "numbersOpacityPct" to "50"))
        assertEquals(20, ok.buttonOpacityPct)
        assertEquals(50, ok.numbersOpacityPct)
        // 範囲外・刻みに合わない・読めない値は、その項目だけ既定値
        for (bad in listOf("10", "110", "35", "x", "")) {
            val s = SettingsCodec.decode(mapOf("buttonOpacityPct" to bad, "numbersOpacityPct" to bad, "rateWindowSec" to "30"))
            assertEquals(70, s.buttonOpacityPct)
            assertEquals(100, s.numbersOpacityPct)
            assertEquals(30, s.rateWindowSec)
        }
    }

    @Test
    fun missingValuesAreDefaults() {
        assertEquals(NavSettings(), SettingsCodec.decode(emptyMap()))
        // D06 より前に保存した設定（移動手段の項目がない）はカスタム1 として読む（値は保存してあったもの = ここでは既定値）
        assertEquals(NavSettings(uiTheme = ColorTheme.GREEN, travelMode = TravelMode.CUSTOM1), SettingsCodec.decode(mapOf("uiTheme" to "GREEN")))
    }

    @Test
    fun oldColorThemeIsCarriedToBothColors() {
        // 古い版の「色テーマ」（1つ）は、UI・地図の両方の初期値として引き継ぐ
        val old = SettingsCodec.decode(mapOf("colorTheme" to "AMBER"))
        assertEquals(ColorTheme.AMBER, old.uiTheme)
        assertEquals(ColorTheme.AMBER, old.mapTheme)
        // 新しい項目が保存されていれば、そちらを使う
        val both = SettingsCodec.decode(mapOf("colorTheme" to "AMBER", "uiTheme" to "WHITE", "mapTheme" to "GREEN"))
        assertEquals(ColorTheme.WHITE, both.uiTheme)
        assertEquals(ColorTheme.GREEN, both.mapTheme)
        // 既定は UI 白・地図 緑。HUD に描く WP の数は 15（1〜20）
        assertEquals(ColorTheme.WHITE, NavSettings().uiTheme)
        assertEquals(ColorTheme.GREEN, NavSettings().mapTheme)
        assertEquals(15, NavSettings().hudWpCount)
        assertEquals(20, SettingsCodec.decode(mapOf("hudWpCount" to "99")).hudWpCount)
        assertEquals(18, SettingsCodec.decode(mapOf("hudWpCount" to "18")).hudWpCount)
    }

    @Test
    fun brokenValuesAreDefaults() {
        val s = SettingsCodec.decode(
            mapOf(
                "sourceMode" to "RADAR",
                "holdEnterSpeedMps" to "abc",
                "rateWindowSec" to "45",
                "rangeStepsKm" to "3,7,x",
                "wpButtonsMax" to "99",
                "hudWpCount" to "0",
                "keepScreenOn" to "yes",
            ),
        )
        val d = NavSettings()
        assertEquals(d.sourceMode, s.sourceMode)
        assertEquals(d.holdEnterSpeedMps, s.holdEnterSpeedMps)
        assertEquals(d.rateWindowSec, s.rateWindowSec)
        assertEquals(d.rangeStepsKm, s.rangeStepsKm)
        assertEquals(6, s.wpButtonsMax)
        assertEquals(1, s.hudWpCount)
        assertEquals(d.keepScreenOn, s.keepScreenOn)
    }

    @Test
    fun rangeStepsAreFilteredAndSorted() {
        val s = SettingsCodec.decode(mapOf("rangeStepsKm" to "10, 0.5,3 ,0.5,1"))
        assertEquals(listOf(0.5, 1.0, 10.0), s.rangeStepsKm)
        // 7b までの段（0.25km）は、今の段にないので捨てる
        assertEquals(listOf(0.5, 2.0), SettingsCodec.decode(mapOf("rangeStepsKm" to "0.25,0.5,2")).rangeStepsKm)
    }
}
