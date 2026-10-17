package io.github.eightbrows.navhud.core.io

import java.io.ByteArrayOutputStream

/**
 * 背景地図の線の種類（§8）。番号はファイルに書く値で、tools/gm2basemap.ps1 と同じ。
 * 今入れているのは海岸線と県境。河川・道路・鉄道は、あとで足すときのために番号だけ決めてある。
 */
enum class MapLayer(val code: Int) {
    COASTLINE(1),
    PREF_BOUNDARY(2),
    RIVER(3),
    ROAD(4),
    RAILWAY(5);

    companion object {
        /** 知らない番号は null（新しい版のファイルを古いアプリで読んでも、その線を飛ばすだけにする） */
        fun of(code: Int): MapLayer? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 背景地図の線 1 本（緯度・経度 [度] の折れ線）。範囲（最小・最大）を持つので、画面の外の線はすぐ飛ばせる。
 * 長い線は変換のときに短く分けてある（tools/gm2basemap.ps1）。
 */
class MapLine(
    val layer: MapLayer,
    val lats: DoubleArray,
    val lons: DoubleArray,
) {
    init {
        require(lats.size == lons.size && lats.size >= 2) { "線の点は 2 つ以上、緯度と経度は同じ数" }
    }

    val minLat = lats.min()
    val maxLat = lats.max()
    val minLon = lons.min()
    val maxLon = lons.max()

    val size: Int get() = lats.size
}

/** 背景地図（線の集まり）。 */
class BaseMap(val lines: List<MapLine>) {
    /** 種類ごとの線の数 */
    fun countOf(layer: MapLayer): Int = lines.count { it.layer == layer }

    companion object {
        val EMPTY = BaseMap(emptyList())
    }
}

/**
 * 背景地図のファイル（assets/basemap.bin）の読み書き。形は tools/gm2basemap.ps1 の先頭の説明と SPEC.md §8 のとおり。
 * リトルエンディアン。"NHBM"、版 1、単位（座標 = 度 × 単位 の整数）、レイヤの一覧、線（種類・点の数・範囲・点の差）。
 * 点は zigzag varint（1 点目は範囲の最小からの差、2 点目からは前の点からの差）なので小さい。
 */
object BaseMapFile {
    const val VERSION = 1
    const val DEFAULT_UNIT = 100_000
    private val MAGIC = "NHBM".toByteArray(Charsets.US_ASCII)

    class FormatException(message: String) : Exception(message)

    fun decode(bytes: ByteArray): BaseMap {
        val r = Reader(bytes)
        if (!MAGIC.indices.all { r.u8() == MAGIC[it].toInt() and 0xFF }) throw FormatException("背景地図のファイルではない")
        val version = r.u8()
        if (version != VERSION) throw FormatException("版 $version は読めない")
        val unit = r.i32().toDouble()
        if (unit <= 0) throw FormatException("単位が正しくない")
        repeat(r.u8()) { r.u8(); r.skip(r.u8()) }   // レイヤの一覧（番号と名前）。中身は線ごとの番号で分かる
        val count = r.u32()
        val lines = ArrayList<MapLine>(count.coerceAtMost(1_000_000))
        repeat(count) {
            val layer = MapLayer.of(r.u8())
            val n = r.uvarint().toInt()
            if (n < 2) throw FormatException("点が 2 つより少ない線")
            var lat = r.svarint()
            var lon = r.svarint()
            r.svarint(); r.svarint()   // 最大の緯度・経度（点から計算し直す）
            val lats = DoubleArray(n)
            val lons = DoubleArray(n)
            for (i in 0 until n) {
                lat += r.svarint()
                lon += r.svarint()
                lats[i] = lat / unit
                lons[i] = lon / unit
            }
            if (layer != null) lines += MapLine(layer, lats, lons)
        }
        return BaseMap(lines)
    }

    /** 書く（テスト用。アプリに入れるファイルは tools/gm2basemap.ps1 が作る）。 */
    fun encode(map: BaseMap, unit: Int = DEFAULT_UNIT): ByteArray {
        val w = Writer()
        MAGIC.forEach { w.u8(it.toInt()) }
        w.u8(VERSION)
        w.i32(unit)
        val layers = map.lines.map { it.layer }.distinct().sortedBy { it.code }
        w.u8(layers.size)
        for (l in layers) {
            val name = l.name.lowercase().toByteArray(Charsets.UTF_8)
            w.u8(l.code); w.u8(name.size); name.forEach { w.u8(it.toInt()) }
        }
        w.i32(map.lines.size)
        for (line in map.lines) {
            val lats = LongArray(line.size) { Math.round(line.lats[it] * unit) }
            val lons = LongArray(line.size) { Math.round(line.lons[it] * unit) }
            w.u8(line.layer.code)
            w.uvarint(line.size.toLong())
            w.svarint(lats.min()); w.svarint(lons.min()); w.svarint(lats.max()); w.svarint(lons.max())
            var pLat = lats.min()
            var pLon = lons.min()
            for (i in 0 until line.size) {
                w.svarint(lats[i] - pLat); w.svarint(lons[i] - pLon)
                pLat = lats[i]; pLon = lons[i]
            }
        }
        return w.bytes()
    }

    private class Reader(private val b: ByteArray) {
        private var p = 0

        private fun next(): Int {
            if (p >= b.size) throw FormatException("ファイルが途中で終わっている")
            return b[p++].toInt() and 0xFF
        }

        fun u8(): Int = next()
        fun skip(n: Int) { repeat(n) { next() } }
        fun i32(): Int = next() or (next() shl 8) or (next() shl 16) or (next() shl 24)
        fun u32(): Int = i32().also { if (it < 0) throw FormatException("線の数が大きすぎる") }

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
