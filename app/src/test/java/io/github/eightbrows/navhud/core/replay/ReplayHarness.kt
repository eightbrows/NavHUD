package io.github.eightbrows.navhud.core.replay

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavState

/**
 * NavViewModel と同じ流れ（時計 → 送出 → NavEngine）を、偽の実時間で動かすテスト用の仕組み。
 * 実時間を stepMs ずつ進め、そのたびに「出すべき Fix を onFix」「onTick」を行う。
 */
class ReplayHarness(track: List<Fix>, val engine: NavEngine) {
    var realMs = 0L
        private set
    val clock = ReplayClock { realMs }
    private val player = ReplayPlayer(track)

    /** 受け取った Fix ごとの NavState。 */
    val fixStates = mutableListOf<NavState>()

    init {
        clock.seek(player.startMs ?: 0L)
        pump()
    }

    fun play() = clock.play()

    fun pause() = clock.pause()

    /** トラック時刻が targetMs になるまで実時間を進める（最後の一歩は targetMs ちょうどに合わせる）。 */
    fun advanceTo(targetMs: Long, stepMs: Long = 100): NavState {
        require(clock.playing) { "再生中でないと時計は進まない" }
        while (clock.nowMs() < targetMs) {
            realMs += minOf(stepMs, targetMs - clock.nowMs())
            pump()
        }
        return engine.state
    }

    /** 実時間を ms だけ進める（一時停止中なら時計は止まったまま）。 */
    fun advanceBy(ms: Long): NavState {
        realMs += ms
        return pump()
    }

    private fun pump(): NavState {
        val now = clock.nowMs()
        for (f in player.due(now)) fixStates += engine.onFix(f, now)
        return engine.onTick(now)
    }
}
