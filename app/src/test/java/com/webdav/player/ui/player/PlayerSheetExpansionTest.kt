package com.webdav.player.ui.player

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import com.webdav.player.ui.navigation.MainNavigationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerSheetExpansionTest {

    private lateinit var fakeSession: FakeMusicPlayerAppSession
    private lateinit var coordinator: MainNavigationCoordinator

    private val sampleTrack = AudioTrack(
        id = "1:/music/track1.flac",
        serverId = 1L,
        remotePath = "/music/track1.flac",
        title = "Shine On You Crazy Diamond",
        artist = "Pink Floyd",
        album = "Wish You Were Here",
        format = AudioFormat.FLAC
    )

    private fun sessionWithTrack(track: AudioTrack?): PlayerSessionState {
        return if (track == null) {
            PlayerSessionState()
        } else {
            PlayerSessionState(queue = PlaybackQueue(listOf(track), 0))
        }
    }

    @Before
    fun setUp() {
        fakeSession = FakeMusicPlayerAppSession()
        coordinator = MainNavigationCoordinator(fakeSession)
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
}
