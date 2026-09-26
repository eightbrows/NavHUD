package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.SourceMode

/** 実際に使う方位。deg == null のとき src == NONE。 */
data class Heading(val deg: Float?, val src: HeadingSrc) {
    companion object {
        val NONE = Heading(null, HeadingSrc.NONE)
    }
}

/**
 * 方位ソースの選択（§5.2）。最後に使えた GPS 方位を保持するので状態を持つ。
 * @param minSpeedMps GPS 方位を使う最低速度
 * @param maxAccM GPS 方位を使う最大の水平精度
 */
class HeadingSelector(
    var minSpeedMps: Float = 1.4f,
    var maxAccM: Float = 15f,
) {
    /** 最後に使えた GPS 方位。 */
    var lastGpsDeg: Float? = null
        private set

    fun isGpsUsable(fix: Fix): Boolean {
        val speed = fix.speedMps
        val acc = fix.horizAccM
        return fix.bearingDeg != null &&
            (speed == null || speed >= minSpeedMps) &&
            (acc == null || acc <= maxAccM)
    }

    /**
     * @param fix 最新の Fix（なければ null）
     * @param compassDeg コンパス方位（真北）。センサがない端末や未取得なら null
     */
    fun select(mode: SourceMode, fix: Fix?, compassDeg: Float?): Heading {
        val gpsNow = fix?.takeIf { isGpsUsable(it) }?.bearingDeg
        if (gpsNow != null) lastGpsDeg = gpsNow
        val held = lastGpsDeg?.let { Heading(it, HeadingSrc.GPS) } ?: Heading.NONE
        val compass = compassDeg?.let { Heading(it, HeadingSrc.COMPASS) }

        return when (mode) {
            SourceMode.GPS -> held
            SourceMode.COMPASS -> compass ?: Heading.NONE
            SourceMode.HYBRID -> when {
                gpsNow != null -> Heading(gpsNow, HeadingSrc.GPS)
                compass != null -> compass
                else -> held
            }
        }
    }

    fun reset() {
        lastGpsDeg = null
    }
}
