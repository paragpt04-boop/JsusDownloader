package com.jsus.downloader.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.jsus.downloader.data.OutFile
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** Copia los archivos terminados desde la carpeta temporal al destino final. */
object Saver {

    const val DEFAULT_FOLDER = "JSUS Downloader"

    /** MIME que no provoque que Android cambie la extensión del archivo. */
    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val map = MimeTypeMap.getSingleton()
        val mime = map.getMimeTypeFromExtension(ext)
        return if (mime != null && map.getExtensionFromMimeType(mime) == ext) mime else "application/octet-stream"
    }

    fun defaultLabel(): String = "Descargas/$DEFAULT_FOLDER"

    fun treeLabel(context: Context, tree: String?): String {
        if (tree.isNullOrBlank()) return defaultLabel()
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(tree))?.name ?: "Carpeta elegida"
        } catch (_: Exception) { "Carpeta elegida" }
    }

    fun hasTreePermission(context: Context, tree: String): Boolean = try {
        val uri = Uri.parse(tree)
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    } catch (_: Exception) { false }

    suspend fun save(context: Context, files: List<File>, root: File, tree: String?): List<OutFile> {
        val out = mutableListOf<OutFile>()
        for (f in files) {
            val rel = f.parentFile?.relativeTo(root)?.path?.trim('/', '\\') ?: ""
            out += when {
                !tree.isNullOrBlank() -> saveToTree(context, f, rel, tree)
                Build.VERSION.SDK_INT >= 29 -> saveToMediaStore(context, f, rel)
                else -> saveLegacy(context, f, rel)
            }
        }
        return out
    }

    private fun saveToTree(context: Context, f: File, rel: String, tree: String): OutFile {
        val base = DocumentFile.fromTreeUri(context, Uri.parse(tree))
            ?: throw Exception("No tengo acceso a la carpeta elegida. Elígela de nuevo en Ajustes.")
        var dir: DocumentFile = base
        if (rel.isNotEmpty()) {
            for (seg in rel.split('/', '\\').filter { it.isNotBlank() }) {
                dir = dir.findFile(seg)?.takeIf { it.isDirectory } ?: dir.createDirectory(seg)
                    ?: throw Exception("No se pudo crear la carpeta \"$seg\"")
            }
        }
        val name = uniqueName(f.name) { dir.findFile(it) != null }
        val mime = mimeFor(name)
        val doc = dir.createFile(mime, name) ?: throw Exception("No se pudo crear el archivo en la carpeta elegida.")
        context.contentResolver.openOutputStream(doc.uri)?.use { os -> f.inputStream().use { it.copyTo(os, 256 * 1024) } }
            ?: throw Exception("No se pudo escribir en la carpeta elegida.")
        return OutFile(doc.name ?: name, doc.uri.toString(), mime)
    }

    private fun saveToMediaStore(context: Context, f: File, rel: String): OutFile {
        val mime = mimeFor(f.name)
        val relPath = Environment.DIRECTORY_DOWNLOADS + "/" + DEFAULT_FOLDER + if (rel.isNotEmpty()) "/$rel" else ""
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Android no dejó crear el archivo en Descargas.")
        try {
            resolver.openOutputStream(uri)?.use { os -> f.inputStream().use { it.copyTo(os, 256 * 1024) } }
                ?: throw Exception("No se pudo escribir en Descargas.")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
        var finalName = f.name
        try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) finalName = c.getString(0) ?: f.name
            }
        } catch (_: Exception) {}
        return OutFile(finalName, uri.toString(), mime)
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(context: Context, f: File, rel: String): OutFile {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DEFAULT_FOLDER + if (rel.isNotEmpty()) "/$rel" else "")
        dir.mkdirs()
        val name = uniqueName(f.name) { File(dir, it).exists() }
        val dest = File(dir, name)
        f.copyTo(dest, overwrite = false)
        val mime = mimeFor(name)
        val uri = withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<Uri?> { cont ->
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime)) { _, u ->
                    if (cont.isActive) cont.resume(u)
                }
            }
        }
        return OutFile(name, uri?.toString() ?: "", mime)
    }

    private fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val n = if (ext.isNotEmpty()) "$base ($i).$ext" else "$base ($i)"
            if (!exists(n)) return n
            i++
        }
    }
}
