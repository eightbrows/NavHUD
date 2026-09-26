package io.github.eightbrows.navhud.core.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

class WaypointGpxTest {

    private val jst = ZoneId.of("Asia/Tokyo")

    private val gpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:nav="https://example.com/navhud">
          <wpt lat="34.69370" lon="135.50230">
            <ele>15.5</ele>
            <name>大阪駅</name>
            <time>2026-08-14T00:30:00Z</time>
            <extensions><nav:deadline>2026-08-14T10:00:00+09:00</nav:deadline></extensions>
          </wpt>
          <wpt lat="35.0" lon="136.0">
            <name>時刻だけ</name>
            <time>9:30</time>
            <extensions><deadline>09:45:30</deadline></extensions>
          </wpt>
          <wpt lat="36.0" lon="137.0"/>
          <wpt lon="137.0"><name>lat なし</name></wpt>
          <wpt lat="95.0" lon="137.0"><name>範囲外</name></wpt>
          <trk><trkseg><trkpt lat="1" lon="1"/></trkseg></trk>
        </gpx>
    """.trimIndent()

    @Test
    fun readsWaypoints() {
        val r = WaypointGpx.parse(gpx.toByteArray(), jst)
        assertEquals(2, r.skippedLines)
        assertEquals(3, r.waypoints.size)

        val a = r.waypoints[0]
        assertEquals("大阪駅", a.name)
        assertEquals(34.6937, a.lat, 0.0)
        assertEquals(135.5023, a.lon, 0.0)
        assertEquals(15.5, a.eleM!!, 0.0)
        // ISO 8601 の日時は JST の時刻へ
        assertEquals(LocalTime.of(9, 30), a.targetTime)
        assertEquals(LocalTime.of(10, 0), a.deadlineTime)
        assertTrue(a.enabled)

        val b = r.waypoints[1]
        assertEquals(LocalTime.of(9, 30), b.targetTime)
        assertEquals(LocalTime.of(9, 45, 30), b.deadlineTime)
        assertNull(b.eleM)

        val c = r.waypoints[2]
        assertEquals("WP3", c.name)
        assertNull(c.targetTime)
        assertNull(c.deadlineTime)
    }

    @Test
    fun brokenXmlReturnsEmpty() {
        assertEquals(WaypointParseResult(emptyList(), 0), WaypointGpx.parse("<gpx><wpt".toByteArray(), jst))
    }

    @Test
    fun doctypeIsNotExpanded() {
        val evil = """
            <?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <gpx><wpt lat="1" lon="1"><name>&x;</name></wpt></gpx>
        """.trimIndent()
        val r = WaypointGpx.parse(evil.toByteArray(), jst)
        assertTrue(r.waypoints.none { it.name.contains("root") })
    }

    @Test
    fun detectsGpx() {
        assertTrue(WaypointGpx.looksLikeGpx(gpx.toByteArray()))
        assertTrue(WaypointGpx.looksLikeGpx("﻿<gpx></gpx>".toByteArray()))
        assertFalse(WaypointGpx.looksLikeGpx("lat,lon\n1,1\n".toByteArray()))
    }
}
