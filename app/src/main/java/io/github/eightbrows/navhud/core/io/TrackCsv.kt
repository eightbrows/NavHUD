package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.model.Fix
import java.io.InputStream
import java.time.Instant

/**
 * 読んだ track.csv。resumeElapsedNs は一時停止からの再開点（gap_before=true の点）の経過時間 [ns]（20261125-D01。歩数の区間の境目。
 * 列がない・経過時間がない点は入れない）
 */
data class TrackParseResult(val fixes: List<Fix>, val skippedLines: Int, val resumeElapsedNs: List<Long> = emptyList())

/**
 * GPSロガーのトラックCSV（track.csv 形式）を読む。
 * 列はヘッダ名で引くので、列順の違い・追加列・末尾列の欠けに強い。
 * 必須項目が読めない行はスキップして数える。空行は数えない。
 */
object TrackCsv {

    fun parse(input: InputStream): TrackParseResult =
        input.bufferedReader(Charsets.UTF_8).useLines { parse(it) }

    fun parse(text: String): TrackParseResult = parse(text.lineSequence())

    fun parse(lines: Sequence<String>): TrackParseResult {
        val iter = lines.iterator()
        if (!iter.hasNext()) return TrackParseResult(emptyList(), 0)

        val header = iter.next().removePrefix("\uFEFF").split(',').map { it.trim() }
        val col = header.withIndex().associate { (i, name) -> name to i }
        val iLat = col["latitude"]
        val iLon = col["longitude"]
        val iEpoch = col["epoch_ms"]
        val iIso = col["utc_iso8601"]
        val iAlt = col["altitude_ellipsoid_m"]
        val iSpeed = col["speed_mps"]
        val iBearing = col["bearing_deg"]
        val iBearingAcc = col["bearing_acc_deg"]
        val iHorizAcc = col["horizontal_acc_m"]
        // 歩数との時刻合わせ（20261124-D01）。列がない・読めない点は null
        val iElapsed = col["elapsed_realtime_ns"]
        // 一時停止からの再開点（20261125-D01。歩数の区間の境目）
        val iGap = col["gap_before"]

        val fixes = ArrayList<Fix>()
        val resumes = ArrayList<Long>()
        var skipped = 0
        while (iter.hasNext()) {
            val line = iter.next()
            if (line.isBlank()) continue
            val f = line.split(',')
            fun str(i: Int?): String? = i?.let { f.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            fun dbl(i: Int?): Double? = str(i)?.toDoubleOrNull()?.takeIf { it.isFinite() }
            fun flt(i: Int?): Float? = str(i)?.toFloatOrNull()?.takeIf { it.isFinite() }

            val lat = dbl(iLat)
            val lon = dbl(iLon)
            val time = str(iEpoch)?.toLongOrNull()
                ?: str(iIso)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            if (lat == null || lon == null || time == null) {
                skipped++
                continue
            }

            val speed = flt(iSpeed)
            val bearingValid = if (iBearingAcc != null) {
                flt(iBearingAcc)?.let { it > 0f } ?: false
            } else {
                speed == null || speed > 0f
            }

            fixes += Fix(
                timeMs = time,
                lat = lat,
                lon = lon,
                altRawM = dbl(iAlt),
                speedMps = speed,
                bearingDeg = if (bearingValid) flt(iBearing) else null,
                horizAccM = flt(iHorizAcc),
                bearingAccDeg = if (bearingValid) flt(iBearingAcc) else null,
                elapsedNs = str(iElapsed)?.toLongOrNull(),
            )
            if (str(iGap).equals("true", ignoreCase = true)) str(iElapsed)?.toLongOrNull()?.let { resumes += it }
        }
        return TrackParseResult(fixes, skipped, resumes)
    }

    /**
     * 概要だけを数える（zip のセッション一覧用）。Fix は作らない。
     * 読める行の決まりは parse と同じ（緯度・経度・時刻が読める行だけを数える）。
     */
    fun summarize(lines: Sequence<String>): TrackSummary {
        val iter = lines.iterator()
        if (!iter.hasNext()) return TrackSummary(0, 0, null, null)

        val header = iter.next().removePrefix("\uFEFF").split(',').map { it.trim() }
        val col = header.withIndex().associate { (i, name) -> name to i }
        val iLat = col["latitude"]
        val iLon = col["longitude"]
        val iEpoch = col["epoch_ms"]
        val iIso = col["utc_iso8601"]

        var points = 0
        var skipped = 0
        var start: Long? = null
        var end: Long? = null
        while (iter.hasNext()) {
            val line = iter.next()
            if (line.isBlank()) continue
            val f = line.split(',')
            fun str(i: Int?): String? = i?.let { f.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            fun dbl(i: Int?): Double? = str(i)?.toDoubleOrNull()?.takeIf { it.isFinite() }

            val time = str(iEpoch)?.toLongOrNull()
                ?: str(iIso)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            if (dbl(iLat) == null || dbl(iLon) == null || time == null) {
                skipped++
                continue
            }
            points++
            start = start?.let { minOf(it, time) } ?: time
            end = end?.let { maxOf(it, time) } ?: time
        }
        return TrackSummary(points, skipped, start, end)
    }
}

/** track.csv の概要: 読めた点の数、スキップした行の数、最初と最後の時刻（点がなければ null）。 */
data class TrackSummary(val points: Int, val skippedLines: Int, val startMs: Long?, val endMs: Long?)
