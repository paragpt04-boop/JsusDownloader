package com.jsus.downloader.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jsus.downloader.App
import com.jsus.downloader.data.DlOptions
import com.jsus.downloader.data.Fmt
import com.jsus.downloader.data.MediaInfo
import com.jsus.downloader.data.Prefs
import com.jsus.downloader.download.DownloadRepo
import com.jsus.downloader.download.Engine
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    var url by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<MediaInfo?>(null)
    var tab by mutableStateOf("video")
    var sel by mutableStateOf(DlOptions())
    var askFolder by mutableStateOf(Prefs.askEachTime)
    var engineReady by mutableStateOf(App.ready.isCompleted)
    var engineError by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    private var lastAnalyzed = ""

    init {
        viewModelScope.launch {
            try {
                App.ready.await()
                engineReady = true
            } catch (e: Throwable) {
                engineError = App.initError ?: e.message
            }
        }
    }

    fun extractUrl(text: String): String? =
        Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')

    fun analyze(input: String = url) {
        val u = extractUrl(input.trim()) ?: run {
            error = if (input.isBlank()) "Pega primero un link." else "Eso no parece un link válido."
            return
        }
        url = u
        if (loading) return
        loading = true
        error = null
        info = null
        viewModelScope.launch {
            try {
                val i = Engine.fetchInfo(u)
                info = i
                lastAnalyzed = u
                sel = initialSelection(i)
            } catch (e: Throwable) {
                error = Engine.friendlyError(e.message ?: e.toString())
            } finally {
                loading = false
            }
        }
    }

    /** Llega un link compartido desde YouTube u otra app. */
    fun onShared(text: String) {
        val u = extractUrl(text) ?: return
        if (u == lastAnalyzed && info != null) return
        url = u
        analyze(u)
    }

    private fun initialSelection(i: MediaInfo): DlOptions {
        var quality = "best"
        val qs = i.qualities
        if (qs.isNotEmpty()) {
            quality = if (Prefs.videoQuality == "best") qs.first().height.toString() else {
                val want = Prefs.videoQuality.toIntOrNull() ?: 99999
                (qs.firstOrNull { it.height <= want } ?: qs.last()).height.toString()
            }
        } else if (Prefs.videoQuality != "best") quality = Prefs.videoQuality

        return DlOptions(
            type = tab,
            quality = quality,
            container = Prefs.videoContainer,
            h264 = Prefs.h264,
            audioFormat = Prefs.audioFormat,
            bitrate = Prefs.audioBitrate,
            subtitles = Prefs.subtitles,
            embedThumbnail = Prefs.embedThumbnail,
            sponsorBlock = Prefs.sponsorBlock,
            playlist = i.kind == "playlist",
            start = "", end = ""
        )
    }

    fun canDownload(): String? {
        if (info == null) return "Analiza un video primero."
        if (!Fmt.validTime(sel.start) || !Fmt.validTime(sel.end)) return "Revisa los tiempos del recorte (ejemplo: 1:30 o 01:02:03)."
        return null
    }

    fun download(context: Context, destTree: String?) {
        val i = info ?: return
        val s = sel.copy(type = tab, subtitles = tab == "video" && sel.subtitles)
        val finalUrl = if (s.playlist) url else i.webpageUrl
        val title = if (s.playlist) (i.playlistTitle ?: i.title) else i.title
        DownloadRepo.enqueue(context, finalUrl, title, i.thumbnail, s, destTree)
        toast = "Descarga iniciada ✓"
    }
}
