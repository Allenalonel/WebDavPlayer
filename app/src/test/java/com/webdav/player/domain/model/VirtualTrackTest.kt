package com.webdav.player.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VirtualTrackTest {

    @Test
    fun durationMs_whenEndTimeMsIsNull_returnsZero() {
        val track = VirtualTrack(
            trackNumber = 1,
            title = "Track 1",
            performer = "Artist",
            startTimeMs = 1000L,
            endTimeMs = null,
            parentAudioPath = "/music/album.flac"
        )

        assertNull(track.endTimeMs)
        assertEquals(0L, track.durationMs)
    }

    @Test
    fun durationMs_whenEndTimeMsIsGreaterThanStartTimeMs_returnsDifference() {
        val track = VirtualTrack(
            trackNumber = 2,
            title = "Track 2",
            performer = "Artist",
            startTimeMs = 10000L,
            endTimeMs = 75000L,
            parentAudioPath = "/music/album.flac"
        )

        assertEquals(65000L, track.durationMs)
    }

    @Test
    fun durationMs_whenEndTimeMsIsEqualOrLessThanStartTimeMs_returnsZero() {
        val trackEqual = VirtualTrack(
            trackNumber = 3,
            title = "Track 3",
            performer = "Artist",
            startTimeMs = 10000L,
            endTimeMs = 10000L,
            parentAudioPath = "/music/album.flac"
        )
        assertEquals(0L, trackEqual.durationMs)

        val trackLess = VirtualTrack(
            trackNumber = 4,
            title = "Track 4",
            performer = "Artist",
            startTimeMs = 20000L,
            endTimeMs = 10000L,
            parentAudioPath = "/music/album.flac"
        )
        assertEquals(0L, trackLess.durationMs)
    }

    @Test
    fun properties_retainedCorrectly() {
        val track = VirtualTrack(
            trackNumber = 5,
            title = "Title",
            performer = "Performer",
            startTimeMs = 5000L,
            endTimeMs = 15000L,
            parentAudioPath = "/path/test.flac"
        )

        assertEquals(5, track.trackNumber)
        assertEquals("Title", track.title)
        assertEquals("Performer", track.performer)
        assertEquals(5000L, track.startTimeMs)
        assertEquals(15000L, track.endTimeMs)
        assertEquals("/path/test.flac", track.parentAudioPath)
    }
}
