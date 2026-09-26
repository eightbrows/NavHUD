package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionLostTest {

    @Test
    fun neverFixedIsLost() {
        assertTrue(isPositionLost(nowMs = 0, lastFixMs = null))
    }

    @Test
    fun tenSecondBoundary() {
        assertFalse(isPositionLost(nowMs = 10_000, lastFixMs = 0))
        assertTrue(isPositionLost(nowMs = 10_001, lastFixMs = 0))
    }

    @Test
    fun customTimeout() {
        assertFalse(isPositionLost(nowMs = 30_000, lastFixMs = 0, timeoutMs = 30_000))
        assertTrue(isPositionLost(nowMs = 30_001, lastFixMs = 0, timeoutMs = 30_000))
    }
}
