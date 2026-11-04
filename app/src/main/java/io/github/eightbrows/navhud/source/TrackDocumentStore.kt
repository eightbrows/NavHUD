package io.github.eightbrows.navhud.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.io.FileKind
import io.github.eightbrows.navhud.core.io.FileKindException
import io.github.eightbrows.navhud.core.io.FileKinds
import io.github.eightbrows.navhud.core.io.LastFileMemory
import io.github.eightbrows.navhud.core.io.RecentFile
import io.github.eightbrows.navhud.core.io.RecentFiles
import io.github.eightbrows.navhud.core.io.SessionZip
import io.github.eightbrows.navhud.core.io.TrackCsv
import io.github.eightbrows.navhud.core.io.TrackParseResult
import io.github.eightbrows.navhud.core.io.ZipSession

/**
 * 読み込んだトラック。displayName は画面のひとことに出す名前（zip は「ファイル名 / セッションの名前」）。
 * fileName はファイルの名前だけ、session は zip のセッションの名前（zip でなければ null。20261104-D03）
 */
data class LoadedTrack(
    val uri: Uri, val displayName: String, val result: TrackParseResult,
    val fileName: String = displayName, val session: String? = null,
)

/** GpsLogger の zip の中のセッションの一覧（どれを再生するか選んでもらう）。 */
data class ZipSessions(val uri: Uri, val displayName: String, val sessions: List<ZipSession>)

/** ファイルを開いた結果: そのまま再生できるトラックか、zip のセッションの一覧。 */
sealed interface OpenedTrack {
    data class Track(val track: LoadedTrack) : OpenedTrack
    data class Sessions(val zip: ZipSessions) : OpenedTrack
}

/**
 * リプレイ用のファイル（track.csv か GpsLogger の zip）の選択結果を覚えておく（§6.7）。
 * ACTION_OPEN_DOCUMENT で選んだ URI の読み取り権限を永続化し、次回の起動で再利用する。
 * zip のときは、選んだセッション（zip の中の track.csv の名前）も覚える。
 */
class TrackDocumentStore(context: Context) {

    private val appContext = context.applicationContext
    private val memory = LastFileMemory(PrefsKeyValueStore(appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)), KEY_URI, KEY_ENTRY)

    val savedUri: Uri? get() = memory.uri?.let(Uri::parse)

    /** 前回選んだ zip のセッション（zip の中の track.csv の名前）。zip でない・まだ選んでいないなら null */
    val savedEntry: String? get() = memory.entry

    /** zip の中で選んだセッションを覚える。 */
    fun rememberEntry(entryName: String) = memory.rememberEntry(entryName)

    /** 選ばれた URI の読み取り権限を永続化して保存する（前のファイルで選んだセッションは忘れる）。 */
    fun remember(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // 永続化できないプロバイダ。今回の起動中は読めるので、保存だけしておく
        }
        memory.remember(uri.toString())
    }

    /**
     * 前回のファイルを忘れる（読めなかったとき・クリアしたとき）。次の起動では自動で開かない（20261102-D01）。
     * 忘れたファイル（URI の文字）を返す。読み取りの権限は呼び出し側が、まだ使っているか確かめてから返す（20261104-D03）
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

    /** このストアが使っているファイル（最近のファイルと前回のファイル）。読み取りの権限を返すか決めるのに使う */
    fun inUse(): Set<String> = recent.map { it.uri }.toSet() + listOfNotNull(memory.uri)

    /**
     * URI のファイルを開く。ファイル削除・権限失効などで読めなければ例外を投げる。WP リストのファイルなら FileKindException（20261103-D01）。ブロッキングなので IO スレッドで呼ぶ。
     * - track.csv: そのまま読む。
     * - zip（先頭が PK）: entryName のセッションがあればそれを読む。なければ（選んでいない・見つからない）セッションの一覧を返す。
     *   中身は展開せず、必要な track.csv だけを流し読みする。
     * @throws io.github.eightbrows.navhud.core.io.SessionZipException 壊れた zip、track.csv のない zip
     */
    fun open(uri: Uri, entryName: String?): OpenedTrack {
        val name = displayName(uri)
        // 先頭で見分ける（20261103-D01）: WP リスト（lat・lon の CSV・<wpt> のある GPX）なら読み込まない
        val head = stream(uri).use { readHead(it, FileKinds.HEAD_BYTES) }
        if (FileKinds.detect(head) == FileKind.WAYPOINTS) throw FileKindException(FileKind.WAYPOINTS, name)
        if (!SessionZip.isZip(head.copyOf(minOf(head.size, 4)))) {
            return OpenedTrack.Track(LoadedTrack(uri, name, stream(uri).use { TrackCsv.parse(it) }))
        }
        if (entryName != null) {
            stream(uri).use { SessionZip.read(it, entryName) }?.let { result ->
                val session = SessionZip.sessionName(entryName)
                return OpenedTrack.Track(LoadedTrack(uri, if (session.isEmpty()) name else "$name / $session", result, name, session.ifEmpty { null }))
            }
        }
        return OpenedTrack.Sessions(ZipSessions(uri, name, stream(uri).use { SessionZip.list(it) }))
    }

    /** 先頭の max バイトまで（短いファイルはその長さ） */
    private fun readHead(input: java.io.InputStream, max: Int): ByteArray {
        val buf = ByteArray(max)
        var n = 0
        while (n < max) {
            val r = input.read(buf, n, max - n)
            if (r < 0) break
            n += r
        }
        return buf.copyOf(n)
    }

    private fun stream(uri: Uri) =
        appContext.contentResolver.openInputStream(uri) ?: error(AppLanguage.localized(appContext).getString(R.string.err_file_open))

    private fun displayName(uri: Uri): String =
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: uri.toString()

    /** Download フォルダを最初に開くファイル選択。 */
    class OpenTrackDocument : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_URI)
    }

    companion object {
        private const val PREFS = "replay"
        private const val KEY_URI = "track_uri"
        private const val KEY_ENTRY = "track_zip_entry"
        private const val KEY_RECENT = "track_recent"

        /** CSV は端末によって MIME が違うので広めに受ける。GpsLogger の zip も選べる。 */
        val MIME_TYPES = arrayOf(
            "text/*", "application/csv", "application/octet-stream",
            "application/zip", "application/x-zip-compressed", "application/x-zip",
        )

        private val DOWNLOADS_URI: Uri =
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
    }
}
