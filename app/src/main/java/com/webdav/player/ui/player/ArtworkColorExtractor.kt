package com.webdav.player.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.webdav.player.ui.theme.ExpressiveDarkColorScheme
import com.webdav.player.ui.theme.ExpressiveLightColorScheme
import com.webdav.player.ui.theme.SurfaceContainerDark
import com.webdav.player.ui.theme.SurfaceContainerLight
import com.webdav.player.ui.theme.SurfaceDark
import com.webdav.player.ui.theme.SurfaceLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/**
 * Atmospheric color scheme dynamically derived from track artwork or fallback colors.
 */
data class ArtworkColors(
    val dominantColor: Color,
    val backgroundTopColor: Color,
    val backgroundBottomColor: Color,
    val accentColor: Color,
    val isFallback: Boolean = false,
    val darkColorScheme: ColorScheme = ArtworkColorExtractor.createDarkColorScheme(dominantColor, isFallback),
    val lightColorScheme: ColorScheme = ArtworkColorExtractor.createLightColorScheme(dominantColor, isFallback),
) {
    fun colorScheme(isDark: Boolean): ColorScheme = if (isDark) darkColorScheme else lightColorScheme

    val gradientBrush: Brush
        get() =
            Brush.verticalGradient(
                colors =
                    listOf(
                        backgroundTopColor,
                        backgroundBottomColor,
                    ),
            )
}

object ArtworkColorExtractor {
    private const val SAMPLE_SIZE = 32

