package com.jsus.downloader.download

import android.content.Context
import com.jsus.downloader.App
import com.jsus.downloader.data.DlJob
import com.jsus.downloader.data.DlOptions
import com.jsus.downloader.data.Fmt
import com.jsus.downloader.data.HistoryItem
import com.jsus.downloader.data.HistoryStore
import com.jsus.downloader.data.JobStatus
import com.jsus.downloader.data.Prefs
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Cola de descargas: estado global que ven la UI, el servicio y las notificaciones. */
object DownloadRepo {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<List<DlJob>>(emptyList())
    val jobs: StateFlow<List<DlJob>> = _jobs

    private val canceled: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val lastEmit = ConcurrentHashMap<String, Long>()

    private val RUNNING = setOf(JobStatus.PREPARING, JobStatus.DOWNLOADING, JobStatus.PROCESSING, JobStatus.SAVING)
    private val RE_ITEM = Regex("""Downloading item (\d+) of (\d+)""")
    private val RE_SPEED = Regex("""at\s+~?\s*([\d.]+\s*[KMGT]?i?B/s)""")
    private val RE_TOTAL = Regex("""of\s+~?\s*([\d.]+\s*[KMGT]?i?B)""")

    fun get(id: String): DlJob? = _jobs.value.firstOrNull { it.id == id }

    private fun set(id: String, change: DlJob.() -> DlJob) {
        _jobs.update { list -> list.map { if (it.id == id) it.change() else it } }
    }

    fun enqueue(context: Context, url: String, title: String, thumbnail: String, opts: DlOptions, destTree: String?): DlJob {
        val id = System.currentTimeMillis().toString(36) + (1000..9999).random()
        val job = DlJob(
            id = id, url = url, title = title.ifBlank { url }, thumbnail = thumbnail,
            label = Fmt.label(opts), opts = opts, destTree = destTree,
            destLabel = Saver.treeLabel(context, destTree)
        )
        _jobs.update { listOf(job) + it }
        DownloadService.start(context.applicationContext)
        pump()
        return job
    }

    @Synchronized
    private fun pump() {
        val list = _jobs.value
        var slots = Prefs.maxConcurrent.coerceIn(1, 5) - list.count { it.status in RUNNING }
        for (j in list.reversed()) {           // más viejas primero
            if (slots <= 0) break
            if (j.status == JobStatus.QUEUED) {
                set(j.id) { copy(status = JobStatus.PREPARING, stage = "Preparando…") }
                slots--
                scope.launch { runJob(j.id) }
            }
        }
    }

