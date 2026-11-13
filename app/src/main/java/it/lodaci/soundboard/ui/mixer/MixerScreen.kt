package it.lodaci.soundboard.ui.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.lodaci.soundboard.playback.VoiceState
import it.lodaci.soundboard.playback.VoiceStatus
import it.lodaci.soundboard.ui.AppViewModel
import it.lodaci.soundboard.ui.ScreenScaffold
import it.lodaci.soundboard.ui.formatMs
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerScreen(vm: AppViewModel) {
    val voices by vm.voices.collectAsStateWithLifecycle()
    val fadeMs by vm.settings.fadeMs.collectAsStateWithLifecycle()
    val master by vm.settings.master.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<Long>() }
    LaunchedEffect(voices) { selected.retainAll(voices.map { it.trackId }.toSet()) }

    ScreenScaffold(topBar = { TopAppBar(title = { Text("Mixer") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        LabeledSlider(
                            icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                            label = "Volume generale ${(master * 100).toInt()}%",
                            value = master,
                            onChange = { vm.mixer.setMaster(it) },
                        )
                        LabeledSlider(
                            icon = { Icon(Icons.Default.Timer, null) },
                            label = "Dissolvenza: %.1f s".format(fadeMs / 1000f),
                            value = fadeMs / 10_000f,
                            steps = 19,
                            onChange = { vm.settings.setFadeMs(((it * 20).roundToLong()) * 500L) },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { selected.toList().forEach { vm.mixer.stop(it) } },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Fade out (${selected.size})") }
                    FilledTonalButton(
                        onClick = { selected.toList().forEach { vm.mixer.resume(it) } },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Fade in (${selected.size})") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.mixer.stopAll() }, enabled = voices.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("Stop tutto (fade)")
                    }
                    OutlinedButton(onClick = { vm.mixer.stopNow() }, enabled = voices.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("Stop immediato")
                    }
                }
            }
            if (voices.isEmpty()) {
                item {
                    Text(
                        "Nessuna traccia in riproduzione.\nAvviale dalla Soundboard o dalla Libreria: qui potrai regolarne il volume e sfumarle.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(voices, key = { it.trackId }) { v ->
                VoiceCard(
                    v = v,
                    selected = v.trackId in selected,
                    onSelect = { if (it) selected.add(v.trackId) else selected.remove(v.trackId) },
                    onVolume = { vm.mixer.setVolume(v.trackId, it) },
                    onPauseResume = {
                        if (v.status == VoiceStatus.PAUSED || v.status == VoiceStatus.STOPPING) vm.mixer.resume(v.trackId)
                        else vm.mixer.pause(v.trackId)
                    },
                    onStop = { vm.mixer.stop(v.trackId) },
                    onLoop = { vm.mixer.setLoop(v.trackId, it) },
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    icon: @Composable () -> Unit,
    label: String,
    value: Float,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text("  $label", style = MaterialTheme.typography.titleSmall)
        }
        Slider(value = value, onValueChange = onChange, steps = steps)
    }
}

@Composable
private fun VoiceCard(
    v: VoiceState,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onLoop: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = onSelect)
                Column(Modifier.weight(1f)) {
                    Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    val status = when (v.status) {
                        VoiceStatus.LOADING -> "Caricamento…"
                        VoiceStatus.PLAYING -> "In riproduzione"
                        VoiceStatus.PAUSED -> "In pausa"
                        VoiceStatus.STOPPING -> "Dissolvenza in uscita…"
                    }
                    Text(
                        "$status • ${formatMs(v.positionMs)} / ${formatMs(v.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconToggleButton(checked = v.loop, onCheckedChange = onLoop) {
                    Icon(
                        Icons.Default.Repeat, "Loop",
                        tint = if (v.loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPauseResume) {
                    val paused = v.status == VoiceStatus.PAUSED || v.status == VoiceStatus.STOPPING
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) "Riprendi" else "Pausa")
                }
                IconButton(onClick = onStop) { Icon(Icons.Default.Stop, "Ferma") }
            }
            if (v.status == VoiceStatus.LOADING) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            } else if (v.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (v.positionMs.toFloat() / v.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = v.volume, onValueChange = onVolume, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("${(v.volume * 100).toInt()}%", modifier = Modifier.width(44.dp))
            }
        }
    }
}
