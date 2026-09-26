package io.github.eightbrows.navhud.core.nav

const val DEFAULT_LOST_TIMEOUT_MS = 10_000L

/** 最後の Fix から timeoutMs を超えたら lost。一度も測位していなければ lost（§5.5）。 */
fun isPositionLost(nowMs: Long, lastFixMs: Long?, timeoutMs: Long = DEFAULT_LOST_TIMEOUT_MS): Boolean =
    lastFixMs == null || nowMs - lastFixMs > timeoutMs
