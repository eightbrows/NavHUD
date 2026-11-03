package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 読み込んだ結果のひとこと（20261103-D01）の中身。文は画面の側で言語のリソースから組み立てる
 * （「WP 6 件（うち無効 1）を読み込みました（wp_profile.csv）」「軌跡 11,172 点・7:52〜11:01（track.csv）」）。Android に依存しない。
 */
object LoadSummary {

    /** WP の数と、そのうち無効の数 */
    data class Waypoints(val count: Int, val disabled: Int)

    /** 軌跡の点の数（3 桁ごとにカンマ）と、最初と最後の時刻（ローカル。同じ日なら "7:52"、日をまたぐなら "8/14 7:52"） */
    data class Track(val points: String, val start: String, val end: String)

    private val TIME = DateTimeFormatter.ofPattern("H:mm", Locale.US)
    private val DATE_TIME = DateTimeFormatter.ofPattern("M/d H:mm", Locale.US)

    fun waypoints(wps: List<Waypoint>): Waypoints = Waypoints(wps.size, wps.count { !it.enabled })

    /** 点がなければ null */
    fun track(fixes: List<Fix>, zone: ZoneId): Track? {
        if (fixes.isEmpty()) return null
        val start = Instant.ofEpochMilli(fixes.minOf { it.timeMs }).atZone(zone)
        val end = Instant.ofEpochMilli(fixes.maxOf { it.timeMs }).atZone(zone)
        val f = if (start.toLocalDate() == end.toLocalDate()) TIME else DATE_TIME
        return Track("%,d".format(Locale.US, fixes.size), start.format(f), end.format(f))
    }
}
