package com.jsus.downloader.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.jsus.downloader.data.DlJob
import com.jsus.downloader.data.Fmt
import com.jsus.downloader.data.JobStatus
import com.jsus.downloader.data.MediaInfo
import com.jsus.downloader.data.Prefs
import com.jsus.downloader.data.Quality
import com.jsus.downloader.download.DownloadRepo
import com.jsus.downloader.download.Saver

private val GENERIC_QUALITIES = listOf(
    Quality(0, "Máxima"), Quality(2160, "4K"), Quality(1440, "2K"), Quality(1080, "1080p"),
    Quality(720, "720p"), Quality(480, "480p"), Quality(360, "360p"), Quality(240, "240p")
)

private val AUDIO_FORMATS = listOf(
    Triple("mp3", "MP3", "Universal"), Triple("m4a", "M4A", "AAC · iPhone"), Triple("opus", "OPUS", "Liviano"),
    Triple("flac", "FLAC", "Sin pérdida"), Triple("wav", "WAV", "Sin comprimir"), Triple("original", "Original", "Sin convertir")
)
private val BITRATES = listOf("320", "256", "192", "160", "128", "96")

fun currentTree(context: Context): String? {
    val t = Prefs.destTreeUri
    if (t.isBlank()) return null
    return if (Saver.hasTreePermission(context, t)) t else {
        Prefs.destTreeUri = ""
        null
    }
}

