package it.lodaci.soundboard.playback

import android.content.Context
import android.os.SystemClock
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
import it.lodaci.soundboard.data.Settings
import it.lodaci.soundboard.data.Track
import it.lodaci.soundboard.data.TrackKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VoiceStatus { LOADING, PLAYING, PAUSED, STOPPING }

data class VoiceState(
    val trackId: Long,
    val title: String,
    val kind: TrackKind,
    val volume: Float,
    val loop: Boolean,
    val status: VoiceStatus,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * Mixer: ogni traccia attiva ha il proprio ExoPlayer, così più suoni si sovrappongono liberamente
 * (il mixaggio lo fa Android). Il volume effettivo è  fader × volume traccia × master.
 *
 * Tutti i metodi vanno chiamati dal main thread.
 */
@OptIn(UnstableApi::class)
class MixerEngine(
    private val context: Context,
    private val resolver: SourceResolver,
    private val settings: Settings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val voices = LinkedHashMap<Long, Voice>()

    private val _state = MutableStateFlow<List<VoiceState>>(emptyList())
    val state: StateFlow<List<VoiceState>> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Chiamato quando il mixer passa da vuoto ad attivo (per avviare il servizio in primo piano). */
    var onBecameActive: (() -> Unit)? = null

    private var ticker: Job? = null

    private val mediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true),
        )
    )

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private inner class Voice(val track: Track, val player: ExoPlayer) {
        var volume = track.volume
        var gain = 0f
        var loop = track.loop
        var paused = false
        var stopping = false
        var loading = true
        var loadJob: Job? = null
        var fadeJob: Job? = null

        fun apply() {
            player.volume = (gain * volume * settings.master.value).coerceIn(0f, 1f)
        }
    }

    fun isActive(trackId: Long): Boolean = voices[trackId]?.let { !it.stopping && !it.paused } ?: false

    /**
     * Azione "tasto": le tracce in loop si avviano/fermano, i suoni one‑shot ripartono da capo.
     */
    fun trigger(track: Track, fade: Boolean) {
        val ms = if (fade) settings.fadeMs.value else 0L
        val v = voices[track.id]
        when {
            v == null -> play(track, ms)
            v.stopping || v.paused -> resume(track.id, ms)
            v.loop -> stop(track.id, ms)
            else -> restart(v)
        }
    }

    fun play(track: Track, fadeMs: Long = settings.fadeMs.value) {
        voices[track.id]?.let {
            if (it.stopping || it.paused) resume(track.id, fadeMs)
            return
        }
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .build()
        val voice = Voice(track, player)
        voice.apply()
        player.repeatMode = if (track.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && voices[track.id] === voice) remove(track.id) else publish()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

            override fun onPlayerError(error: PlaybackException) {
                resolver.invalidate(track)
                _errors.tryEmit("${track.title}: ${error.errorCodeName}")
                if (voices[track.id] === voice) remove(track.id)
            }
        })
        val wasEmpty = voices.isEmpty()
        voices[track.id] = voice
        if (wasEmpty) onBecameActive?.invoke()
        publish()
        startTicker()

        voice.loadJob = scope.launch {
            try {
                val uri = resolver.resolve(track)
                if (voices[track.id] !== voice) return@launch
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = !voice.paused
                while (isActive && player.playbackState != Player.STATE_READY) delay(15)
                voice.loading = false
                if (!voice.paused && !voice.stopping) fadeTo(voice, 1f, fadeMs)
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit("${track.title}: ${e.message ?: "errore di caricamento"}")
                if (voices[track.id] === voice) remove(track.id)
            }
        }
    }

    fun stop(trackId: Long, fadeMs: Long = settings.fadeMs.value) {
        val v = voices[trackId] ?: return
        if (v.paused || v.loading) {
            remove(trackId)
            return
        }
        v.stopping = true
        publish()
        fadeTo(v, 0f, Fader.scaledDuration(fadeMs, v.gain, 0f)) { remove(trackId) }
    }

    fun pause(trackId: Long, fadeMs: Long = settings.fadeMs.value) {
        val v = voices[trackId] ?: return
        if (v.paused) return
        v.paused = true
        v.stopping = false
        publish()
        fadeTo(v, 0f, Fader.scaledDuration(fadeMs, v.gain, 0f)) {
            v.player.pause()
            publish()
        }
    }

    fun resume(trackId: Long, fadeMs: Long = settings.fadeMs.value) {
        val v = voices[trackId] ?: return
        v.paused = false
        v.stopping = false
        if (!v.loading) {
            v.player.play()
            fadeTo(v, 1f, Fader.scaledDuration(fadeMs, v.gain, 1f))
        }
        publish()
    }

    private fun restart(v: Voice) {
        v.player.seekTo(0)
        v.player.play()
        fadeTo(v, 1f, 0)
    }

    fun stopAll(fadeMs: Long = settings.fadeMs.value) {
        voices.keys.toList().forEach { stop(it, fadeMs) }
    }

    fun stopNow() {
        voices.keys.toList().forEach { remove(it) }
    }

    fun setVolume(trackId: Long, volume: Float) {
        val v = voices[trackId] ?: return
        v.volume = volume
        v.apply()
        publish()
    }

    fun setLoop(trackId: Long, loop: Boolean) {
        val v = voices[trackId] ?: return
        v.loop = loop
        v.player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        publish()
    }

    fun setMaster(value: Float) {
        settings.setMaster(value)
        voices.values.forEach { it.apply() }
    }

    private fun fadeTo(v: Voice, target: Float, ms: Long, onDone: (() -> Unit)? = null) {
        v.fadeJob?.cancel()
        v.fadeJob = scope.launch {
            val start = v.gain
            if (ms > 0) {
                val t0 = SystemClock.uptimeMillis()
                while (true) {
                    val t = (SystemClock.uptimeMillis() - t0).toFloat() / ms
                    if (t >= 1f) break
                    v.gain = Fader.value(start, target, t)
                    v.apply()
                    delay(FRAME_MS)
                }
            }
            v.gain = target
            v.apply()
            onDone?.invoke()
        }
    }

    private fun remove(trackId: Long) {
        val v = voices.remove(trackId) ?: return
        v.loadJob?.cancel()
        v.fadeJob?.cancel()
        v.player.release()
        publish()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (voices.isNotEmpty()) {
                delay(500)
                publish()
            }
        }
    }

    private fun publish() {
        _state.value = voices.values.map { v ->
            VoiceState(
                trackId = v.track.id,
                title = v.track.title,
                kind = v.track.kind,
                volume = v.volume,
                loop = v.loop,
                status = when {
                    v.stopping -> VoiceStatus.STOPPING
                    v.paused -> VoiceStatus.PAUSED
                    v.loading || v.player.playbackState == Player.STATE_BUFFERING -> VoiceStatus.LOADING
                    else -> VoiceStatus.PLAYING
                },
                positionMs = v.player.currentPosition.coerceAtLeast(0),
                durationMs = v.player.duration.takeIf { it != C.TIME_UNSET } ?: v.track.durationMs,
            )
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
