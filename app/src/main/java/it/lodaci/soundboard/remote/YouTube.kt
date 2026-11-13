package it.lodaci.soundboard.remote

import java.net.URI

/** Riconoscimento dei link YouTube (puro Kotlin, testabile senza Android). */
object YouTube {
    private val HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtu.be", "www.youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com",
    )

    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()

    fun isYouTube(url: String): Boolean = parse(url)?.host?.lowercase() in HOSTS

    private fun queryParams(uri: URI): Map<String, String> =
        uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    /** Link che indica solo una playlist (senza un video specifico): vanno importati tutti i video. */
    fun isPlaylistOnly(url: String): Boolean {
        val uri = parse(url) ?: return false
        if (uri.host?.lowercase() !in HOSTS) return false
        val q = queryParams(uri)
        return q.containsKey("list") && !q.containsKey("v") && uri.path.orEmpty().trimEnd('/') == "/playlist"
    }

    /** Server che servono i file audio/video di YouTube. */
    fun isGoogleVideo(url: String): Boolean =
        parse(url)?.host?.lowercase()?.endsWith(".googlevideo.com") == true
}

/** Scelta dello stream audio migliore (logica separata per poterla testare). */
object AudioPicker {
    data class Candidate(
        val url: String,
        val isM4a: Boolean,
        val bitrate: Int,
        val isOriginalTrack: Boolean,
        val progressive: Boolean,
    )

    /** Preferisce: traccia audio originale (non doppiata), m4a/AAC (compatibile ovunque), bitrate più alto. */
    fun pick(candidates: List<Candidate>): Candidate? =
        candidates.filter { it.progressive && it.url.isNotBlank() }
            .sortedWith(
                compareByDescending<Candidate> { it.isOriginalTrack }
                    .thenByDescending { it.isM4a }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()
}
