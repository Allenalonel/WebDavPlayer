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
    fun parse_duplicateIdenticalLines_within300ms_deduplicated() {
        val lrc = """
            [00:10.00]Echo line
            [00:10.15]Echo line
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(10000L, lyrics.lines[0].timestampMs)
        assertEquals("Echo line", lyrics.lines[0].text)
        assertEquals(null, lyrics.lines[0].translation)
    }

    @Test
    fun parse_duplicateExactTimestampAndText_deduplicated() {
        val lrc = """
            [00:10.00]Identical line
            [00:10.00]Identical line
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(10000L, lyrics.lines[0].timestampMs)
        assertEquals("Identical line", lyrics.lines[0].text)
        assertEquals(null, lyrics.lines[0].translation)
    }

    @Test
    fun parse_bilingualIdenticalTimestamp_mergesAsTranslation() {
        val lrc = """
            [00:15.00]Line A
            [00:15.00]Line B
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(15000L, lyrics.lines[0].timestampMs)
        assertEquals("Line A", lyrics.lines[0].text)
        assertEquals("Line B", lyrics.lines[0].translation)
        assertEquals("Line A", lyrics.lines[0].mainText)
        assertTrue(lyrics.lines[0].hasTranslation)
    }

    @Test
    fun parse_bilingualNearlyIdenticalTimestamp_within300ms_mergesAsTranslation() {
        val lrc = """
            [00:15.000]Hello world
            [00:15.200]你好世界
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(15000L, lyrics.lines[0].timestampMs)
        assertEquals("Hello world", lyrics.lines[0].text)
        assertEquals("你好世界", lyrics.lines[0].translation)
    }

    @Test
    fun parse_bilingualTimestamps_beyond300ms_keepsSeparateLines() {
        val lrc = """
            [00:15.000]Line A
            [00:15.350]Line B
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(2, lyrics.lines.size)
        assertEquals(15000L, lyrics.lines[0].timestampMs)
        assertEquals("Line A", lyrics.lines[0].text)
        assertEquals(null, lyrics.lines[0].translation)
        assertEquals(15350L, lyrics.lines[1].timestampMs)
        assertEquals("Line B", lyrics.lines[1].text)
        assertEquals(null, lyrics.lines[1].translation)
    }

    @Test
    fun parse_inlineSquareBracketTimestamps_strippedWithoutLineDuplication() {
        val lrc = "[01:00.00] Word1 [01:00.50] Word2 [01:01.00] Word3"
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(60000L, lyrics.lines[0].timestampMs)
        assertEquals("Word1 Word2 Word3", lyrics.lines[0].text)
    }

    @Test
    fun parse_inlineAngleBracketKaraokeTimestamps_strippedWithoutLineDuplication() {
        val lrc = "[01:00.00]<01:00.00>Never <01:00.30>gonna <01:00.60>give <01:00.90>you <01:01.20>up"
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(60000L, lyrics.lines[0].timestampMs)
        assertEquals("Never gonna give you up", lyrics.lines[0].text)
    }

    @Test
    fun parse_cjkKaraokeInlineTimestamps_strippedCleanly() {
        val lrc = "[00:01.00]我[00:01.50]爱[00:02.00]你"
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(1000L, lyrics.lines[0].timestampMs)
        assertEquals("我爱你", lyrics.lines[0].text)
    }

    @Test
    fun parse_multiTimestampWithInlineTimestamps_stripsInlineAndExpandsLeading() {
        val lrc = "[01:00.00][02:30.00]Chorus [01:00.50]with [02:30.50]inline"
        val lyrics = LrcParser.parse(lrc)

        assertEquals(2, lyrics.lines.size)
        assertEquals(60000L, lyrics.lines[0].timestampMs)
        assertEquals("Chorus with inline", lyrics.lines[0].text)
        assertEquals(150000L, lyrics.lines[1].timestampMs)
        assertEquals("Chorus with inline", lyrics.lines[1].text)
    }

    @Test
    fun parse_bilingualRepeatedChorus_mergesTranslationAtBothTimestamps() {
        val lrc = """
            [01:00.00][02:00.00]Chorus line
            [01:00.00][02:00.00]副歌行
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(2, lyrics.lines.size)
        assertEquals(60000L, lyrics.lines[0].timestampMs)
        assertEquals("Chorus line", lyrics.lines[0].text)
        assertEquals("副歌行", lyrics.lines[0].translation)
        assertEquals(120000L, lyrics.lines[1].timestampMs)
        assertEquals("Chorus line", lyrics.lines[1].text)
        assertEquals("副歌行", lyrics.lines[1].translation)
    }

    @Test
    fun parse_deduplicateDuplicateBeforeBilingualMerge() {
        val lrc = """
            [00:10.00]Main line
            [00:10.10]Main line
            [00:10.15]翻译行
            [00:10.20]翻译行
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc)

        assertEquals(1, lyrics.lines.size)
        assertEquals(10000L, lyrics.lines[0].timestampMs)
        assertEquals("Main line", lyrics.lines[0].text)
        assertEquals("翻译行", lyrics.lines[0].translation)
    }
}
