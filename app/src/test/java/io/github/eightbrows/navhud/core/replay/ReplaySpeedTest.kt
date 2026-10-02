package io.github.eightbrows.navhud.core.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplaySpeedTest {

    @Test
    fun slowerAndFaster() {
        assertEquals(5, ReplaySpeed.faster(2))
        assertEquals(30, ReplaySpeed.faster(10))
        assertEquals(2, ReplaySpeed.slower(5))
        assertEquals(1, ReplaySpeed.slower(2))
        // 端では変えられない（ボタンはグレー）
        assertNull(ReplaySpeed.slower(1))
        assertNull(ReplaySpeed.faster(30))
    }
}
