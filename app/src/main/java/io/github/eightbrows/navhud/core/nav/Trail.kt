package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix

/** 軌跡の点。 */
data class TrackPoint(val lat: Double, val lon: Double, val timeMs: Long)

/** 軌跡の表示（§6.7）の計算。Android に依存しない。 */
object Trail {

    /** REPLAY のトラック全体: 前の点から minM 未満の点を間引く（最初と最後の点は残す）。 */
    fun decimate(fixes: List<Fix>, minM: Double = 10.0): List<TrackPoint> {
        if (fixes.isEmpty()) return emptyList()
        val out = mutableListOf(fixes.first().toPoint())
        for (f in fixes.subList(1, fixes.size)) {
            val last = out.last()
            if (Geo.distanceM(last.lat, last.lon, f.lat, f.lon) >= minM) out += f.toPoint()
        }
        val end = fixes.last().toPoint()
        if (out.last() != end) out += end
        return out
    }

    /** トラックのうち時刻 untilMs までの点の数（再生済みの部分。点は時刻順）。 */
    fun playedCount(points: List<TrackPoint>, untilMs: Long?): Int {
        if (untilMs == null) return 0
        var lo = 0
        var hi = points.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid].timeMs <= untilMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    internal fun Fix.toPoint() = TrackPoint(lat, lon, timeMs)
}

/**
 * LIVE の軌跡（起動してからの分。保存はしない）。前の点から minStepM 以上動いたときだけ足し、
 * maxPoints を超えたら古い方から捨てる。
 */
class LiveTrail(private val minStepM: Double = 5.0, private val maxPoints: Int = 5_000) {

    private val deque = ArrayDeque<TrackPoint>()

    /** 画面に渡す軌跡（点を足したときだけ作り直す）。 */
    var points: List<TrackPoint> = emptyList()
        private set

    /** 点を足したら true。 */
    fun add(fix: Fix): Boolean {
        val last = deque.lastOrNull()
        if (last != null && Geo.distanceM(last.lat, last.lon, fix.lat, fix.lon) < minStepM) return false
        deque.addLast(TrackPoint(fix.lat, fix.lon, fix.timeMs))
        while (deque.size > maxPoints) deque.removeFirst()
        points = deque.toList()
        return true
    }

    fun clear() {
        deque.clear()
        points = emptyList()
    }
}
