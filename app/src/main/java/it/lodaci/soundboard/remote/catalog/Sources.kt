package it.lodaci.soundboard.remote.catalog

import it.lodaci.soundboard.data.Settings
import it.lodaci.soundboard.data.TrackKind
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject

private fun JSONObject.str(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).ifBlank { null }

/** Openverse (WordPress): audio Creative Commons da Freesound, Wikimedia, Jamendo… Nessuna chiave. */
class OpenverseSource(private val client: OkHttpClient) : SoundSource {
    override val id = SourceId.OPENVERSE
    override fun notConfiguredReason(): String? = null

    override suspend fun search(query: SearchQuery): SearchPage {
        val url = "https://api.openverse.org/v1/audio/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.text)
            .addQueryParameter("page", query.page.toString())
            .addQueryParameter("page_size", PAGE_SIZE.toString())
            .addQueryParameter("mature", "false")
            .apply {
                when (query.kind) {
                    TrackKind.SFX -> addQueryParameter("category", "sound_effect")
                    TrackKind.MUSIC -> addQueryParameter("category", "music")
                    null -> {}
                }
            }
            .build().toString()
        return parse(client.getText(url), query.page)
    }

    companion object {
        const val PAGE_SIZE = 20

        fun parse(json: String, page: Int): SearchPage {
            val o = JSONObject(json)
            val arr = o.optJSONArray("results")
            val results = buildList {
                for (i in 0 until (arr?.length() ?: 0)) {
                    val r = arr!!.getJSONObject(i)
                    val file = r.str("url") ?: continue
                    val category = r.str("category")
                    add(
                        SoundResult(
                            source = SourceId.OPENVERSE,
                            id = r.optString("id"),
                            title = r.str("title") ?: "Senza titolo",
                            author = r.str("creator"),
                            license = licenseLabel(r.str("license"), r.str("license_version")),
                            licenseUrl = r.str("license_url"),
                            landingUrl = r.str("foreign_landing_url"),
                            durationMs = r.optLong("duration", 0),
                            sizeBytes = r.optLong("filesize", 0),
                            format = r.str("filetype"),
                            previewUrl = file,
                            downloadUrl = file,
                            origin = r.str("source") ?: r.str("provider"),
                            suggestedKind = if (category == "music") TrackKind.MUSIC else TrackKind.SFX,
                        )
                    )
                }
            }
            val pageCount = o.optInt("page_count", page)
            return SearchPage(results, page < pageCount, o.optInt("result_count", results.size))
        }
    }
}

/** Freesound diretto: richiede una chiave API gratuita (freesound.org/apiv2/apply). Scarica l'MP3 HQ. */
class FreesoundSource(private val client: OkHttpClient, private val settings: Settings) : SoundSource {
    override val id = SourceId.FREESOUND

    override fun notConfiguredReason(): String? =
        if (settings.freesoundKey.isBlank()) "Serve una chiave API gratuita di Freesound" else null

    override suspend fun search(query: SearchQuery): SearchPage {
        val url = "https://freesound.org/apiv2/search/text/".toHttpUrl().newBuilder()
            .addQueryParameter("query", query.text)
            .addQueryParameter("page", query.page.toString())
            .addQueryParameter("page_size", PAGE_SIZE.toString())
            .addQueryParameter("fields", "id,name,username,license,duration,previews,filesize,type,url")
            .apply {
                when (query.kind) {
                    TrackKind.SFX -> addQueryParameter("filter", "duration:[0 TO 30]")
                    TrackKind.MUSIC -> addQueryParameter("filter", "duration:[30 TO *]")
                    null -> {}
                }
            }
            .addQueryParameter("token", settings.freesoundKey.trim())
            .build().toString()
        return parse(client.getText(url))
    }

