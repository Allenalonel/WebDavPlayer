package com.webdav.player.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DirectoryBrowserViewModel(
    private val serverRepository: ServerRepository,
    private val directoryRepository: DirectoryRepository,
    val musicPlayerAppSession: MusicPlayerAppSession? = null,
    val trackMetadataRepository: TrackMetadataRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryBrowserUiState())
    val uiState: StateFlow<DirectoryBrowserUiState> = _uiState.asStateFlow()

    val playerSessionState: StateFlow<PlayerSessionState>? = musicPlayerAppSession?.sessionState

    private var metadataObserverJob: Job? = null
    private var metadataResolutionJob: Job? = null

    init {
        viewModelScope.launch {
            serverRepository.getActiveServer().collect { server ->
                val previousServer = _uiState.value.activeServer
                if (server != previousServer) {
                    metadataObserverJob?.cancel()
                    metadataResolutionJob?.cancel()

                    // If previousServer == null, this is app startup -> restore saved directory from session.
                    // If previousServer != null, user switched servers -> reset directory to root "/".
                    val initialPath = if (previousServer == null) {
                        musicPlayerAppSession?.sessionState?.value?.currentDirectoryPath
                            ?.takeIf { it.isNotBlank() } ?: "/"
                    } else {
                        musicPlayerAppSession?.setCurrentDirectoryPath("/")
                        "/"
                    }

                    _uiState.update {
                        it.copy(
                            activeServer = server,
                            currentPath = initialPath,
                            breadcrumbs = buildBreadcrumbs(server, initialPath),
                            canNavigateUp = initialPath != "/",
                            currentDirectory = null,
                            errorMessage = null,
                            metadataMap = emptyMap()
                        )
                    }

                    if (server != null) {
                        if (trackMetadataRepository != null) {
                            metadataObserverJob = viewModelScope.launch {
                                trackMetadataRepository.getAllMetadataFlow(server.id).collect { list ->
                                    val map = list.associateBy { it.remotePath }
                                    _uiState.update { it.copy(metadataMap = map) }
                                }
                            }
                        }
                        loadInitialDirectory(server, initialPath)
                    }
                }
            }
        }
    }

    private fun loadInitialDirectory(server: WebDavServer, initialPath: String) {
        if (initialPath == "/") {
            loadDirectory("/", forceRefresh = false)
            return
        }

        val normalized = normalizeDirectoryPath(initialPath)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentPath = normalized,
                    breadcrumbs = buildBreadcrumbs(server, normalized),
                    canNavigateUp = normalized != "/",
                    isLoading = true,
                    isRefreshing = false,
                    errorMessage = null
                )
            }

            val result = directoryRepository.listDirectory(server, normalized, false)
            if (result is ListDirectoryResult.Success) {
                musicPlayerAppSession?.setCurrentDirectoryPath(normalized)
                val audioFiles = result.directory.files.filter { it.isAudio }
                if (audioFiles.isNotEmpty() && trackMetadataRepository != null) {
                    metadataResolutionJob?.cancel()
                    metadataResolutionJob = viewModelScope.launch {
                        trackMetadataRepository.resolveMetadata(server, audioFiles)
                    }
                }

                _uiState.update {
                    it.copy(
                        currentDirectory = result.directory,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null
                    )
                }
            } else {
                // Graceful fallback to root directory if initial restored directory fails
                loadDirectory("/", forceRefresh = false)
            }
        }
    }

    fun onDirectoryClicked(directory: RemoteDirectory) {
        loadDirectory(directory.path, forceRefresh = false)
    }

    fun onBreadcrumbClicked(breadcrumb: Breadcrumb) {
        if (breadcrumb.path == _uiState.value.currentPath) return
        loadDirectory(breadcrumb.path, forceRefresh = false)
    }

    fun resetToRoot() {
        musicPlayerAppSession?.setCurrentDirectoryPath("/")
        loadDirectory("/", forceRefresh = false)
    }

    fun onAudioTrackClicked(file: RemoteFile) {
        if (!file.isAudio) return
        val dir = _uiState.value.currentDirectory ?: return
        musicPlayerAppSession?.playDirectoryTrack(dir, file)
    }

    fun playNext(file: RemoteFile) {
        if (!file.isAudio) return
        val server = _uiState.value.activeServer ?: return
        val metadata = _uiState.value.metadataMap[file.path]
        val track = AudioTrack.fromRemoteFile(server, file, metadata) ?: return
        musicPlayerAppSession?.playNext(track)
    }

    fun togglePlayPause() {
        musicPlayerAppSession?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        musicPlayerAppSession?.seekTo(positionMs)
    }

    fun skipToNext() {
        musicPlayerAppSession?.skipToNext()
    }

    fun skipToPrevious() {
        musicPlayerAppSession?.skipToPrevious()
    }

    fun cyclePlaybackMode() {
        musicPlayerAppSession?.cyclePlaybackMode()
    }

    fun playQueueIndex(index: Int) {
        musicPlayerAppSession?.playQueueIndex(index)
    }

    fun removeQueueTrack(index: Int) {
        musicPlayerAppSession?.removeQueueTrack(index)
    }

    fun onNavigateUp(): Boolean {
        val current = _uiState.value.currentPath
        val parentPath = BreadcrumbNavigationHelper.getParentPath(current) ?: return false
        loadDirectory(parentPath, forceRefresh = false)
        return true
    }

    fun onRefresh() {
        loadDirectory(_uiState.value.currentPath, forceRefresh = true)
    }

    fun onRetry() {
        loadDirectory(_uiState.value.currentPath, forceRefresh = true)
    }

    private fun loadDirectory(path: String, forceRefresh: Boolean) {
        val server = _uiState.value.activeServer ?: return
        val normalizedPath = BreadcrumbNavigationHelper.normalizeDirectoryPath(path)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentPath = normalizedPath,
                    breadcrumbs = BreadcrumbNavigationHelper.buildBreadcrumbs(server, normalizedPath),
                    canNavigateUp = normalizedPath != "/",
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    errorMessage = null
                )
            }

            val result = directoryRepository.listDirectory(server, normalizedPath, forceRefresh)

            _uiState.update { current ->
                when (result) {
                    is ListDirectoryResult.Success -> {
                        musicPlayerAppSession?.setCurrentDirectoryPath(normalizedPath)
                        val audioFiles = result.directory.files.filter { it.isAudio }
                        if (audioFiles.isNotEmpty() && trackMetadataRepository != null) {
                            metadataResolutionJob?.cancel()
                            metadataResolutionJob = viewModelScope.launch {
                                trackMetadataRepository.resolveMetadata(server, audioFiles)
                            }
                        }

                        current.copy(
                            currentDirectory = result.directory,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                    is ListDirectoryResult.Failure -> {
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun buildBreadcrumbs(server: WebDavServer?, path: String): List<Breadcrumb> {
        return BreadcrumbNavigationHelper.buildBreadcrumbs(server, path)
    }

    private fun normalizeDirectoryPath(path: String): String {
        return BreadcrumbNavigationHelper.normalizeDirectoryPath(path)
    }
}
