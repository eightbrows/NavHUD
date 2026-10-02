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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.source.TrackDocumentStore
import io.github.eightbrows.navhud.source.WaypointDocumentStore
import io.github.eightbrows.navhud.ui.DebugScreen
import io.github.eightbrows.navhud.ui.HudColors
import io.github.eightbrows.navhud.ui.HudPalette
import io.github.eightbrows.navhud.ui.LocationPermission
import io.github.eightbrows.navhud.ui.MainScreen
import io.github.eightbrows.navhud.ui.NavViewModel
import io.github.eightbrows.navhud.ui.SettingsScreen
import io.github.eightbrows.navhud.ui.WaypointSettingsScreen
import io.github.eightbrows.navhud.ui.WaypointUiState
import io.github.eightbrows.navhud.ui.theme.NavHUDTheme

/** 画面の切り替え（ライブラリは使わない）。 */
private enum class Screen { MAIN, WAYPOINTS, SETTINGS, DEBUG }

class MainActivity : ComponentActivity() {

    private lateinit var vm: NavViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this)[NavViewModel::class.java]
        setContent {
            NavHUDTheme {
                val state by vm.state.collectAsState()
                val replay by vm.replay.collectAsState()
                val wpUi by vm.wp.collectAsState()
                val live by vm.live.collectAsState()
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
                // 色（UI の色と地図の色。基本色だけ切り替える）
                val uiTheme = state.settings.uiTheme
                val mapTheme = state.settings.mapTheme
                LaunchedEffect(uiTheme, mapTheme) {
                    HudColors.uiPalette = HudPalette.of(uiTheme)
                    HudColors.mapPalette = HudPalette.of(mapTheme)
                }
                // 画面常時点灯（§6.8）
                val keepScreenOn = state.settings.keepScreenOn
                DisposableEffect(keepScreenOn) {
                    if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose {}
                }
                val onImport = { importWaypoints.launch(WaypointDocumentStore.IMPORT_MIME_TYPES) }

                BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }

                Scaffold(containerColor = HudColors.Background) { innerPadding ->
                    val modifier = Modifier.padding(innerPadding)
                    when (screen) {
                        Screen.MAIN -> MainScreen(
                            state = state,
                            replay = replay,
                            onPickTrack = onPickTrack,
                            onTogglePlay = vm::togglePlay,
                            onCycleSource = vm::cycleSourceMode,
                            onToggleDisplay = vm::toggleDisplayMode,
                            onCycleRate = vm::cycleRateWindow,
                            onOpenDebug = { screen = Screen.DEBUG },
                            wpUi = wpUi,
                            onToggleWpButtons = vm::toggleWaypointButtons,
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
                            onEndPan = vm::endPan,
                            onSlower = vm::slowerReplay,
                            onFaster = vm::fasterReplay,
                            onSeek = vm::seekReplay,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            // 地図はステータスバーの下から画面の下端まで（下部パネルはナビゲーションバーの上に置く）
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
                            onImport = onImport,
                            onExport = { exportWaypoints.launch(WaypointDocumentStore.EXPORT_DEFAULT_NAME) },
                            onReverse = vm::reverseWaypoints,
                            onAdjustTimes = vm::adjustWaypointTimes,
                            modifier = modifier,
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            settings = state.settings,
                            input = state.sourceKind,
                            onChange = vm::updateSettings,
                            onInput = vm::setSourceKind,
                            onReset = vm::resetSettings,
                            onBack = { screen = Screen.MAIN },
                            modifier = modifier,
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

    override fun onStop() {
        super.onStop()
        vm.onBackground()
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
        title = { Text("WP リスト") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPrevious, enabled = wpUi.hasSavedList) { Text("前回のリスト") }
                TextButton(onClick = onChoose) { Text("リストを選ぶ（CSV / GPX）") }
                TextButton(onClick = onNone) { Text("リストなし") }
            }
        },
        confirmButton = {},
    )
}

/** クリップボードの文字列（なければ null）。 */
private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
