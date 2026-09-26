package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.nav.Rate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HudFormatTest {

    @Test
    fun bearing() {
        assertEquals("062°", HudFormat.bearing(62.4))
        assertEquals("000°", HudFormat.bearing(359.6))
        assertEquals("---", HudFormat.bearing(null as Double?))
    }

    @Test
    fun distance() {
        assertEquals("850 m", HudFormat.distance(850.2))
        assertEquals("1.17 km", HudFormat.distance(1_172.0))
        assertEquals("11.9 km", HudFormat.distance(11_860.0))
        assertEquals("---", HudFormat.distance(null))
    }

    @Test
    fun labels() {
        assertEquals("N", HudFormat.compassLabel(0))
        assertEquals("3", HudFormat.compassLabel(30))
        assertEquals("E", HudFormat.compassLabel(90))
        assertEquals("33", HudFormat.compassLabel(330))
        assertEquals("1", HudFormat.ringKm(1_000.0))
        assertEquals("0.5", HudFormat.ringKm(500.0))
    }

    @Test
    fun countdownAndTime() {
        assertEquals("+0:10:00", HudFormat.countdown(600))
        assertEquals("-0:05:00", HudFormat.countdown(-300))
        assertEquals("---", HudFormat.countdown(null))
        val t = Instant.parse("2026-08-14T01:00:05Z").toEpochMilli()
        assertEquals("10:00:05", HudFormat.time(t, ZoneId.of("Asia/Tokyo")))
        assertEquals("---", HudFormat.time(null, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun valuesAndRate() {
        assertEquals("52 km/h", HudFormat.speedKmh(14.48f))
        assertEquals("1285 m", HudFormat.altitude(1284.7))
        assertEquals("33.47407, 133.00035", HudFormat.latLon(33.47406592, 133.00034867))
        assertEquals("1.20 km  +35 m", HudFormat.rate(Rate(60, 1_200.0, 35.0, 20.0)))
        assertEquals("600 m  ---", HudFormat.rate(Rate(60, 600.0, null, 10.0)))
        assertEquals("---", HudFormat.rate(null))
    }
}
