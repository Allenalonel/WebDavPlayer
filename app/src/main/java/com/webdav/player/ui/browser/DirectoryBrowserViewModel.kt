package com.webdav.player.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private var metadataObserverJob: Job? = null
    private var metadataResolutionJob: Job? = null
    private var currentLoadJob: Job? = null

    init {
        viewModelScope.launch {
            if (musicPlayerAppSession != null) {
                musicPlayerAppSession.isRestored.first { it }
            }

            serverRepository.getActiveServer().collect { server ->
                val previousServer = _uiState.value.activeServer
                if (server != previousServer) {
                    metadataObserverJob?.cancel()
                    metadataResolutionJob?.cancel()
                    currentLoadJob?.cancel()

                    if (previousServer != null) {
                        musicPlayerAppSession?.setActiveServer(server)
                    }

                    // If previousServer == null, this is app startup -> restore saved directory from session.
                    // If previousServer != null, user switched servers -> restore target server's last visited directory.
                    val initialPath = if (previousServer == null) {
                        val savedForServer = server?.let { musicPlayerAppSession?.getLastDirectoryForServer(it.id) }
                        val sessionPath = musicPlayerAppSession?.sessionState?.value?.currentDirectoryPath
                        if (savedForServer != null && savedForServer != "/") {
                            savedForServer
                        } else if (!sessionPath.isNullOrBlank() && sessionPath != "/") {
                            sessionPath
                        } else {
                            savedForServer ?: sessionPath ?: "/"
                        }
                    } else {
                        if (server != null) {
                            musicPlayerAppSession?.getLastDirectoryForServer(server.id) ?: "/"
                        } else {
                            "/"
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isInitializing = false,
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
                        loadDirectory(initialPath, forceRefresh = false, fallbackToRoot = true)
                    }
                }
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

    private fun loadDirectory(
        path: String,
        forceRefresh: Boolean,
        fallbackToRoot: Boolean = false
    ) {
        val server = _uiState.value.activeServer ?: return
        val normalizedPath = BreadcrumbNavigationHelper.normalizeDirectoryPath(path)

        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
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

            var hasEmittedContent = false

            directoryRepository.observeDirectory(server, normalizedPath, forceRefresh).collect { result ->
                when (result) {
                    is ListDirectoryResult.Success -> {
                        hasEmittedContent = true
                        musicPlayerAppSession?.setCurrentDirectoryPath(normalizedPath)
                        val audioFiles = result.directory.files.filter { it.isAudio }
                        if (audioFiles.isNotEmpty() && trackMetadataRepository != null) {
                            metadataResolutionJob?.cancel()
                            metadataResolutionJob = viewModelScope.launch {
                                trackMetadataRepository.resolveMetadata(server, audioFiles)
                            }
                        }

                        _uiState.update { current ->
                            current.copy(
                                currentDirectory = result.directory,
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = null
                            )
                        }
                    }
                    is ListDirectoryResult.Failure -> {
                        _uiState.update { current ->
                            if (hasEmittedContent) {
                                current.copy(
                                    isLoading = false,
                                    isRefreshing = false
                                )
                            } else if (fallbackToRoot && normalizedPath != "/") {
                                loadDirectory("/", forceRefresh = false)
                                current
                            } else {
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

            _uiState.update { current ->
                if (current.isLoading || current.isRefreshing) {
                    current.copy(isLoading = false, isRefreshing = false)
                } else {
                    current
                }
            }
        }
    }

    private fun buildBreadcrumbs(server: WebDavServer?, path: String): List<Breadcrumb> {
        return BreadcrumbNavigationHelper.buildBreadcrumbs(server, path)
    }
}
