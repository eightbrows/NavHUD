package io.github.eightbrows.navhud.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SettingsDefaultMarkColorTest {
    /** RGB（0〜255）の距離。目で見分けられるかの大まかな目安 */
    private fun dist(a: Color, b: Color): Float {
        val dr = (a.red - b.red) * 255; val dg = (a.green - b.green) * 255; val db = (a.blue - b.blue) * 255
        return sqrt(dr * dr + dg * dg + db * db)
    }

    @Test
    fun defaultMarkDiffersFromEveryThemeFillAndTheNormalFrame() {
        // 初期値の印（水色の枠と点）は、白・緑・琥珀のどのテーマでも、選んだボタンの塗り・ふつうの枠・背景と見分けられる
        val mark = HudColors.SettingsDefault
        for (p in listOf(HudPalette.WHITE, HudPalette.GREEN, HudPalette.AMBER)) assertTrue(dist(mark, p.scale) > 100f)
        assertTrue(dist(mark, HudColors.WpDisabled) > 100f)
        assertTrue(dist(mark, HudColors.Background) > 200f)
        // 青みの色（青が赤より強い）
        assertTrue(mark.blue > mark.red)
    }
}
