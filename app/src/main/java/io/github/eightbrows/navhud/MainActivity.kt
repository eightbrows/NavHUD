package io.github.eightbrows.navhud

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.eightbrows.navhud.source.TrackDocumentStore
import io.github.eightbrows.navhud.source.WaypointDocumentStore
import io.github.eightbrows.navhud.ui.DebugScreen
import io.github.eightbrows.navhud.ui.HudColors
import io.github.eightbrows.navhud.ui.MainScreen
import io.github.eightbrows.navhud.ui.NavViewModel
import io.github.eightbrows.navhud.ui.WaypointSettingsScreen
import io.github.eightbrows.navhud.ui.WaypointUiState
import io.github.eightbrows.navhud.ui.theme.NavHUDTheme

/** 画面の切り替え（ライブラリは使わない）。 */
private enum class Screen { MAIN, WAYPOINTS, DEBUG }

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
                            modifier = modifier,
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
                            onToggleButtonsSide = vm::toggleWaypointButtonsSide,
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

    // フォアグラウンドのみで動作する（§6.8）。裏に回ったらリプレイを止める
    override fun onStop() {
        super.onStop()
        vm.pause()
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
