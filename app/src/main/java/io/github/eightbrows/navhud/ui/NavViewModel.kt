package io.github.eightbrows.navhud.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eightbrows.navhud.core.io.CoordinateText
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.view.HudViewport
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TemporaryWaypoints
import io.github.eightbrows.navhud.core.replay.LiveClock
import io.github.eightbrows.navhud.core.replay.ReplayClock
import io.github.eightbrows.navhud.source.CompassSource
import io.github.eightbrows.navhud.source.GeoPoint
import io.github.eightbrows.navhud.source.LiveGpsSource
import io.github.eightbrows.navhud.source.LiveLocationBus
import io.github.eightbrows.navhud.source.NavLocationService
import io.github.eightbrows.navhud.source.LoadedTrack
import io.github.eightbrows.navhud.source.ReplayPositionSource
import io.github.eightbrows.navhud.source.SettingsStore
import io.github.eightbrows.navhud.source.TrackDocumentStore
import io.github.eightbrows.navhud.source.WaypointDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** リプレイのファイルと進み具合（NavState の外の、確認用画面のための情報）。 */
data class ReplayUiState(
    val fileName: String? = null,
    val fixCount: Int = 0,
    val skippedLines: Int = 0,
    val loading: Boolean = false,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val ready: Boolean get() = fileName != null && !loading
}

/** WP リストのファイルと、WP 関連の画面の状態。 */
data class WaypointUiState(
    /** 読み込んだか書き出したファイルの名前。リストなしなら null */
    val listName: String? = null,
    /** 未エクスポートの編集がある */
    val dirty: Boolean = false,
    val message: String? = null,
    /** 「前回のリスト」がある */
    val hasSavedList: Boolean = false,
    /** 起動時の選択（前回のリスト / リストを選ぶ / リストなし）をまだしていない */
    val startupPending: Boolean = true,
    /** WP ボタン列を出す */
    val showButtons: Boolean = true,
    val loading: Boolean = false,
)

/**
 * NavState を StateFlow で公開する。NavEngine への入力（Fix・tick）はすべてメインスレッドから行う。
 * LIVE は GPS（フォアグラウンドサービス）とコンパス、REPLAY は track.csv。
 */
