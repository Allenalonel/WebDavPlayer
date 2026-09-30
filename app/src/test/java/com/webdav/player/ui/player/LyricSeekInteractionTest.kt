package com.webdav.player.ui.player

import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSeekInteractionTest {

    private val sampleSyncedLyrics = Lyrics(
        lines = listOf(
            LyricLine(timestampMs = 5000L, text = "First line at 5s"),
            LyricLine(timestampMs = 12000L, text = "Second line at 12s"),
            LyricLine(timestampMs = 25000L, text = "Third line at 25s"),
            LyricLine(timestampMs = 40000L, text = "Fourth line at 40s")
        ),
        isSynchronized = true
    )

    private val sampleUnsyncedLyrics = Lyrics(
        lines = listOf(
            LyricLine(timestampMs = 0L, text = "Unsynced Line 1"),
            LyricLine(timestampMs = 0L, text = "Unsynced Line 2")
        ),
        isSynchronized = false
    )

    @Test
    fun findActiveLineIndex_beforeFirstLine_returnsNegativeOne() {
        val index = sampleSyncedLyrics.findActiveLineIndex(2000L)
        assertEquals(-1, index)
    }

    @Test
    fun findActiveLineIndex_atExactLineTimestamp_returnsCorrectIndex() {
        assertEquals(0, sampleSyncedLyrics.findActiveLineIndex(5000L))
        assertEquals(1, sampleSyncedLyrics.findActiveLineIndex(12000L))
        assertEquals(2, sampleSyncedLyrics.findActiveLineIndex(25000L))
        assertEquals(3, sampleSyncedLyrics.findActiveLineIndex(40000L))
    }

    @Test
    fun findActiveLineIndex_betweenTimestamps_returnsPrecedingLineIndex() {
        // Between 5000 and 12000
        assertEquals(0, sampleSyncedLyrics.findActiveLineIndex(8000L))
        // Between 12000 and 25000
        assertEquals(1, sampleSyncedLyrics.findActiveLineIndex(20000L))
        // Between 25000 and 40000
        assertEquals(2, sampleSyncedLyrics.findActiveLineIndex(30000L))
    }

    @Test
    fun findActiveLineIndex_afterLastLine_returnsLastIndex() {
        val index = sampleSyncedLyrics.findActiveLineIndex(60000L)
        assertEquals(3, index)
    }

    @Test
    fun findActiveLineIndex_forUnsynchronizedLyrics_alwaysReturnsNegativeOne() {
        val index = sampleUnsyncedLyrics.findActiveLineIndex(15000L)
        assertEquals(-1, index)
    }

    @Test
    fun findActiveLineIndex_forEmptyLyrics_returnsNegativeOne() {
        val index = Lyrics.EMPTY.findActiveLineIndex(10000L)
        assertEquals(-1, index)
    }

    @Test
    fun handleLyricLineClick_whenSynchronized_seeksToLineTimestamp() {
        val targetLine = sampleSyncedLyrics.lines[2] // 25000L
        var soughtTimestamp = -1L
        var toggleCoverCalled = false

        handleLyricLineClick(
            line = targetLine,
            isSynchronized = true,
            onSeekTo = { soughtTimestamp = it },
            onToggleCover = { toggleCoverCalled = true }
        )

        assertEquals(25000L, soughtTimestamp)
        assertFalse(toggleCoverCalled)
    }

    @Test
    fun handleLyricLineClick_whenUnsynchronized_invokesToggleCoverInsteadOfSeek() {
        val line = sampleUnsyncedLyrics.lines[0]
        var soughtTimestamp = -1L
        var toggleCoverCalled = false

        handleLyricLineClick(
            line = line,
            isSynchronized = false,
            onSeekTo = { soughtTimestamp = it },
            onToggleCover = { toggleCoverCalled = true }
        )

        assertEquals(-1L, soughtTimestamp)
        assertTrue(toggleCoverCalled)
    }

    @Test
    fun handleLyricLineClick_bilingualSynchronized_seeksToLineTimestamp() {
        val bilingualLine = LyricLine(
            timestampMs = 35000L,
            text = "On a dark desert highway",
            translation = "行驶在漆黑昏暗的荒漠公路上"
        )
        var soughtTimestamp = -1L
        var toggleCoverCalled = false

        handleLyricLineClick(
            line = bilingualLine,
            isSynchronized = true,
            onSeekTo = { soughtTimestamp = it },
            onToggleCover = { toggleCoverCalled = true }
        )

        assertEquals(35000L, soughtTimestamp)
        assertFalse(toggleCoverCalled)
        assertTrue(bilingualLine.hasTranslation)
        assertEquals("On a dark desert highway", bilingualLine.mainText)
        assertEquals("行驶在漆黑昏暗的荒漠公路上", bilingualLine.translation)
    }

    @Test
    fun handleLyricLineClick_bilingualUnsynchronized_invokesToggleCover() {
        val bilingualLine = LyricLine(
            timestampMs = 0L,
            text = "Hello darkness my old friend",
            translation = "你好，黑暗，我的老朋友"
        )
        var soughtTimestamp = -1L
        var toggleCoverCalled = false

        handleLyricLineClick(
            line = bilingualLine,
            isSynchronized = false,
            onSeekTo = { soughtTimestamp = it },
            onToggleCover = { toggleCoverCalled = true }
        )

        assertEquals(-1L, soughtTimestamp)
        assertTrue(toggleCoverCalled)
    }

    @Test
    fun findActiveLineIndex_withBilingualLyrics_accuratelyTracksPosition() {
        val bilingualLyrics = Lyrics(
            lines = listOf(
                LyricLine(timestampMs = 10000L, text = "Line 1", translation = "第一句"),
                LyricLine(timestampMs = 20000L, text = "Line 2", translation = "第二句"),
                LyricLine(timestampMs = 30000L, text = "Line 3", translation = null)
            ),
            isSynchronized = true
        )

        assertEquals(-1, bilingualLyrics.findActiveLineIndex(5000L))
        assertEquals(0, bilingualLyrics.findActiveLineIndex(10000L))
        assertEquals(0, bilingualLyrics.findActiveLineIndex(15000L))
        assertEquals(1, bilingualLyrics.findActiveLineIndex(20000L))
        assertEquals(1, bilingualLyrics.findActiveLineIndex(25000L))
        assertEquals(2, bilingualLyrics.findActiveLineIndex(30000L))
        assertEquals(2, bilingualLyrics.findActiveLineIndex(40000L))
    }
}
