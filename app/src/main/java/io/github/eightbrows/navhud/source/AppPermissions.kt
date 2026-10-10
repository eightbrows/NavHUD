package io.github.eightbrows.navhud.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 設定画面の「権限」の欄（§6.9）に出す権限: 利用者が許可する・外すもの（位置情報と通知）。
 * 位置情報は「正確な位置」と「おおよその位置」を分ける（おおよその位置だけの許可では GPS を使えないため）。
 * フォアグラウンドサービスの権限（インストールしたときに自動で許可される）は出さない。
 */
enum class AppPermission {
    /** ACCESS_FINE_LOCATION: GPS で現在地を測る */
    FINE_LOCATION,

    /** ACCESS_COARSE_LOCATION: 正確な位置といっしょに求める */
    COARSE_LOCATION,

    /** POST_NOTIFICATIONS（Android 13 以降）: 動作中であることを通知に出す */
    NOTIFICATIONS,
}

/** 権限の今の状態。 */
data class PermissionStatus(val permission: AppPermission, val granted: Boolean)

object AppPermissions {

    /** 今の状態の一覧。画面に戻ってきたときに読み直す。 */
    fun statuses(context: Context): List<PermissionStatus> = buildList {
        add(PermissionStatus(AppPermission.FINE_LOCATION, granted(context, Manifest.permission.ACCESS_FINE_LOCATION)))
        add(PermissionStatus(AppPermission.COARSE_LOCATION, granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)))
        // 通知: Android 13 以降は POST_NOTIFICATIONS の許可、12 以前は端末の設定の「通知」のオン / オフ（どちらもここで分かる）
        add(PermissionStatus(AppPermission.NOTIFICATIONS, NotificationManagerCompat.from(context).areNotificationsEnabled()))
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