    fun cancel(id: String) {
        val j = get(id) ?: return
        if (!j.status.active) return
        canceled.add(id)
        if (j.status == JobStatus.QUEUED) {
            finish(id, JobStatus.CANCELED, "Cancelado por ti.")
        } else {
            try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) {}
        }
    }

    fun remove(id: String) {
        _jobs.update { list -> list.filterNot { it.id == id && !it.status.active } }
    }

    fun clearFinished() {
        _jobs.update { list -> list.filter { it.status.active } }
    }

    private suspend fun runJob(id: String) {
        val ctx = App.instance
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val tmp = File(base, "tmp/$id")
        try {
            tmp.deleteRecursively()
            tmp.mkdirs()
            Engine.waitEngine()
            if (id in canceled) { finish(id, JobStatus.CANCELED, "Cancelado por ti."); return }
            val job = get(id) ?: return
            val req = Engine.buildRequest(job, tmp)

            var failure: Throwable? = null
            try {
                YoutubeDL.getInstance().execute(request = req, processId = id, callback = { p: Float, eta: Long, line: String ->
                    onLine(id, p, eta, line)
                })
            } catch (e: Throwable) {
                failure = e
            }

            if (id in canceled) { finish(id, JobStatus.CANCELED, "Cancelado por ti."); return }

            val files = tmp.walkTopDown().filter { f ->
                f.isFile && !f.name.startsWith(".") &&
                    !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") && !f.name.contains(".part-Frag") &&
                    !f.name.endsWith(".temp") &&
                    f.extension.lowercase() !in setOf("jpg", "jpeg", "webp", "png")
            }.sortedBy { it.absolutePath }.toList()

            if (failure != null) {
                val msg = failure.message ?: failure.toString()
                val onlyPostProcess = files.isNotEmpty() &&
                    Regex("thumbnail|metadata|subtitle|Postprocessing|ERROR: \\[.*\\] .*item", RegexOption.IGNORE_CASE).containsMatchIn(msg)
                if (!onlyPostProcess && !(job.opts.playlist && files.isNotEmpty())) {
                    finish(id, JobStatus.ERROR, Engine.friendlyError(msg))
                    return
                }
            }
            if (files.isEmpty()) {
                finish(id, JobStatus.ERROR, "No se generó ningún archivo. Prueba otra calidad o actualiza yt-dlp en Ajustes.")
                return
            }

            set(id) { copy(status = JobStatus.SAVING, stage = "Guardando en $destLabel…", percent = 100f) }
            val outs = Saver.save(ctx, files, tmp, job.destTree)
            set(id) { copy(outputs = outs) }
            finish(id, JobStatus.DONE, null)
        } catch (e: Throwable) {
            if (id in canceled) finish(id, JobStatus.CANCELED, "Cancelado por ti.")
            else finish(id, JobStatus.ERROR, Engine.friendlyError(e.message ?: e.toString()))
        } finally {
            try { tmp.deleteRecursively() } catch (_: Exception) {}
            canceled.remove(id)
            lastEmit.remove(id)
            pump()
        }
    }

    private fun onLine(id: String, progress: Float, eta: Long, line: String) {
        val stage = when {
            line.startsWith("[Merger]") -> "Uniendo video y audio…"
            line.startsWith("[ExtractAudio]") -> "Convirtiendo audio…"
            line.startsWith("[EmbedThumbnail]") -> "Poniendo la portada…"
            line.startsWith("[Metadata]") -> "Escribiendo metadatos…"
            line.startsWith("[EmbedSubtitle]") -> "Incrustando subtítulos…"
            line.startsWith("[VideoRemuxer]") || line.startsWith("[VideoConvertor]") -> "Preparando formato final…"
            line.startsWith("[SponsorBlock]") || line.startsWith("[ModifyChapters]") -> "Quitando patrocinios…"
            line.startsWith("[FixupM") -> "Corrigiendo archivo…"
            else -> null
        }
        if (stage != null) {
            set(id) { copy(status = JobStatus.PROCESSING, stage = stage) }
            return
        }
        RE_ITEM.find(line)?.let { m ->
            set(id) { copy(plIndex = m.groupValues[1].toInt(), plCount = m.groupValues[2].toInt(), part = 1, percent = 0f) }
            return
        }
        if (line.startsWith("[download] Destination:")) {
            set(id) { copy(part = if (status == JobStatus.DOWNLOADING && percent > 50f) part + 1 else part, percent = 0f, status = JobStatus.DOWNLOADING) }
            return
        }
        if (progress >= 0f && line.startsWith("[download]")) {
            val now = System.currentTimeMillis()
            if (now - (lastEmit[id] ?: 0L) < 300 && progress < 100f) return
            lastEmit[id] = now
            val speed = RE_SPEED.find(line)?.groupValues?.get(1) ?: ""
            val total = RE_TOTAL.find(line)?.groupValues?.get(1) ?: ""
            set(id) {
                copy(
                    status = JobStatus.DOWNLOADING, percent = progress, eta = eta,
                    speed = speed.ifBlank { this.speed }, total = total.ifBlank { this.total }, stage = ""
                )
            }
        }
    }

    private fun finish(id: String, status: JobStatus, error: String?) {
        val before = get(id) ?: return
        if (!before.status.active) return
        set(id) { copy(status = status, error = error, finishedAt = System.currentTimeMillis(), stage = "", speed = "") }
        val job = get(id) ?: return
        if (status == JobStatus.DONE) {
            HistoryStore.add(
                HistoryItem(job.id, job.title, job.label, job.url, job.thumbnail, job.finishedAt, job.outputs)
            )
        }
        if (status == JobStatus.DONE || status == JobStatus.ERROR) Notifier.finished(App.instance, job)
    }
}
