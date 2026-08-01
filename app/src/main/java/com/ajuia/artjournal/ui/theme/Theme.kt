package com.ajuia.artjournal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArtJournalColorScheme = darkColorScheme(
    primary = PrimaryYellow,
    onPrimary = DeepBlack,
    primaryContainer = DarkMutedYellow,
    onPrimaryContainer = PureWhite,
    secondary = AccentYellow,
    onSecondary = DeepBlack,
    background = DeepBlack,
    onBackground = PureWhite,
    surface = DarkSurface,
    onSurface = PureWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = PureWhite,
    outline = BorderGray,
    error = SoftRed,
    onError = DeepBlack
)

// Standard fallback if light mode requested, but we preserve the yellow details and high contrast black elements
private val LightArtJournalColorScheme = lightColorScheme(
    primary = PrimaryYellow,
    onPrimary = DeepBlack,
    primaryContainer = DarkMutedYellow,
    onPrimaryContainer = PureWhite,
    secondary = AccentYellow,
    onSecondary = DeepBlack,
    background = DeepBlack, // Keep it deep black as requested!
    onBackground = PureWhite,
    surface = DarkSurface,
    onSurface = PureWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = PureWhite,
    outline = BorderGray,
    error = SoftRed,
    onError = DeepBlack
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for artistic deep black vibe!
    dynamicColor: Boolean = false, // Disable dynamic colors so our yellow accents take center stage!
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ArtJournalColorScheme else LightArtJournalColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
