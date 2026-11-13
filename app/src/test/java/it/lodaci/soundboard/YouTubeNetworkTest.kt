package it.lodaci.soundboard

import it.lodaci.soundboard.remote.HttpDownloader
import it.lodaci.soundboard.remote.YouTubeExtractor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Prova reale contro YouTube: si esegue solo con -PnetworkTests=true. */
class YouTubeNetworkTest {
    private val client = OkHttpClient()

    @Before
    fun onlyWhenEnabled() {
        assumeTrue(System.getProperty("networkTests") == "true")
    }

    @Test
    fun resolvesAndDownloadsAudio(): Unit = runBlocking {
        val yt = YouTubeExtractor(client)
        val video = yt.video("https://youtu.be/hAkZgArOF8Q")
        println("Titolo: ${video.title}, durata ${video.durationMs} ms, ${video.extension}, ${video.contentLength} byte")
        println("URL: ${video.audioUrl.take(120)}…")
        assertTrue(video.audioUrl.startsWith("http"))

        val file = File.createTempFile("yt", "." + video.extension)
        val bytes = HttpDownloader(client).download(video.audioUrl, file, video.contentLength, maxBytes = 2_000_000)
        println("Scaricati $bytes byte")
        assertTrue(bytes > 100_000)
        file.delete()
    }
}
