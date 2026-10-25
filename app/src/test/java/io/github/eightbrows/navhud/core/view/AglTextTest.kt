package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.nav.AglNegative
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SettingsCodec
import io.github.eightbrows.navhud.core.view.HudFormat.AglText
import org.junit.Assert.assertEquals
import org.junit.Test

/** 地上高の負の値の出し方（設定「地上高の負の値」。20261025-D08）。 */
class AglTextTest {

    @Test
    fun zeroModeShowsZeroForNegativeValues() {
        // 0 にする（初期値）: 0 未満は「0 m」、色はふつう
        assertEquals(AglText("0 m", false), HudFormat.agl(-75.0, AglNegative.ZERO))
        assertEquals(AglText("0 m", false), HudFormat.agl(-0.6, AglNegative.ZERO))
        assertEquals(AglText("120 m", false), HudFormat.agl(120.4, AglNegative.ZERO))
    }

    @Test
    fun showModeKeepsNegativeValuesAsWarnings() {
        // 負のまま出す: 0 未満は負のまま、警告の色。0 以上はふつうの色
        assertEquals(AglText("-75 m", true), HudFormat.agl(-75.0, AglNegative.SHOW))
        assertEquals(AglText("-1 m", true), HudFormat.agl(-0.6, AglNegative.SHOW))
        assertEquals(AglText("120 m", false), HudFormat.agl(120.4, AglNegative.SHOW))
        assertEquals(AglText("-174 m", true), HudFormat.agl(-174.2, AglNegative.SHOW))
    }

    @Test
    fun zeroExactlyIsNormalInBothModes() {
        // 0 ちょうど・丸めて 0（-0.4 m）は、どちらも「0 m」でふつうの色（「-0 m」にしない）
        for (mode in AglNegative.entries) {
            assertEquals(AglText("0 m", false), HudFormat.agl(0.0, mode))
            assertEquals(AglText("0 m", false), HudFormat.agl(-0.4, mode))
            assertEquals(AglText("0 m", false), HudFormat.agl(0.4, mode))
        }
    }

    @Test
    fun noValueIsDashesInBothModes() {
        // NO FIX・範囲外・unknown（値なし）は、どちらの設定でも「---」
        for (mode in AglNegative.entries) {
            assertEquals(AglText("---", false), HudFormat.agl(null, mode))
            assertEquals(AglText("---", false), HudFormat.agl(Double.NaN, mode))
        }
    }

    @Test
    fun settingIsSavedAndDefaultsToZero() {
        // 初期値は「0 にする」。保存の名前は aglNegative（値は ZERO / SHOW）
        assertEquals(AglNegative.ZERO, NavSettings().aglNegative)
        assertEquals("ZERO", SettingsCodec.encode(NavSettings())["aglNegative"])
        val show = NavSettings(aglNegative = AglNegative.SHOW)
        assertEquals("SHOW", SettingsCodec.encode(show)["aglNegative"])
        assertEquals(AglNegative.SHOW, SettingsCodec.decode(SettingsCodec.encode(show)).aglNegative)
        // 前の保存がない端末（キーがない）・読めない名前は「0 にする」
        assertEquals(AglNegative.ZERO, SettingsCodec.decode(mapOf("altOffsetM" to "36.0")).aglNegative)
        assertEquals(AglNegative.ZERO, SettingsCodec.decode(mapOf("aglNegative" to "CLAMP")).aglNegative)
    }
}
