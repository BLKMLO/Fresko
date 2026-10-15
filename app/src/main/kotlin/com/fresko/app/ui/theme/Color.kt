package com.fresko.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Identité Fresko : encre profonde, corail, lavande. */
object FreskoColors {
    val Coral = Color(0xFFFF7A59)
    val CoralDeep = Color(0xFFE5532F)
    val Violet = Color(0xFF8B5CF6)
    val Lavender = Color(0xFFB7A8FF)
    val Mint = Color(0xFF5EEAD4)

    /** Dégradé de marque (titre, icône, pastilles). */
    val Brand = Brush.linearGradient(listOf(Coral, Violet))
}

internal val DarkScheme = darkColorScheme(
    primary = FreskoColors.Coral,
    onPrimary = Color(0xFF2B0A00),
    primaryContainer = Color(0xFF5C1F0E),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = FreskoColors.Lavender,
    onSecondary = Color(0xFF1B0F5C),
    secondaryContainer = Color(0xFF352C66),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = FreskoColors.Mint,
    onTertiary = Color(0xFF00201B),
    tertiaryContainer = Color(0xFF0F3D38),
    onTertiaryContainer = Color(0xFFB5F5EA),
    background = Color(0xFF0E0D14),
    onBackground = Color(0xFFF1EEF8),
    surface = Color(0xFF0E0D14),
    onSurface = Color(0xFFF1EEF8),
    surfaceVariant = Color(0xFF2A2638),
    onSurfaceVariant = Color(0xFFA9A3BC),
    surfaceContainerLowest = Color(0xFF0A0910),
    surfaceContainerLow = Color(0xFF14121C),
    surfaceContainer = Color(0xFF191723),
    surfaceContainerHigh = Color(0xFF221F2E),
    surfaceContainerHighest = Color(0xFF2B2839),
    outline = Color(0xFF4A4559),
    outlineVariant = Color(0xFF2E2A3B),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3B0010),
)

internal val LightScheme = lightColorScheme(
    primary = FreskoColors.CoralDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF5B47D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E0FF),
    onSecondaryContainer = Color(0xFF1B0F5C),
    tertiary = Color(0xFF0F8A7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8F5EC),
    onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFFAF7F4),
    onBackground = Color(0xFF1A1622),
    surface = Color(0xFFFAF7F4),
    onSurface = Color(0xFF1A1622),
    surfaceVariant = Color(0xFFEDE7E1),
    onSurfaceVariant = Color(0xFF6B6578),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2EE),
    surfaceContainer = Color(0xFFF2ECE7),
    surfaceContainerHigh = Color(0xFFECE5DF),
    surfaceContainerHighest = Color(0xFFE5DED7),
    outline = Color(0xFFC9C2D0),
    outlineVariant = Color(0xFFE6E0EA),
    error = Color(0xFFC62842),
    onError = Color.White,
)
