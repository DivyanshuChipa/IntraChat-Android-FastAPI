package com.example.intraplay.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TvBackground = Color(0xFF0B0E14)
val TvSurface = Color(0xFF161B22)
val TvSurfaceVariant = Color(0xFF21262D)
val TvFocusBorder = Color(0xFF3B82F6)
val TvAccentCyan = Color(0xFF38BDF8)
val TvTextPrimary = Color(0xFFF0F6FC)
val TvTextSecondary = Color(0xFF8B949E)
val TvFavoriteGold = Color(0xFFFBBF24)

private val TvColorScheme = darkColorScheme(
    primary = TvFocusBorder,
    onPrimary = Color.White,
    secondary = TvAccentCyan,
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
