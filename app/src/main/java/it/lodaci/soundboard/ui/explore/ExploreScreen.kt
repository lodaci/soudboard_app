package it.lodaci.soundboard.ui.explore

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.lodaci.soundboard.data.TrackKind
import it.lodaci.soundboard.remote.catalog.SoundResult
import it.lodaci.soundboard.remote.catalog.SourceId
import it.lodaci.soundboard.ui.ScreenScaffold
import it.lodaci.soundboard.ui.formatMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen() {
    val vm: ExploreViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<SoundResult?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) { onDispose { vm.stopPreview() } }

    ScreenScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Esplora suoni") },
                actions = { IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Configura sorgenti") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SourceId.entries) { s ->
                        FilterChip(selected = ui.source == s, onClick = { vm.selectSource(s) }, label = { Text(s.label) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    placeholder = { Text(if (ui.source == SourceId.ARCHIVE) "Cerca (vuoto = sfoglia tutto)" else "Cerca in inglese: sword, tavern, dragon…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, "Cancella") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                if (ui.source == SourceId.ARCHIVE) {
                    if (ui.collections.isNotEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = ui.collection == null, onClick = { vm.setCollection(null) }, label = { Text("Tutte") }) }
                            items(ui.collections) { col ->
                                FilterChip(selected = ui.collection == col, onClick = { vm.setCollection(col) }, label = { Text(col) })
                            }
                        }
                    }
                } else {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TrackKind.SFX to "Effetti", TrackKind.MUSIC to "Musica", null to "Tutto").forEach { (k, label) ->
                            FilterChip(selected = ui.kind == k, onClick = { vm.setKind(k) }, label = { Text(label) })
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Button(onClick = { vm.search() }, enabled = !ui.loading && ui.notConfigured == null, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ui.source == SourceId.ARCHIVE && ui.query.isBlank()) "Sfoglia" else "Cerca")
                    }
                }
            }

            ui.notConfigured?.let { reason ->
                item {
                    InfoCard(reason) {
                        if (ui.source == SourceId.FREESOUND) {
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://freesound.org/apiv2/apply")))
                            }) { Text("Crea la chiave") }
                        }
                        TextButton(onClick = { settingsOpen = true }) { Text("Configura") }
                    }
                }
            }
            ui.error?.let { err -> item { InfoCard(err, error = true) {} } }
            preview.error?.let { err -> item { InfoCard(err, error = true) {} } }

            if (ui.searched && !ui.loading && ui.results.isEmpty() && ui.error == null) {
                item { Text("Nessun risultato.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (ui.results.isNotEmpty()) {
                item {
                    Text(
                        "${ui.total} risultati",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            itemsIndexed(ui.results, key = { i, r -> "${r.source}:${r.id}:$i" }) { _, r ->
                val key = "${r.source}:${r.id}"
                ResultRow(
                    r = r,
                    playing = preview.key == key,
                    loading = preview.key == key && preview.loading,
                    onPreview = { vm.togglePreview(r) },
                    onAdd = { adding = r },
                )
                HorizontalDivider()
            }
            if (ui.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (ui.hasMore) {
                item {
                    OutlinedButton(onClick = { vm.loadMore() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Carica altri") }
                }
            }
            item {
                Text(
                    when (ui.source) {
                        SourceId.OPENVERSE -> "Openverse raccoglie audio con licenze Creative Commons da Freesound, Wikimedia, Jamendo e altri. Controlla la licenza: per CC BY va citato l'autore (i crediti restano salvati nella traccia)."
                        SourceId.FREESOUND -> "Da Freesound viene scaricata la versione MP3 di alta qualità. Licenze Creative Commons: i crediti restano salvati nella traccia."
                        SourceId.ARCHIVE -> "Il tuo archivio SoundVault sul NUC. I WAV pesanti arrivano convertiti in MP3 se sul NUC c'è ffmpeg."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    adding?.let { r ->
        AddDialog(r, onDismiss = { adding = null }) { kind ->
            vm.add(r, kind)
            adding = null
        }
    }
    if (settingsOpen) {
        SourcesSettingsDialog(vm, onDismiss = { settingsOpen = false })
    }
}

@Composable
private fun InfoCard(text: String, error: Boolean = false, actions: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { actions() }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}

@Composable
private fun ResultRow(r: SoundResult, playing: Boolean, loading: Boolean, onPreview: () -> Unit, onAdd: () -> Unit) {
    val details = listOfNotNull(
        formatMs(r.durationMs).takeIf { r.durationMs > 0 },
        r.format?.uppercase(),
        formatSize(r.sizeBytes).ifEmpty { null },
        r.license,
    ).joinToString(" • ")
    val sub = listOfNotNull(r.author?.let { "di $it" }, r.origin).joinToString(" — ")
    ListItem(
        leadingContent = {
            IconButton(onClick = onPreview) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    playing -> Icon(Icons.Default.Stop, "Ferma anteprima", tint = MaterialTheme.colorScheme.primary)
                    else -> Icon(Icons.Default.PlayArrow, "Anteprima")
                }
            }
        },
        headlineContent = { Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (sub.isNotBlank()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.labelSmall)
            }
        },
        trailingContent = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Aggiungi alla libreria") } },
    )
}

@Composable
private fun AddDialog(r: SoundResult, onDismiss: () -> Unit, onAdd: (TrackKind) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi alla libreria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall)
                if (r.license != null || r.author != null) {
                    Text(
                        "Licenza: ${r.license ?: "—"}${r.author?.let { " • Autore: $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Verrà scaricato nel telefono e funzionerà offline.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onAdd(TrackKind.SFX) }) { Text(if (r.suggestedKind == TrackKind.SFX) "Effetto ✓" else "Effetto") }
                TextButton(onClick = { onAdd(TrackKind.MUSIC) }) { Text(if (r.suggestedKind == TrackKind.MUSIC) "Musica ✓" else "Musica") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun SourcesSettingsDialog(vm: ExploreViewModel, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(vm.settings.freesoundKey) }
    var url by remember { mutableStateOf(vm.settings.archiveUrl) }
    var token by remember { mutableStateOf(vm.settings.archiveToken) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val insecure = url.startsWith("http://") && !isLocalAddress(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sorgenti") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Freesound", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true,
                    label = { Text("Chiave API (Client secret)") }, modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text("Il mio archivio (SoundVault sul NUC)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = url, onValueChange = { url = it; testMsg = null }, singleLine = true,
                    label = { Text("Indirizzo") }, placeholder = { Text("https://suoni.miodominio.it") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it; testMsg = null }, singleLine = true,
                    label = { Text("Token") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (insecure) {
                    Text(
                        "Attenzione: con http:// il token viaggia in chiaro su internet. Da fuori casa usa https://.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    enabled = !testing && url.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        testing = true
                        vm.testArchive(url, token) { testMsg = it; testing = false }
                    },
                ) { Text(if (testing) "Connessione…" else "Prova connessione") }
                testMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveSettings(key, url, token); onDismiss() }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

private fun isLocalAddress(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return false
    return host == "localhost" || host.endsWith(".local") || host.startsWith("192.168.") || host.startsWith("10.") ||
        Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(host) || host.startsWith("100.")
}

