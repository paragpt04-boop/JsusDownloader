package com.jsus.downloader.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.reflect.KProperty

/** Ajustes guardados de la app (SharedPreferences). */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("jsus_prefs", Context.MODE_PRIVATE)
    }

    private class Str(val key: String, val def: String) {
        operator fun getValue(thisRef: Any?, p: KProperty<*>): String = sp.getString(key, def) ?: def
        operator fun setValue(thisRef: Any?, p: KProperty<*>, v: String) { sp.edit().putString(key, v).apply() }
    }

    private class Bool(val key: String, val def: Boolean) {
        operator fun getValue(thisRef: Any?, p: KProperty<*>): Boolean = sp.getBoolean(key, def)
        operator fun setValue(thisRef: Any?, p: KProperty<*>, v: Boolean) { sp.edit().putBoolean(key, v).apply() }
    }

    private class IntP(val key: String, val def: Int) {
        operator fun getValue(thisRef: Any?, p: KProperty<*>): Int = sp.getInt(key, def)
        operator fun setValue(thisRef: Any?, p: KProperty<*>, v: Int) { sp.edit().putInt(key, v).apply() }
    }

    // Video
    var videoQuality: String by Str("videoQuality", "best")      // best | 2160 | 1440 | 1080 | 720 ...
    var videoContainer: String by Str("videoContainer", "mp4")   // mp4 | mkv | webm
    var h264: Boolean by Bool("h264", false)
    var embedChapters: Boolean by Bool("embedChapters", true)

    // Audio
    var audioFormat: String by Str("audioFormat", "mp3")         // mp3 | m4a | opus | flac | wav | original
    var audioBitrate: String by Str("audioBitrate", "320")

    // Extras
    var subtitles: Boolean by Bool("subtitles", false)
    var subLangs: String by Str("subLangs", "es,en")
    var autoSubs: Boolean by Bool("autoSubs", true)
    var embedSubs: Boolean by Bool("embedSubs", true)
    var embedThumbnail: Boolean by Bool("embedThumbnail", true)
    var embedMetadata: Boolean by Bool("embedMetadata", true)
    var sponsorBlock: Boolean by Bool("sponsorBlock", false)

    // Archivos y destino
    var filenameTemplate: String by Str("filenameTemplate", "%(title)s")
    var playlistSubfolder: Boolean by Bool("playlistSubfolder", true)
    var destTreeUri: String by Str("destTreeUri", "")            // vacío = Descargas/JSUS Downloader
    var askEachTime: Boolean by Bool("askEachTime", false)
    var askName: Boolean by Bool("askName", true)                // preguntar nombre y carpeta al descargar
    var recentFolders: String by Str("recentFolders", "")       // últimas subcarpetas usadas, separadas por \n
    var reelFit: String by Str("reelFit", "crop")               // crop | blur

    fun recentFolderList(): List<String> = recentFolders.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun addRecentFolder(f: String) {
        if (f.isBlank()) return
        recentFolders = (listOf(f) + recentFolderList().filterNot { it.equals(f, ignoreCase = true) }).take(8).joinToString("\n")
    }

    // Red
    var speedLimit: String by Str("speedLimit", "")              // "" = sin límite, ej. 2M
    var maxConcurrent: Int by IntP("maxConcurrent", 2)
    var notifications: Boolean by Bool("notifications", true)

    fun resetAll() { sp.edit().clear().apply() }
}
