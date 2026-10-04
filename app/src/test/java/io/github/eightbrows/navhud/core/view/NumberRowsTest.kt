package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.nav.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 数値の表示の4行（§6.1）と、1行目が切れないための幅の見本。 */
class NumberRowsTest {

    @Test
    fun thirdRowIsTargetAndDeadlineOnly() {
        // 3行目は TGT・DDL だけ（NEXT はなくした。次の WP の名前・方位・距離は地図上の WP の文字で見る）
        assertEquals(listOf(NumberField.TGT, NumberField.DDL), NumberRows.ROWS[2])
        assertEquals(4, NumberRows.ROWS.size)
        assertEquals(listOf(NumberField.TIME, NumberField.ALT, NumberField.RATE), NumberRows.ROWS[0])
        assertEquals(listOf(NumberField.HDG, NumberField.GS, NumberField.ETA), NumberRows.ROWS[1])
        assertEquals(listOf(NumberField.LAT_LON), NumberRows.ROWS[3])
        assertFalse(NumberRows.ROWS.flatten().any { it.caption == "NEXT" })
        // どの欄も1回だけ
        assertEquals(NumberField.entries.toSet(), NumberRows.ROWS.flatten().toSet())
        assertEquals(NumberField.entries.size, NumberRows.ROWS.flatten().size)
    }

    @Test
    fun firstRowSamplesAreTheLongestValues() {
        // 1行目は、見本の幅を必ず取る（等幅なので文字数で比べる）。実際の値が見本より長くならないこと
        val (time, alt, rate) = Tuning.NUMBERS_ROW1_SAMPLES
        assertEquals(listOf("TIME", "ALT", "RATE 60s"), listOf(time.first, alt.first, rate.first))
        assertEquals(Tuning.NUMBERS_ROW1_WEIGHTS.size, Tuning.NUMBERS_ROW1_SAMPLES.size)
        val zone = ZoneId.of("Asia/Tokyo")
        // TIME: HH:MM:SS（秒まで）
        val t = HudFormat.time(1_786_661_555_000, zone)
        assertTrue(t, Regex("""\d\d:\d\d:\d\d""").matches(t))
        assertEquals(time.second.length, t.length)
        // ALT: 日本の最高地点・海面下も見本以内
        for (m in listOf(3_776.0, -99.0, 0.0)) assertTrue(HudFormat.altitude(m).length <= alt.second.length)
        // RATE: 60 秒の窓で 10 km 近く（時速 600 km）・高低差 3 桁まで
        for (r in listOf(Rate(60, 9_994.0, -999.0, 166.0), Rate(60, 0.0, 0.0, 0.0), Rate(60, 1_234.0, null, 20.0))) {
            val s = HudFormat.rate(r)
            assertTrue(s, s.length <= rate.second.length)
        }
        // RATE の見出しは窓の秒数を付けても長さが同じ（10s / 30s / 60s）
        assertTrue(listOf(10, 30, 60).all { "${NumberField.RATE.caption} ${it}s".length == rate.first.length })
    }
}
