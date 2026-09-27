package io.github.eightbrows.navhud.source

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.PositionSource
import io.github.eightbrows.navhud.core.sensor.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 実機の GPS（§6.8）。LocationManager の GPS_PROVIDER を 1 秒間隔で使う（FusedLocation は使わない）。
 * 位置情報の権限（ACCESS_FINE_LOCATION）がないと SecurityException で Flow が終わる。
 */
class LiveGpsSource(context: Context, private val onGpsEnabledChanged: (Boolean) -> Unit = {}) : PositionSource {

    private val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    override val fixes: Flow<Fix> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            override fun onProviderEnabled(provider: String) = onGpsEnabledChanged(true)

            override fun onProviderDisabled(provider: String) = onGpsEnabledChanged(false)
        }
        onGpsEnabledChanged(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
        awaitClose { lm.removeUpdates(listener) }
    }

    companion object {
        const val INTERVAL_MS = 1_000L

        /** 最後に分かっている位置（偏角の計算用）。権限がなければ null。 */
        @SuppressLint("MissingPermission")
        fun lastKnown(context: Context): Location? = try {
            val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
    }
}

/** Location → Fix。値がないもの（hasXxx() が false）は null にして純粋関数に渡す。 */
fun Location.toFix(): Fix = LocationFix.toFix(
    timeMs = time,
    lat = latitude,
    lon = longitude,
    altitudeM = if (hasAltitude()) altitude else null,
    speedMps = if (hasSpeed()) speed else null,
    bearingDeg = if (hasBearing()) bearing else null,
    bearingAccDeg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasBearingAccuracy()) bearingAccuracyDegrees else null,
    horizAccM = if (hasAccuracy()) accuracy else null,
)
