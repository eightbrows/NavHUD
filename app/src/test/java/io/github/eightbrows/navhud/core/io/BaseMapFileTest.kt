package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.geo.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 背景地図のファイル（§8）: 読み書きの往復（段・レイヤの中の種類・点）、版 1 と版 2 の形、知らない種類、壊れたファイル、
 * それと tools/gm2basemap.ps1 が作ってアプリに入れたファイル（assets/basemap.bin）の中身。
 */
class BaseMapFileTest {

    private fun line(layer: MapLayer, kind: Int, vararg latLon: Double) =
        MapLine.of(layer, DoubleArray(latLon.size / 2) { latLon[it * 2] }, DoubleArray(latLon.size / 2) { latLon[it * 2 + 1] }, kind)

    @Test
    fun encodeThenDecodeGivesTheSameLevelsLinesAndPoints() {
        val map = BaseMap(
            listOf(
                MapLevel(0.0, listOf(
                    line(MapLayer.COASTLINE, 1, 33.4765, 133.0028, 33.4668, 132.9617, 33.4838, 132.8722),
                    line(MapLayer.ROAD, 15, 33.6, 132.9, 33.61, 132.91),
                    // 西経・南緯（負の数）と、大きく離れた点（差が大きい）も読める
                    line(MapLayer.RAILWAY, 2, -10.0, -170.5, 45.5, 153.98),
                )),
                MapLevel(25_000.0, listOf(line(MapLayer.COASTLINE, 1, 33.4765, 133.0028, 33.4838, 132.8722))),
            ),
            listOf(
                MapPoint(MapLayer.STATION, 0, 33.8392, 132.7511, "MATSUYAMA"),
                MapPoint(MapLayer.CITY, 0, 33.5597, 133.5311, ""),
            ),
        )
        val back = BaseMapFile.decode(BaseMapFile.encode(map))
        assertEquals(listOf(0.0, 25_000.0), back.levels.map { it.minR1M })
        for ((la, lb) in map.levels.zip(back.levels)) {
            assertEquals(la.lines.size, lb.lines.size)
            for ((a, b) in la.lines.zip(lb.lines)) {
                assertEquals(a.layer, b.layer)
                assertEquals(a.kind, b.kind)
                // 単位は 1e-4 度（約 11 m）。それより細かい所は丸める
                for (i in 0 until a.size) {
                    assertEquals(a.lat(i), b.lat(i), 0.5e-4)
                    assertEquals(a.lon(i), b.lon(i), 0.5e-4)
                }
            }
        }
        // 範囲（最小・最大）は点から決まる
        val c = back.lines[0]
        assertEquals(33.4668, c.minLat, 1e-9)
        assertEquals(33.4838, c.maxLat, 1e-9)
        assertEquals(132.8722, c.minLon, 1e-9)
        assertEquals(133.0028, c.maxLon, 1e-9)
        // 点は名前ごと戻る
        assertEquals(listOf("MATSUYAMA", ""), back.points.map { it.name })
        assertEquals(listOf(MapLayer.STATION, MapLayer.CITY), back.points.map { it.layer })
        assertEquals(33.8392, back.points[0].lat, 0.5e-4)
        assertEquals(1, back.countOf(MapLayer.STATION))
        assertEquals(2, back.countOf(MapLayer.COASTLINE) + back.countOf(MapLayer.ROAD))
    }

    /** 試験用に、形の説明（SPEC.md §8・tools/gm2basemap.ps1）どおりに手で書く */
    private class Hand {
        val o = ByteArrayOutputStream()
        fun u8(v: Int) = o.write(v and 0xFF)
        fun i32(v: Int) { u8(v); u8(v shr 8); u8(v shr 16); u8(v shr 24) }
        fun uv(value: Long) { var v = value; while (v >= 0x80) { u8(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }; u8(v.toInt()) }
        fun sv(v: Long) = uv((v shl 1) xor (v shr 63))
        fun head(version: Int, unit: Int) { "NHBM".forEach { u8(it.code) }; u8(version); i32(unit); u8(0) }
    }