    companion object {
        const val PAGE_SIZE = 30

        fun parse(json: String): SearchPage {
            val o = JSONObject(json)
            val arr = o.optJSONArray("results")
            val results = buildList {
                for (i in 0 until (arr?.length() ?: 0)) {
                    val r = arr!!.getJSONObject(i)
                    val previews = r.optJSONObject("previews")
                    val mp3 = previews?.str("preview-hq-mp3") ?: previews?.str("preview-lq-mp3") ?: continue
                    val durationMs = (r.optDouble("duration", 0.0) * 1000).toLong()
                    val name = r.str("name") ?: "Senza titolo"
                    add(
                        SoundResult(
                            source = SourceId.FREESOUND,
                            id = r.optString("id"),
                            title = name.substringBeforeLast('.').ifBlank { name },
                            author = r.str("username"),
                            license = licenseLabel(r.str("license")),
                            licenseUrl = r.str("license"),
                            landingUrl = r.str("url"),
                            durationMs = durationMs,
                            sizeBytes = 0, // l'MP3 di anteprima è molto più piccolo dell'originale
                            format = "mp3",
                            previewUrl = mp3,
                            downloadUrl = mp3,
                            origin = r.str("type")?.let { "originale $it" },
                            suggestedKind = if (durationMs > 30_000) TrackKind.MUSIC else TrackKind.SFX,
                        )
                    )
                }
            }
            return SearchPage(results, o.str("next") != null, o.optInt("count", results.size))
        }
    }
}

/** Archivio privato SoundVault sul NUC. */
class ArchiveSource(private val client: OkHttpClient, private val settings: Settings) : SoundSource {
    override val id = SourceId.ARCHIVE

    private val base get() = settings.archiveUrl.trim().trimEnd('/')

    override fun notConfiguredReason(): String? = when {
        settings.archiveUrl.isBlank() || settings.archiveToken.isBlank() -> "Configura indirizzo e token del tuo archivio"
        runCatching { base.toHttpUrl() }.isFailure -> "Indirizzo dell'archivio non valido"
        else -> null
    }

    override fun headers(): Map<String, String> = mapOf("Authorization" to "Bearer ${settings.archiveToken.trim()}")

    override suspend fun search(query: SearchQuery): SearchPage {
        val url = "$base/api/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.text)
            .addQueryParameter("page", query.page.toString())
            .addQueryParameter("page_size", PAGE_SIZE.toString())
            .apply { query.collection?.let { addQueryParameter("collection", it) } }
            .build().toString()
        return parse(client.getText(url, headers()), base)
    }

    data class Health(val sounds: Int, val ffmpeg: Boolean, val version: String)

    suspend fun health(): Health {
        val o = JSONObject(client.getText("$base/api/v1/health", headers()))
        return Health(o.optInt("sounds"), o.optBoolean("ffmpeg"), o.optString("version"))
    }

    suspend fun collections(): List<String> {
        val o = JSONObject(client.getText("$base/api/v1/categories", headers()))
        val arr = o.optJSONArray("categories") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("collection") }.distinct()
    }

    companion object {
        const val PAGE_SIZE = 30

        fun parse(json: String, base: String): SearchPage {
            val o = JSONObject(json)
            val arr = o.optJSONArray("results")
            val results = buildList {
                for (i in 0 until (arr?.length() ?: 0)) {
                    val r = arr!!.getJSONObject(i)
                    val id = r.optLong("id")
                    val file = "$base/api/v1/files/$id?compact=true"
                    val collection = r.str("collection")
                    val category = r.str("category")
                    val durationMs = r.optLong("duration_ms", 0)
                    add(
                        SoundResult(
                            source = SourceId.ARCHIVE,
                            id = id.toString(),
                            title = r.str("title") ?: "Senza titolo",
                            author = null,
                            license = null,
                            licenseUrl = null,
                            landingUrl = null,
                            durationMs = durationMs,
                            sizeBytes = r.optLong("size", 0),
                            format = r.str("format"),
                            previewUrl = file,
                            downloadUrl = file,
                            origin = listOfNotNull(collection, category).joinToString(" › ").ifBlank { null },
                            suggestedKind = if (durationMs > 45_000) TrackKind.MUSIC else TrackKind.SFX,
                        )
                    )
                }
            }
            val page = o.optInt("page", 1)
            val size = o.optInt("page_size", PAGE_SIZE)
            val total = o.optInt("total", results.size)
            return SearchPage(results, page * size < total, total)
        }
    }
}

class CatalogRepository(client: OkHttpClient, settings: Settings) {
    val openverse = OpenverseSource(client)
    val freesound = FreesoundSource(client, settings)
    val archive = ArchiveSource(client, settings)

    fun source(id: SourceId): SoundSource = when (id) {
        SourceId.OPENVERSE -> openverse
        SourceId.FREESOUND -> freesound
        SourceId.ARCHIVE -> archive
    }
}
