package io.github.eightbrows.navhud.source

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.PositionSource
import io.github.eightbrows.navhud.core.replay.ReplayClock
import io.github.eightbrows.navhud.core.replay.ReplayPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * track.csv のリプレイ（§6.7）。元の時刻間隔どおりに Fix を流す。
 * 送出は clock（トラック時刻）を基準にするので、欠損区間では Fix が来ずに時計だけ進む。
 * 操作は再生 / 一時停止のみ（clock の play / pause）。
 */
class ReplayPositionSource(
    track: List<Fix>,
    val clock: ReplayClock,
    private val pollMs: Long = POLL_MS,
) : PositionSource {

    private val player = ReplayPlayer(track)

    init {
        player.startMs?.let(clock::seek)
    }

    val startMs: Long? get() = player.startMs

    /** 全部出し終わったか。 */
    val finished: Boolean get() = player.finished

    /** 最後の Fix を出したら完了する。一時停止中は何も出さずに待つ。1回だけ collect すること。 */
    override val fixes: Flow<Fix> = flow {
        while (!player.finished) {
            player.due(clock.nowMs()).forEach { emit(it) }
            if (!player.finished) delay(pollMs)
        }
    }

    companion object {
        const val POLL_MS = 50L
    }
}
