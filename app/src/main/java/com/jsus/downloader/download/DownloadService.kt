package com.jsus.downloader.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Servicio en primer plano: mantiene vivas las descargas aunque salgas de la app. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastUpdate = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = Notifier.progress(this, DownloadRepo.jobs.value)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifier.PROGRESS_ID, n, type)

        scope.launch {
            DownloadRepo.jobs.collect { list ->
                if (list.none { it.status.active }) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 800) {
                        lastUpdate = now
                        try {
                            NotificationManagerCompat.from(this@DownloadService)
                                .notify(Notifier.PROGRESS_ID, Notifier.progress(this@DownloadService, list))
                        } catch (_: SecurityException) {
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (_: Exception) {
                // Si Android no deja iniciar el servicio, la descarga sigue mientras la app esté abierta
            }
        }
    }
}
