package com.example.sparely.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FallbackPrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = FallbackPrimaryContainerDark,
    onPrimaryContainer = FallbackPrimaryContainer,
    secondary = FallbackSecondaryDark,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = FallbackSecondaryContainerDark,
    onSecondaryContainer = FallbackSecondaryContainer,
    tertiary = FallbackTertiaryDark,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = FallbackTertiaryContainerDark,
    onTertiaryContainer = FallbackTertiaryContainer,
    background = FallbackBackgroundDark,
    onBackground = Color(0xFFE6E1E5),
    surface = FallbackSurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = FallbackSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = FallbackOutlineDark,
    error = ErrorRed,
    onError = Color.White,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = SurfaceDimDark
)

private val LightColorScheme = lightColorScheme(
    primary = FallbackPrimary,
    onPrimary = Color.White,
    primaryContainer = FallbackPrimaryContainer,
    onPrimaryContainer = Color(0xFF21005D),
    secondary = FallbackSecondary,
    onSecondary = Color.White,
    secondaryContainer = FallbackSecondaryContainer,
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = FallbackTertiary,
    onTertiary = Color.White,
    tertiaryContainer = FallbackTertiaryContainer,
    onTertiaryContainer = Color(0xFF31111D),
    background = FallbackBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = FallbackSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = FallbackSurfaceVariant,
    onSurfaceVariant = Color(0xFF49454F),
    outline = FallbackOutline,
    error = ErrorRed,
    onError = Color.White,
    surfaceBright = SurfaceBright,
    surfaceDim = SurfaceDim
)

@Composable
fun SparelyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalSpacing provides SparelySpacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}
