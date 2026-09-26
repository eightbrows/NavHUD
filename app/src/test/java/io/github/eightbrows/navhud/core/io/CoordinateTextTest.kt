package io.github.eightbrows.navhud.core.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateTextTest {

    @Test
    fun googleMapsPin() {
        assertEquals(LatLon(34.6937, 135.5023), CoordinateText.parse("34.69370, 135.50230"))
        assertEquals(LatLon(34.6937, 135.5023), CoordinateText.parse("  34.69370,135.50230 \n"))
        assertEquals(LatLon(-33.8688, -151.2093), CoordinateText.parse("-33.8688 , -151.2093"))
        assertEquals(LatLon(35.0, 135.0), CoordinateText.parse("35,135"))
    }

    @Test
    fun outOfRange() {
        assertNull(CoordinateText.parse("90.1, 135"))
        assertNull(CoordinateText.parse("-90.1, 135"))
        assertNull(CoordinateText.parse("35, 180.5"))
        assertNull(CoordinateText.parse("35, -181"))
        assertEquals(LatLon(90.0, -180.0), CoordinateText.parse("90, -180"))
    }

    @Test
    fun unsupportedFormats() {
        assertNull(CoordinateText.parse(""))
        assertNull(CoordinateText.parse("34.69370"))
        assertNull(CoordinateText.parse("34.69370 135.50230"))
        assertNull(CoordinateText.parse("34°41'37.3\"N 135°30'08.3\"E"))
        assertNull(CoordinateText.parse("https://maps.google.com/?q=34.69370,135.50230"))
        assertNull(CoordinateText.parse("34.69370, 135.50230\n35.0, 136.0"))
        assertNull(CoordinateText.parse("abc, def"))
    }

    @Test
    fun formatRoundTrips() {
        val s = CoordinateText.format(34.693701234, 135.5023)
        assertEquals("34.693701234, 135.5023", s)
        assertEquals(LatLon(34.693701234, 135.5023), CoordinateText.parse(s))
    }
}
