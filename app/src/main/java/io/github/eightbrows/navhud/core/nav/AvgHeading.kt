package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.Fix
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 平均した方位（20261122-D03）: 直近 windowSec 秒の HDG の平均の向き。画面に「平均した方位線」を描くだけに使う
 * （地図を回す向き・HDG の数値・到達の判定は、今までどおり今の HDG）。Android に依存しない。
 *
 * - 向きはベクトルで足す（角度をそのまま足さない。0° をまたいでも正しい）。
 * - 重みは、その点までに動いた距離（RateTracker.segmentM。2 秒以内で両端の速さがあれば台形、そうでなければ直線の距離）。
 *   止まっている間の点は重みがほぼ 0 なので、平均を引きずらない。
 * - 窓の中で動いた距離の合計が Tuning.AVG_HEADING_MIN_DIST_M 未満（ほとんど動いていない）なら、前の向きのまま（止まる前の向きを保つ）。
 * - 点の間が gapMs を超えたら（測位の欠け）・時刻が戻ったら、それまでの点と向きを捨てる。
 *
 * これから（今回は作らない）: ARC で地図をこの向きで回すときは、NavState.avgHeadingDeg を HudViewport の上の向きに使えばよい
 * （計算は今のまま。§ レポート 20261122-D03）。
 */
class AvgHeading {

    private data class Sample(val timeMs: Long, val weightM: Double, val deg: Float)

    private val samples = ArrayDeque<Sample>()
    private var prev: Fix? = null

    /** 今の平均の向き [°]（真北から時計回り）。まだなければ null */
    var deg: Float? = null
        private set

    /**
     * Fix を 1 つ入れる。
     * @param headingDeg この Fix のときの HDG（なければ null。その点は数えない）
     * @param windowSec 平均する秒数（移動のプリセットの値）
     * @param gapMs 点の間がこれを超えたら測位の欠けとみなす [ms]
     */
    fun update(fix: Fix, headingDeg: Float?, windowSec: Int, gapMs: Long) {
        val p = prev
        if (p != null && (fix.timeMs < p.timeMs || fix.timeMs - p.timeMs > gapMs)) reset()
        val w = prev?.let { RateTracker.segmentM(it, fix) } ?: 0.0
        prev = fix
        if (headingDeg != null) samples.addLast(Sample(fix.timeMs, w, headingDeg))
        while (samples.isNotEmpty() && fix.timeMs - samples.first().timeMs >= windowSec * 1000L) samples.removeFirst()
        compute()?.let { deg = it }
    }

    private fun compute(): Float? {
        var x = 0.0
        var y = 0.0
        var total = 0.0
        for (s in samples) {
            val r = Math.toRadians(s.deg.toDouble())
            x += s.weightM * sin(r)
            y += s.weightM * cos(r)
            total += s.weightM
        }
        // ほとんど動いていない・向きが打ち消し合う（行ったり来たり）ときは、前の向きのまま
        if (total < Tuning.AVG_HEADING_MIN_DIST_M || hypot(x, y) < total * Tuning.AVG_HEADING_MIN_RESULTANT) return null
        val d = Math.toDegrees(atan2(x, y))
        return ((d + 360.0) % 360.0).toFloat()
    }

    fun reset() {
        samples.clear()
        prev = null
        deg = null
    }

    companion object {
        /** 平均する秒数の範囲（3〜10 秒、1 秒刻み） */
        val WINDOW_SEC_RANGE = Tuning.AVG_HEADING_MIN_SEC..Tuning.AVG_HEADING_MAX_SEC
    }
}
