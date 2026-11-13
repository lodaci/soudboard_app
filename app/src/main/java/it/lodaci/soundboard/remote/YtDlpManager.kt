package it.lodaci.soundboard.remote

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Wrapper attorno a yt-dlp (youtubedl-android): estrae lo stream audio di un link oppure lo scarica. */
class YtDlpManager(private val context: Context, scope: CoroutineScope) {

    data class LinkInfo(val title: String, val durationMs: Long, val streamUrl: String?)

    private val ready = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        YoutubeDL.getInstance().init(context)
    }

    /** Estrae Python/yt-dlp in background all'avvio, così il primo link è più veloce. */
    fun warmUp() {
        ready.start()
    }

    /** Aggiorna yt-dlp al massimo una volta al giorno (i siti cambiano spesso). */
    suspend fun autoUpdate(lastUpdate: Long, onUpdated: (Long) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < 24 * 60 * 60 * 1000L) return
        runCatching { update() }.onSuccess { onUpdated(now) }
    }

    private fun YoutubeDLRequest.commonOptions(url: String) {
        addOption("-f", AUDIO_FORMAT)
        addOption("--no-playlist")
        if (YouTube.isYouTube(url)) addOption("--extractor-args", "youtube:player_client=default,mweb")
    }

    private suspend fun awaitReady() {
        try {
            ready.await()
        } catch (e: Exception) {
            throw IllegalStateException("Impossibile inizializzare yt-dlp: ${e.message}", e)
        }
    }

    suspend fun fetchInfo(url: String): LinkInfo = withContext(Dispatchers.IO) {
        awaitReady()
        val request = YoutubeDLRequest(url).apply { commonOptions(url) }
        val info = YoutubeDL.getInstance().getInfo(request)
        LinkInfo(
            title = info.title?.takeIf { it.isNotBlank() } ?: url,
            durationMs = info.duration.toLong() * 1000L,
            streamUrl = info.url,
        )
    }

    suspend fun streamUrl(url: String): String =
        fetchInfo(url).streamUrl ?: error("Nessuno stream audio trovato per questo link")

    /** Scarica l'audio in una cartella dedicata dentro la memoria dell'app e restituisce il file. */
    suspend fun download(url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        awaitReady()
        val dir = File(context.filesDir, "audio/${UUID.randomUUID()}").apply { mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            commonOptions(url)
            addOption("--no-mtime")
            addOption("--restrict-filenames")
            addOption("-o", dir.absolutePath + "/%(title).60B.%(ext)s")
        }
        try {
            YoutubeDL.getInstance().execute(request, null) { progress, _, _ -> onProgress(progress) }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }
            ?: run {
                dir.deleteRecursively()
                error("Download completato ma file non trovato")
            }
    }

    suspend fun update(): String = withContext(Dispatchers.IO) {
        awaitReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
        when (status) {
            YoutubeDL.UpdateStatus.DONE -> "yt-dlp aggiornato (${version() ?: "?"})"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp è già aggiornato (${version() ?: "?"})"
            else -> "Aggiornamento: $status"
        }
    }

    suspend fun versionOrNull(): String? = withContext(Dispatchers.IO) {
        runCatching { awaitReady(); version() }.getOrNull()
    }

    private fun version(): String? = YoutubeDL.getInstance().version(context)

    companion object {
        /** Preferisce m4a (riproducibile ovunque, nessuna conversione con ffmpeg necessaria). */
        const val AUDIO_FORMAT = "bestaudio[ext=m4a]/bestaudio/best"

        private val DIRECT_AUDIO = Regex(""".*\.(mp3|ogg|oga|opus|m4a|aac|wav|flac|webm)$""", RegexOption.IGNORE_CASE)

        /** Link che puntano già a un file audio: niente yt-dlp, li riproduciamo direttamente. */
        fun isDirectAudio(url: String): Boolean {
            val path = runCatching { java.net.URI(url).path }.getOrNull() ?: return false
            return DIRECT_AUDIO.matches(path)
        }
    }
}
