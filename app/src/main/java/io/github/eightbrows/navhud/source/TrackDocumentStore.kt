package io.github.eightbrows.navhud.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.io.SessionZip
import io.github.eightbrows.navhud.core.io.TrackCsv
import io.github.eightbrows.navhud.core.io.TrackParseResult
import io.github.eightbrows.navhud.core.io.ZipSession

/** 読み込んだトラック。 */
data class LoadedTrack(val uri: Uri, val displayName: String, val result: TrackParseResult)

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
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val savedUri: Uri? get() = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    /** 前回選んだ zip のセッション（zip の中の track.csv の名前）。zip でない・まだ選んでいないなら null */
    val savedEntry: String? get() = prefs.getString(KEY_ENTRY, null)

    /** zip の中で選んだセッションを覚える。 */
    fun rememberEntry(entryName: String) = prefs.edit { putString(KEY_ENTRY, entryName) }

    /** 選ばれた URI の読み取り権限を永続化して保存する（前のファイルで選んだセッションは忘れる）。 */
    fun remember(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // 永続化できないプロバイダ。今回の起動中は読めるので、保存だけしておく
        }
        prefs.edit {
            putString(KEY_URI, uri.toString())
            remove(KEY_ENTRY)
        }
    }

    fun forget() {
        savedUri?.let { uri ->
            try {
                appContext.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
        }
        prefs.edit {
            remove(KEY_URI)
            remove(KEY_ENTRY)
        }
    }

    /**
     * URI のファイルを開く。ファイル削除・権限失効などで読めなければ例外を投げる。ブロッキングなので IO スレッドで呼ぶ。
     * - track.csv: そのまま読む。
     * - zip（先頭が PK）: entryName のセッションがあればそれを読む。なければ（選んでいない・見つからない）セッションの一覧を返す。
     *   中身は展開せず、必要な track.csv だけを流し読みする。
     * @throws io.github.eightbrows.navhud.core.io.SessionZipException 壊れた zip、track.csv のない zip
     */
    fun open(uri: Uri, entryName: String?): OpenedTrack {
        val name = displayName(uri)
        val head = ByteArray(4)
        val n = stream(uri).use { it.read(head) }
        if (!SessionZip.isZip(head.copyOf(maxOf(n, 0)))) {
            return OpenedTrack.Track(LoadedTrack(uri, name, stream(uri).use { TrackCsv.parse(it) }))
        }
        if (entryName != null) {
            stream(uri).use { SessionZip.read(it, entryName) }?.let { result ->
                val session = SessionZip.sessionName(entryName)
                return OpenedTrack.Track(LoadedTrack(uri, if (session.isEmpty()) name else "$name / $session", result))
            }
        }
        return OpenedTrack.Sessions(ZipSessions(uri, name, stream(uri).use { SessionZip.list(it) }))
    }

    private fun stream(uri: Uri) =
        appContext.contentResolver.openInputStream(uri) ?: error(appContext.getString(R.string.err_file_open))

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

        /** CSV は端末によって MIME が違うので広めに受ける。GpsLogger の zip も選べる。 */
        val MIME_TYPES = arrayOf(
            "text/*", "application/csv", "application/octet-stream",
            "application/zip", "application/x-zip-compressed", "application/x-zip",
        )

        private val DOWNLOADS_URI: Uri =
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
    }
}
