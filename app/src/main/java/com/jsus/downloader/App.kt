package com.jsus.downloader

import android.app.Application
import com.jsus.downloader.data.HistoryStore
import com.jsus.downloader.data.Prefs
import com.jsus.downloader.download.Notifier
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        HistoryStore.init(this)
        Notifier.createChannels(this)

        // Inicializar el motor (la primera vez descomprime Python + yt-dlp, tarda unos segundos)
        appScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
                ready.complete(Unit)
            } catch (e: Throwable) {
                initError = e.message ?: e.toString()
                ready.completeExceptionally(e)
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val ready = CompletableDeferred<Unit>()
        @Volatile
        var initError: String? = null
    }
}
