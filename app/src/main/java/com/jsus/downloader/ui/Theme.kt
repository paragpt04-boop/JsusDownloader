package com.jsus.downloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object JC {
    val Bg = Color(0xFF0A0E1A)
    val Bg2 = Color(0xFF111827)
    val Bg3 = Color(0xFF172033)
    val Bg4 = Color(0xFF1E2A42)
    val Line = Color(0xFF243149)
    val Text = Color(0xFFE6EDF7)
    val Text2 = Color(0xFF93A3BB)
    val Text3 = Color(0xFF5F6F88)
    val Cyan = Color(0xFF00D4FF)
    val Violet = Color(0xFF8B5CF6)
    val Ok = Color(0xFF10B981)
    val Err = Color(0xFFEF4444)
    val Warn = Color(0xFFF59E0B)
    val Grad = Brush.linearGradient(listOf(Cyan, Violet))
}

@Composable
fun JsusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = JC.Cyan,
            onPrimary = Color(0xFF001018),
            secondary = JC.Violet,
            background = JC.Bg,
            onBackground = JC.Text,
            surface = JC.Bg2,
            onSurface = JC.Text,
            surfaceVariant = JC.Bg3,
            onSurfaceVariant = JC.Text2,
            outline = JC.Line,
            error = JC.Err
        ),
        content = content
    )
}
