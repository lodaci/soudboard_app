package it.lodaci.soundboard.ui.board

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.lodaci.soundboard.data.Board
import it.lodaci.soundboard.data.Pad
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.playback.VoiceStatus
import it.lodaci.soundboard.ui.AppViewModel
import it.lodaci.soundboard.ui.ScreenScaffold
import it.lodaci.soundboard.ui.contentColorFor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundboardScreen(vm: AppViewModel) {
    val boards by vm.boards.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()

    var selectedId by rememberSaveable { mutableLongStateOf(vm.settings.lastBoardId) }
    val board = boards.firstOrNull { it.id == selectedId } ?: boards.firstOrNull()
    LaunchedEffect(board?.id) { board?.let { vm.settings.lastBoardId = it.id } }

    var menuOpen by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<NameDialogMode?>(null) }
    var columnsDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }

    ScreenScaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Soundboard") },
                    actions = {
                        IconButton(onClick = { vm.mixer.stopAll() }, enabled = voices.isNotEmpty()) {
                            Icon(Icons.Default.StopCircle, "Ferma tutto con dissolvenza")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Nuova scheda") }, onClick = { menuOpen = false; nameDialog = NameDialogMode.NEW })
                                if (board != null) {
                                    DropdownMenuItem(text = { Text("Rinomina scheda") }, onClick = { menuOpen = false; nameDialog = NameDialogMode.RENAME })
                                    DropdownMenuItem(text = { Text("Colonne (${board.columns})") }, onClick = { menuOpen = false; columnsDialog = true })
                                    DropdownMenuItem(text = { Text("Aggiungi una riga di tasti") }, onClick = {
                                        menuOpen = false
                                        vm.updateBoard(board.copy(slots = board.slots + board.columns))
                                    })
                                    if (board.slots > board.columns) {
                                        DropdownMenuItem(text = { Text("Rimuovi ultima riga") }, onClick = {
                                            menuOpen = false
                                            vm.updateBoard(board.copy(slots = (board.slots - board.columns).coerceAtLeast(board.columns)))
                                        })
                                    }
                                    if (boards.size > 1) {
                                        DropdownMenuItem(text = { Text("Elimina scheda") }, onClick = { menuOpen = false; confirmDelete = true })
                                    }
                                }
                            }
                        }
                    },
                )
                if (boards.isNotEmpty()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = boards.indexOf(board).coerceAtLeast(0),
                        edgePadding = 8.dp,
                    ) {
                        boards.forEach { b ->
                            Tab(selected = b.id == board?.id, onClick = { selectedId = b.id }, text = { Text(b.name) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (board == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Caricamento…") }
            return@ScreenScaffold
        }
        val pads by remember(board.id) { vm.padsFor(board.id) }.collectAsStateWithLifecycle(emptyList())
        val padByPos = pads.associateBy { it.position }
        val trackById = tracks.associateBy { it.id }
        val activeIds = voices.filter { it.status != VoiceStatus.STOPPING && it.status != VoiceStatus.PAUSED }
            .map { it.trackId }.toSet()
        val slotCount = maxOf(board.slots, (pads.maxOfOrNull { it.position } ?: -1) + 1)

        LazyVerticalGrid(
            columns = GridCells.Fixed(board.columns),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(slotCount, key = { it }) { pos ->
                val pad = padByPos[pos]
                val track = pad?.trackId?.let { trackById[it] }
                PadTile(
                    pad = pad,
                    track = track,
                    active = track != null && track.id in activeIds,
                    columns = board.columns,
                    onTap = {
                        if (pad != null && track != null) vm.triggerPad(pad) else editing = pos
                    },
                    onLongPress = { editing = pos },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(
                    onClick = { vm.updateBoard(board.copy(slots = slotCount + board.columns)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("  Aggiungi riga")
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tocca un tasto per suonare • tieni premuto per modificarlo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }

        editing?.let { pos ->
            PadEditorDialog(
                initial = padByPos[pos] ?: Pad(boardId = board.id, position = pos, trackId = null),
                tracks = tracks,
                onDismiss = { editing = null },
                onSave = { vm.savePad(it); editing = null },
                onClear = { vm.clearPad(board.id, pos); editing = null },
            )
        }
    }

    nameDialog?.let { mode ->
        NameDialog(
            title = if (mode == NameDialogMode.NEW) "Nuova scheda" else "Rinomina scheda",
            initial = if (mode == NameDialogMode.RENAME) board?.name.orEmpty() else "",
            onDismiss = { nameDialog = null },
            onConfirm = { name ->
                nameDialog = null
                if (mode == NameDialogMode.NEW) vm.addBoard(name) { selectedId = it }
                else board?.let { vm.updateBoard(it.copy(name = name)) }
            },
        )
    }
    if (columnsDialog && board != null) {
        ColumnsDialog(board, onDismiss = { columnsDialog = false }) { cols ->
            columnsDialog = false
            val rows = (board.slots + board.columns - 1) / board.columns
            vm.updateBoard(board.copy(columns = cols, slots = maxOf(rows * cols, cols)))
        }
    }
    if (confirmDelete && board != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare \"${board.name}\"?") },
            text = { Text("I tasti di questa scheda verranno rimossi. Le tracce restano nella libreria.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteBoard(board) }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}

private enum class NameDialogMode { NEW, RENAME }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PadTile(
    pad: Pad?,
    track: Track?,
    active: Boolean,
    columns: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .aspectRatio(1f)
        .clip(shape)
        .combinedClickable(
            onClick = onTap,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            },
        )

    if (pad == null) {
        Box(
            base.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, "Assegna tasto", tint = MaterialTheme.colorScheme.outlineVariant)
        }
        return
    }

    val bg = Color(pad.color)
    val fg = contentColorFor(bg)
    val pulse = if (active) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a").value
    } else 0f
    val emojiSize = when (columns) { 3 -> 40.sp; 4 -> 32.sp; 5 -> 26.sp; else -> 22.sp }
    val labelSize = when (columns) { 3 -> 14.sp; 4 -> 12.sp; else -> 10.sp }

    Box(
        base
            .background(bg)
            .then(
                if (active) Modifier.border(3.dp, Color.White.copy(alpha = pulse), shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp),
        ) {
            if (pad.emoji.isNotBlank()) Text(pad.emoji, fontSize = emojiSize)
            val label = pad.label.ifBlank { if (pad.emoji.isBlank()) track?.title.orEmpty() else "" }
            if (label.isNotBlank()) {
                Text(
                    label,
                    color = fg,
                    fontSize = labelSize,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = labelSize * 1.1f,
                )
            }
            if (track == null) Text("(nessuna traccia)", color = fg.copy(alpha = 0.7f), fontSize = 9.sp)
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nome") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun ColumnsDialog(board: Board, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var cols by remember { mutableFloatStateOf(board.columns.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colonne: ${cols.roundToInt()}") },
        text = { Slider(value = cols, onValueChange = { cols = it }, valueRange = 2f..6f, steps = 3) },
        confirmButton = { TextButton(onClick = { onConfirm(cols.roundToInt()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
