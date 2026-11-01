package com.example.intraplay.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =========================================================================
// 📺 INTRAPLAY FAMILY THEME (Signature Intra Purple Ecosystem)
// =========================================================================

// Deep Midnight OLED Violet-Black Backgrounds
val TvBackground = Color(0xFF0A0812)
val TvSurface = Color(0xFF151022)
val TvSurfaceVariant = Color(0xFF211A35)

// Electric Intra Violet / Purple Focus Glow for TV D-Pad & Remote navigation
val TvFocusBorder = Color(0xFFA855F7)
val IntraPurple = Color(0xFF6741A8)
val TvAccentPurple = Color(0xFFC084FC)
val TvAccentCyan = TvAccentPurple // Kept as alias for backward compatibility

// Tonal Selected Card and Border Colors
val TvSelectedCard = Color(0xFF2E204E)
val TvCardBorder = Color(0xFF382B54)

// High-Contrast TV Typography
val TvTextPrimary = Color(0xFFF5F3FF)
val TvTextSecondary = Color(0xFFB8A4D9)
val TvFavoriteGold = Color(0xFFFBBF24)

private val TvColorScheme = darkColorScheme(
    primary = TvFocusBorder,
    onPrimary = Color.White,
    secondary = TvAccentPurple,
    background = TvBackground,
    surface = TvSurface,
    surfaceVariant = TvSurfaceVariant,
    onBackground = TvTextPrimary,
    onSurface = TvTextPrimary,
    onSurfaceVariant = TvTextSecondary
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        content = content
    )
}
