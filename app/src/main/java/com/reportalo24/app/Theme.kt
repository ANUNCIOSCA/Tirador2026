package com.reportalo24.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta de marca Repórtalo 24 (teal + rojo), igual que el generador.
val R24Teal = Color(0xFF1A8B8B)
val R24TealDark = Color(0xFF0F5A5A)
val R24TealSoft = Color(0xFFE6F2F2)
val R24Red = Color(0xFFD61F2C)
val R24RedSoft = Color(0xFFFDECEA)
val R24Green = Color(0xFF27AE60)
val R24Background = Color(0xFFF3F6F6)
val R24Surface = Color(0xFFFFFFFF)
val R24SurfaceAlt = Color(0xFFEDF3F3)
val R24Text = Color(0xFF16323A)
val R24Muted = Color(0xFF5B7373)
val R24Border = Color(0xFFDCE6E6)

private val R24ColorScheme: ColorScheme = lightColorScheme(
    primary = R24Teal,
    onPrimary = Color.White,
    primaryContainer = R24TealSoft,
    onPrimaryContainer = R24TealDark,
    secondary = R24Red,
    onSecondary = Color.White,
    secondaryContainer = R24RedSoft,
    onSecondaryContainer = R24Red,
    background = R24Background,
    onBackground = R24Text,
    surface = R24Surface,
    onSurface = R24Text,
    surfaceVariant = R24SurfaceAlt,
    onSurfaceVariant = R24Muted,
    surfaceContainer = R24Surface,
    surfaceContainerLow = R24Surface,
    surfaceContainerHigh = R24SurfaceAlt,
    error = R24Red,
    outline = R24Border,
    outlineVariant = R24Border,
)

private val R24Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun Reportalo24Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = R24ColorScheme, shapes = R24Shapes, content = content)
}
