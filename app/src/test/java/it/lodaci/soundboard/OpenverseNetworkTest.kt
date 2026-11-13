package it.lodaci.soundboard

import it.lodaci.soundboard.data.TrackKind
import it.lodaci.soundboard.remote.catalog.OpenverseSource
import it.lodaci.soundboard.remote.catalog.SearchQuery
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Prova reale contro Openverse: si esegue solo con -PnetworkTests=true. */
class OpenverseNetworkTest {
    @Before
    fun onlyWhenEnabled() {
        assumeTrue(System.getProperty("networkTests") == "true")
    }

    @Test
    fun rawResponse() {
        for (u in listOf(
            "https://api.openverse.org/v1/audio/?q=sword&page_size=2",
            "https://api.openverse.org/v1/audio/?q=sword&page_size=2&category=sound_effect",
        )) {
            val req = okhttp3.Request.Builder().url(u).header("User-Agent", "DnDSoundboard/1.0 (Android)").build()
            OkHttpClient().newCall(req).execute().use { r ->
                println("Openverse: HTTP ${r.code} $u -> " + r.body?.string()?.take(1500)?.replace("\n", " "))
            }
        }
    }

    @Test
    fun searchesSwordEffects(): Unit = runBlocking {
        val page = OpenverseSource(OkHttpClient()).search(SearchQuery("sword", kind = TrackKind.SFX))
        println("Openverse: ${page.total} risultati, primo: ${page.results.firstOrNull()?.let { "${it.title} (${it.license}) ${it.downloadUrl}" }}")
        assertTrue(page.results.isNotEmpty())
    }
}
