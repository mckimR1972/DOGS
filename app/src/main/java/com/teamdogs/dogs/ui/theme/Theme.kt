package com.teamdogs.dogs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7DC2A8),
    onPrimary = Color(0xFF102D23),
    primaryContainer = Color(0xFF253E32),
    onPrimaryContainer = Color(0xFFD2EBDF),
    secondaryContainer = Color(0xFF253E32),
    onSecondaryContainer = Color(0xFFD2EBDF),
    background = Color(0xFF111613),
    surface = Color(0xFF1D2420),
    onBackground = Color(0xFFE6ECE8),
    onSurface = Color(0xFFE6ECE8),
    onSurfaceVariant = Color(0xFFB5C1B9),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF27634F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EEE8),
    onPrimaryContainer = Color(0xFF244D3C),
    secondaryContainer = Color(0xFFE4EEE8),
    onSecondaryContainer = Color(0xFF244D3C),
    background = Color(0xFFF4F6F3),
    surface = Color.White,
    onBackground = Color(0xFF19231E),
    onSurface = Color(0xFF19231E),
    onSurfaceVariant = Color(0xFF59675E),
)

@Composable
fun DOGSTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
