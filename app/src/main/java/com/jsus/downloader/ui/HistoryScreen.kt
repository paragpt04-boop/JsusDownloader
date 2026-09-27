package com.jsus.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jsus.downloader.data.HistoryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onAgain: (String) -> Unit) {
    val ctx = LocalContext.current
    val items by HistoryStore.items.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("dd MMM · HH:mm", Locale("es")) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", color = JC.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) Text("Borrar todo", color = JC.Text2, fontSize = 13.sp, modifier = Modifier.clickable { confirm = true })
        }
        Spacer(Modifier.height(10.dp))

        if (items.isEmpty()) {
            Text("Todavía no hay descargas.", color = JC.Text2, modifier = Modifier.padding(top = 30.dp).fillMaxWidth())
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { h ->
                JCard {
                    Row {
                        AsyncImage(
                            model = h.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(64.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).background(JC.Bg3)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, color = JC.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${h.label} · ${df.format(Date(h.date))}", color = JC.Text3, fontSize = 11.sp, maxLines = 1)
                            if (h.outputs.size > 1) Text("${h.outputs.size} archivos", color = JC.Text3, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                h.outputs.firstOrNull()?.let { f ->
                                    SmallButton("▶ Abrir", color = JC.Cyan) { Actions.openFile(ctx, f) }
                                    SmallButton("↗") { Actions.shareFile(ctx, f) }
                                }
                                SmallButton("↻ Otra vez") { onAgain(h.url) }
                                SmallButton("✕") { HistoryStore.remove(h.id) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = JC.Bg2,
            title = { Text("¿Borrar el historial?", color = JC.Text) },
            text = { Text("Los archivos descargados NO se borran, solo la lista.", color = JC.Text2) },
            confirmButton = { TextButton(onClick = { HistoryStore.clear(); confirm = false }) { Text("Borrar", color = JC.Err) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar", color = JC.Text2) } }
        )
    }
}
