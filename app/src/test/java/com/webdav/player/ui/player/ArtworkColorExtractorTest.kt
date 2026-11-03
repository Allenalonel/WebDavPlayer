package com.webdav.player.ui.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.webdav.player.ui.theme.ExpressiveDarkColorScheme
import com.webdav.player.ui.theme.ExpressiveLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
class ArtworkColorExtractorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val defaultSurface = Color(0xFF1E1E2E)
    private val defaultBackground = Color(0xFF11111B)

    private fun channelLuminance(channel: Float): Double =
        if (channel <= 0.03928) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).toDouble().pow(2.4)
        }

    private fun relativeLuminance(color: Color): Double {
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(
        colorA: Color,
        colorB: Color,
    ): Double {
        val l1 = relativeLuminance(colorA)
        val l2 = relativeLuminance(colorB)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun extractArtworkColors_withNullPath_returnsFallback() {
        val result =
            ArtworkColorExtractor.extractArtworkColors(
                thumbnailPath = null,
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
            )

        assertTrue(result.isFallback)
        assertEquals(defaultSurface, result.dominantColor)
        assertEquals(defaultSurface, result.backgroundTopColor)
        assertEquals(defaultBackground, result.backgroundBottomColor)
        assertNotNull(result.gradientBrush)
    }

    @Test
    fun extractArtworkColors_withBlankPath_returnsFallback() {
        val result =
            ArtworkColorExtractor.extractArtworkColors(
                thumbnailPath = "   ",
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
            )

        assertTrue(result.isFallback)
        assertEquals(defaultSurface, result.dominantColor)
        assertEquals(defaultSurface, result.backgroundTopColor)
    }

    @Test
    fun extractArtworkColors_withNonExistentFile_returnsFallback() {
        val nonExistentPath = File(tempFolder.root, "does_not_exist.jpg").absolutePath
        val result =
            ArtworkColorExtractor.extractArtworkColors(
                thumbnailPath = nonExistentPath,
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
            )

        assertTrue(result.isFallback)
        assertEquals(defaultSurface, result.dominantColor)
    }

    @Test
    fun extractDominantColor_fromPureRedBitmap_identifiesRedDominantColor() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)

        val dominant = ArtworkColorExtractor.extractDominantColor(bitmap)
        assertNotNull(dominant)

        // Red channel should be close to 1.0, green/blue close to 0
        assertEquals(1f, dominant!!.red, 0.05f)
        assertEquals(0f, dominant.green, 0.05f)
        assertEquals(0f, dominant.blue, 0.05f)
    }

    @Test
    fun extractDominantColor_fromPureBlueBitmap_identifiesBlueDominantColor() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)

        val dominant = ArtworkColorExtractor.extractDominantColor(bitmap)
        assertNotNull(dominant)

        assertEquals(0f, dominant!!.red, 0.05f)
        assertEquals(0f, dominant.green, 0.05f)
        assertEquals(1f, dominant.blue, 0.05f)
    }

    @Test
    fun extractDominantColor_transparentBitmap_returnsNull() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)

        val dominant = ArtworkColorExtractor.extractDominantColor(bitmap)
        assertEquals(null, dominant)
    }

    @Test
    fun extractArtworkColors_fromBitmap_generatesAtmosphericGradientWithoutFallback() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)

        val result =
            ArtworkColorExtractor.extractArtworkColors(
                bitmap = bitmap,
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
                isDarkTheme = true,
            )

        assertFalse(result.isFallback)
        // Dominant should be green
        assertEquals(0f, result.dominantColor.red, 0.05f)
        assertEquals(1f, result.dominantColor.green, 0.05f)
        assertEquals(0f, result.dominantColor.blue, 0.05f)

        // Bottom background matches requested background
        assertEquals(defaultBackground, result.backgroundBottomColor)
        assertNotNull(result.gradientBrush)
    }

    @Test
    fun extractArtworkColors_fromValidDiskFile_extractsProperColors() {
        val imageFile = tempFolder.newFile("sample_cover.png")
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val result =
            ArtworkColorExtractor.extractArtworkColors(
                thumbnailPath = imageFile.absolutePath,
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
                isDarkTheme = true,
            )

        assertFalse(result.isFallback)
        // Magenta is Red + Blue
        assertTrue(result.dominantColor.red > 0.8f)
        assertTrue(result.dominantColor.blue > 0.8f)
        assertEquals(defaultBackground, result.backgroundBottomColor)
    }

    @Test
    fun extractArtworkColors_emptyZeroByteFile_returnsFallback() {
        val emptyFile = tempFolder.newFile("empty.png")
        val result =
            ArtworkColorExtractor.extractArtworkColors(
                thumbnailPath = emptyFile.absolutePath,
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
            )

        assertTrue(result.isFallback)
        assertEquals(defaultSurface, result.dominantColor)
    }

    @Test
    fun extractArtworkColors_fallback_providesDefaultMD3ColorSchemes() {
        val result =
            ArtworkColorExtractor.extractArtworkColors(
                thumbnailPath = null,
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
            )

        assertTrue(result.isFallback)
        assertEquals(ExpressiveDarkColorScheme, result.darkColorScheme)
        assertEquals(ExpressiveLightColorScheme, result.lightColorScheme)
        assertEquals(ExpressiveDarkColorScheme, result.colorScheme(isDark = true))
        assertEquals(ExpressiveLightColorScheme, result.colorScheme(isDark = false))
    }

    @Test
    fun deriveDarkColorScheme_derivesCompleteTonalPaletteFromDominantColors() {
        val testSeeds =
            listOf(
                Color(0xFF8A2BE2), // Violet
                Color(0xFF1E90FF), // Dodger Blue
                Color(0xFF2E8B57), // Sea Green
                Color(0xFFDC143C), // Crimson
            )

        for (seed in testSeeds) {
            val scheme = ArtworkColorExtractor.deriveDarkColorScheme(seed)

            // Background & Surface should be dark tones (Tone 6~8)
            val bgLum = relativeLuminance(scheme.background)
            val surfaceLum = relativeLuminance(scheme.surface)
            assertTrue("Dark background luminance must be deep dark, got $bgLum", bgLum < 0.05)
            assertTrue("Dark surface luminance must be deep dark, got $surfaceLum", surfaceLum < 0.05)

            // Surface hierarchy in dark mode: lowest <= low <= container <= high <= highest
            val lumLowest = relativeLuminance(scheme.surfaceContainerLowest)
            val lumLow = relativeLuminance(scheme.surfaceContainerLow)
            val lumContainer = relativeLuminance(scheme.surfaceContainer)
            val lumHigh = relativeLuminance(scheme.surfaceContainerHigh)
            val lumHighest = relativeLuminance(scheme.surfaceContainerHighest)

            assertTrue("Lowest <= Low", lumLowest <= lumLow + 0.001)
            assertTrue("Low <= Container", lumLow <= lumContainer + 0.001)
            assertTrue("Container <= High", lumContainer <= lumHigh + 0.001)
            assertTrue("High <= Highest", lumHigh <= lumHighest + 0.001)

            // High contrast text & primary
            val bgContrast = contrastRatio(scheme.onBackground, scheme.background)
            val surfaceContrast = contrastRatio(scheme.onSurface, scheme.surface)
            val primaryContrast = contrastRatio(scheme.onPrimary, scheme.primary)

            assertTrue("onBackground vs background must be >= 7:1, got $bgContrast", bgContrast >= 7.0)
            assertTrue("onSurface vs surface must be >= 7:1, got $surfaceContrast", surfaceContrast >= 7.0)
            assertTrue("onPrimary vs primary must be >= 4.5:1, got $primaryContrast", primaryContrast >= 4.5)
        }
    }

    @Test
    fun deriveLightColorScheme_derivesCompleteTonalPaletteWithWCAGAAASpecifications() {
        val testSeeds =
            listOf(
                Color(0xFF8A2BE2), // Violet -> pastel #F7F3FB
                Color(0xFFFF8C00), // Orange -> sunset peach #FFF5F0
                Color(0xFF00BFFF), // Blue -> ice sea morning blue #F0F6FA
                Color(0xFF228B22), // Green -> pastoral grass green #F0F8F2
            )

        for (seed in testSeeds) {
            val scheme = ArtworkColorExtractor.deriveLightColorScheme(seed)

            // Background & Surface should be light soft pastel tones (Tone 96~98)
            val bgLum = relativeLuminance(scheme.background)
            val surfaceLum = relativeLuminance(scheme.surface)
            assertTrue("Light background luminance must be ambient pastel >= 0.85, got $bgLum", bgLum >= 0.85)
            assertTrue("Light surface luminance must be ambient pastel >= 0.85, got $surfaceLum", surfaceLum >= 0.85)

            // Surface hierarchy in light mode: lowest (pure white) >= low >= container >= high >= highest
            val lumLowest = relativeLuminance(scheme.surfaceContainerLowest)
            val lumLow = relativeLuminance(scheme.surfaceContainerLow)
            val lumContainer = relativeLuminance(scheme.surfaceContainer)
            val lumHigh = relativeLuminance(scheme.surfaceContainerHigh)
            val lumHighest = relativeLuminance(scheme.surfaceContainerHighest)

            assertEquals("SurfaceContainerLowest must be pure white", Color.White, scheme.surfaceContainerLowest)
            assertTrue("Lowest >= Low in light mode", lumLowest >= lumLow - 0.001)
            assertTrue("Low >= Container in light mode", lumLow >= lumContainer - 0.001)
            assertTrue("Container >= High in light mode", lumContainer >= lumHigh - 0.001)
            assertTrue("High >= Highest in light mode", lumHigh >= lumHighest - 0.001)

            // Secondary text MUST be dark slate charcoal #3A3546
            assertEquals(Color(0xFF3A3546), scheme.onSurfaceVariant)

            // Secondary text contrast against background & surfaceContainer must exceed 8.5:1 (WCAG AAA)
            val secTextBgContrast = contrastRatio(scheme.onSurfaceVariant, scheme.background)
            val secTextContainerContrast = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainer)
            assertTrue(
                "Secondary text vs background contrast must reach WCAG AAA (>= 8.5:1), got $secTextBgContrast",
                secTextBgContrast >= 8.5,
            )
            assertTrue(
                "Secondary text vs surfaceContainer contrast must reach WCAG AAA (>= 8.5:1), got $secTextContainerContrast",
                secTextContainerContrast >= 8.5,
            )

            // Pills: onPrimaryContainer vs primaryContainer must exceed 8.5:1 (WCAG AAA)
            val pillContrast = contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)
            assertTrue(
                "Pill contrast (onPrimaryContainer vs primaryContainer) must reach WCAG AAA (>= 8.5:1), got $pillContrast",
                pillContrast >= 8.5,
            )

            // Primary text contrast
            val bgContrast = contrastRatio(scheme.onBackground, scheme.background)
            val surfaceContrast = contrastRatio(scheme.onSurface, scheme.surface)
            val primaryContrast = contrastRatio(scheme.onPrimary, scheme.primary)

            assertTrue("onBackground vs background must be >= 7:1, got $bgContrast", bgContrast >= 7.0)
            assertTrue("onSurface vs surface must be >= 7:1, got $surfaceContrast", surfaceContrast >= 7.0)
            assertTrue("onPrimary vs primary must be >= 4.5:1, got $primaryContrast", primaryContrast >= 4.5)
        }
    }

    @Test
    fun extractArtworkColors_fromBitmap_providesDynamicDarkAndLightColorSchemes() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)

        val result =
            ArtworkColorExtractor.extractArtworkColors(
                bitmap = bitmap,
                defaultSurfaceColor = defaultSurface,
                defaultBackgroundColor = defaultBackground,
            )

        assertFalse(result.isFallback)
        val darkScheme = result.darkColorScheme
        val lightScheme = result.lightColorScheme

        assertNotNull(darkScheme)
        assertNotNull(lightScheme)

        // Light scheme onSurfaceVariant must be dark slate charcoal #3A3546
        assertEquals(Color(0xFF3A3546), lightScheme.onSurfaceVariant)

        // Pill contrast reaches WCAG AAA (8.5:1)
        val pillContrast = contrastRatio(lightScheme.onPrimaryContainer, lightScheme.primaryContainer)
        assertTrue("Pill contrast >= 8.5:1, got $pillContrast", pillContrast >= 8.5)

        // Secondary text contrast reaches WCAG AAA (8.5:1)
        val secTextContrast = contrastRatio(lightScheme.onSurfaceVariant, lightScheme.background)
        assertTrue("Secondary text contrast >= 8.5:1, got $secTextContrast", secTextContrast >= 8.5)
    }
}
