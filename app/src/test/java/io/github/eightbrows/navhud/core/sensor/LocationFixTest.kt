package io.github.eightbrows.navhud.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationFixTest {

    private fun fix(
        speed: Float? = 10f,
        bearing: Float? = 62.4f,
        bearingAcc: Float? = 4f,
        alt: Double? = 1320.7,
        acc: Float? = 4.5f,
    ) = LocationFix.toFix(1_786_661_555_000L, 33.474, 133.0003, alt, speed, bearing, bearingAcc, acc)

    @Test
    fun allValues() {
        val f = fix()
        assertEquals(1_786_661_555_000L, f.timeMs)
        assertEquals(33.474, f.lat, 0.0)
        assertEquals(133.0003, f.lon, 0.0)
        assertEquals(1320.7, f.altRawM!!, 0.0)
        assertEquals(10f, f.speedMps)
        assertEquals(62.4f, f.bearingDeg)
        assertEquals(4.5f, f.horizAccM)
    }

    @Test
    fun missingValuesAreNull() {
        val f = fix(speed = null, bearing = null, bearingAcc = null, alt = null, acc = null)
        assertNull(f.altRawM)
        assertNull(f.speedMps)
        assertNull(f.bearingDeg)
        assertNull(f.horizAccM)
    }

    @Test
    fun stoppedWithoutBearingAccuracyHasNoBearing() {
        // track.csv と同じ扱い: 停止中（速度 0）で方位の精度が出ていなければ方位なし
        assertNull(fix(speed = 0f, bearing = 0f, bearingAcc = null).bearingDeg)
        // 精度が出ていれば停止中でも使う
        assertEquals(45f, fix(speed = 0f, bearing = 45f, bearingAcc = 3f).bearingDeg)
        // 走行中で精度を出さない端末（古い端末）は方位を使う
        assertEquals(45f, fix(speed = 8f, bearing = 45f, bearingAcc = null).bearingDeg)
        // 速度が分からない端末も方位を使う
        assertEquals(45f, fix(speed = null, bearing = 45f, bearingAcc = null).bearingDeg)
    }

    @Test
    fun zeroBearingAccuracyMeansNoBearing() {
        assertNull(fix(bearingAcc = 0f).bearingDeg)
    }

    @Test
    fun oddValuesAreCleaned() {
        assertEquals(10f, fix(bearing = 370f).bearingDeg!!, 1e-4f)
        assertEquals(350f, fix(bearing = -10f).bearingDeg!!, 1e-4f)
        assertNull(fix(alt = Double.NaN).altRawM)
        assertNull(fix(speed = -1f).speedMps)
    }
}
