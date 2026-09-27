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
            rangeStepsKm = listOf(0.25, 2.0, 20.0),
            initialRangeKm = 2.0,
            autoRange = false,
            autoRangeZoomInDelaySec = 8,
            wpButtonsSide = ScreenSide.LEFT,
            wpButtonsMax = 7,
            hudWpCount = 4,
            colorTheme = ColorTheme.AMBER,
            ownshipPosition = OwnshipPosition.HIGH,
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
        assertEquals(NavSettings(colorTheme = ColorTheme.GREEN), SettingsCodec.decode(mapOf("colorTheme" to "GREEN")))
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
        assertEquals(10, s.wpButtonsMax)
        assertEquals(1, s.hudWpCount)
        assertEquals(d.keepScreenOn, s.keepScreenOn)
    }

    @Test
    fun rangeStepsAreFilteredAndSorted() {
        val s = SettingsCodec.decode(mapOf("rangeStepsKm" to "10, 0.5,3 ,0.5,1"))
        assertEquals(listOf(0.5, 1.0, 10.0), s.rangeStepsKm)
    }
}
