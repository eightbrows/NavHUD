package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Waypoint
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong

/** ウェイポイントの計算（§5.4）。リストは変更せず、新しいリストを返す。 */
object WaypointNav {

    /** ETA を出す最低の平均速度。 */
    const val MIN_ETA_SPEED_MPS = Tuning.ETA_MIN_SPEED_MPS

    private const val HALF_DAY_SEC = 12 * 3600L
    private const val DAY_SEC = 24 * 3600L

    /** 次の目標 = enabled かつ未 reached のうちリスト順で最初。なければ null。 */
    fun nextIndex(wps: List<Waypoint>): Int? =
        wps.indexOfFirst { it.enabled && !it.reached }.takeIf { it >= 0 }

    /** 次の目標との距離が到達半径（WP ごとの値、なければ defaultRadiusM）以下なら reached にする。 */
    fun autoReach(wps: List<Waypoint>, lat: Double, lon: Double, defaultRadiusM: Double): List<Waypoint> {
        val i = nextIndex(wps) ?: return wps
        val wp = wps[i]
        if (Geo.distanceM(lat, lon, wp.lat, wp.lon) > (wp.radiusM ?: defaultRadiusM)) return wps
        return wps.toMutableList().also { it[i] = wp.copy(reached = true) }
    }

    /** 指定 WP の reached を反転する。他の WP は変えない。無効 WP には効かない。 */
    fun toggleReached(wps: List<Waypoint>, index: Int): List<Waypoint> {
        val wp = wps.getOrNull(index) ?: return wps
        if (!wp.enabled) return wps
        return wps.toMutableList().also { it[index] = wp.copy(reached = !wp.reached) }
    }

    /** ETA（epoch ms）= 現在時刻 + 直線距離 / 平均速度。平均速度がない・遅すぎるなら null。 */
    fun etaMs(nowMs: Long, distanceM: Double, avgSpeedMps: Double?): Long? {
        if (avgSpeedMps == null || avgSpeedMps < MIN_ETA_SPEED_MPS) return null
        return nowMs + (distanceM / avgSpeedMps * 1000).roundToLong()
    }

    /**
     * 対象時刻までの秒数（過ぎたら負）。日付を持たないので ±12時間を超えたら 24時間で折り返す。
     */
    fun countdownSec(now: LocalTime, target: LocalTime): Long {
        var d = target.toSecondOfDay().toLong() - now.toSecondOfDay()
        if (d > HALF_DAY_SEC) d -= DAY_SEC
        if (d < -HALF_DAY_SEC) d += DAY_SEC
        return d
    }

    /** nowMs をそのタイムゾーンのローカル時刻にしてからカウントダウンする。 */
    fun countdownSec(nowMs: Long, target: LocalTime, zone: ZoneId): Long =
        countdownSec(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime(), target)
}
