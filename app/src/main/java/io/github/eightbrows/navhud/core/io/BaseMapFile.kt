package io.github.eightbrows.navhud.core.io

import java.io.ByteArrayOutputStream

/**
 * 背景地図のレイヤ（§8）。番号はファイルに書く値で、tools/gm2basemap.ps1 と同じ。
 * 線（1〜10。面は輪郭の線として入れる）と点（20〜）。元のデータは国土地理院「地球地図日本」第2.2版。
 */
enum class MapLayer(val code: Int, val isPoint: Boolean = false) {
    COASTLINE(1),
    PREF_BOUNDARY(2),
    RIVER(3),
    ROAD(4),
    RAILWAY(5),
    MUNI_BOUNDARY(6),
    FERRY(7),
    DAM_LINE(8),
    INLAND_WATER(9),
    BUILT_UP_AREA(10),
    AIRPORT(20, isPoint = true),
    PORT(21, isPoint = true),
    STATION(22, isPoint = true),
    DAM_POINT(23, isPoint = true),
    CITY(24, isPoint = true);

    companion object {
        /** 知らない番号は null（新しい版のファイルを古いアプリで読んでも、その線・点を飛ばすだけにする） */
        fun of(code: Int): MapLayer? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 河川・湖沼などのレイヤの中の種類: 下の 4 ビットが大きさの段（1 = 小・2 = 中・3 = 大。変換で、広域の段では小さいものを入れない）、
 * WIDE_RIVER（0x10）が幅のある川の印。河川では川の中心の線（地球地図の lit = 2）、湖沼などでは川の面（hyt = 1）の輪郭。
 * 幅のある川は、R1 が Tuning.BASEMAP_WIDE_RIVER_R1_M 未満では川の面の輪郭（両岸）で、以上では川の中心の線 1 本で描く。
 */
object WaterKind {
    const val WIDE_RIVER = 0x10
    const val SIZE_MASK = 0x0F

    fun isWideRiver(kind: Int): Boolean = kind and WIDE_RIVER != 0
}

/**
 * 道路のレイヤの中の種類（地球地図の rtt。データにはこの 3 つしかない）。
 * 国道・県道に当たるとしたのは見立て: データの長さ（14 が約 5.2 万 km、15 が約 6.3 万 km）が、全国の国道・主要地方道の長さに近いため。
 */
object RoadKind {
    /** 国道（地球地図の Primary route） */
    const val PRIMARY = 14
    /** 県道（主要地方道など。地球地図の Secondary route） */
    const val SECONDARY = 15
    /** 高速道路（地球地図の Highway。端がつながるまとまりの長さが 20 km 以上） */
    const val MOTORWAY = 16
    /** その他の自動車専用道路（地球地図では Highway のうち、まとまりの長さが 20 km 未満。変換で付ける） */
    const val OTHER_MOTORWAY = 17
}

/**
 * 鉄道のレイヤの中の種類: 下位 4 ビットは地球地図の fco（2 = 複線、3 = 単線、0 = 不明）。
 * 新幹線の印は変換で付ける（データに新幹線の区分はない。新幹線の駅どうしを結ぶ、カーブの少ない道から見分ける。20261019-D02）。
 */
object RailKind {
    /** 新幹線 */
    const val SHINKANSEN = 0x10
    /** 在来線の線路を走る新幹線（山形・秋田）。今は在来線として描く */
    const val MINI_SHINKANSEN = 0x20

    fun isShinkansen(kind: Int): Boolean = kind and SHINKANSEN != 0
}

/**
 * 背景地図の線 1 本（緯度・経度の折れ線）。メモリを節約するため、座標は「度 × unit」の整数で持つ。
 * 範囲（最小・最大）を持つので、画面の外の線はすぐ飛ばせる。長い線は変換のときに短く分けてある。
 * @param kind レイヤの中の種類（道路の rtt、鉄道の fco など。0〜255）
 */
class MapLine(
    val layer: MapLayer,
    val kind: Int,
    private val latU: IntArray,
    private val lonU: IntArray,
    private val unit: Double,
) {
    init {
        require(latU.size == lonU.size && latU.size >= 2) { "線の点は 2 つ以上、緯度と経度は同じ数" }
    }

    val size: Int get() = latU.size
    fun lat(i: Int): Double = latU[i] / unit
    fun lon(i: Int): Double = lonU[i] / unit

    val minLat = latU.min() / unit
    val maxLat = latU.max() / unit
    val minLon = lonU.min() / unit
    val maxLon = lonU.max() / unit

    companion object {
        /** 度の配列から作る（テスト・書き出し用） */
        fun of(layer: MapLayer, lats: DoubleArray, lons: DoubleArray, kind: Int = 0, unit: Int = BaseMapFile.DEFAULT_UNIT): MapLine =
            MapLine(layer, kind, IntArray(lats.size) { Math.round(lats[it] * unit).toInt() }, IntArray(lons.size) { Math.round(lons[it] * unit).toInt() }, unit.toDouble())
    }
}

/** 背景地図の点（空港・港・駅・ダム・都市）。名前はローマ字（元のデータのまま。今は描かない）。 */
class MapPoint(val layer: MapLayer, val kind: Int, val lat: Double, val lon: Double, val name: String)


/**
 * 1 つの段の、1 つのレイヤの線のかたまり。ファイルから読んだかたまりは、使うときに初めて中身を読む（[load]）。
 * 読んだあとは升目の索引も作る。読み込みは重いので、画面の側（[candidates]）は読み終わったかたまりだけを使い、待たない。
 */
class MapSegment private constructor(
    val layer: MapLayer,
    /** 線の数 */
    val count: Int,
    private val source: (() -> List<MapLine>)?,
    loaded: List<MapLine>?,
) {
    private class Decoded(val lines: List<MapLine>, val grid: GridIndex)

    @Volatile
    private var decoded: Decoded? = loaded?.let { Decoded(it, GridIndex(it)) }

    /** 中身を読み終わっているか */
    val isLoaded: Boolean get() = decoded != null

    /** 中身（まだなら、ここで読む。重いので、画面を描く所からは呼ばない） */
    fun load(): List<MapLine> = (decoded ?: synchronized(this) {
        decoded ?: source!!().let { Decoded(it, GridIndex(it)) }.also { decoded = it }
    }).lines

    /** 読み終わっていれば、範囲と重なりうる線（升目で絞った候補）。まだなら空 */
    fun candidates(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<MapLine> =
        decoded?.grid?.query(minLat, minLon, maxLat, maxLon).orEmpty()

    companion object {
        /** 中身を持っているかたまり（テスト・版 1 のファイル） */
        fun of(layer: MapLayer, lines: List<MapLine>) = MapSegment(layer, lines.size, null, lines)

        /** 使うときに読むかたまり */
        internal fun lazy(layer: MapLayer, count: Int, source: () -> List<MapLine>) = MapSegment(layer, count, source, null)
    }
}

/**
 * 点を間引いた線の組（段）。R1（自機から 1 つ目の距離環の距離）が minR1M 以上のときに使う。
 * 段 0（minR1M = 0）は間引いていない線。中身はレイヤごとのかたまり。
 */
class MapLevel(val minR1M: Double, val segments: List<MapSegment>) {
    /** 中身を持っている段（テスト用。レイヤごとのかたまりに分ける） */
    constructor(minR1M: Double, lines: List<MapLine>, @Suppress("UNUSED_PARAMETER") eager: Boolean = true) :
        this(minR1M, lines.groupBy { it.layer }.map { (layer, l) -> MapSegment.of(layer, l) })

    /** 全部の線（まだ読んでいないかたまりは、ここで読む。テスト・確認用） */
    val lines: List<MapLine> get() = segments.flatMap { it.load() }

    /** レイヤごとの線（まだ読んでいないかたまりは、ここで読む。テスト・確認用） */
    val byLayer: Map<MapLayer, List<MapLine>> get() = segments.groupBy { it.layer }.mapValues { (_, s) -> s.flatMap { it.load() } }

    /** そのレイヤの線の数（読まずに分かる） */
    fun countOf(layer: MapLayer): Int = segments.filter { it.layer == layer }.sumOf { it.count }

    /** そのレイヤの、読み終わったかたまりの線のうち、範囲と重なりうるもの（升目で絞った候補。待たない） */
    fun candidates(layer: MapLayer, minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<MapLine> {
        val s = segments.filter { it.layer == layer }
        return if (s.size == 1) s[0].candidates(minLat, minLon, maxLat, maxLon) else s.flatMap { it.candidates(minLat, minLon, maxLat, maxLon) }
    }

    /** そのレイヤのかたまりを読む（重い。画面を描く所からは呼ばない） */
    fun preload(layers: Set<MapLayer>) = segments.filter { it.layer in layers }.forEach { it.load() }
}

/**
 * 線の升目の索引: 緯度・経度 CELL_DEG 度ごとの升目に、範囲が重なる線を入れておく（線は短く分けてあるので、たいてい 1〜2 升）。
 * 広い範囲を聞かれたとき（升目の数が線の数より多いとき）は、全部の線を返す。
 */
internal class GridIndex(private val lines: List<MapLine>) {
    private val cells = HashMap<Long, IntArray>()

    init {
        // 升目ごとの線の数を数えてから、配列に入れる（細かい入れ物を作らない）
        val counts = HashMap<Long, IntArray>()
        for (l in lines) forCells(l) { k -> (counts.getOrPut(k) { IntArray(1) })[0]++ }
        for ((k, c) in counts) cells[k] = IntArray(c[0])
        val fill = HashMap<Long, IntArray>()
        lines.forEachIndexed { i, l -> forCells(l) { k -> val f = fill.getOrPut(k) { IntArray(1) }; cells.getValue(k)[f[0]++] = i } }
    }

    private inline fun forCells(l: MapLine, f: (Long) -> Unit) {
        for (y in cell(l.minLat)..cell(l.maxLat)) for (x in cell(l.minLon)..cell(l.maxLon)) f(key(y, x))
    }

    fun query(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<MapLine> {
        val y0 = cell(minLat); val y1 = cell(maxLat); val x0 = cell(minLon); val x1 = cell(maxLon)
        if ((y1 - y0 + 1).toLong() * (x1 - x0 + 1) > lines.size) return lines
        val seen = java.util.BitSet(lines.size)
        val out = ArrayList<MapLine>()
        for (y in y0..y1) for (x in x0..x1) {
            val ids = cells[key(y, x)] ?: continue
            for (i in ids) if (!seen.get(i)) { seen.set(i); out += lines[i] }
        }
        return out
    }

    private fun cell(deg: Double): Int = Math.floor(deg / CELL_DEG).toInt()
    private fun key(y: Int, x: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)

    companion object {
        /** 升目の大きさ [度]（約 20 km） */
        const val CELL_DEG = 0.2
    }
}

/** 背景地図（段ごとの線と、点）。 */
class BaseMap(levels: List<MapLevel>, val points: List<MapPoint> = emptyList()) {
    /** minR1M の小さい順 */
    val levels: List<MapLevel> = levels.sortedBy { it.minR1M }

    /** 段が 1 つだけで点のない背景地図（テスト用・版 1 のファイル） */
    constructor(lines: List<MapLine>) : this(listOf(MapLevel(0.0, lines)))

    /** 間引いていない線（段 0）。まだ読んでいないかたまりは、ここで読む（テスト・確認用） */
    val lines: List<MapLine> get() = levels.firstOrNull()?.lines.orEmpty()

    /** 今の R1 [m] で使う段: minR1M が R1 以下の段のうち、いちばん間引いた段 */
    fun levelFor(r1M: Double): MapLevel? = levels.lastOrNull { r1M >= it.minR1M } ?: levels.firstOrNull()

    /** 段 0 の、そのレイヤの線の数（点のレイヤは点の数）。読まずに分かる */
    fun countOf(layer: MapLayer): Int = if (layer.isPoint) points.count { it.layer == layer } else levels.firstOrNull()?.countOf(layer) ?: 0

    /** 描くレイヤの線のかたまりを、どの段も読んでおく（重い。読み込みのとき・レイヤを変えたときに、画面とは別のスレッドで呼ぶ） */
    fun preload(layers: Set<MapLayer>) = levels.forEach { it.preload(layers) }

    companion object {
        val EMPTY = BaseMap(emptyList<MapLine>())
    }
}

/**
 * 背景地図のファイル（assets/basemap.bin）の読み書き。形は tools/gm2basemap.ps1 の先頭の説明と SPEC.md §8 のとおり。
 * - 版 2: 段ごとに、レイヤごとのかたまり（見出しに線の数とバイト数）。線ごとにレイヤの中の種類、範囲は前の線からの差と幅。点（名前つき）。
 *   かたまりの中身は、使うときに読む（[BaseMap.preload]）。読むのはかたまりの見出しと点だけなので、すぐ終わる。
 * - 版 1（20261017-D01）: 段は 1 つ、種類なし、範囲は最小・最大のそのままの値、点なし。今も読める（全部をすぐ読む）。
 * 点の座標は zigzag varint（1 点目は範囲の最小からの差、2 点目からは前の点からの差）。
 */
object BaseMapFile {
    const val VERSION = 2
    const val DEFAULT_UNIT = 10_000
    private val MAGIC = "NHBM".toByteArray(Charsets.US_ASCII)

    class FormatException(message: String) : Exception(message)

    fun decode(bytes: ByteArray): BaseMap {
        val r = Reader(bytes)
        if (!MAGIC.indices.all { r.u8() == MAGIC[it].toInt() and 0xFF }) throw FormatException("背景地図のファイルではない")
        val version = r.u8()
        if (version != 1 && version != 2) throw FormatException("版 $version は読めない")
        val unit = r.i32().toDouble()
        if (unit <= 0) throw FormatException("単位が正しくない")
        repeat(r.u8()) { r.u8(); r.skip(r.u8()) }   // レイヤの一覧（番号と名前）。中身はかたまり・線ごとの番号で分かる
        if (version == 1) return BaseMap(readV1Lines(r, unit, r.u32()))
        val levels = List(r.u8()) {
            val minR1 = r.i32().toDouble()
            val segments = ArrayList<MapSegment>()
            repeat(r.u8()) {
                val layer = MapLayer.of(r.u8())
                val count = r.u32()
                val length = r.u32()
                val from = r.position
                r.skip(length)
                if (layer != null && !layer.isPoint) segments += MapSegment.lazy(layer, count) { readSegment(bytes, from, from + length, count, layer, unit) }
            }
            MapLevel(minR1, segments)
        }
        val points = ArrayList<MapPoint>()
        var lat = 0L
        var lon = 0L
        repeat(r.u32()) {
            val layer = MapLayer.of(r.u8())
            val kind = r.u8()
            lat += r.svarint()
            lon += r.svarint()
            val name = String(r.bytes(r.uvarint().toInt()), Charsets.UTF_8)
            if (layer != null && layer.isPoint) points += MapPoint(layer, kind, lat / unit, lon / unit, name)
        }
        // 中身が壊れていないかは、かたまりを読むときに分かる。かたまりの線の数と見出しが合わなければ FormatException
        return BaseMap(levels, points)
    }

    /** 版 2 のかたまりの中身（線 count 本）を読む */
    private fun readSegment(bytes: ByteArray, from: Int, to: Int, count: Int, layer: MapLayer, unit: Double): List<MapLine> {
        val r = Reader(bytes, from, to)
        val lines = ArrayList<MapLine>(count)
        var prevLat = 0L
        var prevLon = 0L
        repeat(count) {
            val kind = r.u8()
            val n = r.uvarint().toInt()
            if (n < 2) throw FormatException("点が 2 つより少ない線")
            var lat = prevLat + r.svarint()
            var lon = prevLon + r.svarint()
            r.uvarint(); r.uvarint()   // 幅（点から計算し直す）
            prevLat = lat; prevLon = lon
            val la = IntArray(n)
            val lo = IntArray(n)
            for (i in 0 until n) {
                lat += r.svarint()
                lon += r.svarint()
                la[i] = lat.toInt()
                lo[i] = lon.toInt()
            }
            lines += MapLine(layer, kind, la, lo, unit)
        }
        if (r.position != to) throw FormatException("かたまりの大きさが見出しと合わない")
        return lines
    }

    /** 版 1 の線（線ごとに種類の番号、範囲は最小・最大のそのままの値） */
    private fun readV1Lines(r: Reader, unit: Double, count: Int): List<MapLine> {
        val lines = ArrayList<MapLine>(count.coerceAtMost(1_000_000))
        repeat(count) {
            val layer = MapLayer.of(r.u8())
            val n = r.uvarint().toInt()
            if (n < 2) throw FormatException("点が 2 つより少ない線")
            var lat = r.svarint()
            var lon = r.svarint()
            r.svarint(); r.svarint()   // 最大の緯度・経度（点から計算し直す）
            val la = IntArray(n)
            val lo = IntArray(n)
            for (i in 0 until n) {
                lat += r.svarint()
                lon += r.svarint()
                la[i] = lat.toInt()
                lo[i] = lon.toInt()
            }
            if (layer != null && !layer.isPoint) lines += MapLine(layer, 0, la, lo, unit)
        }
        return lines
    }

    /** 書く（版 2。テスト用。アプリに入れるファイルは tools/gm2basemap.ps1 が作る）。 */
    fun encode(map: BaseMap, unit: Int = DEFAULT_UNIT): ByteArray {
        val w = Writer()
        MAGIC.forEach { w.u8(it.toInt()) }
        w.u8(VERSION)
        w.i32(unit)
        val layers = (map.levels.flatMap { l -> l.segments.map { it.layer } } + map.points.map { it.layer }).distinct().sortedBy { it.code }
        w.u8(layers.size)
        for (l in layers) {
            val name = l.name.lowercase().toByteArray(Charsets.UTF_8)
            w.u8(l.code); w.u8(name.size); name.forEach { w.u8(it.toInt()) }
        }
        w.u8(map.levels.size)
        for (level in map.levels) {
            w.i32(level.minR1M.toInt())
            w.u8(level.segments.size)
            for (seg in level.segments) {
                val lines = seg.load()
                val s = Writer()
                var prevLat = 0L
                var prevLon = 0L
                for (line in lines) {
                    val lats = LongArray(line.size) { Math.round(line.lat(it) * unit) }
                    val lons = LongArray(line.size) { Math.round(line.lon(it) * unit) }
                    s.u8(line.kind)
                    s.uvarint(line.size.toLong())
                    val minLat = lats.min()
                    val minLon = lons.min()
                    s.svarint(minLat - prevLat); s.svarint(minLon - prevLon); s.uvarint(lats.max() - minLat); s.uvarint(lons.max() - minLon)
                    prevLat = minLat; prevLon = minLon
                    var pLat = minLat
                    var pLon = minLon
                    for (i in 0 until line.size) {
                        s.svarint(lats[i] - pLat); s.svarint(lons[i] - pLon)
                        pLat = lats[i]; pLon = lons[i]
                    }
                }
                val body = s.bytes()
                w.u8(seg.layer.code); w.i32(lines.size); w.i32(body.size); body.forEach { w.u8(it.toInt()) }
            }
        }
        w.i32(map.points.size)
        var pLat = 0L
        var pLon = 0L
        for (p in map.points) {
            val la = Math.round(p.lat * unit)
            val lo = Math.round(p.lon * unit)
            w.u8(p.layer.code); w.u8(p.kind)
            w.svarint(la - pLat); w.svarint(lo - pLon)
            pLat = la; pLon = lo
            val name = p.name.toByteArray(Charsets.UTF_8)
            w.uvarint(name.size.toLong()); name.forEach { w.u8(it.toInt()) }
        }
        return w.bytes()
    }

    private class Reader(private val b: ByteArray, from: Int = 0, private val end: Int = b.size) {
        private var p = from
        val position: Int get() = p

        private fun next(): Int {
            if (p >= end) throw FormatException("ファイルが途中で終わっている")
            return b[p++].toInt() and 0xFF
        }

        fun u8(): Int = next()
        fun skip(n: Int) {
            if (n < 0 || p + n > end) throw FormatException("ファイルが途中で終わっている")
            p += n
        }
        fun bytes(n: Int): ByteArray {
            if (n < 0 || p + n > end) throw FormatException("ファイルが途中で終わっている")
            return b.copyOfRange(p, p + n).also { p += n }
        }
        fun i32(): Int = next() or (next() shl 8) or (next() shl 16) or (next() shl 24)
        fun u32(): Int = i32().also { if (it < 0) throw FormatException("数が大きすぎる") }

        fun uvarint(): Long {
            var v = 0L
            var shift = 0
            while (true) {
                val c = next()
                v = v or ((c and 0x7F).toLong() shl shift)
                if (c and 0x80 == 0) return v
                shift += 7
                if (shift > 63) throw FormatException("数が長すぎる")
            }
        }

        fun svarint(): Long = uvarint().let { (it ushr 1) xor -(it and 1) }
    }

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun i32(v: Int) { u8(v); u8(v shr 8); u8(v shr 16); u8(v shr 24) }
        fun uvarint(value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) { u8(((v and 0x7F) or 0x80).toInt()); v = v ushr 7 }
            u8(v.toInt())
        }
        fun svarint(v: Long) = uvarint((v shl 1) xor (v shr 63))
        fun bytes(): ByteArray = out.toByteArray()
    }
}
