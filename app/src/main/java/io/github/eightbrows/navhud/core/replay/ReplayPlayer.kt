package io.github.eightbrows.navhud.core.replay

import io.github.eightbrows.navhud.core.model.Fix

/**
 * リプレイの送出スケジュール。時刻順の Fix のうち、指定時刻までに出すべきものを返す。
 * 時計は持たない（ReplayClock の時刻を渡す）。
 */
/** シークの結果。last はシーク先の Fix、passed は前方へのシークで飛ばした Fix（時刻順）。 */
data class SeekResult(val last: Fix?, val passed: List<Fix>)

class ReplayPlayer(private val track: List<Fix>) {

    private var next = 0

    init {
        require(track.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }) { "Fix が時刻順ではありません" }
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
        return SeekResult(track.getOrNull(lo - 1), passed)
    }

    /** 最後の Fix の時刻。トラックが空なら null。 */
    val endMs: Long? get() = track.lastOrNull()?.timeMs
}
