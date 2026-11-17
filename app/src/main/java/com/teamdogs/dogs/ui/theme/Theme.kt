package com.teamdogs.dogs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

// Semantic layer from design/tokens.json → semantic.light / semantic.dark.
private val LightColorScheme = lightColorScheme(
    primary = Palette.Evergreen700,
    onPrimary = Palette.Neutral0,
    primaryContainer = Palette.Evergreen50,
    onPrimaryContainer = Palette.Evergreen800,
    secondary = Palette.Neutral900,
    onSecondary = Palette.Neutral0,
    secondaryContainer = Palette.Evergreen50,
    onSecondaryContainer = Palette.Evergreen800,
    tertiary = Palette.Amber700,
    onTertiary = Palette.Neutral0,
    tertiaryContainer = Palette.Amber50,
    onTertiaryContainer = Palette.Neutral900,
    background = Palette.Neutral25,
    onBackground = Palette.Neutral900,
    surface = Palette.Neutral25,
    onSurface = Palette.Neutral900,
    surfaceVariant = Palette.Neutral50,
    onSurfaceVariant = Palette.Neutral600,
    surfaceContainerLowest = Palette.Neutral0,
    surfaceContainerLow = Palette.Neutral0,
    surfaceContainer = Palette.Neutral0,
    surfaceContainerHigh = Palette.Neutral25,
    surfaceContainerHighest = Palette.Neutral50,
    inverseSurface = Palette.Neutral900,
    inverseOnSurface = Palette.Neutral0,
    outline = Palette.Neutral500,
    outlineVariant = Palette.Neutral100,
    error = Palette.Red700,
    onError = Palette.Neutral0,
    errorContainer = Palette.Red50,
    onErrorContainer = Palette.Red700,
    scrim = Palette.Neutral900,
)

private val DarkColorScheme = darkColorScheme(
    primary = Palette.Evergreen400,
    onPrimary = Palette.Evergreen900,
    primaryContainer = Palette.EvergreenD50,
    onPrimaryContainer = Palette.Evergreen300,
    secondary = Palette.NeutralD100,
    onSecondary = Palette.NeutralD900,
    secondaryContainer = Palette.EvergreenD50,
    onSecondaryContainer = Palette.Evergreen300,
    tertiary = Palette.Amber400,
    onTertiary = Palette.NeutralD900,
    tertiaryContainer = Palette.AmberD50,
    onTertiaryContainer = Palette.NeutralD100,
    background = Palette.NeutralD900,
    onBackground = Palette.NeutralD100,
    surface = Palette.NeutralD900,
    onSurface = Palette.NeutralD100,
    surfaceVariant = Palette.NeutralD800,
    onSurfaceVariant = Palette.NeutralD300,
    surfaceContainerLowest = Palette.NeutralD850,
    surfaceContainerLow = Palette.NeutralD850,
    surfaceContainer = Palette.NeutralD850,
    surfaceContainerHigh = Palette.NeutralD800,
    surfaceContainerHighest = Palette.NeutralD750,
    inverseSurface = Palette.NeutralD100,
    inverseOnSurface = Palette.NeutralD900,
    outline = Palette.NeutralD400,
    outlineVariant = Palette.NeutralD750,
    error = Palette.Red400,
    onError = Palette.NeutralD900,
    errorContainer = Palette.RedD50,
    onErrorContainer = Palette.Red400,
    scrim = Palette.Neutral900,
)

// sm 8 · md 12 · lg 20 · dialogs/sheets 28. Buttons and chips use CircleShape (full).
private val DogsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DOGSTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDogsColors provides if (darkTheme) DarkDogsColors else LightDogsColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = DogsShapes,
            content = content,
        )
    }
}

/** Access point for DOGS-only semantic colors: `DogsTheme.colors.attention`. */
object DogsTheme {
    val colors: DogsColors
        @Composable @ReadOnlyComposable get() = LocalDogsColors.current
}
