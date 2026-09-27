package com.jsus.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, hint: String = "") {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(text.uppercase(), color = JC.Text2, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        Spacer(Modifier.weight(1f))
        if (hint.isNotEmpty()) Text(hint, color = JC.Text3, fontSize = 11.sp)
    }
}

@Composable
fun JCard(modifier: Modifier = Modifier, border: Color = JC.Line, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JC.Bg2)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) { content() }
}

@Composable
fun JChip(
    title: String,
    sub: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: String = "",
    corner: String = "",
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(shape)
            .background(if (selected) JC.Cyan.copy(alpha = 0.12f) else JC.Bg2)
            .border(1.dp, if (selected) JC.Cyan else JC.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = if (selected) JC.Cyan else JC.Text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, color = JC.Text3, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
        if (badge.isNotEmpty()) {
            Text(
                badge, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(4.dp)).background(JC.Grad).padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        if (corner.isNotEmpty()) {
            Text(corner, color = JC.Warn, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp))
        }
    }
}

/** Rejilla de chips con N columnas (sin APIs experimentales). */
@Composable
fun <T> ChipGrid(items: List<T>, columns: Int, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(JC.Bg2)
            .border(1.dp, JC.Line, RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) JC.Bg4 else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (sel) JC.Cyan else JC.Text2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, sub: String = "", enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = JC.Text, fontSize = 14.sp)
            if (sub.isNotEmpty()) Text(sub, color = JC.Text3, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JC.Cyan, checkedTrackColor = JC.Cyan.copy(alpha = 0.3f),
                uncheckedThumbColor = JC.Text3, uncheckedTrackColor = JC.Bg4, uncheckedBorderColor = JC.Line
            )
        )
    }
}

/** Fila que abre un diálogo con opciones (reemplazo simple de un Select). */
@Composable
fun SelectRow(title: String, options: List<Pair<String, String>>, value: String, sub: String = "", onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value }?.second ?: value
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = JC.Text, fontSize = 14.sp)
            if (sub.isNotEmpty()) Text(sub, color = JC.Text3, fontSize = 11.sp)
        }
        Text(current, color = JC.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        Text("  ▾", color = JC.Text3, fontSize = 13.sp)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = JC.Bg2,
            title = { Text(title, color = JC.Text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (v, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(v); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = v == value, onClick = { onSelect(v); open = false }, colors = RadioButtonDefaults.colors(selectedColor = JC.Cyan))
                            Text(label, color = JC.Text, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar", color = JC.Cyan) } }
        )
    }
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShape(12.dp))
            .background(JC.Grad).clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("⬇  $text", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, color: Color = JC.Text2, border: Color = JC.Line, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(7.dp)).background(JC.Bg4).border(1.dp, border, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
