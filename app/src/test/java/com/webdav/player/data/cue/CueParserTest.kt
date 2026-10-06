package com.webdav.player.data.cue

import com.webdav.player.domain.model.VirtualTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CueParserTest {

    @Test
    fun parse_standardSingleFileCue_returnsAllVirtualTracksWithCorrectIntervals() {
        val cue = """
            REM GENRE "Progressive Rock"
            REM DATE 1973
            PERFORMER "Pink Floyd"
            TITLE "The Dark Side of the Moon"
            FILE "DarkSide.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Speak to Me"
                PERFORMER "Pink Floyd"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Breathe"
                PERFORMER "Pink Floyd"
                INDEX 01 01:07:25
              TRACK 03 AUDIO
                TITLE "On the Run"
                PERFORMER "Pink Floyd"
                INDEX 01 03:57:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)

        assertEquals(3, tracks.size)

        // Track 1: 00:00:00 -> 0 ms
        // Next track at 01:07:25 -> (1 * 60 + 7) * 1000 + (25 * 1000 / 75) = 67000 + 333 = 67333 ms
        val track1 = tracks[0]
        assertEquals(1, track1.trackNumber)
        assertEquals("Speak to Me", track1.title)
        assertEquals("Pink Floyd", track1.performer)
        assertEquals(0L, track1.startTimeMs)
        assertEquals(67333L, track1.endTimeMs)
        assertEquals(67333L, track1.durationMs)
        assertEquals("DarkSide.flac", track1.parentAudioPath)

        // Track 2: 01:07:25 (67333 ms) to 03:57:00 (3 * 60 + 57 = 237s = 237000 ms)
        val track2 = tracks[1]
        assertEquals(2, track2.trackNumber)
        assertEquals("Breathe", track2.title)
        assertEquals("Pink Floyd", track2.performer)
        assertEquals(67333L, track2.startTimeMs)
        assertEquals(237000L, track2.endTimeMs)
        assertEquals(237000L - 67333L, track2.durationMs)
        assertEquals("DarkSide.flac", track2.parentAudioPath)

        // Track 3: 03:57:00 (237000 ms) to end (unknown without totalDurationMs)
        val track3 = tracks[2]
        assertEquals(3, track3.trackNumber)
        assertEquals("On the Run", track3.title)
        assertEquals("Pink Floyd", track3.performer)
        assertEquals(237000L, track3.startTimeMs)
        assertNull(track3.endTimeMs)
        assertEquals(0L, track3.durationMs)
        assertEquals("DarkSide.flac", track3.parentAudioPath)
    }

    @Test
    fun parse_frameToMillisCalculation_matchesExactFormula() {
        val cue = """
            FILE "test.wav" WAVE
              TRACK 01 AUDIO
                TITLE "Frame 0"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Frame 1"
                INDEX 01 00:00:01
              TRACK 03 AUDIO
                TITLE "Frame 15"
                INDEX 01 00:00:15
              TRACK 04 AUDIO
                TITLE "Frame 45"
                INDEX 01 00:00:45
              TRACK 05 AUDIO
                TITLE "Frame 74"
                INDEX 01 00:00:74
              TRACK 06 AUDIO
                TITLE "Frame 75"
                INDEX 01 00:00:75
              TRACK 07 AUDIO
                TITLE "1m23s45f"
                INDEX 01 01:23:45
              TRACK 08 AUDIO
                TITLE "Max CD"
                INDEX 01 79:59:74
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(8, tracks.size)

        // (0 * 60 + 0) * 1000 + (0 * 1000 / 75) = 0
        assertEquals(0L, tracks[0].startTimeMs)
        // (0 * 60 + 0) * 1000 + (1 * 1000 / 75) = 13
        assertEquals(13L, tracks[1].startTimeMs)
        // (0 * 60 + 0) * 1000 + (15 * 1000 / 75) = 200
        assertEquals(200L, tracks[2].startTimeMs)
        // (0 * 60 + 0) * 1000 + (45 * 1000 / 75) = 600
        assertEquals(600L, tracks[3].startTimeMs)
        // (0 * 60 + 0) * 1000 + (74 * 1000 / 75) = 986
        assertEquals(986L, tracks[4].startTimeMs)
        // (0 * 60 + 0) * 1000 + (75 * 1000 / 75) = 1000
        assertEquals(1000L, tracks[5].startTimeMs)
        // (1 * 60 + 23) * 1000 + (45 * 1000 / 75) = 83000 + 600 = 83600
        assertEquals(83600L, tracks[6].startTimeMs)
        // (79 * 60 + 59) * 1000 + (74 * 1000 / 75) = 4799000 + 986 = 4799986
        assertEquals(4799986L, tracks[7].startTimeMs)
    }

    @Test
    fun parse_unquotedAndQuotedStrings_parsedCorrectly() {
        val cue = """
            PERFORMER Queen
            TITLE Greatest Hits
            FILE Bohemian.flac WAVE
              TRACK 01 AUDIO
                TITLE Bohemian Rhapsody
                PERFORMER Queen
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Another One Bites the Dust"
                PERFORMER "Queen feat. Guest"
                INDEX 01 05:55:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(2, tracks.size)

        assertEquals("Bohemian Rhapsody", tracks[0].title)
        assertEquals("Queen", tracks[0].performer)
        assertEquals("Bohemian.flac", tracks[0].parentAudioPath)

        assertEquals("Another One Bites the Dust", tracks[1].title)
        assertEquals("Queen feat. Guest", tracks[1].performer)
        assertEquals("Bohemian.flac", tracks[1].parentAudioPath)
    }

    @Test
    fun parse_performerInheritance_trackOverridesAlbumPerformer() {
        val cue = """
            PERFORMER "Album Artist"
            TITLE "Various Compilation"
            FILE "compilation.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Track 1 - Default Artist"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Track 2 - Specific Artist"
                PERFORMER "Special Guest"
                INDEX 01 03:00:00
              TRACK 03 AUDIO
                TITLE "Track 3 - Inherited Again"
                INDEX 01 06:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(3, tracks.size)

        assertEquals("Album Artist", tracks[0].performer)
        assertEquals("Special Guest", tracks[1].performer)
        assertEquals("Album Artist", tracks[2].performer)
    }

    @Test
    fun parse_commentsAndRemLines_ignored() {
        val cue = """
            REM GENRE Rock
            REM DATE 1994
            REM DISCID 12345678
            REM COMMENT "ExactAudioCopy v0.99"
            PERFORMER "Artist"
            TITLE "Album"
            FILE "album.flac" WAVE
              REM Track comment
              TRACK 01 AUDIO
                REM Another comment
                TITLE "Song 1"
                INDEX 01 00:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(1, tracks.size)
        assertEquals("Song 1", tracks[0].title)
    }

    @Test
    fun parse_caseInsensitiveCommands() {
        val cue = """
            performer "artist"
            title "album"
            file "album.flac" wave
              track 01 audio
                title "song 1"
                index 01 00:00:00
              track 02 audio
                title "song 2"
                index 01 02:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(2, tracks.size)
        assertEquals("song 1", tracks[0].title)
        assertEquals("artist", tracks[0].performer)
        assertEquals(0L, tracks[0].startTimeMs)
        assertEquals(120000L, tracks[0].endTimeMs)
    }

    @Test
    fun parse_withParentAudioPath_resolvesFullPath() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Song 1"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Song 2"
                INDEX 01 03:15:00
        """.trimIndent()

        val tracks = CueParser.parse(cue, parentAudioPath = "/music/rock/album.flac")
        assertEquals(2, tracks.size)
        assertEquals("/music/rock/album.flac", tracks[0].parentAudioPath)
        assertEquals("/music/rock/album.flac", tracks[1].parentAudioPath)
    }

    @Test
    fun parse_withTotalDuration_setsLastTrackEndTime() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Song 1"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Song 2"
                INDEX 01 03:00:00
        """.trimIndent()

        val totalDurationMs = 360000L // 6 minutes
        val tracks = CueParser.parse(cue, totalDurationMs = totalDurationMs)

        assertEquals(2, tracks.size)
        assertEquals(180000L, tracks[0].endTimeMs)
        assertEquals(180000L, tracks[0].durationMs)

        assertEquals(180000L, tracks[1].startTimeMs)
        assertEquals(360000L, tracks[1].endTimeMs)
        assertEquals(180000L, tracks[1].durationMs)
    }

    @Test
    fun parse_missingFileTag_returnsEmptyList() {
        val cue = """
            PERFORMER "Artist"
            TITLE "Album"
            TRACK 01 AUDIO
              TITLE "Song 1"
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              TITLE "Song 2"
              INDEX 01 03:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertTrue("CUE without FILE tag must return empty list", tracks.isEmpty())
    }

    @Test
    fun parse_emptyOrBlankContent_returnsEmptyList() {
        assertTrue(CueParser.parse("").isEmpty())
        assertTrue(CueParser.parse("   \n  \t  \n").isEmpty())
    }

    @Test
    fun parse_corruptedOrNonCueText_returnsEmptyListWithoutCrashing() {
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><title>404 Not Found</title></head>
            <body><h1>Not Found</h1></body>
            </html>
        """.trimIndent()

        val tracks = CueParser.parse(htmlContent)
        assertTrue(tracks.isEmpty())

        val binaryJunk = "\u0000\u0001\u0002\uFFFD\u001F\u008B\u0008\u0000"
        val binaryTracks = CueParser.parse(binaryJunk)
        assertTrue(binaryTracks.isEmpty())
    }

    @Test
    fun parse_disorderedTimestamps_returnsValidUsableSubset() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Track 1"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Track 2 - normal"
                INDEX 01 03:00:00
              TRACK 03 AUDIO
                TITLE "Track 3 - disordered backwards"
                INDEX 01 01:00:00
              TRACK 04 AUDIO
                TITLE "Track 4 - forward again"
                INDEX 01 05:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        // Disordered track 3 should be dropped or safely handled so timeline is strictly monotonic
        assertTrue(tracks.isNotEmpty())
        for (i in 0 until tracks.size - 1) {
            assertTrue(
                "Timestamps must be monotonically increasing: ${tracks[i].startTimeMs} < ${tracks[i + 1].startTimeMs}",
                tracks[i].startTimeMs <= tracks[i + 1].startTimeMs
            )
            assertEquals(tracks[i + 1].startTimeMs, tracks[i].endTimeMs)
        }
    }

    @Test
    fun parse_tracksWithoutIndex_skippedSafely() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Track without index"
              TRACK 02 AUDIO
                TITLE "Valid Track"
                INDEX 01 02:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(1, tracks.size)
        assertEquals("Valid Track", tracks[0].title)
        assertEquals(2, tracks[0].trackNumber)
        assertEquals(120000L, tracks[0].startTimeMs)
    }

    @Test
    fun parse_multipleFileCue_calculatesIntervalsPerFile() {
        val cue = """
            FILE "CD1.flac" WAVE
              TRACK 01 AUDIO
                TITLE "CD1 Track 1"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "CD1 Track 2"
                INDEX 01 04:00:00
            FILE "CD2.flac" WAVE
              TRACK 03 AUDIO
                TITLE "CD2 Track 1"
                INDEX 01 00:00:00
              TRACK 04 AUDIO
                TITLE "CD2 Track 2"
                INDEX 01 03:30:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(4, tracks.size)

        assertEquals("CD1.flac", tracks[0].parentAudioPath)
        assertEquals(240000L, tracks[0].endTimeMs)

        // Last track of CD1.flac does not know end time unless totalDuration is known
        assertEquals("CD1.flac", tracks[1].parentAudioPath)
        assertNull(tracks[1].endTimeMs)

        assertEquals("CD2.flac", tracks[2].parentAudioPath)
        assertEquals(0L, tracks[2].startTimeMs)
        assertEquals(210000L, tracks[2].endTimeMs)

        assertEquals("CD2.flac", tracks[3].parentAudioPath)
        assertEquals(210000L, tracks[3].startTimeMs)
        assertNull(tracks[3].endTimeMs)
    }

    @Test
    fun parse_extraWhitespaceAndTabs_handledRobustly() {
        val cue = "\t\tFILE   \"album.flac\"   WAVE  \r\n" +
                "  TRACK   01   AUDIO   \r\n" +
                "\t  TITLE   \"Spaced Song\"  \r\n" +
                "   PERFORMER   \"Spaced Artist\"   \r\n" +
                "      INDEX   01   01:02:03   \r\n"

        val tracks = CueParser.parse(cue)
        assertEquals(1, tracks.size)
        assertEquals("Spaced Song", tracks[0].title)
        assertEquals("Spaced Artist", tracks[0].performer)
        // (1 * 60 + 2) * 1000 + (3 * 1000 / 75) = 62000 + 40 = 62040
        assertEquals(62040L, tracks[0].startTimeMs)
    }

    @Test
    fun parse_index00AndIndex01_usesIndex01AsStartTime() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Track with pregap"
                INDEX 00 00:00:00
                INDEX 01 00:02:00
              TRACK 02 AUDIO
                TITLE "Next Track"
                INDEX 00 03:28:50
                INDEX 01 03:30:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(2, tracks.size)
        // Track 1 starts at INDEX 01 00:02:00 -> 2000 ms
        assertEquals(2000L, tracks[0].startTimeMs)
        // Track 2 starts at INDEX 01 03:30:00 -> (3 * 60 + 30) * 1000 = 210000 ms
        assertEquals(210000L, tracks[1].startTimeMs)
        assertEquals(210000L, tracks[0].endTimeMs)
    }

    @Test
    fun parse_utf8Bom_handledGracefully() {
        val cueWithBom = "\uFEFFFILE \"bom.flac\" WAVE\r\n" +
                "  TRACK 01 AUDIO\r\n" +
                "    TITLE \"BOM Track\"\r\n" +
                "    INDEX 01 00:00:00\r\n"

        val tracks = CueParser.parse(cueWithBom)
        assertEquals(1, tracks.size)
        assertEquals("BOM Track", tracks[0].title)
        assertEquals("bom.flac", tracks[0].parentAudioPath)
    }

    @Test
    fun parse_missingTrackTitle_fallsBackToTrackNumber() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                INDEX 01 02:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(2, tracks.size)
        assertEquals("Track 01", tracks[0].title)
        assertEquals("Track 02", tracks[1].title)
    }

    @Test
    fun parse_noPerformerAtAll_performerIsNull() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "No Artist Track"
                INDEX 01 00:00:00
        """.trimIndent()

        val tracks = CueParser.parse(cue)
        assertEquals(1, tracks.size)
        assertNull(tracks[0].performer)
    }

    @Test
    fun parse_totalDurationShorterThanStartTime_setsEndTimeToNull() {
        val cue = """
            FILE "album.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Track 1"
                INDEX 01 05:00:00
        """.trimIndent()

        // Total duration is 4 minutes, but track starts at 5 minutes
        val tracks = CueParser.parse(cue, totalDurationMs = 240000L)
        assertEquals(1, tracks.size)
        assertEquals(300000L, tracks[0].startTimeMs)
        assertNull(tracks[0].endTimeMs)
        assertEquals(0L, tracks[0].durationMs)
    }

    @Test
    fun parse_allowMissingFileTag_withFallbackPath_parsesSuccessfully() {
        val cueWithoutFile = """
            TRACK 01 AUDIO
              TITLE "Fallback Track"
              INDEX 01 00:00:00
        """.trimIndent()

        val defaultResult = CueParser.parse(cueWithoutFile)
        assertTrue(defaultResult.isEmpty())

        val allowedResult = CueParser.parse(
            content = cueWithoutFile,
            parentAudioPath = "/music/fallback.flac",
            allowMissingFileTag = true
        )
        assertEquals(1, allowedResult.size)
        assertEquals("Fallback Track", allowedResult[0].title)
        assertEquals("/music/fallback.flac", allowedResult[0].parentAudioPath)
    }

    @Test
    fun extractReferencedFiles_singleQuotedFile_returnsCleanFileName() {
        val cue = """
            TITLE "Test Album"
            FILE "DarkSide.flac" WAVE
              TRACK 01 AUDIO
                INDEX 01 00:00:00
        """.trimIndent()

        val files = CueParser.extractReferencedFiles(cue)
        assertEquals(listOf("DarkSide.flac"), files)
    }

    @Test
    fun extractReferencedFiles_multipleFiles_returnsAllInOrder() {
        val cue = """
            FILE "Disc1.flac" WAVE
              TRACK 01 AUDIO
                INDEX 01 00:00:00
            FILE "Disc2.flac" WAVE
              TRACK 02 AUDIO
                INDEX 01 00:00:00
        """.trimIndent()

        val files = CueParser.extractReferencedFiles(cue)
        assertEquals(listOf("Disc1.flac", "Disc2.flac"), files)
    }

    @Test
    fun extractReferencedFiles_blankOrNoFileDirective_returnsEmptyList() {
        val cue = """
            TITLE "No File Album"
            TRACK 01 AUDIO
              INDEX 01 00:00:00
        """.trimIndent()

        assertTrue(CueParser.extractReferencedFiles(cue).isEmpty())
        assertTrue(CueParser.extractReferencedFiles("").isEmpty())
    }
}
