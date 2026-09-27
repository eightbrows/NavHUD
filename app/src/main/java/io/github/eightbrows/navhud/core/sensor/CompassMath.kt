package io.github.eightbrows.navhud.core.sensor

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 端末の姿勢。 */
enum class Posture {
    /** 平置きに近い（画面が上を向く） */
    FLAT,

    /** 立てている（車載ホルダー）。画面の上が空、背面が進行方向 */
    UPRIGHT,
}

/** コンパスの状態。画面の SRC 表示に印を付けるのに使う。 */
data class CompassQuality(
    /** センサの精度が低い（キャリブレーションが必要）→ CAL */
    val lowAccuracy: Boolean = false,
    /** 偏角が分からず磁北のまま（位置が一度も分からない）→ MAG */
    val declinationUnknown: Boolean = false,
)

object CompassMath {

    /** 平置き → 立て置きに切り替える傾き [°] */
    const val TO_UPRIGHT_DEG = Tuning.COMPASS_TO_UPRIGHT_DEG

    /** 立て置き → 平置きに戻す傾き [°] */
    const val TO_FLAT_DEG = Tuning.COMPASS_TO_FLAT_DEG

    /**
     * 画面の法線（端末の z 軸）が鉛直からどれだけ傾いているか [°]。0 = 平置き、90 = 立て置き。
     * @param upX 上向き（重力の逆）の方向を端末座標で表したもの（回転行列の3行目、または加速度）
     */
    fun tiltDeg(upX: Double, upY: Double, upZ: Double): Double {
        val n = kotlin.math.sqrt(upX * upX + upY * upY + upZ * upZ)
        if (n == 0.0) return 0.0
        return Math.toDegrees(acos((abs(upZ) / n).coerceIn(0.0, 1.0)))
    }

    /**
     * 重力方向から姿勢を判定する。境目で行ったり来たりしないよう、35°〜55° の間は前の姿勢を保つ。
     */
    fun posture(upX: Double, upY: Double, upZ: Double, previous: Posture?): Posture {
        val tilt = tiltDeg(upX, upY, upZ)
        return when {
            tilt >= TO_UPRIGHT_DEG -> Posture.UPRIGHT
            tilt <= TO_FLAT_DEG -> Posture.FLAT
            else -> previous ?: if (tilt >= (TO_FLAT_DEG + TO_UPRIGHT_DEG) / 2) Posture.UPRIGHT else Posture.FLAT
        }
    }

    /** 磁北基準の方位に偏角（東偏が正）を足して真北基準にする。0..360。 */
    fun trueHeading(magneticDeg: Double, declinationDeg: Double): Double =
        Geo.normalize360(magneticDeg + declinationDeg)

    /** ラジアンの方位角（-π..π）を 0..360 の度へ。 */
    fun azimuthToDeg(azimuthRad: Double): Double = Geo.normalize360(Math.toDegrees(azimuthRad))
}

/**
 * 角度の平滑化。角度を単位ベクトルに直して指数移動平均をとるので、359° と 1° をまたいでも正しく扱える。
 * @param alpha 新しい値の重み（0..1、大きいほど追従が速い）
 */
class AngleSmoother(private val alpha: Double = 0.2) {
    private var x = 0.0
    private var y = 0.0
    private var started = false

    fun reset() {
        started = false
    }

    /** 値を1つ入れて、平滑化した角度（0..360）を返す。 */
    fun update(deg: Double): Double {
        val r = Math.toRadians(deg)
        if (!started) {
            x = cos(r)
            y = sin(r)
            started = true
        } else {
            x += alpha * (cos(r) - x)
            y += alpha * (sin(r) - y)
        }
        // 真逆の値が続いてベクトルがほぼ 0 になったら、新しい値をそのまま使う
        if (hypot(x, y) < 1e-6) {
            x = cos(r)
            y = sin(r)
        }
        return Geo.normalize360(Math.toDegrees(atan2(y, x)))
    }
}
