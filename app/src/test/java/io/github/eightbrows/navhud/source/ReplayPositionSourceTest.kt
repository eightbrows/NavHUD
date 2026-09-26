package io.github.eightbrows.navhud.source

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.replay.ReplayClock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPositionSourceTest {

    @Test
    fun emitsWholeTrackInOrderThenCompletes() = runBlocking {
        val track = listOf(1_000L, 2_000L, 3_000L, 10_000L, 11_000L).map { Fix(timeMs = it, lat = 33.0, lon = 133.0) }
        // 時計を読むたびに実時間が 1 秒進む偽の実時間
        var real = 0L
        val clock = ReplayClock { real.also { real += 1_000 } }
        val source = ReplayPositionSource(track, clock, pollMs = 1)
        assertEquals(1_000L, clock.nowMs())

        clock.play()
        val emitted = source.fixes.toList()
        assertEquals(track, emitted)
        assertTrue(source.finished)
    }
}
