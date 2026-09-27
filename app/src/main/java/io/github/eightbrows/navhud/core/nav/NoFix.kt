package io.github.eightbrows.navhud.core.nav

const val DEFAULT_NO_FIX_TIMEOUT_MS = 10_000L

/** 最後の Fix から timeoutMs を超えたら NO FIX。一度も測位していなければ NO FIX（§5.5）。 */
fun isNoFix(nowMs: Long, lastFixMs: Long?, timeoutMs: Long = DEFAULT_NO_FIX_TIMEOUT_MS): Boolean =
    lastFixMs == null || nowMs - lastFixMs > timeoutMs
