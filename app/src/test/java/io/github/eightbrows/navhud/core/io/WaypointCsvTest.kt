package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.LocalTime

class WaypointCsvTest {

    private val sjis = Charset.forName("windows-31j")

    @Test
    fun writeThenReadIsIdentical() {
        val wps = listOf(
            Waypoint("大阪駅", 34.6937, 135.5023, 15.0, LocalTime.of(9, 30), LocalTime.of(10, 0)),
            Waypoint("A, \"B\" 地点", -33.8688, 151.2093, null, LocalTime.of(9, 5, 30), null, enabled = false),
            Waypoint("  前後に空白  ", 0.00001, -0.5, -12.5),
            Waypoint("改行\n入り", 89.999999, 179.9999999, 1234.0),
            Waypoint("半径つき", 34.0, 135.0, radiusM = 250.0),
        )
        val back = WaypointCsv.parse(WaypointCsv.encode(wps))
        assertEquals(0, back.skippedLines)
        assertEquals(wps, back.waypoints)
    }

    @Test
    fun reachedIsNotSaved() {
        val back = WaypointCsv.parse(WaypointCsv.encode(listOf(Waypoint("A", 1.0, 2.0, reached = true))))
        assertFalse(back.waypoints.single().reached)
    }

    @Test
    fun exportIsExcelFriendly() {
        val bytes = WaypointCsv.encode(listOf(Waypoint("大阪駅", 34.6937, 135.5023, 15.0, LocalTime.of(9, 30))))
        // UTF-8 の BOM 付き
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
        val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        assertEquals(
            "lat,lon,ele,name,target_time,deadline_time,enabled,radius\r\n34.6937,135.5023,15,大阪駅,09:30,,1,\r\n",
            text,
        )
        // 改行はすべて CRLF
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(text))
    }

    @Test
    fun timeFormatsFromExcel() {
        val csv = """
            lat,lon,name,target_time,deadline_time
            1,1,a,9:30,09:30
            1,1,b,9:30:00,09:30:15
            1,1,c,,
            1,1,d, 7:05 ,23:59:59
        """.trimIndent()
        val r = WaypointCsv.parse(csv)
        assertEquals(0, r.skippedLines)
        val w = r.waypoints
        assertEquals(LocalTime.of(9, 30), w[0].targetTime)
        assertEquals(LocalTime.of(9, 30), w[0].deadlineTime)
        assertEquals(LocalTime.of(9, 30), w[1].targetTime)
        assertEquals(LocalTime.of(9, 30, 15), w[1].deadlineTime)
        assertNull(w[2].targetTime)
        assertNull(w[2].deadlineTime)
        assertEquals(LocalTime.of(7, 5), w[3].targetTime)
        assertEquals(LocalTime.of(23, 59, 59), w[3].deadlineTime)
    }

    @Test
    fun eleBlankAndEnabledColumnOptional() {
        val r = WaypointCsv.parse("lat,lon,ele,name\n34.1,135.1,,A\n34.2,135.2,12.5,B\n")
        assertNull(r.waypoints[0].eleM)
        assertEquals(12.5, r.waypoints[1].eleM!!, 0.0)
        assertTrue(r.waypoints.all { it.enabled })
    }

    @Test
    fun enabledColumn() {
        val r = WaypointCsv.parse("lat,lon,enabled\n1,1,1\n1,1,0\n1,1,\n1,1,true\n1,1,FALSE\n")
        assertEquals(listOf(true, false, true, true, false), r.waypoints.map { it.enabled })
    }

    @Test
    fun bomColumnOrderAndExtraColumns() {
        val text = "\uFEFFname,memo,deadline_time,lon,lat\r\n京都,メモ,10:00,135.7588,34.9858\r\n"
        val r = WaypointCsv.parse(text.toByteArray(Charsets.UTF_8))
        val w = r.waypoints.single()
        assertEquals("京都", w.name)
        assertEquals(34.9858, w.lat, 0.0)
        assertEquals(135.7588, w.lon, 0.0)
        assertEquals(LocalTime.of(10, 0), w.deadlineTime)
    }

    @Test
    fun utf8WithoutBom() {
        val r = WaypointCsv.parse("lat,lon,name\n35.0,135.0,名古屋城\n".toByteArray(Charsets.UTF_8))
        assertEquals("名古屋城", r.waypoints.single().name)
    }

    @Test
    fun shiftJisJapaneseName() {
        // Excel（日本語版）が「CSV（コンマ区切り）」で保存したファイル
        val bytes = "lat,lon,name,target_time\r\n34.6937,135.5023,大阪駅・中央口,9:30\r\n".toByteArray(sjis)
        val r = WaypointCsv.parse(bytes)
        assertEquals(0, r.skippedLines)
        assertEquals("大阪駅・中央口", r.waypoints.single().name)
        assertEquals(LocalTime.of(9, 30), r.waypoints.single().targetTime)
    }

    @Test
    fun whitespaceBlankLinesAndMixedNewlines() {
        val text = "lat,lon,ele,name\r\n 34.1 , 135.1 , 10 ,A\n34.2,135.2,,B\r\n\r\n\n   \r\n"
        val r = WaypointCsv.parse(text)
        assertEquals(0, r.skippedLines)
        assertEquals(2, r.waypoints.size)
        assertEquals(34.1, r.waypoints[0].lat, 0.0)
        assertEquals(10.0, r.waypoints[0].eleM!!, 0.0)
    }

    @Test
    fun unreadableRowsAreSkippedAndCounted() {
        val csv = """
            lat,lon,ele,name,target_time,enabled
            ,135.0,,lat 空欄,,
            91.0,135.0,,緯度が範囲外,,
            34.0,181.0,,経度が範囲外,,
            34.0,135.0,abc,標高が数値でない,,
            34.0,135.0,,時刻が不正,25:00,
            34.0,135.0,,enabled が不正,,x
            34.0,135.0,,読める,,
        """.trimIndent()
        val r = WaypointCsv.parse(csv)
        assertEquals(6, r.skippedLines)
        assertEquals("読める", r.waypoints.single().name)
    }

    @Test
    fun radiusColumnIsOptional() {
        val r = WaypointCsv.parse("lat,lon,name,radius\n1,1,a,200\n1,1,b,\n1,1,c, 50.5 \n1,1,d,0\n1,1,e,abc\n")
        assertEquals(2, r.skippedLines) // 0 と abc は読めない
        assertEquals(listOf(200.0, null, 50.5), r.waypoints.map { it.radiusM })
        // 列がなければ全体の設定（null）
        assertNull(WaypointCsv.parse("lat,lon\n1,1\n").waypoints.single().radiusM)
    }

    @Test
    fun blankNameGetsDefault() {
        val r = WaypointCsv.parse("lat,lon,name\n1,1,\n2,2,B\n3,3,\n")
        assertEquals(listOf("WP1", "B", "WP3"), r.waypoints.map { it.name })
    }

    @Test
    fun emptyInput() {
        assertEquals(WaypointParseResult(emptyList(), 0), WaypointCsv.parse(""))
        assertEquals(WaypointParseResult(emptyList(), 0), WaypointCsv.parse("lat,lon\r\n"))
    }

    @Test
    fun fullWidthTimeFromJapaneseInput() {
        // 日本語入力のまま打った「１３：００」も読める
        assertEquals(LocalTime.of(13, 0), TimeText.parse("１３：００"))
        assertEquals(LocalTime.of(9, 5, 30), TimeText.parse(" ９:０５：３０ "))
        assertNull(TimeText.parse("２５：００"))
    }
}
