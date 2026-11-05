package com.webdav.player.ui.player

import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.navigation.MainNavigationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerSheetExpansionTest {
    private lateinit var coordinator: MainNavigationCoordinator

    private val sampleTrack =
        AudioTrack(
            id = "1:/music/track1.flac",
            serverId = 1L,
            remotePath = "/music/track1.flac",
            title = "Shine On You Crazy Diamond",
            artist = "Pink Floyd",
            album = "Wish You Were Here",
            format = AudioFormat.FLAC,
        )

    private fun sessionWithTrack(track: AudioTrack?): PlayerSessionState =
        if (track == null) {
            PlayerSessionState()
        } else {
            PlayerSessionState(queue = PlaybackQueue(listOf(track), 0))
        }

    @Before
    fun setUp() {
        coordinator = MainNavigationCoordinator()
    }

    @Test
    fun initialState_sheetIsCollapsed() {
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)
        assertFalse(coordinator.uiState.value.isMiniPlayerVisible)
    }

    @Test
    fun miniPlayerClickOrSwipeUp_expandsSheet_whenTrackIsPresent() {
        coordinator.onSessionStateChanged(sessionWithTrack(sampleTrack))
        assertTrue(coordinator.uiState.value.isMiniPlayerVisible)
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)

        // User taps or swipes up on Docked Mini-Player
        coordinator.expandFullPlayer()
        assertTrue(coordinator.uiState.value.isFullPlayerExpanded)
    }

    @Test
    fun collapseArrowOrDownwardSwipe_dismissesSheet() {
        coordinator.onSessionStateChanged(sessionWithTrack(sampleTrack))
        coordinator.expandFullPlayer()
        assertTrue(coordinator.uiState.value.isFullPlayerExpanded)

        // User taps collapse arrow or swipes downward
        coordinator.collapseFullPlayer()
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)
    }

    @Test
    fun clearTrack_automaticallyCollapsesExpandedSheet() {
        coordinator.onSessionStateChanged(sessionWithTrack(sampleTrack))
        coordinator.expandFullPlayer()
        assertTrue(coordinator.uiState.value.isFullPlayerExpanded)

        // Track ends or is cleared
        coordinator.onSessionStateChanged(sessionWithTrack(null))
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)
        assertFalse(coordinator.uiState.value.isMiniPlayerVisible)
    }

    @Test
    fun expandRequest_ignoredWhenNoTrackIsPlaying() {
        coordinator.expandFullPlayer()
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)
    }

    @Test
    fun formatTrackArtistAndFormat_withValidTrack_formatsProperly() {
        val formatted = formatTrackArtistAndFormat(sampleTrack)
        assertEquals("Pink Floyd · FLAC", formatted)
    }

    @Test
    fun formatTrackArtistAndFormat_withNullArtist_usesFallback() {
        val trackWithoutArtist = sampleTrack.copy(artist = null, format = AudioFormat.MP3)
        val formatted = formatTrackArtistAndFormat(trackWithoutArtist)
        assertEquals("未知艺术家 · MP3", formatted)
    }

    @Test
    fun formatTrackArtistAndFormat_withNullTrack_returnsDefault() {
        val formatted = formatTrackArtistAndFormat(null)
        assertEquals("未知艺术家", formatted)
    }

    // --- Morphing Interpolation Math Tests ---

    @Test
    fun morphingMath_containerHeight_interpolatesSmoothly() {
        val screenHeight = 840.dp
        assertEquals(60.dp, MorphingPlayerMath.containerHeight(screenHeight, 0.0f))
        assertEquals(840.dp, MorphingPlayerMath.containerHeight(screenHeight, 1.0f))
        assertEquals(450.dp, MorphingPlayerMath.containerHeight(screenHeight, 0.5f))

        // Clamping check
        assertEquals(60.dp, MorphingPlayerMath.containerHeight(screenHeight, -0.2f))
        assertEquals(840.dp, MorphingPlayerMath.containerHeight(screenHeight, 1.5f))
    }

    @Test
    fun morphingMath_containerMargins_interpolatesFromCapsuleToEdgeToEdge() {
        val collapsedBottomPadding = 68.dp
        // Horizontal margins
        assertEquals(14.dp, MorphingPlayerMath.containerHorizontalMargin(0.0f))
        assertEquals(0.dp, MorphingPlayerMath.containerHorizontalMargin(1.0f))
        assertEquals(7.dp, MorphingPlayerMath.containerHorizontalMargin(0.5f))

        // Bottom padding
        assertEquals(68.dp, MorphingPlayerMath.containerBottomMargin(collapsedBottomPadding, 0.0f))
        assertEquals(0.dp, MorphingPlayerMath.containerBottomMargin(collapsedBottomPadding, 1.0f))
        assertEquals(34.dp, MorphingPlayerMath.containerBottomMargin(collapsedBottomPadding, 0.5f))
    }

    @Test
    fun morphingMath_containerCornerRadius_interpolatesFromCapsuleToTopWindowRadius() {
        assertEquals(30.dp, MorphingPlayerMath.containerCornerRadius(0.0f))
        assertEquals(36.dp, MorphingPlayerMath.containerCornerRadius(1.0f))
        assertEquals(33.dp, MorphingPlayerMath.containerCornerRadius(0.5f))
    }

    @Test
    fun morphingMath_artworkSizeAndRadius_interpolatesToSquircleCard() {
        // Collapsed (42dp circle)
        assertEquals(42.dp, MorphingPlayerMath.artworkSize(0.0f))
        assertEquals(21.dp, MorphingPlayerMath.artworkCornerRadius(0.0f))

        // Fullscreen (280dp squircle with 28dp radius)
        assertEquals(280.dp, MorphingPlayerMath.artworkSize(1.0f))
        assertEquals(28.dp, MorphingPlayerMath.artworkCornerRadius(1.0f))

        // Midpoint
        assertEquals(161.dp, MorphingPlayerMath.artworkSize(0.5f))
    }

    @Test
    fun morphingMath_artworkRotation_remainsStationaryZeroDegrees() {
        assertEquals(0f, MorphingPlayerMath.artworkRotation(0.0f), 0.0001f)
        assertEquals(0f, MorphingPlayerMath.artworkRotation(0.25f), 0.0001f)
        assertEquals(0f, MorphingPlayerMath.artworkRotation(0.5f), 0.0001f)
        assertEquals(0f, MorphingPlayerMath.artworkRotation(0.75f), 0.0001f)
        assertEquals(0f, MorphingPlayerMath.artworkRotation(1.0f), 0.0001f)
    }

    @Test
    fun morphingMath_dualButtons_interpolateProperly() {
        // Play / Pause button: 40dp -> 68dp squircle (26dp radius)
        assertEquals(40.dp, MorphingPlayerMath.playButtonSize(0.0f))
        assertEquals(20.dp, MorphingPlayerMath.playButtonCornerRadius(0.0f))
        assertEquals(68.dp, MorphingPlayerMath.playButtonSize(1.0f))
        assertEquals(26.dp, MorphingPlayerMath.playButtonCornerRadius(1.0f))

        // Queue button: 38dp -> 48dp
        assertEquals(38.dp, MorphingPlayerMath.queueButtonSize(0.0f))
        assertEquals(48.dp, MorphingPlayerMath.queueButtonSize(1.0f))
        assertEquals(43.dp, MorphingPlayerMath.queueButtonSize(0.5f))
    }

    @Test
    fun morphingMath_secondaryControlsAlpha_fadesInPastThreshold() {
        assertEquals(0.0f, MorphingPlayerMath.secondaryControlsAlpha(0.0f), 0.0001f)
        assertEquals(0.0f, MorphingPlayerMath.secondaryControlsAlpha(0.20f), 0.0001f)
        assertEquals(0.0f, MorphingPlayerMath.secondaryControlsAlpha(0.35f), 0.0001f)

        // Midpoint between 0.35 and 1.0 (0.675) -> ~0.5
        assertEquals(0.5f, MorphingPlayerMath.secondaryControlsAlpha(0.675f), 0.01f)

        // Fully expanded
        assertEquals(1.0f, MorphingPlayerMath.secondaryControlsAlpha(1.0f), 0.0001f)
    }

    @Test
    fun morphingMath_miniControlsAlpha_fadesOutBeforeThreshold() {
        assertEquals(1.0f, MorphingPlayerMath.miniControlsAlpha(0.0f), 0.0001f)
        assertEquals(0.5f, MorphingPlayerMath.miniControlsAlpha(0.125f), 0.0001f)
        assertEquals(0.0f, MorphingPlayerMath.miniControlsAlpha(0.25f), 0.0001f)
        assertEquals(0.0f, MorphingPlayerMath.miniControlsAlpha(0.5f), 0.0001f)
        assertEquals(0.0f, MorphingPlayerMath.miniControlsAlpha(1.0f), 0.0001f)
    }
}
