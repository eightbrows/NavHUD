package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.ReachInfo
import io.github.eightbrows.navhud.core.model.ReachReason
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
    private val sidePassDetector = SidePassDetector()

    /** AUTO の待機（§6.1）: 到達した WP を通り過ぎたか。真横通過と同じ判定（SidePassDetector.updatePassed） */
    private val passedDetector = SidePassDetector()

    /** 通り過ぎるのを待っている、最後に到達した WP の番号（null なら待っていない） */
    private var waitingPassIndex: Int? = null

    /** この Fix の判定で到達にした WP と理由（次の WP が変わったときの待ち方を決める） */
    private var reachedNow: Pair<Int, ReachReason>? = null

    /** 到達の理由に書く「いちばん近づいた距離」: 次の WP ごとに、隣り合う Fix を結んだ線分との最短距離を追う */
    private var closestKey: Any? = null
    private var closestM = Double.POSITIVE_INFINITY
    private var prevFix: Fix? = null

    /** 前方へのシークで飛ばした区間の Fix を判定にかけている間 */
    private var seeking = false

    private var lastFix: Fix? = null
    /** 最後に Fix を受け取った時刻（onFix / onFixes の nowMs）。LIVE の NO FIX の判定に使う */
    private var lastFixAtMs: Long? = null
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
    /**
     * AUTO の待機を始める（次の recompute で、そこから設定の秒数のあいだ段を動かさない）。到達した WP を通り過ぎたとき、
     * 真横通過・手動で到達にしたとき、手動で未到達に戻したとき
     */
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
        lastFixAtMs = nowMs
        return recompute()
    }

    /**
     * 何点かの Fix をまとめて入れる（リプレイの倍速）。1点ずつ onFix したのと同じ判定をし、NavState は最後に1回だけ作る。
     */
    fun onFixes(fixes: List<Fix>, nowMs: Long): NavState {
        fixes.forEach(::ingest)
        this.nowMs = nowMs
        if (fixes.isNotEmpty()) lastFixAtMs = nowMs
        return recompute()
    }

    private fun ingest(fix: Fix) {
        // リプレイの巻き戻し・別ファイルなどで時刻が戻ったら、位置に関する履歴を捨てる
        lastFix?.let { if (fix.timeMs < it.timeMs) clearHistory() }
        prevFix = lastFix
        lastFix = fix
        headingSelector.update(fix)
        rateTracker.add(fix)
        val nextBefore = WaypointNav.nextIndex(waypoints)
        reachedNow = null
        checkRadius(fix)
        checkSidePass(fix)
        checkPass(fix)
        if (WaypointNav.nextIndex(waypoints) != nextBefore) onNextChanged(reachedNow)
        checkPassedReached(fix)
        // LIVE の軌跡（起動してからの分。保存しない）
        if (sourceKind == SourceKind.LIVE) liveTrail.add(fix)
    }

    /**
     * @param deg コンパス方位（真北）。センサがなくなった・値が使えないなら null
     * @param nowMs 現在時刻。null なら時刻は進めない（REPLAY でトラックがまだないときなど）
     */
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

    /**
     * ピンチ（§6.12）: steps 段だけ ＋（正）か −（負）を押したのと同じにする。AUTO の扱い・最小と最大の段で止まるのも ＋ / − と同じ。
     */
    fun zoomBy(steps: Int): NavState {
        repeat(kotlin.math.abs(steps)) {
            if (steps > 0) rangeSelector.zoomIn(keepAuto = pan != null) else rangeSelector.zoomOut(keepAuto = pan != null)
        }
        return recompute()
    }

    /** 縮尺の AUTO の ON / OFF。 */
    fun toggleAutoRange(): NavState {
        rangeSelector.setAuto(!rangeSelector.auto)
        return recompute()
    }

    fun setSourceMode(mode: SourceMode): NavState = updateSettings(settings.copy(sourceMode = mode))

    fun setWaypoints(wps: List<Waypoint>): NavState {
        // 通り過ぎるのを待っていた WP が、同じ番号・同じ位置で残っていなければ（別のリストなど）待つのをやめる
        waitingPassIndex?.let { i ->
            val old = waypoints.getOrNull(i)
            val new = wps.getOrNull(i)
            if (old == null || new == null || old.lat != new.lat || old.lon != new.lon) {
                waitingPassIndex = null
                rangeSelector.stopWaitingForPass()
            }
        }
        waypoints = wps
        return recompute()
    }

    fun toggleReached(index: Int): NavState {
        val nextBefore = WaypointNav.nextIndex(waypoints)
        waypoints = WaypointNav.toggleReached(waypoints, index)
        // 手動で到達にしたら理由は「手動」（時刻だけ）
        waypoints.getOrNull(index)?.takeIf { it.reached }?.let { wp ->
            waypoints = waypoints.toMutableList().also { it[index] = wp.copy(reach = ReachInfo(ReachReason.MANUAL, nowMs ?: lastFix?.timeMs)) }
        }
        // 手動のときは通り過ぎるのを待たず、すぐ秒数を数え始める
        if (WaypointNav.nextIndex(waypoints) != nextBefore) onNextChanged(null)
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
     * - 前方へのシーク: 飛ばした区間の Fix（passed）を先に通常どおりの到達判定（WP ごとの半径・到達半径・真横通過・通過判定）にかけ、
     *   トラックが通った WP を到達にする。
     * - 後方へのシーク: WP の到達状態は残す。トラックの先頭まで戻したとき（toStart）だけ、すべて未到達に戻す。
     */
    fun seekReset(toStart: Boolean, passed: List<Fix> = emptyList()): NavState {
        seeking = true
        passed.forEach(::ingest)
        seeking = false
        clearHistory()
        // 地図が跳ぶので、AUTO は待たずに縮尺を決め直す（一時停止中はトラックの時計が進まず、狭める方向の待ちが終わらないため）。
        // 飛ばした区間で到達した WP を通り過ぎるのも待たない
        wpChanged = false
        rangeSelector.decideNow()
        nowMs = null
        if (toStart) waypoints = waypoints.map { it.copy(reached = false, reach = null) }
        return recompute()
    }

    /** 位置・時刻に関する状態を捨てる（新しいトラックを読んだときなど）。WP と設定は残す。 */
    fun resetPosition(): NavState {
        clearHistory()
        nowMs = null
        return recompute()
    }

    /** WP ごとの半径・到着半径（§5.4 の 1・2）: 次の WP がその半径に入ったら到達にする。 */
    private fun checkRadius(fix: Fix) {
        val i = WaypointNav.nextIndex(waypoints) ?: return
        val wp = waypoints[i]
        closestTo(i, fix)
        if (Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon) <= (wp.radiusM ?: settings.reachRadiusM)) {
            markReached(i, if (wp.radiusM != null) ReachReason.RADIUS else ReachReason.ARRIVAL, fix)
        }
    }

    /** 次の WP（i）にいちばん近づいた距離 [m]。この Fix の分も入れて返す。次の WP が変わったら数え直す。 */
    private fun closestTo(i: Int, fix: Fix): Double {
        val wp = waypoints[i]
        val key = Triple(i, wp.lat, wp.lon)
        if (key != closestKey) {
            closestKey = key
            closestM = Double.POSITIVE_INFINITY
        }
        val p = prevFix
        val d = if (p == null) Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon) else PassDetector.segmentDistanceM(p, fix, wp)
        if (d < closestM) closestM = d
        return closestM
    }

    /** 次の WP（i）を到達にして、理由・Fix の時刻・いちばん近づいた距離を記録する。 */
    private fun markReached(i: Int, reason: ReachReason, fix: Fix) {
        reachedNow = i to reason
        val closest = closestTo(i, fix)
        waypoints = waypoints.toMutableList().also {
            it[i] = it[i].copy(reached = true, reach = ReachInfo(reason, fix.timeMs, closest, viaSeek = seeking))
        }
    }

    /** 真横通過（§5.4）。走行中に次の WP が真横か後ろになり、いちばん近づいた距離から離れたら到達にする。 */
    private fun checkSidePass(fix: Fix) {
        val i = WaypointNav.nextIndex(waypoints)
        if (i == null) {
            sidePassDetector.reset()
            return
        }
        val wp = waypoints[i]
        if (sidePassDetector.update(fix, wp, Triple(i, wp.lat, wp.lon), settings)) markReached(i, ReachReason.SIDE, fix)
    }

    /** 通過判定（§5.4 の予備。方位が取れない場面用）。次の WP に最接近したあと離れていったら到達にする。 */
    private fun checkPass(fix: Fix) {
        val i = WaypointNav.nextIndex(waypoints)
        if (!settings.passDetection || i == null) {
            passDetector.reset()
            return
        }
        val wp = waypoints[i]
        if (passDetector.update(fix, wp, Triple(i, wp.lat, wp.lon), settings)) markReached(i, ReachReason.PASS, fix)
    }

    /**
     * 次の WP が変わったときの AUTO の待ち方（§6.1）。reached はこの Fix で到達にした WP と理由（手動のトグルは null）。
     * - 真横通過で到達・手動: もう通り過ぎている（手動は待たない）ので、すぐ秒数を数え始める
     * - WP ごとの半径・到着半径・通過判定で到達: その WP を通り過ぎるまで段を動かさない（待っている WP は最後に到達したもの）
     */
    private fun onNextChanged(reached: Pair<Int, ReachReason>?) {
        if (reached == null || reached.second == ReachReason.SIDE) {
            waitingPassIndex = null
            rangeSelector.stopWaitingForPass()
            wpChanged = true
        } else {
            waitingPassIndex = reached.first
            passedDetector.reset()
            wpChanged = false
            rangeSelector.waitForPass()
        }
    }

    /** 最後に到達した WP を通り過ぎたら、そこから秒数を数え始める（真横通過と同じ条件。距離の上限なし）。 */
    private fun checkPassedReached(fix: Fix) {
        val i = waitingPassIndex ?: return
        val wp = waypoints.getOrNull(i) ?: return
        if (passedDetector.updatePassed(fix, wp, Triple(i, wp.lat, wp.lon), settings)) {
            waitingPassIndex = null
            wpChanged = true
        }
    }

    private fun clearHistory() {
        lastFix = null
        waitingPassIndex = null
        passedDetector.reset()
        rangeSelector.stopWaitingForPass()
        lastFixAtMs = null
        pan = null
        rateTracker.clear()
        headingSelector.reset()
        passDetector.reset()
        sidePassDetector.reset()
        prevFix = null
        closestKey = null
    }

    private fun recompute(): NavState {
        val now = nowMs
        val fix = lastFix
        // NO FIX（§5.5）: LIVE は「今 − 最後に Fix を受け取った時刻」。Fix の時刻は GPS の時刻で、端末の時計とずれることがあるため
        // （GS・RATE などは Fix の時刻のまま）。REPLAY は「今（トラックの時計）− 最後の Fix の時刻」
        val lastFixMs = if (sourceKind == SourceKind.LIVE) lastFixAtMs else fix?.timeMs
        val noFix = now == null || isNoFix(now, lastFixMs, settings.noFixTimeoutSec * 1000L)
        // NO FIX 中の古い Fix の方位は「今使える GPS 方位」として扱わない
        val heading = headingSelector.select(settings.sourceMode, noFix, compassDeg, compassQuality.lowAccuracy)
        val rate = rateTracker.rate(settings.rateWindowSec)

        val next = WaypointNav.nextIndex(waypoints)
        val wp = next?.let { waypoints[it] }
        val dist = if (fix != null && wp != null) Geo.distanceM(fix.lat, fix.lon, wp.lat, wp.lon) else null
        val bearing = if (fix != null && wp != null) Geo.bearingDeg(fix.lat, fix.lon, wp.lat, wp.lon) else null
        // ETA: RATE の窓の平均速度。履歴が窓に足りなければ、ある分（最低 10 秒）の平均速度
        val eta = if (now != null && dist != null) WaypointNav.etaMs(now, dist, rateTracker.etaSpeed(settings.rateWindowSec)) else null
        // 到達した WP を通り過ぎたら（真横通過・手動は到達したら）、そこから設定の秒数のあいだ AUTO の段を動かさない（時刻はトラックの時刻）
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
                override val targetKey = Triple(next, wp.lat, wp.lon)
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
            override val targetKey = Triple(next, wp.lat, wp.lon)
        }
    }

    private fun countdown(now: Long?, target: LocalTime?): Long? =
        if (now != null && target != null) WaypointNav.countdownSec(now, target, zone) else null
}
