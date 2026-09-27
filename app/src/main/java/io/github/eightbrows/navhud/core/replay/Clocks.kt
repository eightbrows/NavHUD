package io.github.eightbrows.navhud.core.replay

/** NavState の「現在時刻」を出す時計（epoch ms）。 */
fun interface NavClock {
    fun nowMs(): Long
}

/** LIVE: 端末の時刻。 */
object LiveClock : NavClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

/**
 * REPLAY: トラックの時刻。再生中は実時間 × 倍速で進み、一時停止で止まる。
 * リプレイの Fix 送出もこの時計を基準にするので、欠損区間では Fix が来ずに時計だけ進む。
 *
 * @param realtimeMs 単調増加する実時間 [ms]（Android では SystemClock.elapsedRealtime）。テストでは差し替える
 */
class ReplayClock(private val realtimeMs: () -> Long) : NavClock {
    private var anchorTrackMs = 0L
    private var anchorRealMs = 0L

    var playing: Boolean = false
        private set

    /** 倍速（1 / 2 / 5 / 10 / 30）。 */
    var speed: Int = 1
        private set

    override fun nowMs(): Long =
        if (playing) anchorTrackMs + (realtimeMs() - anchorRealMs) * speed else anchorTrackMs

    /** 倍速を変える。今のトラック時刻から、新しい速さで進む。 */
    fun setSpeed(x: Int) {
        require(x >= 1) { "speed: $x" }
        anchorTrackMs = nowMs()
        anchorRealMs = realtimeMs()
        speed = x
    }

    /** トラック時刻を trackMs に合わせる。再生中ならそこから進み続ける。 */
    fun seek(trackMs: Long) {
        anchorTrackMs = trackMs
        anchorRealMs = realtimeMs()
    }

    fun play() {
        if (playing) return
        anchorRealMs = realtimeMs()
        playing = true
    }

    fun pause() {
        if (!playing) return
        anchorTrackMs = nowMs()
        playing = false
    }
}
