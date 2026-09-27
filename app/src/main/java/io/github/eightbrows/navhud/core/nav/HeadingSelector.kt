package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.SourceMode

/**
 * 実際に使う方位。deg == null のとき src == NONE。
 * @param held GPS 方位を保持している（低速・停止中）。画面の HDG の付記は HLD
 */
data class Heading(val deg: Float?, val src: HeadingSrc, val held: Boolean = false) {
    companion object {
        val NONE = Heading(null, HeadingSrc.NONE)
    }
}

/**
 * 方位ソースの選択（§5.2）。
 *
 * GPS 方位は低速でふらつくので、ヒステリシス付きで保持する:
 * - 速度が holdEnterSpeedMps 未満になったら保持に入る
 * - 速度が holdExitSpeedMps を超え、GPS 方位が使えるときに保持を解く
 * - 保持する値は、保持解除速度以上で走っていた間の最後の方位（減速中のふらついた値は使わない）
 *
 * 状態は Fix が来たとき（[update]）だけ変わる。[select] は何度呼んでも状態を変えない。
 */
class HeadingSelector(
    var holdEnterSpeedMps: Float = 2.0f,
    var holdExitSpeedMps: Float = 3.0f,
    var maxAccM: Float = 15f,
    var maxBearingAccDeg: Float = 20f,
) {
    /** GPS 方位を保持中か。 */
    var holding: Boolean = false
        private set

    /**
     * 今の GPS 方位（保持中は null）。走行中に方位の精度が悪い Fix が混ざったときは、直前の使えた値のまま
     * （1秒ごとに HLD と入れ替わらないように。HLD は速度による保持だけを表す）。
     */
    var liveGpsDeg: Float? = null
        private set

    /** 保持解除速度以上で走っていた間の最後の方位（保持中に使う）。 */
    var stableGpsDeg: Float? = null
        private set

    /** GPS 方位が使えるか（方位あり、水平精度・方位の精度が閾値以内）。速度は見ない。 */
    fun isGpsUsable(fix: Fix): Boolean {
        val acc = fix.horizAccM
        val bAcc = fix.bearingAccDeg
        return fix.bearingDeg != null &&
            (acc == null || acc <= maxAccM) &&
            (bAcc == null || bAcc <= maxBearingAccDeg)
    }

    /** Fix を1つ入れて、保持の状態を進める。 */
    fun update(fix: Fix) {
        val ok = isGpsUsable(fix)
        val speed = fix.speedMps
        if (holding) {
            if (ok && speed != null && speed > holdExitSpeedMps) holding = false
        } else {
            if (speed != null && speed < holdEnterSpeedMps) holding = true
        }
        liveGpsDeg = when {
            holding -> null
            ok -> fix.bearingDeg
            else -> liveGpsDeg
        }
        // 速度を出さない端末は、方位が使える限り安定とみなす
        if (!holding && ok && (speed == null || speed >= holdExitSpeedMps)) stableGpsDeg = fix.bearingDeg
    }

    /**
     * 使う方位を選ぶ。
     * @param noFix NO FIX 中なら true（古い Fix の方位を「今の GPS 方位」として使わない）
     * @param compassDeg コンパス方位（真北）。センサがない端末や未取得なら null
     * @param compassLowAccuracy コンパスの精度が低い（CAL）
     */
    fun select(mode: SourceMode, noFix: Boolean, compassDeg: Float?, compassLowAccuracy: Boolean = false): Heading {
        val live = liveGpsDeg?.takeIf { !noFix }?.let { Heading(it, HeadingSrc.GPS) }
        val held = stableGpsDeg?.let { Heading(it, HeadingSrc.GPS, held = true) }
        val compass = compassDeg?.let { Heading(it, HeadingSrc.COMPASS) }
        return when (mode) {
            SourceMode.GPS -> live ?: held ?: Heading.NONE
            SourceMode.COMPASS -> compass ?: Heading.NONE
            // GPS が使えないとき、コンパスが CAL でなければコンパス。CAL なら保持した GPS 方位
            SourceMode.HYBRID -> live
                ?: compass?.takeIf { !compassLowAccuracy }
                ?: held
                ?: compass
                ?: Heading.NONE
        }
    }

    fun reset() {
        holding = false
        liveGpsDeg = null
        stableGpsDeg = null
    }
}