class NavViewModel(app: Application) : AndroidViewModel(app) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val store = TrackDocumentStore(app)
    private val wpStore = WaypointDocumentStore(app)
    private val clock = ReplayClock { SystemClock.elapsedRealtime() }
    private val settingsStore = SettingsStore(app)

    // 保存した設定と INPUT（LIVE / REPLAY）で始める
    private val engine = NavEngine(settingsStore.load(), zone, settingsStore.input)

    private val _state = MutableStateFlow(engine.state)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val _replay = MutableStateFlow(ReplayUiState())
    val replay: StateFlow<ReplayUiState> = _replay.asStateFlow()

    private val _wp = MutableStateFlow(WaypointUiState(hasSavedList = wpStore.savedUri != null))
    val wp: StateFlow<WaypointUiState> = _wp.asStateFlow()

    private val _live = MutableStateFlow(LiveUiState())
    val live: StateFlow<LiveUiState> = _live.asStateFlow()

    private var source: ReplayPositionSource? = null
    private var trackFixes: List<Fix> = emptyList()
    private var fixJob: Job? = null

    private val compass = CompassSource(app) { declinationPoint() }
    private var compassJob: Job? = null
    private var panJob: Job? = null
    private var foreground = false

    init {
        store.savedUri?.let { load(it, isSaved = true) }
        // 設定「前回のリストを自動で開く」: 起動時の選択を出さずに読み込む
        if (engine.settings.autoOpenLastList && wpStore.savedUri != null) openPreviousList()
        // 時計の tick。Fix が来ない欠損区間でも NO FIX とカウントダウンを進める
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                nowMs()?.let { publish(engine.onTick(it)) }
            }
        }
        // LIVE: サービスが受け取った GPS の Fix
        viewModelScope.launch {
            LiveLocationBus.fixes.collect { if (kind == SourceKind.LIVE) publish(engine.onFix(it, LiveClock.nowMs())) }
        }
        viewModelScope.launch {
            LiveLocationBus.gpsEnabled.collect { _live.value = _live.value.copy(gpsEnabled = it) }
        }
    }

    /** ファイル選択の結果。キャンセルなら null。 */
    fun onTrackPicked(uri: Uri?) {
        if (uri == null) return
        store.remember(uri)
        load(uri, isSaved = false)
    }

    fun togglePlay() {
        val src = source ?: return
        if (kind != SourceKind.REPLAY || src.finished) return
        if (clock.playing) pauseReplay() else play()
    }

    /** リプレイを止める。LIVE の「動作中」には影響しない。 */
    private fun pauseReplay() {
        clock.pause()
        if (kind == SourceKind.REPLAY) publish(engine.setPlaying(false))
    }

    private fun play() {
        clock.play()
        publish(engine.setPlaying(true))
    }

    /** 設定を変える（すぐ画面に反映し、保存する）。 */
    fun updateSettings(transform: (NavSettings) -> NavSettings) {
        val next = transform(engine.settings)
        publish(engine.updateSettings(next))
        settingsStore.save(next)
    }

    /** 設定を初期値に戻す。 */
    fun resetSettings() = updateSettings { NavSettings() }

    fun setSourceMode(mode: SourceMode) = updateSettings { it.copy(sourceMode = mode) }

    /** HYBRID → GPS → COMPASS → HYBRID … */
    fun cycleSourceMode() {
        val modes = SourceMode.entries
        setSourceMode(modes[(engine.settings.sourceMode.ordinal + 1) % modes.size])
    }

    /** 縮尺の ＋ / − / AUTO */
    fun zoomIn() {
        publish(engine.zoomIn())
        restartPanTimer()
    }

    fun zoomOut() {
        publish(engine.zoomOut())
        restartPanTimer()
    }

    fun toggleAutoRange() = publish(engine.toggleAutoRange())

    /** HUD の描画領域が変わったとき（AUTO 縮尺は、次の WP がこの表示枠に収まる最小の段を選ぶ）。 */
    fun setViewport(viewport: HudViewport) = publish(engine.setViewport(viewport))

    /** PAN: ドラッグの量 [px] だけ地図を動かす。操作がないまま設定の秒数たったら現在地へ戻る。 */
    fun panBy(dxPx: Float, dyPx: Float) {
        publish(engine.panBy(dxPx, dyPx))
        restartPanTimer()
    }

    /** WP ボタンの長押し: その WP を地図の中心にした PAN にする。 */
    fun panToWaypoint(index: Int) {
        val wp = engine.state.waypoints.getOrNull(index) ?: return
        publish(engine.panTo(wp.lat, wp.lon))
        restartPanTimer()
    }

    /** 現在地の表示に戻る。 */
    fun endPan() {
        panJob?.cancel()
        publish(engine.endPan())
    }

    /** PAN の自動復帰の時計（端末の時計で数える。リプレイの一時停止中も戻る）。 */
    private fun restartPanTimer() {
        panJob?.cancel()
        if (engine.state.pan == null) return
        panJob = viewModelScope.launch {
            delay(engine.settings.panReturnSec * 1000L)
            publish(engine.endPan())
        }
    }

    /** ARC ⇔ North Up */
    fun toggleDisplayMode() = updateSettings {
        it.copy(displayMode = if (it.displayMode == DisplayMode.ARC) DisplayMode.NORTH_UP else DisplayMode.ARC)
    }

    /** RATE の窓 10 → 30 → 60 → 10 … */
    fun cycleRateWindow() = updateSettings {
        val choices = NavSettings.RATE_WINDOW_CHOICES_SEC
        it.copy(rateWindowSec = choices[(choices.indexOf(it.rateWindowSec) + 1) % choices.size])
    }

    fun toggleReached(index: Int) = publish(engine.toggleReached(index))

    // ---- WP（§5.4, §6.4, §6.5） ----

    private val waypoints: List<Waypoint> get() = engine.state.waypoints

    /** 編集した WP リストを反映する。未エクスポートの編集ありにする。 */
    private fun edit(wps: List<Waypoint>, message: String? = null) {
        publish(engine.setWaypoints(wps))
        _wp.value = _wp.value.copy(dirty = true, message = message)
    }

    /** 新しい WP の名前の既定値（WP<番号>）。 */
    fun defaultWaypointName(): String = "WP${waypoints.size + 1}"

    fun addWaypoint(wp: Waypoint) = edit(waypoints + wp, "${wp.name} を追加しました")

    fun updateWaypoint(index: Int, wp: Waypoint) {
        if (index !in waypoints.indices) return
        edit(waypoints.toMutableList().also { it[index] = wp })
    }

    fun deleteWaypoint(index: Int) {
        if (index !in waypoints.indices) return
        val name = waypoints[index].name
        edit(waypoints.toMutableList().also { it.removeAt(index) }, "$name を削除しました")
    }

    fun moveWaypoint(from: Int, to: Int) {
        if (from !in waypoints.indices || to !in waypoints.indices || from == to) return
        edit(waypoints.toMutableList().also { it.add(to, it.removeAt(from)) })
    }

    fun setWaypointEnabled(index: Int, enabled: Boolean) {
        if (index !in waypoints.indices) return
        edit(waypoints.toMutableList().also { it[index] = it[index].copy(enabled = enabled) })
    }

    /** クリップボードの座標（"34.69370, 135.50230"）から WP を追加する。 */
    fun pasteCoordinates(text: String?) {
        val ll = text?.let(CoordinateText::parse)
        if (ll == null) {
            _wp.value = _wp.value.copy(message = "クリップボードに座標がありません（例: 34.69370, 135.50230）")
            return
        }
        addWaypoint(Waypoint(defaultWaypointName(), ll.lat, ll.lon))
    }

    /** インポート（CSV / GPX）。リストを置き換える。キャンセルなら null。 */
    fun importWaypoints(uri: Uri?) {
        _wp.value = _wp.value.copy(startupPending = false)
        if (uri == null) return
        loadWaypoints(uri, isSaved = false)
    }

    /** エクスポート（CSV）。キャンセルなら null。 */
    fun exportWaypoints(uri: Uri?) {
        if (uri == null) return
        val wps = waypoints
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { wpStore.save(uri, wps) } }
                .onSuccess { name ->
                    wpStore.remember(uri, writable = true)
                    _wp.value = _wp.value.copy(
                        listName = name, dirty = false, hasSavedList = true,
                        message = "$name に ${wps.size} 件を書き出しました",
                    )
                }
                .onFailure { e -> _wp.value = _wp.value.copy(message = "書き出せませんでした（${e.message}）") }
        }
    }

    /** 起動時の選択: 前回のリスト。 */
    fun openPreviousList() {
        _wp.value = _wp.value.copy(startupPending = false)
        wpStore.savedUri?.let { loadWaypoints(it, isSaved = true) }
    }

    /** 起動時の選択: リストなし。 */
    fun startWithoutList() {
        _wp.value = _wp.value.copy(startupPending = false)
    }

    private fun loadWaypoints(uri: Uri, isSaved: Boolean) {
        _wp.value = _wp.value.copy(loading = true, message = null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { wpStore.load(uri, zone) } }
                .onSuccess { loaded ->
                    wpStore.remember(uri, writable = false)
                    publish(engine.setWaypoints(loaded.result.waypoints))
                    val skipped = loaded.result.skippedLines
                    _wp.value = _wp.value.copy(
                        listName = loaded.displayName, dirty = false, loading = false, hasSavedList = true,
                        message = "${loaded.displayName} から ${loaded.result.waypoints.size} 件を読み込みました" +
                            if (skipped > 0) "（読めない行 $skipped 件をスキップ）" else "",
                    )
                }
                .onFailure { e ->
                    if (isSaved) wpStore.forget()
                    val what = if (isSaved) "前回のリスト" else "選んだファイル"
                    _wp.value = _wp.value.copy(
                        loading = false, hasSavedList = wpStore.savedUri != null,
                        message = "${what}を読めませんでした（${e.message}）",
                    )
                }
        }
    }

    fun toggleWaypointButtons() {
        _wp.value = _wp.value.copy(showButtons = !_wp.value.showButtons)
    }


    fun clearWaypointMessage() {
        _wp.value = _wp.value.copy(message = null)
    }

    /** 開発用: トラックから仮 WP を入れる（WP リストが空のときだけ）。 */
    val canLoadTemporaryWaypoints: Boolean get() = waypoints.isEmpty() && trackFixes.size >= 2

    fun loadTemporaryWaypoints() {
        if (!canLoadTemporaryWaypoints) return
        publish(engine.setWaypoints(TemporaryWaypoints.fromTrack(trackFixes, zone)))
        _wp.value = _wp.value.copy(listName = "仮 WP（開発用）", dirty = false, message = null)
    }

    private fun load(uri: Uri, isSaved: Boolean) {
        pauseReplay()
        fixJob?.cancel()
        source = null
        _replay.value = ReplayUiState(loading = true)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.load(uri) } }
                .onSuccess { start(it) }
                .onFailure { e ->
                    store.forget()
                    if (kind == SourceKind.REPLAY) publish(engine.resetPosition())
                    val what = if (isSaved) "前回のファイル" else "選んだファイル"
                    _replay.value = ReplayUiState(message = "${what}を読めませんでした。もう一度選んでください（${e.message}）")
                }
        }
    }

    private fun start(track: LoadedTrack) {
        val fixes = track.result.fixes
        if (fixes.isEmpty()) {
            _replay.value = ReplayUiState(
                message = "${track.displayName} に読める行がありません（スキップ ${track.result.skippedLines} 行）",
            )
            return
        }
        val src = ReplayPositionSource(fixes, clock)
        source = src
        trackFixes = fixes
        // LIVE 中に読み込んだときは、REPLAY に切り替えたときに最初から使う
        if (kind == SourceKind.REPLAY) {
            engine.resetPosition()
            engine.setSource(SourceKind.REPLAY, playing = false)
            publish(engine.onTick(clock.nowMs()))
        }
        _replay.value = ReplayUiState(
            fileName = track.displayName,
            fixCount = fixes.size,
            skippedLines = track.result.skippedLines,
        )
        fixJob = viewModelScope.launch {
            src.fixes.collect { if (kind == SourceKind.REPLAY) publish(engine.onFix(it, clock.nowMs())) }
            // 最後の Fix まで出したら止める
            pauseReplay()
            _replay.value = _replay.value.copy(finished = true)
        }
    }

    // ---- LIVE（§6.8） ----

    private val kind: SourceKind get() = engine.state.sourceKind

    /** 今の「現在時刻」。LIVE は端末の時刻、REPLAY はトラックの時刻（トラックがなければ null）。 */
    private fun nowMs(): Long? = when {
        kind == SourceKind.LIVE -> LiveClock.nowMs()
        source != null -> clock.nowMs()
        else -> null
    }

    /** 画面が前面に来た（onStart）。コンパスと、LIVE なら位置のサービスを開始する。 */
    fun onForeground() {
        foreground = true
        startCompass()
        updateService()
    }

    /** 画面が裏に回った（onStop）。フォアグラウンドのみで動作する（§6.8）。 */
    fun onBackground() {
        foreground = false
        stopCompass()
        pauseReplay()
        updateService()
    }

    /** 位置情報の権限の結果。FINE がなければ GPS_PROVIDER は使えない。 */
    fun onLocationPermission(fine: Boolean, coarse: Boolean) {
        val p = when {
            fine -> LocationPermission.GRANTED
            coarse -> LocationPermission.APPROXIMATE_ONLY
            else -> LocationPermission.DENIED
        }
        _live.value = _live.value.copy(permission = p)
        updateService()
    }

    /** LIVE ⇔ REPLAY。RATE の履歴・通過判定の記録はリセットし、WP の到達状態は残す。 */
    fun toggleSourceKind() = setSourceKind(if (kind == SourceKind.LIVE) SourceKind.REPLAY else SourceKind.LIVE)

    /** INPUT を変える（保存する）。 */
    fun setSourceKind(next: SourceKind) {
        if (next == kind) return
        settingsStore.input = next
        pauseReplay()
        publish(engine.switchSource(next, playing = next == SourceKind.LIVE))
        nowMs()?.let { publish(engine.onTick(it)) }
        updateService()
    }

    private fun updateService() {
        val ctx = getApplication<Application>()
        if (foreground && kind == SourceKind.LIVE && _live.value.permission == LocationPermission.GRANTED) {
            NavLocationService.start(ctx)
        } else {
            NavLocationService.stop(ctx)
        }
    }

    private fun startCompass() {
        if (compassJob != null) return
        if (!compass.available) {
            _live.value = _live.value.copy(hasCompass = false)
            return
        }
        compassJob = viewModelScope.launch {
            compass.readings.collect { r -> publish(engine.onCompass(r.trueDeg, nowMs(), r.quality)) }
        }
    }

    private fun stopCompass() {
        compassJob?.cancel()
        compassJob = null
        publish(engine.onCompass(null, nowMs()))
    }

    /** 偏角の計算に使う位置: 今の Fix → 最後に分かっている位置 → なし。 */
    private fun declinationPoint(): GeoPoint? {
        engine.state.fix?.let { return GeoPoint(it.lat, it.lon, it.altRawM ?: 0.0, it.timeMs) }
        return LiveGpsSource.lastKnown(getApplication())?.let {
            GeoPoint(it.latitude, it.longitude, if (it.hasAltitude()) it.altitude else 0.0, it.time)
        }
    }

    private fun publish(s: NavState) {
        _state.value = s
    }

    companion object {
        private const val TICK_MS = 200L
    }
}

enum class LocationPermission {
    /** まだ聞いていない */
    UNKNOWN,
    GRANTED,

    /** おおよその位置だけ許可（GPS_PROVIDER は使えない） */
    APPROXIMATE_ONLY,
    DENIED,
}

/** LIVE の状態（NavState の外の、権限・端末の設定）。 */
data class LiveUiState(
    val permission: LocationPermission = LocationPermission.UNKNOWN,
    /** 端末の位置情報（GPS）がオン */
    val gpsEnabled: Boolean = true,
    /** コンパスがある端末 */
    val hasCompass: Boolean = true,
)
