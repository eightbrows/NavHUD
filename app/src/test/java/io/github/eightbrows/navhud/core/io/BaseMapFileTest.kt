package io.github.eightbrows.navhud.core.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 背景地図のファイル（§8）: 読み書きの往復、知らない種類の線、壊れたファイル、
 * それと tools/gm2basemap.ps1 が作ってアプリに入れたファイル（assets/basemap.bin）の中身。
 */
class BaseMapFileTest {

    private fun line(layer: MapLayer, vararg latLon: Double) =
        MapLine(layer, DoubleArray(latLon.size / 2) { latLon[it * 2] }, DoubleArray(latLon.size / 2) { latLon[it * 2 + 1] })

    @Test
    fun encodeThenDecodeGivesTheSameLines() {
        val map = BaseMap(
            listOf(
                line(MapLayer.COASTLINE, 33.47649, 133.00275, 33.46679, 132.96175, 33.48378, 132.87221),
                line(MapLayer.PREF_BOUNDARY, 33.6, 132.9, 33.61, 132.91),
                // 西経・南緯（負の数）と、大きく離れた点（差が大きい）も読める
                line(MapLayer.COASTLINE, -10.0, -170.5, 45.5, 153.98),
            ),
        )
        val back = BaseMapFile.decode(BaseMapFile.encode(map))
        assertEquals(3, back.lines.size)
        for ((a, b) in map.lines.zip(back.lines)) {
            assertEquals(a.layer, b.layer)
            // 単位は 1e-5 度（約 1 m）。それより細かい所は丸める
            for (i in 0 until a.size) {
                assertEquals(a.lats[i], b.lats[i], 0.5e-5)
                assertEquals(a.lons[i], b.lons[i], 0.5e-5)
            }
        }
        // 範囲（最小・最大）は点から決まる
        val c = back.lines[0]
        assertEquals(33.46679, c.minLat, 1e-9)
        assertEquals(33.48378, c.maxLat, 1e-9)
        assertEquals(132.87221, c.minLon, 1e-9)
        assertEquals(133.00275, c.maxLon, 1e-9)
        assertEquals(2, back.countOf(MapLayer.COASTLINE))
        assertEquals(1, back.countOf(MapLayer.PREF_BOUNDARY))
    }

