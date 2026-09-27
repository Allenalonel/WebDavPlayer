package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.LyricsRepository
import com.webdav.player.domain.repository.PlaybackSessionStore
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerAppSessionImpl(
    private val playerEngine: AudioPlayerEngine,
    private val serverRepository: ServerRepository? = null,
    private val trackMetadataRepository: TrackMetadataRepository? = null,
    private val lyricsRepository: LyricsRepository? = null,
    private val sessionStore: PlaybackSessionStore? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val progressDispatcher: CoroutineDispatcher =
        (coroutineScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as? CoroutineDispatcher)?.takeIf {
            it !is kotlinx.coroutines.MainCoroutineDispatcher
        }
            ?: Dispatchers.Default,
    private val periodicDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MusicPlayerAppSession {
    private val _sessionState = MutableStateFlow(PlayerSessionState())
    override val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(PlaybackProgress.ZERO)
    override val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private val _isRestored = MutableStateFlow(sessionStore == null)
    override val isRestored: StateFlow<Boolean> = _isRestored.asStateFlow()

    private val serverLastDirectories = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private var periodicFlushJob: Job? = null

    init {
        coroutineScope.launch {
            // Restore cold start state first if store is provided
            if (sessionStore != null) {
                try {
                    restoreSession()
                } finally {
                    _isRestored.value = true
                }
            } else {
                _isRestored.value = true
            }

            // Observe active server from repository after initial restoration
            if (serverRepository != null) {
                serverRepository.getActiveServer().collect { server ->
                    val current = _sessionState.value
                    if (current.activeServer?.id != server?.id) {
                        setActiveServer(server)
                    }
                }
            }
        }

        // Observe player engine state changes
        coroutineScope.launch {
            playerEngine.playbackState.collect { state ->
                _sessionState.update { current ->
                    val targetState =
                        if (state is PlaybackState.Idle && current.isPaused && current.hasTrack &&
                            playerEngine.currentTrackIndex.value == -1
                        ) {
                            current.playbackState
                        } else {
                            state
                        }
                    current.copy(
                        playbackState = targetState,
                        errorMessage = if (state is PlaybackState.Error) state.message else current.errorMessage,
                    )
                }
                if (state is PlaybackState.Playing) {
                    startPeriodicFlush()
                } else {
                    stopPeriodicFlush()
                }
            }
        }

        // High-frequency playback progress pipeline calculated and throttled off the main thread
        coroutineScope.launch(progressDispatcher) {
            combine(
                playerEngine.currentPositionMs,
                playerEngine.durationMs,
                playerEngine.bufferedPositionMs,
            ) { pos, dur, buf ->
                val fallbackDur = _sessionState.value.currentTrack?.durationMs ?: 0L
                val effectiveDur = if (dur > 0L) dur else fallbackDur
                PlaybackProgress(
                    currentPositionMs = pos,
                    durationMs = effectiveDur,
                    bufferedPositionMs = buf,
                )
            }.distinctUntilChanged()
                .collect { progress ->
                    if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                        _playbackProgress.value = progress
                    }
                }
        }

        coroutineScope.launch {
            playerEngine.durationMs.collect { dur ->
                if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                    _sessionState.update { it.copy(durationMs = dur) }
                }
            }
        }

        coroutineScope.launch {
            playerEngine.currentTrackIndex.collect { index ->
                if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                    var indexChanged = false
                    _sessionState.update { current ->
                        if (index in current.queue.tracks.indices) {
                            if (current.queue.currentIndex != index) {
                                indexChanged = true
                                current.copy(
                                    queue = current.queue.copy(currentIndex = index),
                                )
                            } else {
                                current
                            }
                        } else {
                            current
                        }
                    }
                    if (indexChanged) {
                        _playbackProgress.value =
                            PlaybackProgress(
                                currentPositionMs = 0L,
                                durationMs = _sessionState.value.currentTrack?.durationMs ?: 0L,
                                bufferedPositionMs = 0L,
                            )
                        flushSession()
                    }
                }
            }
        }

        coroutineScope.launch {
            playerEngine.playbackMode.collect { mode ->
                _sessionState.update { it.copy(playbackMode = mode) }
            }
        }

        // Observe metadata changes and enrich queue tracks reactively
        if (trackMetadataRepository != null) {
            coroutineScope.launch {
                var currentMetadataJob: Job? = null
                _sessionState
                    .map { it.activeServer?.id }
                    .distinctUntilChanged()
                    .collect { serverId ->
                        currentMetadataJob?.cancel()
                        if (serverId != null) {
                            currentMetadataJob =
                                coroutineScope.launch {
                                    trackMetadataRepository.getAllMetadataFlow(serverId).collect { metadataList ->
                                        if (metadataList.isNotEmpty()) {
                                            val metaMap = metadataList.associateBy { it.remotePath }
                                            val tracksToUpdate = mutableListOf<Pair<Int, AudioTrack>>()

                                            _sessionState.update { current ->
                                                var anyChanged = false
                                                val updatedTracks =
                                                    current.queue.tracks.mapIndexed { index, track ->
                                                        val meta = metaMap[track.remotePath]
                                                        if (meta != null) {
                                                            val enriched = track.withMetadata(meta)
                                                            if (enriched != track) {
                                                                anyChanged = true
                                                                tracksToUpdate.add(index to enriched)
                                                                enriched
                                                            } else {
                                                                track
                                                            }
                                                        } else {
                                                            track
                                                        }
                                                    }
                                                if (anyChanged) {
                                                    current.copy(queue = current.queue.copy(tracks = updatedTracks))
                                                } else {
                                                    current
                                                }
                                            }

                                            // Dispatch side-effects (playerEngine updates) outside the StateFlow reducer
                                            tracksToUpdate.forEach { (index, enriched) ->
                                                playerEngine.updateTrack(index, enriched)
                                            }
                                        }
                                    }
                                }
                        }
                    }
            }
        }

        // Observe active track changes and load lyrics reactively
        if (lyricsRepository != null) {
            coroutineScope.launch {
                var lyricJob: Job? = null
                _sessionState
                    .map { state ->
                        val server = state.activeServer
                        val track = state.currentTrack
                        if (server != null && track != null) server to track else null
                    }.distinctUntilChanged()
                    .collect { pair ->
                        lyricJob?.cancel()
                        if (pair == null) {
                            _sessionState.update { it.copy(lyrics = null, isLoadingLyrics = false) }
                        } else {
                            val (server, track) = pair
                            _sessionState.update { it.copy(lyrics = null, isLoadingLyrics = true) }
                            lyricJob =
                                coroutineScope.launch {
                                    val resolved = lyricsRepository.resolveLyrics(server, track)
                                    _sessionState.update { it.copy(lyrics = resolved, isLoadingLyrics = false) }
                                }
                        }
                    }
            }
        }
    }

    override fun getLastDirectoryForServer(serverId: Long): String = serverLastDirectories[serverId] ?: "/"

    override fun setActiveServer(server: WebDavServer?) {
        _sessionState.update { current ->
            if (current.activeServer?.id != server?.id) {
                // If on cold start activeServer was null and we already have a restored session/queue,
                // do NOT clear the queue or playback state! Attach the server and keep the restored session.
                if (current.activeServer == null && current.hasTrack) {
                    val path =
                        if (current.currentDirectoryPath.isNotBlank() && current.currentDirectoryPath != "/") {
                            current.currentDirectoryPath
                        } else {
                            if (server != null) getLastDirectoryForServer(server.id) else "/"
                        }
                    return@update current.copy(
                        activeServer = server,
                        currentDirectoryPath = path,
                    )
                }

                if (current.playbackState !is PlaybackState.Idle) {
                    playerEngine.stop()
                }
                _playbackProgress.value = PlaybackProgress.ZERO
                val lastPath = if (server != null) getLastDirectoryForServer(server.id) else "/"
                current.copy(
                    activeServer = server,
                    queue = PlaybackQueue.EMPTY,
                    playbackState = PlaybackState.Idle,
                    durationMs = 0L,
                    currentDirectoryPath = lastPath,
                    errorMessage = null,
                    lyrics = null,
                    isLoadingLyrics = false,
                )
            } else {
                current.copy(activeServer = server)
            }
        }
    }

    override fun playDirectoryTrack(
        directory: RemoteDirectory,
        selectedFile: RemoteFile,
    ) {
        _isRestored.value = true
        val server = _sessionState.value.activeServer ?: return
        val audioFiles = directory.files.filter { it.isAudio }
        if (audioFiles.isEmpty()) return

        val tracks = audioFiles.mapNotNull { AudioTrack.fromRemoteFile(server, it) }
        if (tracks.isEmpty()) return

        val selectedIndex =
            tracks
                .indexOfFirst { it.remotePath == selectedFile.path }
                .takeIf { it >= 0 } ?: 0

        val queue = PlaybackQueue(tracks = tracks, currentIndex = selectedIndex)
        _playbackProgress.value =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = tracks.getOrNull(selectedIndex)?.durationMs ?: 0L,
                bufferedPositionMs = 0L,
            )
        _sessionState.update {
            it.copy(
                queue = queue,
                currentDirectoryPath = directory.path,
                errorMessage = null,
            )
        }

        playerEngine.playTracks(
            server = server,
            tracks = tracks,
            startIndex = selectedIndex,
        )

        coroutineScope.launch { flushSession() }
    }

    override fun playTrack(track: AudioTrack) {
        _isRestored.value = true
        val server = _sessionState.value.activeServer ?: return
        val queue = PlaybackQueue(tracks = listOf(track), currentIndex = 0)
        _playbackProgress.value =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                bufferedPositionMs = 0L,
            )
        _sessionState.update { it.copy(queue = queue, errorMessage = null) }
        playerEngine.playTracks(server = server, tracks = listOf(track), startIndex = 0)

        if (trackMetadataRepository != null) {
            coroutineScope.launch {
                val file = RemoteFile(name = track.title, path = track.remotePath)
                val meta = trackMetadataRepository.resolveSingleTrackMetadata(server, file)
                var updatedIndex: Int? = null
                var enrichedTrack: AudioTrack? = null

                _sessionState.update { current ->
                    var changed = false
                    val updated =
                        current.queue.tracks.mapIndexed { index, t ->
                            if (t.id == track.id) {
                                val enriched = t.withMetadata(meta)
                                if (enriched != t) {
                                    changed = true
                                    updatedIndex = index
                                    enrichedTrack = enriched
                                    enriched
                                } else {
                                    t
                                }
                            } else {
                                t
                            }
                        }
                    if (changed) current.copy(queue = current.queue.copy(tracks = updated)) else current
                }

                if (updatedIndex != null && enrichedTrack != null) {
                    playerEngine.updateTrack(updatedIndex!!, enrichedTrack!!)
                }
            }
        }
    }

    override fun playNext(track: AudioTrack) {
        val server = _sessionState.value.activeServer ?: return
        val currentQueue = _sessionState.value.queue
        if (currentQueue.isEmpty) {
            playTrack(track)
            return
        }
        val insertIndex = (currentQueue.currentIndex + 1).coerceIn(0, currentQueue.tracks.size)
        val updatedQueue = currentQueue.insertNext(track)
        _sessionState.update { it.copy(queue = updatedQueue) }
        playerEngine.insertTrack(insertIndex, server, track)
        coroutineScope.launch { flushSession() }
    }

    override fun togglePlayPause() {
        val current = _sessionState.value
        when {
            current.isPlaying -> {
                playerEngine.pause()
                coroutineScope.launch {
                    if (_isRestored.value) {
                        val pos = playerEngine.currentPositionMs.value
                        if (pos > 0L) {
                            sessionStore?.savePosition(pos)
                        }
                    }
                    flushSession()
                }
            }

            current.isPaused -> {
                if (playerEngine.playbackState.value is PlaybackState.Idle && current.currentTrack != null &&
                    current.activeServer != null
                ) {
                    playerEngine.playTracks(
                        server = current.activeServer,
                        tracks = current.queue.tracks,
                        startIndex = current.queue.currentIndex.coerceAtLeast(0),
                        startPositionMs = _playbackProgress.value.currentPositionMs,
                    )
                } else {
                    playerEngine.play()
                }
            }

            current.currentTrack != null -> {
                val server = current.activeServer ?: return
                playerEngine.playTracks(
                    server = server,
                    tracks = current.queue.tracks,
                    startIndex = current.queue.currentIndex.coerceAtLeast(0),
                    startPositionMs = _playbackProgress.value.currentPositionMs,
                )
            }
        }
    }

    override fun play() {
        val current = _sessionState.value
        if (current.isPaused) {
            if (playerEngine.playbackState.value is PlaybackState.Idle && current.currentTrack != null && current.activeServer != null) {
                playerEngine.playTracks(
                    server = current.activeServer,
                    tracks = current.queue.tracks,
                    startIndex = current.queue.currentIndex.coerceAtLeast(0),
                    startPositionMs = _playbackProgress.value.currentPositionMs,
                )
            } else {
                playerEngine.play()
            }
        } else if (current.currentTrack != null && current.activeServer != null) {
            playerEngine.playTracks(
                server = current.activeServer,
                tracks = current.queue.tracks,
                startIndex = current.queue.currentIndex.coerceAtLeast(0),
                startPositionMs = _playbackProgress.value.currentPositionMs,
            )
        }
    }

    override fun pause() {
        playerEngine.pause()
        coroutineScope.launch {
            if (_isRestored.value) {
                val pos = playerEngine.currentPositionMs.value
                if (pos > 0L) {
                    sessionStore?.savePosition(pos)
                }
            }
            flushSession()
        }
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        _playbackProgress.update { it.copy(currentPositionMs = clamped) }
        if (playerEngine.playbackState.value !is PlaybackState.Idle) {
            playerEngine.seekTo(clamped)
        }
        coroutineScope.launch {
            if (_isRestored.value) {
                sessionStore?.savePosition(clamped)
            }
        }
    }

    override fun skipToNext() {
        playerEngine.skipToNext()
    }

    override fun skipToPrevious() {
        playerEngine.skipToPrevious()
    }

    override fun playQueueIndex(index: Int) {
        val queue = _sessionState.value.queue
        if (index in queue.tracks.indices) {
            playerEngine.seekToTrack(index)
        }
    }

    override fun cyclePlaybackMode() {
        val nextMode = _sessionState.value.playbackMode.next()
        setPlaybackMode(nextMode)
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        _sessionState.update { it.copy(playbackMode = mode) }
        playerEngine.setPlaybackMode(mode)
        coroutineScope.launch { flushSession() }
    }

    override fun removeQueueTrack(index: Int) {
        val currentQueue = _sessionState.value.queue
        if (index !in currentQueue.tracks.indices) return

        val updatedQueue = currentQueue.removeTrackAt(index)
        playerEngine.removeTrack(index)

        if (updatedQueue.isEmpty) {
            _playbackProgress.value = PlaybackProgress.ZERO
            _sessionState.update {
                it.copy(
                    queue = PlaybackQueue.EMPTY,
                    playbackState = PlaybackState.Idle,
                    durationMs = 0L,
                    lyrics = null,
                    isLoadingLyrics = false,
                )
            }
        } else {
            _sessionState.update {
                it.copy(queue = updatedQueue)
            }
        }
        coroutineScope.launch { flushSession() }
    }

    override fun stop() {
        stopPeriodicFlush()
        playerEngine.stop()
        _playbackProgress.value = PlaybackProgress.ZERO
        coroutineScope.launch { flushSession() }
    }

    override fun release() {
        stopPeriodicFlush()
        playerEngine.release()
        _playbackProgress.value = PlaybackProgress.ZERO
    }

    override fun flushSessionAsync() {
        coroutineScope.launch {
            flushSession()
        }
    }

    private fun startPeriodicFlush() {
        if (sessionStore == null || periodicFlushJob?.isActive == true) return
        periodicFlushJob =
            coroutineScope.launch(periodicDispatcher) {
                while (isActive) {
                    delay(1000L)
                    if (_isRestored.value && playerEngine.playbackState.value is PlaybackState.Playing) {
                        val pos = playerEngine.currentPositionMs.value
                        if (pos > 0L) {
                            sessionStore.savePosition(pos)
                        }
                    }
                }
            }
    }

    private fun stopPeriodicFlush() {
        periodicFlushJob?.cancel()
        periodicFlushJob = null
    }

    override fun setCurrentDirectoryPath(path: String) {
        val serverId = _sessionState.value.activeServer?.id
        if (serverId != null) {
            serverLastDirectories[serverId] = path
        }
        _sessionState.update { it.copy(currentDirectoryPath = path) }
        coroutineScope.launch { flushSession() }
    }

    override suspend fun restoreSession() {
        val store = sessionStore ?: return
        try {
            val savedSession = store.getSavedSession() ?: return
            savedSession.serverLastDirectories.forEach { (id, path) ->
                serverLastDirectories[id] = path
            }
            val serverId = savedSession.activeServerId
            if (serverId != null && savedSession.currentDirectoryPath.isNotBlank()) {
                serverLastDirectories[serverId] = savedSession.currentDirectoryPath
            }

            val server =
                if (serverId != null) {
                    serverRepository?.getServerById(serverId)
                } else {
                    null
                }

            // If server ID was specified but server no longer exists in repository, gracefully do not restore
            if (serverId != null && server == null) {
                return
            }

            val current = _sessionState.value
            val active = current.activeServer
            // If active server is already different from saved server, don't overwrite
            if (active != null && server != null && active.id != server.id) {
                return
            }

            // Don't overwrite if playback is actively happening
            if (current.hasTrack && current.isPlaying) {
                return
            }

            val restoredQueue =
                PlaybackQueue(
                    tracks = savedSession.queueTracks,
                    currentIndex = savedSession.currentTrackIndex.coerceIn(-1, savedSession.queueTracks.lastIndex),
                )
            val restoredTrack = restoredQueue.currentTrack
            val restoredPlaybackState = if (restoredTrack != null) PlaybackState.Paused else PlaybackState.Idle
            var durationMs = restoredTrack?.durationMs ?: 0L

            var finalQueue = restoredQueue
            if (durationMs <= 0L && server != null && restoredTrack != null) {
                val cachedMeta = trackMetadataRepository?.getCachedMetadata(server.id, restoredTrack.remotePath)
                if (cachedMeta != null && cachedMeta.durationMs > 0L) {
                    durationMs = cachedMeta.durationMs
                    val updatedTracks =
                        savedSession.queueTracks.mapIndexed { index, track ->
                            if (index == savedSession.currentTrackIndex) track.withMetadata(cachedMeta) else track
                        }
                    finalQueue = PlaybackQueue(tracks = updatedTracks, currentIndex = savedSession.currentTrackIndex)
                }
            }

            _sessionState.update {
                it.copy(
                    activeServer = server ?: it.activeServer,
                    queue = finalQueue,
                    playbackState = restoredPlaybackState,
                    playbackMode = savedSession.playbackMode,
                    durationMs = durationMs,
                    currentDirectoryPath = savedSession.currentDirectoryPath,
                    errorMessage = null,
                )
            }
            _playbackProgress.value =
                PlaybackProgress(
                    currentPositionMs = savedSession.positionMs,
                    durationMs = durationMs,
                    bufferedPositionMs = 0L,
                )

            playerEngine.setPlaybackMode(savedSession.playbackMode)
        } finally {
            _isRestored.value = true
        }
    }

    override suspend fun flushSession() {
        if (!_isRestored.value) return
        val store = sessionStore ?: return
        val current = _sessionState.value
        val currentServerId = current.activeServer?.id
        if (currentServerId != null && current.currentDirectoryPath.isNotBlank()) {
            serverLastDirectories[currentServerId] = current.currentDirectoryPath
        }
        if (current.activeServer == null && current.queue.isEmpty && serverLastDirectories.isEmpty()) {
            store.clearSession()
            return
        }

        // Query live engine position if active
        val livePositionMs =
            if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                val enginePos = playerEngine.currentPositionMs.value
                if (enginePos > 0L) enginePos else _playbackProgress.value.currentPositionMs
            } else {
                _playbackProgress.value.currentPositionMs
            }

        val sessionData =
            PlaybackSessionData(
                activeServerId = current.activeServer?.id,
                currentDirectoryPath = current.currentDirectoryPath,
                queueTracks = current.queue.tracks,
                currentTrackIndex = current.queue.currentIndex,
                positionMs = livePositionMs,
                playbackMode = current.playbackMode,
                serverLastDirectories = serverLastDirectories.toMap(),
            )
        store.saveSession(sessionData)
    }
}
