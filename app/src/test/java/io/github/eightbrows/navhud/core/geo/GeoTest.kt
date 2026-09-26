package io.github.eightbrows.navhud.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class GeoTest {

    @Test
    fun oneDegreeLatitude() {
        assertEquals(111_195.0, Geo.distanceM(33.0, 133.0, 34.0, 133.0), 1.0)
    }

    @Test
    fun cardinalBearings() {
        assertEquals(0.0, Geo.bearingDeg(33.0, 133.0, 33.1, 133.0), 1e-9)
        assertEquals(90.0, Geo.bearingDeg(33.0, 133.0, 33.0, 133.1), 0.05)
        assertEquals(180.0, Geo.bearingDeg(33.0, 133.0, 32.9, 133.0), 1e-9)
        assertEquals(270.0, Geo.bearingDeg(33.0, 133.0, 33.0, 132.9), 0.05)
    }

    @Test
    fun angleDiff() {
        assertEquals(20.0, Geo.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Geo.angleDiff(10.0, 350.0), 1e-9)
        assertEquals(180.0, Geo.angleDiff(0.0, 180.0), 1e-9)
    }

    @Test
    fun planarApproxWithin3km() {
        val lat0 = 33.5
        val lon0 = 133.0
        for (b in 0 until 360 step 15) {
            // 約3km先の点
            val rad = Math.toRadians(b.toDouble())
            val lat = lat0 + 0.027 * kotlin.math.cos(rad)
            val lon = lon0 + 0.032 * kotlin.math.sin(rad)
            val p = Geo.toEN(lat0, lon0, lat, lon)
            val diff = abs(hypot(p.e, p.n) - Geo.distanceM(lat0, lon0, lat, lon))
            assertTrue("bearing $b diff $diff", diff < 2.0)
        }
    }

    @Test
    fun screenRotation() {
        val east = Geo.toScreen(EN(100.0, 0.0), 90.0)
        assertEquals(0.0, east.right, 1e-9)
        assertEquals(100.0, east.fwd, 1e-9)
        val north = Geo.toScreen(EN(0.0, 100.0), 90.0)
        assertEquals(-100.0, north.right, 1e-9)
        assertEquals(0.0, north.fwd, 1e-9)
        val nu = Geo.toScreen(EN(30.0, 40.0), 0.0)
        assertEquals(30.0, nu.right, 1e-9)
        assertEquals(40.0, nu.fwd, 1e-9)
    }
}
