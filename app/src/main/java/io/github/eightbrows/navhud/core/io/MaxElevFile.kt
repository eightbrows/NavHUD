package io.github.eightbrows.navhud.core.io

import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.floor

/** 緯度・経度からその場所の最高標高を返すもの（地上高の計算に使う。20261025-D07）。 */
fun interface TerrainLookup {
    /** 最高標高 [m]。範囲外・不明（unknown）は null。海などデータのない所は 0 */
    fun maxElevationM(lat: Double, lon: Double): Int?
}

/**
 * 最高標高のファイル（assets/maxelev.bin。tools/build_maxelev.py で作る。20261025-D07）。
 * 国土数値情報「標高・傾斜度5次メッシュ（G04-d）」の各 5 次メッシュ（約 250 m 四方）の最高標高。
 *
 * 形（リトルエンディアン）: ヘッダ 10 バイト（"MXEL"・版 u16 = 1・ブロック数 N u16・刻み STEP u16 [m]）、
 * 索引 10 バイト × N（1 次メッシュコード u16・ブロックの開始位置 u32・長さ u32。コードの昇順）、ブロック × N。
 * ブロックは 1 次メッシュ 1 つ分の zlib 圧縮で、展開すると 320 × 320 の int16（行優先。行 0 = 南端、列 0 = 西端）。
 * 値 × STEP が最高標高 [m]（STEP 単位に切り上げ済み）。0 はデータなし（海など。海面 0 m として扱う）、
 * -32768 は元データが unknown。負の値は海面下の陸地。
 *
 * 展開したブロックは 1 つだけ覚えておき、1 次メッシュが変わったときだけ展開し直す。スレッドセーフではない（1 つのスレッドから使う）。
 */
class MaxElevFile private constructor(
    private val bytes: ByteArray,
    /** 標高の刻み [m] */
    val step: Int,
    private val codes: IntArray,
    private val offsets: IntArray,
    private val lengths: IntArray,
) : TerrainLookup {

    /** 検索の結果 */
    sealed interface Result {
        /** 最高標高 [m]（海などデータのない所は 0） */
        data class Elevation(val meters: Int) : Result

        /** 元データが unknown（地上高は出さない） */
        data object Unknown : Result

        /** データの範囲外（日本の外・外洋など） */
        data object OutOfRange : Result
    }

    /** ブロックの数 */
    val blockCount: Int get() = codes.size

    /** 入っている 1 次メッシュコード（昇順） */
    val meshCodes: List<Int> get() = codes.toList()

    private var cachedCode = -1
    private var cached: ShortArray? = null

    /** 展開し直した回数（テスト・確認用） */
    var inflateCount: Int = 0
        private set

    override fun maxElevationM(lat: Double, lon: Double): Int? = (lookup(lat, lon) as? Result.Elevation)?.meters

    fun lookup(lat: Double, lon: Double): Result {
        val cell = cellOf(lat, lon) ?: return Result.OutOfRange
        val grid = block(cell.code) ?: return Result.OutOfRange
        val v = grid[cell.row * SIDE + cell.col].toInt()
        return if (v == UNKNOWN) Result.Unknown else Result.Elevation(v * step)
    }

    /** 1 次メッシュの展開した値（なければ null）。前と同じ 1 次メッシュなら展開し直さない */
    private fun block(code: Int): ShortArray? {
        if (code == cachedCode) return cached
        val i = codes.binarySearch(code)
        if (i < 0) return null
        val grid = inflate(bytes, offsets[i], lengths[i])
        inflateCount++
        cachedCode = code
        cached = grid
        return grid
    }

    class FormatException(message: String) : Exception(message)

    /** 1 次メッシュコードと、その中の行・列 */
    data class Cell(val code: Int, val row: Int, val col: Int)

    companion object {
        const val SIDE = 320
        const val UNKNOWN = -32768
        private const val HEADER = 10
        private const val INDEX_ENTRY = 10
        private val MAGIC = "MXEL".toByteArray(Charsets.US_ASCII)

        /** ファイルの中身を読む（索引だけ。ブロックは使うときに展開する） */
        fun decode(bytes: ByteArray): MaxElevFile {
            if (bytes.size < HEADER || !MAGIC.indices.all { bytes[it] == MAGIC[it] }) throw FormatException("最高標高のファイルではない")
            val version = u16(bytes, 4)
            if (version != 1) throw FormatException("版 $version は読めない")
            val n = u16(bytes, 6)
            val step = u16(bytes, 8)
            if (step <= 0) throw FormatException("刻みが正しくない")
            if (bytes.size < HEADER + INDEX_ENTRY * n) throw FormatException("索引が途中で終わっている")
            val codes = IntArray(n)
            val offsets = IntArray(n)
            val lengths = IntArray(n)
            for (i in 0 until n) {
                val p = HEADER + INDEX_ENTRY * i
                codes[i] = u16(bytes, p)
                val off = u32(bytes, p + 2)
                val len = u32(bytes, p + 6)
                if (off < 0 || len < 0 || off.toLong() + len > bytes.size) throw FormatException("ブロックの位置が正しくない")
                if (i > 0 && codes[i] <= codes[i - 1]) throw FormatException("索引がコードの昇順でない")
                offsets[i] = off
                lengths[i] = len
            }
            return MaxElevFile(bytes, step, codes, offsets, lengths)
        }

        /**
         * 緯度・経度 → 1 次メッシュコードと行・列。y = 緯度 × 1.5、x = 経度 − 100 で、コード = floor(y) × 100 + floor(x)、
         * 行 = floor((y − floor(y)) × 320)、列 = floor((x − floor(x)) × 320)。コードが 4 桁に収まらない所は null
         */
        fun cellOf(lat: Double, lon: Double): Cell? {
            if (!lat.isFinite() || !lon.isFinite()) return null
            val y = lat * 1.5
            val x = lon - 100.0
            val fy = floor(y)
            val fx = floor(x)
            if (fy < 0 || fy > 99 || fx < 0 || fx > 99) return null
            val row = floor((y - fy) * SIDE).toInt().coerceIn(0, SIDE - 1)
            val col = floor((x - fx) * SIDE).toInt().coerceIn(0, SIDE - 1)
            return Cell(fy.toInt() * 100 + fx.toInt(), row, col)
        }

        private fun inflate(bytes: ByteArray, off: Int, len: Int): ShortArray {
            val inf = Inflater()
            try {
                inf.setInput(bytes, off, len)
                val out = ByteArray(SIDE * SIDE * 2)
                var n = 0
                while (n < out.size && !inf.finished()) {
                    val k = inf.inflate(out, n, out.size - n)
                    if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break
                    n += k
                }
                if (n != out.size) throw FormatException("ブロックを展開した大きさが正しくない（$n バイト）")
                return ShortArray(SIDE * SIDE) { i -> ((out[2 * i].toInt() and 0xFF) or (out[2 * i + 1].toInt() shl 8)).toShort() }
            } catch (e: DataFormatException) {
                throw FormatException("ブロックを展開できない: ${e.message}")
            } finally {
                inf.end()
            }
        }

        private fun u16(b: ByteArray, p: Int): Int = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)

        private fun u32(b: ByteArray, p: Int): Int =
            (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)
    }
}
