package io.github.eightbrows.navhud.core.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayClockTest {

    private var real = 5_000L
    private val clock = ReplayClock { real }

    @Test
    fun pausedUntilPlay() {
        clock.seek(1_000_000)
        real += 3_000
        assertEquals(1_000_000, clock.nowMs())
        assertFalse(clock.playing)
    }

    @Test
    fun runsInRealTimeWhilePlaying() {
        clock.seek(1_000_000)
        clock.play()
        real += 2_500
        assertEquals(1_002_500, clock.nowMs())
        assertTrue(clock.playing)
    }

    @Test
    fun pauseStopsAndResumeContinues() {
        clock.seek(1_000_000)
        clock.play()
        real += 1_000
        clock.pause()
        real += 60_000
        assertEquals(1_001_000, clock.nowMs())
        clock.play()
        real += 500
        assertEquals(1_001_500, clock.nowMs())
    }

    @Test
    fun seekWhilePlaying() {
        clock.play()
        real += 1_000
        clock.seek(2_000_000)
        real += 1_000
        assertEquals(2_001_000, clock.nowMs())
    }

    @Test
    fun playAndPauseAreIdempotent() {
        clock.seek(0)
        clock.play()
        real += 1_000
        clock.play()
        real += 1_000
        assertEquals(2_000, clock.nowMs())
        clock.pause()
        clock.pause()
        assertEquals(2_000, clock.nowMs())
    }
}