    /**
     * Converts a Compose [Color] into HSL components:
     * - Hue in [0f, 360f)
     * - Saturation in [0f, 1f]
     * - Lightness in [0f, 1f]
     */
    fun colorToHsl(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        var h = 0f
        var s = 0f
        val l = (max + min) / 2f

        if (delta > 1e-5f) {
            s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
            h =
                when (max) {
                    r -> ((g - b) / delta + (if (g < b) 6f else 0f)) * 60f
                    g -> ((b - r) / delta + 2f) * 60f
                    else -> ((r - g) / delta + 4f) * 60f
                }
        }
        return floatArrayOf((h % 360f + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /**
     * Reconstructs a Compose [Color] from HSL components.
     */
    fun hslToColor(
        h: Float,
        s: Float,
        l: Float,
        alpha: Float = 1f,
    ): Color {
        val normalizedH = (h % 360f + 360f) % 360f
        val clampedS = s.coerceIn(0f, 1f)
        val clampedL = l.coerceIn(0f, 1f)

        val c = (1f - abs(2f * clampedL - 1f)) * clampedS
        val x = c * (1f - abs((normalizedH / 60f) % 2f - 1f))
        val m = clampedL - c / 2f

        val (rPrime, gPrime, bPrime) =
            when {
                normalizedH < 60f -> Triple(c, x, 0f)
                normalizedH < 120f -> Triple(x, c, 0f)
                normalizedH < 180f -> Triple(0f, c, x)
                normalizedH < 240f -> Triple(0f, x, c)
                normalizedH < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        return Color(
            red = (rPrime + m).coerceIn(0f, 1f),
            green = (gPrime + m).coerceIn(0f, 1f),
            blue = (bPrime + m).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }

    /**
     * Derives a complete dark [ColorScheme] from the dominant seed color.
     * Produces deep dark immersive background (Tone 6~8), dark container (Tone 12~14), and vibrant primary (Tone 80).
     */
    fun deriveDarkColorScheme(dominantColor: Color): ColorScheme {
        val hsl = colorToHsl(dominantColor)
        val h = hsl[0]
        val s = hsl[1]

        // Tone 6~8 deep dark immersive background & surface (e.g. #141022 for violet)
        val background = hslToColor(h, s * 0.28f, 0.07f)
        val surface = background
        val surfaceContainerLowest = hslToColor(h, s * 0.25f, 0.04f)
        val surfaceContainerLow = hslToColor(h, s * 0.25f, 0.09f)
        val surfaceContainer = hslToColor(h, s * 0.25f, 0.13f)
        val surfaceContainerHigh = hslToColor(h, s * 0.25f, 0.17f)
        val surfaceContainerHighest = hslToColor(h, s * 0.25f, 0.22f)
        val surfaceVariant = hslToColor(h, s * 0.20f, 0.26f)

        // Tone 80 primary - ensure contrast with onPrimary >= 4.5:1
        var primaryL = 0.80f
        var primary = hslToColor(h, maxOf(0.40f, s * 0.85f), primaryL)
        var onPrimaryL = 0.20f
        var onPrimary = hslToColor(h, s * 0.90f, onPrimaryL)
        while (contrastRatio(onPrimary, primary) < 4.5 && (primaryL < 0.92f || onPrimaryL > 0.08f)) {
            if (primaryL < 0.92f) primaryL += 0.02f
            if (onPrimaryL > 0.08f) onPrimaryL -= 0.02f
            primary = hslToColor(h, maxOf(0.40f, s * 0.85f), primaryL)
            onPrimary = hslToColor(h, s * 0.90f, onPrimaryL)
        }

        val primaryContainer = hslToColor(h, s * 0.70f, 0.30f)
        val onPrimaryContainer = hslToColor(h, s * 0.30f, 0.90f)

        val secondary = hslToColor(h, s * 0.50f, 0.80f)
        val onSecondary = hslToColor(h, s * 0.50f, 0.20f)
        val secondaryContainer = hslToColor(h, s * 0.40f, 0.30f)
        val onSecondaryContainer = hslToColor(h, s * 0.20f, 0.90f)

        val inverseSurface = hslToColor(h, maxOf(0.08f, s * 0.22f), 0.90f)
        val inverseOnSurface = Color(0xFF1A1B21)
        val inversePrimary = hslToColor(h, maxOf(0.50f, s * 0.95f), 0.40f)

        return ExpressiveDarkColorScheme.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            surfaceTint = primary,
        )
    }

    /**
     * Derives a complete light [ColorScheme] from the dominant seed color.
     * Produces soft ambient pastel background (Tone 96~98), delicate container (Tone 92~94),
     * dark slate charcoal secondary text (#3A3546, contrast > 8.5:1), and high-contrast pills (> 8.5:1).
     */
    fun deriveLightColorScheme(dominantColor: Color): ColorScheme {
        val hsl = colorToHsl(dominantColor)
        val h = hsl[0]
        val s = hsl[1]

        // Tone 96~98 delicate soft pastel ambient background
        val background = hslToColor(h, maxOf(0.08f, s * 0.22f), 0.97f)
        val surface = background
        val surfaceContainerLowest = Color.White
        val surfaceContainerLow = hslToColor(h, maxOf(0.06f, s * 0.18f), 0.95f)
        val surfaceContainer = hslToColor(h, maxOf(0.06f, s * 0.16f), 0.93f)
        val surfaceContainerHigh = hslToColor(h, maxOf(0.06f, s * 0.16f), 0.90f)
        val surfaceContainerHighest = hslToColor(h, maxOf(0.06f, s * 0.16f), 0.86f)
        val surfaceVariant = hslToColor(h, maxOf(0.06f, s * 0.14f), 0.90f)

        // Tone 40 primary - ensure contrast with onPrimary (White) >= 4.5:1
        var primaryL = 0.38f
        var primary = hslToColor(h, maxOf(0.50f, s * 0.95f), primaryL)
        while (contrastRatio(Color.White, primary) < 4.5 && primaryL > 0.15f) {
            primaryL -= 0.02f
            primary = hslToColor(h, maxOf(0.50f, s * 0.95f), primaryL)
        }
        val onPrimary = Color.White

        // Pills: primaryContainer (Tone 92) + onPrimaryContainer (Tone 8~10) reaching WCAG AAA (>= 8.5:1)
        val primaryContainer = hslToColor(h, minOf(0.35f, s * 0.45f), 0.92f)
        var textL = 0.08f
        var onPrimaryContainer = hslToColor(h, minOf(0.60f, s * 0.70f), textL)
        while (contrastRatio(onPrimaryContainer, primaryContainer) < 8.5 && textL > 0.02f) {
            textL -= 0.01f
            onPrimaryContainer = hslToColor(h, minOf(0.60f, s * 0.70f), textL)
        }

        val inverseSurface = hslToColor(h, s * 0.25f, 0.20f)
        val inverseOnSurface = Color(0xFFE4E1E8)
        val inversePrimary = hslToColor(h, maxOf(0.40f, s * 0.85f), 0.80f)

        return ExpressiveLightColorScheme.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            // Secondary text is dark slate charcoal (#3A3546) reaching WCAG AAA (> 8.5:1)
            onSurfaceVariant = Color(0xFF3A3546),
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            surfaceTint = primary,
        )
    }

    private fun channelLuminance(channel: Float): Double =
        if (channel <= 0.03928) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).toDouble().pow(2.4)
        }

    fun relativeLuminance(color: Color): Double {
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(
        colorA: Color,
        colorB: Color,
    ): Double {
        val l1 = relativeLuminance(colorA)
        val l2 = relativeLuminance(colorB)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Creates dark [ColorScheme] respecting the fallback flag.
     */
    fun createDarkColorScheme(
        dominantColor: Color,
        isFallback: Boolean = false,
    ): ColorScheme = if (isFallback) ExpressiveDarkColorScheme else deriveDarkColorScheme(dominantColor)

    /**
     * Creates light [ColorScheme] respecting the fallback flag.
     */
    fun createLightColorScheme(
        dominantColor: Color,
        isFallback: Boolean = false,
    ): ColorScheme = if (isFallback) ExpressiveLightColorScheme else deriveLightColorScheme(dominantColor)

    /**
     * Derives [ColorScheme] based on mode and fallback flag.
     */
    fun deriveColorScheme(
        dominantColor: Color,
        isDark: Boolean,
        isFallback: Boolean = false,
    ): ColorScheme =
        if (isDark) {
            createDarkColorScheme(dominantColor, isFallback)
        } else {
            createLightColorScheme(dominantColor, isFallback)
        }

    /**
     * Extracts dynamic artwork colors from a cached thumbnail file path.
     * Gracefully falls back to standard MD3 surface colors if file is null, empty, or unreadable.
     */
    fun extractArtworkColors(
        thumbnailPath: String?,
        defaultSurfaceColor: Color = SurfaceContainerDark,
        defaultBackgroundColor: Color = SurfaceDark,
        isDarkTheme: Boolean = true,
    ): ArtworkColors {
        if (thumbnailPath.isNullOrBlank()) {
            return createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)
        }

        return try {
            val file = File(thumbnailPath)
            if (!file.exists() || file.length() == 0L) {
                return createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)
            }

            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val bitmap =
                BitmapFactory.decodeFile(file.absolutePath, options)
                    ?: return createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)

            try {
                extractArtworkColors(bitmap, defaultSurfaceColor, defaultBackgroundColor, isDarkTheme)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } catch (_: Throwable) {
            createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)
        }
    }

