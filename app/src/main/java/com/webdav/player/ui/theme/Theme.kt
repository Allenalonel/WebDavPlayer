package com.webdav.player.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.webdav.player.ui.player.ArtworkColors

val ExpressiveDarkColorScheme =
    darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = SecondaryDark,
        onSecondary = OnSecondaryDark,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary = TertiaryDark,
        onTertiary = OnTertiaryDark,
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
    )

val ExpressiveLightColorScheme =
    lightColorScheme(
        primary = PrimaryLight,
        onPrimary = OnPrimaryLight,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        secondary = SecondaryLight,
        onSecondary = OnSecondaryLight,
        secondaryContainer = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary = TertiaryLight,
        onTertiary = OnTertiaryLight,
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        surfaceContainerLowest = SurfaceContainerLowestLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
    )

/**
 * Smoothly animates key background and surface container tiers over 0.45 seconds (450ms).
 * Transitions background and surfaceContainer across track changes without 17-color full tree lag.
 */
@Composable
fun animatedColorScheme(
    target: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(durationMillis = 450),
): ColorScheme {
    val background by animateColorAsState(target.background, animationSpec, label = "color_background")
    val surface by animateColorAsState(target.surface, animationSpec, label = "color_surface")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, animationSpec, label = "color_surfaceContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, animationSpec, label = "color_surfaceContainerHigh")
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, animationSpec, label = "color_surfaceContainerLow")

    return target.copy(
        background = background,
        surface = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerLow = surfaceContainerLow,
    )
}

@Composable
fun WebDavPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    artworkColors: ArtworkColors? = null,
    targetColorScheme: ColorScheme? = null,
    animateTransitions: Boolean = true,
    content: @Composable () -> Unit,
) {
    val baseScheme =
        when {
            targetColorScheme != null -> {
                targetColorScheme
            }

            artworkColors != null && !artworkColors.isFallback -> {
                artworkColors.colorScheme(darkTheme)
            }

            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                ExpressiveDarkColorScheme
            }

            else -> {
                ExpressiveLightColorScheme
            }
        }

    val finalColorScheme =
        if (animateTransitions) {
            animatedColorScheme(baseScheme)
        } else {
            baseScheme
        }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
