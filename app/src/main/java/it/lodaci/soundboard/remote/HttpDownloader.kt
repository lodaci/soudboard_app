package it.lodaci.soundboard.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Come vanno fatte le richieste ai server video di YouTube (stesse regole dell'app NewPipe):
 * gli URL del client WEB vogliono POST + intervallo nell'URL, quelli visionOS il suo user agent.
 */
object YouTubeHttp {
    val POST_BODY = byteArrayOf(0x78, 0)
    private val requestNumber = AtomicLong(0)

    data class Shaped(
        val url: String,
        val post: Boolean,
        val headers: Map<String, String>,
        /** true se l'intervallo di byte è già nell'URL (niente header Range) */
        val rangeInUrl: Boolean,
    )

    fun shape(url: String, start: Long, endInclusive: Long?): Shaped {
        if (!YouTube.isGoogleVideo(url)) {
            return Shaped(url, false, mapOf("User-Agent" to HttpDownloader.DESKTOP_UA), false)
        }
        val web = runCatching { YoutubeParsingHelper.isWebStreamingUrl(url) }.getOrDefault(false)
        if (web) {
            val ranged = url + "&range=$start-${endInclusive ?: ""}" + "&rn=${requestNumber.getAndIncrement()}"
            return Shaped(
                ranged, true,
                mapOf(
                    "User-Agent" to HttpDownloader.DESKTOP_UA,
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/",
                ),
                true,
            )
        }
        val visionOs = runCatching { YoutubeParsingHelper.isVisionOsStreamingUrl(url) }.getOrDefault(false)
        val ua = if (visionOs) YoutubeParsingHelper.getVisionOsUserAgent(null) else HttpDownloader.DESKTOP_UA
        return Shaped(url, false, mapOf("User-Agent" to ua), false)
    }
}

/** Download HTTP a blocchi (evita il rallentamento che YouTube applica ai download "in un colpo solo"). */
class HttpDownloader(private val client: OkHttpClient) {

    /**
     * @param expectedLength dimensione nota in anticipo (se > 0) per la percentuale
     * @param maxBytes per i test: ferma il download dopo questi byte
     */
    suspend fun download(
        url: String,
        dest: File,
        expectedLength: Long = -1,
        maxBytes: Long = Long.MAX_VALUE,
        extraHeaders: Map<String, String> = emptyMap(),
        onProgress: (Float) -> Unit = {},
    ): Long = withContext(Dispatchers.IO) {
        var pos = 0L
        var total = expectedLength
        val buffer = ByteArray(64 * 1024)
        FileOutputStream(dest).use { out ->
            while (pos < maxBytes) {
                ensureActive()
                if (total in 1..pos) break
                val end = pos + CHUNK - 1
                val shaped = YouTubeHttp.shape(url, pos, end)
                val builder = Request.Builder().url(shaped.url)
                shaped.headers.forEach { (k, v) -> builder.header(k, v) }
                extraHeaders.forEach { (k, v) -> builder.header(k, v) }
                if (!shaped.rangeInUrl) builder.header("Range", "bytes=$pos-$end")
                if (shaped.post) builder.post(YouTubeHttp.POST_BODY.toRequestBody()) else builder.get()

                var received = 0L
                var attempt = 0
                while (true) {
                    try {
                        client.newCall(builder.build()).execute().use { resp ->
                            if (resp.code == 416 && pos > 0) return@use // oltre la fine: finito
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            if (total <= 0) {
                                resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                            }
                            val wholeFile = resp.code == 200 && !shaped.rangeInUrl
                            val input = resp.body?.byteStream() ?: throw IOException("Risposta vuota")
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                received += n
                                if (total > 0) onProgress(((pos + received) * 100f / total).coerceAtMost(100f))
                            }
                            if (wholeFile) total = pos + received // il server ha ignorato il Range: file completo
                        }
                        break
                    } catch (e: IOException) {
                        if (++attempt >= 3 || received > 0) throw e
                    }
                }
                pos += received
                if (received < CHUNK) break // ultimo blocco
            }
        }
        if (pos == 0L) {
            dest.delete()
            throw IOException("Download vuoto")
        }
        pos
    }

    companion object {
        const val CHUNK = 1_000_000L
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
