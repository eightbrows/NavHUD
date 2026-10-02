package io.github.eightbrows.navhud.core.replay

import io.github.eightbrows.navhud.core.Tuning

/** リプレイの倍速（§6.7）の上げ下げ。端では null（それ以上は変えられない）。 */
object ReplaySpeed {

    /** 1段遅く。いちばん遅い段なら null。 */
    fun slower(current: Int, speeds: List<Int> = Tuning.REPLAY_SPEEDS): Int? =
        speeds.indexOf(current).takeIf { it > 0 }?.let { speeds[it - 1] }

    /** 1段速く。いちばん速い段なら null。 */
    fun faster(current: Int, speeds: List<Int> = Tuning.REPLAY_SPEEDS): Int? =
        speeds.indexOf(current).takeIf { it in 0 until speeds.size - 1 }?.let { speeds[it + 1] }
}
