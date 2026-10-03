package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Waypoint
import java.time.LocalTime
import kotlin.math.abs

/** WP 設定画面の「逆順にする」「時刻を一括調整」（§6.5）。Android に依存しない。リストは変えずに新しいリストを返す。 */
object WaypointTimes {

    private const val DAY_SEC = 24 * 3600L

    /** 逆順にする: 並びを反転し、到達済みをすべて解除する。 */
    fun reverse(wps: List<Waypoint>): List<Waypoint> = wps.reversed().map { it.copy(reached = false, reach = null) }

    /** 一括調整の基準の既定: 先頭の、有効で目標時刻の入った WP。なければ目標時刻の入った最初の WP。どちらもなければ null。 */
    fun defaultBaseIndex(wps: List<Waypoint>): Int? =
        wps.indexOfFirst { it.enabled && it.targetTime != null }.takeIf { it >= 0 }
            ?: wps.indexOfFirst { it.targetTime != null }.takeIf { it >= 0 }

    /**
     * 時刻を一括調整する。目標時刻の入った WP（無効 WP も含む）を並び順に取り出し、元の隣どうしの時間差の絶対値を保ったまま、
     * 基準の WP（baseIndex）の目標時刻を newTarget にして、前後の目標時刻を並び順に決め直す。
     * 締切時刻は、各 WP の「目標 → 締切」の差を保つ。目標時刻が空欄の WP は変えない（締切だけ入っていても変えない）。
     * 日付を持たないので、差は ±12 時間で折り返し（23:50 → 00:10 は 20 分）、結果は 24 時間で折り返す。
     * @return 基準の WP に目標時刻がなければ、元のリストのまま
     */
    fun adjust(wps: List<Waypoint>, baseIndex: Int, newTarget: LocalTime): List<Waypoint> {
        val timed = wps.indices.filter { wps[it].targetTime != null }
        val k = timed.indexOf(baseIndex)
        if (k < 0) return wps
        val sec = timed.map { wps[it].targetTime!!.toSecondOfDay().toLong() }
        // 隣どうしの時間差（絶対値）
        val gaps = sec.zipWithNext { a, b -> abs(fold(b - a)) }
        val newSec = LongArray(timed.size)
        newSec[k] = newTarget.toSecondOfDay().toLong()
        for (j in k + 1 until timed.size) newSec[j] = newSec[j - 1] + gaps[j - 1]
        for (j in k - 1 downTo 0) newSec[j] = newSec[j + 1] - gaps[j]

        val out = wps.toMutableList()
        for ((j, i) in timed.withIndex()) {
            val wp = wps[i]
            val target = time(newSec[j])
            val deadline = wp.deadlineTime?.let { d ->
                time(newSec[j] + fold(d.toSecondOfDay().toLong() - sec[j]))
            }
            out[i] = wp.copy(targetTime = target, deadlineTime = deadline)
        }
        return out
    }

    /** 秒の差を ±12 時間に折り返す（日付をまたぐ差）。 */
    private fun fold(d: Long): Long {
        var x = d % DAY_SEC
        if (x > DAY_SEC / 2) x -= DAY_SEC
        if (x <= -DAY_SEC / 2) x += DAY_SEC
        return x
    }

    private fun time(sec: Long): LocalTime = LocalTime.ofSecondOfDay(Math.floorMod(sec, DAY_SEC))
}
