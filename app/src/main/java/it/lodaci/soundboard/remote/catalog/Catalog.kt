package it.lodaci.soundboard.remote.catalog

import it.lodaci.soundboard.data.TrackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

enum class SourceId(val label: String) {
    OPENVERSE("Openverse"),
    FREESOUND("Freesound"),
    ARCHIVE("Il mio archivio"),
}

/** Un suono trovato in un archivio online. */
data class SoundResult(
    val source: SourceId,
    val id: String,
    val title: String,
    val author: String?,
    /** etichetta breve, es. "CC0", "CC BY 4.0" */
    val license: String?,
    val licenseUrl: String?,
    /** pagina originale del suono (per i crediti) */
    val landingUrl: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val format: String?,
    /** URL da usare per l'anteprima */
    val previewUrl: String,
    /** URL del file da scaricare nella libreria */
    val downloadUrl: String,
    /** etichetta secondaria (provider Openverse / raccolta dell'archivio) */
    val origin: String? = null,
    val suggestedKind: TrackKind = TrackKind.SFX,
)

data class SearchPage(val results: List<SoundResult>, val hasMore: Boolean, val total: Int)

/** Filtro tipo: null = tutti */
data class SearchQuery(
    val text: String,
    val page: Int = 1,
    val kind: TrackKind? = null,
    val collection: String? = null,
)

interface SoundSource {
    val id: SourceId

    /** null se la sorgente è utilizzabile, altrimenti il motivo (es. chiave mancante) */
    fun notConfiguredReason(): String?

    suspend fun search(query: SearchQuery): SearchPage

    /** header da inviare per anteprima e download (es. token dell'archivio) */
    fun headers(): Map<String, String> = emptyMap()
}

class CatalogException(message: String) : IOException(message)

internal suspend fun OkHttpClient.getText(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply {
            header("User-Agent", "DnDSoundboard/1.0 (Android)")
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            when {
                resp.code == 401 || resp.code == 403 -> throw CatalogException("Accesso negato (chiave o token non validi)")
                resp.code == 429 -> throw CatalogException("Troppe richieste: riprova tra un minuto")
                !resp.isSuccessful -> throw CatalogException("Errore del server (HTTP ${resp.code})")
            }
            body
        }
    }

/** Etichetta breve per le licenze Creative Commons, partendo da codice o URL. */
fun licenseLabel(code: String?, version: String? = null): String? {
    if (code.isNullOrBlank()) return null
    val c = code.lowercase()
    val base = when {
        "zero" in c || c == "cc0" || "publicdomain" in c || c == "pdm" -> return "CC0"
        "sampling" in c -> "Sampling+"
        else -> {
            val m = Regex("""(by(-nc)?(-nd|-sa)?)""").find(c)?.value
            if (m != null) "CC " + m.uppercase() else code
        }
    }
    val ver = version ?: Regex("""/(\d\.\d)/?""").find(c)?.groupValues?.get(1)
    return if (ver != null && base.startsWith("CC ")) "$base $ver" else base
}
