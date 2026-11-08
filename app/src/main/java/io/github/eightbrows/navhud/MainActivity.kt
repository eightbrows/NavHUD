package io.github.eightbrows.navhud

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.source.AppLanguage
import io.github.eightbrows.navhud.source.AppPermissions
import io.github.eightbrows.navhud.source.TrackDocumentStore
import io.github.eightbrows.navhud.source.WaypointDocumentStore
import io.github.eightbrows.navhud.core.view.LoadSummary
import io.github.eightbrows.navhud.ui.DebugScreen
import io.github.eightbrows.navhud.ui.FileSheet
import io.github.eightbrows.navhud.ui.FileSheetKind
import io.github.eightbrows.navhud.ui.ReplayUiState
import io.github.eightbrows.navhud.ui.SheetCurrentFile
import io.github.eightbrows.navhud.ui.HudColors
import io.github.eightbrows.navhud.ui.HudPalette
import io.github.eightbrows.navhud.ui.LocationPermission
import io.github.eightbrows.navhud.ui.MainScreen
import io.github.eightbrows.navhud.ui.NavViewModel
import io.github.eightbrows.navhud.ui.SettingsScreen
import io.github.eightbrows.navhud.ui.WaypointSettingsScreen
import io.github.eightbrows.navhud.ui.WaypointUiState
import io.github.eightbrows.navhud.ui.ZipSessionDialog
import io.github.eightbrows.navhud.ui.theme.NavHUDTheme

/** 画面の切り替え（ライブラリは使わない）。 */
private enum class Screen { MAIN, WAYPOINTS, SETTINGS, DEBUG }

class MainActivity : ComponentActivity() {

    private lateinit var vm: NavViewModel

    /** 画面が前に来た回数（設定画面の言語・権限の表示を読み直す合図） */
    private var resumeCount by mutableIntStateOf(0)

    /** 選んだ表示言語にする（§6.11。Android 12 以前。13 以降は端末がする） */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.migrate(this)
        enableEdgeToEdge()
        vm = ViewModelProvider(this)[NavViewModel::class.java]
        setContent {
            NavHUDTheme {
                val state by vm.state.collectAsState()
                val replay by vm.replay.collectAsState()
                val wpUi by vm.wp.collectAsState()
                val live by vm.live.collectAsState()
                val baseMap by vm.baseMap.collectAsState()
                val notices by vm.notices.collectAsState()
                var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
                val context = LocalContext.current

                val pickTrack = rememberLauncherForActivityResult(TrackDocumentStore.OpenTrackDocument()) {
                    vm.onTrackPicked(it)
                }
                val importWaypoints = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                    vm.importWaypoints(it)
                }
                val exportWaypoints = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(WaypointDocumentStore.EXPORT_MIME_TYPE),
                ) { vm.exportWaypoints(it) }
                val onPickTrack = { pickTrack.launch(TrackDocumentStore.MIME_TYPES) }
                // FILE（20261104-D03）: いつもファイルの画面（軌跡）を開く。前の「ファイルを選ぶ / クリア」の画面は置き換えた
                val fileSheet by vm.fileSheet.collectAsState()
                var confirmTrackClear by remember { mutableStateOf(false) }
                var confirmWpClear by remember { mutableStateOf(false) }
                val onFileButton = { vm.openFileSheet(FileSheetKind.TRACK) }

