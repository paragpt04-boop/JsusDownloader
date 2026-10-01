package com.jsus.downloader.download

import android.net.Uri
import com.jsus.downloader.App
import com.jsus.downloader.data.DlJob
import com.jsus.downloader.data.Fmt
import com.jsus.downloader.data.MediaInfo
import com.jsus.downloader.data.Prefs
import com.jsus.downloader.data.Quality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Todo lo que habla con yt-dlp: info del video, armado de la petición y errores legibles. */
object Engine {

    private val LOSSY = setOf("mp3", "m4a", "opus")

    /** Nombre base de los archivos temporales de un video suelto */
    const val TMP_BASE = "media"

    private fun JSONObject.s(key: String, def: String = ""): String = if (isNull(key)) def else optString(key, def)

    // ─────────────── Info ───────────────
    suspend fun fetchInfo(url: String): MediaInfo = withContext(Dispatchers.IO) {
        waitEngine()
        val hasList = try { Uri.parse(url).getQueryParameter("list") != null } catch (_: Exception) { false }

        val single = async { runJson(url, listOf("-J", "--no-playlist")) }
        val flat = if (hasList) async {
            try { runJson(url, listOf("-J", "--flat-playlist", "--yes-playlist")) } catch (_: Exception) { null }
        } else null

        parse(single.await(), flat?.await(), url)
    }

    suspend fun waitEngine() {
        try {
            App.ready.await()
        } catch (e: Throwable) {
            throw Exception("No se pudo iniciar el motor de descargas: ${App.initError ?: e.message}")
        }
    }

    private fun runJson(url: String, opts: List<String>): JSONObject {
        val req = YoutubeDLRequest(url)
        opts.forEach { req.addOption(it) }
        val resp = YoutubeDL.getInstance().execute(req)
        val out = resp.out.trim()
        val start = out.indexOf('{')
        if (start < 0) throw Exception(resp.err.ifBlank { "yt-dlp no devolvió información" })
        return JSONObject(out.substring(start))
    }

    private fun sizeOf(f: JSONObject): Long {
        val a = f.optLong("filesize", 0L)
        return if (a > 0) a else f.optLong("filesize_approx", 0L)
    }

    private fun parse(info: JSONObject, flat: JSONObject?, url: String): MediaInfo {
        var plTitle: String? = null
        var plCount = 0
        if (flat != null && flat.s("_type") == "playlist") {
            plTitle = flat.s("title", "Playlist")
            plCount = flat.optJSONArray("entries")?.length() ?: flat.optInt("playlist_count", 0)
        }

        if (info.s("_type") == "playlist") {
            val thumbs = info.optJSONArray("thumbnails")
            val thumb = if (thumbs != null && thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).s("url") else ""
            val title = info.s("title", "Playlist")
            return MediaInfo(
                kind = "playlist", title = title, uploader = info.s("uploader", info.s("channel")),
                thumbnail = thumb, duration = 0, views = 0, webpageUrl = url, qualities = emptyList(),
                h264Max = 1080, maxAbr = 160, playlistTitle = title,
                playlistCount = info.optJSONArray("entries")?.length() ?: plCount
            )
        }

        if (info.optBoolean("is_live", false)) throw Exception("Las transmisiones en vivo no se pueden descargar mientras están al aire.")

        val fmts = info.optJSONArray("formats") ?: JSONArray()
        var bestAbr = 0.0
        var audioSize = 0L
        for (i in 0 until fmts.length()) {
            val f = fmts.getJSONObject(i)
            if (f.s("vcodec", "none") == "none" && f.s("acodec", "none") != "none") {
                var abr = f.optDouble("abr", 0.0)
                if (abr.isNaN() || abr <= 0) abr = f.optDouble("tbr", 0.0).let { if (it.isNaN()) 0.0 else it }
                if (abr > bestAbr) { bestAbr = abr; audioSize = sizeOf(f) }
            }
        }

        val map = LinkedHashMap<Int, Quality>()
        var h264Max = 0
        for (i in 0 until fmts.length()) {
            val f = fmts.getJSONObject(i)
            val vcodec = f.s("vcodec", "none")
            val height = f.optInt("height", 0)
            if (vcodec == "none" || height <= 0) continue
            val width = f.optInt("width", 0)
            val key = if (width > 0) minOf(width, height) else height   // lado corto (Shorts verticales)
            if (key < 144) continue
            val hasAudio = f.s("acodec", "none") != "none"
            val size = sizeOf(f) + if (hasAudio) 0 else audioSize
            val fps = f.optDouble("fps", 0.0).let { if (it.isNaN()) 0 else it.toInt() }
            if (vcodec.startsWith("avc") || vcodec.startsWith("h264")) h264Max = maxOf(h264Max, key)
            val cur = map[key]
            map[key] = if (cur == null) Quality(key, Fmt.resLabel(key), fps, size)
            else cur.copy(fps = maxOf(cur.fps, fps), size = maxOf(cur.size, size))
        }

        return MediaInfo(
            kind = "video",
            title = info.s("title", "Sin título"),
            uploader = info.s("uploader", info.s("channel")),
            thumbnail = info.s("thumbnail"),
            duration = info.optDouble("duration", 0.0).let { if (it.isNaN()) 0 else it.toInt() },
            views = info.optLong("view_count", 0L),
            webpageUrl = info.s("webpage_url", url),
            qualities = map.values.sortedByDescending { it.height },
            h264Max = if (h264Max > 0) h264Max else 1080,
            maxAbr = bestAbr.toInt(),
            playlistTitle = plTitle,
            playlistCount = plCount,
            id = info.s("id"),
            uploadDate = info.s("upload_date")
        )
    }

