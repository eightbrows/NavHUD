package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import kotlin.math.exp

/**
 * 軌跡の点。速さ・水平精度は軌跡のなめらかさ（20261122-D04）に使う（その点の Fix の値。なければ null）。
 * 画面に描くのは位置だけ。
 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val speedMps: Float? = null,
    val horizAccM: Float? = null,
)

/** 軌跡の表示（§6.7）の計算。Android に依存しない。 */
object Trail {

    /** REPLAY のトラック全体: 前の点から minM 未満の点を間引く（最初と最後の点は残す）。 */
    fun decimate(fixes: List<Fix>, minM: Double = Tuning.TRACK_DECIMATE_M): List<TrackPoint> = thin(fixes.map { it.toPoint() }, minM)

    /**
     * 軌跡の点を、前に残した点から minM 未満の点を間引く（最初と最後の点は残す。20261122-D03 で TrackPoint にも使えるようにした）。
     * 間引いた結果をもう一度同じ minM で間引いても変わらない
     */
    fun thin(points: List<TrackPoint>, minM: Double): List<TrackPoint> {
        if (points.isEmpty()) return emptyList()
        val out = mutableListOf(points.first())
        for (p in points.subList(1, points.size)) {
            val last = out.last()
            if (Geo.distanceM(last.lat, last.lon, p.lat, p.lon) >= minM) out += p
        }
        val end = points.last()
        if (out.last() != end) out += end
        return out
    }

    /** LIVE の軌跡の点の間隔 [m]: 軌跡の点の間隔（プリセットの値）の半分（自動車 10 m → 5 m、歩行 3 m → 1.5 m） */
    fun liveStepM(trackStepM: Int): Double = trackStepM / 2.0

    /**
     * 2 点の間に歩いた距離 [m]（RateTracker.segmentM と同じ: 両方に速さがあり、間が Tuning.RATE_GAP_SEGMENT_MS 以内なら速さの台形、
     * そうでなければ直線の距離）。止まっている間の位置の揺れは数えない
     */
    internal fun walkedM(a: TrackPoint, b: TrackPoint): Double {
        val va = a.speedMps
        val vb = b.speedMps
        val dtMs = b.timeMs - a.timeMs
        return if (va != null && vb != null && dtMs <= Tuning.RATE_GAP_SEGMENT_MS) {
            (va.toDouble() + vb.toDouble()) / 2.0 * dtMs / 1000.0
        } else {
            Geo.distanceM(a.lat, a.lon, b.lat, b.lon)
        }
    }

    /**
     * 軌跡のなめらかさ（20261122-D04。調べ 20261122-SMOOTH の「前後 σ」）: 点ごとに、歩いた距離で前後 Tuning.TRACK_SMOOTH_WINDOW_SIGMA × σ 以内の点の位置を、
     * ガウスの重み exp(−(距離 / σ)² / 2) で平均する。点の間が gapMs を超える（時刻が戻る）所はまたがない。水平精度が
     * Tuning.TRACK_SMOOTH_MAX_ACC_M を超える点は平均に使わない（使える点がなければその点のまま）。点の数・時刻は変えない。
     * 止まっている間は歩いた距離が増えないので、止まった所の点は 1 か所にまとまる。sigmaM が 0 以下なら、そのまま返す
     */
    fun smooth(points: List<TrackPoint>, sigmaM: Double, gapMs: Long): List<TrackPoint> {
        if (sigmaM <= 0.0 || points.size < 2) return points
        val n = points.size
        val path = DoubleArray(n)
        val seg = IntArray(n)
        for (i in 1 until n) {
            val a = points[i - 1]
            val b = points[i]
            val dt = b.timeMs - a.timeMs
            if (dt < 0 || dt > gapMs) {
                seg[i] = seg[i - 1] + 1
                path[i] = path[i - 1]
            } else {
                seg[i] = seg[i - 1]
                path[i] = path[i - 1] + walkedM(a, b)
            }
        }
        val win = Tuning.TRACK_SMOOTH_WINDOW_SIGMA * sigmaM
        var lo = 0
        var hi = 0
        return List(n) { i ->
            while (seg[lo] != seg[i] || path[i] - path[lo] > win) lo++
            if (hi < i) hi = i
            while (hi + 1 < n && seg[hi + 1] == seg[i] && path[hi + 1] - path[i] <= win) hi++
            average(points, i, lo, hi, path, sigmaM)
        }
    }

