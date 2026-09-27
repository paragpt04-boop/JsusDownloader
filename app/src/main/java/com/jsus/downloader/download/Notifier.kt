package com.jsus.downloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jsus.downloader.MainActivity
import com.jsus.downloader.R
import com.jsus.downloader.data.DlJob
import com.jsus.downloader.data.JobStatus
import com.jsus.downloader.data.Prefs

object Notifier {
    const val CH_PROGRESS = "progress"
    const val CH_DONE = "done"
    const val PROGRESS_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun progress(context: Context, jobs: List<DlJob>): Notification {
        val active = jobs.filter { it.status.active }
        val first = active.firstOrNull { it.status == JobStatus.DOWNLOADING } ?: active.firstOrNull()
        val title = when {
            active.size > 1 -> "Descargando ${active.size} archivos"
            first != null -> first.title
            else -> "JSUS Downloader"
        }
        val text = when (first?.status) {
            JobStatus.DOWNLOADING -> "${first.percent.toInt()}%  ${first.speed}".trim()
            JobStatus.PROCESSING, JobStatus.SAVING -> first.stage
            JobStatus.QUEUED -> "En cola"
            else -> "Preparando…"
        }
        val pct = first?.percent?.toInt() ?: 0
        val indeterminate = first == null || first.status != JobStatus.DOWNLOADING
        return NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct, indeterminate)
            .setContentIntent(openAppIntent(context))
            .build()
    }

    fun finished(context: Context, job: DlJob) {
        if (!Prefs.notifications) return
        val ok = job.status == JobStatus.DONE
        val single = job.outputs.singleOrNull()
        val content = if (ok && single != null && single.uri.isNotBlank()) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(single.uri), single.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, job.id.hashCode(), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else openAppIntent(context)

        val n = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(if (ok) "Descarga completa" else "Error en la descarga")
            .setContentText(job.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (ok) "${job.title}\n${job.label} · ${job.destLabel}" else "${job.title}\n${job.error ?: ""}"))
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(job.id.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }
}
