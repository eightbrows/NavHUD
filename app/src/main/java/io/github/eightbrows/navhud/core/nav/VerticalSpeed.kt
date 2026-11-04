package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.Fix

/**
 * V/S（昇降率。20261104-D03 で RATE を置き換えた）: 直前 windowSec 秒（60 秒で固定）の高さの変化を、1 分あたり [m/min] で出す。
 * 高さは ALT と同じもの（呼び出し側が渡す。Fix.mslAltM、なければ 楕円体高 − 標高オフセット）。時刻は Fix の時刻
 * （REPLAY は記録の時刻なので、倍速でも同じ値になる）。Android に依存しない。
 *
 * 起点 = 最新の時刻 − windowSec。起点より前で最も新しい Fix を base とする。次のときは null（表示 "---"）:
 * - base がない（測り始めてから windowSec 秒分の記録がそろっていない）、base が起点より Tuning.RATE_BASE_MAX_LAG_MS を超えて古い
 * - base から最新までに、Fix の間が maxGapMs（測位が途切れたとみなす時間）を超えて空いた所がある（途中で途切れた）
 * - base か最新の高さがない
 */
class VerticalSpeedTracker(private val windowSec: Int = Tuning.VS_WINDOW_SEC) {

    private val history = ArrayDeque<Fix>()

    fun add(fix: Fix) {
        // リプレイの巻き戻しなどで時刻が戻ったら履歴を捨てる
        if (history.isNotEmpty() && fix.timeMs < history.last().timeMs) history.clear()
        history.addLast(fix)
        val keepFrom = fix.timeMs - windowSec * 1000L - Tuning.RATE_BASE_MAX_LAG_MS - Tuning.RATE_KEEP_MARGIN_MS
        while (history.first().timeMs < keepFrom) history.removeFirst()
    }

    fun clear() = history.clear()

    /**
     * V/S [m/min]。上りが正。
     * @param maxGapMs Fix の間がこれを超えて空いたら、途切れたとみなす（設定「測位が途切れたとみなす時間」）
     * @param altM Fix の高さ [m]（ALT と同じもの）。なければ null
     */
    fun metersPerMinute(maxGapMs: Long, altM: (Fix) -> Double?): Double? {
        val latest = history.lastOrNull() ?: return null
        val startMs = latest.timeMs - windowSec * 1000L
        val baseIdx = history.indexOfLast { it.timeMs <= startMs }
        if (baseIdx < 0) return null
        val base = history[baseIdx]
        if (startMs - base.timeMs > Tuning.RATE_BASE_MAX_LAG_MS) return null
        for (i in baseIdx until history.lastIndex) {
            if (history[i + 1].timeMs - history[i].timeMs > maxGapMs) return null
        }
        val a0 = altM(base) ?: return null
        val a1 = altM(latest) ?: return null
        val min = (latest.timeMs - base.timeMs) / 60_000.0
        return (a1 - a0) / min
    }
}
