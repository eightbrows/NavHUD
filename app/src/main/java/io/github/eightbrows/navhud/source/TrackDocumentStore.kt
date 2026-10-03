package io.github.eightbrows.navhud.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import io.github.eightbrows.navhud.core.io.TrackCsv
import io.github.eightbrows.navhud.core.io.TrackParseResult

/** 読み込んだトラック。 */
data class LoadedTrack(val uri: Uri, val displayName: String, val result: TrackParseResult)

/**
 * リプレイ用 track.csv の選択結果を覚えておく（§6.7）。
 * ACTION_OPEN_DOCUMENT で選んだ URI の読み取り権限を永続化し、次回の起動で再利用する。
 */
class TrackDocumentStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val savedUri: Uri? get() = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    /** 選ばれた URI の読み取り権限を永続化して保存する。 */
    fun remember(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // 永続化できないプロバイダ。今回の起動中は読めるので、保存だけしておく
        }
        prefs.edit { putString(KEY_URI, uri.toString()) }
    }

    fun forget() {
        savedUri?.let { uri ->
            try {
                appContext.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
        }
        prefs.edit { remove(KEY_URI) }
    }

    /** URI のファイルを読む。ファイル削除・権限失効などで読めなければ例外を投げる。ブロッキングなので IO スレッドで呼ぶ。 */
    fun load(uri: Uri): LoadedTrack {
        val resolver = appContext.contentResolver
        val result = resolver.openInputStream(uri)?.use { TrackCsv.parse(it) }
            ?: error("ファイルを開けません")
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: uri.toString()
        return LoadedTrack(uri, name, result)
    }

    /** Download フォルダを最初に開くファイル選択。 */
    class OpenTrackDocument : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_URI)
    }

    companion object {
        private const val PREFS = "replay"
        private const val KEY_URI = "track_uri"

        /** CSV は端末によって MIME が違うので広めに受ける。 */
        val MIME_TYPES = arrayOf("text/*", "application/csv", "application/octet-stream")

        private val DOWNLOADS_URI: Uri =
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
    }
}
