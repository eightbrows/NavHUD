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
import io.github.eightbrows.navhud.core.nav.ScreenSide
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TemporaryWaypoints
import io.github.eightbrows.navhud.core.replay.ReplayClock
import io.github.eightbrows.navhud.source.LoadedTrack
import io.github.eightbrows.navhud.source.ReplayPositionSource
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
 * ステップ3ではリプレイのみ。LIVE（GPS・コンパス）はステップ4以降。
 */
class NavViewModel(app: Application) : AndroidViewModel(app) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val store = TrackDocumentStore(app)
    private val wpStore = WaypointDocumentStore(app)
    private val clock = ReplayClock { SystemClock.elapsedRealtime() }
    private val engine = NavEngine(NavSettings(), zone, SourceKind.REPLAY)

    private val _state = MutableStateFlow(engine.state)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val _replay = MutableStateFlow(ReplayUiState())
    val replay: StateFlow<ReplayUiState> = _replay.asStateFlow()

    private val _wp = MutableStateFlow(WaypointUiState(hasSavedList = wpStore.savedUri != null))
    val wp: StateFlow<WaypointUiState> = _wp.asStateFlow()

    private var source: ReplayPositionSource? = null
    private var trackFixes: List<Fix> = emptyList()
    private var fixJob: Job? = null

    init {
        store.savedUri?.let { load(it, isSaved = true) }
        // 時計の tick。Fix が来ない欠損区間でも POSITION LOST とカウントダウンを進める
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (source != null) publish(engine.onTick(clock.nowMs()))
            }
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
        if (src.finished) return
        if (clock.playing) pause() else play()
    }

    /** 画面が裏に回ったときなど。 */
    fun pause() {
        clock.pause()
        publish(engine.setPlaying(false))
    }

    private fun play() {
        clock.play()
        publish(engine.setPlaying(true))
    }

    fun setSourceMode(mode: SourceMode) = publish(engine.setSourceMode(mode))

    /** HYBRID → GPS → COMPASS → HYBRID … */
    fun cycleSourceMode() {
        val modes = SourceMode.entries
        setSourceMode(modes[(engine.settings.sourceMode.ordinal + 1) % modes.size])
    }

    /** ARC ⇔ North Up */
    fun toggleDisplayMode() {
        val s = engine.settings
        val next = if (s.displayMode == DisplayMode.ARC) DisplayMode.NORTH_UP else DisplayMode.ARC
        publish(engine.updateSettings(s.copy(displayMode = next)))
    }

    /** RATE の窓 10 → 30 → 60 → 10 … */
    fun cycleRateWindow() {
        val s = engine.settings
        val choices = NavSettings.RATE_WINDOW_CHOICES_SEC
        val next = choices[(choices.indexOf(s.rateWindowSec) + 1) % choices.size]
        publish(engine.updateSettings(s.copy(rateWindowSec = next)))
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

    /** WP ボタン列の左右（ステップ7で設定画面に移す）。 */
    fun toggleWaypointButtonsSide() {
        val s = engine.settings
        val side = if (s.wpButtonsSide == ScreenSide.RIGHT) ScreenSide.LEFT else ScreenSide.RIGHT
        publish(engine.updateSettings(s.copy(wpButtonsSide = side)))
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
        pause()
        fixJob?.cancel()
        source = null
        _replay.value = ReplayUiState(loading = true)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.load(uri) } }
                .onSuccess { start(it) }
                .onFailure { e ->
                    store.forget()
                    publish(engine.resetPosition())
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
        engine.resetPosition()
        engine.setSource(SourceKind.REPLAY, playing = false)
        publish(engine.onTick(clock.nowMs()))
        _replay.value = ReplayUiState(
            fileName = track.displayName,
            fixCount = fixes.size,
            skippedLines = track.result.skippedLines,
        )
        fixJob = viewModelScope.launch {
            src.fixes.collect { publish(engine.onFix(it, clock.nowMs())) }
            // 最後の Fix まで出したら止める
            pause()
            _replay.value = _replay.value.copy(finished = true)
        }
    }

    private fun publish(s: NavState) {
        _state.value = s
    }

    companion object {
        private const val TICK_MS = 200L
    }
}
