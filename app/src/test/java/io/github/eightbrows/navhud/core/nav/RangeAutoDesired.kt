package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning

// テスト用: 段の選び方（次の WP が収まる最小の段）を確かめる関数。本体の AUTO は RangeSelector が行う。

/**
 * 距離だけで見た、次の WP が「縮尺 × fitRatio」以内に収まる最小の段 [m]。どの段にも収まらなければ最大の段。
 * 次の WP がなければ null。
 * @param stepsM 有効な段 [m]（昇順）
 * @param groundSpeedMps 対地速度（判定に使わない。使わないことをテストで確かめる）
 */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
fun RangeAuto.desired(stepsM: List<Double>, nextWpDistanceM: Double?, groundSpeedMps: Float?, fitRatio: Double = Tuning.RANGE_DISTANCE_FIT_RATIO): Double? {
    if (nextWpDistanceM == null) return null
    return desired(stepsM) { nextWpDistanceM <= it * fitRatio }
}

/** fits（その縮尺で次の WP が表示枠に収まるか）が true になる最小の段。どの段にも収まらなければ最大の段。 */
@Suppress("UnusedReceiverParameter")
fun RangeAuto.desired(stepsM: List<Double>, fits: (rangeM: Double) -> Boolean): Double? {
    if (stepsM.isEmpty()) return null
    return stepsM.firstOrNull(fits) ?: stepsM.last()
}
