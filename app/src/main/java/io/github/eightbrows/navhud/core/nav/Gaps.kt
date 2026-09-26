package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix

/** 記録の欠損（テスト・デバッグ用）。from/to は欠損前後の Fix。 */
data class Gap(val from: Fix, val to: Fix) {
    val durationMs: Long get() = to.timeMs - from.timeMs
}

const val GAP_THRESHOLD_MS = 1_500L

/** 隣接 Fix の間隔 > thresholdMs を欠損とする。 */
fun detectGaps(fixes: List<Fix>, thresholdMs: Long = GAP_THRESHOLD_MS): List<Gap> =
    fixes.zipWithNext()
        .filter { (a, b) -> b.timeMs - a.timeMs > thresholdMs }
        .map { (a, b) -> Gap(a, b) }
