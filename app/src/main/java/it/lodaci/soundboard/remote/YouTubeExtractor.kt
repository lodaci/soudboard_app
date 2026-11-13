package it.lodaci.soundboard.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Estrazione YouTube tramite NewPipeExtractor (Java puro, niente Python né JavaScript esterno). */
class YouTubeExtractor(client: OkHttpClient) {

    data class Video(
        val title: String,
        val durationMs: Long,
        val audioUrl: String,
        val extension: String,
        val contentLength: Long,
    )

    data class PlaylistEntry(val title: String, val url: String)
    data class Playlist(val title: String, val entries: List<PlaylistEntry>)

    init {
        synchronized(YouTubeExtractor::class.java) {
            if (!initialized) {
                NewPipe.init(NewPipeDownloader(client))
                initialized = true
            }
        }
    }

    suspend fun video(url: String): Video = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val streams = info.audioStreams.orEmpty()
        val candidates = streams.map { s ->
            AudioPicker.Candidate(
                url = if (s.isUrl) s.content else "",
                isM4a = s.format == MediaFormat.M4A,
                bitrate = s.averageBitrate,
                isOriginalTrack = s.audioTrackType == null || s.audioTrackType == AudioTrackType.ORIGINAL,
                progressive = s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP,
            )
        }
        val chosen = AudioPicker.pick(candidates)
            ?: throw IllegalStateException("Nessuno stream audio disponibile per questo video")
        val stream = streams[candidates.indexOf(chosen)]
        Video(
            title = info.name?.takeIf { it.isNotBlank() } ?: url,
            durationMs = info.duration * 1000L,
            audioUrl = chosen.url,
            extension = stream.format?.suffix ?: "m4a",
            contentLength = runCatching { stream.itagItem?.contentLength ?: -1L }.getOrDefault(-1L),
        )
    }

    suspend fun playlist(url: String, max: Int = 500): Playlist = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val info = PlaylistInfo.getInfo(service, url)
        val entries = info.relatedItems.map { PlaylistEntry(it.name, it.url) }.toMutableList()
        var page: Page? = info.nextPage
        while (Page.isValid(page) && entries.size < max) {
            val more = PlaylistInfo.getMoreItems(service, url, page)
            entries += more.items.map { PlaylistEntry(it.name, it.url) }
            page = more.nextPage
        }
        Playlist(info.name ?: "Playlist", entries.take(max))
    }

    private companion object {
        @Volatile
        var initialized = false
    }
}
