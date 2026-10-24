@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.example.sparely.ui.theme

import androidx.compose.material3.MotionScheme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val SparelyDarkColorScheme = darkColorScheme(
    primary = SparelyPrimaryDark,
    onPrimary = SparelyOnPrimaryDark,
    primaryContainer = SparelyPrimaryContainerDark,
    onPrimaryContainer = SparelyOnPrimaryContainerDark,
    inversePrimary = SparelyPrimary,
    secondary = SparelySecondaryDark,
    onSecondary = SparelyOnSecondaryDark,
    secondaryContainer = SparelySecondaryContainerDark,
    onSecondaryContainer = SparelyOnSecondaryContainerDark,
    tertiary = SparelyTertiaryDark,
    onTertiary = SparelyOnTertiaryDark,
    tertiaryContainer = SparelyTertiaryContainerDark,
    onTertiaryContainer = SparelyOnTertiaryContainerDark,
    background = SparelyBackgroundDark,
    onBackground = SparelyOnBackgroundDark,
    surface = SparelyBackgroundDark,
    onSurface = SparelyOnBackgroundDark,
    surfaceVariant = SparelySurfaceVariantDark,
    onSurfaceVariant = SparelyOnSurfaceVariantDark,
    surfaceTint = SparelyPrimaryDark,
    inverseSurface = SparelyInverseSurfaceDark,
    inverseOnSurface = SparelyInverseOnSurfaceDark,
    error = AlertDark,
    onError = OnAlertDark,
    errorContainer = AlertContainerDark,
    onErrorContainer = OnAlertContainerDark,
    outline = SparelyOutlineDark,
    outlineVariant = SparelyOutlineVariantDark,
    surfaceBright = SparelySurfaceBrightDark,
    surfaceDim = SparelyBackgroundDark,
    surfaceContainerLowest = SparelySurfaceContainerLowestDark,
    surfaceContainerLow = SparelySurfaceContainerLowDark,
    surfaceContainer = SparelySurfaceContainerDark,
    surfaceContainerHigh = SparelySurfaceContainerHighDark,
    surfaceContainerHighest = SparelySurfaceContainerHighestDark
)

private val SparelyLightColorScheme = lightColorScheme(
    primary = SparelyPrimary,
    onPrimary = SparelyOnPrimary,
    primaryContainer = SparelyPrimaryContainer,
    onPrimaryContainer = SparelyOnPrimaryContainer,
    inversePrimary = BrandMint,
    secondary = SparelySecondary,
    onSecondary = SparelyOnSecondary,
    secondaryContainer = SparelySecondaryContainer,
    onSecondaryContainer = SparelyOnSecondaryContainer,
    tertiary = SparelyTertiary,
    onTertiary = SparelyOnTertiary,
    tertiaryContainer = SparelyTertiaryContainer,
    onTertiaryContainer = SparelyOnTertiaryContainer,
    background = SparelyBackground,
    onBackground = SparelyOnBackground,
    surface = SparelyBackground,
    onSurface = SparelyOnBackground,
    surfaceVariant = SparelySurfaceVariant,
    onSurfaceVariant = SparelyOnSurfaceVariant,
    surfaceTint = SparelyPrimary,
    inverseSurface = SparelyInverseSurface,
    inverseOnSurface = SparelyInverseOnSurface,
    error = AlertLight,
    onError = OnAlertLight,
    errorContainer = AlertContainerLight,
    onErrorContainer = OnAlertContainerLight,
    outline = SparelyOutline,
    outlineVariant = SparelyOutlineVariant,
    surfaceBright = SparelyBackground,
    surfaceDim = SparelySurfaceDim,
    surfaceContainerLowest = SparelySurfaceContainerLowest,
    surfaceContainerLow = SparelySurfaceContainerLow,
    surfaceContainer = SparelySurfaceContainer,
    surfaceContainerHigh = SparelySurfaceContainerHigh,
    surfaceContainerHighest = SparelySurfaceContainerHighest
)

/**
 * Fixed brand colors for signature surfaces like the dashboard hero card: spruce container, ink text,
 * mint accents, in every theme (wallpaper colors included). Wrap content in [SparelyBrandSurface].
 */
private val SparelyBrandColorScheme = SparelyDarkColorScheme.copy(
    primary = BrandMint,
    primaryContainer = BrandSpruce,
    onPrimaryContainer = BrandInk,
    background = BrandDeepSpruce,
    surface = BrandDeepSpruce,
    onSurface = BrandInk,
    onSurfaceVariant = BrandMuted
)

@Composable
fun SparelyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Wallpaper colors (Android 12+). Off by default so the app wears Sparely's colors;
    // Settings > Appearance turns it on.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> SparelyDarkColorScheme
        else -> SparelyLightColorScheme
    }

    CompositionLocalProvider(LocalSpacing provides SparelySpacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            // Spring-based Expressive motion for every Material component (docs/DESIGN.md §9)
            motionScheme = MotionScheme.expressive(),
            typography = Typography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}

/** Shows [content] in Sparely's fixed brand colors, whatever the app theme. */
@Composable
fun SparelyBrandSurface(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SparelyBrandColorScheme, content = content)
}

/** True for dark color schemes, wallpaper-based ones included. */
internal val androidx.compose.material3.ColorScheme.isDark: Boolean
    get() = background.luminance() < 0.5f
