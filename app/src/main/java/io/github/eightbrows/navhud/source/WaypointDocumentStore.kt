package io.github.eightbrows.navhud.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.io.FileKind
import io.github.eightbrows.navhud.core.io.FileKindException
import io.github.eightbrows.navhud.core.io.FileKinds
import io.github.eightbrows.navhud.core.io.LastFileMemory
import io.github.eightbrows.navhud.core.io.RecentFile
import io.github.eightbrows.navhud.core.io.RecentFiles
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
    private val memory = LastFileMemory(PrefsKeyValueStore(appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)), KEY_URI)

    val savedUri: Uri? get() = memory.uri?.let(Uri::parse)

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
        memory.remember(uri.toString())
    }

    /**
     * 前回のリストを忘れる（読めなかったとき・クリアしたとき）。次の起動では自動で開かない（20261102-D01）。
     * 忘れたファイル（URI の文字）を返す。権限は呼び出し側が、まだ使っているか確かめてから返す（20261104-D03）
     */
    fun forget(): String? {
        val uri = memory.uri
        memory.forget()
        return uri
    }

    private val recentFiles = RecentFiles(PrefsKeyValueStore(appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)), KEY_RECENT)

    /** 最近のファイル（新しい順。20261104-D03） */
    val recent: List<RecentFile> get() = recentFiles.list()

    /** 開いた結果を最近のファイルに反映する（RecentFiles.recordOpen）。履歴から外れたファイル（URI の文字）を返す */
    fun recordRecent(file: RecentFile, error: Throwable?): List<String> = recentFiles.recordOpen(file, error)

    /** 最近のファイルから消す（✕） */
    fun removeRecent(uri: String) = recentFiles.remove(uri) != null

    /** このストアが使っているファイル（最近のファイルと前回のファイル）。権限を返すか決めるのに使う */
    fun inUse(): Set<String> = recent.map { it.uri }.toSet() + listOfNotNull(memory.uri)

    /**
     * CSV か GPX を読む。中身で見分ける。軌跡のファイルなら FileKindException（20261103-D01）。ブロッキングなので IO スレッドで呼ぶ。
     */
    fun load(uri: Uri, zone: ZoneId): LoadedWaypoints {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error(AppLanguage.localized(appContext).getString(R.string.err_file_open))
        // 中身が軌跡（track.csv・zip・<trk> だけの GPX）なら読み込まない（20261103-D01）
        if (FileKinds.detect(bytes) == FileKind.TRACK) throw FileKindException(FileKind.TRACK, displayName(uri))
        val gpx = WaypointGpx.looksLikeGpx(bytes)
        val result = if (gpx) WaypointGpx.parse(bytes, zone) else WaypointCsv.parse(bytes)
        return LoadedWaypoints(uri, displayName(uri), result, gpx)
    }

    /** CSV で書き出す（UTF-8 BOM 付き、CRLF）。ブロッキングなので IO スレッドで呼ぶ。 */
    fun save(uri: Uri, wps: List<Waypoint>): String {
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(WaypointCsv.encode(wps)) }
            ?: error(AppLanguage.localized(appContext).getString(R.string.err_file_write))
        return displayName(uri)
    }

    fun displayName(uri: Uri): String =
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: uri.toString()

    companion object {
        private const val PREFS = "waypoints"
        private const val KEY_URI = "list_uri"
        private const val KEY_RECENT = "list_recent"

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
