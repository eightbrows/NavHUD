package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.io.MaxElevFile.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater

/**
 * 最高標高のファイル（maxelev.bin。20261025-D07）。テストの中で小さなファイルを作る（tools/build_maxelev.py と同じ形）。
 */
class MaxElevFileTest {

    /** 1 次メッシュ 1 つ分の値（320 × 320、行 0 = 南、列 0 = 西）。set に (行, 列) → 値 */
    private fun grid(fill: Int = 0, set: Map<Pair<Int, Int>, Int> = emptyMap()): ShortArray {
        val g = ShortArray(320 * 320) { fill.toShort() }
        for ((rc, v) in set) g[rc.first * 320 + rc.second] = v.toShort()
        return g
    }

    /** build_maxelev.py と同じ形のファイルを作る */
    private fun build(blocks: Map<Int, ShortArray>, step: Int = 10): ByteArray {
        val sorted = blocks.toSortedMap()
        val datas = sorted.values.map { g ->
            val raw = ByteArray(g.size * 2)
            for (i in g.indices) { raw[2 * i] = g[i].toInt().toByte(); raw[2 * i + 1] = (g[i].toInt() shr 8).toByte() }
            val d = Deflater(9)
            d.setInput(raw); d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(65536)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            out.toByteArray()
        }
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v ushr 16) and 0xFFFF) }
        out.write("MXEL".toByteArray()); u16(1); u16(sorted.size); u16(step)
        var off = 10 + 10 * sorted.size
        sorted.keys.forEachIndexed { i, code -> u16(code); u32(off); u32(datas[i].size); off += datas[i].size }
        datas.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** 1 次メッシュ code の (行, 列) の 5 次メッシュの中心の緯度・経度 */
    private fun center(code: Int, row: Int, col: Int): Pair<Double, Double> {
        val y = code / 100 + (row + 0.5) / 320.0
        val x = code % 100 + (col + 0.5) / 320.0
        return y / 1.5 to x + 100.0
    }

    @Test
    fun meshCodeRowAndColumnFromLatLon() {
        // 東京駅（北緯 35.6812・東経 139.7671）: y = 53.52 → 1 次メッシュ 5339。行は南から、列は西から
        val c = MaxElevFile.cellOf(35.6812, 139.7671)!!
        assertEquals(5339, c.code)
        assertEquals(Math.floor((35.6812 * 1.5 - 53) * 320).toInt(), c.row)
        assertEquals(Math.floor((139.7671 - 139) * 320).toInt(), c.col)
        // 1 次メッシュの南西の角はその区画の行 0・列 0。北東の角のすぐ内側は行 319・列 319
        assertEquals(MaxElevFile.Cell(5339, 0, 0), MaxElevFile.cellOf(53.0 / 1.5, 139.0))
        assertEquals(MaxElevFile.Cell(5339, 319, 319), MaxElevFile.cellOf(53.999999 / 1.5, 139.999999))
        // 区画の境: 経度 140 度ちょうどは東の区画（5340）の列 0、緯度 54/1.5 度ちょうどは北の区画（5439）の行 0
        assertEquals(MaxElevFile.Cell(5340, 0, 0), MaxElevFile.cellOf(53.0 / 1.5, 140.0))
        assertEquals(MaxElevFile.Cell(5439, 0, 0), MaxElevFile.cellOf(54.0 / 1.5, 139.0))
        // 4 桁に収まらない所（南半球・経度 100 度より西・200 度以上）は範囲外
        assertNull(MaxElevFile.cellOf(-1.0, 139.0))
        assertNull(MaxElevFile.cellOf(35.0, 99.9))
        assertNull(MaxElevFile.cellOf(35.0, 200.0))
        assertNull(MaxElevFile.cellOf(Double.NaN, 139.0))
    }

    @Test
    fun valuesRowsColumnsAndSpecialValues() {
        // 5339 に: 南西の角 12（120 m）、北西の角 34、南東の角 56、行 10・列 20 に 0（海）、行 11 に unknown、行 12 に -1（海面下）
        val g5339 = grid(fill = 7, set = mapOf(
            (0 to 0) to 12, (319 to 0) to 34, (0 to 319) to 56,
            (10 to 20) to 0, (11 to 20) to MaxElevFile.UNKNOWN, (12 to 20) to -1,
        ))
        val g5340 = grid(fill = 99)
        val f = MaxElevFile.decode(build(mapOf(5340 to g5340, 5339 to g5339)))
        assertEquals(2, f.blockCount)
        assertEquals(listOf(5339, 5340), f.meshCodes)
        assertEquals(10, f.step)
        fun at(code: Int, r: Int, c: Int) = center(code, r, c).let { f.lookup(it.first, it.second) }
        // 行 0 = 南、列 0 = 西（値 × 刻み 10 m）
        assertEquals(Result.Elevation(120), at(5339, 0, 0))
        assertEquals(Result.Elevation(340), at(5339, 319, 0))
        assertEquals(Result.Elevation(560), at(5339, 0, 319))
        assertEquals(Result.Elevation(70), at(5339, 100, 100))
        // 0 は海（0 m として返す）、-32768 は unknown、負の値はそのまま（海面下の陸地）
        assertEquals(Result.Elevation(0), at(5339, 10, 20))
        assertEquals(Result.Unknown, at(5339, 11, 20))
        assertEquals(Result.Elevation(-10), at(5339, 12, 20))
        assertEquals(0, f.maxElevationM(center(5339, 10, 20).first, center(5339, 10, 20).second))
        assertNull(f.maxElevationM(center(5339, 11, 20).first, center(5339, 11, 20).second))
        // 区画の境の東側は 5340
        assertEquals(Result.Elevation(990), f.lookup(53.0 / 1.5, 140.0))
        // 索引にない 1 次メッシュ・4 桁に収まらない所は範囲外
        assertEquals(Result.OutOfRange, at(5338, 0, 0))
        assertEquals(Result.OutOfRange, f.lookup(-35.0, 139.0))
        assertNull(f.maxElevationM(center(5338, 0, 0).first, center(5338, 0, 0).second))
    }

    @Test
    fun onlyOneBlockIsKeptAndReinflatedWhenTheMeshChanges() {
        val f = MaxElevFile.decode(build(mapOf(5339 to grid(1), 5340 to grid(2))))
        assertEquals(0, f.inflateCount)
        f.lookup(center(5339, 5, 5).first, center(5339, 5, 5).second)
        f.lookup(center(5339, 300, 300).first, center(5339, 300, 300).second)
        assertEquals(1, f.inflateCount)   // 同じ区画のあいだは展開し直さない
        f.lookup(center(5340, 5, 5).first, center(5340, 5, 5).second)
        assertEquals(2, f.inflateCount)
        f.lookup(center(5339, 5, 5).first, center(5339, 5, 5).second)
        assertEquals(3, f.inflateCount)   // 覚えているのは 1 区画だけ
        // 範囲外は展開しない
        f.lookup(center(5338, 5, 5).first, center(5338, 5, 5).second)
        assertEquals(3, f.inflateCount)
        // 刻みは見出しの値を使う
        val f5 = MaxElevFile.decode(build(mapOf(5339 to grid(3)), step = 5))
        assertEquals(Result.Elevation(15), f5.lookup(center(5339, 1, 1).first, center(5339, 1, 1).second))
    }

    @Test
    fun brokenFilesAreRejected() {
        val ok = build(mapOf(5339 to grid(1)))
        assertThrows(MaxElevFile.FormatException::class.java) { MaxElevFile.decode("NHBM".toByteArray() + ok.copyOfRange(4, ok.size)) }
        assertThrows(MaxElevFile.FormatException::class.java) { MaxElevFile.decode(ok.copyOf(15)) }
        val v2 = ok.copyOf().also { it[4] = 2 }
        assertThrows(MaxElevFile.FormatException::class.java) { MaxElevFile.decode(v2) }
        // ブロックの中身が壊れている: 展開するときに分かる
        val broken = ok.copyOf().also { for (i in 30 until 60) it[i] = 0x55 }
        val f = MaxElevFile.decode(broken)
        assertThrows(MaxElevFile.FormatException::class.java) { f.lookup(center(5339, 1, 1).first, center(5339, 1, 1).second) }
    }

    @Test
    fun bundledFileCoversJapan() {
        // アプリに入れたファイル（assets/maxelev.bin）。富士山頂・東京湾・沖縄・日本の外
        val file = listOf("src/main/assets/maxelev.bin", "app/src/main/assets/maxelev.bin").map(::File).firstOrNull { it.exists() }
        assumeTrue("maxelev.bin がない", file != null)
        val f = MaxElevFile.decode(file!!.readBytes())
        assertEquals(10, f.step)
        assertEquals(169, f.blockCount)
        // 富士山頂（剣ヶ峰 3,776 m）のメッシュの最高標高は 3,780 m（10 m に切り上げ）
        val fuji = f.maxElevationM(35.3606, 138.7274)!!
        assertTrue("富士山 $fuji", fuji in 3_770..3_780)
        // 東京湾の真ん中は海（0）
        assertEquals(0, f.maxElevationM(35.45, 139.85))
        // 那覇空港のあたりは数 m〜数十 m
        val naha = f.maxElevationM(26.2, 127.65)
        assertTrue("那覇 $naha", naha == null || naha in 0..60)
        // 太平洋の真ん中は範囲外
        assertEquals(Result.OutOfRange, f.lookup(30.0, 150.0))
    }
}
