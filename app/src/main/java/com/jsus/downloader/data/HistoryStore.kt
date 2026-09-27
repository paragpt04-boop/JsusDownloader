package com.jsus.downloader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Historial de descargas guardado en history.json */
object HistoryStore {
    private lateinit var file: File
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items

    fun init(context: Context) {
        file = File(context.filesDir, "history.json")
        _items.value = load()
    }

    @Synchronized
    fun add(item: HistoryItem) {
        _items.value = (listOf(item) + _items.value).take(300)
        save()
    }

    @Synchronized
    fun remove(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
        save()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        save()
    }

    private fun load(): List<HistoryItem> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val outs = o.optJSONArray("outputs") ?: JSONArray()
                HistoryItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    label = o.optString("label"),
                    url = o.optString("url"),
                    thumbnail = o.optString("thumbnail"),
                    date = o.optLong("date"),
                    outputs = (0 until outs.length()).map { j ->
                        val f = outs.getJSONObject(j)
                        OutFile(f.optString("name"), f.optString("uri"), f.optString("mime"))
                    }
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save() {
        try {
            val arr = JSONArray()
            _items.value.forEach { h ->
                val outs = JSONArray()
                h.outputs.forEach { f -> outs.put(JSONObject().put("name", f.name).put("uri", f.uri).put("mime", f.mime)) }
                arr.put(
                    JSONObject()
                        .put("id", h.id).put("title", h.title).put("label", h.label)
                        .put("url", h.url).put("thumbnail", h.thumbnail).put("date", h.date)
                        .put("outputs", outs)
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
