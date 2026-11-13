package it.lodaci.soundboard

import android.app.Application
import it.lodaci.soundboard.data.AppDatabase
import it.lodaci.soundboard.data.Board
import it.lodaci.soundboard.data.Settings
import it.lodaci.soundboard.playback.MixerEngine
import it.lodaci.soundboard.playback.PlaybackService
import it.lodaci.soundboard.playback.SourceResolver
import it.lodaci.soundboard.remote.LinkTasks
import it.lodaci.soundboard.remote.YtDlpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SoundboardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Dipendenze condivise dell'app (DI manuale). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(app)
    val settings = Settings(app)
    val ytdlp = YtDlpManager(app, appScope)
    val resolver = SourceResolver(ytdlp)
    val mixer = MixerEngine(app, resolver, settings)
    val linkTasks = LinkTasks(app, appScope, ytdlp, db.trackDao())

    init {
        ytdlp.warmUp()
        mixer.onBecameActive = { PlaybackService.start(app) }
        appScope.launch {
            if (db.boardDao().count() == 0) db.boardDao().insert(Board(name = "Generale"))
        }
    }
}