    /** 点 center を、lo..hi（同じ区間で窓の中の点）の位置のガウスの重みの平均にする */
    private fun average(points: List<TrackPoint>, center: Int, lo: Int, hi: Int, path: DoubleArray, sigmaM: Double): TrackPoint {
        var sw = 0.0
        var slat = 0.0
        var slon = 0.0
        val pc = path[center]
        for (j in lo..hi) {
            val q = points[j]
            val acc = q.horizAccM
            if (acc != null && acc > Tuning.TRACK_SMOOTH_MAX_ACC_M) continue
            val u = (path[j] - pc) / sigmaM
            val w = exp(-0.5 * u * u)
            sw += w
            slat += w * q.lat
            slon += w * q.lon
        }
        val p = points[center]
        return if (sw > 0.0) p.copy(lat = slat / sw, lon = slon / sw) else p
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

    internal fun Fix.toPoint() = TrackPoint(lat, lon, timeMs, speedMps, horizAccM)

    /** なめらかにして（sigmaM が 0 ならそのまま）、間引く（読み込んだ軌跡。20261122-D04） */
    fun smoothThenThin(points: List<TrackPoint>, sigmaM: Double, gapMs: Long, stepM: Double): List<TrackPoint> =
        thin(smooth(points, sigmaM, gapMs), stepM)
}

/**
 * LIVE の軌跡（起動してからの分。保存はしない）。前の点から minStepM 以上動いたときだけ足し、
 * maxPoints を超えたら古い方から捨てる。
 *
 * 軌跡のなめらかさ（20261122-D04）: smoothM（σ）が 0 より大きいと、過去の点だけでなめらかにする。来た点を持っておき、その点から
 * Tuning.TRACK_SMOOTH_WINDOW_SIGMA × σ 歩いた先の点が来たら（前後の窓がそろったら）、読み込んだ軌跡と同じ平均でその点の位置を決めて足す
 * （σ 3 m なら 9 m 遅れて決まる）。まだ決まっていない自機のすぐ後ろは、今と同じ線（来た点を minStepM ごと）でつなぐ。
 * 測位の欠け（gapMs を超える間・時刻が戻る）では、それまでの点を前の点だけで決める。
 */
class LiveTrail(
    /** 前の点からこれ以上動いたら足す [m]（20261122-D03 から移動のプリセットの値で変わる。足してある点はそのまま） */
    var minStepM: Double = Tuning.LIVE_TRAIL_MIN_STEP_M,
    private val maxPoints: Int = Tuning.LIVE_TRAIL_MAX_POINTS,
) {
    private class Sample(val p: TrackPoint, val pathM: Double)

    /** 軌跡のなめらかさ σ [m]（0 = なし）。変えると、まだ決まっていない点は前の点だけで決めてから切り替える */
    var smoothM: Double = 0.0
        set(v) {
            if (v != field) {
                flush()
                field = v
                rebuild()
            }
        }

    /** 測位の欠けとみなす点の間 [ms]（なめらかさの区切り） */
    var gapMs: Long = 10_000L

    /** 決まった点（なめらかにした・間引いた） */
    private val deque = ArrayDeque<TrackPoint>()

    /** なめらかさの窓の点（同じ区間。古い順）と、そのうち決まった点の数 */
    private val buf = ArrayList<Sample>()
    private var done = 0

    /** 画面に渡す軌跡（変わったときだけ作り直す）。 */
    var points: List<TrackPoint> = emptyList()
        private set

    /** 点を足したら true（なめらかさがあるときは、自機のすぐ後ろが変わるので、点が来るたびに true）。 */
    fun add(fix: Fix): Boolean {
        val p = TrackPoint(fix.lat, fix.lon, fix.timeMs, fix.speedMps, fix.horizAccM)
        if (smoothM <= 0.0) {
            if (!push(p)) return false
            points = deque.toList()
            return true
        }
        val last = buf.lastOrNull()
        val pathM = if (last == null) {
            0.0
        } else {
            val dt = p.timeMs - last.p.timeMs
            if (dt < 0 || dt > gapMs) {
                flush()
                0.0
            } else {
                last.pathM + Trail.walkedM(last.p, p)
            }
        }
        buf += Sample(p, pathM)
        val win = Tuning.TRACK_SMOOTH_WINDOW_SIGMA * smoothM
        while (done < buf.size && buf.last().pathM - buf[done].pathM >= win) {
            finalize(done)
            done++
        }
        // 窓の外になった古い点を捨てる（決まっていない最初の点から win より前）
        val keepFrom = if (done < buf.size) buf[done].pathM - win else buf.last().pathM - win
        var drop = 0
        while (drop < done && buf[drop].pathM < keepFrom) drop++
        if (drop > 0) {
            buf.subList(0, drop).clear()
            done -= drop
        }
        rebuild()
        return true
    }

    /** buf[i] の位置を、窓の中の点（buf にある同じ区間の点）の平均で決めて、間引いて足す */
    private fun finalize(i: Int) {
        val win = Tuning.TRACK_SMOOTH_WINDOW_SIGMA * smoothM
        val c = buf[i]
        var sw = 0.0
        var slat = 0.0
        var slon = 0.0
        for (s in buf) {
            val d = s.pathM - c.pathM
            if (d < -win || d > win) continue
            val acc = s.p.horizAccM
            if (acc != null && acc > Tuning.TRACK_SMOOTH_MAX_ACC_M) continue
            val u = d / smoothM
            val w = exp(-0.5 * u * u)
            sw += w
            slat += w * s.p.lat
            slon += w * s.p.lon
        }
        push(if (sw > 0.0) c.p.copy(lat = slat / sw, lon = slon / sw) else c.p)
    }

    /** まだ決まっていない点を、今ある点だけで決める（区間の終わり・なめらかさを変えたとき） */
    private fun flush() {
        if (smoothM > 0.0) {
            while (done < buf.size) {
                finalize(done)
                done++
            }
        }
        buf.clear()
        done = 0
    }

    /** 決まった点に足す（前の点から minStepM 未満なら足さない）。足したら true */
    private fun push(p: TrackPoint): Boolean {
        val last = deque.lastOrNull()
        if (last != null && Geo.distanceM(last.lat, last.lon, p.lat, p.lon) < minStepM) return false
        deque.addLast(p)
        while (deque.size > maxPoints) deque.removeFirst()
        return true
    }

    /** 決まった点 ＋ 自機のすぐ後ろ（まだ決まっていない点を、今と同じく minStepM ごと） */
    private fun rebuild() {
        if (smoothM <= 0.0 || done >= buf.size) {
            points = deque.toList()
            return
        }
        val out = ArrayList<TrackPoint>(deque.size + buf.size - done)
        out.addAll(deque)
        var last = out.lastOrNull()
        for (k in done until buf.size) {
            val q = buf[k].p
            if (last == null || Geo.distanceM(last.lat, last.lon, q.lat, q.lon) >= minStepM) {
                out += q
                last = q
            }
        }
        points = out
    }

    fun clear() {
        deque.clear()
        buf.clear()
        done = 0
        points = emptyList()
    }
}
