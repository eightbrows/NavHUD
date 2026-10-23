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
        // 3 段（20261022-D01）: 高 #FFA8C6、中はその RGB × 0.7（#B3768B）、低は × 0.5（#805463）。どれも塗りつぶし（不透明）
        val (low, mid, high) = tiers
        assertEquals(Color(0xFFFFA8C6), high)
        for (ch in listOf<(Color) -> Float>({ it.red }, { it.green }, { it.blue })) {
            assertEquals(0.7f, ch(mid) / ch(high), 0.01f)
            assertEquals(0.5f, ch(low) / ch(high), 0.01f)
        }
        assertTrue(tiers.all { it.alpha == 1f })
    }

    @Test
    fun landmarkBrightnessKeepsTheOrderOfTheTiers() {
        // ランドマークの明るさの段（20261022-D01）: 段 4（1.0）は今の色。どの段でも、山頂の高 > 中 > 低の明るさの順と、3 段の差が残る
        // （明るくする方は白に近づけるので、高が 255 で止まって中と同じになることがない）
        val inks = listOf(Ink.MAP_PEAK_LOW, Ink.MAP_PEAK_MID, Ink.MAP_PEAK_HIGH)
        assertEquals(inks.map { HudColors.ofMap(it) }, inks.map { HudColors.landmark(it, 1f) })
        for (k in io.github.eightbrows.navhud.core.Tuning.BASEMAP_BRIGHTNESS_FACTORS) {
            val c = inks.map { HudColors.landmark(it, k) }
            assertTrue("段 $k", c.zipWithNext().all { (a, b) -> b.luminance() > a.luminance() * 1.2f })
        }
        // 暗くする方は RGB に掛ける、明るくする方は 1 −（1 − 色）÷ 掛ける数
        assertEquals(HudColors.MapPeakHigh.green * 0.4f, HudColors.landmark(Ink.MAP_PEAK_HIGH, 0.4f).green, 0.01f)
        assertEquals(1f - (1f - HudColors.MapPeakHigh.green) / 1.6f, HudColors.landmark(Ink.MAP_PEAK_HIGH, 1.6f).green, 0.01f)
        // 空港の敷地は半透明（30%。20261023-D01 で 20% から濃くした）のまま、明るさの段で不透明度は変わらない
        assertEquals(0.3f, HudColors.landmark(Ink.MAP_AIRPORT_AREA, 1.6f).alpha, 0.01f)
        assertEquals(0.3f, HudColors.landmark(Ink.MAP_AIRPORT_AREA, 0.4f).alpha, 0.01f)
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