    // ─────────────── Petición de descarga ───────────────
    fun buildRequest(job: DlJob, tmp: File): YoutubeDLRequest {
        val o = job.opts
        val r = YoutubeDLRequest(job.url)
        r.addOption("--newline")
        r.addOption("--no-mtime")
        r.addOption("--windows-filenames")
        r.addOption(if (o.playlist) "--yes-playlist" else "--no-playlist")
        if (o.playlist) r.addOption("--ignore-errors")

        // Conexión más resistente: reintentos y descarga por partes (evita que YouTube frene)
        r.addOption("--retries", "10")
        r.addOption("--fragment-retries", "10")
        r.addOption("--socket-timeout", "30")
        r.addOption("--http-chunk-size", "10M")

        // Nombre temporal seguro; el nombre final lo pone la app al guardar
        r.addOption("-P", tmp.absolutePath)
        if (o.playlist) r.addOption("-o", "%(playlist_index)03d - %(title).80B.%(ext)s")
        else r.addOption("-o", "$TMP_BASE.%(ext)s")

        if (o.type == "video" && o.reel) {
            // ── Modo Reel / Short: vertical 1080x1920, H.264 + AAC ──
            r.addOption("-f", "bv*+ba/b")
            r.addOption("-S", "res:1080,fps")
            r.addOption("--merge-output-format", "mkv")   // mkv -> mp4 obliga a convertir
            r.addOption("--recode-video", "mp4")
            val vf = if (o.reelFit == "blur")
                "split[a][b];[a]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,boxblur=20:5[bg];[b]scale=1080:1920:force_original_aspect_ratio=decrease[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2,setsar=1"
            else
                "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1"
            val codec = if (o.reelMpeg4) "-c:v mpeg4 -q:v 2" else "-c:v libx264 -preset veryfast -crf 21 -pix_fmt yuv420p"
            r.addOption("--postprocessor-args", "VideoConvertor:-vf $vf $codec -c:a aac -b:a 192k -movflags +faststart")
            if (Prefs.embedMetadata) r.addOption("--embed-metadata")
        } else if (o.type == "video") {
            val sort = mutableListOf<String>()
            if (o.h264) sort.add("vcodec:h264")
            sort.add(if (o.quality == "best") "res" else "res:${o.quality}")
            sort.add("fps")
            val fmt = when (o.container) {
                "mp4" -> "bv*+ba[ext=m4a]/bv*+ba/b"
                "webm" -> "bv*[ext=webm]+ba[ext=webm]/bv*+ba/b"
                else -> "bv*+ba/b"
            }
            r.addOption("-f", fmt)
            r.addOption("-S", sort.joinToString(","))
            r.addOption("--merge-output-format", o.container)
            if (o.container != "webm") r.addOption("--remux-video", o.container)

            if (o.subtitles) {
                val langs = Prefs.subLangs.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    .joinToString(",") { "$it.*" }.ifBlank { "es.*,en.*" }
                r.addOption("--write-subs")
                r.addOption("--sub-langs", "$langs,-live_chat")
                if (Prefs.autoSubs) r.addOption("--write-auto-subs")
                if (Prefs.embedSubs) r.addOption("--embed-subs") else r.addOption("--convert-subs", "srt")
            }
            if (o.embedThumbnail && o.container != "webm") {
                r.addOption("--embed-thumbnail")
                r.addOption("--convert-thumbnails", "jpg")
            }
            if (Prefs.embedChapters) r.addOption("--embed-chapters")
        } else {
            r.addOption("-f", "ba/b")
            r.addOption("-x")
            if (o.audioFormat != "original") {
                r.addOption("--audio-format", o.audioFormat)
                r.addOption("--audio-quality", if (o.audioFormat in LOSSY) "${o.bitrate}K" else "0")
            }
            if (o.embedThumbnail && o.audioFormat != "wav") {
                r.addOption("--embed-thumbnail")
                r.addOption("--convert-thumbnails", "jpg")
            }
        }

        if (Prefs.embedMetadata && !(o.type == "video" && o.reel)) r.addOption("--embed-metadata")
        if (o.sponsorBlock) r.addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
        val limit = Prefs.speedLimit.trim()
        if (limit.matches(Regex("""^\d+(\.\d+)?[KMG]?$""", RegexOption.IGNORE_CASE))) r.addOption("-r", limit)
        if (!o.playlist && (o.start.isNotBlank() || o.end.isNotBlank())) {
            r.addOption("--download-sections", "*${o.start.ifBlank { "0" }}-${o.end.ifBlank { "inf" }}")
            r.addOption("--force-keyframes-at-cuts")
        }
        return r
    }