    @Test
    fun readsVersion1AsDescribed() {
        // 版 1（20261017-D01）: 段は 1 つ、種類なし、範囲は最小・最大のそのままの値、点なし。単位 1e-5 度
        val h = Hand()
        h.head(1, 100_000)
        h.i32(1)
        h.u8(1); h.uv(2)
        h.sv(3_346_679); h.sv(13_296_175); h.sv(3_347_649); h.sv(13_300_275)
        h.sv(3_347_649 - 3_346_679); h.sv(13_300_275 - 13_296_175)   // 1 点目: 範囲の最小からの差
        h.sv(3_346_679 - 3_347_649); h.sv(13_296_175 - 13_300_275)   // 2 点目: 前の点からの差
        val map = BaseMapFile.decode(h.o.toByteArray())
        assertEquals(1, map.levels.size)
        val l = map.lines.single()
        assertEquals(MapLayer.COASTLINE, l.layer)
        assertEquals(33.47649, l.lat(0), 1e-9)
        assertEquals(133.00275, l.lon(0), 1e-9)
        assertEquals(33.46679, l.lat(1), 1e-9)
        assertTrue(map.points.isEmpty())
    }

    @Test
    fun readsVersion2AsDescribed() {
        // 版 2: 段ごとに、レイヤごとのかたまり（種類の番号・線の数・バイト数）。線の範囲の最小は同じかたまりの前の線からの差、最大は幅。
        // 点は前の点からの差と名前
        val road = Hand()   // かたまりの中身（道路の線 1 本）
        road.u8(15); road.uv(2)                                     // rtt 15、2 点
        road.sv(334_000); road.sv(1_329_000); road.uv(10); road.uv(20)   // 最小（かたまりの最初の線なので 0 からの差）と幅
        road.sv(0); road.sv(0); road.sv(10); road.sv(20)            // (33.4, 132.9) → (33.401, 132.902)
        val pref = Hand()   // 県境の線 2 本
        pref.u8(1); pref.uv(2); pref.sv(334_100); pref.sv(1_328_000); pref.uv(5); pref.uv(5)
        pref.sv(5); pref.sv(0); pref.sv(-5); pref.sv(5)             // (33.4105, 132.8) → (33.41, 132.8005)
        pref.u8(1); pref.uv(2); pref.sv(100); pref.sv(-1000); pref.uv(1); pref.uv(1)   // 最小は前の線の最小からの差: (33.42, 132.7)
        pref.sv(0); pref.sv(0); pref.sv(1); pref.sv(1)
        val h = Hand()
        h.head(2, 10_000)
        h.u8(2)                                                     // 段の数
        h.i32(0); h.u8(2)                                           // 段 0: かたまり 2 つ
        h.u8(4); h.i32(1); h.i32(road.o.size()); h.o.write(road.o.toByteArray())
        h.u8(2); h.i32(2); h.i32(pref.o.size()); h.o.write(pref.o.toByteArray())
        h.i32(25_000); h.u8(0)                                      // 段 1: かたまりなし
        h.i32(2)                                                    // 点 2 つ
        h.u8(22); h.u8(0); h.sv(338_392); h.sv(1_327_511); h.uv(1); h.u8('M'.code)
        h.u8(24); h.u8(0); h.sv(-10); h.sv(10); h.uv(0)            // 前の点からの差
        val map = BaseMapFile.decode(h.o.toByteArray())
        assertEquals(listOf(0.0, 25_000.0), map.levels.map { it.minR1M })
        // かたまりは読む前でも、線の数は分かる
        assertEquals(1, map.countOf(MapLayer.ROAD))
        assertEquals(2, map.countOf(MapLayer.PREF_BOUNDARY))
        val (r, p1, p2) = map.lines
        assertEquals(MapLayer.ROAD, r.layer)
        assertEquals(15, r.kind)
        assertEquals(33.4, r.lat(0), 1e-9)
        assertEquals(132.902, r.lon(1), 1e-9)
        assertEquals(MapLayer.PREF_BOUNDARY, p1.layer)
        assertEquals(33.4105, p1.lat(0), 1e-9)
        assertEquals(132.8005, p1.lon(1), 1e-9)
        assertEquals(33.42, p2.minLat, 1e-9)
        assertEquals(132.7, p2.minLon, 1e-9)
        assertEquals(listOf("M", ""), map.points.map { it.name })
        assertEquals(33.8382, map.points[1].lat, 1e-9)
        assertEquals(132.7521, map.points[1].lon, 1e-9)
    }

