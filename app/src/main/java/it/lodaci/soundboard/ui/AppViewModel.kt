package it.lodaci.soundboard.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.lodaci.soundboard.SoundboardApp
import it.lodaci.soundboard.data.AudioMeta
import it.lodaci.soundboard.data.Board
import it.lodaci.soundboard.data.Pad
import it.lodaci.soundboard.data.SourceType
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.data.TrackKind
import it.lodaci.soundboard.playback.VoiceState
import it.lodaci.soundboard.remote.LinkTasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SoundboardApp).container
    private val trackDao = c.db.trackDao()
    private val boardDao = c.db.boardDao()
    private val padDao = c.db.padDao()
    val mixer = c.mixer
    val settings = c.settings

    val tracks: StateFlow<List<Track>> =
        trackDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val boards: StateFlow<List<Board>> =
        boardDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val voices: StateFlow<List<VoiceState>> = mixer.state
    val linkTasks: StateFlow<List<LinkTasks.Task>> = c.linkTasks.tasks

    private val localMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Messaggi da mostrare in una snackbar (raccolti da un solo punto della UI). */
    val messages: Flow<String> = merge(localMessages, mixer.errors, c.linkTasks.events)

    fun padsFor(boardId: Long): Flow<List<Pad>> = padDao.observeForBoard(boardId)

    // ---- Libreria ----

    fun importLocal(uris: List<Uri>, kind: TrackKind) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            var n = 0
            for (uri in uris) {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = AudioMeta.displayName(ctx, uri) ?: uri.lastPathSegment ?: "Traccia"
                trackDao.insert(
                    Track(
                        title = AudioMeta.titleFromFileName(name),
                        kind = kind,
                        source = SourceType.LOCAL,
                        uri = uri.toString(),
                        durationMs = AudioMeta.durationMs(ctx, uri),
                        loop = kind == TrackKind.MUSIC,
                    )
                )
                n++
            }
            localMessages.tryEmit(if (n == 1) "1 file importato" else "$n file importati")
        }
    }

    fun addLink(url: String, kind: TrackKind, download: Boolean) = c.linkTasks.add(url.trim(), kind, download)

    fun downloadExisting(track: Track) {
        val url = track.remoteUrl ?: return
        c.linkTasks.add(url, track.kind, download = true, existing = track)
    }

    fun dismissTask(id: String) = c.linkTasks.dismiss(id)

    fun updateTrack(track: Track) {
        viewModelScope.launch { trackDao.update(track) }
    }

    fun deleteTracks(list: List<Track>) {
        list.forEach { mixer.stop(it.id, 0) }
        viewModelScope.launch(Dispatchers.IO) {
            list.forEach { t ->
                trackDao.delete(t)
                LinkTasks.deleteDownloadedFile(t)
                if (t.source == SourceType.LOCAL) runCatching {
                    getApplication<Application>().contentResolver.releasePersistableUriPermission(
                        Uri.parse(t.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        }
    }

    // ---- Riproduzione ----

    fun toggle(track: Track) = mixer.trigger(track, fade = track.kind == TrackKind.MUSIC)

    fun startTogether(list: List<Track>) = list.forEach { mixer.play(it) }

    fun triggerPad(pad: Pad) {
        val track = tracks.value.firstOrNull { it.id == pad.trackId } ?: return
        mixer.trigger(track, pad.fade)
    }

    // ---- Soundboard ----

    fun savePad(pad: Pad) {
        viewModelScope.launch { padDao.upsert(pad) }
    }

    fun clearPad(boardId: Long, position: Int) {
        viewModelScope.launch { padDao.clear(boardId, position) }
    }

    fun addBoard(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = boardDao.insert(Board(name = name, sortOrder = boards.value.size))
            onCreated(id)
        }
    }

    fun updateBoard(board: Board) {
        viewModelScope.launch { boardDao.update(board) }
    }

    fun deleteBoard(board: Board) {
        viewModelScope.launch { boardDao.delete(board) }
    }

    // ---- yt-dlp ----

    suspend fun ytdlpVersion(): String? = c.ytdlp.versionOrNull()

    fun updateYtDlp(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = runCatching { c.ytdlp.update() }.getOrElse { "Aggiornamento fallito: ${it.message}" }
            withContext(Dispatchers.Main) { onDone(msg) }
        }
    }
}
