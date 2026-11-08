package io.github.eightbrows.navhud.core.replay

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.Fix

/**
 * リプレイの送出スケジュール。時刻順の Fix のうち、指定時刻までに出すべきものを返す。
 * 時計は持たない（ReplayClock の時刻を渡す）。
 */
/**
 * シークの結果。last はシーク先の Fix、passed は前方へのシークで飛ばした Fix（時刻順）。
 * before は last の前の Tuning.VS_PRIME_MS（τ の 10 倍）の Fix（時刻順。last は含まない）。シークのすぐあとから V/S を出すために使う（20261104-D03・20261108-D01）
 */
data class SeekResult(val last: Fix?, val passed: List<Fix>, val before: List<Fix> = emptyList())

class ReplayPlayer(private val track: List<Fix>) {

    private var next = 0

    init {
        require(track.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }) { "Fixes are not in time order" }
    }

    /** 最初の Fix の時刻。トラックが空なら null。 */
    val startMs: Long? get() = track.firstOrNull()?.timeMs

    val finished: Boolean get() = next >= track.size

    /** 次に出す Fix の時刻。出し終わったら null。 */
    val nextTimeMs: Long? get() = track.getOrNull(next)?.timeMs

    /** 時刻 nowMs 以前でまだ出していない Fix を時刻順に返す。 */
    fun due(nowMs: Long): List<Fix> {
        val from = next
        while (next < track.size && track[next].timeMs <= nowMs) next++
        return track.subList(from, next)
    }

    fun rewind() {
        next = 0
    }

    /**
     * シーク: 時刻 trackMs 以前の Fix は出したことにする。
     * @return last = trackMs 以前の最後の Fix（すぐ画面に出すため。先頭より前なら null で、最初から出し直す）、
     *   passed = 前方へのシークで飛ばした Fix（まだ出していなかった、trackMs 以前の Fix。後方へのシークなら空）
     */
    fun seek(trackMs: Long): SeekResult {
        // trackMs より後の最初の Fix の位置（二分探索）
        var lo = 0
        var hi = track.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (track[mid].timeMs <= trackMs) lo = mid + 1 else hi = mid
        }
        val passed = if (lo > next) track.subList(next, lo) else emptyList()
        next = lo
        val last = track.getOrNull(lo - 1) ?: return SeekResult(null, passed)
        val from = last.timeMs - BEFORE_SPAN_MS
        var b = lo - 1
        while (b > 0 && track[b - 1].timeMs >= from) b--
        return SeekResult(last, passed, track.subList(b, lo - 1))
    }

    /** 最後の Fix の時刻。トラックが空なら null。 */
    val endMs: Long? get() = track.lastOrNull()?.timeMs

    companion object {
        /** SeekResult.before の長さ [ms]: V/S を作っておく長さ（τ の 10 倍。20261108-D01） */
        const val BEFORE_SPAN_MS = Tuning.VS_PRIME_MS
    }
}
