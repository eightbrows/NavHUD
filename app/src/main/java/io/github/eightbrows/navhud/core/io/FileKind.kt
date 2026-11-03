package io.github.eightbrows.navhud.core.io

/** ファイルの中身から見分けた種類（20261103-D01）。 */
enum class FileKind {
    /** 軌跡（track.csv・GpsLogger の zip・<wpt> がなく <trk> だけの GPX） */
    TRACK,

    /** WP リスト（lat・lon の列があり、軌跡の時刻の列がない CSV・<wpt> のある GPX） */
    WAYPOINTS,

    /** どちらとも言えない（今までどおり、それぞれの読み込みにまかせる） */
    UNKNOWN,
}

/**
 * ファイルの種類の見分け（20261103-D01）。WP リストと軌跡の読み込みの前に、違う種類のファイルを選んでいないかを見る。
 * 見出しの名前の扱いは、それぞれの読み込みと同じにする:
 * - 軌跡の時刻の列: TrackCsv と同じく、見出しの名前を前後の空白を除いてそのまま比べる（`epoch_ms`・`utc_iso8601`）
 * - WP の位置の列: WaypointCsv と同じく、前後の空白を除いて小文字にして比べる（`lat`・`lon`）
 * 軌跡の時刻の列があれば軌跡（lat・lon があっても）。なく、lat と lon があれば WP。どちらもなければ UNKNOWN。
 * GPX は <wpt> があれば WP、なく <trk> があれば軌跡。zip（先頭が PK）は軌跡（GpsLogger の zip）。Android に依存しない。
 */
object FileKinds {
    /** 軌跡の読み込み（TrackCsv）が使う時刻の列 */
    val TRACK_TIME_COLUMNS = setOf("epoch_ms", "utc_iso8601")

    /** 見分けに使う先頭の長さ（GPX の <wpt> / <trk> を探す範囲） */
    const val HEAD_BYTES = 64 * 1024

    /** @param bytes ファイルの先頭（HEAD_BYTES まであれば十分。全部でもよい） */
    fun detect(bytes: ByteArray): FileKind {
        if (SessionZip.isZip(bytes.copyOf(minOf(bytes.size, 4)))) return FileKind.TRACK
        if (WaypointGpx.looksLikeGpx(bytes)) return gpx(bytes)
        return csvHeader(bytes)
    }

    private fun gpx(bytes: ByteArray): FileKind {
        val text = String(bytes, 0, minOf(bytes.size, HEAD_BYTES), Charsets.UTF_8)
        return when {
            Regex("<(\\w+:)?wpt[\\s>/]").containsMatchIn(text) -> FileKind.WAYPOINTS
            Regex("<(\\w+:)?trk[\\s>/]").containsMatchIn(text) -> FileKind.TRACK
            else -> FileKind.UNKNOWN
        }
    }

    /** CSV の見出し（最初の空でない行）から見分ける */
    private fun csvHeader(bytes: ByteArray): FileKind {
        val text = WaypointCsv.decode(bytes.copyOf(minOf(bytes.size, HEAD_BYTES))).removePrefix(0xFEFF.toChar().toString()) // BOM
        val header = CsvRecords.parse(text.lineSequence().firstOrNull { it.isNotBlank() } ?: return FileKind.UNKNOWN)
            .firstOrNull() ?: return FileKind.UNKNOWN
        val names = header.map { it.trim() }
        if (names.any { it in TRACK_TIME_COLUMNS }) return FileKind.TRACK
        val lower = names.map { it.lowercase() }.toSet()
        return if ("lat" in lower && "lon" in lower) FileKind.WAYPOINTS else FileKind.UNKNOWN
    }
}
