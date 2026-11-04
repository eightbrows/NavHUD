package io.github.eightbrows.navhud.source

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** 選んだファイル（URI）の読み書きの権限と、開けるかどうか（20261104-D03。最近のファイルで使う）。 */
object FileAccess {

    /** 永続化した読み取り・書き込みの権限を返す（持っていなければ何もしない） */
    fun release(context: Context, uri: String) {
        val u = uri.toUri()
        for (flag in listOf(Intent.FLAG_GRANT_READ_URI_PERMISSION, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
            try {
                context.applicationContext.contentResolver.releasePersistableUriPermission(u, flag)
            } catch (_: SecurityException) {
            }
        }
    }

    /** 今そのファイルを開けるか（消えた・権限がなくなったなら false）。ブロッキングなので IO スレッドで呼ぶ */
    fun canOpen(context: Context, uri: String): Boolean = runCatching {
        context.applicationContext.contentResolver.openAssetFileDescriptor(uri.toUri(), "r")?.use { true } ?: false
    }.getOrDefault(false)
}
