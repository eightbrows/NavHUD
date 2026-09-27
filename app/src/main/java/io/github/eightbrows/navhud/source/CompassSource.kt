package io.github.eightbrows.navhud.source

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.eightbrows.navhud.core.sensor.AngleSmoother
import io.github.eightbrows.navhud.core.sensor.CompassMath
import io.github.eightbrows.navhud.core.sensor.CompassQuality
import io.github.eightbrows.navhud.core.sensor.Posture
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 偏角の計算に使う位置。 */
data class GeoPoint(val lat: Double, val lon: Double, val altM: Double, val timeMs: Long)

/** コンパスの1回分の値（真北基準、平滑化済み）。 */
data class CompassReading(val trueDeg: Float, val quality: CompassQuality, val posture: Posture)

/**
 * コンパス（§6.8）。TYPE_ROTATION_VECTOR を使い、なければ加速度＋地磁気、それもなければコンパスなし。
 * 平置き / 立て置き（車載ホルダー）を重力方向から判定して軸を選び、GeomagneticField で真北に補正する。
 *
 * @param location 偏角の計算に使う位置（今の Fix → 最後に分かっている位置）。null なら偏角 0（MAG の印）
 */
class CompassSource(context: Context, private val location: () -> GeoPoint?) {

    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** コンパスが使える端末か。 */
    val available: Boolean get() = rotation != null || (accel != null && magnet != null)

    val readings: Flow<CompassReading> = callbackFlow {
        val r = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        var hasGravity = false
        var hasGeomag = false
        var posture: Posture? = null
        var lowAccuracy = false
        val smoother = AngleSmoother(SMOOTHING)
        var declination = 0f
        // 最初の1回は必ず計算する（Long.MIN_VALUE だと now との差がオーバーフローする）
        var declinationAt: Long? = null
        var declinationUnknown = true

        fun declination(nowMs: Long): Float {
            // 偏角はゆっくりしか変わらないので、10 秒に1回だけ計算し直す
            val last = declinationAt
            if (last != null && nowMs - last < DECLINATION_INTERVAL_MS) return declination
            declinationAt = nowMs
            val p = location()
            declinationUnknown = p == null
            declination = if (p == null) 0f else GeomagneticField(p.lat.toFloat(), p.lon.toFloat(), p.altM.toFloat(), p.timeMs).declination
            return declination
        }

        fun emitFromRotationMatrix() {
            // 回転行列の3行目 = 上向き（重力の逆）を端末座標で表したもの
            posture = CompassMath.posture(r[6].toDouble(), r[7].toDouble(), r[8].toDouble(), posture)
            val m = if (posture == Posture.UPRIGHT) {
                // 立て置き: 背面が向く方向を機首方位にする
                SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                remapped
            } else {
                r
            }
            SensorManager.getOrientation(m, orientation)
            val now = System.currentTimeMillis()
            val magnetic = CompassMath.azimuthToDeg(orientation[0].toDouble())
            val trueDeg = smoother.update(CompassMath.trueHeading(magnetic, declination(now).toDouble()))
            trySend(CompassReading(trueDeg.toFloat(), CompassQuality(lowAccuracy, declinationUnknown), posture!!))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(r, e.values)
                        emitFromRotationMatrix()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        e.values.copyInto(gravity, endIndex = 3)
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        e.values.copyInto(geomag, endIndex = 3)
                        hasGeomag = true
                        lowAccuracy = e.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        if (rotation == null && hasGravity && SensorManager.getRotationMatrix(r, null, gravity, geomag)) {
                            emitFromRotationMatrix()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
                }
            }
        }

        if (rotation != null) {
            sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
            // 精度（CAL）を知るために地磁気センサも聞く
            magnet?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else if (accel != null && magnet != null) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        }
        awaitClose { sm.unregisterListener(listener) }
    }

    companion object {
        /** 平滑化の強さ（SENSOR_DELAY_UI ≒ 60ms ごとに 20% ずつ追従） */
        const val SMOOTHING = 0.2

        /** 偏角を計算し直す間隔 */
        const val DECLINATION_INTERVAL_MS = 10_000L
    }
}
