package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
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

    private val headingSelector = HeadingSelector()
    private val rangeSelector = RangeSelector(
        settings.rangeStepsKm, settings.initialRangeKm, settings.autoRange,
        autoMinKm = settings.autoMinRangeKm, autoMaxKm = settings.autoMaxRangeKm,
    )
    private val rateTracker = RateTracker(maxWindowSec = NavSettings.RATE_WINDOW_CHOICES_SEC.max())
    private val passDetector = PassDetector()

    private var lastFix: Fix? = null
    private var compassDeg: Float? = null
    private var compassQuality = CompassQuality()
    private var waypoints: List<Waypoint> = emptyList()
    private var nowMs: Long? = null
    private var sourceKind = sourceKind
    private var playing = sourceKind == SourceKind.LIVE
    /** 画面の表示枠（AUTO 縮尺の判定用）。まだ分からなければ距離で判定する。 */
    private var viewport: MapViewport? = null
    /** LIVE の軌跡と、REPLAY のトラック全体（軌跡の表示） */
    private val liveTrail = LiveTrail()
    private var replayTrack: List<TrackPoint> = emptyList()
    /** PAN の表示（null なら通常の表示） */
    private var pan: PanView? = null
    /** 次の WP が変わった（到達・通過・手動のトグル）。次の recompute で AUTO の通過後の待機を始める */
    private var wpChanged = false

    var state: NavState = NavState()
        private set

    init {
        applySettings(settings)
        recompute()
    }

    fun onFix(fix: Fix, nowMs: Long): NavState {
        ingest(fix)
        this.nowMs = nowMs
        return recompute()
    }

    /**
     * 何点かの Fix をまとめて入れる（リプレイの倍速）。1点ずつ onFix したのと同じ判定をし、NavState は最後に1回だけ作る。
     */
    fun onFixes(fixes: List<Fix>, nowMs: Long): NavState {
        fixes.forEach(::ingest)
        this.nowMs = nowMs
        return recompute()
    }

    private fun ingest(fix: Fix) {
        // リプレイの巻き戻し・別ファイルなどで時刻が戻ったら、位置に関する履歴を捨てる
        lastFix?.let { if (fix.timeMs < it.timeMs) clearHistory() }
        lastFix = fix
        headingSelector.update(fix)
        rateTracker.add(fix)
        val nextBefore = WaypointNav.nextIndex(waypoints)
        waypoints = WaypointNav.autoReach(waypoints, fix.lat, fix.lon, settings.reachRadiusM)
        checkPass(fix)
        if (WaypointNav.nextIndex(waypoints) != nextBefore) wpChanged = true
        // LIVE の軌跡（起動してからの分。保存しない）
        if (sourceKind == SourceKind.LIVE) liveTrail.add(fix)
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
        // 表示モードが変わったら PAN をやめる（画面の上の向きの決め方が変わるため）
        if (s.displayMode != settings.displayMode) pan = null
        settings = s
        applySettings(s)
        return recompute()
    }

    private fun applySettings(s: NavSettings) {
        headingSelector.holdEnterSpeedMps = s.holdEnterSpeedMps
        headingSelector.holdExitSpeedMps = s.holdExitSpeedMps
        headingSelector.maxAccM = s.maxGpsAccM
        headingSelector.maxBearingAccDeg = s.maxGpsBearingAccDeg
        rangeSelector.setSteps(s.rangeStepsKm)
        rangeSelector.zoomInDelayMs = s.autoRangeZoomInDelaySec * 1000L
        rangeSelector.setAutoLimits(s.autoMinRangeKm, s.autoMaxRangeKm)
        rangeSelector.holdAfterWpMs = s.autoHoldAfterWpSec * 1000L
        rangeSelector.zoomInDistRatio = s.autoZoomInDistRatio
    }

    /** 画面の大きさ・帯が変わったとき（AUTO 縮尺は、次の WP がこの表示枠に収まる最小の段を選ぶ）。 */
    fun setViewport(vp: MapViewport?): NavState {
        viewport = vp
        return recompute()
    }

    /**
     * PAN: 指を (dxPx, dyPx) 動かした分、地図を動かす。PAN でなければ、今の表示枠の中心から始める
     * （ARC は今の機首方位で向きを固定）。PAN 中は縮尺の AUTO を止める。画面の大きさが分からない・Fix がないときは何もしない。
     */
    fun panBy(dxPx: Float, dyPx: Float): NavState {
        val vp = viewport ?: return state
        val current = pan ?: run {
            val fix = lastFix ?: return state
            val offset = vp.frameCenterOffset(rangeSelector.rangeM, state.heading.deg?.toDouble(), settings)
            Pan.start(fix.lat, fix.lon, offset, panUpDeg())
        }
        pan = Pan.drag(current, dxPx, dyPx, vp.pxPerM(rangeSelector.rangeM, settings))
        return recompute()
    }

    /** PAN: 地点 (lat, lon) を表示枠の中心にする（WP ボタンの長押し）。向きは PAN 中ならそのまま。 */
    fun panTo(lat: Double, lon: Double): NavState {
        pan = PanView(lat, lon, pan?.upDeg ?: panUpDeg())
        return recompute()
    }

    /** PAN をやめて現在地の表示に戻る。AUTO が ON なら、待たずに縮尺を決め直す。 */
    fun endPan(): NavState {
        if (pan != null) rangeSelector.decideNow()
        pan = null
        return recompute()
    }

    private fun panUpDeg(): Double =
        if (settings.displayMode == DisplayMode.ARC) state.heading.deg?.toDouble() ?: 0.0 else 0.0

    /** 縮尺の ＋（1段狭く）。AUTO は OFF（PAN 中は AUTO をそのままにし、現在地に戻ったら AUTO が決め直す）。 */
    fun zoomIn(): NavState {
        rangeSelector.zoomIn(keepAuto = pan != null)
        return recompute()
    }

    /** 縮尺の −（1段広く）。AUTO は OFF（PAN 中は ＋ と同じ）。 */
    fun zoomOut(): NavState {
        rangeSelector.zoomOut(keepAuto = pan != null)
        return recompute()
    }

    /** 縮尺の AUTO の ON / OFF。 */
    fun toggleAutoRange(): NavState {
        rangeSelector.setAuto(!rangeSelector.auto)
        return recompute()
    }

    fun setSourceMode(mode: SourceMode): NavState = updateSettings(settings.copy(sourceMode = mode))

    fun setWaypoints(wps: List<Waypoint>): NavState {
        waypoints = wps
        return recompute()
    }

    fun toggleReached(index: Int): NavState {
        val nextBefore = WaypointNav.nextIndex(waypoints)
        waypoints = WaypointNav.toggleReached(waypoints, index)
        if (WaypointNav.nextIndex(waypoints) != nextBefore) wpChanged = true
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

    /** REPLAY のトラック全体（間引いたもの）。軌跡の表示に使う。 */
    fun setReplayTrack(points: List<TrackPoint>): NavState {
        replayTrack = points
        return recompute()
    }

    /**
     * リプレイのシーク: RATE の履歴・通過判定の記録・GPS 方位の保持・PAN をリセットする。このあと呼び出し側がシーク先の Fix を入れる。
     * - 前方へのシーク: 飛ばした区間の Fix（passed）を先に通常どおりの到達判定（半径・通過判定）にかけ、
     *   トラックが通った WP を到達にする。
     * - 後方へのシーク: WP の到達状態は残す。トラックの先頭まで戻したとき（toStart）だけ、すべて未到達に戻す。
     */
    fun seekReset(toStart: Boolean, passed: List<Fix> = emptyList()): NavState {
        passed.forEach(::ingest)
        clearHistory()
        // 地図が跳ぶので、AUTO は待たずに縮尺を決め直す（一時停止中はトラックの時計が進まず、狭める方向の待ちが終わらないため）
        rangeSelector.decideNow()
        nowMs = null
        if (toStart) waypoints = waypoints.map { it.copy(reached = false) }
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
        pan = null
        rateTracker.clear()
        headingSelector.reset()
        passDetector.reset()
    }

    private fun recompute(): NavState {
        val now = nowMs
        val fix = lastFix
        val noFix = now == null || isNoFix(now, fix?.timeMs, settings.noFixTimeoutSec * 1000L)
        // NO FIX 中の古い Fix の方位は「今使える GPS 方位」として扱わない
        val heading = headingSelector.select(settings.sourceMode, noFix, compassDeg, compassQuality.lowAccuracy)
        val rate = rateTracker.rate(settings.rateWindowSec)

        val next = WaypointNav.nextIndex(waypoints)
        val wp = next?.let { waypoints[it] }
        val dist = if (fix != null && wp != null) Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon) else null
        val bearing = if (fix != null && wp != null) Geo.bearingDeg(fix.lat, fix.lon, wp.lat, wp.lon) else null
        // ETA: RATE の窓の平均速度。履歴が窓に足りなければ、ある分（最低 10 秒）の平均速度
        val eta = if (now != null && dist != null) WaypointNav.etaMs(now, dist, rateTracker.etaSpeed(settings.rateWindowSec)) else null
        // 次の WP が変わったら、そこから設定の秒数のあいだ AUTO の段を動かさない（時刻はトラックの時刻）
        if (wpChanged && now != null) {
            rangeSelector.holdForWpChange(now)
            wpChanged = false
        }
        // 縮尺の AUTO（§6.1）: 次の WP を収める段を基本に、[下限, 上限] の中で1段ずつ。WP を区別できる幅を優先する
        val rangeM = when {
            // PAN 中は AUTO を止める（＋ / − は効く）
            pan != null -> rangeSelector.also { it.restartWait() }.rangeM
            now == null -> rangeSelector.rangeM
            // 次の WP がない: 下限〜上限の中央の段
            next == null -> rangeSelector.update(null, now)
            // 位置がまだ分からない: 今の段のまま
            fix == null -> rangeSelector.rangeM
            else -> rangeSelector.update(rangeProbe(fix, next, heading.deg?.toDouble()), now)
        }

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
            rangeM = rangeM,
            rangeAuto = rangeSelector.auto,
            pan = pan,
            liveTrail = liveTrail.points,
            replayTrack = replayTrack,
        )
        return state
    }

    /**
     * AUTO の判定の問い。画面が分かれば次の WP を画面に投影して枠に収まるか、見えている隣り合う目標の間隔を見る。
     * 画面が分からなければ距離で判定する（次の WP が「縮尺 × Tuning.RANGE_DISTANCE_FIT_RATIO」以内）。
     * 隣り合う目標: 次の WP から先の有効・未到達の WP（HUD に描く数まで）。到達済みと自機は数えない。
     */
    private fun rangeProbe(fix: Fix, next: Int, headingDeg: Double?): RangeProbe {
        val wp = waypoints[next]
        val target = Geo.toEN(fix.lat, fix.lon, wp.lat, wp.lon)
        val vp = viewport
        if (vp == null) {
            val dist = Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon)
            return object : RangeProbe {
                override fun fits(rangeM: Double, spread: Double) = dist * spread <= rangeM * Tuning.RANGE_DISTANCE_FIT_RATIO
                override val distanceM = dist
            }
        }
        val points = (next until waypoints.size)
            .map { waypoints[it] }
            .filter { it.enabled && !it.reached }
            .take(settings.hudWpCount.coerceAtLeast(1))
            .map { Geo.toEN(fix.lat, fix.lon, it.lat, it.lon) }
        val s = settings
        return object : RangeProbe {
            override fun fits(rangeM: Double, spread: Double) = vp.fits(rangeM, target, headingDeg, s, spread)
            override fun separated(rangeM: Double) = vp.separated(rangeM, points, headingDeg, s)
            override val distanceM = Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon)
        }
    }

    private fun countdown(now: Long?, target: LocalTime?): Long? =
        if (now != null && target != null) WaypointNav.countdownSec(now, target, zone) else null
}
