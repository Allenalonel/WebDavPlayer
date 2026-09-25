package com.webdav.player.data.lyrics

import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_standardCentisecondTimestamps() {
        val lrc = """
            [00:01.00]First line
            [00:12.34]Second line
            [01:05.80]Third line
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)

        assertTrue(lyrics.isSynchronized)
        assertEquals(3, lyrics.lines.size)
        assertEquals(1000L, lyrics.lines[0].timestampMs)
        assertEquals("First line", lyrics.lines[0].text)
        assertEquals(12340L, lyrics.lines[1].timestampMs)
        assertEquals("Second line", lyrics.lines[1].text)
        assertEquals(65800L, lyrics.lines[2].timestampMs)
        assertEquals("Third line", lyrics.lines[2].text)
    }

    @Test
    fun parse_millisecondTimestamps() {
        val lrc = """
            [00:01.500]Half second
            [02:30.123]Line with ms
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)

        assertTrue(lyrics.isSynchronized)
        assertEquals(2, lyrics.lines.size)
        assertEquals(1500L, lyrics.lines[0].timestampMs)
        assertEquals("Half second", lyrics.lines[0].text)
        assertEquals(150123L, lyrics.lines[1].timestampMs)
        assertEquals("Line with ms", lyrics.lines[1].text)
    }

    @Test
    fun parse_singleDigitFraction() {
        val lrc = "[00:05.5]Tenths"
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(5500L, lyrics.lines[0].timestampMs)
        assertEquals("Tenths", lyrics.lines[0].text)
    }

    @Test
    fun parse_multipleTimestampsPerLine() {
        val lrc = "[00:10.00][00:20.00]Repeated chorus"
        val lyrics = LrcParser.parse(lrc)

        assertTrue(lyrics.isSynchronized)
        assertEquals(2, lyrics.lines.size)
        assertEquals(10000L, lyrics.lines[0].timestampMs)
        assertEquals("Repeated chorus", lyrics.lines[0].text)
        assertEquals(20000L, lyrics.lines[1].timestampMs)
        assertEquals("Repeated chorus", lyrics.lines[1].text)
    }

    @Test
    fun parse_respectsOffsetTag() {
        val lrcPositive = """
            [offset:+500]
            [00:10.00]Shifted forward
        """.trimIndent()
        val lyricsPos = LrcParser.parse(lrcPositive)
        assertEquals(10500L, lyricsPos.lines[0].timestampMs)

        val lrcNegative = """
            [offset:-500]
            [00:10.00]Shifted backward
            [00:00.200]Clamped to zero
        """.trimIndent()
        val lyricsNeg = LrcParser.parse(lrcNegative)
        assertEquals(0L, lyricsNeg.lines[0].timestampMs)
        assertEquals("Clamped to zero", lyricsNeg.lines[0].text)
        assertEquals(9500L, lyricsNeg.lines[1].timestampMs)
        assertEquals("Shifted backward", lyricsNeg.lines[1].text)
    }

    @Test
    fun parse_ignoresMetadataTags() {
        val lrc = """
            [ti:Song Title]
            [ar:Awesome Artist]
            [al:Great Album]
            [by:Lyric Creator]
            [length:03:45]
            [00:05.00]Actual lyric line
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)
        assertEquals(1, lyrics.lines.size)
        assertEquals(5000L, lyrics.lines[0].timestampMs)
        assertEquals("Actual lyric line", lyrics.lines[0].text)
    }

    @Test
    fun parse_sortsLinesChronologically() {
        val lrc = """
            [00:30.00]Second event
            [00:10.00]First event
            [00:50.00]Third event
        """.trimIndent()

        val lyrics = LrcParser.parse(lrc)
        assertEquals(3, lyrics.lines.size)
        assertEquals(10000L, lyrics.lines[0].timestampMs)
        assertEquals("First event", lyrics.lines[0].text)
        assertEquals(30000L, lyrics.lines[1].timestampMs)
        assertEquals("Second event", lyrics.lines[1].text)
        assertEquals(50000L, lyrics.lines[2].timestampMs)
        assertEquals("Third event", lyrics.lines[2].text)
    }

    @Test
    fun parse_fallbackToUnsynchronizedWhenNoTimestamps() {
        val plain = """
            Yesterday, all my troubles seemed so far away
            Now it looks as though they're here to stay
            Oh, I believe in yesterday
        """.trimIndent()

        val lyrics = LrcParser.parse(plain)

        assertFalse(lyrics.isSynchronized)
        assertEquals(3, lyrics.lines.size)
        assertEquals("Yesterday, all my troubles seemed so far away", lyrics.lines[0].text)
        assertEquals("Now it looks as though they're here to stay", lyrics.lines[1].text)
        assertEquals("Oh, I believe in yesterday", lyrics.lines[2].text)
    }

    @Test
    fun parse_emptyAndBlankInput() {
        assertTrue(LrcParser.parse("").isEmpty)
        assertTrue(LrcParser.parse("   \n \t  ").isEmpty)
    }

    @Test
    fun findActiveLineIndex_calculations() {
        val lyrics = Lyrics(
            lines = listOf(
                LyricLine(10000L, "Line 1"),
                LyricLine(20000L, "Line 2"),
                LyricLine(30000L, "Line 3")
            ),
            isSynchronized = true
        )

        // Before first line
        assertEquals(-1, lyrics.findActiveLineIndex(0L))
        assertEquals(-1, lyrics.findActiveLineIndex(9999L))

        // At exact timestamps
        assertEquals(0, lyrics.findActiveLineIndex(10000L))
        assertEquals(1, lyrics.findActiveLineIndex(20000L))
        assertEquals(2, lyrics.findActiveLineIndex(30000L))

        // In-between timestamps
        assertEquals(0, lyrics.findActiveLineIndex(15000L))
        assertEquals(1, lyrics.findActiveLineIndex(25000L))

        // Beyond last line
        assertEquals(2, lyrics.findActiveLineIndex(60000L))

        // Unsynchronized lyrics should always return -1
        val unsynced = lyrics.copy(isSynchronized = false)
        assertEquals(-1, unsynced.findActiveLineIndex(15000L))

        // Empty lyrics
        assertEquals(-1, Lyrics.EMPTY.findActiveLineIndex(15000L))
    }

    @Test
    fun parse_colonSeparatedCentiseconds() {
        val lrc = "[01:23:45]Colon separator"
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(83450L, lyrics.lines[0].timestampMs)
        assertEquals("Colon separator", lyrics.lines[0].text)
    }

    @Test
    fun parse_instrumentalBlankLyricLine() {
        val lrc = """
            [00:10.00]Vocal line
            [00:20.00]
            [00:30.00]Vocal resumed
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(3, lyrics.lines.size)
        assertEquals("", lyrics.lines[1].text)
        assertEquals(20000L, lyrics.lines[1].timestampMs)
    }

    @Test
    fun parse_duplicateTimestamps_preservesBothLines() {
        val lrc = """
            [00:15.00]Line A
            [00:15.00]Line B
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(2, lyrics.lines.size)
        assertEquals(15000L, lyrics.lines[0].timestampMs)
        assertEquals(15000L, lyrics.lines[1].timestampMs)
    }
}
