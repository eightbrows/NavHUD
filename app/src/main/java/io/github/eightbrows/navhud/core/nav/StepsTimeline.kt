package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.io.StepsCsvResult
import io.github.eightbrows.navhud.core.model.Fix

/**
 * 歩数（steps.csv）を、軌跡の時刻（epoch ms）の上に置いたもの（20261124-D01）。Android に依存しない。
 *
 * 時刻合わせは経過時間（steps.csv の event_elapsed_ns と track.csv の elapsed_realtime_ns。どちらも端末の起動からの経過時間）だけで行う
 * （epoch_ms どうしでは合わせない。track.csv の epoch_ms は GNSS の時刻、steps.csv は端末の時計で、ずれがあるため）。
 * 歩数の経過時間 e が、軌跡の点 i と i+1 の経過時間の間（el_i < e ≤ el_i+1）にあれば、その 2 点の時刻の間に比例で置く
 * （点 i と i+1 の間の歩数は、REPLAY でもその 2 点の間に流れる）。最初の点より前・最後の点より後は、経過時間の差をそのまま足す。
 * detector（1 歩ごと）が主、counter（累計の更新）は補助。今は画面・計算には使わず、REPLAY で軌跡と一緒に流すだけ。
 */
class StepsTimeline private constructor(
    /** detector の時刻 [ms]（軌跡の時刻。昇順） */
    val detectorMs: DoubleArray,
    /** counter の時刻 [ms] と累計歩数（昇順） */
    val counterMs: DoubleArray,
    val counterCount: LongArray,
    /** detector の経過時間 [ns]（昇順。区間の歩数を経過時間で数える） */
    private val detectorNs: LongArray,
) {
    /** 歩数（detector の数） */
    val total: Int get() = detectorMs.size

    /** 時刻 tMs（軌跡の時刻）までの歩数（detector の時刻 ≤ tMs の数） */
    fun countUntil(tMs: Long): Int {
        var lo = 0
        var hi = detectorMs.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (detectorMs[mid] <= tMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** 時刻 tMs までで最後の歩の時刻 [ms]。まだなければ null */
    fun lastStepMs(tMs: Long): Long? = countUntil(tMs).takeIf { it > 0 }?.let { detectorMs[it - 1].toLong() }

    /** 経過時間で aNs < e ≤ bNs の detector の数（測位点 i と i+1 の間の歩数。仕様の計算例） */
    fun detectorsBetween(aNs: Long, bNs: Long): Int = upper(detectorNs, bNs) - upper(detectorNs, aNs)

    private fun upper(a: LongArray, v: Long): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] <= v) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /**
         * 軌跡の点（時刻の順。どの点にも elapsedNs がある）と steps.csv（並べ替え済み）から作る。
         * 軌跡に点がない・elapsedNs のない点があれば null（時刻合わせができない）
         */
        fun align(fixes: List<Fix>, csv: StepsCsvResult): StepsTimeline? {
            if (fixes.isEmpty() || fixes.any { it.elapsedNs == null }) return null
            val el = LongArray(fixes.size) { fixes[it].elapsedNs!! }
            val t = LongArray(fixes.size) { fixes[it].timeMs }
            fun toMs(e: Long): Double {
                if (e <= el[0]) return t[0] - (el[0] - e) / 1e6
                if (e > el[el.size - 1]) return t[t.size - 1] + (e - el[el.size - 1]) / 1e6
                // el[i] < e ≤ el[i+1] となる i
                var lo = 0
                var hi = el.size - 1
                while (hi - lo > 1) {
                    val mid = (lo + hi) ushr 1
                    if (el[mid] < e) lo = mid else hi = mid
                }
                val span = (el[hi] - el[lo]).toDouble()
                return if (span <= 0.0) t[hi].toDouble() else t[lo] + (e - el[lo]) / span * (t[hi] - t[lo])
            }
            val det = csv.detector.map { it.eventElapsedNs }
            return StepsTimeline(
                DoubleArray(det.size) { toMs(det[it]) },
                DoubleArray(csv.counter.size) { toMs(csv.counter[it].eventElapsedNs) },
                LongArray(csv.counter.size) { csv.counter[it].count ?: 0L },
                det.toLongArray(),
            )
        }
    }
}

/** 今の歩数（NavState。REPLAY で、歩数のある軌跡を読み込んでいるときだけ） */
data class StepsState(
    /** 記録の歩数（detector の数） */
    val total: Int,
    /** 今の時刻までの歩数 */
    val soFar: Int,
    /** 今の時刻までで最後の歩の時刻 [ms]（まだなければ null） */
    val lastStepMs: Long?,
)
