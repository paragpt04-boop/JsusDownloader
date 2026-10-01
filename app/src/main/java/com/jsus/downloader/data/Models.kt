package com.jsus.downloader.data

import java.util.Locale

data class Quality(
    val height: Int,        // 0 = "máxima"
    val label: String,
    val fps: Int = 0,
    val size: Long = 0
)

data class MediaInfo(
    val kind: String,               // video | playlist
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val duration: Int,
    val views: Long,
    val webpageUrl: String,
    val qualities: List<Quality>,
    val h264Max: Int,
    val maxAbr: Int,
    val playlistTitle: String?,
    val playlistCount: Int,
    val id: String = "",
    val uploadDate: String = ""     // YYYYMMDD
)

data class DlOptions(
    val type: String = "video",       // video | audio
    val quality: String = "best",     // best | altura
    val container: String = "mp4",
    val h264: Boolean = false,
    val audioFormat: String = "mp3",
    val bitrate: String = "320",
    val subtitles: Boolean = false,
    val embedThumbnail: Boolean = true,
    val sponsorBlock: Boolean = false,
    val playlist: Boolean = false,
    val start: String = "",
    val end: String = "",
    val customName: String = "",      // nombre elegido (sin extensión). Vacío = automático
    val subfolder: String = "",       // carpeta / categoría dentro del destino
    val reel: Boolean = false,        // convertir a vertical 9:16 para Reels/Shorts
    val reelFit: String = "crop",     // crop = llenar pantalla | blur = completo con fondo desenfocado
    val reelMpeg4: Boolean = false    // interno: codificador de respaldo si no hay H.264
)

enum class JobStatus {
    QUEUED, PREPARING, DOWNLOADING, PROCESSING, SAVING, DONE, ERROR, CANCELED;

    val active: Boolean
        get() = this == QUEUED || this == PREPARING || this == DOWNLOADING || this == PROCESSING || this == SAVING
}

data class OutFile(val name: String, val uri: String, val mime: String)

data class DlJob(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String,
    val label: String,
    val opts: DlOptions,
    val destTree: String?,
    val destLabel: String,
    val status: JobStatus = JobStatus.QUEUED,
    val percent: Float = 0f,
    val speed: String = "",
    val total: String = "",
    val eta: Long = 0,
    val stage: String = "",
    val part: Int = 1,
    val plIndex: Int = 0,
    val plCount: Int = 0,
    val error: String? = null,
    val outputs: List<OutFile> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0
)

data class HistoryItem(
    val id: String,
    val title: String,
    val label: String,
    val url: String,
    val thumbnail: String,
    val date: Long,
    val outputs: List<OutFile>
)

object Fmt {
    fun resLabel(h: Int): String = when {
        h <= 0 -> "Máxima"
        h >= 4320 -> "8K"
        h >= 2160 -> "4K"
        h >= 1440 -> "2K"
        else -> "${h}p"
    }

    fun size(bytes: Long): String {
        if (bytes <= 0) return ""
        val b = bytes.toDouble()
        return when {
            b >= 1e9 -> String.format(Locale.US, "%.2f GB", b / 1e9)
            b >= 1e8 -> String.format(Locale.US, "%.0f MB", b / 1e6)
            b >= 1e6 -> String.format(Locale.US, "%.1f MB", b / 1e6)
            else -> String.format(Locale.US, "%.0f KB", maxOf(1.0, b / 1e3))
        }
    }

    fun duration(sec: Long): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun views(n: Long): String = when {
        n <= 0 -> ""
        n >= 1_000_000_000 -> String.format(Locale.US, "%.1f mil M vistas", n / 1e9)
        n >= 1_000_000 -> String.format(Locale.US, "%.1f M vistas", n / 1e6)
        n >= 1_000 -> "${n / 1000} mil vistas"
        else -> "$n vistas"
    }

    fun label(o: DlOptions): String {
        var l = if (o.type == "video") {
            val q = if (o.quality == "best") "Máxima" else resLabel(o.quality.toIntOrNull() ?: 0)
            "$q · ${o.container.uppercase()}" + if (o.h264) " · H.264" else ""
        } else when (o.audioFormat) {
            "original" -> "Audio original"
            "mp3", "m4a", "opus" -> "${o.audioFormat.uppercase()} · ${o.bitrate} kbps"
            else -> o.audioFormat.uppercase()
        }
        if (o.reel && o.type == "video") l = "Reel 9:16 · MP4"
        if (o.playlist) l += " · Playlist"
        if (o.start.isNotBlank() || o.end.isNotBlank()) l += " · Recorte"
        return l
    }

    /** Nombre seguro para Android/Windows: sin caracteres prohibidos y máximo ~120 bytes. */
    fun safeName(raw: String, maxBytes: Int = 120): String {
        var n = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim().trim('.', ' ')
        if (n.isEmpty()) n = "descarga"
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < n.length) {
            val cp = n.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + len > maxBytes) break
            sb.appendCodePoint(cp)
            bytes += len
            i += Character.charCount(cp)
        }
        return sb.toString().trim().trim('.', ' ').ifEmpty { "descarga" }
    }

    /** Subcarpeta segura: hasta 3 niveles separados por "/" */
    fun safeFolder(raw: String): String =
        raw.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
            .joinToString("/") { safeName(it, 60) }

    /** Aplica la plantilla de nombre (estilo yt-dlp) con los datos del video. */
    fun applyTemplate(tpl: String, i: MediaInfo, o: DlOptions): String {
        val date = if (i.uploadDate.length == 8) "${i.uploadDate.substring(0, 4)}-${i.uploadDate.substring(4, 6)}-${i.uploadDate.substring(6, 8)}" else ""
        val height = if (o.quality == "best") (i.qualities.firstOrNull()?.height?.toString() ?: "") else o.quality
        val out = tpl
            .replace(Regex("""%\(upload_date>[^)]*\)s"""), date)
            .replace("%(title)s", i.title)
            .replace("%(uploader)s", i.uploader)
            .replace("%(channel)s", i.uploader)
            .replace("%(id)s", i.id)
            .replace("%(height)s", height)
            .replace(Regex("""%\([^)]*\)[a-z0-9]*"""), "")
            .replace(Regex("""\s*-\s*-\s*"""), " - ")
            .trim().trim('-', ' ', '[', ']').trim()
        return safeName(out.ifBlank { i.title })
    }

    val TIME_RE = Regex("""^\d{1,3}(:\d{1,2}){0,2}(\.\d+)?$""")
    fun validTime(t: String) = t.isBlank() || TIME_RE.matches(t.trim())
}
