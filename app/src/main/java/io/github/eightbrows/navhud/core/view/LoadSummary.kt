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

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val DATE_HM = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm", Locale.US)

    /**
     * ファイルの画面の、読み込み中の軌跡の期間（20261104-D03）: 同じ日なら "2026-10-17 7:52〜11:02"、日をまたぐなら
     * "2026-10-17 23:10〜2026-10-18 1:05"
     */
    fun trackSpan(startMs: Long, endMs: Long, zone: ZoneId): String {
        val s = Instant.ofEpochMilli(startMs).atZone(zone)
        val e = Instant.ofEpochMilli(endMs).atZone(zone)
        val end = if (s.toLocalDate() == e.toLocalDate()) e.format(TIME) else e.format(DATE_HM)
        return "${s.format(DATE)} ${s.format(TIME)}〜$end"
    }

    /** 点の数（3 桁ごとにカンマ） */
    fun points(n: Int): String = "%,d".format(Locale.US, n)

    /** 最近のファイルの、最後に開いた日時（20261104-D03）: "2026-11-04 21:30" */
    fun openedAt(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).format(DATE_HM)

    /** 点がなければ null */
    fun track(fixes: List<Fix>, zone: ZoneId): Track? {
        if (fixes.isEmpty()) return null
        val start = Instant.ofEpochMilli(fixes.minOf { it.timeMs }).atZone(zone)
        val end = Instant.ofEpochMilli(fixes.maxOf { it.timeMs }).atZone(zone)
        val f = if (start.toLocalDate() == end.toLocalDate()) TIME else DATE_TIME
        return Track("%,d".format(Locale.US, fixes.size), start.format(f), end.format(f))
    }
}