    @Test
    fun unknownLayersAreSkipped() {
        // 新しい版で足した種類（ここでは 99）のかたまり・点は飛ばし、その後ろは読める
        val seg = Hand()
        seg.u8(0); seg.uv(2); seg.sv(0); seg.sv(0); seg.uv(1); seg.uv(1); seg.sv(0); seg.sv(0); seg.sv(1); seg.sv(1)
        val h = Hand()
        h.head(2, 10_000)
        h.u8(1); h.i32(0); h.u8(2)
        h.u8(99); h.i32(1); h.i32(seg.o.size()); h.o.write(seg.o.toByteArray())
        h.u8(1); h.i32(1); h.i32(seg.o.size()); h.o.write(seg.o.toByteArray())
        h.i32(2)
        h.u8(99); h.u8(0); h.sv(5); h.sv(5); h.uv(0)
        h.u8(20); h.u8(4); h.sv(5); h.sv(5); h.uv(0)
        val map = BaseMapFile.decode(h.o.toByteArray())
        assertEquals(listOf(MapLayer.COASTLINE), map.lines.map { it.layer })
        assertEquals(listOf(MapLayer.AIRPORT), map.points.map { it.layer })
        assertEquals(4, map.points.single().kind)
        assertNull(MapLayer.of(99))
        assertEquals(MapLayer.CITY, MapLayer.of(24))
    }

    @Test
    fun segmentsAreReadOnlyWhenNeeded() {
        // アプリに入れたファイル: 読み込んだ直後は、どのかたまりも中身を読んでいない（見出しと点だけ）
        val map = BaseMapFile.decode(asset.readBytes())
        assertTrue(map.levels.all { l -> l.segments.none { it.isLoaded } })
        assertEquals(1779, map.countOf(MapLayer.COASTLINE))   // 線の数は読まずに分かる
        // 描くレイヤだけを読む（初期値: 海岸線と県境）
        map.preload(setOf(MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY))
        for (level in map.levels) for (s in level.segments) assertEquals(s.layer.name, s.layer in setOf(MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY), s.isLoaded)
        // 読んでいないレイヤは、画面の側からは空に見える（読み終わるのを待たない）
        val level = map.levelFor(250.0)!!
        assertTrue(level.candidates(MapLayer.ROAD, 33.0, 132.5, 34.0, 133.5).isEmpty())
        assertTrue(level.candidates(MapLayer.COASTLINE, 33.0, 132.5, 34.0, 133.5).isNotEmpty())
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
        val good = BaseMapFile.encode(BaseMap(listOf(line(MapLayer.COASTLINE, 0, 1.0, 1.0, 2.0, 2.0))))
        rejects("XXXX".toByteArray() + good.copyOfRange(4, good.size))   // 先頭の印が違う
        rejects(good.copyOf().also { it[4] = 3 })                       // 読めない版
        rejects(good.copyOf(good.size - 3))                             // 途中で終わっている
        rejects(ByteArray(0))
    }

    /** アプリに入れたファイル（tools/gm2basemap.ps1 で作ったもの）。単体テストはモジュールのフォルダで動く */
    private val asset = File("src/main/assets/basemap.bin")

