package com.calcrux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mi Calculator color palette
private val Orange = Color(0xFFFF6900)
private val OrangeLight = Color(0xFFFF8C42)
private val OrangeDark = Color(0xFF6E2B00)
private val OrangeContainer = Color(0xFFFFDCC9)

private val LightColorScheme = lightColorScheme(
    primary             = Orange,
    onPrimary           = Color.White,
    primaryContainer    = OrangeContainer,
    onPrimaryContainer  = Color(0xFF5C1900),
    secondary           = Color(0xFFEEEEEE),
    onSecondary         = Color(0xFF1A1A1A),
    secondaryContainer  = Color(0xFFEEEEEE),
    onSecondaryContainer= Color(0xFF1A1A1A),
    tertiary            = Color(0xFFFF6900),
    onTertiary          = Color.White,
    background          = Color(0xFFF7F7F7),
    onBackground        = Color(0xFF1A1A1A),
    surface             = Color.White,
    onSurface           = Color(0xFF1A1A1A),
    surfaceVariant      = Color(0xFFEBEBEB),
    onSurfaceVariant    = Color(0xFF1A1A1A),
    error               = Color(0xFFE53935),
    onError             = Color.White,
    errorContainer      = Color(0xFFFFDAD6),
    onErrorContainer    = Color(0xFF410002),
    outline             = Color(0xFFCCCCCC),
)

private val DarkColorScheme = darkColorScheme(
    primary             = OrangeLight,
    onPrimary           = Color(0xFF4A1200),
    primaryContainer    = OrangeDark,
    onPrimaryContainer  = OrangeContainer,
    secondary           = Color(0xFF3D3D3D),
    onSecondary         = Color(0xFFE0E0E0),
    secondaryContainer  = Color(0xFF3D3D3D),
    onSecondaryContainer= Color(0xFFE0E0E0),
    tertiary            = OrangeLight,
    onTertiary          = Color(0xFF4A1200),
    background          = Color(0xFF1C1C1C),
    onBackground        = Color(0xFFE0E0E0),
    surface             = Color(0xFF2C2C2C),
    onSurface           = Color(0xFFE0E0E0),
    surfaceVariant      = Color(0xFF383838),
    onSurfaceVariant    = Color(0xFFE0E0E0),
    error               = Color(0xFFCF6679),
    onError             = Color(0xFF370009),
    errorContainer      = Color(0xFF93000A),
    onErrorContainer    = Color(0xFFFFDAD6),
    outline             = Color(0xFF555555),
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
