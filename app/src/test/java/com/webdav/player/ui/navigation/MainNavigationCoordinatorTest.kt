package com.webdav.player.ui.navigation

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.WebDavServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainNavigationCoordinatorTest {
    private lateinit var coordinator: MainNavigationCoordinator

    private val sampleServer =
        WebDavServer(
            id = 1L,
            name = "My NAS",
            url = "http://nas.local",
            isDefault = true,
        )

    private val sampleTrack =
        AudioTrack(
            id = "1:/track.flac",
            serverId = 1L,
            remotePath = "/track.flac",
            title = "Comfortably Numb",
            artist = "Pink Floyd",
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
    fun initialState_respectsExplicitMiniPlayerVisibility() {
        val visibleCoordinator =
            MainNavigationCoordinator(
                initialIsMiniPlayerVisible = true,
            )
        assertTrue(visibleCoordinator.uiState.value.isMiniPlayerVisible)
    }

    @Test
    fun selectDestination_includesPlaylistsTab() {
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)
        assertEquals("歌单", AppDestination.PLAYLISTS.label)
    }

    @Test
    fun onActiveServerLoaded_nullServerWhileOnPlaylists_remainsOnPlaylists() {
        coordinator.onActiveServerLoaded(sampleServer)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)

        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)

        coordinator.onActiveServerLoaded(null)
        // Playlists are local, so it should stay on PLAYLISTS
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)
    }

    @Test
    fun onServerCardClicked_activatesServer_andSwitchesToBrowserTab() {
        var rootResetCalled = false
        coordinator.onServerCardClicked(sampleServer) {
            rootResetCalled = true
        }

        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
        assertTrue(rootResetCalled)
    }

    @Test
    fun onServerCardClicked_whenAlreadyOnBrowserTab_stillResetsToRoot() {
        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        var rootResetCalled = false
        coordinator.onServerCardClicked(sampleServer) {
            rootResetCalled = true
        }

        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
        assertTrue(rootResetCalled)
    }

    @Test
    fun playlistDetailNavigation_managesSelectedPlaylistId() {
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(null, coordinator.uiState.value.selectedPlaylistId)

        coordinator.navigateToPlaylistDetail(42L)
        assertEquals(42L, coordinator.uiState.value.selectedPlaylistId)

        coordinator.navigateBackFromPlaylistDetail()
        assertEquals(null, coordinator.uiState.value.selectedPlaylistId)
    }

    @Test
    fun playlistDetailNavigation_switchingTabsClearsSelectedPlaylist() {
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        coordinator.navigateToPlaylistDetail(42L)
        assertEquals(42L, coordinator.uiState.value.selectedPlaylistId)

        coordinator.selectDestination(AppDestination.SERVER_LIST)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
        assertEquals(null, coordinator.uiState.value.selectedPlaylistId)
    }

    @Test
    fun playlistDetailNavigation_tappingPlaylistsTabWhileInDetail_popsBackToList() {
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        coordinator.navigateToPlaylistDetail(42L)
        assertEquals(42L, coordinator.uiState.value.selectedPlaylistId)

        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)
        assertEquals(null, coordinator.uiState.value.selectedPlaylistId)
    }

    @Test
    fun explicitInitialDestination_browser_persistsAcrossNullServerEmission() {
        val restoredCoordinator =
            MainNavigationCoordinator(
                initialDestination = AppDestination.DIRECTORY_BROWSER,
                hasExplicitInitialDestination = true,
            )

        assertEquals(AppDestination.DIRECTORY_BROWSER, restoredCoordinator.uiState.value.currentDestination)

        // Transient null emission while database is loading
        restoredCoordinator.onActiveServerLoaded(null)
        assertEquals(AppDestination.DIRECTORY_BROWSER, restoredCoordinator.uiState.value.currentDestination)

        // Then server loaded
        restoredCoordinator.onActiveServerLoaded(sampleServer)
        assertEquals(AppDestination.DIRECTORY_BROWSER, restoredCoordinator.uiState.value.currentDestination)
    }

    @Test
    fun explicitInitialDestination_playlists_persistsAcrossActiveServerLoaded() {
        val restoredCoordinator =
            MainNavigationCoordinator(
                initialDestination = AppDestination.PLAYLISTS,
                hasExplicitInitialDestination = true,
            )

        assertEquals(AppDestination.PLAYLISTS, restoredCoordinator.uiState.value.currentDestination)

        restoredCoordinator.onActiveServerLoaded(sampleServer)
        assertEquals(AppDestination.PLAYLISTS, restoredCoordinator.uiState.value.currentDestination)
    }

    @Test
    fun onServerCardClicked_notifiesDestinationChanged() {
        var notifiedDestination: AppDestination? = null
        val testCoordinator =
            MainNavigationCoordinator(
                initialDestination = AppDestination.SERVER_LIST,
                hasExplicitInitialDestination = true,
                onDestinationChanged = { notifiedDestination = it },
            )

        testCoordinator.onServerCardClicked(sampleServer)
        assertEquals(AppDestination.DIRECTORY_BROWSER, testCoordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.DIRECTORY_BROWSER, notifiedDestination)
    }

    @Test
    fun destinationOrdering_conformsToM3SharedAxisLayout() {
        assertEquals("PLAYLISTS must be index 0", 0, AppDestination.PLAYLISTS.ordinal)
        assertEquals("DIRECTORY_BROWSER must be index 1", 1, AppDestination.DIRECTORY_BROWSER.ordinal)
        assertEquals("SERVER_LIST must be index 2", 2, AppDestination.SERVER_LIST.ordinal)
    }

    @Test
    fun initialState_hasNullPreviousDestination_andDefaultsToIsForwardTrue() {
        val state = coordinator.uiState.value
        assertEquals(AppDestination.SERVER_LIST, state.currentDestination)
        assertNull(state.previousDestination)
        assertTrue(state.isForward)
    }

    @Test
    fun selectDestination_forwardNavigation_calculatesIsForwardTrue() {
        val coordinatorFromPlaylists = MainNavigationCoordinator(initialDestination = AppDestination.PLAYLISTS)
        assertNull(coordinatorFromPlaylists.uiState.value.previousDestination)

        coordinatorFromPlaylists.selectDestination(AppDestination.DIRECTORY_BROWSER)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinatorFromPlaylists.uiState.value.currentDestination)
        assertEquals(AppDestination.PLAYLISTS, coordinatorFromPlaylists.uiState.value.previousDestination)
        assertTrue(coordinatorFromPlaylists.uiState.value.isForward)

        coordinatorFromPlaylists.selectDestination(AppDestination.SERVER_LIST)
        assertEquals(AppDestination.SERVER_LIST, coordinatorFromPlaylists.uiState.value.currentDestination)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinatorFromPlaylists.uiState.value.previousDestination)
        assertTrue(coordinatorFromPlaylists.uiState.value.isForward)

        coordinatorFromPlaylists.selectDestination(AppDestination.PLAYLISTS)
        coordinatorFromPlaylists.selectDestination(AppDestination.SERVER_LIST)
        assertEquals(AppDestination.SERVER_LIST, coordinatorFromPlaylists.uiState.value.currentDestination)
        assertEquals(AppDestination.PLAYLISTS, coordinatorFromPlaylists.uiState.value.previousDestination)
        assertTrue(coordinatorFromPlaylists.uiState.value.isForward)
    }

    @Test
    fun selectDestination_backwardNavigation_calculatesIsForwardFalse() {
        coordinator.selectDestination(AppDestination.SERVER_LIST)

        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)

        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)

        coordinator.selectDestination(AppDestination.SERVER_LIST)
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)
    }

    @Test
    fun selectDestination_sameDestination_retainsPreviousDestinationAndDirection() {
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.previousDestination)
        assertTrue(coordinator.uiState.value.isForward)

        // Select the same destination again
        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.previousDestination)
        assertTrue(coordinator.uiState.value.isForward)
    }

    @Test
    fun selectDestination_rapidSwitching_preservesStateAndDirectionConsistency() {
        // Initial state: SERVER_LIST (2), prev = null
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)

        // Step 1: rapid click to PLAYLISTS (0) -> backward
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)

        // Step 2: rapid click to SERVER_LIST (2) -> forward
        coordinator.selectDestination(AppDestination.SERVER_LIST)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.previousDestination)
        assertTrue(coordinator.uiState.value.isForward)

        // Step 3: rapid click to DIRECTORY_BROWSER (1) -> backward
        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)

        // Step 4: rapid click to PLAYLISTS (0) -> backward
        coordinator.selectDestination(AppDestination.PLAYLISTS)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)

        // Step 5: rapid click to SERVER_LIST (2) -> forward
        coordinator.selectDestination(AppDestination.SERVER_LIST)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.PLAYLISTS, coordinator.uiState.value.previousDestination)
        assertTrue(coordinator.uiState.value.isForward)

        // Step 6: rapid click to DIRECTORY_BROWSER (1) -> backward
        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)
    }

    @Test
    fun onActiveServerLoaded_updatesPreviousDestinationAccurately() {
        // Initially on SERVER_LIST (2)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
        assertNull(coordinator.uiState.value.previousDestination)

        // Active server loaded: transitions to DIRECTORY_BROWSER (1)
        coordinator.onActiveServerLoaded(sampleServer)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.previousDestination)
        assertFalse(coordinator.uiState.value.isForward)

        // Server removed while on DIRECTORY_BROWSER: returns to SERVER_LIST (2)
        coordinator.onActiveServerLoaded(null)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.previousDestination)
        assertTrue(coordinator.uiState.value.isForward)
    }
}
