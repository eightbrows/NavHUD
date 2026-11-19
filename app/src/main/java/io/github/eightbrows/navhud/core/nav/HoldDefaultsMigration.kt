package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.io.KeyValueStore

/**
 * 方位を止める / 戻す速度の初期値の移し（20261119-D02）。20261119-D01 で初期値を 7 / 11 → 4 / 6 km/h にしたが、
 * 設定の保存は全部の値を書くので、前の版を使っていた端末には前の初期値 7 / 11 km/h が保存されている。
 * それを一度だけ新しい初期値にする:
 * - 「移した印」（KEY_DONE）の保存があれば何もしない（移すのは 1 回だけ。移したあとに選び直した 7 / 11 km/h は残る）
 * - 保存してある止める / 戻す速度が 7 / 11 km/h（一番近い km/h の整数で。D03 までの 2.0 / 3.0 m/s も含む）なら 4 / 6 km/h にする
 * - それ以外の値（自分で変えた値）・保存がない端末はそのまま
 * - どの場合も印を保存する。印は NavSettings の外なので、「設定を初期値に戻す」では消えない
 * Android に依存しない（SettingsStore が起動のたびに、読み込みの前に呼ぶ）。
 */
object HoldDefaultsMigration {
    /** 移した印の保存の名前。値は移した版 */
    const val KEY_DONE = "holdDefaultsMigrated"
    const val DONE_VALUE = "20261119-D02"

    /** 保存の名前（SettingsCodec と同じ） */
    const val KEY_ENTER = "holdEnterSpeedMps"
    const val KEY_EXIT = "holdExitSpeedMps"

    /** 前の初期値 [km/h] */
    const val OLD_ENTER_KMH = 7
    const val OLD_EXIT_KMH = 11

    /**
     * 印がなければ移して印を保存する。
     * @return 7 / 11 km/h を 4 / 6 km/h に移したら true（印があった・ほかの値・保存なしは false）
     */
    fun run(kv: KeyValueStore): Boolean {
        if (kv.get(KEY_DONE) != null) return false
        fun kmh(key: String): Int? =
            kv.get(key)?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() }?.let(NavSettings::holdSpeedKmh)
        val moved = kmh(KEY_ENTER) == OLD_ENTER_KMH && kmh(KEY_EXIT) == OLD_EXIT_KMH
        if (moved) {
            kv.put(KEY_ENTER, NavSettings.kmhToMps(Tuning.HOLD_ENTER_SPEED_DEFAULT_KMH).toString())
            kv.put(KEY_EXIT, NavSettings.kmhToMps(Tuning.HOLD_EXIT_SPEED_DEFAULT_KMH).toString())
        }
        kv.put(KEY_DONE, DONE_VALUE)
        return moved
    }
}
