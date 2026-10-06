package com.webdav.player.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.data.cue.CueAssociationHelper
import com.webdav.player.data.cue.CueParser
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class DirectoryBrowserViewModel(
    private val serverRepository: ServerRepository? = null,
    private val directoryRepository: DirectoryRepository,
    val musicPlayerAppSession: MusicPlayerAppSession? = null,
    val trackMetadataRepository: TrackMetadataRepository? = null,
    val webDavClient: WebDavClient? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DirectoryBrowserUiState())
    val uiState: StateFlow<DirectoryBrowserUiState> = _uiState.asStateFlow()

    private var metadataObserverJob: Job? = null
    private var metadataResolutionJob: Job? = null
    private var currentLoadJob: Job? = null
    private var activeRefreshJob: Job? = null
    private var cueResolutionJob: Job? = null
    private var activeMetadataPath: String? = null

    init {
        if (musicPlayerAppSession != null) {
            viewModelScope.launch {
                musicPlayerAppSession.isRestored.first { it }
                musicPlayerAppSession.sessionState.collect { sessionState ->
                    val currentTrack = sessionState.currentTrack
                    val isPlaying = sessionState.isPlaying
                    _uiState.update { current ->
                        val (activeTrackPath, activeTrackId, trackIsPlaying) =
                            resolveActivePlayback(current.activeServer, currentTrack, isPlaying)
                        current.copy(
                            activeTrackPath = activeTrackPath,
                            activeTrackId = activeTrackId,
                            isPlaying = trackIsPlaying,
                        )
                    }
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
                    metadataObserverJob?.cancel()
                    metadataResolutionJob?.cancel()
                    currentLoadJob?.cancel()
                    activeRefreshJob?.cancel()
                    cueResolutionJob?.cancel()
                    activeMetadataPath = null

                    // If previousServer == null, this is app startup -> restore saved directory from session.
                    // If previousServer != null, user switched servers -> restore target server's last visited directory.
                    val initialPath =
                        if (previousServer == null) {
                            val sessionPath = musicPlayerAppSession?.sessionState?.value?.currentDirectoryPath
                            val savedForServer = server?.let { musicPlayerAppSession?.getLastDirectoryForServer(it.id) }
                            if (!sessionPath.isNullOrBlank() && sessionPath != "/") {
                                sessionPath
                            } else if (savedForServer != null && savedForServer != "/") {
                                savedForServer
                            } else {
                                sessionPath ?: savedForServer ?: "/"
                            }
                        } else {
                            if (server != null) {
                                musicPlayerAppSession?.getLastDirectoryForServer(server.id) ?: "/"
                            } else {
                                "/"
                            }
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
                        if (trackMetadataRepository != null) {
                            metadataObserverJob =
                                viewModelScope.launch {
                                    trackMetadataRepository.getAllMetadataFlow(server.id).collect { list ->
                                        val map = list.associateBy { it.remotePath }
                                        _uiState.update { current ->
                                            val updatedAlbums =
                                                current.cueAlbums.map { album ->
                                                    val dur = album.audioFile?.path?.let { map[it]?.durationMs }
                                                    if (dur != null && dur > 0L && album.tracks.isNotEmpty()) {
                                                        val lastTrack = album.tracks.last()
                                                        if (lastTrack.endTimeMs == null && dur > lastTrack.startTimeMs) {
                                                            val updatedTracks = album.tracks.toMutableList()
                                                            updatedTracks[updatedTracks.lastIndex] = lastTrack.copy(endTimeMs = dur)
                                                            album.copy(tracks = updatedTracks)
                                                        } else {
                                                            album
                                                        }
                                                    } else {
                                                        album
                                                    }
                                                }
                                            current.copy(metadataMap = map, cueAlbums = updatedAlbums)
                                        }
                                    }
                                }
                        }
                        loadDirectory(initialPath, forceRefresh = false, fallbackToRoot = true)
                    } else {
                        _uiState.update {
                            it.copy(
                                isInitializing = false,
                                activeServer = null,
                                currentPath = "/",
                                breadcrumbs = emptyList(),
                                canNavigateUp = false,
                                currentDirectory = null,
                                errorMessage = null,
                                metadataMap = emptyMap(),
                                activeTrackPath = null,
                                activeTrackId = null,
                                isPlaying = false,
                                cueAlbums = emptyList(),
                            )
                        }
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

    fun onNavigateUp(): Boolean {
        val current = _uiState.value.currentPath
        val parentPath = RemoteDirectory.getParentPath(current) ?: return false
        loadDirectory(parentPath, forceRefresh = false)
        return true
    }

    fun onRefresh() {
        refreshCurrentDirectory()
    }

    fun onRetry() {
        refreshCurrentDirectory()
    }

    private fun refreshCurrentDirectory() {
        val server = _uiState.value.activeServer ?: return
        val path = _uiState.value.currentPath
        val normalizedPath = RemoteDirectory.normalizePath(path)

        // Cancel any pending non-force directory loading flow
        currentLoadJob?.cancel()

        val previousRefresh = activeRefreshJob
        activeRefreshJob =
            viewModelScope.launch {
                if (previousRefresh != null && previousRefresh.isActive) {
                    // Sequence safely: await any active in-flight refresh so it completes cleanly
                    // without prematurely cancelling it or discarding partial batches.
                    previousRefresh.join()
                }

                if (_uiState.value.currentPath != normalizedPath || _uiState.value.activeServer?.id != server.id) {
                    return@launch
                }

                executeRefresh(server, normalizedPath)
            }
    }

    private suspend fun executeRefresh(
        server: WebDavServer,
        normalizedPath: String,
    ) {
        val myJob = coroutineContext[Job]
        _uiState.update { current ->
            if (current.currentPath == normalizedPath) {
                current.copy(
                    isLoading = current.currentDirectory == null,
                    isRefreshing = current.currentDirectory != null,
                    errorMessage = null,
                )
            } else {
                current
            }
        }

        try {
            var refreshedDirectory: RemoteDirectory? = null

            directoryRepository.observeDirectory(server, normalizedPath, forceRefresh = true).collect { result ->
                when (result) {
                    is ListDirectoryResult.Success -> {
                        refreshedDirectory = result.directory
                        musicPlayerAppSession?.setCurrentDirectoryPath(normalizedPath)
                        processDirectoryCueAlbums(server, result.directory)
                        _uiState.update { current ->
                            if (current.currentPath == normalizedPath) {
                                current.copy(
                                    currentDirectory = result.directory,
                                    isLoading = false,
                                    // Notice: isRefreshing stays true until metadata resolution finishes!
                                    errorMessage = null,
                                )
                            } else {
                                current
                            }
                        }
                    }

                    is ListDirectoryResult.Failure -> {
                        _uiState.update { current ->
                            if (current.currentPath == normalizedPath) {
                                val hasExistingDirectoryForPath =
                                    current.currentDirectory != null && current.currentDirectory.path == normalizedPath
                                if (hasExistingDirectoryForPath || refreshedDirectory != null) {
                                    // Transient network error during background refresh:
                                    // Do NOT wipe previously rendered cached directory or cause UI flickering!
                                    current.copy(
                                        isLoading = false,
                                        isRefreshing = false,
                                    )
                                } else {
                                    current.copy(
                                        isLoading = false,
                                        isRefreshing = false,
                                        errorMessage = result.message,
                                    )
                                }
                            } else {
                                current
                            }
                        }
                    }
                }
            }

            val dir = refreshedDirectory
            if (dir != null && _uiState.value.currentPath == normalizedPath) {
                val audioFiles = dir.files.filter { it.isAudio }
                if (audioFiles.isNotEmpty() && trackMetadataRepository != null) {
                    // If a previous background metadata resolution is in flight, await it safely
                    val inFlightMeta = metadataResolutionJob
                    if (inFlightMeta != null && inFlightMeta.isActive && inFlightMeta != myJob) {
                        inFlightMeta.join()
                    }
                    activeMetadataPath = normalizedPath
                    metadataResolutionJob = myJob
                    trackMetadataRepository.resolveMetadata(
                        server = server,
                        files = audioFiles,
                        forceRefresh = true,
                    )
                }
            }
        } finally {
            _uiState.update { current ->
                if (current.currentPath == normalizedPath) {
                    // Only dismiss isRefreshing if this was the last queued refresh job
                    if (activeRefreshJob == null || activeRefreshJob == myJob || !activeRefreshJob!!.isActive) {
                        current.copy(isRefreshing = false, isLoading = false)
                    } else {
                        current
                    }
                } else {
                    current
                }
            }
        }
    }

    private fun loadDirectory(
        path: String,
        forceRefresh: Boolean,
        fallbackToRoot: Boolean = false,
    ) {
        val server = _uiState.value.activeServer ?: return
        val normalizedPath = RemoteDirectory.normalizePath(path)

        if (forceRefresh) {
            refreshCurrentDirectory()
            return
        }

        currentLoadJob?.cancel()
        activeRefreshJob?.cancel()
        cueResolutionJob?.cancel()
        if (activeMetadataPath != normalizedPath) {
            metadataResolutionJob?.cancel()
            activeMetadataPath = null
        }

        currentLoadJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        currentPath = normalizedPath,
                        breadcrumbs = RemoteDirectory.buildBreadcrumbs(server, normalizedPath),
                        canNavigateUp = normalizedPath != "/",
                        isLoading = true,
                        isRefreshing = false,
                        errorMessage = null,
                    )
                }

                var hasEmittedContent = false

                directoryRepository.observeDirectory(server, normalizedPath, forceRefresh = false).collect { result ->
                    when (result) {
                        is ListDirectoryResult.Success -> {
                            hasEmittedContent = true
                            musicPlayerAppSession?.setCurrentDirectoryPath(normalizedPath)
                            processDirectoryCueAlbums(server, result.directory)
                            val audioFiles = result.directory.files.filter { it.isAudio }
                            if (audioFiles.isNotEmpty() && trackMetadataRepository != null) {
                                if (activeMetadataPath != normalizedPath || metadataResolutionJob?.isActive != true) {
                                    activeMetadataPath = normalizedPath
                                    metadataResolutionJob?.cancel()
                                    metadataResolutionJob =
                                        viewModelScope.launch {
                                            trackMetadataRepository.resolveMetadata(
                                                server = server,
                                                files = audioFiles,
                                                forceRefresh = false,
                                            )
                                        }
                                }
                            }

                            _uiState.update { current ->
                                current.copy(
                                    currentDirectory = result.directory,
                                    isLoading = false,
                                    errorMessage = null,
                                )
                            }
                        }

                        is ListDirectoryResult.Failure -> {
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
                                        errorMessage = result.message,
                                    )
                                }
                            }
                        }
                    }
                }

                _uiState.update { current ->
                    if (current.isLoading) {
                        current.copy(isLoading = false)
                    } else {
                        current
                    }
                }
            }
    }

    fun toggleCueAlbumExpanded(cuePath: String) {
        _uiState.update { current ->
            val updated =
                current.cueAlbums.map { album ->
                    if (album.cueFile.path == cuePath) {
                        album.copy(isExpanded = !album.isExpanded)
                    } else {
                        album
                    }
                }
            current.copy(cueAlbums = updated)
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
        if (album.tracks.isEmpty()) {
            onAudioTrackClicked(audioFile)
            return
        }
        playVirtualTrack(album, trackIndex = 0)
    }

    private fun processDirectoryCueAlbums(
        server: WebDavServer,
        directory: RemoteDirectory,
    ) {
        cueResolutionJob?.cancel()
        val cueFiles = directory.files.filter { it.isCue }
        val audioFiles = directory.files.filter { it.isAudio }

        if (cueFiles.isEmpty()) {
            _uiState.update { it.copy(cueAlbums = emptyList()) }
            return
        }

        val initialAlbums =
            cueFiles.map { cueFile ->
                val matchedAudio =
                    CueAssociationHelper.findMatchingAudioFile(
                        cueFile = cueFile,
                        audioFiles = audioFiles,
                        totalCueFilesCount = cueFiles.size,
                    )
                CueAlbumItem(
                    cueFile = cueFile,
                    audioFile = matchedAudio,
                    tracks = emptyList(),
                    isExpanded = false,
                    isLoading = webDavClient != null,
                    errorMessage = if (webDavClient == null && matchedAudio == null) "未找到对应的目标音频文件" else null,
                )
            }

        _uiState.update { it.copy(cueAlbums = initialAlbums) }

        if (webDavClient == null || initialAlbums.isEmpty()) {
            return
        }

        cueResolutionJob =
            viewModelScope.launch {
                val resolvedAlbums =
                    initialAlbums.map { initialAlbum ->
                        try {
                            val cachedCue = com.webdav.player.data.cue.CueTextCache.get(server.id, initialAlbum.cueFile.path)
                            val cueContent = cachedCue ?: webDavClient.fetchText(server, initialAlbum.cueFile.path)?.also {
                                com.webdav.player.data.cue.CueTextCache.put(server.id, initialAlbum.cueFile.path, it)
                            }
                            if (cueContent.isNullOrBlank()) {
                                initialAlbum.copy(
                                    isLoading = false,
                                    errorMessage = "无法读取 CUE 文件内容",
                                )
                            } else {
                                val referencedFiles = CueParser.extractReferencedFiles(cueContent)
                                val matchedAudio =
                                    CueAssociationHelper.findMatchingAudioFile(
                                        cueFile = initialAlbum.cueFile,
                                        audioFiles = audioFiles,
                                        referencedFileNames = referencedFiles,
                                        totalCueFilesCount = cueFiles.size,
                                    ) ?: if (referencedFiles.isEmpty()) initialAlbum.audioFile else null

                                if (matchedAudio == null) {
                                    initialAlbum.copy(
                                        audioFile = null,
                                        isLoading = false,
                                        errorMessage = "未找到 CUE 指向的目标音频文件",
                                    )
                                } else {
                                    val parentDurationMs = _uiState.value.metadataMap[matchedAudio.path]?.durationMs
                                    val tracks =
                                        CueParser.parse(
                                            content = cueContent,
                                            parentAudioPath = matchedAudio.path,
                                            totalDurationMs = parentDurationMs,
                                        )

                                    if (tracks.isEmpty()) {
                                        initialAlbum.copy(
                                            audioFile = matchedAudio,
                                            isLoading = false,
                                            errorMessage = "CUE 文件未包含有效分轨",
                                        )
                                    } else {
                                        initialAlbum.copy(
                                            audioFile = matchedAudio,
                                            tracks = tracks,
                                            isLoading = false,
                                            errorMessage = null,
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            initialAlbum.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "解析 CUE 文件失败",
                            )
                        }
                    }

                _uiState.update { current ->
                    if (current.currentPath == directory.path) {
                        val prevMap = current.cueAlbums.associateBy { it.cueFile.path }
                        val merged =
                            resolvedAlbums.map { alb ->
                                val prev = prevMap[alb.cueFile.path]
                                if (prev != null) alb.copy(isExpanded = prev.isExpanded) else alb
                            }
                        current.copy(cueAlbums = merged)
                    } else {
                        current
                    }
                }
            }
    }

    private fun resolveActivePlayback(
        server: WebDavServer?,
        track: AudioTrack?,
        isPlaying: Boolean,
    ): Triple<String?, String?, Boolean> {
        val matchesServer = track != null && server != null && track.serverId == server.id
        return if (matchesServer) {
            Triple(track?.remotePath, track?.id, isPlaying)
        } else {
            Triple(null, null, false)
        }
    }

    private fun buildBreadcrumbs(
        server: WebDavServer?,
        path: String,
    ): List<Breadcrumb> = RemoteDirectory.buildBreadcrumbs(server, path)
}
