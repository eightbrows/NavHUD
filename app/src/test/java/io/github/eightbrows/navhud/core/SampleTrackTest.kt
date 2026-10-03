package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.TrackParseResult
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.nav.RateTracker
import io.github.eightbrows.navhud.core.nav.detectGaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * §9 受け入れテスト（track.csv）。
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class SampleTrackTest {

    companion object {

        private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    }

    private val result: TrackParseResult
        get() = SampleTrack.result()

    private val fixes: List<Fix> get() = result.fixes

    /** 時刻 untilMs 以下の Fix をすべて入れた RateTracker。 */
    private fun trackerUntil(untilMs: Long): RateTracker {
        val t = RateTracker()
        fixes.takeWhile { it.timeMs <= untilMs }.forEach(t::add)
        return t
    }

    @Test
    fun countAndSkipped() {
        assertEquals(11_172, fixes.size)
        assertEquals(0, result.skippedLines)
    }

    @Test
    fun firstRow() {
        val f = fixes.first()
        assertEquals(33.47406592, f.lat, 0.0)
        assertEquals(133.00034867, f.lon, 0.0)
        assertEquals(1320.71875, f.altRawM!!, 0.0)
        assertEquals(14.48f, f.speedMps!!, 0f)
        assertEquals(62.4f, f.bearingDeg!!, 0f)
        assertEquals(4.58744f, f.horizAccM!!, 0f)
    }

    @Test
    fun shortRowIsRead() {
        val f = fixes.single { it.timeMs == ms("2026-08-13T23:03:26Z") }
        assertEquals(9.61f, f.speedMps!!, 0f)
        assertEquals(301.3f, f.bearingDeg!!, 0f)
    }

    @Test
    fun nullBearingsAreStopped() {
        val nulls = fixes.filter { it.bearingDeg == null }
        assertEquals(3_243, nulls.size)
        assertTrue(nulls.all { it.speedMps == 0f })
    }

    @Test
    fun totalDistance() {
        val km = fixes.zipWithNext().sumOf { (a, b) -> Geo.distanceM(a.lat, a.lon, b.lat, b.lon) } / 1000
        assertEquals(74.23, km, 0.05)
    }

    @Test
    fun gaps() {
        val gaps = detectGaps(fixes)
        assertEquals(9, gaps.size)
        assertEquals(62_000L, gaps.maxOf { it.durationMs })
    }

    @Test
    fun rate60IsZeroWhileStopped() {
        // 速度 0 が欠損なしで 61 点以上続く最初の区間の末尾
        var run = 0
        var end = -1
        for (i in fixes.indices) {
            val contiguous = i > 0 && fixes[i].timeMs - fixes[i - 1].timeMs <= 1_500
            run = if (fixes[i].speedMps == 0f) (if (contiguous && run > 0) run + 1 else 1) else 0
            if (run >= 61) { end = i; break }
        }
        assertTrue("停止区間が見つからない", end >= 0)

        val stopped = fixes.subList(end - 60, end + 1)
        val jitterM = stopped.zipWithNext().sumOf { (a, b) -> Geo.distanceM(a.lat, a.lon, b.lat, b.lon) }
        assertTrue("この区間に位置ジッタがあること: $jitterM", jitterM > 0.0)

        val rate = trackerUntil(fixes[end].timeMs).rate(60)
        assertNotNull(rate)
        assertEquals(0.0, rate!!.distanceM, 0.0)
    }

    @Test
    fun rateAcrossLongGap() {
        // 62秒欠損（01:06:52Z → 01:07:54Z）の直後
        val t = trackerUntil(ms("2026-08-14T01:07:54Z"))
        val r60 = t.rate(60)
        assertNotNull(r60)
        assertEquals(1172.0, r60!!.distanceM, 5.0)
        assertNull(t.rate(10))

        // 48秒欠損（01:07:56Z → 01:08:44Z）の後、12 秒分（01:08:44〜01:08:55）
        val after = trackerUntil(ms("2026-08-14T01:08:55Z"))
        assertNotNull(after.rate(10))
    }

    @Test
    fun rate10IsNullUntilTenSecondsAfterGap() {
        // 01:08:44 から 10 秒たたないうちは base が欠損前（01:07:56）になるので null
        assertNull(trackerUntil(ms("2026-08-14T01:08:53Z")).rate(10))
        assertNotNull(trackerUntil(ms("2026-08-14T01:08:54Z")).rate(10))
    }
}
