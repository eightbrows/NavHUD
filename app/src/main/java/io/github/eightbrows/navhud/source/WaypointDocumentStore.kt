package io.github.eightbrows.navhud.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import io.github.eightbrows.navhud.core.io.WaypointCsv
import io.github.eightbrows.navhud.core.io.WaypointGpx
import io.github.eightbrows.navhud.core.io.WaypointParseResult
import io.github.eightbrows.navhud.core.model.Waypoint
import java.time.ZoneId

/** 読み込んだ WP リスト。 */
data class LoadedWaypoints(val uri: Uri, val displayName: String, val result: WaypointParseResult, val isGpx: Boolean)

/**
 * WP リストのファイル（§6.5, §7）。アプリ内に DB は持たない。
 * 最後に読み込んだか書き出したファイルの URI を永続化し、起動時の「前回のリスト」で読み直す。
 */
class WaypointDocumentStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val savedUri: Uri? get() = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    /** 読み取り（書き出したファイルなら書き込みも）の権限を永続化して、「前回のリスト」として覚える。 */
    fun remember(uri: Uri, writable: Boolean) {
        val resolver = appContext.contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        if (writable) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
        }
        prefs.edit { putString(KEY_URI, uri.toString()) }
    }

    fun forget() {
        prefs.edit { remove(KEY_URI) }
    }

    /** CSV か GPX を読む。中身で見分ける。ブロッキングなので IO スレッドで呼ぶ。 */
    fun load(uri: Uri, zone: ZoneId): LoadedWaypoints {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("ファイルを開けません")
        val gpx = WaypointGpx.looksLikeGpx(bytes)
        val result = if (gpx) WaypointGpx.parse(bytes, zone) else WaypointCsv.parse(bytes)
        return LoadedWaypoints(uri, displayName(uri), result, gpx)
    }

    /** CSV で書き出す（UTF-8 BOM 付き、CRLF）。ブロッキングなので IO スレッドで呼ぶ。 */
    fun save(uri: Uri, wps: List<Waypoint>): String {
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(WaypointCsv.encode(wps)) }
            ?: error("ファイルに書き込めません")
        return displayName(uri)
    }

    fun displayName(uri: Uri): String =
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: uri.toString()

    companion object {
        private const val PREFS = "waypoints"
        private const val KEY_URI = "list_uri"

        /** インポートで受ける MIME。GPX / CSV は端末によって MIME が違うので広めに受ける。 */
        val IMPORT_MIME_TYPES = arrayOf(
            "text/*",
            "application/csv",
            "application/gpx+xml",
            "application/xml",
            "application/octet-stream",
        )
        const val EXPORT_MIME_TYPE = "text/csv"
        const val EXPORT_DEFAULT_NAME = "waypoints.csv"
    }
}
