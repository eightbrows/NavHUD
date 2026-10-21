package io.github.eightbrows.navhud.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.eightbrows.navhud.core.view.Ink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** 山頂の色（20261021-D02）: 淡い桃色。目印になる山は標高の段ごとに明るさを変え、目印になりにくい山は半透明の丸 */
class MapPeakColorTest {
    /** RGB（0〜255）の距離。目で見分けられるかの大まかな目安 */
    private fun dist(a: Color, b: Color): Float {
        val dr = (a.red - b.red) * 255; val dg = (a.green - b.green) * 255; val db = (a.blue - b.blue) * 255
        return sqrt(dr * dr + dg * dg + db * db)
    }

    @Test
    fun peakTiersGetBrighterWithElevationAndKeepTheHue() {
        val tiers = listOf(Ink.MAP_PEAK_LOW, Ink.MAP_PEAK_MID, Ink.MAP_PEAK_HIGH).map { HudColors.ofMap(it) }
        // 高い段ほど明るい。どの段も同じ色の系統（RGB の比がほぼ同じ）
        assertTrue(tiers.zipWithNext().all { (a, b) -> a.luminance() < b.luminance() })
        for (c in tiers) {
            assertEquals(HudColors.MapPeakHigh.green / HudColors.MapPeakHigh.red, c.green / c.red, 0.02f)
            assertEquals(HudColors.MapPeakHigh.blue / HudColors.MapPeakHigh.red, c.blue / c.red, 0.02f)
        }
        // 段の掛ける数は 1・0.9・0.8（20261021-D03。前は 1・0.85・0.7）
        val (low, mid, high) = tiers
        for (ch in listOf<(Color) -> Float>({ it.red }, { it.green }, { it.blue })) {
            assertEquals(0.9f, ch(mid) / ch(high), 0.01f)
            assertEquals(0.8f, ch(low) / ch(high), 0.01f)
        }
        // 目印になりにくい山の丸は、同じ桃色の半透明（20%。20261021-D03 で 30% から暗くした）
        val area = HudColors.ofMap(Ink.MAP_PEAK_AREA)
        assertEquals(0.2f, area.alpha, 0.01f)
        assertEquals(HudColors.MapPeakHigh.copy(alpha = area.alpha), area)
    }

    @Test
    fun peakColorDiffersFromHudAndMapColors() {
        // いちばん明るい段は、HUD の色・道路・鉄道・河川・空港の色と離れている（ΔE はレポート。ここは RGB の距離の目安）
        val others = listOf(
            HudPalette.GREEN.scale, HudPalette.WHITE.scale, HudPalette.AMBER.scale, HudColors.Active, HudColors.Warning, HudColors.Caution,
            HudColors.MapMotorway, HudColors.MapNationalRoad, HudColors.MapRoad, HudColors.MapRail, HudColors.MapShinkansen,
            HudColors.MapWater, HudColors.MapAirport, HudColors.PrefBoundary, HudColors.MuniBoundary,
        )
        for (o in others) assertTrue(o.toString(), dist(HudColors.MapPeakHigh, o) > 60f)
    }
}
