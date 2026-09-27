package io.github.eightbrows.navhud.core.nav

/** NO FIX とみなす時間の既定 [ms]。値は NavSettings の既定値（noFixTimeoutSec）から取る（二重に定義しない）。 */
val DEFAULT_NO_FIX_TIMEOUT_MS: Long = NavSettings().noFixTimeoutSec * 1000L

/** 最後の Fix から timeoutMs を超えたら NO FIX。一度も測位していなければ NO FIX（§5.5）。 */
fun isNoFix(nowMs: Long, lastFixMs: Long?, timeoutMs: Long = DEFAULT_NO_FIX_TIMEOUT_MS): Boolean =
    lastFixMs == null || nowMs - lastFixMs > timeoutMs
