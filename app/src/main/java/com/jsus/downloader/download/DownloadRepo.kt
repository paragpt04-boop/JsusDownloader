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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Cola de descargas: estado global que ven la UI, el servicio y las notificaciones. */
object DownloadRepo {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<List<DlJob>>(emptyList())
    val jobs: StateFlow<List<DlJob>> = _jobs

    private val canceled: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val stalled: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val lastEmit = ConcurrentHashMap<String, Long>()
    private val lastActivity = ConcurrentHashMap<String, Long>()

    private const val MAX_ATTEMPTS = 3
    private const val STALL_MS = 75_000L            // sin avanzar este tiempo = trabada

    private val RUNNING = setOf(JobStatus.PREPARING, JobStatus.DOWNLOADING, JobStatus.PROCESSING, JobStatus.SAVING)
    private val RE_ITEM = Regex("""Downloading item (\d+) of (\d+)""")
    private val RE_SPEED = Regex("""at\s+~?\s*([\d.]+\s*[KMGT]?i?B/s)""")
    private val RE_TOTAL = Regex("""of\s+~?\s*([\d.]+\s*[KMGT]?i?B)""")
    private val RE_RETRYABLE = Regex(
        "timed out|timeout|Connection|reset by peer|IncompleteRead|Temporary failure|Network is unreachable|" +
            "HTTP Error 5\\d\\d|HTTP Error 403|Got error|unable to download video data|Broken pipe|EOF occurred",
        RegexOption.IGNORE_CASE
    )
    private val IMAGE_EXT = setOf("jpg", "jpeg", "webp", "png")

    fun get(id: String): DlJob? = _jobs.value.firstOrNull { it.id == id }

    private fun set(id: String, change: DlJob.() -> DlJob) {
        _jobs.update { list -> list.map { if (it.id == id) it.change() else it } }
    }

