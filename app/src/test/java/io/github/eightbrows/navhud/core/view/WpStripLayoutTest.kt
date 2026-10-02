package io.github.eightbrows.navhud.core.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WpStripLayoutTest {

    @Test
    fun nextWaypointIsSecondFromLeft() {
        // 左端は1つ前の WP、次の WP は2番目
        assertEquals(4, WpStripLayout.firstIndex(5))
        assertEquals(0, WpStripLayout.firstIndex(1))
        // 次の WP がリストの最初なら左詰め
        assertEquals(0, WpStripLayout.firstIndex(0))
        // 次の WP がなければ動かさない
        assertNull(WpStripLayout.firstIndex(null))
    }

    @Test
    fun trailingSpaceKeepsTheLastOnesSecond() {
        // 4 個見せるなら右端に 2 個分の空き（最後の WP でも2番目に置ける）
        assertEquals(2, WpStripLayout.trailingSlots(4))
        assertEquals(1, WpStripLayout.trailingSlots(3))
        assertEquals(0, WpStripLayout.trailingSlots(2))
        assertEquals(0, WpStripLayout.trailingSlots(1))
    }
}
