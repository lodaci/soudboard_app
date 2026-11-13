package it.lodaci.soundboard.playback

import android.net.Uri
import it.lodaci.soundboard.data.SourceType
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.remote.YtDlpManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Trasforma una traccia nell'URI effettivamente riproducibile da ExoPlayer. */
class SourceResolver(private val ytdlp: YtDlpManager) {
    private data class Cached(val url: String, val at: Long)

    // gli URL di YouTube scadono dopo qualche ora: li teniamo per poco tempo
    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun resolve(track: Track): Uri = when (track.source) {
        SourceType.LOCAL -> Uri.parse(track.uri ?: error("URI mancante"))
        SourceType.DOWNLOADED -> {
            val file = File(track.uri ?: error("File mancante"))
            if (!file.exists()) error("File scaricato non trovato")
            Uri.fromFile(file)
        }
        SourceType.STREAM -> {
            val direct = track.uri
            if (direct != null) {
                Uri.parse(direct)
            } else {
                val link = track.remoteUrl ?: error("Link mancante")
                val now = System.currentTimeMillis()
                val hit = cache[link]?.takeIf { now - it.at < CACHE_MS }
                val url = hit?.url ?: ytdlp.streamUrl(link).also { cache[link] = Cached(it, now) }
                Uri.parse(url)
            }
        }
    }

    fun invalidate(track: Track) {
        track.remoteUrl?.let { cache.remove(it) }
    }

    private companion object {
        const val CACHE_MS = 60 * 60 * 1000L
    }
}
