package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.SourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun roundTripOfEveryValue() {
        // 既定値とすべて違う値にする
        val s = NavSettings(
            sourceMode = SourceMode.HYBRID,
            holdEnterSpeedMps = 1.5f,
            holdExitSpeedMps = 4.2f,
            maxGpsAccM = 20f,
            maxGpsBearingAccDeg = 30f,
            reachRadiusM = 200.0,
            passDetection = false,
            passMaxApproachM = 450.0,
            passDepartM = 80.0,
            passHoldSec = 8,
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
        )
        val d = NavSettings()
        // 念のため、ほんとうに全項目が既定値と違うことを確かめる（項目を足したらここも足す）
        assertEquals(SettingsCodec.encode(d).keys, SettingsCodec.encode(s).keys)
        SettingsCodec.encode(d).forEach { (k, v) -> assert(SettingsCodec.encode(s)[k] != v) { "既定値と同じ: $k" } }

        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun missingValuesAreDefaults() {
        assertEquals(NavSettings(), SettingsCodec.decode(emptyMap()))
        assertEquals(NavSettings(uiTheme = ColorTheme.GREEN), SettingsCodec.decode(mapOf("uiTheme" to "GREEN")))
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
