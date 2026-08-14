package com.openconvert.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF111111)
val Muted = Color(0xFF6B6B6B)
val Canvas = Color(0xFFFFFFFF)
val SurfaceSoft = Color(0xFFF7F7F8)
val Border = Color(0xFFEAEAEA)

private val OpenConvertColors = lightColorScheme(
    primary = Ink,
    onPrimary = Canvas,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = Muted,
    outline = Border,
    outlineVariant = Border,
)

@Composable
fun OpenConvertTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenConvertColors,
        content = content,
    )
}

