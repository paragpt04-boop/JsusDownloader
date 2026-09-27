package com.jsus.downloader.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jsus.downloader.data.OutFile

object Actions {

    fun openFile(context: Context, f: OutFile) {
        if (f.uri.isBlank()) return toast(context, "El archivo está en Descargas/JSUS Downloader")
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(f.uri), f.mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent.createChooser(i, "Abrir con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast(context, "No hay una app para abrir este archivo")
        } catch (_: Exception) {
            toast(context, "No se pudo abrir (¿lo moviste o borraste?)")
        }
    }

    fun shareFile(context: Context, f: OutFile) {
        if (f.uri.isBlank()) return
        val i = Intent(Intent.ACTION_SEND).setType(f.mime.ifBlank { "*/*" })
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(f.uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(i, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast(context, "No se pudo compartir")
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    fun clipboardText(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
    }

    fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
