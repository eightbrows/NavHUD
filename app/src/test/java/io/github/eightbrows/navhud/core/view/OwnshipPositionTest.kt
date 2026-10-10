package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ARC の自機の位置（§6.2）: 下から5段（1 / 2 / 3 / 4 / 中央）。 */
class OwnshipPositionTest {

    private val m = HudMetrics()
    private val rect = HudRect(0f, 0f, 720f, 1300f)

    /** 回避枠: 上 200（数値の下端）〜下 1100（WP ボタン列の上端）。高さ 900、縦中央 650 */
    private val avoid = HudRect(0f, 200f, 720f, 1100f)

    private fun y(p: OwnshipPosition, frame: HudRect = avoid) = HudSceneBuilder.arcOriginY(p, frame, m)

    @Test
    fun fiveStepsFromTheBottom() {
        // 1〜3段目は前と同じ（回避枠の下端から 24 / 84 / 144dp）
        assertEquals(1100f - 24f, y(OwnshipPosition.STANDARD), 1e-3f)
        assertEquals(1100f - 84f, y(OwnshipPosition.HIGH), 1e-3f)
        assertEquals(1100f - 144f, y(OwnshipPosition.HIGHER), 1e-3f)
        // 5段目（中央）: 回避枠の縦中央 = North Up の自機と同じ高さ
        assertEquals(650f, y(OwnshipPosition.CENTER), 1e-3f)
        val northUp = HudSceneBuilder.projection(NavSettings(displayMode = DisplayMode.NORTH_UP), rect, avoid, 1_000.0, 0.0, m)
        assertEquals(northUp.origin.y, y(OwnshipPosition.CENTER), 1e-3f)
        // 4段目: 3段目（956）と 5段目（650）のちょうど中間
        assertEquals((956f + 650f) / 2, y(OwnshipPosition.NEAR_CENTER), 1e-3f)
        // 上の段ほど上（y が小さい）
        val ys = OwnshipPosition.entries.map { y(it) }
        assertEquals(ys.sortedDescending(), ys)
        assertEquals(5, ys.distinct().size)
    }

    @Test
    fun arcProjectionAndTheAutoFrameFollowThePosition() {
        // ARC の投影の原点（自機）は、どの段でも横は画面の中央、縦は arcOriginY。AUTO の判定（HudViewport.fits）も同じ投影を使う（下で確かめる）
        val vp = HudViewport(rect, m, HudInsets(top = 200f, bottom = 200f))
        for (p in OwnshipPosition.entries) {
            val s = NavSettings(displayMode = DisplayMode.ARC, ownshipPosition = p)
            val proj = HudSceneBuilder.projection(s, rect, avoid, 1_000.0, 0.0, m)
            assertEquals(360f, proj.origin.x, 1e-3f)
            assertEquals(y(p), proj.origin.y, 1e-3f)
        }
        // 1km の段（1 m = 0.36 px）で、真後ろ 700m（252 px 下）の WP: 1段目では画面の下に出て収まらず、中央では収まる
        val behind = EN(0.0, -700.0)
        fun fits(p: OwnshipPosition) = vp.fits(1_000.0, behind, 0.0, NavSettings(displayMode = DisplayMode.ARC, ownshipPosition = p))
        assertFalse(fits(OwnshipPosition.STANDARD))
        assertTrue(fits(OwnshipPosition.CENTER))
        // 前方 1.9km（684 px 上）: 1段目では収まるが、中央（数値の下端まで 450 px）では収まらない
        val ahead = EN(0.0, 1_900.0)
        fun fitsAhead(p: OwnshipPosition) = vp.fits(1_000.0, ahead, 0.0, NavSettings(displayMode = DisplayMode.ARC, ownshipPosition = p))
        assertTrue(fitsAhead(OwnshipPosition.STANDARD))
        assertFalse(fitsAhead(OwnshipPosition.CENTER))
    }

    @Test
    fun neverOverTheHeadingMarkerAndNeverBelowTheLowerSteps() {
        // 上部の方位の三角にかからない: 三角の下端 + 自機の記号の半高 より上にはしない（今までの決まり）
        fun limit(frame: HudRect) = HudSceneBuilder.arcMarkerTipY(frame.top, m) + m.pointerSize + m.ownShipClear
        for (h in listOf(900f, 500f, 300f, 220f, 150f, 60f)) {
            val frame = HudRect(0f, 200f, 720f, 200f + h)
            val ys = OwnshipPosition.entries.map { y(it, frame) }
            val standard = frame.bottom - m.arcOriginFromBottom
            for ((p, v) in OwnshipPosition.entries.zip(ys)) {
                // 三角にかからない高さか、それができないほど低い枠では 1段目の高さ
                assertTrue("$h $p $v", v >= minOf(limit(frame), standard) - 1e-3f)
                assertTrue("$h $p $v", v <= standard + 1e-3f)
            }
            // 上の段が下の段より下にならない
            assertEquals("$h", ys.sortedDescending(), ys)
        }
        // 回避枠が低くて縦中央が 3段目より下になるとき（高さ 220 → 中央は下端から 110 < 144）: 4・5段目は 3段目と同じ高さ
        val low = HudRect(0f, 200f, 720f, 420f)
        assertEquals(y(OwnshipPosition.HIGHER, low), y(OwnshipPosition.NEAR_CENTER, low), 1e-3f)
        assertEquals(y(OwnshipPosition.HIGHER, low), y(OwnshipPosition.CENTER, low), 1e-3f)
    }
}
