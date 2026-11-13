package it.lodaci.soundboard.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.lodaci.soundboard.SoundboardApp
import it.lodaci.soundboard.data.TrackKind
import it.lodaci.soundboard.playback.PreviewPlayer
import it.lodaci.soundboard.remote.LinkTasks
import it.lodaci.soundboard.remote.catalog.ArchiveSource
import it.lodaci.soundboard.remote.catalog.SearchQuery
import it.lodaci.soundboard.remote.catalog.SoundResult
import it.lodaci.soundboard.remote.catalog.SourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SoundboardApp).container
    val settings = c.settings
    val preview: StateFlow<PreviewPlayer.State> = c.preview.state

    data class UiState(
        val source: SourceId = SourceId.OPENVERSE,
        val query: String = "",
        val kind: TrackKind? = TrackKind.SFX,
        val collection: String? = null,
        val collections: List<String> = emptyList(),
        val results: List<SoundResult> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val total: Int = 0,
        val loading: Boolean = false,
        val searched: Boolean = false,
        val error: String? = null,
        val notConfigured: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        refreshConfig()
    }

    fun refreshConfig() {
        val src = c.catalog.source(_ui.value.source)
        _ui.update { it.copy(notConfigured = src.notConfiguredReason()) }
        if (src is ArchiveSource && src.notConfiguredReason() == null) loadCollections(src)
    }

    fun selectSource(id: SourceId) {
        if (id == _ui.value.source) return
        c.preview.stop()
        job?.cancel()
        _ui.update { UiState(source = id, query = it.query, kind = it.kind) }
        refreshConfig()
        if (_ui.value.query.isNotBlank() && _ui.value.notConfigured == null) search()
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    fun setKind(kind: TrackKind?) {
        _ui.update { it.copy(kind = kind) }
        if (_ui.value.searched) search()
    }

    fun setCollection(name: String?) {
        _ui.update { it.copy(collection = name) }
        search()
    }

    fun search() = load(page = 1)

    fun loadMore() {
        if (!_ui.value.loading && _ui.value.hasMore) load(_ui.value.page + 1)
    }

    private fun load(page: Int) {
        val s = _ui.value
        val source = c.catalog.source(s.source)
        source.notConfiguredReason()?.let { reason ->
            _ui.update { it.copy(notConfigured = reason) }
            return
        }
        // l'archivio permette di sfogliare anche senza testo
        if (s.query.isBlank() && s.source != SourceId.ARCHIVE) return
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null, results = if (page == 1) emptyList() else it.results) }
        job = viewModelScope.launch {
            try {
                val res = source.search(SearchQuery(s.query.trim(), page, s.kind, s.collection))
                _ui.update {
                    it.copy(
                        results = if (page == 1) res.results else it.results + res.results,
                        page = page, hasMore = res.hasMore, total = res.total,
                        loading = false, searched = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, searched = true, error = LinkTasks.friendlyError(e).let { (m, d) -> "$m\n$d" }) }
            }
        }
    }

    private fun loadCollections(src: ArchiveSource) {
        viewModelScope.launch {
            runCatching { src.collections() }.onSuccess { list -> _ui.update { it.copy(collections = list) } }
        }
    }

    fun togglePreview(r: SoundResult) {
        c.preview.toggle("${r.source}:${r.id}", r.previewUrl, c.catalog.source(r.source).headers())
    }

    fun stopPreview() = c.preview.stop()

    fun add(r: SoundResult, kind: TrackKind) {
        c.linkTasks.addFromCatalog(r, kind, c.catalog.source(r.source).headers())
    }

    // ---- impostazioni ----

    fun saveSettings(freesoundKey: String, archiveUrl: String, archiveToken: String) {
        settings.freesoundKey = freesoundKey
        settings.archiveUrl = archiveUrl
        settings.archiveToken = archiveToken
        refreshConfig()
    }

    /** Prova la connessione all'archivio con i valori inseriti (senza salvarli se falliscono). */
    fun testArchive(url: String, token: String, onResult: (String) -> Unit) {
        val oldUrl = settings.archiveUrl
        val oldToken = settings.archiveToken
        settings.archiveUrl = url
        settings.archiveToken = token
        viewModelScope.launch {
            val msg = try {
                c.catalog.archive.notConfiguredReason()?.let { throw IllegalArgumentException(it) }
                val h = c.catalog.archive.health()
                "Connesso: ${h.sounds} suoni nell'archivio" + if (!h.ffmpeg) " (ffmpeg non installato: i WAV arriveranno non compressi)" else ""
            } catch (e: Exception) {
                settings.archiveUrl = oldUrl
                settings.archiveToken = oldToken
                "Connessione fallita: ${e.message ?: e.javaClass.simpleName}"
            }
            onResult(msg)
        }
    }

    override fun onCleared() {
        c.preview.stop()
    }
}
