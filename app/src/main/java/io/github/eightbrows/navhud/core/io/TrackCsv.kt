package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.model.Fix
import java.io.InputStream
import java.time.Instant

data class TrackParseResult(val fixes: List<Fix>, val skippedLines: Int)

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

        val fixes = ArrayList<Fix>()
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
            )
        }
        return TrackParseResult(fixes, skipped)
    }
}
