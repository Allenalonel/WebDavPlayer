package com.webdav.player.ui.navigation

import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppDestination(val label: String) {
    DIRECTORY_BROWSER("媒体库"),
    SERVER_LIST("服务器")
}

data class MainNavigationUiState(
    val currentDestination: AppDestination = AppDestination.SERVER_LIST,
    val isFullPlayerExpanded: Boolean = false,
    val isMiniPlayerVisible: Boolean = false
)

class MainNavigationCoordinator(
    private val musicPlayerAppSession: MusicPlayerAppSession? = null,
    initialDestination: AppDestination = AppDestination.SERVER_LIST
) {
    private val _uiState = MutableStateFlow(
        MainNavigationUiState(
            currentDestination = initialDestination,
            isMiniPlayerVisible = musicPlayerAppSession?.sessionState?.value?.hasTrack == true
        )
    )
    val uiState: StateFlow<MainNavigationUiState> = _uiState.asStateFlow()

    private var hasCheckedInitialActiveServer = false

    fun onActiveServerLoaded(activeServer: WebDavServer?) {
        if (!hasCheckedInitialActiveServer) {
            hasCheckedInitialActiveServer = true
            if (activeServer != null) {
                _uiState.update { it.copy(currentDestination = AppDestination.DIRECTORY_BROWSER) }
            }
        } else if (activeServer == null && _uiState.value.currentDestination == AppDestination.DIRECTORY_BROWSER) {
            _uiState.update { it.copy(currentDestination = AppDestination.SERVER_LIST) }
        }
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
    }

    fun onServerCardClicked(@Suppress("UNUSED_PARAMETER") server: WebDavServer, onResetToRoot: (() -> Unit)? = null) {
        onResetToRoot?.invoke()
        _uiState.update { it.copy(currentDestination = AppDestination.DIRECTORY_BROWSER) }
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

    fun togglePlayPause() {
        musicPlayerAppSession?.togglePlayPause()
    }

    fun skipToNext() {
        musicPlayerAppSession?.skipToNext()
    }
}
