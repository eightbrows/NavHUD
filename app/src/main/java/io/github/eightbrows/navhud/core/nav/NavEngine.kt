package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.sensor.CompassQuality
import java.time.LocalTime
import java.time.ZoneId

/**
 * NavState を更新する純粋ロジック。Android に依存しない。
 * 入力は「Fix 到着」「コンパス値」「時刻の tick」の3種類。時刻は常に呼び出し側が渡す。
 * スレッドセーフではないので、1つのスレッドから呼ぶこと。
 *
 * @param zone 目標/締切時刻（ローカル時刻）を解釈するタイムゾーン
 */
class NavEngine(
    settings: NavSettings = NavSettings(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    sourceKind: SourceKind = SourceKind.REPLAY,
) {
    var settings: NavSettings = settings
        private set

    private val headingSelector = HeadingSelector(settings.minGpsSpeedMps, settings.maxGpsAccM)
    private val rateTracker = RateTracker(maxWindowSec = NavSettings.RATE_WINDOW_CHOICES_SEC.max())
    private val passDetector = PassDetector()

    private var lastFix: Fix? = null
    private var compassDeg: Float? = null
    private var compassQuality = CompassQuality()
    private var waypoints: List<Waypoint> = emptyList()
    private var nowMs: Long? = null
    private var sourceKind = sourceKind
    private var playing = sourceKind == SourceKind.LIVE

    var state: NavState = NavState()
        private set

    init {
        recompute()
    }

    fun onFix(fix: Fix, nowMs: Long): NavState {
        // リプレイの巻き戻し・別ファイルなどで時刻が戻ったら、位置に関する履歴を捨てる
        lastFix?.let { if (fix.timeMs < it.timeMs) clearHistory() }
        lastFix = fix
        rateTracker.add(fix)
        waypoints = WaypointNav.autoReach(waypoints, fix.lat, fix.lon, settings.reachRadiusM)
        checkPass(fix)
        this.nowMs = nowMs
        return recompute()
    }

    /** @param deg コンパス方位（真北）。センサがなくなった・値が使えないなら null */
    /** @param nowMs 現在時刻。null なら時刻は進めない（REPLAY でトラックがまだないときなど） */
    fun onCompass(deg: Float?, nowMs: Long?, quality: CompassQuality = CompassQuality()): NavState {
        compassDeg = deg
        compassQuality = quality
        if (nowMs != null) this.nowMs = nowMs
        return recompute()
    }

    /** 時刻だけを進める。Fix が来なくても NO FIX とカウントダウンが更新される。 */
    fun onTick(nowMs: Long): NavState {
        this.nowMs = nowMs
        return recompute()
    }

    fun updateSettings(s: NavSettings): NavState {
        settings = s
        headingSelector.minSpeedMps = s.minGpsSpeedMps
        headingSelector.maxAccM = s.maxGpsAccM
        return recompute()
    }

    fun setSourceMode(mode: SourceMode): NavState = updateSettings(settings.copy(sourceMode = mode))

    fun setWaypoints(wps: List<Waypoint>): NavState {
        waypoints = wps
        return recompute()
    }

    fun toggleReached(index: Int): NavState {
        waypoints = WaypointNav.toggleReached(waypoints, index)
        return recompute()
    }

    fun setSource(kind: SourceKind, playing: Boolean): NavState {
        sourceKind = kind
        this.playing = playing
        return recompute()
    }

    /**
     * LIVE ⇔ REPLAY の切替。RATE の履歴・通過判定の記録・時刻をリセットし、WP の到達状態は残す。
     */
    fun switchSource(kind: SourceKind, playing: Boolean): NavState {
        clearHistory()
        nowMs = null
        sourceKind = kind
        this.playing = playing
        return recompute()
    }

    fun setPlaying(playing: Boolean): NavState {
        this.playing = playing
        return recompute()
    }

    /** 位置・時刻に関する状態を捨てる（新しいトラックを読んだときなど）。WP と設定は残す。 */
    fun resetPosition(): NavState {
        clearHistory()
        nowMs = null
        return recompute()
    }

    /** 通過判定（§5.4）。次の WP に最接近したあと離れていったら到達にする。 */
    private fun checkPass(fix: Fix) {
        val i = WaypointNav.nextIndex(waypoints)
        if (!settings.passDetection || i == null) {
            passDetector.reset()
            return
        }
        val wp = waypoints[i]
        if (passDetector.update(fix, wp, Triple(i, wp.lat, wp.lon), settings)) {
            waypoints = waypoints.toMutableList().also { it[i] = wp.copy(reached = true) }
        }
    }

    private fun clearHistory() {
        lastFix = null
        rateTracker.clear()
        headingSelector.reset()
        passDetector.reset()
    }

    private fun recompute(): NavState {
        val now = nowMs
        val fix = lastFix
        val noFix = now == null || isNoFix(now, fix?.timeMs, settings.noFixTimeoutSec * 1000L)
        // NO FIX 中の古い Fix の方位は「今使える GPS 方位」として扱わない
        val heading = headingSelector.select(settings.sourceMode, fix?.takeIf { !noFix }, compassDeg)
        val rate = rateTracker.rate(settings.rateWindowSec)

        val next = WaypointNav.nextIndex(waypoints)
        val wp = next?.let { waypoints[it] }
        val dist = if (fix != null && wp != null) Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon) else null
        val bearing = if (fix != null && wp != null) Geo.bearingDeg(fix.lat, fix.lon, wp.lat, wp.lon) else null
        val eta = if (now != null && dist != null) WaypointNav.etaMs(now, dist, rate?.avgSpeedMps) else null

        state = NavState(
            nowMs = now,
            fix = fix,
            noFix = noFix,
            heading = heading,
            sourceMode = settings.sourceMode,
            groundSpeedMps = fix?.speedMps,
            altM = fix?.altRawM?.let { it - settings.altOffsetM },
            rate = rate,
            waypoints = waypoints,
            nextWpIndex = next,
            nextWpBearingDeg = bearing,
            nextWpDistanceM = dist,
            etaMs = eta,
            targetCountdownSec = countdown(now, wp?.targetTime),
            deadlineCountdownSec = countdown(now, wp?.deadlineTime),
            sourceKind = sourceKind,
            playing = playing,
            settings = settings,
            compass = if (compassDeg != null) compassQuality else null,
        )
        return state
    }

    private fun countdown(now: Long?, target: LocalTime?): Long? =
        if (now != null && target != null) WaypointNav.countdownSec(now, target, zone) else null
}
