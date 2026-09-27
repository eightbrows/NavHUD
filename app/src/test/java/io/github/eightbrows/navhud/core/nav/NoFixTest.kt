package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoFixTest {

    @Test
    fun neverFixedIsNoFix() {
        assertTrue(isNoFix(nowMs = 0, lastFixMs = null))
    }

    @Test
    fun tenSecondBoundary() {
        assertFalse(isNoFix(nowMs = 10_000, lastFixMs = 0))
        assertTrue(isNoFix(nowMs = 10_001, lastFixMs = 0))
    }

    @Test
    fun customTimeout() {
        assertFalse(isNoFix(nowMs = 30_000, lastFixMs = 0, timeoutMs = 30_000))
        assertTrue(isNoFix(nowMs = 30_001, lastFixMs = 0, timeoutMs = 30_000))
    }
}
