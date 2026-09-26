package com.webdav.player.ui.navigation

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainNavigationCoordinatorTest {

    private lateinit var fakeSession: FakeMusicPlayerAppSession
    private lateinit var coordinator: MainNavigationCoordinator

    private val sampleServer = WebDavServer(
        id = 1L,
        name = "My NAS",
        url = "http://nas.local",
        isDefault = true
    )

    private val sampleTrack = AudioTrack(
        id = "1:/track.flac",
        serverId = 1L,
        remotePath = "/track.flac",
        title = "Comfortably Numb",
        artist = "Pink Floyd",
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
    fun initialState_defaultsToServerListAndMiniPlayerHidden() {
        val state = coordinator.uiState.value
        assertEquals(AppDestination.SERVER_LIST, state.currentDestination)
        assertFalse(state.isMiniPlayerVisible)
        assertFalse(state.isFullPlayerExpanded)
    }

    @Test
    fun onActiveServerLoaded_withActiveServer_transitionsToBrowser() {
        coordinator.onActiveServerLoaded(sampleServer)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
    }

    @Test
    fun onActiveServerLoaded_withoutActiveServer_remainsOnServerList() {
        coordinator.onActiveServerLoaded(null)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
    }

    @Test
    fun onActiveServerLoaded_serverRemovedWhileOnBrowser_returnsToServerList() {
        coordinator.onActiveServerLoaded(sampleServer)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)

        coordinator.onActiveServerLoaded(null)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
    }

    @Test
    fun selectDestination_switchesDestinationAccurately() {
        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)

        coordinator.selectDestination(AppDestination.SERVER_LIST)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
    }

    @Test
    fun onSessionStateChanged_trackLoaded_showsMiniPlayer() {
        coordinator.onSessionStateChanged(sessionWithTrack(sampleTrack))
        assertTrue(coordinator.uiState.value.isMiniPlayerVisible)
    }

    @Test
    fun onSessionStateChanged_trackCleared_hidesMiniPlayerAndClosesFullPlayer() {
        coordinator.onSessionStateChanged(sessionWithTrack(sampleTrack))
        coordinator.expandFullPlayer()
        assertTrue(coordinator.uiState.value.isFullPlayerExpanded)

        coordinator.onSessionStateChanged(sessionWithTrack(null))
        assertFalse(coordinator.uiState.value.isMiniPlayerVisible)
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)
    }

    @Test
    fun expandAndCollapseFullPlayer() {
        coordinator.onSessionStateChanged(sessionWithTrack(sampleTrack))

        coordinator.expandFullPlayer()
        assertTrue(coordinator.uiState.value.isFullPlayerExpanded)

        coordinator.collapseFullPlayer()
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)
    }

    @Test
    fun togglePlayPause_delegatesToMusicPlayerAppSession() {
        assertEquals(0, fakeSession.togglePlayPauseCount)
        coordinator.togglePlayPause()
        assertEquals(1, fakeSession.togglePlayPauseCount)
    }

    @Test
    fun skipToNext_delegatesToMusicPlayerAppSession() {
        assertEquals(0, fakeSession.skipNextCount)
        coordinator.skipToNext()
        assertEquals(1, fakeSession.skipNextCount)
    }

    @Test
    fun playbackControls_remainFunctionalRegardlessOfSelectedDestination() {
        coordinator.onSessionStateChanged(sessionWithTrack(sampleTrack))

        // On Browser tab
        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        coordinator.togglePlayPause()
        coordinator.skipToNext()
        assertEquals(1, fakeSession.togglePlayPauseCount)
        assertEquals(1, fakeSession.skipNextCount)

        // Switch to Server List tab
        coordinator.selectDestination(AppDestination.SERVER_LIST)
        coordinator.togglePlayPause()
        coordinator.skipToNext()
        assertEquals(2, fakeSession.togglePlayPauseCount)
        assertEquals(2, fakeSession.skipNextCount)

        // Mini player remains visible on Server List
        assertTrue(coordinator.uiState.value.isMiniPlayerVisible)
    }
}
