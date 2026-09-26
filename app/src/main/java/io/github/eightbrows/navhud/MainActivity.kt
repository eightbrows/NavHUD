package io.github.eightbrows.navhud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import io.github.eightbrows.navhud.source.TrackDocumentStore
import io.github.eightbrows.navhud.ui.DebugScreen
import io.github.eightbrows.navhud.ui.HudColors
import io.github.eightbrows.navhud.ui.MainScreen
import io.github.eightbrows.navhud.ui.NavViewModel
import io.github.eightbrows.navhud.ui.theme.NavHUDTheme

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
                var showDebug by rememberSaveable { mutableStateOf(false) }
                val pickTrack = rememberLauncherForActivityResult(TrackDocumentStore.OpenTrackDocument()) {
                    vm.onTrackPicked(it)
                }
                val onPickTrack = { pickTrack.launch(TrackDocumentStore.MIME_TYPES) }
                Scaffold(containerColor = HudColors.Background) { innerPadding ->
                    if (showDebug) {
                        DebugScreen(
                            state = state,
                            replay = replay,
                            onPickTrack = onPickTrack,
                            onTogglePlay = vm::togglePlay,
                            onSourceMode = vm::setSourceMode,
                            onToggleWp = vm::toggleReached,
                            onClose = { showDebug = false },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        MainScreen(
                            state = state,
                            replay = replay,
                            onPickTrack = onPickTrack,
                            onTogglePlay = vm::togglePlay,
                            onCycleSource = vm::cycleSourceMode,
                            onToggleDisplay = vm::toggleDisplayMode,
                            onCycleRate = vm::cycleRateWindow,
                            onOpenDebug = { showDebug = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
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
