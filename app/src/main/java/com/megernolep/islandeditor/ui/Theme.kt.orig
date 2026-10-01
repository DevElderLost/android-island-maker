package com.megernolep.islandeditor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0D1117)
val Surf = Color(0xFF151B23)
val SurfHi = Color(0xFF1E2630)
val Outline = Color(0xFF2D3846)
val Accent = Color(0xFF5BE0B3)
val Accent2 = Color(0xFF7AA2FF)
val Warn = Color(0xFFFFB454)
val Danger = Color(0xFFFF6B6B)
val TextHi = Color(0xFFE6EDF3)
val Muted = Color(0xFF8B96A5)

private val Scheme = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF00382A),
    secondary = Accent2, onSecondary = Color(0xFF0A1B45),
    tertiary = Warn, error = Danger,
    background = Bg, onBackground = TextHi,
    surface = Surf, onSurface = TextHi,
    surfaceVariant = SurfHi, onSurfaceVariant = Muted,
    outline = Outline, outlineVariant = Outline,
)

private val Type = Typography(
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Default),
)

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    // Aplikasi ini sengaja selalu gelap.
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
