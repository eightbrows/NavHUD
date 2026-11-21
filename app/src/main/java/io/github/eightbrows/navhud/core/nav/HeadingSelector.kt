package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc

/**
 * 実際に使う方位。deg == null のとき src == NONE。
 * @param held GPS 方位を保持している（低速・停止中）。画面の HDG の付記は HLD（TRACK のときは false。付記は TRK）
 */
data class Heading(val deg: Float?, val src: HeadingSrc, val held: Boolean = false) {
    companion object {
        val NONE = Heading(null, HeadingSrc.NONE)
    }
}

/**
 * 方位ソースの選択（§5.2。20261121-D01 でコンパスを外し、位置から求める向き（TRK）を足した）。
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

    /**
     * 最後の Fix の GPS 方位をそのまま使えたか（保持していない・方位が使える）。false なら、画面の GPS の値は保持の値か
     * 「前に使えた値のまま」（20261121-D01。方位の出し方「GPS ＋ 位置」で、位置から求める向きに替える所）
     */
    var gpsFresh: Boolean = false
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
        gpsFresh = !holding && ok
    }

    /**
     * 使う方位を選ぶ。
     * @param method 方位の出し方（移動のプリセットの値）
     * @param noFix NO FIX 中なら true（古い Fix の方位を「今の GPS 方位」として使わない）
     * @param trackDeg 位置から求めた向き（TrackHeading.deg）。まだなければ null。方位の出し方が GPS_TRACK のときだけ使う
     */
    fun select(method: HeadingMethod, noFix: Boolean, trackDeg: Float? = null): Heading {
        val live = liveGpsDeg?.takeIf { !noFix }?.let { Heading(it, HeadingSrc.GPS) }
        val held = stableGpsDeg?.let { Heading(it, HeadingSrc.GPS, held = true) }
        return when (method) {
            HeadingMethod.GPS -> live ?: held ?: Heading.NONE
            // GPS の方位がこの Fix でそのまま使えるときは GPS。使えないとき（保持の間・方位精度が上限を超えて前の値のまま・NO FIX）は、
            // 位置から求めた向きがあればそれ（TRK）。なければ GPS と同じ
            HeadingMethod.GPS_TRACK -> live?.takeIf { gpsFresh }
                ?: trackDeg?.let { Heading(it, HeadingSrc.TRACK) }
                ?: live ?: held ?: Heading.NONE
            // 位置だけ（20261121-D04）: いつも位置から求めた向き。まだなければ GPS ＋ 位置で向きがまだないときと同じ
            HeadingMethod.TRACK_ONLY -> trackDeg?.let { Heading(it, HeadingSrc.TRACK) }
                ?: live ?: held ?: Heading.NONE
        }
    }

    fun reset() {
        holding = false
        liveGpsDeg = null
        stableGpsDeg = null
        gpsFresh = false
    }
}
