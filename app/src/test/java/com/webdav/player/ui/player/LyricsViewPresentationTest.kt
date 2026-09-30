package com.webdav.player.ui.player

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsViewPresentationTest {
    @Test
    fun lyricsViewDefaults_typographyHierarchy_differentiatesMainAndTranslation() {
        // Main text sizes
        assertTrue(LyricsViewDefaults.ActiveMainFontSize > LyricsViewDefaults.InactiveMainFontSize)
        assertEquals(20.sp, LyricsViewDefaults.ActiveMainFontSize)
        assertEquals(16.sp, LyricsViewDefaults.InactiveMainFontSize)

        // Translation text sizes (subordinate to main)
        assertTrue(LyricsViewDefaults.ActiveTranslationFontSize < LyricsViewDefaults.ActiveMainFontSize)
        assertTrue(LyricsViewDefaults.InactiveTranslationFontSize < LyricsViewDefaults.InactiveMainFontSize)
        assertTrue(LyricsViewDefaults.ActiveTranslationFontSize > LyricsViewDefaults.InactiveTranslationFontSize)
        assertEquals(14.sp, LyricsViewDefaults.ActiveTranslationFontSize)
        assertEquals(13.sp, LyricsViewDefaults.InactiveTranslationFontSize)

        // Font weights
        assertEquals(FontWeight.Bold, LyricsViewDefaults.ActiveMainFontWeight)
        assertEquals(FontWeight.Normal, LyricsViewDefaults.InactiveMainFontWeight)
        assertEquals(FontWeight.Medium, LyricsViewDefaults.ActiveTranslationFontWeight)
        assertEquals(FontWeight.Normal, LyricsViewDefaults.InactiveTranslationFontWeight)
    }

    @Test
    fun lyricsViewDefaults_contrastAndAlpha_preservesSubtleHierarchy() {
        val activeMainAlpha = LyricsViewDefaults.resolveMainAlpha(isActive = true, isSynchronized = true)
        val activeTransAlpha = LyricsViewDefaults.resolveTranslationAlpha(isActive = true, isSynchronized = true)
        assertEquals(1.0f, activeMainAlpha, 0.001f)
        assertEquals(0.75f, activeTransAlpha, 0.001f)
        assertTrue("Active main must be higher contrast than active translation", activeMainAlpha > activeTransAlpha)

        val inactiveMainAlpha = LyricsViewDefaults.resolveMainAlpha(isActive = false, isSynchronized = true)
        val inactiveTransAlpha = LyricsViewDefaults.resolveTranslationAlpha(isActive = false, isSynchronized = true)
        assertEquals(0.55f, inactiveMainAlpha, 0.001f)
        assertEquals(0.38f, inactiveTransAlpha, 0.001f)
        assertTrue("Inactive main must be higher contrast than inactive translation", inactiveMainAlpha > inactiveTransAlpha)

        val unsyncedMainAlpha = LyricsViewDefaults.resolveMainAlpha(isActive = false, isSynchronized = false)
        val unsyncedTransAlpha = LyricsViewDefaults.resolveTranslationAlpha(isActive = false, isSynchronized = false)
        assertEquals(1.0f, unsyncedMainAlpha, 0.001f)
        assertEquals(0.70f, unsyncedTransAlpha, 0.001f)
        assertTrue(unsyncedMainAlpha > unsyncedTransAlpha)
    }

    @Test
    fun lyricsViewDefaults_spacingAndPadding_matchDesignSpecs() {
        assertEquals(4.dp, LyricsViewDefaults.BilingualSpacing)
        assertEquals(6.dp, LyricsViewDefaults.ItemVerticalPadding)
        assertEquals(16.dp, LyricsViewDefaults.ItemHorizontalPadding)
        assertEquals(18.dp, LyricsViewDefaults.LineSpacing)
    }

    @Test
    fun bilingualLyricLine_structureAndAttributes() {
        val bilingual =
            LyricLine(
                timestampMs = 45000L,
                text = "Welcome to the Hotel California",
                translation = "欢迎来到加州旅馆",
            )
        assertTrue(bilingual.hasTranslation)
        assertEquals("Welcome to the Hotel California", bilingual.mainText)
        assertEquals("欢迎来到加州旅馆", bilingual.translation)

        val monolingual =
            LyricLine(
                timestampMs = 50000L,
                text = "Such a lovely place",
                translation = null,
            )
        assertFalse(monolingual.hasTranslation)
        assertEquals("Such a lovely place", monolingual.mainText)
        assertEquals(null, monolingual.translation)

        val blankTranslation =
            LyricLine(
                timestampMs = 55000L,
                text = "Such a lovely face",
                translation = "   ",
            )
        assertFalse(blankTranslation.hasTranslation)
    }

    @Test
    fun bilingualTimeline_accuratelyLocatesActiveBilingualLine() {
        val lyrics =
            Lyrics(
                lines =
                    listOf(
                        LyricLine(timestampMs = 0L, text = "Intro instrumental", translation = null),
                        LyricLine(timestampMs = 15000L, text = "Verse 1 Line 1", translation = "主歌第一句"),
                        LyricLine(timestampMs = 28000L, text = "Verse 1 Line 2", translation = "主歌第二句"),
                        LyricLine(timestampMs = 42000L, text = "Chorus Line 1", translation = "副歌第一句"),
                    ),
                isSynchronized = true,
            )

        assertEquals(0, lyrics.findActiveLineIndex(0L))
        assertEquals(0, lyrics.findActiveLineIndex(14999L))
        assertEquals(1, lyrics.findActiveLineIndex(15000L))
        assertEquals(1, lyrics.findActiveLineIndex(27999L))
        assertEquals(2, lyrics.findActiveLineIndex(28000L))
        assertEquals(3, lyrics.findActiveLineIndex(42000L))
        assertEquals(3, lyrics.findActiveLineIndex(99999L))
    }
}
