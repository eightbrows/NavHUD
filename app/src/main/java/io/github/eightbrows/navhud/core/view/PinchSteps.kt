package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.Tuning

/**
 * ピンチ（§6.12）: 2本指の開き具合から、縮尺の段をいくつ変えるかを決める。
 * - 最初の開き具合を基準にする。基準の zoomInRatio 倍以上に開いたら1段拡大（距離のレンジを小さく）、
 *   zoomOutRatio 倍以下に閉じたら1段縮小。1段変えるたびに、基準をその倍率の所に取り直す。
 * - 一度に大きく動いたら、越えた分だけ段を返す（1.25 倍ずつなら、1.6 倍で2段）。
 * - 指が1本になった・離れたら reset する（次に2本になったときの開き具合が新しい基準）。
 * 使う段・最小・最大の段は見ない（変える処理の側で、＋ / − と同じに止める）。
 */
class PinchSteps(
    private val zoomInRatio: Float = Tuning.PINCH_ZOOM_IN_RATIO,
    private val zoomOutRatio: Float = Tuning.PINCH_ZOOM_OUT_RATIO,
) {
    init {
        require(zoomInRatio > 1f) { "zoomInRatio must be > 1: $zoomInRatio" }
        require(zoomOutRatio > 0f && zoomOutRatio < 1f) { "zoomOutRatio must be in (0, 1): $zoomOutRatio" }
    }

    /** 基準の開き具合 [px]。まだなければ null */
    private var base: Float? = null

    /**
     * 今の開き具合 span [px] から、変える段の数を返す。正: 拡大（＋と同じ向き）、負: 縮小（−と同じ向き）、0: 変えない。
     * 0 以下・数でない値は無視する（2本目の指が置かれた直後など）。
     */
    fun update(span: Float): Int {
        if (!span.isFinite() || span <= 0f) return 0
        var b = base ?: run {
            base = span
            return 0
        }
        var steps = 0
        while (span >= b * zoomInRatio) {
            b *= zoomInRatio
            steps++
        }
        while (span <= b * zoomOutRatio) {
            b *= zoomOutRatio
            steps--
        }
        base = b
        return steps
    }

    fun reset() {
        base = null
    }

    companion object {
        /**
         * 指の開き具合 [px]: 指の重心から各指までの距離の平均（2本なら指の間隔の半分）。指が2本未満なら 0。
         * 置いたばかりの指も、今の位置で数える（最初の開き具合が基準になるように）。
         */
        fun span(points: List<P>): Float {
            if (points.size < 2) return 0f
            val cx = points.sumOf { it.x.toDouble() } / points.size
            val cy = points.sumOf { it.y.toDouble() } / points.size
            return points.sumOf { kotlin.math.hypot(it.x - cx, it.y - cy) }.div(points.size).toFloat()
        }
    }
}