    fun enqueue(context: Context, url: String, title: String, thumbnail: String, opts: DlOptions, destTree: String?): DlJob {
        val id = System.currentTimeMillis().toString(36) + (1000..9999).random()
        val shown = opts.customName.ifBlank { title }.ifBlank { url }
        val dest = Saver.treeLabel(context, destTree) + if (opts.subfolder.isNotBlank()) "/${opts.subfolder}" else ""
        val job = DlJob(
            id = id, url = url, title = shown, thumbnail = thumbnail,
            label = Fmt.label(opts), opts = opts, destTree = destTree, destLabel = dest
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

    /** Vuelve a poner en cola una descarga que falló o se canceló. */
    fun retry(context: Context, id: String) {
        val j = get(id) ?: return
        if (j.status.active) return
        set(id) {
            copy(
                status = JobStatus.QUEUED, error = null, percent = 0f, speed = "", total = "", eta = 0,
                stage = "", part = 1, plIndex = 0, plCount = 0, outputs = emptyList(), finishedAt = 0,
                opts = opts.copy(reelMpeg4 = false)
            )
        }
        DownloadService.start(context.applicationContext)
        pump()
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

            var attempt = 1
            var files: List<File> = emptyList()
            while (true) {
                if (id in canceled) { finish(id, JobStatus.CANCELED, "Cancelado por ti."); return }
                val job = get(id) ?: return
                val failure = executeOnce(id, Engine.buildRequest(job, tmp))
                if (id in canceled) { finish(id, JobStatus.CANCELED, "Cancelado por ti."); return }

                files = collectFiles(tmp)
                if (failure == null) break

                val msg = failure.message ?: failure.toString()
                val wasStalled = stalled.remove(id)

                // Sin H.264 en ffmpeg: repetir la conversión del Reel con el codificador de respaldo
                if (job.opts.reel && !job.opts.reelMpeg4 && Regex("libx264|Unknown encoder|Encoder not found", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
                    set(id) { copy(opts = opts.copy(reelMpeg4 = true), status = JobStatus.PROCESSING, stage = "Reintentando conversión del Reel…") }
                    continue
                }

                // Errores de conexión o descarga trabada: reintentar (continúa donde iba)
                if ((wasStalled || RE_RETRYABLE.containsMatchIn(msg)) && attempt < MAX_ATTEMPTS) {
                    attempt++
                    set(id) {
                        copy(
                            status = JobStatus.PREPARING, speed = "",
                            stage = (if (wasStalled) "Se trabó la descarga" else "Se cortó la conexión") + ", reintentando ($attempt/$MAX_ATTEMPTS)…"
                        )
                    }
                    delay(3000L * attempt)
                    continue
                }

                val onlyPostProcess = files.isNotEmpty() &&
                    Regex("thumbnail|metadata|subtitle", RegexOption.IGNORE_CASE).containsMatchIn(msg)
                if (onlyPostProcess || (job.opts.playlist && files.isNotEmpty())) break

                finish(id, JobStatus.ERROR, if (wasStalled) "La descarga se quedó trabada varias veces. Revisa tu conexión y toca Reintentar." else Engine.friendlyError(msg))
                return
            }

            if (files.isEmpty()) {
                finish(id, JobStatus.ERROR, "No se generó ningún archivo. Prueba otra calidad o actualiza yt-dlp en Ajustes.")
                return
            }

            val job = get(id) ?: return
            set(id) { copy(status = JobStatus.SAVING, stage = "Guardando en $destLabel…", percent = 100f) }
            val outs = Saver.save(ctx, buildSaveItems(job, files, tmp), job.destTree)
            set(id) { copy(outputs = outs) }
            if (job.opts.subfolder.isNotBlank()) Prefs.addRecentFolder(job.opts.subfolder)
            finish(id, JobStatus.DONE, null)
        } catch (e: Throwable) {
            if (id in canceled) finish(id, JobStatus.CANCELED, "Cancelado por ti.")
            else finish(id, JobStatus.ERROR, Engine.friendlyError(e.message ?: e.toString()))
        } finally {
            try { tmp.deleteRecursively() } catch (_: Exception) {}
            canceled.remove(id)
            stalled.remove(id)
            lastEmit.remove(id)
            lastActivity.remove(id)
            pump()
        }
    }

    /** Ejecuta yt-dlp una vez con vigilante de "descarga trabada". Devuelve el error o null si salió bien. */
    private suspend fun executeOnce(id: String, req: com.yausername.youtubedl_android.YoutubeDLRequest): Throwable? = coroutineScope {
        lastActivity[id] = System.currentTimeMillis()
        val watchdog = launch {
            while (isActive) {
                delay(5000)
                val j = get(id) ?: break
                val idle = System.currentTimeMillis() - (lastActivity[id] ?: 0L)
                // Solo mientras descarga: la conversión con ffmpeg puede tardar sin escribir nada
                if (j.status == JobStatus.DOWNLOADING && idle > STALL_MS) {
                    stalled.add(id)
                    try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) {}
                    break
                }
            }
        }
        val result: Throwable? = try {
            YoutubeDL.getInstance().execute(request = req, processId = id, callback = { p: Float, eta: Long, line: String ->
                onLine(id, p, eta, line)
            })
            null
        } catch (e: Throwable) {
            e
        }
        watchdog.cancel()
        // Si el vigilante mató el proceso, cuenta como fallo aunque yt-dlp no lance excepción
        if (result == null && id in stalled) Exception("stalled") else result
    }

    private fun collectFiles(tmp: File): List<File> = tmp.walkTopDown().filter { f ->
        f.isFile && !f.name.startsWith(".") &&
            !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") && !f.name.contains(".part-Frag") &&
            !f.name.endsWith(".temp") && !f.name.contains(".temp.") &&
            f.extension.lowercase() !in IMAGE_EXT
    }.sortedBy { it.absolutePath }.toList()

    /** Decide carpeta y nombre final de cada archivo. */
    private fun buildSaveItems(job: DlJob, files: List<File>, tmp: File): List<Saver.SaveItem> {
        val o = job.opts
        val sub = Fmt.safeFolder(o.subfolder)
        return if (o.playlist) {
            val plFolder = if (Prefs.playlistSubfolder) Fmt.safeName(o.customName.ifBlank { job.title }, 80) else ""
            val rel = listOf(sub, plFolder).filter { it.isNotBlank() }.joinToString("/")
            files.map { f -> Saver.SaveItem(f, rel, f.name) }
        } else {
            val baseName = Fmt.safeName(o.customName.ifBlank { job.title })
            files.map { f ->
                // media.mp4 -> Nombre.mp4 · media.es.srt -> Nombre.es.srt
                val suffix = if (f.name.startsWith(Engine.TMP_BASE + ".")) f.name.removePrefix(Engine.TMP_BASE) else "." + f.extension
                Saver.SaveItem(f, sub, baseName + suffix)
            }
        }
    }

    private fun onLine(id: String, progress: Float, eta: Long, line: String) {
        lastActivity[id] = System.currentTimeMillis()
        val stage = when {
            line.startsWith("[Merger]") -> "Uniendo video y audio…"
            line.startsWith("[ExtractAudio]") -> "Convirtiendo audio…"
            line.startsWith("[EmbedThumbnail]") -> "Poniendo la portada…"
            line.startsWith("[Metadata]") -> "Escribiendo metadatos…"
            line.startsWith("[EmbedSubtitle]") -> "Incrustando subtítulos…"
            line.startsWith("[VideoConvertor]") -> if (get(id)?.opts?.reel == true) "Convirtiendo a Reel 9:16… (puede tardar)" else "Convirtiendo video…"
            line.startsWith("[VideoRemuxer]") -> "Preparando formato final…"
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
            set(id) { copy(part = if (status == JobStatus.DOWNLOADING && percent > 50f) part + 1 else part, percent = 0f, status = JobStatus.DOWNLOADING, stage = "") }
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
