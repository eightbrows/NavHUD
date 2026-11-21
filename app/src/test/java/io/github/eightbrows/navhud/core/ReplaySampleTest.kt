package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TemporaryWaypoints
import io.github.eightbrows.navhud.core.replay.ReplayHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * ステップ3: track.csv のリプレイで NavState を確かめる。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class ReplaySampleTest {

    companion object {
        private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

        private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    }

    private val fixes: List<Fix>
        get() = SampleTrack.fixes()

    private fun engine() = NavEngine(NavSettings(noFixTimeoutSec = 10), JST, SourceKind.REPLAY)

    @Test
    fun noFixDuringLongGap() {
        // 62秒欠損（01:06:52Z → 01:07:54Z）の前後だけをリプレイする
        val part = fixes.filter { it.timeMs in ms("2026-08-14T01:06:00Z")..ms("2026-08-14T01:09:00Z") }
        val h = ReplayHarness(part, engine())
        h.play()

        val gapStart = ms("2026-08-14T01:06:52Z")
        val atGapStart = h.advanceTo(gapStart)
        assertFalse(atGapStart.noFix)
        assertEquals(gapStart, atGapStart.fix!!.timeMs)

        // 欠損開始からちょうど 10 秒は NO FIX ではない、それを超えたら NO FIX
        assertFalse(h.advanceTo(gapStart + 10_000).noFix)
        val stalled = h.advanceTo(gapStart + 10_001)
        assertTrue(stalled.noFix)
        assertEquals("欠損中は Fix が来ない", gapStart, stalled.fix!!.timeMs)
        assertEquals("時計は進む", gapStart + 10_001, stalled.nowMs)

        // 欠損中も時計は進み、NO FIX のまま
        val beforeFix = h.advanceTo(ms("2026-08-14T01:07:53.900Z"))
        assertTrue(beforeFix.noFix)
        assertEquals(gapStart, beforeFix.fix!!.timeMs)

        // 01:07:54Z の Fix で戻る
        val back = h.advanceTo(ms("2026-08-14T01:07:54Z"))
        assertFalse(back.noFix)
        assertEquals(ms("2026-08-14T01:07:54Z"), back.fix!!.timeMs)
    }

    @Test
    fun pausedReplayDoesNotGoNoFix() {
        val part = fixes.filter { it.timeMs in ms("2026-08-14T01:06:00Z")..ms("2026-08-14T01:09:00Z") }
        val h = ReplayHarness(part, engine())
        h.play()
        h.advanceTo(ms("2026-08-14T01:06:30Z"))
        h.pause()
        val paused = h.advanceBy(60_000)
        assertFalse(paused.noFix)
        assertEquals(ms("2026-08-14T01:06:30Z"), paused.nowMs)
        assertFalse(paused.playing)
    }

    @Test
    fun temporaryWaypointsArePassedInOrder() {
        val all = fixes
        val wps = TemporaryWaypoints.fromTrack(all, JST)
        assertEquals(listOf("WP1", "WP2", "WP3"), wps.map { it.name })
        assertNotNull(wps[1].targetTime)
        assertNotNull(wps[1].deadlineTime)
        assertNull(wps[0].targetTime)

        val e = engine()
        e.setWaypoints(wps)
        val sequence = mutableListOf(e.state.nextWpIndex)
        var targetBeforeWp2Reached: Long? = null
        for (f in all) {
            val before = e.state
            val s = e.onFix(f, f.timeMs)
            if (s.nextWpIndex != sequence.last()) {
                if (before.nextWpIndex == 1) targetBeforeWp2Reached = before.targetCountdownSec
                sequence += s.nextWpIndex
            }
        }
        assertEquals(listOf(0, 1, 2, null), sequence)
        assertTrue(e.state.waypoints.all { it.reached })
        // WP2 の目標時刻は通過時刻なので、到達半径に入る直前のカウントダウンは 0〜2 分の範囲
        assertTrue("targetCountdown=$targetBeforeWp2Reached", targetBeforeWp2Reached!! in 0..120)
    }
}
