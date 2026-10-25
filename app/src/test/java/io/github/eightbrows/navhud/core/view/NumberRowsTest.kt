package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.nav.AglNegative
import io.github.eightbrows.navhud.core.nav.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 数値の表示の4行（§6.1。20261025-D09 の並び）と、欄の幅の見本、入りきらないときの縮め方。 */
class NumberRowsTest {

    @Test
    fun rowsAreTimeAndWpOnTheLeftOwnShipOnTheRight() {
        assertEquals(4, NumberRows.ROWS.size)
        assertEquals(listOf(NumberField.TIME, NumberField.LAT_LON), NumberRows.ROWS[0])
        assertEquals(listOf(NumberField.ETA, NumberField.GS, NumberField.HDG), NumberRows.ROWS[1])
        assertEquals(listOf(NumberField.TGT, NumberField.ALT, NumberField.AGL), NumberRows.ROWS[2])
        assertEquals(listOf(NumberField.DDL, NumberField.RATE), NumberRows.ROWS[3])
        // 左の列は時刻と WP の欄だけ
        assertEquals(listOf(NumberField.TIME, NumberField.ETA, NumberField.TGT, NumberField.DDL), NumberRows.ROWS.map { it[0] })
        assertFalse(NumberRows.ROWS.flatten().any { it.caption == "NEXT" })
        // どの欄も1回だけ。見本は全部の欄にある
        assertEquals(NumberField.entries.toSet(), NumberRows.ROWS.flatten().toSet())
        assertEquals(NumberField.entries.size, NumberRows.ROWS.flatten().size)
        assertEquals(NumberField.entries.toSet(), NumberRows.SAMPLES.keys)
    }

    @Test
    fun samplesAreTheLongestValues() {
        // 見本の幅を必ず取る（等幅なので文字数で比べる）。実際の値が見本より長くならないこと
        fun sample(f: NumberField) = NumberRows.SAMPLES.getValue(f)
        fun fits(f: NumberField, s: String) = assertTrue("$f: $s", s.length <= sample(f).value.length)
        val zone = ZoneId.of("Asia/Tokyo")
        // TIME・ETA: HH:MM:SS（秒まで）
        val t = HudFormat.time(1_786_661_555_000, zone)
        assertTrue(t, Regex("""\d\d:\d\d:\d\d""").matches(t))
        assertEquals(sample(NumberField.TIME).value.length, t.length)
        fits(NumberField.ETA, t)
        // TGT・DDL: ±12 時間まで
        for (s in listOf(12 * 3600L - 1, -(12 * 3600L - 1), 0L)) {
            fits(NumberField.TGT, HudFormat.countdown(s))
            fits(NumberField.DDL, HudFormat.countdown(s))
        }
        // LAT/LON: 日本の緯度経度（南鳥島・与那国島・北の端）
        for ((lat, lon) in listOf(24.28333 to 153.98333, 24.44972 to 122.93361, 45.55722 to 148.75222)) fits(NumberField.LAT_LON, HudFormat.latLon(lat, lon))
        // GS・HDG
        fits(NumberField.GS, HudFormat.speedKmh(999f / 3.6f))
        fits(NumberField.HDG, HudFormat.bearing(359.4))
        // ALT: 日本の最高地点・海面下も見本以内
        for (m in listOf(3_776.0, -99.0, 0.0)) fits(NumberField.ALT, HudFormat.altitude(m))
        // AGL: 負の 3 桁（負のまま出す設定）と、正の 3 桁
        for (m in listOf(-999.0, 999.0, 0.0)) fits(NumberField.AGL, HudFormat.agl(m, AglNegative.SHOW).text)
        // RATE: 60 秒の窓で 10 km 近く（時速 600 km）・高低差 3 桁まで
        for (r in listOf(Rate(60, 9_994.0, -999.0, 166.0), Rate(60, 0.0, 0.0, 0.0), Rate(60, 1_234.0, null, 20.0))) {
            fits(NumberField.RATE, HudFormat.rate(r))
        }
        // RATE の見出しは窓の秒数を付けても長さが同じ（10s / 30s / 60s）
        assertTrue(listOf(10, 30, 60).all { "${NumberField.RATE.caption} ${it}s".length == sample(NumberField.RATE).caption.length })
        // HDG の小さな文字: 方位ソース 1 つとコンパスの印 1 つ
        assertTrue(listOf("GPS", "HLD", "CMP").all { " $it CAL".length <= sample(NumberField.HDG).small.length })
    }

    @Test
    fun fitsWithoutShrinkingWhenWide() {
        val needs = listOf(listOf(60f, 150f), listOf(50f, 70f, 60f), listOf(70f, 40f, 40f), listOf(70f, 120f))
        val r = NumberLayout.fit(needs, availDp = 340f, gapDp = 10f)
        assertEquals(1f, r.scale, 0f)
        // 左の列は全部の行で同じ（いちばん幅のある 70）
        assertTrue(r.widths.all { it[0] == 70f })
        // 余りは右の欄に同じだけ分け、行の幅はちょうど使える幅
        assertEquals(listOf(70f, 260f), r.widths[0])
        // 2行目: 余り 340 − 20 − 70 − 130 = 120 を右の 2 つに 60 ずつ
        assertEquals(listOf(70f, 130f, 120f), r.widths[1])
        assertEquals(listOf(70f, 125f, 125f), r.widths[2])
        for ((i, row) in r.widths.withIndex()) assertEquals("row $i", 340f, row.sum() + 10f * (row.size - 1), 1e-3f)
        // 右の欄は要る幅より狭くならない
        for ((i, row) in needs.withIndex()) for (j in 1 until row.size) assertTrue(r.widths[i][j] >= row[j])
    }

    @Test
    fun shrinksAllRowsByTheSameScaleWhenNarrow() {
        // 2行目がいちばん入りきらない: 左 100 + 右 120 + 120、すき間 2 つ
        val needs = listOf(listOf(100f, 150f), listOf(80f, 120f, 120f), listOf(90f, 60f, 60f))
        val r = NumberLayout.fit(needs, availDp = 300f, gapDp = 10f)
        val expected = (300f - 20f) / (100f + 240f)
        assertEquals(expected, r.scale, 1e-5f)
        assertTrue(r.scale < 1f)
        // 左の列は縮めた幅でそろう
        assertTrue(r.widths.all { kotlin.math.abs(it[0] - 100f * expected) < 1e-3f })
        // いちばん入りきらない行は、余りなしでちょうど入る
        assertEquals(120f * expected, r.widths[1][1], 1e-3f)
        assertEquals(120f * expected, r.widths[1][2], 1e-3f)
        // どの行も使える幅を超えない
        for (row in r.widths) assertTrue(row.sum() + 10f * (row.size - 1) <= 300f + 1e-3f)
    }

    @Test
    fun singleCellRowTakesTheWholeWidth() {
        val r = NumberLayout.fit(listOf(listOf(80f), listOf(60f, 100f)), availDp = 200f, gapDp = 10f)
        assertEquals(1f, r.scale, 0f)
        assertEquals(listOf(200f), r.widths[0])
        assertEquals(listOf(80f, 110f), r.widths[1])
    }
}
