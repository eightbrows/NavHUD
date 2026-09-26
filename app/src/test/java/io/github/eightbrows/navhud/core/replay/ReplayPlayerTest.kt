package io.github.eightbrows.navhud.core.replay

import io.github.eightbrows.navhud.core.model.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPlayerTest {

    private fun fix(t: Long) = Fix(timeMs = t, lat = 33.0, lon = 133.0)

    // 1秒間隔、3000〜8000 は欠損
    private val track = listOf(fix(1_000), fix(2_000), fix(3_000), fix(8_000), fix(9_000))

    @Test
    fun emitsInOrderUpToNow() {
        val p = ReplayPlayer(track)
        assertEquals(1_000L, p.startMs)
        assertEquals(listOf(1_000L), p.due(1_000).map { it.timeMs })
        assertEquals(emptyList<Long>(), p.due(1_999).map { it.timeMs })
        assertEquals(listOf(2_000L, 3_000L), p.due(3_500).map { it.timeMs })
    }

    @Test
    fun nothingDuringGap() {
        val p = ReplayPlayer(track)
        p.due(3_000)
        for (t in 3_100L..7_900L step 100) assertTrue(p.due(t).isEmpty())
        assertEquals(listOf(8_000L), p.due(8_000).map { it.timeMs })
    }

    @Test
    fun finishesAfterLast() {
        val p = ReplayPlayer(track)
        assertFalse(p.finished)
        assertEquals(5, p.due(Long.MAX_VALUE).size)
        assertTrue(p.finished)
        assertNull(p.nextTimeMs)
        p.rewind()
        assertEquals(1_000L, p.nextTimeMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsortedTrack() {
        ReplayPlayer(listOf(fix(2_000), fix(1_000)))
    }
}
