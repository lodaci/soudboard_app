package it.lodaci.soundboard.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Anteprima dei suoni in Esplora: un solo player, separato dal mixer della sessione. */
@OptIn(UnstableApi::class)
class PreviewPlayer(private val context: Context) {
    data class State(val key: String?, val loading: Boolean, val error: String? = null)

    private val _state = MutableStateFlow(State(null, false))
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    /** Avvia l'anteprima; se [key] è già in riproduzione la ferma (comportamento "toggle"). */
    fun toggle(key: String, url: String, headers: Map<String, String>) {
        if (_state.value.key == key) {
            stop()
            return
        }
        stop()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("User-Agent" to "DnDSoundboard/1.0 (Android)") + headers)
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false,
            )
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> _state.value = State(key, false)
                    Player.STATE_ENDED -> stop()
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stop()
                _state.value = State(null, false, "Anteprima non disponibile (${error.errorCodeName})")
            }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
        player = p
        _state.value = State(key, true)
    }

    fun stop() {
        player?.release()
        player = null
        _state.value = State(null, false)
    }
}
