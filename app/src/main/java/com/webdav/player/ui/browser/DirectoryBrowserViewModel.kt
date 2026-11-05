package com.webdav.player.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.LibrarySearchResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.LibrarySearchRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DirectoryBrowserViewModel(
    private val serverRepository: ServerRepository? = null,
    private val directoryRepository: DirectoryRepository,
    val musicPlayerAppSession: MusicPlayerAppSession? = null,
    val librarySearchRepository: LibrarySearchRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DirectoryBrowserUiState())
    val uiState: StateFlow<DirectoryBrowserUiState> = _uiState.asStateFlow()

    private var currentLoadJob: Job? = null
    private var searchJob: Job? = null

    init {
        val initialHistory = librarySearchRepository?.getSearchHistory() ?: emptyList()
        if (initialHistory.isNotEmpty()) {
            _uiState.update { it.copy(searchHistory = initialHistory) }
        }

        if (musicPlayerAppSession != null) {
            viewModelScope.launch {
                musicPlayerAppSession.isRestored.first { it }
                musicPlayerAppSession.sessionState.collect { state ->
                    val (path, id, playing) = resolveActivePlayback(_uiState.value.activeServer, state.currentTrack, state.isPlaying)
                    _uiState.update { it.copy(activeTrackPath = path, activeTrackId = id, isPlaying = playing) }
                }
            }
        }

        viewModelScope.launch {
            if (musicPlayerAppSession != null) {
                musicPlayerAppSession.isRestored.first { it }
            }

            val activeServerFlow: Flow<WebDavServer?> =
                musicPlayerAppSession?.sessionState?.map { it.activeServer }?.distinctUntilChanged()
                    ?: serverRepository?.getActiveServer()?.distinctUntilChanged()
                    ?: emptyFlow()

            activeServerFlow.collect { server ->
                val previousServer = _uiState.value.activeServer
                if (server != previousServer) {
                    currentLoadJob?.cancel()

                    // If previousServer == null, this is app startup -> restore saved directory from session.
                    // If previousServer != null, user switched servers -> restore target server's last visited directory.
                    val initialPath =
                        if (previousServer == null) {
                            val sessionPath = musicPlayerAppSession?.sessionState?.value?.currentDirectoryPath
                            val saved = server?.let { musicPlayerAppSession?.getLastDirectoryForServer(it.id) }
                            if (!sessionPath.isNullOrBlank() && sessionPath != "/") sessionPath else saved ?: "/"
                        } else {
                            server?.let { musicPlayerAppSession?.getLastDirectoryForServer(it.id) } ?: "/"
                        }

                    val session = musicPlayerAppSession?.sessionState?.value
                    val (activeTrackPath, activeTrackId, isPlaying) =
                        resolveActivePlayback(server, session?.currentTrack, session?.isPlaying == true)

                    _uiState.update {
                        it.copy(
                            isInitializing = false,
                            activeServer = server,
                            currentPath = initialPath,
                            breadcrumbs = buildBreadcrumbs(server, initialPath),
                            canNavigateUp = initialPath != "/",
                            currentDirectory = null,
                            errorMessage = null,
                            metadataMap = emptyMap(),
                            activeTrackPath = activeTrackPath,
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            cueAlbums = emptyList(),
                        )
                    }

                    if (server != null) {
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
        loadDirectory("/", forceRefresh = false)
    }

    fun onAudioTrackClicked(file: RemoteFile) {
        if (!file.isAudio) return
        val dir = _uiState.value.currentDirectory ?: return
        val virtualTracksMap =
            _uiState.value.cueAlbums
                .filter { it.audioFile != null && it.tracks.isNotEmpty() }
                .associate { it.audioFile!!.path to it.tracks }
        musicPlayerAppSession?.playDirectoryTrack(
            directory = dir,
            selectedFile = file,
            initialMetadata = _uiState.value.metadataMap,
            virtualTracksByAudioPath = virtualTracksMap,
        )
    }

    fun playNext(file: RemoteFile) {
        if (!file.isAudio) return
        val server = _uiState.value.activeServer ?: return
        val metadata = _uiState.value.metadataMap[file.path]
        val track = AudioTrack.fromRemoteFile(server, file, metadata) ?: return
        musicPlayerAppSession?.playNext(track)
    }

    fun playNext(track: AudioTrack) {
        musicPlayerAppSession?.playNext(track)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(searchResult = LibrarySearchResult(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        searchJob =
            viewModelScope.launch {
                librarySearchRepository?.search(trimmed, serverId = null)?.collect { result ->
                    _uiState.update { it.copy(searchResult = result, isSearching = false) }
                } ?: run {
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
    }

    fun onSearchActiveChanged(active: Boolean) {
        if (!active) {
            searchJob?.cancel()
            _uiState.update {
                it.copy(isSearchActive = false, searchQuery = "", searchResult = LibrarySearchResult(), isSearching = false)
            }
        } else {
            _uiState.update { it.copy(isSearchActive = true) }
        }
    }

    fun onClearSearchQuery() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(searchQuery = "", searchResult = LibrarySearchResult(), isSearching = false)
        }
    }

    fun onSearchSubmitted(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            val updated =
                librarySearchRepository?.recordSearchQuery(trimmed)
                    ?: (listOf(trimmed) + _uiState.value.searchHistory.filter { it != trimmed }).take(20)
            _uiState.update { it.copy(searchHistory = updated) }
        }
    }

    fun onClearSearchHistory() {
        librarySearchRepository?.clearSearchHistory()
        _uiState.update { it.copy(searchHistory = emptyList()) }
    }

    fun onRemoveSearchHistoryItem(item: String) {
        val updated =
            librarySearchRepository?.removeSearchHistory(item)
                ?: _uiState.value.searchHistory.filter { it != item }
        _uiState.update { it.copy(searchHistory = updated) }
    }

    fun onSearchResultDirectoryClicked(directory: RemoteDirectory) {
        val query = _uiState.value.searchQuery.trim()
        if (query.isNotBlank()) onSearchSubmitted(query)
        _uiState.update { it.copy(isSearchActive = false, searchQuery = "") }
        val targetServerId = directory.serverId
        val currentServer = _uiState.value.activeServer
        if (targetServerId != null && targetServerId != currentServer?.id) {
            viewModelScope.launch {
                serverRepository?.getServerById(targetServerId)?.let { targetServer ->
                    serverRepository.setActiveServer(targetServerId)
                    musicPlayerAppSession?.setActiveServer(targetServer)
                    _uiState.update { it.copy(activeServer = targetServer) }
                }
                loadDirectory(directory.path, forceRefresh = false)
            }
        } else {
            loadDirectory(directory.path, forceRefresh = false)
        }
    }

    fun closeSearchBar() {
        _uiState.update { it.copy(isSearchActive = false, searchQuery = "", isSearching = false) }
    }

    fun onSearchResultTrackClicked(track: AudioTrack) {
        val query = _uiState.value.searchQuery.trim()
        if (query.isNotBlank()) onSearchSubmitted(query)
        closeSearchBar()
        musicPlayerAppSession?.playNextAndAdvance(track)
    }

    fun onNavigateUp(): Boolean {
        val current = _uiState.value.currentPath
        val parentPath = RemoteDirectory.getParentPath(current) ?: return false
        loadDirectory(parentPath, forceRefresh = false)
        return true
    }

    fun onRefresh() {
        loadDirectory(_uiState.value.currentPath, forceRefresh = true)
    }

    fun onRetry() {
        loadDirectory(_uiState.value.currentPath, forceRefresh = true)
    }

    fun loadDirectory(
        path: String,
        forceRefresh: Boolean = false,
        fallbackToRoot: Boolean = false,
    ) {
        val server = _uiState.value.activeServer ?: return
        val normalizedPath = RemoteDirectory.normalizePath(path)

        val previousJob = currentLoadJob

        val isNavigatingToNewDirectory = _uiState.value.currentDirectory?.path != normalizedPath

        _uiState.update { current ->
            current.copy(
                currentPath = normalizedPath,
                currentDirectory = if (isNavigatingToNewDirectory) null else current.currentDirectory,
                cueAlbums = if (isNavigatingToNewDirectory) emptyList() else current.cueAlbums,
                metadataMap = if (isNavigatingToNewDirectory) emptyMap() else current.metadataMap,
                breadcrumbs = RemoteDirectory.buildBreadcrumbs(server, normalizedPath),
                canNavigateUp = normalizedPath != "/",
                isLoading = !forceRefresh,
                isRefreshing = forceRefresh,
                errorMessage = null,
            )
        }

        currentLoadJob =
            viewModelScope.launch {
                if (forceRefresh && previousJob != null && previousJob.isActive) {
                    previousJob.join()
                } else if (!forceRefresh) {
                    previousJob?.cancel()
                }

                var hasEmittedContent = false

                directoryRepository
                    .observeDirectoryContent(server, normalizedPath, forceRefresh)
                    .catch { e ->
                        _uiState.update { current ->
                            val hasExistingDirectoryForPath =
                                current.currentDirectory != null && current.currentDirectory.path == normalizedPath
                            if (hasEmittedContent || hasExistingDirectoryForPath) {
                                current.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                )
                            } else if (fallbackToRoot && normalizedPath != "/") {
                                loadDirectory("/", forceRefresh = false)
                                current
                            } else {
                                current.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = e.message ?: "Failed to load directory",
                                )
                            }
                        }
                    }.collect { content ->
                        hasEmittedContent = true
                        musicPlayerAppSession?.setCurrentDirectoryPath(normalizedPath)
                        _uiState.update { current ->
                            if (current.currentPath == normalizedPath) {
                                val prevMap = current.cueAlbums.associateBy { it.cueFile.path }
                                val mergedCueAlbums =
                                    content.cueAlbums.map { alb ->
                                        val prev = prevMap[alb.cueFile.path]
                                        if (prev != null) alb.copy(isExpanded = prev.isExpanded) else alb
                                    }
                                current.copy(
                                    currentDirectory = content.directory,
                                    cueAlbums = mergedCueAlbums,
                                    metadataMap = content.metadataMap,
                                    isLoading = false,
                                    isRefreshing = content.isEnriching,
                                    errorMessage = null,
                                )
                            } else {
                                current
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

    fun toggleCueAlbumExpanded(cuePath: String) {
        _uiState.update { current ->
            current.copy(
                cueAlbums =
                    current.cueAlbums.map {
                        if (it.cueFile.path == cuePath) it.copy(isExpanded = !it.isExpanded) else it
                    },
            )
        }
    }

    fun playVirtualTrack(
        album: CueAlbumItem,
        trackIndex: Int,
    ) {
        val server = _uiState.value.activeServer ?: return
        val audioFile = album.audioFile ?: return
        if (album.tracks.isEmpty()) return
        val metadata = _uiState.value.metadataMap[audioFile.path]
        val parentTrack = AudioTrack.fromRemoteFile(server, audioFile, metadata) ?: return
        musicPlayerAppSession?.playVirtualTracks(
            parentTrack = parentTrack,
            virtualTracks = album.tracks,
            startIndex = trackIndex,
            cuePath = album.cueFile.path,
        )
    }

    fun playCueAlbum(album: CueAlbumItem) {
        val audioFile = album.audioFile ?: return
        if (album.tracks.isEmpty()) onAudioTrackClicked(audioFile) else playVirtualTrack(album, 0)
    }

    private fun resolveActivePlayback(
        server: WebDavServer?,
        track: AudioTrack?,
        isPlaying: Boolean,
    ): Triple<String?, String?, Boolean> {
        val matches = track != null && server != null && track.serverId == server.id
        return if (matches) Triple(track?.remotePath, track?.id, isPlaying) else Triple(null, null, false)
    }

    private fun buildBreadcrumbs(
        server: WebDavServer?,
        path: String,
    ): List<Breadcrumb> = RemoteDirectory.buildBreadcrumbs(server, path)
}
