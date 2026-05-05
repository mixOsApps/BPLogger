package com.bplogger.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ── Brand Palette ──────────────────────────────────────────────
val BPRed        = Color(0xFFD32F2F)
val BPRedLight   = Color(0xFFFF6659)
val BPRedDark    = Color(0xFF9A0007)

val BPYellow     = Color(0xFFFBC02D)
val BPYellowLight= Color(0xFFFFEC6E)
val BPYellowDark = Color(0xFFC49000)

val BPGreen      = Color(0xFF388E3C)
val BPGreenLight = Color(0xFF6ABF69)
val BPGreenDark  = Color(0xFF00600F)

val BPWhite      = Color(0xFFFAFAFA)
val BPSurface    = Color(0xFFFFFFFF)
val BPBackground = Color(0xFFF5F5F5)
val BPOnRed      = Color(0xFFFFFFFF)

// Category Colors (used in chips / badges)
val ColorNormal   = BPGreen
val ColorElevated = BPYellow
val ColorHigh1    = Color(0xFFE65100)
val ColorHigh2    = BPRed
val ColorCrisis   = Color(0xFF880E4F)

private val LightColorScheme = lightColorScheme(
    primary          = BPRed,
    onPrimary        = BPOnRed,
    primaryContainer = BPRedLight,
    secondary        = BPYellow,
    onSecondary      = Color(0xFF1A1A1A),
    secondaryContainer = BPYellowLight,
    tertiary         = BPGreen,
    onTertiary       = BPOnRed,
    background       = BPBackground,
    onBackground     = Color(0xFF1A1A1A),
    surface          = BPSurface,
    onSurface        = Color(0xFF1A1A1A),
    surfaceVariant   = Color(0xFFEEEEEE),
    error            = BPRed
)

private val DarkColorScheme = darkColorScheme(
    primary          = BPYellow,
    onPrimary        = Color(0xFF1A1A1A),
    primaryContainer = BPYellowDark,
    secondary        = BPRedLight,
    onSecondary      = Color(0xFFFFFFFF),
    secondaryContainer = BPRedDark,
    tertiary         = BPGreenLight,
    onTertiary       = Color(0xFF1A1A1A),
    background       = Color(0xFF121212),
    onBackground     = Color(0xFFE0E0E0),
    surface          = Color(0xFF1E1E1E),
    onSurface        = Color(0xFFE0E0E0),
    surfaceVariant   = Color(0xFF2C2C2C),
    error            = BPRedLight
)

/**
 * Theme-aware accent color.
 * Returns BPRed in light mode, BPYellow in dark mode.
 * Use this instead of hardcoded BPRed for elements that should adapt to theme.
 */
val MaterialTheme.accentColor: Color
    @Composable
    @ReadOnlyComposable
    get() = colorScheme.primary

/**
 * Theme-aware green/restore accent.
 * Returns BPGreen in light mode, BPGreenLight in dark mode.
 */
val MaterialTheme.greenAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = colorScheme.tertiary

@Composable
fun BPLoggerTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}