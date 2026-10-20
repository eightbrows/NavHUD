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
                MapPoint(MapLayer.AIRPORT, 4, 33.8272, 132.6997, "MATSUYAMA"),
                MapPoint(MapLayer.AIRPORT, 0, 33.5461, 133.6694, ""),
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
        assertEquals(listOf(MapLayer.AIRPORT, MapLayer.AIRPORT), back.points.map { it.layer })
        assertEquals(listOf(4, 0), back.points.map { it.kind })
        assertEquals(33.8272, back.points[0].lat, 0.5e-4)
        assertEquals(2, back.countOf(MapLayer.AIRPORT))
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
        h.u8(20); h.u8(4); h.sv(338_392); h.sv(1_327_511); h.uv(1); h.u8('M'.code)   // 空港（use 4）
        h.u8(20); h.u8(0); h.sv(-10); h.sv(10); h.uv(0)            // 前の点からの差
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
        assertEquals(listOf(4, 0), map.points.map { it.kind })
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
        assertEquals(MapLayer.AIRPORT, MapLayer.of(20))
        // 入れなくなった市街地（10）・港・駅・ダムと堰の点・都市（21〜24。20261020-D01）、ダム・堰の線（8。20261020-D02）も、知らない番号として飛ばす
        for (code in listOf(8, 10, 21, 22, 23, 24)) assertNull(MapLayer.of(code))
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
        // 変換のとき（20261017-D02。河川・湖沼などは 20261018-D05 で水系・幅のある川の線を入れた。鉄道は 20261019-D02 で新幹線の印が変わる所で切った）の数: 段 0 の線（64 点ずつに分けたあと）と点
        val expectLines = mapOf(
            MapLayer.COASTLINE to 1779, MapLayer.PREF_BOUNDARY to 278, MapLayer.RIVER to 764, MapLayer.ROAD to 31428,
            MapLayer.RAILWAY to 3142, MapLayer.MUNI_BOUNDARY to 4220, MapLayer.FERRY to 540,
            MapLayer.INLAND_WATER to 899,
        )
        for ((layer, n) in expectLines) assertEquals(layer.name, n, map.countOf(layer))
        // 点は空港だけ（港・駅・ダムと堰の点・都市と、市街地の輪郭は 20261020-D01 で入れなくなった）
        assertEquals(92, map.countOf(MapLayer.AIRPORT))
        assertTrue(map.points.all { it.layer == MapLayer.AIRPORT })
        assertEquals(MapLayer.entries.toSet(), map.levels[0].byLayer.keys + MapLayer.AIRPORT)
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
        // 名前はローマ字（例: 空港に WAKKANAI）
        assertTrue(map.points.any { it.layer == MapLayer.AIRPORT && it.name == "WAKKANAI" })
        // サンプルの記録のあたり（高知県・愛媛県）に、海岸線・県境・道路がある
        fun near(lat: Double, lon: Double, layer: MapLayer, km: Double) = map.lines.any { l ->
            l.layer == layer && (0 until l.size).any { i -> Geo.distanceM(lat, lon, l.lat(i), l.lon(i)) < km * 1000 }
        }
        assertTrue(near(33.50, 133.30, MapLayer.COASTLINE, 15.0))       // 須崎のあたり
        assertTrue(near(33.60, 132.95, MapLayer.PREF_BOUNDARY, 20.0))   // サンプルの終点（愛媛県久万高原町）のあたり
        assertTrue(near(33.4741, 133.0003, MapLayer.ROAD, 3.0))         // サンプルの記録の始まり
        // 道路は rtt 14・15・16 と、その他の自動車専用道路（高速道路のうち、まとまりが 20 km 未満）の 17
        for (level in map.levels) {
            val kinds = level.byLayer[MapLayer.ROAD].orEmpty().map { it.kind }.toSet()
            assertEquals(setOf(14, 15, 16, 17), kinds)
        }
        // カーブの多いまとまり（100 km 未満で、1 km あたり 80 度以上）も 17（20261018-D07）: 伊豆スカイラインは 17。
        // 大きな網の中の山の高速道路（中央道の恵那山のあたり・中国道）は 16 のまま
        fun motorwayKindsNear(lat: Double, lon: Double) = map.lines.filter { l ->
            l.layer == MapLayer.ROAD && l.kind >= 16 && (0 until l.size).any { i -> Geo.distanceM(lat, lon, l.lat(i), l.lon(i)) < 300 }
        }.map { it.kind }.toSet()
        assertEquals(setOf(17), motorwayKindsNear(35.0662, 138.9983))
        assertEquals(setOf(16), motorwayKindsNear(35.4572, 137.4468))
        assertEquals(setOf(16), motorwayKindsNear(35.0109, 133.5008))
        // 大きな網の枝（片方の端だけで網につながる部分）のうち、2〜100 km で 1 km あたり 100 度以上のものも 17（20261019-D01）
        fun kindAt(lat: Double, lon: Double) = map.lines.filter { l ->
            l.layer == MapLayer.ROAD && l.kind >= 16 && (0 until l.size).any { i -> Geo.distanceM(lat, lon, l.lat(i), l.lon(i)) < 50 }
        }.map { it.kind }.toSet()
        val curvyBranches = mapOf(
            "六甲のドライブウェイ" to (34.7626 to 135.2236), "箱根ターンパイク" to (35.2153 to 139.1015),
            "指宿スカイライン" to (31.3666 to 130.5058), "白山白川郷ホワイトロード" to (36.2619 to 136.8324),
            "日本平のあたり" to (34.9702 to 138.4590), "山梨北部（小淵沢のあたり）" to (35.8989 to 138.3602),
        )
        for ((name, p) in curvyBranches) assertEquals(name, setOf(17), kindAt(p.first, p.second))
        // 本当の高速道路（通り道）と、網の枝でも曲がりの少ない支線・空港線は 16 のまま
        val realMotorways = mapOf(
            "上信越道（碓氷のあたり）" to (36.3097 to 138.6829), "東海北陸道（白川郷の北）" to (36.5938 to 136.9020),
            "東海北陸道（白川郷の南）" to (35.9814 to 136.9023), "磐越道" to (37.5369 to 140.0274),
            "関西空港連絡橋" to (34.4150 to 135.2925), "新空港自動車道（成田）" to (35.7830 to 140.3729),
            "西九州道（佐世保のあたり）の枝" to (33.1594 to 129.8661), "阪神高速（堺のあたり）の枝" to (34.6407 to 135.4861),
        )
        for ((name, p) in realMotorways) assertEquals(name, setOf(16), kindAt(p.first, p.second))
        // 鉄道: 新幹線の印（0x10）と、山形・秋田の印（0x20）は変換で付ける（20261019-D02）。どの段にも新幹線と在来線がある
        for (level in map.levels) {
            val rail = level.byLayer[MapLayer.RAILWAY].orEmpty()
            assertTrue(rail.any { RailKind.isShinkansen(it.kind) } && rail.any { !RailKind.isShinkansen(it.kind) })
        }
        fun railKindsAt(lat: Double, lon: Double) = map.lines.filter { l ->
            l.layer == MapLayer.RAILWAY && (0 until l.size).any { i -> Geo.distanceM(lat, lon, l.lat(i), l.lon(i)) < 50 }
        }.map { it.kind }.toSet()
        val shinkansen = mapOf("東海道新幹線（浜松の西）" to (34.71093 to 137.87790), "東北新幹線（仙台の北）" to (38.24120 to 140.89430))
        for ((name, p) in shinkansen) railKindsAt(p.first, p.second).let { assertTrue(name, it.isNotEmpty() && it.all { k -> RailKind.isShinkansen(k) }) }
        // 新幹線と並んで走る在来線は在来線のまま
        val conventional = mapOf("東海道線（浜松の西）" to (34.71020 to 137.83330), "東北線（仙台）" to (38.23200 to 140.88969))
        for ((name, p) in conventional) railKindsAt(p.first, p.second).let { assertTrue(name, it.isNotEmpty() && it.none { k -> RailKind.isShinkansen(k) }) }
        // 山形・秋田新幹線の線路は、山形・秋田の印だけ（新幹線の印はない。在来線として描く）
        val mini = mapOf("山形新幹線（赤湯の北）" to (38.08840 to 140.19310), "秋田新幹線（雫石の西）" to (39.67220 to 140.88580))
        for ((name, p) in mini) railKindsAt(p.first, p.second).let { assertTrue(name, it.isNotEmpty() && it.all { k -> k and RailKind.MINI_SHINKANSEN != 0 && !RailKind.isShinkansen(k) }) }
        // 河川・湖沼などの種類は大きさの段（下の 4 ビット: 1 = 小・2 = 中・3 = 大）と幅のある川の印。
        // R1 50 km 以上の段は中・大だけ、100 km 以上の段は大だけ
        for ((i, level) in map.levels.withIndex()) {
            val min = when (i) { 2 -> 2; 3 -> 3; else -> 1 }
            for (layer in listOf(MapLayer.RIVER, MapLayer.INLAND_WATER)) {
                val kinds = level.byLayer[layer].orEmpty().map { it.kind }.toSet()
                assertTrue("段 $i ${layer.name} $kinds", kinds.isNotEmpty() && kinds.all { (it and WaterKind.SIZE_MASK) in min..3 })
            }
            // 川の中心の線（印付きの河川）はどの段にもある。川の面（印付きの湖沼など）は段 0 だけ（R1 10 km 未満でだけ描くため）
            assertTrue(level.byLayer[MapLayer.RIVER].orEmpty().any { WaterKind.isWideRiver(it.kind) })
            assertEquals("段 $i", i == 0, level.byLayer[MapLayer.INLAND_WATER].orEmpty().any { WaterKind.isWideRiver(it.kind) })
        }
        // 琵琶湖から大阪湾まで（瀬田川 → 宇治川 → 淀川）が、どの段でもつながっている: 瀬田川の琵琶湖側の端（北緯 34.9838・東経 135.9082）から、
        // 淀川の河口（北緯 34.6806・東経 135.4177）まで、河川の線の端どうしをたどって行ける
        for ((i, level) in map.levels.withIndex()) {
            val rivers = level.byLayer[MapLayer.RIVER].orEmpty()
            fun key(l: MapLine, j: Int) = Math.round(l.lat(j) * 1e3) to Math.round(l.lon(j) * 1e3)
            val byEnd = HashMap<Pair<Long, Long>, MutableList<MapLine>>()
            for (l in rivers) for (j in listOf(0, l.size - 1)) byEnd.getOrPut(key(l, j)) { mutableListOf() } += l
            val start = rivers.filter { l -> (0 until l.size).any { Geo.distanceM(34.9838, 135.9082, l.lat(it), l.lon(it)) < 300 } }
            val seen = HashSet<MapLine>(start); val todo = ArrayDeque(start)
            while (todo.isNotEmpty()) { val l = todo.removeFirst(); for (j in listOf(0, l.size - 1)) for (n in byEnd[key(l, j)].orEmpty()) if (seen.add(n)) todo += n }
            assertTrue("段 $i", seen.any { l -> (0 until l.size).any { Geo.distanceM(34.6806, 135.4177, l.lat(it), l.lon(it)) < 500 } })
        }
        // 道路は、広域の段でも短い線を捨てない（段 3 の線の数は、段 0 のつないだ線の数と同じ。20261018-D04 の変換のとき）
        assertEquals(30_251, map.levels[3].countOf(MapLayer.ROAD))
    }
}
