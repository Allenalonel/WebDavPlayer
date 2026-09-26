package com.webdav.player.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Atmospheric color scheme dynamically derived from track artwork or fallback colors.
 */
data class ArtworkColors(
    val dominantColor: Color,
    val backgroundTopColor: Color,
    val backgroundBottomColor: Color,
    val accentColor: Color,
    val isFallback: Boolean = false
) {
    val gradientBrush: Brush
        get() = Brush.verticalGradient(
            colors = listOf(
                backgroundTopColor,
                backgroundBottomColor
            )
        )
}

object ArtworkColorExtractor {

    private const val SAMPLE_SIZE = 32

    /**
     * Extracts dynamic artwork colors from a cached thumbnail file path.
     * Gracefully falls back to standard MD3 surface colors if file is null, empty, or unreadable.
     */
    fun extractArtworkColors(
        thumbnailPath: String?,
        defaultSurfaceColor: Color,
        defaultBackgroundColor: Color,
        isDarkTheme: Boolean = true
    ): ArtworkColors {
        if (thumbnailPath.isNullOrBlank()) {
            return createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)
        }

        return try {
            val file = File(thumbnailPath)
            if (!file.exists() || file.length() == 0L) {
                return createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
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
        defaultSurfaceColor: Color,
        defaultBackgroundColor: Color,
        isDarkTheme: Boolean = true
    ): ArtworkColors {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return createFallbackColors(defaultSurfaceColor, defaultBackgroundColor)
        }

        val dominant = extractDominantColor(bitmap) ?: return createFallbackColors(
            defaultSurfaceColor,
            defaultBackgroundColor
        )

        // Generate soft atmospheric gradient colors
        // In dark mode: atmospheric top tint composited with lower opacity over surface container
        // In light mode: delicate pastel top tint composited over surface container
        val atmosphericTintAlpha = if (isDarkTheme) 0.50f else 0.28f
        val backgroundTop = dominant.copy(alpha = atmosphericTintAlpha).compositeOver(defaultSurfaceColor)
        val backgroundBottom = defaultBackgroundColor

        return ArtworkColors(
            dominantColor = dominant,
            backgroundTopColor = backgroundTop,
            backgroundBottomColor = backgroundBottom,
            accentColor = dominant,
            isFallback = false
        )
    }

    /**
     * Extracts the most prominent/vibrant dominant color from a bitmap using hue-binning sampling.
     * Returns null if no non-transparent pixels exist.
     */
    fun extractDominantColor(bitmap: Bitmap): Color? {
        val scaledBitmap = if (bitmap.width > SAMPLE_SIZE || bitmap.height > SAMPLE_SIZE) {
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
        defaultSurfaceColor: Color,
        defaultBackgroundColor: Color
    ): ArtworkColors {
        return ArtworkColors(
            dominantColor = defaultSurfaceColor,
            backgroundTopColor = defaultSurfaceColor,
            backgroundBottomColor = defaultBackgroundColor,
            accentColor = defaultSurfaceColor,
            isFallback = true
        )
    }
}

/**
 * Remembers reactive dynamic artwork colors from the given cover thumbnail path.
 * Computes in background IO dispatcher when thumbnail path changes, falling back to MD3 surfaceContainer.
 */
@Composable
fun rememberArtworkColors(
    thumbnailPath: String?,
    defaultSurfaceColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    defaultBackgroundColor: Color = MaterialTheme.colorScheme.surface,
    isDarkTheme: Boolean = isSystemInDarkTheme()
): State<ArtworkColors> {
    val initialFallback = ArtworkColorExtractor.createFallbackColors(
        defaultSurfaceColor,
        defaultBackgroundColor
    )

    return produceState(
        initialValue = initialFallback,
        thumbnailPath,
        defaultSurfaceColor,
        defaultBackgroundColor,
        isDarkTheme
    ) {
        if (thumbnailPath.isNullOrBlank()) {
            value = initialFallback
            return@produceState
        }

        val extracted = withContext(Dispatchers.IO) {
            ArtworkColorExtractor.extractArtworkColors(
                thumbnailPath = thumbnailPath,
                defaultSurfaceColor = defaultSurfaceColor,
                defaultBackgroundColor = defaultBackgroundColor,
                isDarkTheme = isDarkTheme
            )
        }
        value = extracted
    }
}
