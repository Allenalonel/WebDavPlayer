package com.webdav.player.domain.session

import com.webdav.player.domain.model.VirtualTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualTimelineEngineTest {

    private fun sampleTracks(): List<VirtualTrack> = listOf(
        VirtualTrack(
            trackNumber = 1,
            title = "Track 1",
            performer = "Artist 1",
            startTimeMs = 0L,
            endTimeMs = 180_000L,
            parentAudioPath = "/music/album.flac",
        ),
        VirtualTrack(
            trackNumber = 2,
            title = "Track 2",
            performer = "Artist 2",
            startTimeMs = 180_000L,
            endTimeMs = 420_000L,
            parentAudioPath = "/music/album.flac",
        ),
        VirtualTrack(
            trackNumber = 3,
            title = "Track 3",
            performer = "Artist 3",
            startTimeMs = 420_000L,
            endTimeMs = 600_000L,
            parentAudioPath = "/music/album.flac",
        ),
    )

    @Test
    fun initialState_isInactiveAndEmpty() {
        val engine = VirtualTimelineEngine()

        assertFalse(engine.isActive)
        assertEquals(-1, engine.currentIndex)
        assertNull(engine.activeTrack)
        assertTrue(engine.activeTracks.isEmpty())
    }

    @Test
    fun loadTracks_activatesEngineAndSetsInitialIndex() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()

        val startMs = engine.loadTracks(tracks, initialIndex = 1)

        assertTrue(engine.isActive)
        assertEquals(1, engine.currentIndex)
        assertEquals(tracks[1], engine.activeTrack)
        assertEquals(3, engine.activeTracks.size)
        assertEquals(180_000L, startMs)
    }

    @Test
    fun loadTracks_clampsOutOfBoundsInitialIndex() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()

        val startNegative = engine.loadTracks(tracks, initialIndex = -5)
        assertEquals(0, engine.currentIndex)
        assertEquals(0L, startNegative)

        val startOverflow = engine.loadTracks(tracks, initialIndex = 99)
        assertEquals(2, engine.currentIndex)
        assertEquals(420_000L, startOverflow)
    }

    @Test
    fun clear_resetsStateToInactive() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 0)
        assertTrue(engine.isActive)

        engine.clear()

        assertFalse(engine.isActive)
        assertEquals(-1, engine.currentIndex)
        assertNull(engine.activeTrack)
        assertTrue(engine.activeTracks.isEmpty())
    }

    @Test
    fun mapToVirtualProgress_whenInactive_returnsGlobalValuesUnchanged() {
        val engine = VirtualTimelineEngine()

        val progress = engine.mapToVirtualProgress(
            globalPositionMs = 50_000L,
            globalDurationMs = 600_000L,
            bufferedGlobalPositionMs = 80_000L,
        )

        assertEquals(50_000L, progress.currentPositionMs)
        assertEquals(600_000L, progress.durationMs)
        assertEquals(80_000L, progress.bufferedPositionMs)
    }

    @Test
    fun mapToVirtualProgress_whenActiveOnFirstTrack_calculatesRelativeProgress() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 0) // Track 1: 0L..180_000L

        val progress = engine.mapToVirtualProgress(
            globalPositionMs = 45_000L,
            globalDurationMs = 600_000L,
            bufferedGlobalPositionMs = 90_000L,
        )

        assertEquals(45_000L, progress.currentPositionMs)
        assertEquals(180_000L, progress.durationMs)
        assertEquals(90_000L, progress.bufferedPositionMs)
    }

    @Test
    fun mapToVirtualProgress_whenActiveOnMiddleTrack_offsetsByStartTime() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 1) // Track 2: 180_000L..420_000L (duration = 240_000L)

        // Global position is 200_000L -> relative is 20_000L
        val progress = engine.mapToVirtualProgress(
            globalPositionMs = 200_000L,
            globalDurationMs = 600_000L,
            bufferedGlobalPositionMs = 250_000L,
        )

        assertEquals(20_000L, progress.currentPositionMs)
        assertEquals(240_000L, progress.durationMs)
        assertEquals(70_000L, progress.bufferedPositionMs)
    }

    @Test
    fun mapToVirtualProgress_whenGlobalPositionBeforeTrackStart_clampsToZero() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 1) // startTime = 180_000L

        // Edge case: player reports momentary lag before seek completes
        val progress = engine.mapToVirtualProgress(
            globalPositionMs = 175_000L,
            globalDurationMs = 600_000L,
            bufferedGlobalPositionMs = 170_000L,
        )

        assertEquals(0L, progress.currentPositionMs)
        assertEquals(240_000L, progress.durationMs)
        assertEquals(0L, progress.bufferedPositionMs)
    }

    @Test
    fun mapToVirtualProgress_whenTrackDurationZeroAndGlobalDurationProvided_fallsBackToRemaining() {
        val engine = VirtualTimelineEngine()
        val openEndedTrack = VirtualTrack(
            trackNumber = 1,
            title = "Final Track",
            startTimeMs = 500_000L,
            endTimeMs = null, // durationMs is 0L
            parentAudioPath = "/music/album.flac",
        )
        engine.loadTracks(listOf(openEndedTrack), initialIndex = 0)

        val progress = engine.mapToVirtualProgress(
            globalPositionMs = 520_000L,
            globalDurationMs = 600_000L,
            bufferedGlobalPositionMs = 550_000L,
        )

        assertEquals(20_000L, progress.currentPositionMs)
        assertEquals(100_000L, progress.durationMs)
        assertEquals(50_000L, progress.bufferedPositionMs)
    }

    @Test
    fun onPositionUpdate_whenInactive_returnsNoChange() {
        val engine = VirtualTimelineEngine()
        val result = engine.onPositionUpdate(50_000L)
        assertTrue(result is VirtualTimelineEngine.TransitionResult.NoChange)
    }

    @Test
    fun onPositionUpdate_withinCurrentTrackBounds_returnsNoChange() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 0) // Track 1: 0L..180_000L

        val result1 = engine.onPositionUpdate(50_000L)
        assertTrue(result1 is VirtualTimelineEngine.TransitionResult.NoChange)
        assertEquals(0, engine.currentIndex)

        val result2 = engine.onPositionUpdate(179_999L)
        assertTrue(result2 is VirtualTimelineEngine.TransitionResult.NoChange)
        assertEquals(0, engine.currentIndex)
    }

    @Test
    fun onPositionUpdate_whenReachingBoundary_transitionsToNextTrack() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 0) // Track 1: 0L..180_000L

        // Exact boundary crossing point
        val result = engine.onPositionUpdate(180_000L)

        assertTrue(result is VirtualTimelineEngine.TransitionResult.Transitioned)
        val transitioned = result as VirtualTimelineEngine.TransitionResult.Transitioned
        assertEquals(0, transitioned.oldIndex)
        assertEquals(1, transitioned.newIndex)
        assertEquals(tracks[1], transitioned.newTrack)
        assertEquals(1, engine.currentIndex)
        assertEquals(tracks[1], engine.activeTrack)
    }

    @Test
    fun onPositionUpdate_whenCrossingMultipleTrackBoundaries_jumpsToTargetTrack() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 0)

        // Directly jump to 450_000L (inside Track 3: 420_000L..600_000L)
        val result = engine.onPositionUpdate(450_000L)

        assertTrue(result is VirtualTimelineEngine.TransitionResult.Transitioned)
        val transitioned = result as VirtualTimelineEngine.TransitionResult.Transitioned
        assertEquals(0, transitioned.oldIndex)
        assertEquals(2, transitioned.newIndex)
        assertEquals(tracks[2], transitioned.newTrack)
        assertEquals(2, engine.currentIndex)
    }

    @Test
    fun onPositionUpdate_whenSeekingBackward_revertsToEarlierTrack() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 2) // Track 3: 420_000L..600_000L

        // Stream position rewound to 100_000L (inside Track 1)
        val result = engine.onPositionUpdate(100_000L)

        assertTrue(result is VirtualTimelineEngine.TransitionResult.Transitioned)
        val transitioned = result as VirtualTimelineEngine.TransitionResult.Transitioned
        assertEquals(2, transitioned.oldIndex)
        assertEquals(0, transitioned.newIndex)
        assertEquals(tracks[0], transitioned.newTrack)
        assertEquals(0, engine.currentIndex)
    }

    @Test
    fun onPositionUpdate_onLastTrackPastEnd_retainsLastTrackWithoutThrowing() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 2) // Track 3: 420_000L..600_000L

        val result = engine.onPositionUpdate(650_000L)
        assertTrue(result is VirtualTimelineEngine.TransitionResult.NoChange)
        assertEquals(2, engine.currentIndex)
    }

    @Test
    fun calculateSeekTargetMs_whenInactive_returnsRelativeOffsetUnchanged() {
        val engine = VirtualTimelineEngine()
        val target = engine.calculateSeekTargetMs(relativeOffsetMs = 25_000L)
        assertEquals(25_000L, target)
    }

    @Test
    fun calculateSeekTargetMs_withinTrackBounds_offsetsByTrackStartTime() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 1) // Track 2: 180_000L..420_000L (duration = 240_000L)

        // Seeking to 30s within Track 2
        val target = engine.calculateSeekTargetMs(relativeOffsetMs = 30_000L)
        assertEquals(210_000L, target)
    }

    @Test
    fun calculateSeekTargetMs_whenNegative_clampsToTrackStart() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 1) // Track 2: startTime = 180_000L

        val target = engine.calculateSeekTargetMs(relativeOffsetMs = -5_000L)
        assertEquals(180_000L, target)
    }

    @Test
    fun calculateSeekTargetMs_whenExceedsTrackDuration_clampsToTrackEnd() {
        val engine = VirtualTimelineEngine()
        engine.loadTracks(sampleTracks(), initialIndex = 1) // Track 2: 180_000L..420_000L (duration = 240_000L)

        // Seeking to 300s when duration is only 240s
        val target = engine.calculateSeekTargetMs(relativeOffsetMs = 300_000L)
        assertEquals(420_000L, target)
    }

    @Test
    fun calculateSeekTargetMs_whenLastTrackWithoutEndTime_clampsToGlobalDuration() {
        val engine = VirtualTimelineEngine()
        val openEndedTrack = VirtualTrack(
            trackNumber = 3,
            title = "Final Track",
            startTimeMs = 420_000L,
            endTimeMs = null,
            parentAudioPath = "/music/album.flac",
        )
        engine.loadTracks(listOf(openEndedTrack), initialIndex = 0)

        // Global duration is 500_000L, so max relative is 80_000L
        val target = engine.calculateSeekTargetMs(relativeOffsetMs = 150_000L, globalDurationMs = 500_000L)
        assertEquals(500_000L, target)
    }

    @Test
    fun getNextTrackSeekPosition_whenInactive_returnsNull() {
        val engine = VirtualTimelineEngine()
        assertNull(engine.getNextTrackSeekPosition())
    }

    @Test
    fun getNextTrackSeekPosition_advancesToNextTrackStartTime() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 0) // Track 1

        val result = engine.getNextTrackSeekPosition()

        assertNotNull(result)
        assertEquals(1, result?.trackIndex)
        assertEquals(tracks[1], result?.track)
        assertEquals(180_000L, result?.targetPositionMs)
        assertEquals(1, engine.currentIndex)
    }

    @Test
    fun getNextTrackSeekPosition_atEndOfTracks_loopsToFirstTrackInListLoop() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 2) // Last track (Track 3)

        val result = engine.getNextTrackSeekPosition(com.webdav.player.domain.model.PlaybackMode.LIST_LOOP)

        assertNotNull(result)
        assertEquals(0, result?.trackIndex)
        assertEquals(tracks[0], result?.track)
        assertEquals(0L, result?.targetPositionMs)
        assertEquals(0, engine.currentIndex)
    }

    @Test
    fun getNextTrackSeekPosition_inSingleLoop_replaysCurrentTrack() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 1)

        val result = engine.getNextTrackSeekPosition(com.webdav.player.domain.model.PlaybackMode.SINGLE_LOOP)

        assertNotNull(result)
        assertEquals(1, result?.trackIndex)
        assertEquals(tracks[1], result?.track)
        assertEquals(180_000L, result?.targetPositionMs)
        assertEquals(1, engine.currentIndex)
    }

    @Test
    fun getPreviousTrackSeekPosition_whenPlayedMoreThanThreeSeconds_restartsCurrentTrack() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 1) // Track 2 starts at 180_000L

        // Played 3001 ms -> relative position is 3001L > 3000L
        val result = engine.getPreviousTrackSeekPosition(globalPositionMs = 183_001L)

        assertNotNull(result)
        assertEquals(1, result?.trackIndex)
        assertEquals(tracks[1], result?.track)
        assertEquals(180_000L, result?.targetPositionMs)
        assertEquals(1, engine.currentIndex)
    }

    @Test
    fun getPreviousTrackSeekPosition_whenPlayedThreeSecondsOrLess_stepsToPreviousTrack() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 1) // Track 2 starts at 180_000L

        // Played exactly 3000 ms -> relative position <= 3000L -> steps to previous track
        val result = engine.getPreviousTrackSeekPosition(globalPositionMs = 183_000L)

        assertNotNull(result)
        assertEquals(0, result?.trackIndex)
        assertEquals(tracks[0], result?.track)
        assertEquals(0L, result?.targetPositionMs)
        assertEquals(0, engine.currentIndex)
    }

    @Test
    fun getPreviousTrackSeekPosition_atFirstTrackUnderThreeSeconds_loopsToLastTrackInListLoop() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 0) // Track 1 starts at 0L

        // Played 1500 ms (< 3000 ms) at index 0
        val result = engine.getPreviousTrackSeekPosition(
            globalPositionMs = 1500L,
            mode = com.webdav.player.domain.model.PlaybackMode.LIST_LOOP,
        )

        assertNotNull(result)
        assertEquals(2, result?.trackIndex)
        assertEquals(tracks[2], result?.track)
        assertEquals(420_000L, result?.targetPositionMs)
        assertEquals(2, engine.currentIndex)
    }

    @Test
    fun seekToTrackIndex_jumpsToTargetTrack() {
        val engine = VirtualTimelineEngine()
        val tracks = sampleTracks()
        engine.loadTracks(tracks, initialIndex = 0)

        val result = engine.seekToTrackIndex(2)

        assertNotNull(result)
        assertEquals(2, result?.trackIndex)
        assertEquals(tracks[2], result?.track)
        assertEquals(420_000L, result?.targetPositionMs)
        assertEquals(2, engine.currentIndex)

        val invalid = engine.seekToTrackIndex(99)
        assertNull(invalid)
        assertEquals(2, engine.currentIndex)
    }
}
