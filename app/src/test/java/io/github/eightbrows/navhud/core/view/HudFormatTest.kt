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
        // 距離環: 1000m 未満は m の数字だけ、以上は k
        assertEquals(
            listOf("25", "50", "100", "250", "500", "1k", "2.5k", "5k", "10k", "25k"),
            listOf(25.0, 50.0, 100.0, 250.0, 500.0, 1_000.0, 2_500.0, 5_000.0, 10_000.0, 25_000.0).map { HudFormat.ringLabel(it) },
        )
        assertEquals("75", HudFormat.ringLabel(75.0))
        assertEquals("1.5k", HudFormat.ringLabel(1_500.0))
        assertEquals("12.5k", HudFormat.ringLabel(12_500.0))
        // 縮尺の段
        assertEquals(
            listOf("50m", "100m", "200m", "500m", "1km", "2km", "5km", "10km", "20km", "50km"),
            io.github.eightbrows.navhud.core.nav.RangeAuto.ALL_STEPS_KM.map { HudFormat.rangeStep(it * 1000) },
        )
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
