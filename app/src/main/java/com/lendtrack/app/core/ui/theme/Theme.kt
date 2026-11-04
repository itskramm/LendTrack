package com.lendtrack.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Material 3 color scheme — mapped from the LendTrack brand palette
// ─────────────────────────────────────────────────────────────────────────────

private val LendTrackColorScheme = lightColorScheme(
    primary            = LtRose,
    onPrimary          = Color.White,
    primaryContainer   = LtBlush,
    onPrimaryContainer = LtBurgundy,

    secondary          = LtDusty,
    onSecondary        = Color.White,
    secondaryContainer = LtBlush,
    onSecondaryContainer = LtBurgundy,

    tertiary           = LtSage,
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFD8EBD9),
    onTertiaryContainer = Color(0xFF1B4E1E),

    error              = LtRed,
    onError            = Color.White,
    errorContainer     = Color(0xFFFFDAD6),
    onErrorContainer   = Color(0xFF410002),

    background         = Color(0xFF2A2A2A), // Dark gray background
    onBackground       = Color(0xFFE5E5E5), // Light text
    surface            = Color(0xFF3A3A3A), // Slightly lighter gray
    onSurface          = Color(0xFFE5E5E5), // Light text
    surfaceVariant     = Color(0xFF4A4A4A), // Even lighter for elevation
    onSurfaceVariant   = Color(0xFFD0D0D0),

    outline            = LtDusty,
    outlineVariant     = LtDivider,

    inverseSurface     = Color(0xFFE5E5E5),
    inverseOnSurface   = Color(0xFF2A2A2A),
    inversePrimary     = LtRose,

    scrim              = LtScrim,
)

private val DarkColorScheme = darkColorScheme(
    primary            = Color(0xFFE91E63), // Vibrant pink from the image
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF8E1537), // Deeper pink for containers
    onPrimaryContainer = Color(0xFFFFDADF),

    secondary          = Color(0xFFCF6679), // Dusty rose
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFF5A3A3E),
    onSecondaryContainer = Color(0xFFFFD9DD),

    tertiary           = Color(0xFFE91E63), // Accent pink
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFF8E1537),
    onTertiaryContainer = Color(0xFFFFDADF),

    error              = Color(0xFFFFB4AB),
    onError            = Color(0xFF690005),
    errorContainer     = Color(0xFF93000A),
    onErrorContainer   = Color(0xFFFFDAD6),

    background         = Color(0xFF000000), // Pure black background like in image
    onBackground       = Color(0xFFFFFFFF),
    surface            = Color(0xFF0A0A0A), // Nearly black surface for cards
    onSurface          = Color(0xFFFFFFFF),
    surfaceVariant     = Color(0xFF1A1A1A), // Dark surface variant
    onSurfaceVariant   = Color(0xFFD0D0D0),

    outline            = Color(0xFFE91E63).copy(alpha = 0.3f), // Pink outline
    outlineVariant     = Color(0xFF49454F),

    inverseSurface     = Color(0xFFE5E5E5),
    inverseOnSurface   = Color(0xFF000000),
    inversePrimary     = Color(0xFFE91E63),

    scrim              = Color(0xDD000000),
)

// ─────────────────────────────────────────────────────────────────────────────
// Extended spacing tokens — used via LocalSpacing.current
// ─────────────────────────────────────────────────────────────────────────────

data class Spacing(
    val xxs: Dp =  2.dp,
    val xs:  Dp =  4.dp,
    val sm:  Dp =  8.dp,
    val md:  Dp = 12.dp,
    val lg:  Dp = 16.dp,
    val xl:  Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl:Dp = 32.dp,
    val sidebar: Dp = 240.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

// ─────────────────────────────────────────────────────────────────────────────
// Root app theme
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LendTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LendTrackColorScheme
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = LendTrackTypography,
            shapes      = LendTrackShapes,
            content     = content,
        )
    }
}
