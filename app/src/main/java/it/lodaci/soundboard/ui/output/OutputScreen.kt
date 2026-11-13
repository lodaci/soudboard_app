package it.lodaci.soundboard.ui.output

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.lodaci.soundboard.ui.AppViewModel
import it.lodaci.soundboard.ui.ScreenScaffold

private data class Output(val name: String, val icon: ImageVector, val bluetooth: Boolean)

private fun currentOutput(am: AudioManager): Output {
    val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val btTypes = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= 31) {
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
    devices.firstOrNull { it.type in btTypes }?.let {
        return Output("Bluetooth: ${it.productName}", Icons.Default.BluetoothConnected, true)
    }
    devices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
    }?.let { return Output("Cuffie / USB: ${it.productName}", Icons.Default.Headphones, false) }
    return Output("Altoparlante del telefono", Icons.Default.Speaker, false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var output by remember { mutableStateOf(currentOutput(am)) }
    var ytdlpVersion by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(am) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { output = currentOutput(am) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { output = currentOutput(am) }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    LaunchedEffect(Unit) { ytdlpVersion = vm.ytdlpVersion() }

    ScreenScaffold(topBar = { TopAppBar(title = { Text("Uscita audio") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (output.bluetooth) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(output.icon, null)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("L'audio esce da", style = MaterialTheme.typography.labelMedium)
                        Text(output.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Bluetooth, null)
                    Text(" Bluetooth")
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 1000)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null)
                    Text(" Suono di prova")
                }
            }

            Guide()

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Motore link (yt-dlp)", style = MaterialTheme.typography.titleMedium)
                    Text("Versione: ${ytdlpVersion ?: "inizializzazione…"}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Se i link YouTube smettono di funzionare, aggiorna yt-dlp (serve internet).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        enabled = !updating,
                        onClick = {
                            updating = true
                            updateMsg = null
                            vm.updateYtDlp { msg ->
                                updating = false
                                updateMsg = msg
                            }
                        },
                    ) { Text(if (updating) "Aggiornamento…" else "Aggiorna yt-dlp") }
                    updateMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun Guide() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Usare le casse del laptop (Windows)", style = MaterialTheme.typography.titleMedium)
            val steps = listOf(
                "Sul PC: Impostazioni → Bluetooth e dispositivi → Aggiungi dispositivo → Bluetooth, e accoppia il telefono.",
                "Sul PC installa dal Microsoft Store l'app gratuita \"Bluetooth Audio Receiver\" (Windows 10/11).",
                "Aprila, seleziona il tuo telefono e premi \"Open Connection\": il PC diventa una cassa Bluetooth.",
                "Qui sopra dovrebbe comparire \"Bluetooth: <nome PC>\". Premi \"Suono di prova\" per verificare.",
                "Regola il volume dal telefono o dal mixer di Windows. Tutti i suoni della soundboard usciranno dal laptop.",
            )
            steps.forEachIndexed { i, s ->
                Row {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Suggerimento: se l'audio si interrompe, avvicina il telefono al PC e chiudi altre app che usano il Bluetooth. " +
                    "La riproduzione continua anche a schermo spento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
