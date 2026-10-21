package io.github.eightbrows.navhud.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 設定画面の選ぶボタン（20261021-D01）: 初期値はテーマの色で塗り、選んでいる値は水色の太い枠と点。
 * 水色の印が、どのテーマの塗り・ふつうの枠・背景とも見分けられること
 */
class SettingsSelectedMarkColorTest {
    /** RGB（0〜255）の距離。目で見分けられるかの大まかな目安 */
    private fun dist(a: Color, b: Color): Float {
        val dr = (a.red - b.red) * 255; val dg = (a.green - b.green) * 255; val db = (a.blue - b.blue) * 255
        return sqrt(dr * dr + dg * dg + db * db)
    }

    @Test
    fun selectedMarkDiffersFromEveryThemeFillAndTheNormalFrame() {
        val mark = HudColors.SettingsSelected
        for (p in listOf(HudPalette.WHITE, HudPalette.GREEN, HudPalette.AMBER)) assertTrue(dist(mark, p.scale) > 100f)
        assertTrue(dist(mark, HudColors.WpDisabled) > 100f)
        assertTrue(dist(mark, HudColors.Background) > 200f)
        // 青みの色（青が赤より強い）
        assertTrue(mark.blue > mark.red)
    }

    @Test
    fun disabledSelectedMarkIsADarkerBlue() {
        // 押せないボタンの印: 同じ水色の系統で暗い（押せるボタンの印より暗く、背景の黒・押せないボタンのグレーとは見分けられる）
        val dim = HudColors.SettingsSelectedDim
        val mark = HudColors.SettingsSelected
        assertTrue(dim.luminance() < mark.luminance() / 2)
        assertTrue(dim.blue > dim.red && dim.blue > dim.green)
        assertTrue(dist(dim, HudColors.Background) > 100f)
        assertTrue(dist(dim, HudColors.WpReached) > 60f)
    }
}
