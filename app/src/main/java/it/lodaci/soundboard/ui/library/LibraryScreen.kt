package it.lodaci.soundboard.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.lodaci.soundboard.data.SourceType
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.data.TrackKind
import it.lodaci.soundboard.playback.VoiceStatus
import it.lodaci.soundboard.remote.LinkTasks
import it.lodaci.soundboard.ui.AppViewModel
import it.lodaci.soundboard.ui.ScreenScaffold
import it.lodaci.soundboard.ui.formatMs

private enum class Filter(val label: String) { ALL("Tutti"), MUSIC("Musica"), SFX("Effetti") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: AppViewModel, incomingUrl: String?, onIncomingUrlConsumed: () -> Unit) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val tasks by vm.linkTasks.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var fabMenu by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<Uri>?>(null) }
    var linkDialog by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Track?>(null) }
    var confirmDelete by remember { mutableStateOf<List<Track>?>(null) }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            linkDialog = incomingUrl
            onIncomingUrlConsumed()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pendingUris = uris
    }

    val playingIds = voices.filter { it.status != VoiceStatus.STOPPING && it.status != VoiceStatus.PAUSED }
        .map { it.trackId }.toSet()
    val shown = tracks.filter {
        (filter == Filter.ALL || (filter == Filter.MUSIC) == (it.kind == TrackKind.MUSIC)) &&
            (query.isBlank() || it.title.contains(query, ignoreCase = true))
    }
    val selectionMode = selected.isNotEmpty()

    ScreenScaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { selected.clear() }) { Icon(Icons.Default.Close, "Annulla selezione") } },
                    title = { Text("${selected.size} selezionate") },
                    actions = {
                        IconButton(onClick = { confirmDelete = tracks.filter { it.id in selected } }) {
                            Icon(Icons.Default.Delete, "Elimina")
                        }
                    },
                )
            } else if (searching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Cerca…") }, modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, "Chiudi ricerca") } },
                )
            } else {
                TopAppBar(
                    title = { Text("Libreria") },
                    actions = { IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Cerca") } },
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                BottomAppBar {
                    Button(
                        onClick = {
                            vm.startTogether(tracks.filter { it.id in selected })
                            selected.clear()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("  Avvia insieme con fade in")
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { fabMenu = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Aggiungi") },
                    )
                    DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("File dal telefono") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            onClick = { fabMenu = false; picker.launch(arrayOf("audio/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Link (YouTube, ecc.)") },
                            leadingIcon = { Icon(Icons.Default.Link, null) },
                            onClick = { fabMenu = false; linkDialog = "" },
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Filter.entries) { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            items(tasks, key = { "task-" + it.id }) { task -> TaskCard(task, onDismiss = { vm.dismissTask(task.id) }) }
            if (tracks.isEmpty()) {
                item {
                    Text(
                        "La libreria è vuota.\nTocca \"Aggiungi\" per importare file audio dal telefono o incollare un link YouTube.",
                        modifier = Modifier.padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.id }) { track ->
                val isSelected = track.id in selected
                TrackRow(
                    track = track,
                    playing = track.id in playingIds,
                    selected = isSelected,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                if (isSelected) selected.remove(track.id) else selected.add(track.id)
                            } else vm.toggle(track)
                        },
                        onLongClick = { if (isSelected) selected.remove(track.id) else selected.add(track.id) },
                    ),
                    onToggle = { vm.toggle(track) },
                    onEdit = { editing = track },
                    onDownload = { vm.downloadExisting(track) },
                    onDelete = { confirmDelete = listOf(track) },
                )
            }
        }
    }

    pendingUris?.let { uris ->
        KindDialog(
            count = uris.size,
            onDismiss = { pendingUris = null },
            onPick = { kind -> vm.importLocal(uris, kind); pendingUris = null },
        )
    }
    linkDialog?.let { initial ->
        LinkDialog(
            initial = initial,
            onDismiss = { linkDialog = null },
            onConfirm = { url, kind, download -> vm.addLink(url, kind, download); linkDialog = null },
        )
    }
    editing?.let { t ->
        TrackEditDialog(t, onDismiss = { editing = null }, onSave = { vm.updateTrack(it); editing = null })
    }
    confirmDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (list.size == 1) "Eliminare \"${list[0].title}\"?" else "Eliminare ${list.size} tracce?") },
            text = { Text("I file scaricati verranno cancellati. I file importati dal telefono non vengono toccati.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTracks(list); selected.clear(); confirmDelete = null }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    playing: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val source = when (track.source) {
        SourceType.LOCAL -> "File locale"
        SourceType.STREAM -> "Streaming"
        SourceType.DOWNLOADED -> "Scaricato"
    }
    val kind = if (track.kind == TrackKind.MUSIC) "Musica" else "Effetto"
    ListItem(
        modifier = modifier,
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else ListItemDefaults.colors(),
        leadingContent = {
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
            else Icon(if (track.kind == TrackKind.SFX) Icons.Default.GraphicEq else Icons.Default.MusicNote, null)
        },
        headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("$kind • $source • ${formatMs(track.durationMs)}" + if (track.loop) " • loop" else "")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (playing) "Ferma" else "Riproduci",
                        tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Altro") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Modifica") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        if (track.source == SourceType.STREAM) {
                            DropdownMenuItem(
                                text = { Text("Scarica in locale") }, leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { menu = false; onDownload() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Elimina") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun TaskCard(task: LinkTasks.Task, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.error != null) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (task.error == null && task.progress < 0) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(24.dp), strokeWidth = 2.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                when {
                    task.error != null -> {
                        Text(task.error, style = MaterialTheme.typography.bodyMedium)
                        task.errorDetail?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    else -> {
                        val label = task.status ?: if (task.download && task.progress >= 0) "Download…" else "Lettura del link…"
                        Text(
                            label + if (task.progress >= 0) " ${task.progress.toInt()}%" else "",
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (task.progress >= 0) {
                            LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
            }
            if (task.error != null) IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Chiudi") }
        }
    }
}

@Composable
private fun KindSelector(kind: TrackKind, onChange: (TrackKind) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(TrackKind.MUSIC to "Musica (loop)", TrackKind.SFX to "Effetto sonoro").forEachIndexed { i, (k, label) ->
            SegmentedButton(
                selected = kind == k,
                onClick = { onChange(k) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(label) }
        }
    }
}

@Composable
private fun KindDialog(count: Int, onDismiss: () -> Unit, onPick: (TrackKind) -> Unit) {
    var kind by remember { mutableStateOf(TrackKind.MUSIC) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Importa 1 file" else "Importa $count file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Che tipo di audio è?")
                KindSelector(kind) { kind = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(kind) }) { Text("Importa") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun LinkDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String, TrackKind, Boolean) -> Unit) {
    var url by remember { mutableStateOf(initial) }
    var kind by remember { mutableStateOf(TrackKind.MUSIC) }
    val clipboard = LocalClipboardManager.current
    val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi da link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL (YouTube o file audio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) {
                            Icon(Icons.Default.ContentPaste, "Incolla")
                        }
                    },
                )
                KindSelector(kind) { kind = it }
                Text(
                    "Streaming: non occupa spazio ma serve internet durante la sessione.\n" +
                        "Scarica: salva l'audio nel telefono e funziona offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onConfirm(url, kind, false) }, enabled = valid, modifier = Modifier.weight(1f)) {
                        Text("Streaming")
                    }
                    FilledTonalButton(onClick = { onConfirm(url, kind, true) }, enabled = valid, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, null)
                        Text(" Scarica")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun TrackEditDialog(track: Track, onDismiss: () -> Unit, onSave: (Track) -> Unit) {
    var title by remember { mutableStateOf(track.title) }
    var kind by remember { mutableStateOf(track.kind) }
    var loop by remember { mutableStateOf(track.loop) }
    var volume by remember { mutableStateOf(track.volume) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica traccia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
                KindSelector(kind) { kind = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ripeti in loop", Modifier.weight(1f))
                    Switch(checked = loop, onCheckedChange = { loop = it })
                }
                Text("Volume predefinito: ${(volume * 100).toInt()}%")
                Slider(value = volume, onValueChange = { volume = it })
                if (track.author != null || track.license != null || track.sourceUrl != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Text("Crediti", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(track.author?.let { "Autore: $it" }, track.license?.let { "Licenza: $it" })
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    track.sourceUrl?.takeIf { it.startsWith("http") }?.let { link ->
                        TextButton(onClick = {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(link)))
                        }) { Text("Apri la pagina originale") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(track.copy(title = title.trim().ifEmpty { track.title }, kind = kind, loop = loop, volume = volume)) },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
