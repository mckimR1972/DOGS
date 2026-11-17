package com.teamdogs.dogs.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Primitive palette. Mirrors design/tokens.json → primitive.color. Screens must not use these directly;
// use MaterialTheme.colorScheme or DogsTheme.colors (semantic layer) instead.
internal object Palette {
    val Neutral0 = Color(0xFFFFFFFF)
    val Neutral25 = Color(0xFFF5F6F3)
    val Neutral50 = Color(0xFFEDEFEB)
    val Neutral100 = Color(0xFFE2E5E0)
    val Neutral200 = Color(0xFFC3C9C5)
    val Neutral500 = Color(0xFF687270)
    val Neutral600 = Color(0xFF59645E)
    val Neutral900 = Color(0xFF17201C)

    val NeutralD900 = Color(0xFF121614)
    val NeutralD850 = Color(0xFF1B201D)
    val NeutralD800 = Color(0xFF232925)
    val NeutralD750 = Color(0xFF2C332F)
    val NeutralD500 = Color(0xFF5A635E)
    val NeutralD400 = Color(0xFF7D8781)
    val NeutralD300 = Color(0xFFA9B3AD)
    val NeutralD100 = Color(0xFFE7ECE9)

    val Evergreen50 = Color(0xFFE5EEE9)
    val EvergreenD50 = Color(0xFF1F3A2F)
    val Evergreen300 = Color(0xFFA8DCC4)
    val Evergreen400 = Color(0xFF7CC3A4)
    val Evergreen700 = Color(0xFF24614B)
    val Evergreen800 = Color(0xFF174535)
    val Evergreen900 = Color(0xFF0E2A20)

    val Amber50 = Color(0xFFF6ECE0)
    val AmberD50 = Color(0xFF3A2C1D)
    val Amber400 = Color(0xFFE3A060)
    val Amber700 = Color(0xFF965210)

    val Red50 = Color(0xFFF7E3DF)
    val RedD50 = Color(0xFF3D211C)
    val Red400 = Color(0xFFF08A78)
    val Red700 = Color(0xFFB93A28)
}

/** Stable per-dog avatar tones (background, content). Picked by a hash of the dog id. */
internal val DogTonesLight = listOf(
    Color(0xFFE9DCC9) to Color(0xFF6B4E2A),
    Color(0xFFD9E2E8) to Color(0xFF33495A),
    Color(0xFFDDE6D5) to Color(0xFF3F5233),
    Color(0xFFEADBD8) to Color(0xFF6A3F37),
)
internal val DogTonesDark = listOf(
    Color(0xFF4A3D2C) to Color(0xFFE9DCC9),
    Color(0xFF2F3B44) to Color(0xFFD9E2E8),
    Color(0xFF36432E) to Color(0xFFDDE6D5),
    Color(0xFF4A3330) to Color(0xFFEADBD8),
)

/**
 * Semantic colors that Material 3's ColorScheme has no slot for.
 * done = a care task someone completed, attention = late task / request / change proposal.
 */
@Immutable
data class DogsColors(
    val textTertiary: Color,
    val divider: Color,
    val controlOutline: Color,
    val done: Color,
    val doneContainer: Color,
    val onDoneContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val errorContainer: Color,
    val photoPlaceholder: Color,
    val viewer: Color,
    val onViewer: Color,
    val onViewerVariant: Color,
    val dogTones: List<Pair<Color, Color>>,
)

internal val LightDogsColors = DogsColors(
    textTertiary = Palette.Neutral500,
    divider = Palette.Neutral50,
    controlOutline = Palette.Neutral200,
    done = Palette.Evergreen700,
    doneContainer = Palette.Evergreen50,
    onDoneContainer = Palette.Evergreen800,
    attention = Palette.Amber700,
    attentionContainer = Palette.Amber50,
    errorContainer = Palette.Red50,
    photoPlaceholder = Palette.Neutral100,
    viewer = Color(0xFF0F1412),
    onViewer = Palette.Neutral0,
    onViewerVariant = Palette.NeutralD300,
    dogTones = DogTonesLight,
)

internal val DarkDogsColors = DogsColors(
    textTertiary = Palette.NeutralD400,
    divider = Palette.NeutralD800,
    controlOutline = Palette.NeutralD500,
    done = Palette.Evergreen400,
    doneContainer = Palette.EvergreenD50,
    onDoneContainer = Palette.Evergreen300,
    attention = Palette.Amber400,
    attentionContainer = Palette.AmberD50,
    errorContainer = Palette.RedD50,
    photoPlaceholder = Palette.NeutralD800,
    viewer = Color(0xFF0A0D0C),
    onViewer = Palette.NeutralD100,
    onViewerVariant = Palette.NeutralD300,
    dogTones = DogTonesDark,
)

val LocalDogsColors = staticCompositionLocalOf { LightDogsColors }

