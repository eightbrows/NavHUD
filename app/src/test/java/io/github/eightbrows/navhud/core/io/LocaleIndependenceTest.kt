package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.view.HudFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * 端末の言語で書き方が変わらないこと（§6.11）: 小数点は「.」、数字は 0-9、時刻は HH:MM:SS。
 * WP の CSV は、どの言語の端末で書き出しても同じ中身になり、同じように読める。
 */
class LocaleIndependenceTest {

    private val saved = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(saved)

    /** 小数点が「,」のドイツ語と、数字がタイ数字になるタイ語（数字の体系つき）。 */
    private val locales = listOf(Locale.US, Locale.JAPAN, Locale.GERMANY, Locale.forLanguageTag("th-TH-u-nu-thai"))

    private val wps = listOf(
        Waypoint("大阪駅", 34.6937, 135.5023, 15.5, LocalTime.of(9, 30), LocalTime.of(10, 0, 30)),
        Waypoint("B", -33.8688, 151.2093, null, null, null, enabled = false, radiusM = 250.5),
    )

    @Test
    fun waypointCsvIsTheSameInEveryLocale() {
        Locale.setDefault(Locale.US)
        val reference = WaypointCsv.encode(wps).toString(Charsets.UTF_8)
        for (l in locales) {
            Locale.setDefault(l)
            val text = WaypointCsv.encode(wps).toString(Charsets.UTF_8)
            assertEquals("$l", reference, text)
            assertEquals("$l", wps, WaypointCsv.parse(text).waypoints)
        }
    }

    @Test
    fun csvWrittenInOneLocaleReadsInAnother() {
        for (writer in locales) for (reader in locales) {
            Locale.setDefault(writer)
            val bytes = WaypointCsv.encode(wps)
            Locale.setDefault(reader)
            assertEquals("$writer -> $reader", wps, WaypointCsv.parse(bytes).waypoints)
        }
    }

    @Test
    fun hudNumbersAndTimesDoNotDependOnLocale() {
        val zone = ZoneId.of("Asia/Tokyo")
        for (l in locales) {
            Locale.setDefault(l)
            assertEquals("$l", "1.17 km", HudFormat.distance(1170.0))
            assertEquals("$l", "062°", HudFormat.bearing(62.0))
            assertEquals("$l", "34.69370, 135.50230", HudFormat.latLon(34.6937, 135.5023))
            assertEquals("$l", "08:00:21", HudFormat.time(1_786_143_621_000, zone))
            assertEquals("$l", "+0:10:05", HudFormat.countdown(605))
            assertEquals("$l", "09:05:30", TimeText.format(LocalTime.of(9, 5, 30)))
            assertEquals("$l", "34.6937, 135.5023", CoordinateText.format(34.6937, 135.5023))
        }
    }
}