    /** 試験用に、形の説明（SPEC.md §8）どおりに手で書く */
    private fun handWritten(lines: List<Pair<Int, List<Pair<Int, Int>>>>, version: Int = 1, unit: Int = 100_000): ByteArray {
        val o = ByteArrayOutputStream()
        fun u8(v: Int) = o.write(v and 0xFF)
        fun i32(v: Int) { u8(v); u8(v shr 8); u8(v shr 16); u8(v shr 24) }
        fun uv(value: Long) { var v = value; while (v >= 0x80) { u8(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }; u8(v.toInt()) }
        fun sv(v: Long) = uv((v shl 1) xor (v shr 63))
        "NHBM".forEach { u8(it.code) }
        u8(version); i32(unit)
        u8(0)   // レイヤの一覧なし（読む側は使わない）
        i32(lines.size)
        for ((code, pts) in lines) {
            u8(code); uv(pts.size.toLong())
            val minLat = pts.minOf { it.first }.toLong(); val minLon = pts.minOf { it.second }.toLong()
            sv(minLat); sv(minLon); sv(pts.maxOf { it.first }.toLong()); sv(pts.maxOf { it.second }.toLong())
            var pLat = minLat; var pLon = minLon
            for ((la, lo) in pts) { sv(la - pLat); sv(lo - pLon); pLat = la.toLong(); pLon = lo.toLong() }
        }
        return o.toByteArray()
    }

    @Test
    fun readsTheFormatAsDescribed() {
        val bytes = handWritten(listOf(1 to listOf(3_347_649 to 13_300_275, 3_346_679 to 13_296_175)))
        val map = BaseMapFile.decode(bytes)
        val l = map.lines.single()
        assertEquals(MapLayer.COASTLINE, l.layer)
        assertArrayEquals(doubleArrayOf(33.47649, 33.46679), l.lats, 1e-9)
        assertArrayEquals(doubleArrayOf(133.00275, 132.96175), l.lons, 1e-9)
    }

    @Test
    fun unknownLayersAreSkipped() {
        // 新しい版で足した種類（ここでは 99）は飛ばし、その後ろの線は読める
        val bytes = handWritten(
            listOf(
                99 to listOf(1 to 1, 2 to 2, 3 to 3),
                2 to listOf(3_360_000 to 13_290_000, 3_361_000 to 13_291_000),
            ),
        )
        val map = BaseMapFile.decode(bytes)
        assertEquals(listOf(MapLayer.PREF_BOUNDARY), map.lines.map { it.layer })
        assertNull(MapLayer.of(99))
        assertEquals(MapLayer.RAILWAY, MapLayer.of(5))
    }

    @Test
    fun brokenFilesAreRejected() {
        fun rejects(bytes: ByteArray) {
            try {
                BaseMapFile.decode(bytes)
                fail("読めてしまった")
            } catch (e: BaseMapFile.FormatException) {
                // 期待どおり
            }
        }
        val good = handWritten(listOf(1 to listOf(1 to 1, 2 to 2)))
        rejects("XXXX".toByteArray() + good.copyOfRange(4, good.size))      // 先頭の印が違う
        rejects(handWritten(listOf(1 to listOf(1 to 1, 2 to 2)), version = 2))   // 読めない版
        rejects(good.copyOf(good.size - 3))                                 // 途中で終わっている
        rejects(ByteArray(0))
    }

    /** アプリに入れたファイル（tools/gm2basemap.ps1 で作ったもの）。単体テストはモジュールのフォルダで動く */
    private val asset = File("src/main/assets/basemap.bin")

    @Test
    fun bundledAssetHasCoastlinesAndPrefectureBorders() {
        assertTrue("assets/basemap.bin がない", asset.isFile)
        val map = BaseMapFile.decode(asset.readBytes())
        // 変換のとき（20261017-D01）の数: 海岸線 1,802 本・県境 791 本（どちらも 64 点ずつに分けたあとの数）
        assertEquals(1802, map.countOf(MapLayer.COASTLINE))
        assertEquals(791, map.countOf(MapLayer.PREF_BOUNDARY))
        assertEquals(map.lines.size, map.countOf(MapLayer.COASTLINE) + map.countOf(MapLayer.PREF_BOUNDARY))
        // どの線も 2〜64 点で、日本の範囲（地球地図日本の範囲: 北緯 20〜46 度、東経 122〜154 度）に入る
        for (l in map.lines) {
            assertTrue(l.size in 2..64)
            assertTrue(l.minLat >= 20.0 && l.maxLat <= 46.0 && l.minLon >= 122.0 && l.maxLon <= 154.0)
        }
        // 点の数（変換のときの数。分けたつなぎ目の点は 2 回数える）
        assertEquals(55_721 + (1802 - 1204), map.lines.filter { it.layer == MapLayer.COASTLINE }.sumOf { it.size })
        // サンプルの記録のあたり（高知県・愛媛県）: 土佐湾の海岸線と、愛媛県と高知県の県境の線がある
        fun near(lat: Double, lon: Double, layer: MapLayer, km: Double) = map.lines.any { l ->
            l.layer == layer && (0 until l.size).any { i -> io.github.eightbrows.navhud.core.geo.Geo.distanceM(lat, lon, l.lats[i], l.lons[i]) < km * 1000 }
        }
        assertTrue(near(33.50, 133.30, MapLayer.COASTLINE, 15.0))       // 須崎のあたり
        assertTrue(near(33.60, 132.95, MapLayer.PREF_BOUNDARY, 20.0))   // サンプルの終点（愛媛県久万高原町）のあたり
    }
}
