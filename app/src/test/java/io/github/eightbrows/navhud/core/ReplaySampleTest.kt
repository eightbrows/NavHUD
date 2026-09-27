package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.io.TrackCsv
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
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * ステップ3: track.csv のリプレイで NavState を確かめる。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class ReplaySampleTest {

    companion object {
        private const val PATH = "sample/session_20260814_075234/track.csv"
        private val file: File? = listOf(File(PATH), File("../$PATH")).firstOrNull { it.exists() }
        private val track: List<Fix>? by lazy { file?.inputStream()?.use { TrackCsv.parse(it).fixes } }

        private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
        private const val COMPASS_DEG = 45f

        private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    }

    private val fixes: List<Fix>
        get() {
            assumeTrue("track.csv がないためスキップ: $PATH", track != null)
            return track!!
        }

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
    fun hybridSwitchesToCompassWhileStopped() {
        val all = fixes
        // 速度 0 が 61 点以上続く最初の区間
        var run = 0
        var runStart = -1
        for (i in all.indices) {
            run = if (all[i].speedMps == 0f) run + 1 else 0
            if (run == 1) runStart = i
            if (run >= 61) break
        }
        assertTrue(run >= 61)

        val e = engine()
        e.onCompass(COMPASS_DEG, all.first().timeMs)
        val states = all.subList(0, runStart + 61).map { e.onFix(it, it.timeMs) }

        // 停止前、最後に GPS 方位が使えた地点では GPS
        val lastMoving = (runStart - 1 downTo 0).first { isUsable(all[it]) }
        assertEquals(Heading(all[lastMoving].bearingDeg, HeadingSrc.GPS), states[lastMoving].heading)
        // 停止区間ではずっとコンパス
        for (i in runStart until runStart + 61) {
            assertEquals("index $i", Heading(COMPASS_DEG, HeadingSrc.COMPASS), states[i].heading)
        }
    }

    private fun isUsable(f: Fix): Boolean =
        f.bearingDeg != null && (f.speedMps ?: 99f) >= 1.4f && (f.horizAccM ?: 0f) <= 15f

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
