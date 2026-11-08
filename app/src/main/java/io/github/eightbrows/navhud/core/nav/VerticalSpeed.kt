package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.model.Fix
import kotlin.math.exp

/**
 * V/S（昇降率。20261104-D03 で RATE を置き換え、20261108-D01 で出し方を変えた）[m/min]。上りが正。
 * 点ごとの傾き r =（今の高さ − 前の点の高さ）÷ 間 × 60 を、時定数 τ（Tuning.VS_TAU_SEC）の一次遅れでなまらせる:
 * V ← V + (1 − e^(−間/τ))(r − V)。計器の針のように、坂が変わると遅れて追いつく（63% まで約 τ 秒）。
 * 前の「直前 60 秒の差」より早く今の昇降率が分かる（20261105-R02-VS の比べ）。Android に依存しない。
 *
 * - 高さは ALT と同じもの（呼び出し側が渡す。Fix.mslAltM、なければ 楕円体高 − 標高オフセット）。高さのない Fix は使わない。
 * - 時刻は Fix の時刻（REPLAY は記録の時刻なので、倍速でも同じ値になる）。
 * - 測り始めは最初の傾き r から始め、測り始め（最初の点）から τ 秒たつまでは出さない（null。表示 "---"）。
 * - 点の間が maxGapMs（設定「測位が途切れたとみなす時間」）を超えたら、前の値を捨てて、その点から測り直す。時刻が戻ったときも測り直す。
 * - NO FIX のときに出さないのは呼び出し側（NavEngine）。
 */
class VerticalSpeedTracker(private val tauSec: Double = Tuning.VS_TAU_SEC) {

    private var startMs: Long? = null
    private var prevMs: Long = 0
    private var prevAltM: Double = 0.0
    private var v: Double? = null

    /**
     * 新しい点を入れる。
     * @param maxGapMs 点の間がこれを超えたら測り直す（設定「測位が途切れたとみなす時間」）
     * @param altM Fix の高さ [m]（ALT と同じもの）。なければ null（その点は使わない）
     */
    fun add(fix: Fix, maxGapMs: Long, altM: (Fix) -> Double?) {
        val a = altM(fix) ?: return
        val t = fix.timeMs
        val start = startMs
        if (start == null || t < prevMs || t - prevMs > maxGapMs) {
            // 測り始め・測り直し（欠けのあと、REPLAY の巻き戻し）
            startMs = t
            v = null
        } else if (t > prevMs) {
            val dtSec = (t - prevMs) / 1000.0
            val r = (a - prevAltM) / dtSec * 60.0
            v = v?.let { it + (1 - exp(-dtSec / tauSec)) * (r - it) } ?: r
        } else {
            // 同じ時刻の点（重なり）: 傾きは出せないので、高さだけ新しくする
        }
        prevMs = t
        prevAltM = a
    }

    fun clear() {
        startMs = null
        v = null
    }

    /** 今の V/S [m/min]。測り始めから τ 秒たっていなければ null */
    fun metersPerMinute(): Double? {
        val start = startMs ?: return null
        if (prevMs - start < (tauSec * 1000).toLong()) return null
        return v
    }
}
