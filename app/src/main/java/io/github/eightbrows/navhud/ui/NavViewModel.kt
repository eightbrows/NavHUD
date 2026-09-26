package io.github.eightbrows.navhud.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TemporaryWaypoints
import io.github.eightbrows.navhud.core.replay.ReplayClock
import io.github.eightbrows.navhud.source.LoadedTrack
import io.github.eightbrows.navhud.source.ReplayPositionSource
import io.github.eightbrows.navhud.source.TrackDocumentStore
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

/**
 * NavState を StateFlow で公開する。NavEngine への入力（Fix・tick）はすべてメインスレッドから行う。
 * ステップ3ではリプレイのみ。LIVE（GPS・コンパス）はステップ4以降。
 */
class NavViewModel(app: Application) : AndroidViewModel(app) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val store = TrackDocumentStore(app)
    private val clock = ReplayClock { SystemClock.elapsedRealtime() }
    private val engine = NavEngine(NavSettings(), zone, SourceKind.REPLAY)

    private val _state = MutableStateFlow(engine.state)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val _replay = MutableStateFlow(ReplayUiState())
    val replay: StateFlow<ReplayUiState> = _replay.asStateFlow()

    private var source: ReplayPositionSource? = null
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
        engine.resetPosition()
        engine.setWaypoints(TemporaryWaypoints.fromTrack(fixes, zone))
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
