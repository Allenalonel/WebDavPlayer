package com.webdav.player.ui.browser

import androidx.compose.ui.unit.dp
import com.webdav.player.ui.browser.components.EqualizerStateHelper
import com.webdav.player.ui.browser.components.EqualizerWaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerStateHelperTest {

    @Test
    fun resolveEqualizerState_whenMatchingActiveTrackAndPlaying_returnsPlaying() {
        val state = EqualizerStateHelper.resolveEqualizerState(
            trackPath = "/Music/song.flac",
            activeTrackPath = "/Music/song.flac",
            isPlaying = true
        )
        assertEquals(EqualizerWaveState.PLAYING, state)
    }

    @Test
    fun resolveEqualizerState_whenMatchingActiveTrackAndPaused_returnsPaused() {
        val state = EqualizerStateHelper.resolveEqualizerState(
            trackPath = "/Music/song.flac",
            activeTrackPath = "/Music/song.flac",
            isPlaying = false
        )
        assertEquals(EqualizerWaveState.PAUSED, state)
    }

    @Test
    fun resolveEqualizerState_whenNonMatchingTrack_returnsIdleRegardlessOfPlaying() {
        val statePlaying = EqualizerStateHelper.resolveEqualizerState(
            trackPath = "/Music/other.flac",
            activeTrackPath = "/Music/song.flac",
            isPlaying = true
        )
        assertEquals(EqualizerWaveState.IDLE, statePlaying)

        val statePaused = EqualizerStateHelper.resolveEqualizerState(
            trackPath = "/Music/other.flac",
            activeTrackPath = "/Music/song.flac",
            isPlaying = false
        )
        assertEquals(EqualizerWaveState.IDLE, statePaused)
    }

    @Test
    fun resolveEqualizerState_whenActiveTrackPathIsNull_returnsIdle() {
        val state = EqualizerStateHelper.resolveEqualizerState(
            trackPath = "/Music/song.flac",
            activeTrackPath = null,
            isPlaying = true
        )
        assertEquals(EqualizerWaveState.IDLE, state)
    }

    @Test
    fun isTrackActive_matchesPathAccurately() {
        assertTrue(EqualizerStateHelper.isTrackActive("/Music/song.flac", "/Music/song.flac"))
        assertFalse(EqualizerStateHelper.isTrackActive("/Music/song1.flac", "/Music/song2.flac"))
        assertFalse(EqualizerStateHelper.isTrackActive("/Music/song.flac", null))
    }

    @Test
    fun frozenBarHeights_distinctHeightsMatchDesignSpecification() {
        // Paused state requires distinct frozen heights for the 3 bars
        assertEquals(6.dp, EqualizerStateHelper.FrozenBar1Height)
        assertEquals(14.dp, EqualizerStateHelper.FrozenBar2Height)
        assertEquals(8.dp, EqualizerStateHelper.FrozenBar3Height)

        // Ensure distinct non-uniform heights
        assertNotEquals(EqualizerStateHelper.FrozenBar1Height, EqualizerStateHelper.FrozenBar2Height)
        assertNotEquals(EqualizerStateHelper.FrozenBar2Height, EqualizerStateHelper.FrozenBar3Height)
        assertNotEquals(EqualizerStateHelper.FrozenBar1Height, EqualizerStateHelper.FrozenBar3Height)
    }

    @Test
    fun animatedBarBounds_conformToSpec() {
        assertEquals(4.dp, EqualizerStateHelper.MinAnimatedBarHeight)
        assertEquals(20.dp, EqualizerStateHelper.MaxAnimatedBarHeight)

        // Verify frozen heights sit within animation bounds
        assertTrue(EqualizerStateHelper.FrozenBar1Height >= EqualizerStateHelper.MinAnimatedBarHeight)
        assertTrue(EqualizerStateHelper.FrozenBar1Height <= EqualizerStateHelper.MaxAnimatedBarHeight)

        assertTrue(EqualizerStateHelper.FrozenBar2Height >= EqualizerStateHelper.MinAnimatedBarHeight)
        assertTrue(EqualizerStateHelper.FrozenBar2Height <= EqualizerStateHelper.MaxAnimatedBarHeight)

        assertTrue(EqualizerStateHelper.FrozenBar3Height >= EqualizerStateHelper.MinAnimatedBarHeight)
        assertTrue(EqualizerStateHelper.FrozenBar3Height <= EqualizerStateHelper.MaxAnimatedBarHeight)
    }

    @Test
    fun activePausedIdleTransitions_simulatePlaybackLifecycleSequence() {
        val trackA = "/Music/Album/Track01.flac"
        val trackB = "/Music/Album/Track02.flac"

        // Phase 1: App cold launch / no track loaded
        var currentActiveTrack: String? = null
        var isPlaying = false
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackA, currentActiveTrack, isPlaying)
        )
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackB, currentActiveTrack, isPlaying)
        )

        // Phase 2: User taps Track A to play -> Active & Playing
        currentActiveTrack = trackA
        isPlaying = true
        assertEquals(
            EqualizerWaveState.PLAYING,
            EqualizerStateHelper.resolveEqualizerState(trackA, currentActiveTrack, isPlaying)
        )
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackB, currentActiveTrack, isPlaying)
        )

        // Phase 3: User pauses playback -> Track A becomes Paused
        isPlaying = false
        assertEquals(
            EqualizerWaveState.PAUSED,
            EqualizerStateHelper.resolveEqualizerState(trackA, currentActiveTrack, isPlaying)
        )
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackB, currentActiveTrack, isPlaying)
        )

        // Phase 4: User resumes playback -> Track A resumes Playing
        isPlaying = true
        assertEquals(
            EqualizerWaveState.PLAYING,
            EqualizerStateHelper.resolveEqualizerState(trackA, currentActiveTrack, isPlaying)
        )

        // Phase 5: Skip to Next -> Track B becomes Active & Playing; Track A returns to IDLE
        currentActiveTrack = trackB
        isPlaying = true
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackA, currentActiveTrack, isPlaying)
        )
        assertEquals(
            EqualizerWaveState.PLAYING,
            EqualizerStateHelper.resolveEqualizerState(trackB, currentActiveTrack, isPlaying)
        )

        // Phase 6: Track B paused -> Track B becomes Paused
        isPlaying = false
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackA, currentActiveTrack, isPlaying)
        )
        assertEquals(
            EqualizerWaveState.PAUSED,
            EqualizerStateHelper.resolveEqualizerState(trackB, currentActiveTrack, isPlaying)
        )

        // Phase 7: Playback cleared/stopped -> Track B becomes IDLE
        currentActiveTrack = null
        isPlaying = false
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackA, currentActiveTrack, isPlaying)
        )
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(trackB, currentActiveTrack, isPlaying)
        )
    }
}
