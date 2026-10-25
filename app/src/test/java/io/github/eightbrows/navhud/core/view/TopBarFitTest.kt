package io.github.eightbrows.navhud.core.view

import org.junit.Assert.assertEquals
import org.junit.Test

/** 上部バーの文字の倍率（20261025-D09）: 入るときは縮めない。入らないときだけ全部同じ倍率で小さくする。 */
class TopBarFitTest {

    @Test
    fun noShrinkWhenItFits() {
        assertEquals(1f, TopBarFit.textScale(availDp = 360f, textDp = 200f, fixedDp = 100f), 0f)
        // ちょうど入る
        assertEquals(1f, TopBarFit.textScale(availDp = 300f, textDp = 200f, fixedDp = 100f), 0f)
        // 文字がない
        assertEquals(1f, TopBarFit.textScale(availDp = 10f, textDp = 0f, fixedDp = 100f), 0f)
    }

    @Test
    fun shrinksTextOnlyWhenItDoesNotFit() {
        // 余白・枠・すき間（100）は縮めない。文字の 260 を 360 − 100 = 260 に → 1、240 に → 240 / 260
        val s = TopBarFit.textScale(availDp = 340f, textDp = 260f, fixedDp = 100f)
        assertEquals(240f / 260f, s, 1e-6f)
        // 縮めた文字と決まった幅で、ちょうど使える幅
        assertEquals(340f, 260f * s + 100f, 1e-3f)
    }

    @Test
    fun neverBelowTheMinimum() {
        assertEquals(TopBarFit.MIN_SCALE, TopBarFit.textScale(availDp = 150f, textDp = 260f, fixedDp = 100f), 0f)
        assertEquals(TopBarFit.MIN_SCALE, TopBarFit.textScale(availDp = 50f, textDp = 260f, fixedDp = 100f), 0f)
    }
}
