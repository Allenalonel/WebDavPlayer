package com.webdav.player.ui.navigation

import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppDestination(val label: String) {
    DIRECTORY_BROWSER("媒体库"),
    PLAYLISTS("歌单"),
    SERVER_LIST("服务器")
}

data class MainNavigationUiState(
    val currentDestination: AppDestination = AppDestination.SERVER_LIST,
    val isFullPlayerExpanded: Boolean = false,
    val isMiniPlayerVisible: Boolean = false,
    val selectedPlaylistId: Long? = null,
)

class MainNavigationCoordinator(
    initialDestination: AppDestination = AppDestination.SERVER_LIST,
    private val hasExplicitInitialDestination: Boolean = false,
    initialIsMiniPlayerVisible: Boolean = false,
    private val onDestinationChanged: ((AppDestination) -> Unit)? = null,
) {
    private val _uiState = MutableStateFlow(
        MainNavigationUiState(
            currentDestination = initialDestination,
            isMiniPlayerVisible = initialIsMiniPlayerVisible,
        )
    )
    val uiState: StateFlow<MainNavigationUiState> = _uiState.asStateFlow()

    private var hasCheckedInitialActiveServer = hasExplicitInitialDestination

    fun onActiveServerLoaded(activeServer: WebDavServer?) {
        if (!hasCheckedInitialActiveServer) {
            hasCheckedInitialActiveServer = true
            if (activeServer != null) {
                _uiState.update { it.copy(currentDestination = AppDestination.DIRECTORY_BROWSER) }
                onDestinationChanged?.invoke(AppDestination.DIRECTORY_BROWSER)
            }
        } else if (!hasExplicitInitialDestination && activeServer == null && _uiState.value.currentDestination == AppDestination.DIRECTORY_BROWSER) {
            _uiState.update { it.copy(currentDestination = AppDestination.SERVER_LIST) }
            onDestinationChanged?.invoke(AppDestination.SERVER_LIST)
        }
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update {
            if (destination == AppDestination.PLAYLISTS && it.currentDestination == AppDestination.PLAYLISTS && it.selectedPlaylistId != null) {
                it.copy(selectedPlaylistId = null)
            } else if (destination != AppDestination.PLAYLISTS) {
                it.copy(currentDestination = destination, selectedPlaylistId = null)
            } else {
                it.copy(currentDestination = destination)
            }
        }
        onDestinationChanged?.invoke(destination)
    }

    fun navigateToPlaylistDetail(playlistId: Long) {
        _uiState.update { it.copy(selectedPlaylistId = playlistId) }
    }

    fun navigateBackFromPlaylistDetail() {
        _uiState.update { it.copy(selectedPlaylistId = null) }
    }

    fun onServerCardClicked(@Suppress("UNUSED_PARAMETER") server: WebDavServer, onResetToRoot: (() -> Unit)? = null) {
        onResetToRoot?.invoke()
        selectDestination(AppDestination.DIRECTORY_BROWSER)
    }

    fun onSessionStateChanged(sessionState: PlayerSessionState) {
        _uiState.update {
            it.copy(
                isMiniPlayerVisible = sessionState.hasTrack,
                isFullPlayerExpanded = if (!sessionState.hasTrack) false else it.isFullPlayerExpanded
            )
        }
    }

    fun expandFullPlayer() {
        if (_uiState.value.isMiniPlayerVisible) {
            _uiState.update { it.copy(isFullPlayerExpanded = true) }
        }
    }

    fun collapseFullPlayer() {
        _uiState.update { it.copy(isFullPlayerExpanded = false) }
    }
}
