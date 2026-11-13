package it.lodaci.soundboard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _fadeMs = MutableStateFlow(prefs.getLong(K_FADE, 2000L))
    val fadeMs: StateFlow<Long> = _fadeMs.asStateFlow()

    private val _master = MutableStateFlow(prefs.getFloat(K_MASTER, 1f))
    val master: StateFlow<Float> = _master.asStateFlow()

    fun setFadeMs(value: Long) {
        _fadeMs.value = value
        prefs.edit().putLong(K_FADE, value).apply()
    }

    fun setMaster(value: Float) {
        _master.value = value
        prefs.edit().putFloat(K_MASTER, value).apply()
    }

    var lastBoardId: Long
        get() = prefs.getLong(K_BOARD, -1L)
        set(value) = prefs.edit().putLong(K_BOARD, value).apply()

    private companion object {
        const val K_FADE = "fadeMs"
        const val K_MASTER = "master"
        const val K_BOARD = "lastBoard"
    }
}
