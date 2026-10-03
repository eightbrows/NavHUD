package io.github.eightbrows.navhud.core.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackCsvTest {

    @Test
    fun headerOrderMissingOptionalColumnsAndBadRows() {
        val csv = """
            longitude,latitude,utc_iso8601,speed_mps
            133.0,33.0,2026-08-13T22:52:35.000Z,1.5
            133.1,,2026-08-13T22:52:36.000Z,1.5
            133.2,33.2,not-a-time,1.5

            133.3,33.3,2026-08-13T22:52:38.000Z,0
            133.4,33.4,2026-08-13T22:52:39.000Z
        """.trimIndent()
        val r = TrackCsv.parse(csv)
        assertEquals(2, r.skippedLines)
        assertEquals(3, r.fixes.size)
        val f0 = r.fixes[0]
        assertEquals(33.0, f0.lat, 0.0)
        assertEquals(133.0, f0.lon, 0.0)
        assertEquals(1786661555000L, f0.timeMs)
        assertNull(f0.altRawM)
        assertNull(f0.bearingDeg) // bearing 列なし
        assertEquals(0f, r.fixes[1].speedMps)
        assertNull(r.fixes[2].speedMps) // 末尾列欠け
    }

    @Test
    fun bomAndEpochPreferred() {
        val csv = "\uFEFFepoch_ms,utc_iso8601,latitude,longitude\n" +
            "1000,2026-08-13T22:52:35.000Z,1.0,2.0\n" +
            ",2026-08-13T22:52:35.000Z,1.0,2.0\n"
        val r = TrackCsv.parse(csv)
        assertEquals(0, r.skippedLines)
        assertEquals(1000L, r.fixes[0].timeMs)
        assertEquals(1786661555000L, r.fixes[1].timeMs)
    }

    @Test
    fun bearingValidity() {
        val withAcc = """
            epoch_ms,latitude,longitude,speed_mps,bearing_deg,bearing_acc_deg
            1,1,1,5,90,3
            2,1,1,0,0,0
            3,1,1,5,90,
        """.trimIndent()
        val a = TrackCsv.parse(withAcc).fixes
        assertEquals(90f, a[0].bearingDeg)
        assertNull(a[1].bearingDeg)
        assertNull(a[2].bearingDeg)

        val noAcc = """
            epoch_ms,latitude,longitude,speed_mps,bearing_deg
            1,1,1,5,90
            2,1,1,0,0
            3,1,1,,45
        """.trimIndent()
        val b = TrackCsv.parse(noAcc).fixes
        assertEquals(90f, b[0].bearingDeg)
        assertNull(b[1].bearingDeg)
        assertEquals(45f, b[2].bearingDeg)
    }
}
