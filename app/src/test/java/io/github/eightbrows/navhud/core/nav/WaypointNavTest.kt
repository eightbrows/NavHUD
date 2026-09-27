package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class WaypointNavTest {

    private val wps = listOf(
        Waypoint("WP1", 33.40, 133.00, enabled = false),
        Waypoint("WP2", 33.41, 133.00, reached = true),
        Waypoint("WP3", 33.42, 133.00),
        Waypoint("WP4", 33.43, 133.00),
    )

    @Test
    fun nextSkipsDisabledAndReached() {
        assertEquals(2, WaypointNav.nextIndex(wps))
        assertNull(WaypointNav.nextIndex(wps.map { it.copy(reached = true) }))
        assertNull(WaypointNav.nextIndex(emptyList()))
    }

    @Test
    fun autoReachWithinRadius() {
        // WP3 の約33m南
        val lat = 33.42 - 33.0 / 111_195.0
        val reached = WaypointNav.autoReach(wps, lat, 133.00, defaultRadiusM = 50.0)
        assertEquals(true, reached[2].reached)
        assertEquals(3, WaypointNav.nextIndex(reached))

        val notYet = WaypointNav.autoReach(wps, lat, 133.00, defaultRadiusM = 20.0)
        assertEquals(wps, notYet)
        assertEquals(2, WaypointNav.nextIndex(notYet))
    }

    @Test
    fun perWaypointRadiusOverridesDefault() {
        // WP3 の約33m南。WP3 だけ半径 20m → 全体が 50m でも到達しない。半径 40m なら到達
        val lat = 33.42 - 33.0 / 111_195.0
        val tight = wps.toMutableList().also { it[2] = it[2].copy(radiusM = 20.0) }
        assertEquals(tight, WaypointNav.autoReach(tight, lat, 133.00, defaultRadiusM = 50.0))
        val wide = wps.toMutableList().also { it[2] = it[2].copy(radiusM = 40.0) }
        assertEquals(true, WaypointNav.autoReach(wide, lat, 133.00, defaultRadiusM = 20.0)[2].reached)
    }

    @Test
    fun manualToggleIsIndividual() {
        val t = WaypointNav.toggleReached(wps, 1)
        assertEquals(false, t[1].reached)
        assertEquals(wps.filterIndexed { i, _ -> i != 1 }, t.filterIndexed { i, _ -> i != 1 })
        assertEquals(1, WaypointNav.nextIndex(t))

        val t3 = WaypointNav.toggleReached(wps, 3)
        assertEquals(true, t3[3].reached)
        assertEquals(false, t3[2].reached)
    }

    @Test
    fun manualToggleIgnoresDisabled() {
        assertSame(wps, WaypointNav.toggleReached(wps, 0))
        assertSame(wps, WaypointNav.toggleReached(wps, 99))
    }

    @Test
    fun eta() {
        assertEquals(1_000_000L + 100_000L, WaypointNav.etaMs(1_000_000L, 1000.0, 10.0))
        assertNull(WaypointNav.etaMs(1_000_000L, 1000.0, 0.2))
        assertNull(WaypointNav.etaMs(1_000_000L, 1000.0, null))
        assertEquals(1_000_000L + 2_000_000L, WaypointNav.etaMs(1_000_000L, 1000.0, 0.5))
    }

    @Test
    fun countdownJst() {
        val jst = ZoneId.of("Asia/Tokyo")
        fun at(iso: String) = Instant.parse(iso).toEpochMilli()
        // 10:00 JST = 01:00Z
        assertEquals(600L, WaypointNav.countdownSec(at("2026-08-14T01:00:00Z"), LocalTime.of(10, 10), jst))
        assertEquals(-300L, WaypointNav.countdownSec(at("2026-08-14T01:05:00Z"), LocalTime.of(10, 0), jst))
        // 23:50 JST = 14:50Z、日付をまたいで 00:10
        assertEquals(1200L, WaypointNav.countdownSec(at("2026-08-14T14:50:00Z"), LocalTime.of(0, 10), jst))
    }

    @Test
    fun countdownWrapsAtTwelveHours() {
        assertEquals(12 * 3600L, WaypointNav.countdownSec(LocalTime.of(0, 0), LocalTime.of(12, 0)))
        assertEquals(-(12 * 3600L - 1), WaypointNav.countdownSec(LocalTime.of(0, 0), LocalTime.of(12, 0, 1)))
        assertEquals(-1200L, WaypointNav.countdownSec(LocalTime.of(0, 10), LocalTime.of(23, 50)))
    }
}