                // 通知の権限（Android 13 以降）。拒否されても動作は続ける
                val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                // 位置情報の権限。FINE がないと GPS は使えない
                val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                    vm.onLocationPermission(
                        fine = r[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted(Manifest.permission.ACCESS_FINE_LOCATION),
                        coarse = r[Manifest.permission.ACCESS_COARSE_LOCATION] == true || granted(Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val onRequestPermission = {
                    requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                // LIVE で起動して、まだ許可されていなければ最初に1回だけ聞く
                LaunchedEffect(Unit) {
                    if (state.sourceKind == SourceKind.LIVE && live.permission == LocationPermission.UNKNOWN) onRequestPermission()
                }
                // 色（UI の色と地図の色。基本色だけ切り替える）と、読み込んだ軌跡の色・明るさ
                val uiTheme = state.settings.uiTheme
                val mapTheme = state.settings.mapTheme
                val trackColor = state.settings.trackColor
                val trackBrightness = state.settings.trackBrightnessPct
                LaunchedEffect(uiTheme, mapTheme, trackColor, trackBrightness) {
                    HudColors.uiPalette = HudPalette.of(uiTheme)
                    HudColors.mapPalette = HudPalette.of(mapTheme)
                    HudColors.trackColor = trackColor
                    HudColors.trackBrightnessPct = trackBrightness
                }
                // 画面常時点灯（§6.8）
                val keepScreenOn = state.settings.keepScreenOn
                DisposableEffect(keepScreenOn) {
                    if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose {}
                }
                val onImport = { importWaypoints.launch(WaypointDocumentStore.IMPORT_MIME_TYPES) }
                // WP設定のインポート（20261104-D03）: ファイルの画面（WP リスト）を開く
                val onImportSheet = { vm.openFileSheet(FileSheetKind.WAYPOINTS) }

                BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }

                Scaffold(containerColor = HudColors.Background) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)
                    when (screen) {
                        Screen.MAIN -> MainScreen(
                            state = state,
                            replay = replay,
                            onPickTrack = onFileButton,
                            onTogglePlay = vm::togglePlay,
                            onCycleSource = vm::cycleSourceMode,
                            onToggleDisplay = vm::toggleDisplayMode,
                            onOpenDebug = { screen = Screen.DEBUG },
                            onOpenWpSettings = {
                                vm.clearWaypointMessage()
                                screen = Screen.WAYPOINTS
                            },
                            onToggleReached = vm::toggleReached,
                            onPanToWaypoint = vm::panToWaypoint,
                            live = live,
                            onToggleSourceKind = vm::toggleSourceKind,
                            onRequestPermission = onRequestPermission,
                            onOpenAppSettings = ::openAppSettings,
                            onZoomIn = vm::zoomIn,
                            onZoomOut = vm::zoomOut,
                            onToggleAutoRange = vm::toggleAutoRange,
                            onViewport = vm::setViewport,
                            onPan = vm::panBy,
                            onPinch = vm::zoomBy,
                            onDoubleTap = vm::zoomInAt,
                            onTwoFingerTap = vm::twoFingerTap,
                            notices = notices,
                            onEndPan = vm::endPan,
                            onSlower = vm::slowerReplay,
                            onFaster = vm::fasterReplay,
                            onSeek = vm::seekReplay,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            baseMap = baseMap,
                            // 地図はステータスバーの下から画面の下端まで（WP ボタン列・標高プロファイルはナビゲーションバーの上に置く）
                            modifier = Modifier.statusBarsPadding(),
                        )
                        Screen.WAYPOINTS -> WaypointSettingsScreen(
                            state = state,
                            wpUi = wpUi,
                            defaultName = vm::defaultWaypointName,
                            onBack = { screen = Screen.MAIN },
                            onAdd = vm::addWaypoint,
                            onUpdate = vm::updateWaypoint,
                            onDelete = vm::deleteWaypoint,
                            onMove = vm::moveWaypoint,
                            onSetEnabled = vm::setWaypointEnabled,
                            onPaste = { vm.pasteCoordinates(clipboardText(context)) },
                            onImport = onImportSheet,
                            onExport = { exportWaypoints.launch(WaypointDocumentStore.EXPORT_DEFAULT_NAME) },
                            onReverse = vm::reverseWaypoints,
                            onAdjustTimes = vm::adjustWaypointTimes,
                            onClear = vm::clearWaypoints,
                            modifier = modifier,
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            settings = state.settings,
                            // 言語と権限は、画面に戻ってきたとき（端末の設定で変えたあとなど）に読み直す
                            language = remember(resumeCount) { AppLanguage.current(context) },
                            permissions = remember(resumeCount) { AppPermissions.statuses(context) },
                            versionName = remember { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() },
                            onChange = vm::updateSettings,
                            onReset = vm::resetSettings,
                            onLanguage = { AppLanguage.set(this@MainActivity, it) },
                            onOpenAppSettings = ::openAppSettings,
                            onBack = { screen = Screen.MAIN },
                            modifier = modifier,
                            // 前に閉じたときのスクロール位置から出す（20261023-D01）
                            initialScrollY = remember { vm.settingsScrollY },
                            onScrollY = { vm.settingsScrollY = it },
                            // 閉じたセクションも、前に閉じたときのまま出す（20261104-D02）
                            initialClosed = remember { vm.settingsClosedSections },
                            onClosedChange = { vm.settingsClosedSections = it },
                        )
                        Screen.DEBUG -> DebugScreen(
                            state = state,
                            replay = replay,
                            onPickTrack = onPickTrack,
                            onTogglePlay = vm::togglePlay,
                            onSourceMode = vm::setSourceMode,
                            onToggleWp = vm::toggleReached,
                            onClose = { screen = Screen.MAIN },
                            canLoadTemporaryWaypoints = state.waypoints.isEmpty() && replay.fileName != null,
                            onLoadTemporaryWaypoints = vm::loadTemporaryWaypoints,
                            modifier = modifier,
                        )
                    }
                }

                if (wpUi.startupPending) {
                    StartupDialog(
                        wpUi = wpUi,
                        onPrevious = vm::openPreviousList,
                        onChoose = {
                            vm.startWithoutList()
                            onImport()
                        },
                        onNone = vm::startWithoutList,
                    )
                } else {
                    // GpsLogger の zip を開いたとき: 中のセッションの一覧（WP リストの選択が済んでから出す）
                    replay.sessions?.let { zip ->
                        ZipSessionDialog(zip, java.time.ZoneId.systemDefault(), vm::onZipSessionPicked, vm::onZipSessionDismissed)
                    }
                }
                // ファイルの画面（20261104-D03）: 軌跡（FILE）・WP リスト（WP設定のインポート）
                fileSheet?.let { sheet ->
                    val zone = java.time.ZoneId.systemDefault()
                    val isTrack = sheet.kind == FileSheetKind.TRACK
                    val current = if (isTrack) trackCurrent(replay, zone) else waypointCurrent(wpUi, state.waypoints)
                    FileSheet(
                        sheet = sheet,
                        dot = if (isTrack) HudColors.trackFull(state.settings.trackColor) else HudColors.Active,
                        current = current,
                        zone = zone,
                        onOpen = { vm.openRecent(sheet.kind, it) },
                        onRemove = { vm.removeRecent(sheet.kind, it) },
                        // クリアは今までと同じ確認を出す（画面は閉じる）
                        onClear = {
                            vm.closeFileSheet()
                            if (isTrack) confirmTrackClear = true else confirmWpClear = true
                        },
                        onChoose = {
                            vm.closeFileSheet()
                            if (isTrack) onPickTrack() else onImport()
                        },
                        onDismiss = vm::closeFileSheet,
                    )
                }
                if (confirmWpClear) {
                    AlertDialog(
                        onDismissRequest = { confirmWpClear = false },
                        title = { Text(stringResource(R.string.wp_clear_title)) },
                        text = { Text(stringResource(R.string.wp_clear_confirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.clearWaypoints()
                                confirmWpClear = false
                            }) { Text(stringResource(R.string.wp_clear)) }
                        },
                        dismissButton = { TextButton(onClick = { confirmWpClear = false }) { Text(stringResource(R.string.cancel)) } },
                    )
                }
                if (confirmTrackClear) {
                    AlertDialog(
                        onDismissRequest = { confirmTrackClear = false },
                        title = { Text(stringResource(R.string.track_clear_title)) },
                        text = { Text(stringResource(R.string.track_clear_confirm, replay.fileName.orEmpty())) },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.clearTrack()
                                confirmTrackClear = false
                            }) { Text(stringResource(R.string.track_clear)) }
                        },
                        dismissButton = { TextButton(onClick = { confirmTrackClear = false }) { Text(stringResource(R.string.cancel)) } },
                    )
                }
            }
        }
    }

    // フォアグラウンドのみで動作する（§6.8）。前面に来たら GPS・コンパスを開始し、裏に回ったら止める
    override fun onStart() {
        super.onStart()
        // 設定画面から戻ったときなど、今の許可を反映する（まだ聞いていなければ聞くまで待つ）
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine || vm.live.value.permission != LocationPermission.UNKNOWN) {
            vm.onLocationPermission(fine, granted(Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        vm.onForeground()
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onStop() {
        super.onStop()
        // 言語の切り替えなどで画面を作り直すだけのときは、止めない（すぐ onStart に戻る。再生も続ける）
        if (!isChangingConfigurations) vm.onBackground()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** アプリの設定画面（権限を「許可しない」にしたあと、もう一度許可するため）。 */
    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 起動時の選択（§6.5）: 前回のリスト / リストを選ぶ / リストなし。 */
@Composable
private fun StartupDialog(wpUi: WaypointUiState, onPrevious: () -> Unit, onChoose: () -> Unit, onNone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNone,
        title = { Text(stringResource(R.string.startup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPrevious, enabled = wpUi.hasSavedList) { Text(stringResource(R.string.startup_previous)) }
                TextButton(onClick = onChoose) { Text(stringResource(R.string.startup_choose)) }
                TextButton(onClick = onNone) { Text(stringResource(R.string.startup_none)) }
            }
        },
        confirmButton = {},
    )
}

/**
 * ファイルの画面の「読み込み中」（軌跡。20261104-D03）: ファイルの名前と「11,172 点・2026-10-17 7:52〜11:02」。
 * zip はその上にセッションの名前。読み込んでいなければ null（「なし」）
 */
@Composable
private fun trackCurrent(replay: ReplayUiState, zone: java.time.ZoneId): SheetCurrentFile? {
    val file = replay.file ?: return null
    val start = replay.startMs
    val end = replay.endMs
    val lines = listOfNotNull(
        replay.session?.let { stringResource(R.string.sheet_session, it) },
        if (start != null && end != null) stringResource(R.string.sheet_track_summary, LoadSummary.points(replay.fixCount), LoadSummary.trackSpan(start, end, zone)) else null,
    )
    return SheetCurrentFile(file, lines)
}

/**
 * ファイルの画面の「読み込み中」（WP リスト。20261104-D03）: ファイルの名前と「6 件（うち無効 1）」（今の WP の数。編集したあとはその数）。
 * リストを読み込んでいなくて WP もなければ null（「なし」）
 */
@Composable
private fun waypointCurrent(wpUi: WaypointUiState, wps: List<io.github.eightbrows.navhud.core.model.Waypoint>): SheetCurrentFile? {
    if (wpUi.listName == null && wps.isEmpty()) return null
    val sum = LoadSummary.waypoints(wps)
    val line = if (sum.disabled > 0) stringResource(R.string.sheet_wp_summary_disabled, sum.count.toString(), sum.disabled.toString())
    else stringResource(R.string.sheet_wp_summary, sum.count.toString())
    return SheetCurrentFile(wpUi.listName ?: stringResource(R.string.sheet_no_file), listOf(line))
}

/** クリップボードの文字列（なければ null）。 */
private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
