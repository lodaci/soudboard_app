package it.lodaci.soundboard.remote

import android.content.Context
import android.net.Uri
import it.lodaci.soundboard.data.AudioMeta
import it.lodaci.soundboard.data.SourceType
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.data.TrackDao
import it.lodaci.soundboard.data.TrackKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Operazioni in corso su link (lettura info / download), mostrate nella Libreria. */
class LinkTasks(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ytdlp: YtDlpManager,
    private val trackDao: TrackDao,
) {
    data class Task(
        val id: String,
        val url: String,
        val title: String,
        val download: Boolean,
        val progress: Float = -1f,
        val error: String? = null,
    )

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /**
     * @param download true = scarica e salva in locale, false = aggiungi come streaming
     * @param existing se valorizzato, converte una traccia STREAM esistente in DOWNLOADED
     */
    fun add(url: String, kind: TrackKind, download: Boolean, existing: Track? = null) {
        val id = UUID.randomUUID().toString()
        _tasks.update { it + Task(id, url, existing?.title ?: url, download) }
        scope.launch(Dispatchers.IO) {
            try {
                val direct = YtDlpManager.isDirectAudio(url)
                val title: String
                val durationMs: Long
                if (existing != null) {
                    title = existing.title
                    durationMs = existing.durationMs
                } else if (direct) {
                    title = AudioMeta.titleFromFileName(Uri.parse(url).lastPathSegment ?: url)
                    durationMs = 0
                } else {
                    val info = ytdlp.fetchInfo(url)
                    title = info.title
                    durationMs = info.durationMs
                }
                updateTask(id) { it.copy(title = title) }

                if (!download) {
                    trackDao.insert(
                        Track(
                            title = title, kind = kind, source = SourceType.STREAM,
                            uri = if (direct) url else null, remoteUrl = url,
                            durationMs = durationMs, loop = kind == TrackKind.MUSIC,
                        )
                    )
                    _events.tryEmit("Aggiunto in streaming: $title")
                } else {
                    val file = ytdlp.download(url) { p -> updateTask(id) { it.copy(progress = p) } }
                    val realDuration = AudioMeta.durationMs(context, Uri.fromFile(file)).takeIf { it > 0 } ?: durationMs
                    if (existing != null) {
                        trackDao.update(
                            existing.copy(source = SourceType.DOWNLOADED, uri = file.absolutePath, durationMs = realDuration)
                        )
                    } else {
                        trackDao.insert(
                            Track(
                                title = title, kind = kind, source = SourceType.DOWNLOADED,
                                uri = file.absolutePath, remoteUrl = url,
                                durationMs = realDuration, loop = kind == TrackKind.MUSIC,
                            )
                        )
                    }
                    _events.tryEmit("Scaricato: $title")
                }
                _tasks.update { list -> list.filterNot { it.id == id } }
            } catch (e: Exception) {
                val msg = (e.message ?: e.javaClass.simpleName).lines().lastOrNull { it.isNotBlank() } ?: "Errore"
                updateTask(id) { it.copy(error = msg.take(300)) }
            }
        }
    }

    fun dismiss(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    private fun updateTask(id: String, f: (Task) -> Task) {
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    companion object {
        fun deleteDownloadedFile(track: Track) {
            if (track.source != SourceType.DOWNLOADED) return
            val file = File(track.uri ?: return)
            val parent = file.parentFile
            file.delete()
            // ogni download ha la sua cartella: la rimuoviamo se ora è vuota
            if (parent != null && parent.list()?.isEmpty() == true) parent.delete()
        }
    }
}
