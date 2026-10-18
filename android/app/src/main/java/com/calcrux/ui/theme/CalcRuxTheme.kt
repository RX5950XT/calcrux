package com.calcrux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mi Calculator color palette. Light-mode orange is a touch deeper than Mi's
// #FF6900 so orange glyphs on gray keys and white "=" text keep ≥3:1 contrast.
private val Orange = Color(0xFFE65A00)
private val OrangeLight = Color(0xFFFF8C42)
private val OrangeDark = Color(0xFF6E2B00)
private val OrangeContainer = Color(0xFFFFDCC9)

// Every surface role is set explicitly: unset roles fall back to the M3
// baseline (purple-tinted), which leaked into cards, dialogs and text fields.
private val LightColorScheme = lightColorScheme(
    primary             = Orange,
    onPrimary           = Color.White,
    primaryContainer    = OrangeContainer,
    onPrimaryContainer  = Color(0xFF5C1900),
    inversePrimary      = OrangeLight,
    secondary           = Color(0xFF5F5F5F),
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFECECEC),
    onSecondaryContainer= Color(0xFF1A1A1A),
    tertiary            = Orange,
    onTertiary          = Color.White,
    tertiaryContainer   = OrangeContainer,
    onTertiaryContainer = Color(0xFF5C1900),
    background          = Color.White,
    onBackground        = Color(0xFF1A1A1A),
    surface             = Color.White,
    onSurface           = Color(0xFF1A1A1A),
    surfaceVariant      = Color(0xFFEBEBEB),
    onSurfaceVariant    = Color(0xFF666666),
    surfaceTint         = Orange,
    surfaceDim          = Color(0xFFDEDEDE),
    surfaceBright       = Color.White,
    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = Color(0xFFFAFAFA),
    surfaceContainer        = Color(0xFFF4F4F4),
    surfaceContainerHigh    = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    inverseSurface      = Color(0xFF2E2E2E),
    inverseOnSurface    = Color(0xFFF2F2F2),
    error               = Color(0xFFD32F2F),
    onError             = Color.White,
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),
    outline             = Color(0xFF8A8A8A),
    outlineVariant      = Color(0xFFDDDDDD),
)

private val DarkColorScheme = darkColorScheme(
    primary             = OrangeLight,
    onPrimary           = Color(0xFF4A1200),
    primaryContainer    = OrangeDark,
    onPrimaryContainer  = OrangeContainer,
    inversePrimary      = Orange,
    secondary           = Color(0xFFB0B0B0),
    onSecondary         = Color(0xFF1C1C1C),
    secondaryContainer  = Color(0xFF3A3A3A),
    onSecondaryContainer= Color(0xFFE0E0E0),
    tertiary            = OrangeLight,
    onTertiary          = Color(0xFF4A1200),
    tertiaryContainer   = OrangeDark,
    onTertiaryContainer = OrangeContainer,
    background          = Color(0xFF1C1C1C),
    onBackground        = Color(0xFFE6E6E6),
    surface             = Color(0xFF1C1C1C),
    onSurface           = Color(0xFFE6E6E6),
    surfaceVariant      = Color(0xFF383838),
    onSurfaceVariant    = Color(0xFFA8A8A8),
    surfaceTint         = OrangeLight,
    surfaceDim          = Color(0xFF141414),
    surfaceBright       = Color(0xFF3A3A3A),
    surfaceContainerLowest  = Color(0xFF141414),
    surfaceContainerLow     = Color(0xFF202020),
    surfaceContainer        = Color(0xFF262626),
    surfaceContainerHigh    = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF383838),
    inverseSurface      = Color(0xFFE6E6E6),
    inverseOnSurface    = Color(0xFF2E2E2E),
    error               = Color(0xFFFF8A80),
    onError             = Color(0xFF370009),
    errorContainer      = Color(0xFF93000A),
    onErrorContainer    = Color(0xFFFFDAD6),
    outline             = Color(0xFF6E6E6E),
    outlineVariant      = Color(0xFF3A3A3A),
)

@Composable
fun CalcRuxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
