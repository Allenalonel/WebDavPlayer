package com.webdav.player.ui.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
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

@RunWith(RobolectricTestRunner::class)
class ArtworkColorExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val defaultSurface = Color(0xFF1E1E2E)
    private val defaultBackground = Color(0xFF11111B)

    @Test
    fun extractArtworkColors_withNullPath_returnsFallback() {
        val result = ArtworkColorExtractor.extractArtworkColors(
            thumbnailPath = null,
            defaultSurfaceColor = defaultSurface,
            defaultBackgroundColor = defaultBackground
        )

        assertTrue(result.isFallback)
        assertEquals(defaultSurface, result.dominantColor)
        assertEquals(defaultSurface, result.backgroundTopColor)
        assertEquals(defaultBackground, result.backgroundBottomColor)
        assertNotNull(result.gradientBrush)
    }

    @Test
    fun extractArtworkColors_withBlankPath_returnsFallback() {
        val result = ArtworkColorExtractor.extractArtworkColors(
            thumbnailPath = "   ",
            defaultSurfaceColor = defaultSurface,
            defaultBackgroundColor = defaultBackground
        )

        assertTrue(result.isFallback)
        assertEquals(defaultSurface, result.dominantColor)
        assertEquals(defaultSurface, result.backgroundTopColor)
    }

    @Test
    fun extractArtworkColors_withNonExistentFile_returnsFallback() {
        val nonExistentPath = File(tempFolder.root, "does_not_exist.jpg").absolutePath
        val result = ArtworkColorExtractor.extractArtworkColors(
            thumbnailPath = nonExistentPath,
            defaultSurfaceColor = defaultSurface,
            defaultBackgroundColor = defaultBackground
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

        val result = ArtworkColorExtractor.extractArtworkColors(
            bitmap = bitmap,
            defaultSurfaceColor = defaultSurface,
            defaultBackgroundColor = defaultBackground,
            isDarkTheme = true
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

        val result = ArtworkColorExtractor.extractArtworkColors(
            thumbnailPath = imageFile.absolutePath,
            defaultSurfaceColor = defaultSurface,
            defaultBackgroundColor = defaultBackground,
            isDarkTheme = true
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
        val result = ArtworkColorExtractor.extractArtworkColors(
            thumbnailPath = emptyFile.absolutePath,
            defaultSurfaceColor = defaultSurface,
            defaultBackgroundColor = defaultBackground
        )

        assertTrue(result.isFallback)
        assertEquals(defaultSurface, result.dominantColor)
    }
}
