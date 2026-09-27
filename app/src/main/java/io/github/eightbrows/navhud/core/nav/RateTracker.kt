package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix

/** RATE の結果（§5.3）。altDiffM は base か最新の高度がなければ null。 */
data class Rate(
    val windowSec: Int,
    val distanceM: Double,
    val altDiffM: Double?,
    val avgSpeedMps: Double,
)

/**
 * 直近の Fix を保持して、窓ごとの移動距離・高度差・平均速度を出す（§5.3）。
 * @param maxWindowSec 求める窓の最大（これ + 余裕分だけ履歴を持つ）
 */
class RateTracker(private val maxWindowSec: Int = 60) {

    private val history = ArrayDeque<Fix>()

    fun add(fix: Fix) {
        // リプレイの巻き戻しなどで時刻が戻ったら履歴を捨てる
        if (history.isNotEmpty() && fix.timeMs < history.last().timeMs) history.clear()
        history.addLast(fix)
        val keepFrom = fix.timeMs - maxWindowSec * 1000L - BASE_MAX_LAG_MS - KEEP_MARGIN_MS
        while (history.first().timeMs < keepFrom) history.removeFirst()
    }

    fun clear() = history.clear()

    /** 窓 windowSec 秒の RATE。base がない・欠損で古すぎるときは null。 */
    fun rate(windowSec: Int): Rate? {
        require(windowSec in 1..maxWindowSec) { "windowSec: $windowSec" }
        val latest = history.lastOrNull() ?: return null
        val startMs = latest.timeMs - windowSec * 1000L
        val baseIdx = history.indexOfLast { it.timeMs <= startMs }
        if (baseIdx < 0) return null
        val base = history[baseIdx]
        if (startMs - base.timeMs > BASE_MAX_LAG_MS) return null

        var dist = 0.0
        for (i in baseIdx until history.lastIndex) {
            dist += segmentM(history[i], history[i + 1])
        }
        val durSec = (latest.timeMs - base.timeMs) / 1000.0
        val altDiff = if (latest.altRawM != null && base.altRawM != null) latest.altRawM - base.altRawM else null
        return Rate(windowSec, dist, altDiff, if (durSec > 0) dist / durSec else 0.0)
    }

    /**
     * ETA に使う平均速度 [m/s]。窓 windowSec 秒の RATE があればその平均速度。
     * 履歴が窓に足りない（走り始め・欠損のあと）ときは、窓の中にある分の平均速度。ある分が minSec 秒未満なら null。
     */
    fun etaSpeed(windowSec: Int, minSec: Int = MIN_ETA_HISTORY_SEC): Double? {
        rate(windowSec)?.let { return it.avgSpeedMps }
        val latest = history.lastOrNull() ?: return null
        val startMs = latest.timeMs - windowSec * 1000L
        val baseIdx = history.indexOfFirst { it.timeMs >= startMs }
        val base = history[baseIdx]
        val durMs = latest.timeMs - base.timeMs
        if (durMs < minSec * 1000L) return null
        var dist = 0.0
        for (i in baseIdx until history.lastIndex) {
            dist += segmentM(history[i], history[i + 1])
        }
        return dist / (durMs / 1000.0)
    }

    companion object {
        /** base が窓の起点よりこれを超えて古ければ欠損とみなす。 */
        const val BASE_MAX_LAG_MS = Tuning.RATE_BASE_MAX_LAG_MS

        /** 窓に足りない履歴で ETA を出すときの、最低の長さ [秒]。 */
        const val MIN_ETA_HISTORY_SEC = Tuning.ETA_MIN_HISTORY_SEC

        /** この間隔を超える区間は欠損として直線距離で数える。 */
        const val GAP_SEGMENT_MS = Tuning.RATE_GAP_SEGMENT_MS

        private const val KEEP_MARGIN_MS = Tuning.RATE_KEEP_MARGIN_MS

        /** 区間の距離。両端の速度があれば台形積分、なければ・欠損区間なら直線距離。 */
        fun segmentM(a: Fix, b: Fix): Double {
            val dtMs = b.timeMs - a.timeMs
            val va = a.speedMps
            val vb = b.speedMps
            return if (va != null && vb != null && dtMs <= GAP_SEGMENT_MS) {
                (va.toDouble() + vb.toDouble()) / 2.0 * dtMs / 1000.0
            } else {
                Geo.distanceM(a.lat, a.lon, b.lat, b.lon)
            }
        }
    }
}
