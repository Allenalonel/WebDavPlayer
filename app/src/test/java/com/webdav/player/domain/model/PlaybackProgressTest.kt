package com.webdav.player.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressTest {
    @Test
    fun formatMs_zeroAndNegative_returnsZeroTime() {
        assertEquals("00:00", PlaybackProgress.formatMs(0L))
        assertEquals("00:00", PlaybackProgress.formatMs(-100L))
        assertEquals("00:00", PlaybackProgress.formatMs(-5000L))
    }

    @Test
    fun formatMs_secondsAndMinutes() {
        assertEquals("00:05", PlaybackProgress.formatMs(5_000L))
        assertEquals("01:23", PlaybackProgress.formatMs(83_000L))
        assertEquals("04:05", PlaybackProgress.formatMs(245_000L))
        assertEquals("59:59", PlaybackProgress.formatMs(3_599_000L))
    }

    @Test
    fun formatMs_oneHourAndBeyond() {
        assertEquals("1:00:00", PlaybackProgress.formatMs(3_600_000L))
        assertEquals("1:05:20", PlaybackProgress.formatMs(3_920_000L))
        assertEquals("10:00:01", PlaybackProgress.formatMs(36_001_000L))
    }

    @Test
    fun formattedProperties_reflectCurrentPositionAndDuration() {
        val progress =
            PlaybackProgress(
                currentPositionMs = 83_000L,
                durationMs = 245_000L,
                bufferedPositionMs = 120_000L,
            )
        assertEquals("01:23", progress.formattedCurrentPosition)
        assertEquals("04:05", progress.formattedDuration)

        val longProgress =
            PlaybackProgress(
                currentPositionMs = 3_600_000L,
                durationMs = 3_920_000L,
            )
        assertEquals("1:00:00", longProgress.formattedCurrentPosition)
        assertEquals("1:05:20", longProgress.formattedDuration)
    }

    @Test
    fun progressFraction_calculatesCorrectly() {
        val progress =
            PlaybackProgress(
                currentPositionMs = 50_000L,
                durationMs = 100_000L,
                bufferedPositionMs = 75_000L,
            )
        assertEquals(0.5f, progress.progressFraction, 0.001f)
        assertEquals(0.75f, progress.bufferedFraction, 0.001f)
    }

    @Test
    fun zeroCompanion_hasZeroValuesAndFormatsToZero() {
        assertEquals(0L, PlaybackProgress.ZERO.currentPositionMs)
        assertEquals(0L, PlaybackProgress.ZERO.durationMs)
        assertEquals("00:00", PlaybackProgress.ZERO.formattedCurrentPosition)
        assertEquals("00:00", PlaybackProgress.ZERO.formattedDuration)
    }
}
