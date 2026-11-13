package it.lodaci.soundboard.remote

import android.content.Context
import android.net.Uri
import it.lodaci.soundboard.data.AudioMeta
import it.lodaci.soundboard.data.SourceType
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.data.TrackDao
import it.lodaci.soundboard.data.TrackKind
import kotlinx.coroutines.CancellationException
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

/**
 * Operazioni in corso su link (lettura info / download), mostrate nella Libreria.
 *
 * Smistamento: YouTube → NewPipeExtractor (con yt-dlp come riserva), altri siti → yt-dlp,
 * link diretti a file audio → nessun estrattore.
 */
class LinkTasks(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ytdlp: YtDlpManager,
    private val youtube: YouTubeExtractor,
    private val http: HttpDownloader,
    private val trackDao: TrackDao,
) {
    data class Task(
        val id: String,
        val url: String,
        val title: String,
        val download: Boolean,
        val progress: Float = -1f,
        val status: String? = null,
        val error: String? = null,
        val errorDetail: String? = null,
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
                if (existing == null && YouTube.isPlaylistOnly(url)) {
                    importPlaylist(id, url, kind, download)
                } else {
                    val title = importSingle(
                        url, kind, download, existing,
                        onTitle = { t -> updateTask(id) { it.copy(title = t) } },
                        onProgress = { p -> updateTask(id) { it.copy(progress = p) } },
                    )
                    _events.tryEmit(if (download) "Scaricato: $title" else "Aggiunto in streaming: $title")
                }
                _tasks.update { list -> list.filterNot { it.id == id } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val (msg, detail) = friendlyError(e)
                updateTask(id) { it.copy(error = msg, errorDetail = detail) }
            }
        }
    }

    private suspend fun importPlaylist(id: String, url: String, kind: TrackKind, download: Boolean) {
        updateTask(id) { it.copy(status = "Lettura della playlist…") }
        val playlist = youtube.playlist(url)
        if (playlist.entries.isEmpty()) error("La playlist è vuota o privata")
        updateTask(id) { it.copy(title = "Playlist: ${playlist.title}") }
        var ok = 0
        var skipped = 0
        playlist.entries.forEachIndexed { i, entry ->
            updateTask(id) {
                it.copy(
                    status = "${i + 1} / ${playlist.entries.size}: ${entry.title}",
                    progress = if (download) -1f else i * 100f / playlist.entries.size,
                )
            }
            try {
                importSingle(
                    entry.url, kind, download, null,
                    onTitle = {},
                    onProgress = { p ->
                        updateTask(id) { it.copy(progress = (i + p / 100f) * 100f / playlist.entries.size) }
                    },
                )
                ok++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                skipped++ // video privato/non disponibile: si va avanti
            }
        }
        _events.tryEmit(
            "Playlist \"${playlist.title}\": $ok tracce aggiunte" + if (skipped > 0) ", $skipped saltate" else ""
        )
        if (ok == 0) error("Nessun video della playlist è stato importato")
    }

    /** Importa un singolo link e restituisce il titolo. */
    private suspend fun importSingle(
        url: String,
        kind: TrackKind,
        download: Boolean,
        existing: Track?,
        onTitle: (String) -> Unit,
        onProgress: (Float) -> Unit,
    ): String {
        val direct = YtDlpManager.isDirectAudio(url)
        val isYouTube = YouTube.isYouTube(url)

        // --- informazioni ---
        var ytVideo: YouTubeExtractor.Video? = null
        var newPipeError: Exception? = null
        val title: String
        val durationMs: Long
        when {
            direct -> {
                title = existing?.title ?: AudioMeta.titleFromFileName(Uri.parse(url).lastPathSegment ?: url)
                durationMs = existing?.durationMs ?: 0
            }
            isYouTube -> {
                ytVideo = try {
                    youtube.video(url)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    newPipeError = e
                    null
                }
                if (ytVideo != null) {
                    title = existing?.title ?: ytVideo.title
                    durationMs = ytVideo.durationMs
                } else {
                    // riserva: yt-dlp
                    val info = try {
                        ytdlp.fetchInfo(url)
                    } catch (e: Exception) {
                        throw newPipeError ?: e
                    }
                    title = existing?.title ?: info.title
                    durationMs = info.durationMs
                }
            }
            else -> {
                val info = if (existing != null) null else ytdlp.fetchInfo(url)
                title = existing?.title ?: info!!.title
                durationMs = existing?.durationMs ?: info!!.durationMs
            }
        }
        onTitle(title)

        // --- solo streaming ---
        if (!download) {
            trackDao.insert(
                Track(
                    title = title, kind = kind, source = SourceType.STREAM,
                    uri = if (direct) url else null, remoteUrl = url,
                    durationMs = durationMs, loop = kind == TrackKind.MUSIC,
                )
            )
            return title
        }

        // --- download ---
        val video = ytVideo
        val dir = File(context.filesDir, "audio/${UUID.randomUUID()}").apply { mkdirs() }
        val file: File = try {
            when {
                direct -> {
                    val ext = url.substringAfterLast('.', "mp3").substringBefore('?').take(5)
                    File(dir, "${safeName(title)}.$ext").also { http.download(url, it, onProgress = onProgress) }
                }
                video != null -> {
                    File(dir, "${safeName(title)}.${video.extension}").also {
                        http.download(video.audioUrl, it, video.contentLength, onProgress = onProgress)
                    }
                }
                else -> {
                    dir.delete()
                    try {
                        ytdlp.download(url, onProgress)
                    } catch (e: Exception) {
                        throw newPipeError ?: e
                    }
                }
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }

        val realDuration = AudioMeta.durationMs(context, Uri.fromFile(file)).takeIf { it > 0 } ?: durationMs
        if (existing != null) {
            trackDao.update(existing.copy(source = SourceType.DOWNLOADED, uri = file.absolutePath, durationMs = realDuration))
        } else {
            trackDao.insert(
                Track(
                    title = title, kind = kind, source = SourceType.DOWNLOADED,
                    uri = file.absolutePath, remoteUrl = url,
                    durationMs = realDuration, loop = kind == TrackKind.MUSIC,
                )
            )
        }
        return title
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

        private fun safeName(title: String): String =
            title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().take(60).ifEmpty { "audio" }

        /** Messaggio comprensibile + dettaglio tecnico. */
        fun friendlyError(e: Throwable): Pair<String, String> {
            val detail = (e.message ?: e.javaClass.simpleName).lines().lastOrNull { it.isNotBlank() }?.take(300)
                ?: e.javaClass.simpleName
            val all = generateSequence(e) { it.cause }.joinToString(" ") { "${it.javaClass.simpleName} ${it.message}" }.lowercase()
            val msg = when {
                "recaptcha" in all || "sign in" in all || "not a bot" in all || "http 429" in all ->
                    "YouTube ha bloccato la richiesta (controllo anti-bot). Riprova tra un po' o cambia rete (Wi‑Fi/dati)."
                "age" in all && ("restrict" in all || "inappropriate" in all || "confirm your age" in all) ->
                    "Video con limite d'età: non può essere riprodotto senza account."
                "private" in all -> "Il video o la playlist è privato."
                "unavailable" in all || "not available" in all || "contentnotavailable" in all ->
                    "Contenuto non disponibile (rimosso o bloccato nel tuo paese)."
                "unknownhost" in all || "unable to resolve host" in all || ("connect" in all && "timeout" in all) ->
                    "Nessuna connessione a internet."
                "paid" in all || "premium" in all -> "Contenuto a pagamento / Premium."
                else -> "Impossibile leggere il link."
            }
            return msg to detail
        }
    }
}