    /**
     * Extracts dynamic artwork colors from a Bitmap instance.
     */
    fun extractArtworkColors(
        bitmap: Bitmap?,
        defaultSurfaceColor: Color = SurfaceContainerDark,
        defaultBackgroundColor: Color = SurfaceDark,
        isDarkTheme: Boolean = true,
    ): ArtworkColors {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)
        }

        val dominant =
            extractDominantColor(bitmap) ?: return createFallbackColors(
                defaultSurfaceColor,
                defaultBackgroundColor,
            )

        // Atmospheric top tint composited over surface
        val atmosphericTintAlpha = if (isDarkTheme) 0.50f else 0.28f
        val backgroundTop = dominant.copy(alpha = atmosphericTintAlpha).compositeOver(defaultSurfaceColor)
        val backgroundBottom = defaultBackgroundColor

        return ArtworkColors(
            dominantColor = dominant,
            backgroundTopColor = backgroundTop,
            backgroundBottomColor = backgroundBottom,
            accentColor = dominant,
            isFallback = false,
            darkColorScheme = deriveDarkColorScheme(dominant),
            lightColorScheme = deriveLightColorScheme(dominant),
        )
    }

    /**
     * Extracts the most prominent/vibrant dominant color from a bitmap using hue-binning sampling.
     * Returns null if no non-transparent pixels exist.
     */
    fun extractDominantColor(bitmap: Bitmap): Color? {
        val scaledBitmap =
            if (bitmap.width > SAMPLE_SIZE || bitmap.height > SAMPLE_SIZE) {
                Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, false)
            } else {
                bitmap
            }

        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val totalPixels = width * height
        if (totalPixels <= 0) return null

        val pixels = IntArray(totalPixels)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        val hsv = FloatArray(3)
        // 12 hue bins: 0°..30°, 30°..60°, ..., 330°..360°
        val binCounts = DoubleArray(12)
        val binRed = DoubleArray(12)
        val binGreen = DoubleArray(12)
        val binBlue = DoubleArray(12)

        var nonTransparentCount = 0
        var totalRed = 0.0
        var totalGreen = 0.0
        var totalBlue = 0.0

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 64) continue // Skip transparent pixels

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            nonTransparentCount++
            totalRed += r
            totalGreen += g
            totalBlue += b

            android.graphics.Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]

            // Give extra weight to saturated, vibrant colors over flat gray/black/white
            val vibrancyWeight = ((1.0f + saturation * 2.5f) * (0.3f + value * 0.7f)).toDouble()
            val hue = hsv[0]
            val binIndex = ((hue % 360f) / 30f).toInt().coerceIn(0, 11)

            binCounts[binIndex] = binCounts[binIndex] + vibrancyWeight
            binRed[binIndex] = binRed[binIndex] + (r * vibrancyWeight)
            binGreen[binIndex] = binGreen[binIndex] + (g * vibrancyWeight)
            binBlue[binIndex] = binBlue[binIndex] + (b * vibrancyWeight)
        }

        if (nonTransparentCount == 0) return null

        // Find the bin with the highest weighted count
        var bestBin = -1
        var maxWeight = 0.0
        for (i in 0 until 12) {
            if (binCounts[i] > maxWeight) {
                maxWeight = binCounts[i]
                bestBin = i
            }
        }

        return if (bestBin >= 0 && maxWeight > 0.0) {
            val avgR = (binRed[bestBin] / binCounts[bestBin]).toInt().coerceIn(0, 255)
            val avgG = (binGreen[bestBin] / binCounts[bestBin]).toInt().coerceIn(0, 255)
            val avgB = (binBlue[bestBin] / binCounts[bestBin]).toInt().coerceIn(0, 255)
            Color(avgR, avgG, avgB)
        } else {
            val avgR = (totalRed / nonTransparentCount).toInt().coerceIn(0, 255)
            val avgG = (totalGreen / nonTransparentCount).toInt().coerceIn(0, 255)
            val avgB = (totalBlue / nonTransparentCount).toInt().coerceIn(0, 255)
            Color(avgR, avgG, avgB)
        }
    }

    /**
     * Fallback atmospheric colors when artwork thumbnail is missing or cannot be loaded.
     */
    fun createFallbackColors(
        defaultSurfaceColor: Color = SurfaceContainerDark,
        defaultBackgroundColor: Color = SurfaceDark,
    ): ArtworkColors =
        ArtworkColors(
            dominantColor = defaultSurfaceColor,
            backgroundTopColor = defaultSurfaceColor,
            backgroundBottomColor = defaultBackgroundColor,
            accentColor = defaultSurfaceColor,
            isFallback = true,
            darkColorScheme = ExpressiveDarkColorScheme,
            lightColorScheme = ExpressiveLightColorScheme,
        )
}

