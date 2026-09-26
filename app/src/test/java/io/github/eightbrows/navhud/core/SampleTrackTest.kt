package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.TrackCsv
import io.github.eightbrows.navhud.core.io.TrackParseResult
import io.github.eightbrows.navhud.core.nav.detectGaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** §9 受け入れテスト（sample/.../track.csv）。 */
class SampleTrackTest {

    companion object {
        private const val PATH = "sample/session_20260814_075234/track.csv"

        val track: TrackParseResult by lazy {
            // 単体テストの作業ディレクトリは app/ のことが多い
            val file = listOf(File(PATH), File("../$PATH")).first { it.exists() }
            file.inputStream().use { TrackCsv.parse(it) }
        }
    }

    private val fixes get() = track.fixes

    @Test
    fun countAndSkipped() {
        assertEquals(11_172, fixes.size)
        assertEquals(0, track.skippedLines)
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
        val t = Instant.parse("2026-08-13T23:03:26Z").toEpochMilli()
        val f = fixes.single { it.timeMs == t }
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
}
