package io.github.eightbrows.navhud.core.sensor

import io.github.eightbrows.navhud.core.model.Fix

/**
 * 端末の位置（android.location.Location の値）から Fix を作る。Location には依存しない。
 * 値がないもの（hasSpeed() などが false）は null で渡す。
 */
object LocationFix {

    /**
     * @param altitudeM 楕円体高（Location.getAltitude は WGS84 の楕円体高）
     * @param bearingAccDeg 方位の精度。端末が出さなければ null
     */
    fun toFix(
        timeMs: Long,
        lat: Double,
        lon: Double,
        altitudeM: Double?,
        speedMps: Float?,
        bearingDeg: Float?,
        bearingAccDeg: Float?,
        horizAccM: Float?,
    ): Fix {
        // 方位が使えないもの: 方位なし、精度 0 以下、停止中で精度も出ていない（track.csv と同じ扱い）
        val bearingValid = bearingDeg != null &&
            (bearingAccDeg == null || bearingAccDeg > 0f) &&
            !(speedMps == 0f && bearingAccDeg == null)
        return Fix(
            timeMs = timeMs,
            lat = lat,
            lon = lon,
            altRawM = altitudeM?.takeIf { it.isFinite() },
            speedMps = speedMps?.takeIf { it.isFinite() && it >= 0f },
            bearingDeg = if (bearingValid) bearingDeg?.takeIf { it.isFinite() }?.let(::normalizeDeg) else null,
            horizAccM = horizAccM?.takeIf { it.isFinite() },
            bearingAccDeg = if (bearingValid) bearingAccDeg?.takeIf { it.isFinite() } else null,
        )
    }

    /** 0..360 に直す。範囲内の値はそのまま（計算で誤差を入れない）。 */
    private fun normalizeDeg(d: Float): Float = if (d >= 0f && d < 360f) d else ((d % 360f) + 360f) % 360f
}
