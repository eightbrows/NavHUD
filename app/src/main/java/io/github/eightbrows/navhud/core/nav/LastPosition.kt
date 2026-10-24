package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix

/**
 * 前回最後にいた位置を、いつ覚え直すか（20261024-D04）。覚えた位置は、次に起動したときに位置が来るまでの地図の中心にする（NavState.idleCenter）。
 * 最初の Fix はすぐ覚え、そのあとは前に覚えてから minMs [ms]（Fix の時刻）たつか、minM [m] 動いたら覚え直す（書き込みを減らすため）。
 * Android に依存しない。
 */
class LastPositionSaver(
    private val minMs: Long = Tuning.LAST_POSITION_SAVE_MIN_MS,
    private val minM: Double = Tuning.LAST_POSITION_SAVE_MIN_M,
) {
    private var saved: Fix? = null

    /** この Fix を覚えるべきなら true（そのとき、この Fix を覚えたことにする） */
    fun shouldSave(fix: Fix): Boolean {
        val last = saved
        val due = last == null ||
            fix.timeMs - last.timeMs >= minMs ||
            fix.timeMs < last.timeMs ||
            Geo.distanceM(last.lat, last.lon, fix.lat, fix.lon) >= minM
        if (due) saved = fix
        return due
    }

    companion object {
        /** 位置が来るまでの地図の中心: 覚えた位置、なければ皇居 */
        fun idleCenter(remembered: Pair<Double, Double>?): Pair<Double, Double> =
            remembered ?: (Tuning.DEFAULT_MAP_CENTER_LAT to Tuning.DEFAULT_MAP_CENTER_LON)
    }
}
