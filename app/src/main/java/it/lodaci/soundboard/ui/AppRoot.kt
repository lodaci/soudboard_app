package it.lodaci.soundboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.lodaci.soundboard.ui.board.SoundboardScreen
import it.lodaci.soundboard.ui.library.LibraryScreen
import it.lodaci.soundboard.ui.mixer.MixerScreen
import it.lodaci.soundboard.ui.output.OutputScreen
import kotlinx.coroutines.flow.StateFlow

private enum class Tab(val label: String, val icon: ImageVector) {
    BOARD("Soundboard", Icons.Default.GridView),
    LIBRARY("Libreria", Icons.Default.LibraryMusic),
    MIXER("Mixer", Icons.Default.Tune),
    OUTPUT("Uscita", Icons.Default.Bluetooth),
}

@Composable
fun AppRoot(sharedUrl: StateFlow<String?>, onSharedUrlConsumed: () -> Unit) {
    val vm: AppViewModel = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(Tab.BOARD.ordinal) }
    val snackbar = remember { SnackbarHostState() }
    val voices by vm.voices.collectAsStateWithLifecycle()
    val incomingUrl by sharedUrl.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) tab = Tab.LIBRARY.ordinal
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = {
                            if (t == Tab.MIXER && voices.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text(voices.size.toString()) } }) { Icon(t.icon, null) }
                            } else {
                                Icon(t.icon, null)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.BOARD -> SoundboardScreen(vm)
                Tab.LIBRARY -> LibraryScreen(vm, incomingUrl = incomingUrl, onIncomingUrlConsumed = onSharedUrlConsumed)
                Tab.MIXER -> MixerScreen(vm)
                Tab.OUTPUT -> OutputScreen(vm)
            }
        }
    }
}
