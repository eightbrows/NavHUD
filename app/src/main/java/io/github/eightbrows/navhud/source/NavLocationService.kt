package io.github.eightbrows.navhud.source

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.eightbrows.navhud.MainActivity
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.model.Fix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** サービスが受け取った GPS の Fix を、画面側（ViewModel）へ渡す。 */
object LiveLocationBus {
    private val _fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 16)
    val fixes: SharedFlow<Fix> = _fixes.asSharedFlow()

    private val _gpsEnabled = MutableStateFlow(true)

    /** 端末の位置情報（GPS）がオンか。 */
    val gpsEnabled: StateFlow<Boolean> = _gpsEnabled.asStateFlow()

    internal fun emit(fix: Fix) {
        _fixes.tryEmit(fix)
    }

    internal fun setGpsEnabled(enabled: Boolean) {
        _gpsEnabled.value = enabled
    }
}

/**
 * 位置の取得をするフォアグラウンドサービス（§6.8）。通知は固定の「NavHUD 動作中」のみ。
 * 画面が前面にある間だけ動かす（MainActivity の onStart で開始、onStop で停止）。
 */
class NavLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var gpsJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            // 権限がないなどでフォアグラウンドにできない
            stopSelf()
            return START_NOT_STICKY
        }
        if (gpsJob == null) {
            gpsJob = scope.launch {
                LiveGpsSource(this@NavLocationService, LiveLocationBus::setGpsEnabled).fixes
                    .catch { stopSelf() }
                    .collect(LiveLocationBus::emit)
            }
        }
        // 画面が裏に回ったら止めるので、勝手に再起動しない
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "動作中の表示", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_navhud)
            .setContentTitle("NavHUD 動作中")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "running"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NavLocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavLocationService::class.java))
        }
    }
}
