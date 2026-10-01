package com.jsus.downloader.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsus.downloader.App
import com.jsus.downloader.BuildConfig
import com.jsus.downloader.data.Prefs
import com.jsus.downloader.download.Saver
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Contador para refrescar la pantalla al cambiar algo
    var rev by remember { mutableIntStateOf(0) }
    fun bump() { rev++ }

    var destTree by remember { mutableStateOf(currentTree(ctx)) }
    var ytVersion by remember { mutableStateOf("…") }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ytVersion = withContext(Dispatchers.IO) {
            try { App.ready.await(); YoutubeDL.getInstance().versionName(ctx) ?: "desconocida" } catch (_: Throwable) { "no disponible" }
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (_: Exception) {
            }
            Prefs.destTreeUri = uri.toString()
            destTree = uri.toString()
        }
    }

    val scroll = rememberScrollState()

    // key(rev): al cambiar un ajuste se redibuja todo con los valores nuevos de Prefs
    key(rev) {
    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Ajustes", color = JC.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text("Valores por defecto al analizar un video", color = JC.Text3, fontSize = 12.sp)

        // Destino
        Group("📁 Dónde guardar") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text(Saver.treeLabel(ctx, destTree), color = JC.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (destTree != null) {
                    SmallButton("Por defecto") { Prefs.destTreeUri = ""; destTree = null }
                    Spacer(Modifier.width(6.dp))
                }
                SmallButton("Elegir", color = JC.Cyan, border = JC.Cyan.copy(alpha = 0.4f)) { treeLauncher.launch(null) }
            }
            ToggleRow("Preguntar nombre y carpeta al descargar", Prefs.askName, sub = "Para organizar tus archivos (Reels, temas, etc.)") { Prefs.askName = it; bump() }
            ToggleRow("Preguntar dónde guardar cada vez", Prefs.askEachTime) { Prefs.askEachTime = it; bump() }
            ToggleRow("Playlists en su propia carpeta", Prefs.playlistSubfolder, sub = "Numeradas 001, 002…") { Prefs.playlistSubfolder = it; bump() }
        }

        // Video
        Group("🎬 Video") {
            SelectRow(
                "Calidad preferida",
                listOf("best" to "Máxima", "2160" to "4K", "1440" to "2K", "1080" to "1080p", "720" to "720p", "480" to "480p", "360" to "360p (ahorra datos)", "240" to "240p"),
                Prefs.videoQuality
            ) { Prefs.videoQuality = it; bump() }
            SelectRow(
                "Formato",
                listOf("mp4" to "MP4 (recomendado)", "mkv" to "MKV", "webm" to "WEBM"),
                Prefs.videoContainer
            ) { Prefs.videoContainer = it; bump() }
            ToggleRow("Compatibilidad máxima H.264", Prefs.h264, sub = "Para TV y celulares viejos · hasta 1080p") { Prefs.h264 = it; bump() }
            ToggleRow("Incrustar capítulos", Prefs.embedChapters) { Prefs.embedChapters = it; bump() }
            SelectRow(
                "Encuadre del modo Reel",
                listOf("crop" to "Llenar pantalla", "blur" to "Completo + fondo desenfocado"),
                Prefs.reelFit
            ) { Prefs.reelFit = it; bump() }
        }

        // Audio
        Group("🎵 Audio") {
            SelectRow(
                "Formato",
                listOf("mp3" to "MP3", "m4a" to "M4A (AAC)", "opus" to "OPUS", "flac" to "FLAC", "wav" to "WAV", "original" to "Original"),
                Prefs.audioFormat
            ) { Prefs.audioFormat = it; bump() }
            SelectRow(
                "Bitrate",
                listOf("320", "256", "192", "160", "128", "96").map { it to "$it kbps" },
                Prefs.audioBitrate,
                sub = "MP3 / M4A / OPUS"
            ) { Prefs.audioBitrate = it; bump() }
            Text("YouTube entrega el audio a ~128–160 kbps. Subir a 320 no agrega calidad real.", color = JC.Text3, fontSize = 11.sp)
        }

        // Extras
        Group("✨ Extras") {
            ToggleRow("Portada (miniatura)", Prefs.embedThumbnail) { Prefs.embedThumbnail = it; bump() }
            ToggleRow("Metadatos (título, artista…)", Prefs.embedMetadata) { Prefs.embedMetadata = it; bump() }
            ToggleRow("Quitar patrocinios (SponsorBlock)", Prefs.sponsorBlock) { Prefs.sponsorBlock = it; bump() }
            HorizontalDivider(color = JC.Line, modifier = Modifier.padding(vertical = 6.dp))
            ToggleRow("Descargar subtítulos", Prefs.subtitles) { Prefs.subtitles = it; bump() }
            SettingField("Idiomas de subtítulos", Prefs.subLangs, "es,en") { Prefs.subLangs = it }
            ToggleRow("Subtítulos automáticos si no hay manuales", Prefs.autoSubs) { Prefs.autoSubs = it; bump() }
            ToggleRow("Incrustarlos en el video", Prefs.embedSubs, sub = "Si no, archivo .srt aparte") { Prefs.embedSubs = it; bump() }
        }

        // Nombre
        Group("🏷️ Nombre del archivo") {
            val presets = listOf(
                "%(title)s" to "Título",
                "%(uploader)s - %(title)s" to "Canal - Título",
                "%(title)s [%(id)s]" to "Título [ID]",
                "%(upload_date>%Y-%m-%d)s - %(title)s" to "Fecha - Título"
            )
            SelectRow("Nombre automático", presets, Prefs.filenameTemplate, sub = "El que sale sugerido al descargar") { Prefs.filenameTemplate = it; bump() }
        }

        // Red
        Group("🌐 Red") {
            SelectRow(
                "Límite de velocidad",
                listOf("" to "Sin límite", "500K" to "500 KB/s", "1M" to "1 MB/s", "2M" to "2 MB/s", "5M" to "5 MB/s", "10M" to "10 MB/s"),
                Prefs.speedLimit,
                sub = "Para no acaparar la conexión"
            ) { Prefs.speedLimit = it; bump() }
            SelectRow(
                "Descargas al mismo tiempo",
                (1..5).map { it.toString() to it.toString() },
                Prefs.maxConcurrent.toString()
            ) { Prefs.maxConcurrent = it.toInt(); bump() }
            ToggleRow("Notificación al terminar", Prefs.notifications) { Prefs.notifications = it; bump() }
        }

        // Motor
        Group("🛠️ Motor yt-dlp") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Versión instalada", color = JC.Text, fontSize = 14.sp)
                    Text(ytVersion, color = JC.Cyan, fontSize = 13.sp)
                }
                if (updating) CircularProgressIndicator(color = JC.Cyan, strokeWidth = 2.dp, modifier = Modifier.width(20.dp).height(20.dp))
                else SmallButton("⬆ Actualizar", color = JC.Cyan, border = JC.Cyan.copy(alpha = 0.4f)) {
                    updating = true
                    updateMsg = null
                    scope.launch {
                        val msg = withContext(Dispatchers.IO) {
                            try {
                                App.ready.await()
                                val st = YoutubeDL.getInstance().updateYoutubeDL(ctx)
                                ytVersion = YoutubeDL.getInstance().versionName(ctx) ?: ytVersion
                                when (st?.name) {
                                    "ALREADY_UP_TO_DATE" -> "Ya tienes la última versión."
                                    "DONE" -> "¡Actualizado!"
                                    else -> "Listo."
                                }
                            } catch (e: Throwable) {
                                "No se pudo actualizar: ${e.message ?: e}"
                            }
                        }
                        updateMsg = msg
                        updating = false
                    }
                }
            }
            updateMsg?.let { Text(it, color = JC.Text2, fontSize = 12.sp) }
            Text("Si YouTube da errores (403, \"not a bot\", calidades que faltan), actualiza aquí.", color = JC.Text3, fontSize = 11.sp)
        }

        Group("ℹ️ Acerca de") {
            Text("JSUS Downloader v${BuildConfig.VERSION_NAME}", color = JC.Text, fontSize = 14.sp)
            Text("Descarga solo contenido tuyo o que tengas derecho a guardar.", color = JC.Text3, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            SmallButton("Ver proyecto en GitHub") { Actions.openUrl(ctx, "https://github.com/paragpt04-boop/JsusDownloader") }
            Spacer(Modifier.height(8.dp))
            SmallButton("Restaurar ajustes de fábrica", color = JC.Err, border = JC.Err.copy(alpha = 0.4f)) { confirmReset = true }
        }
        Spacer(Modifier.height(24.dp))
    }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = JC.Bg2,
            title = { Text("¿Restaurar ajustes?", color = JC.Text) },
            text = { Text("Vuelven todos los valores por defecto.", color = JC.Text2) },
            confirmButton = { TextButton(onClick = { Prefs.resetAll(); destTree = null; confirmReset = false; bump() }) { Text("Restaurar", color = JC.Err) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar", color = JC.Text2) } }
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(14.dp))
    Text(title, color = JC.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
    JCard { Column { content() } }
}

@Composable
private fun SettingField(title: String, value: String, hint: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, color = JC.Text, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onChange(it.filter { c -> c.isLetterOrDigit() || c == ',' || c == '-' }) },
            singleLine = true,
            placeholder = { Text(hint, color = JC.Text3) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JC.Cyan, unfocusedBorderColor = JC.Line,
                focusedContainerColor = JC.Bg3, unfocusedContainerColor = JC.Bg3,
                focusedTextColor = JC.Text, unfocusedTextColor = JC.Text, cursorColor = JC.Cyan
            )
        )
    }
}
