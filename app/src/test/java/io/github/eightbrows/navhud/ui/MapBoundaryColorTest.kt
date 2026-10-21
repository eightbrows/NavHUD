package io.github.eightbrows.navhud.ui

import androidx.compose.ui.graphics.luminance
import io.github.eightbrows.navhud.core.Tuning
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBoundaryColorTest {
    @Test
    fun municipalBoundaryIsVisibleAndDiffersFromPrefectureBoundary() {
        // 市区町村境は細かい点線なので、線より明るい灰色（20261020-D02。前の #383838 は黒の背景とほとんど同じで見えなかった）
        val muni = HudColors.MuniBoundary
        val pref = HudColors.PrefBoundary
        assertTrue(muni.red == muni.green && muni.green == muni.blue)
        assertTrue(muni.luminance() > HudColors.BearingLine.luminance() * 2)
        // 県境は市区町村境より明るい灰色（20261021-D01。太さと合わせて、県境のほうが目立つ）
        assertTrue(pref.red == pref.green && pref.green == pref.blue)
        assertTrue(pref.luminance() > muni.luminance())
        // 県境（太めの点線）より細く、点は細かい（描く 1dp・空ける 2dp。県境は 2dp・4dp）
        // 県境は市区町村境の 2 倍以上の太さ（20261020-D03。境の重さの順が分かるように）
        assertTrue(Tuning.BASEMAP_PREF_LINE_DP >= 2 * Tuning.BASEMAP_MUNI_LINE_DP)
        assertTrue(Tuning.BASEMAP_FINE_DOT_ON_DP < Tuning.BASEMAP_PREF_DASH_ON_DP && Tuning.BASEMAP_FINE_DOT_OFF_DP < Tuning.BASEMAP_PREF_DASH_OFF_DP)
        assertTrue(Tuning.BASEMAP_FINE_DOT_OFF_DP <= 2 * Tuning.BASEMAP_FINE_DOT_ON_DP)
    }
}