@Composable
fun DownloadScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val jobs by DownloadRepo.jobs.collectAsState()
    var destTree by remember { mutableStateOf(currentTree(ctx)) }
    var pendingAskDownload by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {
            }
            if (pendingAskDownload) {
                vm.download(ctx, uri.toString())
            } else {
                Prefs.destTreeUri = uri.toString()
                destTree = uri.toString()
            }
        }
        pendingAskDownload = false
    }

    val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.download(ctx, destTree) else vm.error = "Sin permiso de almacenamiento no puedo guardar en Descargas."
    }

    fun startDownload() {
        vm.canDownload()?.let { vm.error = it; return }
        vm.error = null
        when {
            vm.askFolder -> { pendingAskDownload = true; treeLauncher.launch(null) }
            destTree == null && Build.VERSION.SDK_INT < 29 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ->
                legacyPerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> vm.download(ctx, destTree)
        }
    }

    LaunchedEffect(vm.toast) {
        vm.toast?.let { Actions.toast(ctx, it); vm.toast = null }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Encabezado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("JSUS", color = JC.Cyan, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 4.sp)
                Text("DOWNLOADER", color = JC.Text2, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 3.sp)
            }
            Spacer(Modifier.weight(1f))
            val (dot, txt) = when {
                vm.engineError != null -> JC.Err to "Error"
                vm.engineReady -> JC.Ok to "Listo"
                else -> JC.Warn to "Preparando"
            }
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, dot.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(txt, color = dot, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (!vm.engineReady && vm.engineError == null) {
            Spacer(Modifier.height(10.dp))
            JCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = JC.Cyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Preparando el motor de descargas… (solo tarda la primera vez)", color = JC.Text2, fontSize = 13.sp)
                }
            }
        }
        vm.engineError?.let {
            Spacer(Modifier.height(10.dp))
            JCard(border = JC.Err.copy(alpha = 0.5f)) { Text("No se pudo iniciar el motor: $it", color = Color(0xFFFECACA), fontSize = 13.sp) }
        }

        // URL
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.url,
            onValueChange = { vm.url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Pega aquí el link de YouTube…", color = JC.Text3) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.analyze() }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JC.Cyan, unfocusedBorderColor = JC.Line,
                focusedContainerColor = JC.Bg2, unfocusedContainerColor = JC.Bg2,
                focusedTextColor = JC.Text, unfocusedTextColor = JC.Text, cursorColor = JC.Cyan
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("📋 Pegar", Modifier.weight(1f)) {
                val t = Actions.clipboardText(ctx)
                if (t.isBlank()) Actions.toast(ctx, "El portapapeles está vacío")
                else { vm.url = t; vm.analyze(t) }
            }
            SmallButton("✕ Limpiar", Modifier.weight(1f)) { vm.url = ""; vm.info = null; vm.error = null }
            Box(
                Modifier.weight(1.3f).clip(RoundedCornerShape(7.dp)).background(JC.Cyan)
                    .clickable(enabled = !vm.loading) { vm.analyze() }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) { Text(if (vm.loading) "Analizando…" else "🔍 Analizar", color = Color(0xFF001018), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
        }

        vm.error?.let {
            Spacer(Modifier.height(10.dp))
            JCard(border = JC.Err.copy(alpha = 0.5f)) { Text(it, color = Color(0xFFFECACA), fontSize = 13.sp) }
        }

        if (vm.loading) {
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = JC.Cyan, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Analizando video…", color = JC.Text2)
            }
        }

        vm.info?.let { info ->
            Spacer(Modifier.height(12.dp))
            ResultSection(
                vm = vm, info = info, destLabel = if (vm.askFolder) "Te preguntaré al descargar" else Saver.treeLabel(ctx, destTree),
                moreOpen = moreOpen, onToggleMore = { moreOpen = !moreOpen },
                onChangeDest = { pendingAskDownload = false; treeLauncher.launch(null) },
                onResetDest = { Prefs.destTreeUri = ""; destTree = null },
                hasCustomDest = destTree != null,
                onDownload = { startDownload() }
            )
        }

        // Descargas
        if (jobs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("DESCARGAS", color = JC.Text2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                Text("Limpiar terminadas", color = JC.Text2, fontSize = 12.sp, modifier = Modifier.clickable { DownloadRepo.clearFinished() })
            }
            jobs.forEach { j ->
                JobCard(j, ctx)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (vm.info == null && !vm.loading && jobs.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⬇", fontSize = 40.sp, color = JC.Cyan)
                Spacer(Modifier.height(8.dp))
                Text("Pega un link o compártelo desde YouTube", color = JC.Text2, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("YouTube → Compartir → JSUS Downloader", color = JC.Text3, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultSection(
    vm: MainViewModel,
    info: MediaInfo,
    destLabel: String,
    moreOpen: Boolean,
    onToggleMore: () -> Unit,
    onChangeDest: () -> Unit,
    onResetDest: () -> Unit,
    hasCustomDest: Boolean,
    onDownload: () -> Unit
) {
    val s = vm.sel
    val isVideo = vm.tab == "video"

    // Tarjeta
    Row {
        Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(8.dp)).background(JC.Bg3)) {
            AsyncImage(model = info.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(72.dp))
            if (info.duration > 0) {
                Text(
                    Fmt.duration(info.duration.toLong()), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000)).padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(info.title, color = JC.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val sub = buildList {
                if (info.kind == "playlist") add("Playlist · ${info.playlistCount} videos")
                if (info.uploader.isNotBlank()) add(info.uploader)
                Fmt.views(info.views).takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            Text(sub, color = JC.Text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    // Playlist
    if (info.playlistTitle != null && info.playlistCount > 0) {
        Spacer(Modifier.height(10.dp))
        JCard(border = JC.Violet.copy(alpha = 0.5f)) {
            ToggleRow(
                title = if (info.kind == "playlist") "Descargar la playlist completa" else "Descargar toda la playlist",
                sub = "\"${info.playlistTitle}\" · ${info.playlistCount} videos",
                checked = s.playlist
            ) { vm.sel = s.copy(playlist = it) }
        }
    }

    // Tabs
    Spacer(Modifier.height(12.dp))
    Segmented(listOf("video" to "🎬 Video", "audio" to "🎵 Solo audio"), vm.tab) { vm.tab = it }

    if (isVideo) {
        val list = if (info.qualities.isNotEmpty() && !s.playlist) info.qualities else GENERIC_QUALITIES
        val limit = if (s.h264 && s.container != "webm") info.h264Max else Int.MAX_VALUE
        SectionLabel("Calidad", hint = if (limit != Int.MAX_VALUE) "H.264 llega hasta ${Fmt.resLabel(limit)}" else "")
        ChipGrid(list.withIndex().toList(), 4) { (idx, q), mod ->
            val value = if (q.height == 0) "best" else q.height.toString()
            val sub = when {
                q.size > 0 -> Fmt.size(q.size)
                q.height == 0 -> "La mejor"
                q.height >= 720 -> "HD"
                else -> "SD"
            }
            JChip(
                title = q.label, sub = sub, selected = s.quality == value, modifier = mod,
                enabled = q.height == 0 || q.height <= limit,
                badge = if (idx == 0 && q.height > 0) "MAX" else "",
                corner = if (q.fps > 30) q.fps.toString() else ""
            ) { vm.sel = s.copy(quality = value) }
        }

        SectionLabel("Formato")
        Segmented(listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WEBM"), s.container) { vm.sel = s.copy(container = it) }
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            "Compatibilidad máxima", checked = s.h264, enabled = s.container != "webm",
            sub = "H.264 · TV y celulares viejos · hasta 1080p"
        ) { on ->
            var q = s.quality
            if (on && q != "best" && (q.toIntOrNull() ?: 0) > info.h264Max) {
                q = list.firstOrNull { it.height in 1..info.h264Max }?.height?.toString() ?: "best"
            }
            vm.sel = s.copy(h264 = on, quality = q)
        }
    } else {
        SectionLabel("Formato")
        ChipGrid(AUDIO_FORMATS, 3) { (v, t, sub), mod ->
            JChip(title = t, sub = sub, selected = s.audioFormat == v, modifier = mod) { vm.sel = s.copy(audioFormat = v) }
        }
        if (s.audioFormat in setOf("mp3", "m4a", "opus")) {
            SectionLabel("Calidad", hint = if (info.maxAbr > 0) "Fuente original: ~${info.maxAbr} kbps" else "")
            ChipGrid(BITRATES, 3) { b, mod ->
                val est = if (info.duration > 0) Fmt.size(info.duration.toLong() * b.toLong() * 125L) else ""
                JChip(title = b, sub = if (est.isNotEmpty()) "kbps · $est" else "kbps", selected = s.bitrate == b, modifier = mod) {
                    vm.sel = s.copy(bitrate = b)
                }
            }
        }
    }

    // Más opciones
    Spacer(Modifier.height(12.dp))
    JCard {
        Column {
            Row(Modifier.fillMaxWidth().clickable { onToggleMore() }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (moreOpen) "▾" else "▸", color = JC.Cyan, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text("Más opciones", color = JC.Text2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (moreOpen) {
                Spacer(Modifier.height(6.dp))
                ToggleRow("Subtítulos", checked = s.subtitles && isVideo, enabled = isVideo, sub = "Idiomas: ${Prefs.subLangs}") { vm.sel = s.copy(subtitles = it) }
                val thumbOk = if (isVideo) s.container != "webm" else s.audioFormat != "wav"
                ToggleRow("Portada (miniatura)", checked = s.embedThumbnail && thumbOk, enabled = thumbOk) { vm.sel = s.copy(embedThumbnail = it) }
                ToggleRow("Quitar patrocinios", checked = s.sponsorBlock, sub = "SponsorBlock") { vm.sel = s.copy(sponsorBlock = it) }
                if (!s.playlist) {
                    Text("RECORTAR (opcional)", color = JC.Text2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimeField(s.start, "Inicio 0:00", Modifier.weight(1f)) { vm.sel = s.copy(start = it) }
                        Text("  →  ", color = JC.Text3)
                        TimeField(s.end, "Fin 3:45", Modifier.weight(1f)) { vm.sel = s.copy(end = it) }
                    }
                }
            }
        }
    }

    // Destino
    Spacer(Modifier.height(12.dp))
    JCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📁", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(destLabel, color = if (vm.askFolder) JC.Text3 else JC.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (hasCustomDest && !vm.askFolder) {
                    SmallButton("Por defecto") { onResetDest() }
                    Spacer(Modifier.width(6.dp))
                }
                SmallButton("Cambiar", color = JC.Cyan, border = JC.Cyan.copy(alpha = 0.4f)) { onChangeDest() }
            }
            ToggleRow("Preguntar dónde guardar cada vez", checked = vm.askFolder) {
                vm.askFolder = it
                Prefs.askEachTime = it
            }
        }
    }

    // Botón
    Spacer(Modifier.height(14.dp))
    val baseText = if (isVideo) {
        val q = if (s.quality == "best") "máxima calidad" else Fmt.resLabel(s.quality.toIntOrNull() ?: 0)
        "Descargar video $q · ${s.container.uppercase()}"
    } else {
        when (s.audioFormat) {
            "original" -> "Descargar audio original"
            "mp3", "m4a", "opus" -> "Descargar ${s.audioFormat.uppercase()} ${s.bitrate} kbps"
            else -> "Descargar ${s.audioFormat.uppercase()}"
        }
    }
    val btn = if (s.playlist) "$baseText · ${info.playlistCount} videos" else baseText
    GradientButton(btn, enabled = vm.engineReady) { onDownload() }
}

@Composable
private fun TimeField(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    val bad = !Fmt.validTime(value)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(hint, color = JC.Text3, fontSize = 13.sp) },
        isError = bad,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = JC.Cyan, unfocusedBorderColor = JC.Line,
            focusedContainerColor = JC.Bg3, unfocusedContainerColor = JC.Bg3,
            focusedTextColor = JC.Text, unfocusedTextColor = JC.Text, cursorColor = JC.Cyan
        )
    )
}

@Composable
fun JobCard(j: DlJob, ctx: Context) {
    val border = when (j.status) {
        JobStatus.DONE -> JC.Ok.copy(alpha = 0.4f)
        JobStatus.ERROR -> JC.Err.copy(alpha = 0.4f)
        else -> JC.Line
    }
    JCard(border = border) {
        Row {
            AsyncImage(
                model = j.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(60.dp).height(38.dp).clip(RoundedCornerShape(6.dp)).background(JC.Bg3)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(j.title, color = JC.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${j.label} · ${j.destLabel}", color = JC.Text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (j.status.active) {
                    Spacer(Modifier.height(6.dp))
                    if (j.status == JobStatus.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { (j.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
                            color = JC.Cyan, trackColor = JC.Bg4
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
                            color = JC.Violet, trackColor = JC.Bg4
                        )
                    }
                }

                val pl = if (j.plCount > 0) "Video ${j.plIndex}/${j.plCount} · " else ""
                val (status, color) = when (j.status) {
                    JobStatus.QUEUED -> "⏳ En cola…" to JC.Text2
                    JobStatus.PREPARING -> "Preparando…" to JC.Text2
                    JobStatus.DOWNLOADING -> {
                        val what = if (j.opts.type == "audio") "Audio" else if (j.part > 1) "Audio" else "Video"
                        "$pl$what ${j.percent.toInt()}%" to JC.Text2
                    }
                    JobStatus.PROCESSING, JobStatus.SAVING -> "$pl${j.stage}" to JC.Text2
                    JobStatus.DONE -> (if (j.outputs.size > 1) "✓ Completado · ${j.outputs.size} archivos" else "✓ Completado") to JC.Ok
                    JobStatus.ERROR -> "✕ ${j.error ?: "Error"}" to Color(0xFFFCA5A5)
                    JobStatus.CANCELED -> (j.error ?: "Cancelado") to JC.Text3
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(status, color = color, fontSize = 12.sp, fontWeight = if (j.status == JobStatus.DONE) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (j.status == JobStatus.DOWNLOADING) {
                        val meta = listOf(j.speed, if (j.eta > 0) "quedan ${Fmt.duration(j.eta)}" else "", j.total).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(meta, color = JC.Text3, fontSize = 11.sp, maxLines = 1)
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (j.status.active) {
                        SmallButton("✕ Cancelar", color = Color(0xFFFCA5A5), border = JC.Err.copy(alpha = 0.4f)) { DownloadRepo.cancel(j.id) }
                    } else {
                        val single = j.outputs.singleOrNull()
                        if (j.status == JobStatus.DONE && single != null) {
                            SmallButton("▶ Abrir", color = JC.Cyan) { Actions.openFile(ctx, single) }
                            SmallButton("↗ Compartir") { Actions.shareFile(ctx, single) }
                        }
                        SmallButton("Quitar") { DownloadRepo.remove(j.id) }
                    }
                }
            }
        }
    }
}