/**
 * Remembers reactive dynamic artwork colors from the given cover thumbnail path.
 * Computes in background IO dispatcher when thumbnail path changes, falling back to MD3 surfaceContainer.
 */
@Composable
fun rememberArtworkColors(
    thumbnailPath: String?,
    defaultSurfaceColor: Color = Color.Unspecified,
    defaultBackgroundColor: Color = Color.Unspecified,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
): State<ArtworkColors> {
    val resolvedSurface =
        if (defaultSurfaceColor != Color.Unspecified) {
            defaultSurfaceColor
        } else if (isDarkTheme) {
            SurfaceContainerDark
        } else {
            SurfaceContainerLight
        }

    val resolvedBackground =
        if (defaultBackgroundColor != Color.Unspecified) {
            defaultBackgroundColor
        } else if (isDarkTheme) {
            SurfaceDark
        } else {
            SurfaceLight
        }

    val initialFallback =
        ArtworkColorExtractor.createFallbackColors(
            resolvedSurface,
            resolvedBackground,
        )

    return produceState(
        initialValue = initialFallback,
        thumbnailPath,
        resolvedSurface,
        resolvedBackground,
        isDarkTheme,
    ) {
        if (thumbnailPath.isNullOrBlank()) {
            value = initialFallback
            return@produceState
        }

        val extracted =
            withContext(Dispatchers.IO) {
                ArtworkColorExtractor.extractArtworkColors(
                    thumbnailPath = thumbnailPath,
                    defaultSurfaceColor = resolvedSurface,
                    defaultBackgroundColor = resolvedBackground,
                    isDarkTheme = isDarkTheme,
                )
            }
        value = extracted
    }
}
