package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.model.Waypoint
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalTime

data class WaypointParseResult(val waypoints: List<Waypoint>, val skippedLines: Int)

/**
 * WP の CSV（§7.1）。Excel で編集される前提で、文字コード・改行・時刻の書き方に寛容に読む。
 * 列はヘッダ名で引く。reached は保存しない。
 */
object WaypointCsv {

    val HEADER = listOf("lat", "lon", "ele", "name", "target_time", "deadline_time", "enabled", "radius")

    private const val BOM = '﻿'
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val WINDOWS_31J: Charset = Charset.forName("windows-31j")

    // ---- 読み込み ----

    /** BOM があれば UTF-8。なければ UTF-8 として厳密に読み、失敗したら Windows-31J で読み直す。 */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, WINDOWS_31J)
        }
    }

    fun parse(bytes: ByteArray): WaypointParseResult = parse(decode(bytes))

    fun parse(text: String): WaypointParseResult {
        val records = CsvRecords.parse(text.removePrefix(BOM.toString()))
            .filterNot { r -> r.all { it.isBlank() } }
        if (records.isEmpty()) return WaypointParseResult(emptyList(), 0)

        val col = records.first().withIndex().associate { (i, name) -> name.trim().lowercase() to i }
        val iLat = col["lat"]
        val iLon = col["lon"]
        val iEle = col["ele"]
        val iName = col["name"]
        val iTarget = col["target_time"]
        val iDeadline = col["deadline_time"]
        val iEnabled = col["enabled"]
        val iRadius = col["radius"]

        val wps = ArrayList<Waypoint>()
        var skipped = 0
        for (r in records.drop(1)) {
            fun raw(i: Int?): String = i?.let { r.getOrNull(it) }.orEmpty()
            val wp = run {
                val lat = raw(iLat).trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return@run null
                val lon = raw(iLon).trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return@run null
                val ele = raw(iEle).trim().let { if (it.isEmpty()) null else it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@run null }
                val target = raw(iTarget).trim().let { if (it.isEmpty()) null else TimeText.parse(it) ?: return@run null }
                val deadline = raw(iDeadline).trim().let { if (it.isEmpty()) null else TimeText.parse(it) ?: return@run null }
                val enabled = when (raw(iEnabled).trim().lowercase()) {
                    "", "1", "true" -> true
                    "0", "false" -> false
                    else -> return@run null
                }
                // 到達半径: 空欄なら全体の設定。0 以下・数値でなければ読めない行
                val radius = raw(iRadius).trim().let { if (it.isEmpty()) null else it.toDoubleOrNull()?.takeIf { r -> r.isFinite() && r > 0 } ?: return@run null }
                Waypoint(
                    name = raw(iName).ifBlank { "WP${wps.size + 1}" },
                    lat = lat,
                    lon = lon,
                    eleM = ele,
                    targetTime = target,
                    deadlineTime = deadline,
                    enabled = enabled,
                    radiusM = radius,
                )
            }
            if (wp == null) skipped++ else wps += wp
        }
        return WaypointParseResult(wps, skipped)
    }

    // ---- 書き出し ----

    /** CSV の文字列（改行 CRLF、BOM なし）。 */
    fun write(wps: List<Waypoint>): String = buildString {
        append(HEADER.joinToString(",")).append("\r\n")
        for (w in wps) {
            val fields = listOf(
                number(w.lat),
                number(w.lon),
                w.eleM?.let(::number).orEmpty(),
                w.name,
                w.targetTime?.let(TimeText::format).orEmpty(),
                w.deadlineTime?.let(TimeText::format).orEmpty(),
                if (w.enabled) "1" else "0",
                w.radiusM?.let(::number).orEmpty(),
            )
            append(fields.joinToString(",") { quote(it) }).append("\r\n")
        }
    }

    /** 書き出すバイト列（UTF-8 の BOM 付き、改行 CRLF）。 */
    fun encode(wps: List<Waypoint>): ByteArray = UTF8_BOM + write(wps).toByteArray(Charsets.UTF_8)

    /** 指数表記にならず、読み直すと同じ値になる最短の10進表記。 */
    private fun number(d: Double): String = BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\r' || it == '\n' } || s != s.trim()) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
}

/** ローカル時刻の文字列。Excel が書き換える形（9:30, 9:30:00）も受け付ける。 */
object TimeText {
    private val PATTERN = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")

    /** H:mm / HH:mm / H:mm:ss / HH:mm:ss。読めなければ null。 */
    fun parse(s: String): LocalTime? {
        val m = PATTERN.matchEntire(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val sec = m.groupValues[3].ifEmpty { "0" }.toInt()
        if (h > 23 || min > 59 || sec > 59) return null
        return LocalTime.of(h, min, sec)
    }

    /** 秒が 0 なら HH:mm、そうでなければ HH:mm:ss。 */
    fun format(t: LocalTime): String =
        if (t.second == 0) "%02d:%02d".format(t.hour, t.minute) else "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
}

/**
 * RFC 4180 の CSV を記録（フィールドのリスト）に分ける。CRLF / LF / CR の混在を許容する。
 * "..." で囲まれた値はそのまま、囲まれていない値は前後の空白を除く。
 */
internal object CsvRecords {
    fun parse(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var quoted = false
        var i = 0
        fun endField() {
            fields += if (quoted) sb.toString() else sb.toString().trim()
            sb.setLength(0)
            quoted = false
        }
        fun endRecord() {
            endField()
            records += fields
            fields = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (!quoted && sb.isBlank()) {
                        sb.setLength(0)
                        inQuotes = true
                        quoted = true
                    } else {
                        sb.append(c)
                    }
                    // 閉じ引用符のあとの空白は無視する
                    ' ', '\t' -> if (!quoted) sb.append(c)
                    ',' -> endField()
                    '\r' -> {
                        endRecord()
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                    }
                    '\n' -> endRecord()
                    else -> sb.append(c)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
