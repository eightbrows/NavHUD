package io.github.eightbrows.navhud.core.io

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 読み込むファイルの種類の見分け（20261103-D01）。 */
class FileKindTest {

    private fun kind(text: String) = FileKinds.detect(text.toByteArray(Charsets.UTF_8))

    private val trackCsv = "epoch_ms,utc_iso8601,latitude,longitude,altitude_ellipsoid_m,speed_mps\n1786661555000,2026-08-13T22:52:35Z,33.47406592,133.00034867,1320.7,14.48\n"
    private val wpCsv = "lat,lon,ele,name,target_time,deadline_time,enabled\n33.47649101,133.00275172,1300,峠の入口,,,1\n"

    @Test
    fun rightCombinations() {
        // 軌跡の CSV（GpsLogger の track.csv の見出し）と、WP の CSV
        assertEquals(FileKind.TRACK, kind(trackCsv))
        assertEquals(FileKind.WAYPOINTS, kind(wpCsv))
        // 時刻の列は utc_iso8601 だけでも軌跡
        assertEquals(FileKind.TRACK, kind("latitude,longitude,utc_iso8601\n33.5,133.0,2026-08-13T22:52:35Z\n"))
        // GPX の <wpt> は WP。<wpt> と <trk> が両方あれば WP（WP の読み込みが <wpt> を読める）
        assertEquals(FileKind.WAYPOINTS, kind("<?xml version=\"1.0\"?><gpx><wpt lat=\"33.5\" lon=\"133.0\"><name>A</name></wpt></gpx>"))
        assertEquals(FileKind.WAYPOINTS, kind("<gpx><trk><trkseg/></trk><wpt lat=\"33.5\" lon=\"133.0\"/></gpx>"))
        // GpsLogger の zip は軌跡
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("session_20260814_075234/track.csv")); z.write(trackCsv.toByteArray()); z.closeEntry() }
        }.toByteArray()
        assertEquals(FileKind.TRACK, FileKinds.detect(zip))
    }

    @Test
    fun reversedCombinationsAreTellable() {
        // WP として軌跡を選んだ → TRACK（読み込まずに「軌跡のファイルのようです」）。軌跡として WP を選んだ → WAYPOINTS
        assertEquals(FileKind.TRACK, kind(trackCsv))
        assertEquals(FileKind.WAYPOINTS, kind(wpCsv))
        // 時刻の列があれば、lat・lon があっても軌跡
        assertEquals(FileKind.TRACK, kind("lat,lon,epoch_ms\n33.5,133.0,1786661555000\n"))
    }

    @Test
    fun gpxWithOnlyATrackIsATrack() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>track</name><trkseg><trkpt lat="33.47" lon="133.00"><time>2026-08-13T22:52:35Z</time></trkpt></trkseg></trk>
            </gpx>"""
        assertEquals(FileKind.TRACK, kind(gpx))
        // 名前空間の接頭辞が付いていても同じ
        assertEquals(FileKind.TRACK, kind("<?xml version=\"1.0\"?><g:gpx xmlns:g=\"x\"><g:trk/></g:gpx>"))
        // <wpt> も <trk> もない GPX はどちらでもない
        assertEquals(FileKind.UNKNOWN, kind("<?xml version=\"1.0\"?><gpx><rte/></gpx>"))
    }

    @Test
    fun neitherKindIsUnknown() {
        assertEquals(FileKind.UNKNOWN, kind("name,x,y\nA,1,2\n"))
        assertEquals(FileKind.UNKNOWN, kind(""))
        assertEquals(FileKind.UNKNOWN, kind("\n\n"))
        // lat だけ・lon だけは WP ではない
        assertEquals(FileKind.UNKNOWN, kind("lat,name\n33.5,A\n"))
    }

    @Test
    fun headerNamesFollowEachReader() {
        // WP の読み込みと同じく、lat・lon は大文字小文字・前後の空白を問わない。BOM・引用符・Shift_JIS も読める
        assertEquals(FileKind.WAYPOINTS, kind("﻿ Lat , LON ,name\n33.5,133.0,A\n"))
        assertEquals(FileKind.WAYPOINTS, kind("\"lat\",\"lon\",\"name\"\n33.5,133.0,\"A\"\n"))
        assertEquals(FileKind.WAYPOINTS, FileKinds.detect("lat,lon,name\r\n33.5,133.0,峠\r\n".toByteArray(charset("windows-31j"))))
        // 軌跡の時刻の列は、軌跡の読み込みと同じく名前をそのまま比べる（EPOCH_MS は軌跡の時刻の列ではない）
        assertEquals(FileKind.UNKNOWN, kind("latitude,longitude,EPOCH_MS\n33.5,133.0,1\n"))
        assertEquals(FileKind.TRACK, kind(" epoch_ms ,latitude,longitude\n1,33.5,133.0\n"))
        // 空行のあとの見出しでも見る
        assertEquals(FileKind.WAYPOINTS, kind("\n\nlat,lon\n33.5,133.0\n"))
    }
}