    @Test
    fun bundledAssetHasAllLayersAndThinnedLevels() {
        assertTrue("assets/basemap.bin がない", asset.isFile)
        val map = BaseMapFile.decode(asset.readBytes())
        // 段: 詳細用（間引かない）・R1 25 km 以上用・R1 50 km 以上用・R1 100 km 以上用
        assertEquals(listOf(0.0, 25_000.0, 50_000.0, 100_000.0), map.levels.map { it.minR1M })
        // 変換のとき（20261017-D02。河川・湖沼などは 20261018-D04 で大きさの段を入れた）の数: 段 0 の線（64 点ずつに分けたあと）と点
        val expectLines = mapOf(
            MapLayer.COASTLINE to 1779, MapLayer.PREF_BOUNDARY to 278, MapLayer.RIVER to 547, MapLayer.ROAD to 31428,
            MapLayer.RAILWAY to 3112, MapLayer.MUNI_BOUNDARY to 4220, MapLayer.FERRY to 540, MapLayer.DAM_LINE to 29,
            MapLayer.INLAND_WATER to 894, MapLayer.BUILT_UP_AREA to 249,
        )
        for ((layer, n) in expectLines) assertEquals(layer.name, n, map.countOf(layer))
        val expectPoints = mapOf(MapLayer.AIRPORT to 92, MapLayer.PORT to 128, MapLayer.STATION to 108, MapLayer.DAM_POINT to 271, MapLayer.CITY to 1640)
        for ((layer, n) in expectPoints) assertEquals(layer.name, n, map.countOf(layer))
        // 点の数は、間引いた段ほど少ない（どのレイヤでも）
        fun points(level: MapLevel, layer: MapLayer) = level.byLayer[layer].orEmpty().sumOf { it.size }
        for (layer in expectLines.keys) {
            val (a, b, c, d) = map.levels.map { points(it, layer) }
            assertTrue("${layer.name} $a $b $c $d", a >= b && b >= c && c >= d && d > 0)
        }
        // どの線・点も 2〜64 点で、日本の範囲（北緯 20〜46 度、東経 122〜154 度）に入る
        for (level in map.levels) for (l in level.lines) {
            assertTrue(l.size in 2..64)
            assertTrue(l.minLat >= 20.0 && l.maxLat <= 46.0 && l.minLon >= 122.0 && l.maxLon <= 154.0)
        }
        assertTrue(map.points.all { it.lat in 20.0..46.0 && it.lon in 122.0..154.0 })
        // 名前はローマ字（例: 駅に HAMAMATSU）
        assertTrue(map.points.any { it.layer == MapLayer.STATION && it.name == "HAMAMATSU" })
        // サンプルの記録のあたり（高知県・愛媛県）に、海岸線・県境・道路がある
        fun near(lat: Double, lon: Double, layer: MapLayer, km: Double) = map.lines.any { l ->
            l.layer == layer && (0 until l.size).any { i -> Geo.distanceM(lat, lon, l.lat(i), l.lon(i)) < km * 1000 }
        }
        assertTrue(near(33.50, 133.30, MapLayer.COASTLINE, 15.0))       // 須崎のあたり
        assertTrue(near(33.60, 132.95, MapLayer.PREF_BOUNDARY, 20.0))   // サンプルの終点（愛媛県久万高原町）のあたり
        assertTrue(near(33.4741, 133.0003, MapLayer.ROAD, 3.0))         // サンプルの記録の始まり
        // 道路は rtt 14・15・16 だけ
        for (level in map.levels) assertTrue(level.byLayer[MapLayer.ROAD].orEmpty().all { it.kind in 14..16 })
        // 河川・湖沼などの種類は大きさの段（1 = 小・2 = 中・3 = 大）。R1 50 km 以上の段は中・大だけ、100 km 以上の段は大だけ
        for ((i, level) in map.levels.withIndex()) {
            val min = when (i) { 2 -> 2; 3 -> 3; else -> 1 }
            for (layer in listOf(MapLayer.RIVER, MapLayer.INLAND_WATER)) {
                val kinds = level.byLayer[layer].orEmpty().map { it.kind }.toSet()
                assertTrue("段 $i ${layer.name} $kinds", kinds.isNotEmpty() && kinds.all { it in min..3 })
            }
        }
        // 道路は、広域の段でも短い線を捨てない（段 3 の線の数は、段 0 のつないだ線の数と同じ。20261018-D04 の変換のとき）
        assertEquals(30_251, map.levels[3].countOf(MapLayer.ROAD))
    }
}