    // ─────────────── Errores legibles ───────────────
    fun friendlyError(raw: String?): String {
        val m = raw ?: ""
        val line = m.lines().lastOrNull { it.contains("ERROR:") }?.substringAfter("ERROR:")?.trim() ?: m.lines().lastOrNull { it.isNotBlank() }?.trim() ?: ""
        val rules = listOf(
            Regex("not a bot|Sign in to confirm", RegexOption.IGNORE_CASE) to "YouTube pide verificación anti-bot. Actualiza yt-dlp en Ajustes y reintenta.",
            Regex("confirm your age|age-restricted|inappropriate", RegexOption.IGNORE_CASE) to "Video con restricción de edad: no se puede sin iniciar sesión.",
            Regex("Private video", RegexOption.IGNORE_CASE) to "El video es privado.",
            Regex("members-only|Join this channel", RegexOption.IGNORE_CASE) to "Video solo para miembros del canal.",
            Regex("Video unavailable|This video is unavailable|has been removed", RegexOption.IGNORE_CASE) to "Video no disponible o eliminado.",
            Regex("Requested format is not available", RegexOption.IGNORE_CASE) to "Esa calidad no está disponible. Prueba otra.",
            Regex("HTTP Error 403|403: Forbidden", RegexOption.IGNORE_CASE) to "YouTube bloqueó la descarga (403). Actualiza yt-dlp en Ajustes.",
            Regex("HTTP Error 429|Too Many Requests", RegexOption.IGNORE_CASE) to "YouTube limitó las peticiones (429). Espera unos minutos.",
            Regex("Unable to download|getaddrinfo|timed out|Network is unreachable|No address associated|Failed to resolve", RegexOption.IGNORE_CASE) to "Sin conexión con el sitio. Revisa tu internet.",
            Regex("Unsupported URL|is not a valid URL", RegexOption.IGNORE_CASE) to "Link no soportado.",
            Regex("No space left|Errno 28", RegexOption.IGNORE_CASE) to "No hay espacio en el teléfono.",
            Regex("ffmpeg exited|Conversion failed|Postprocessing", RegexOption.IGNORE_CASE) to "ffmpeg no pudo procesar el archivo. Prueba otro formato o quita el recorte."
        )
        for ((re, txt) in rules) if (re.containsMatchIn(m)) return "$txt\n(${line.take(200)})"
        return line.take(400).ifBlank { "Error desconocido" }
    }
}
