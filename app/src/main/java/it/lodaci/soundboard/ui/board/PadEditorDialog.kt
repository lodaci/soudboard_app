package it.lodaci.soundboard.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lodaci.soundboard.data.Pad
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.data.TrackKind
import it.lodaci.soundboard.ui.contentColorFor

private val EMOJI = listOf(
    "⚔️", "🗡️", "🛡️", "🏹", "🪄", "🔮", "🐉", "🔥", "❄️", "⚡", "🌩️", "🌧️",
    "🌲", "🌊", "🏰", "⛪", "🍺", "🎻", "🥁", "🎺", "👻", "💀", "🧟", "🐺",
    "🕷️", "🦇", "👑", "💰", "🚪", "🔔", "🐎", "🌙", "☀️", "💥", "😱", "🎲",
)

private val COLORS = listOf(
    0xFF3A3150, 0xFF5E2129, 0xFF9B2D20, 0xFFC0571B, 0xFFD4A017, 0xFF6B8E23,
    0xFF2E6B3A, 0xFF1F6F6B, 0xFF24527A, 0xFF283C8C, 0xFF5B2C83, 0xFF8E2C6E,
    0xFF5D4037, 0xFF455A64, 0xFF212121, 0xFFE8E0D0,
).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PadEditorDialog(
    initial: Pad,
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onSave: (Pad) -> Unit,
    onClear: () -> Unit,
) {
    var trackId by remember { mutableStateOf(initial.trackId) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var label by remember { mutableStateOf(initial.label) }
    var color by remember { mutableStateOf(initial.color) }
    var fade by remember { mutableStateOf(initial.fade) }
    var picking by remember { mutableStateOf(initial.trackId == null && tracks.isNotEmpty()) }
    val track = tracks.firstOrNull { it.id == trackId }

    if (picking) {
        TrackPickerDialog(
            tracks = tracks,
            selectedId = trackId,
            onDismiss = { picking = false },
            onPick = { t ->
                picking = false
                if (trackId == null) {
                    // primo collegamento: imposta valori sensati
                    fade = t.kind == TrackKind.MUSIC
                    if (label.isBlank()) label = t.title.take(24)
                }
                trackId = t.id
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tasto ${initial.position + 1}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // anteprima
                val bg = Color(color)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (emoji.isNotBlank()) Text(emoji, fontSize = 30.sp)
                        if (label.isNotBlank()) Text("  $label", color = contentColorFor(bg), maxLines = 2)
                    }
                }

                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (track?.kind == TrackKind.SFX) Icons.Default.GraphicEq else Icons.Default.MusicNote, null)
                    Text(
                        "  " + (track?.title ?: if (tracks.isEmpty()) "Libreria vuota: importa prima delle tracce" else "Scegli traccia"),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(40) },
                    label = { Text("Testo") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emoji, onValueChange = { emoji = it.take(8) },
                    label = { Text("Emoji (anche dalla tastiera)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EMOJI.forEach { e ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = if (emoji == e) "" else e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }

                Text("Colore", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEach { c ->
                        val col = Color(c)
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(col)
                                .border(2.dp, if (c == color) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == color) Icon(Icons.Default.Check, null, tint = contentColorFor(col), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HueSlider(color) { color = it }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dissolvenza")
                        Text(
                            "Avvio/stop con fade in/out",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = fade, onCheckedChange = { fade = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(trackId = trackId, emoji = emoji.trim(), label = label.trim(), color = color, fade = fade)) },
            ) { Text("Salva") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Svuota") }
                Spacer(Modifier.size(4.dp))
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}

/** Colore personalizzato: tonalità continua a saturazione/luminosità fisse. */
@Composable
private fun HueSlider(color: Int, onChange: (Int) -> Unit) {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    Column {
        Text("Tonalità personalizzata", style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Slider(
            value = hsv[0],
            onValueChange = { h -> onChange(Color.hsv(h, 0.65f, 0.6f).toArgb()) },
            valueRange = 0f..359f,
        )
    }
}

@Composable
fun TrackPickerDialog(
    tracks: List<Track>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = tracks.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli traccia") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Cerca") }, modifier = Modifier.fillMaxWidth(),
                )
                if (tracks.isEmpty()) {
                    Text("Nessuna traccia. Vai su Libreria per importare file o link.", Modifier.padding(top = 12.dp))
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.id }) { t ->
                        ListItem(
                            headlineContent = { Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (t.kind == TrackKind.MUSIC) "Musica" else "Effetto") },
                            leadingContent = {
                                Icon(if (t.kind == TrackKind.SFX) Icons.Default.GraphicEq else Icons.Default.MusicNote, null)
                            },
                            trailingContent = { if (t.id == selectedId) Icon(Icons.Default.Check, null) },
                            modifier = Modifier.clickable { onPick(t) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}
