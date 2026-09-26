package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * ステップ5（WP 設定画面）までの仮 WP。
 * トラックの累積距離で 25% / 50% / 75% の地点に WP1〜WP3 を置く。
 * WP2 には、その地点を通った時刻を目標時刻、その 10 分後を締切時刻として付ける。
 */
object TemporaryWaypoints {

    const val DEADLINE_AFTER_TARGET_MIN = 10L

    fun fromTrack(fixes: List<Fix>, zone: ZoneId): List<Waypoint> {
        if (fixes.size < 2) return emptyList()
        val cumulative = DoubleArray(fixes.size)
        for (i in 1 until fixes.size) {
            val a = fixes[i - 1]
            val b = fixes[i]
            cumulative[i] = cumulative[i - 1] + Geo.distanceM(a.lat, a.lon, b.lat, b.lon)
        }
        val total = cumulative.last()
        return listOf(0.25, 0.50, 0.75).mapIndexed { n, ratio ->
            val i = cumulative.indexOfFirst { it >= total * ratio }
            val f = fixes[i]
            val passTime = Instant.ofEpochMilli(f.timeMs).atZone(zone).toLocalTime().truncatedTo(ChronoUnit.SECONDS)
            Waypoint(
                name = "WP${n + 1}",
                lat = f.lat,
                lon = f.lon,
                eleM = null,
                targetTime = if (n == 1) passTime else null,
                deadlineTime = if (n == 1) passTime.plusMinutes(DEADLINE_AFTER_TARGET_MIN) else null,
            )
        }
    }
}
