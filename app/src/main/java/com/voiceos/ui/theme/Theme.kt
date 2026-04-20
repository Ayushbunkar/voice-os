package com.voiceos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark color scheme (primary mode for VoiceOS) ──────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = Blue500,
    onPrimary        = Color.White,
    primaryContainer = Blue600,
    secondary        = Blue400,
    onSecondary      = Color.White,
    background       = Bg900,
    onBackground     = TextPrimary,
    surface          = Bg800,
    onSurface        = TextPrimary,
    surfaceVariant   = Bg700,
    onSurfaceVariant = TextSecondary,
    outline          = Divider,
    error            = RedError,
    onError          = Color.White,
    tertiary         = GreenOk,
    onTertiary       = Color.White
)

// ── Light color scheme (graceful fallback) ────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary          = Blue500,
    onPrimary        = Color.White,
    primaryContainer = Blue300,
    secondary        = Blue600,
    onSecondary      = Color.White,
    background       = Color(0xFFF2F4FB),
    onBackground     = Color(0xFF1A1D27),
    surface          = Color.White,
    onSurface        = Color(0xFF1A1D27),
    surfaceVariant   = Color(0xFFE8ECF8),
    onSurfaceVariant = Color(0xFF5A5F78),
    outline          = Color(0xFFBFC4D8),
    error            = RedError,
    tertiary         = GreenOk
)

/**
 * VoiceOSTheme — wraps the app in Material3 with custom palette + typography.
 *
 * Respects the system dark/light setting but defaults to dark since
 * VoiceOS is predominantly used in a dark context.
 */
@Composable
fun VoiceOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = VoiceOSTypography,
        content     = content
    )
}
