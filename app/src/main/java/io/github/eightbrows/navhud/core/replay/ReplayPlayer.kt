package io.github.eightbrows.navhud.core.replay

import io.github.eightbrows.navhud.core.model.Fix

/**
 * リプレイの送出スケジュール。時刻順の Fix のうち、指定時刻までに出すべきものを返す。
 * 時計は持たない（ReplayClock の時刻を渡す）。
 */
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
}
