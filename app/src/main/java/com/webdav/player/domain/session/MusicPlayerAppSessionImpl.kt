package com.webdav.player.domain.session

import com.webdav.player.data.cue.CueParser
import com.webdav.player.data.cue.CueTextCache
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.VirtualTrack
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
    private val webDavClient: WebDavClient? = null,
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
    private val internalJobs = java.util.concurrent.CopyOnWriteArrayList<Job>()

    private val virtualTimelineEngine = VirtualTimelineEngine()
    private var activeParentAudioTrack: AudioTrack? = null
    private var activeCuePath: String? = null
    private var activePhysicalTracks: List<AudioTrack> = emptyList()

    init {
        playerEngine.setSkipHandler(
            object : AudioPlayerEngine.SkipHandler {
                override fun onSkipToNext(): Boolean {
                    if (virtualTimelineEngine.isActive) {
                        val seekResult = virtualTimelineEngine.getNextTrackSeekPosition(_sessionState.value.playbackMode)
                        if (seekResult != null) {
                            applyVirtualTrackSeek(seekResult)
                            return true
                        }
                    }
                    return false
                }

                override fun onSkipToPrevious(): Boolean {
                    if (virtualTimelineEngine.isActive) {
                        val seekResult =
                            virtualTimelineEngine.getPreviousTrackSeekPosition(
                                globalPositionMs = playerEngine.currentPositionMs.value,
                                mode = _sessionState.value.playbackMode,
                            )
                        if (seekResult != null) {
                            applyVirtualTrackSeek(seekResult)
                            return true
                        }
                    }
                    return false
                }
            },
        )

        internalJobs +=
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
                    try {
                        serverRepository.getActiveServer().collect { server ->
                            val current = _sessionState.value
                            if (current.activeServer?.id != server?.id) {
                                setActiveServer(server)
                            }
                        }
                    } catch (e: Throwable) {
                        // Gracefully ignore closed database or cancelled repository flow during teardown
                    }
                }
            }

        // Observe player engine state changes
        internalJobs +=
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
        internalJobs +=
            coroutineScope.launch(progressDispatcher) {
                combine(
                    playerEngine.currentPositionMs,
                    playerEngine.durationMs,
                    playerEngine.bufferedPositionMs,
                ) { pos, dur, buf ->
                    if (virtualTimelineEngine.isActive) {
                        if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                            val transition = virtualTimelineEngine.onPositionUpdate(pos)
                            if (transition is VirtualTimelineEngine.TransitionResult.Transitioned) {
                                handleVirtualTrackTransition(transition.newIndex, transition.newTrack)
                            }
                            virtualTimelineEngine.mapToVirtualProgress(pos, dur, buf)
                        } else {
                            _playbackProgress.value
                        }
                    } else {
                        val fallbackDur = _sessionState.value.currentTrack?.durationMs ?: 0L
                        val effectiveDur = if (dur > 0L) dur else fallbackDur
                        PlaybackProgress(
                            currentPositionMs = pos,
                            durationMs = effectiveDur,
                            bufferedPositionMs = buf,
                        )
                    }
                }.distinctUntilChanged()
                    .collect { progress ->
                        if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                            _playbackProgress.value = progress
                        }
                    }
            }

        internalJobs +=
            coroutineScope.launch {
                playerEngine.durationMs.collect { dur ->
                    if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                        if (!virtualTimelineEngine.isActive) {
                            _sessionState.update { it.copy(durationMs = dur) }
                        }
                    }
                }
            }

        internalJobs +=
            coroutineScope.launch {
                playerEngine.currentTrackIndex.collect { index ->
                    if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                        if (virtualTimelineEngine.isActive) {
                            return@collect
                        }
                        val targetQueueIndex =
                            if (activePhysicalTracks.isNotEmpty() && index in activePhysicalTracks.indices) {
                                val physTrack = activePhysicalTracks[index]
                                val currentTrack = _sessionState.value.queue.currentTrack
                                if (currentTrack?.remotePath == physTrack.remotePath) {
                                    _sessionState.value.queue.currentIndex
                                } else {
                                    _sessionState.value.queue.tracks.indexOfFirst { it.remotePath == physTrack.remotePath }
                                        .takeIf { it >= 0 } ?: index
                                }
                            } else {
                                index
                            }

                        var indexChanged = false
                        _sessionState.update { current ->
                            if (targetQueueIndex in current.queue.tracks.indices) {
                                if (current.queue.currentIndex != targetQueueIndex) {
                                    indexChanged = true
                                    current.copy(
                                        queue = current.queue.copy(currentIndex = targetQueueIndex),
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
                virtualTimelineEngine.clear()
                activeParentAudioTrack = null
                activeCuePath = null
                activePhysicalTracks = emptyList()
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
        initialMetadata: Map<String, TrackMetadata>,
        virtualTracksByAudioPath: Map<String, List<VirtualTrack>>,
    ) {
        _isRestored.value = true
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null
        val server = _sessionState.value.activeServer ?: return
        val audioFiles = directory.files.filter { it.isAudio }
        if (audioFiles.isEmpty()) return

        val tracks =
            audioFiles.mapNotNull { file ->
                val meta = initialMetadata[file.path]
                AudioTrack.fromRemoteFile(server, file, meta)
            }
        if (tracks.isEmpty()) return

        val selectedIndex =
            tracks
                .indexOfFirst { it.remotePath == selectedFile.path }
                .takeIf { it >= 0 } ?: 0

        val resolvedVirtualMap =
            if (virtualTracksByAudioPath.isNotEmpty()) {
                virtualTracksByAudioPath
            } else {
                val cueFiles = directory.files.filter { it.isCue }
                if (cueFiles.isNotEmpty()) {
                    val map = mutableMapOf<String, List<VirtualTrack>>()
                    for (cueFile in cueFiles) {
                        val cachedText = com.webdav.player.data.cue.CueTextCache.get(server.id, cueFile.path)
                        if (!cachedText.isNullOrBlank()) {
                            val matchedAudio =
                                com.webdav.player.data.cue.CueAssociationHelper.findMatchingAudioFile(
                                    cueFile = cueFile,
                                    audioFiles = audioFiles,
                                    totalCueFilesCount = cueFiles.size,
                                )
                            if (matchedAudio != null) {
                                val parentTrack = tracks.firstOrNull { it.remotePath == matchedAudio.path }
                                val parsed =
                                    com.webdav.player.data.cue.CueParser.parse(
                                        content = cachedText,
                                        parentAudioPath = matchedAudio.path,
                                        totalDurationMs = parentTrack?.durationMs,
                                    )
                                if (parsed.isNotEmpty()) {
                                    map[matchedAudio.path] = parsed
                                }
                            }
                        }
                    }
                    map
                } else {
                    emptyMap()
                }
            }

        val queue =
            PlaybackQueue.fromTracks(
                tracks = tracks,
                virtualTracksMap = resolvedVirtualMap,
                selectedIndex = selectedIndex,
            )

        val selectedTrack = queue.currentTrack ?: return
        val isVirtual = selectedTrack.isVirtualTrack

        if (isVirtual) {
            val parentTrack =
                tracks.firstOrNull { it.remotePath == selectedTrack.remotePath }
                    ?: selectedTrack.copy(id = selectedTrack.id.substringBefore("#cue_"))
            val virtualTracks = resolvedVirtualMap[selectedTrack.remotePath] ?: emptyList()
            val targetVirtualIndex =
                virtualTracks
                    .indexOfFirst {
                        it.trackNumber == selectedTrack.id.substringAfter("#cue_").toIntOrNull()
                    }.takeIf { it >= 0 } ?: 0

            activeParentAudioTrack = parentTrack
            activeCuePath =
                directory.files.firstOrNull { it.isCue }?.path
                    ?: (parentTrack.remotePath.substringBeforeLast('.') + ".cue")

            val startPositionMs = virtualTimelineEngine.loadTracks(virtualTracks, targetVirtualIndex)

            _playbackProgress.value =
                PlaybackProgress(
                    currentPositionMs = 0L,
                    durationMs = selectedTrack.durationMs,
                    bufferedPositionMs = 0L,
                )
            _sessionState.update {
                it.copy(
                    queue = queue,
                    durationMs = selectedTrack.durationMs,
                    currentDirectoryPath = directory.path,
                    errorMessage = null,
                )
            }

            activePhysicalTracks = emptyList()
            playerEngine.playTracks(
                server = server,
                tracks = listOf(parentTrack),
                startIndex = 0,
                startPositionMs = startPositionMs,
            )

            playerEngine.updateTrack(0, selectedTrack)
        } else {
            activePhysicalTracks = tracks
            _playbackProgress.value =
                PlaybackProgress(
                    currentPositionMs = 0L,
                    durationMs = selectedTrack.durationMs,
                    bufferedPositionMs = 0L,
                )
            _sessionState.update {
                it.copy(
                    queue = queue,
                    durationMs = selectedTrack.durationMs,
                    currentDirectoryPath = directory.path,
                    errorMessage = null,
                )
            }

            playerEngine.playTracks(
                server = server,
                tracks = tracks,
                startIndex = selectedIndex,
            )
        }

        // Ensure newly created queue items immediately enrich from Room if missing artwork/duration
        if (trackMetadataRepository != null) {
            coroutineScope.launch {
                val current = _sessionState.value.queue
                val unpopulatedPaths = current.tracks.filter { it.coverThumbnailPath == null }.map { it.remotePath }
                if (unpopulatedPaths.isNotEmpty()) {
                    val tracksToUpdate = mutableListOf<Pair<Int, AudioTrack>>()
                    _sessionState.update { state ->
                        var anyChanged = false
                        val updated =
                            state.queue.tracks.mapIndexed { index, track ->
                                if (track.coverThumbnailPath == null) {
                                    val meta = trackMetadataRepository.getCachedMetadata(server.id, track.remotePath)
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
                                } else {
                                    track
                                }
                            }
                        if (anyChanged) {
                            state.copy(queue = state.queue.copy(tracks = updated))
                        } else {
                            state
                        }
                    }
                    if (!isVirtual) {
                        tracksToUpdate.forEach { (index, enriched) ->
                            playerEngine.updateTrack(index, enriched)
                        }
                    } else {
                        val currentEnriched = tracksToUpdate.firstOrNull { it.first == queue.currentIndex }?.second
                        if (currentEnriched != null) {
                            playerEngine.updateTrack(0, currentEnriched)
                        }
                    }
                }
            }
        }

        coroutineScope.launch { flushSession() }
    }

    override fun playTrack(track: AudioTrack) {
        _isRestored.value = true
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null
        activePhysicalTracks = emptyList()
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
                val cachedMeta = trackMetadataRepository.getCachedMetadata(server.id, track.remotePath)
                val meta =
                    if (cachedMeta != null) {
                        cachedMeta
                    } else {
                        val file = RemoteFile(name = track.fileName, path = track.remotePath)
                        trackMetadataRepository.resolveSingleTrackMetadata(server, file)
                    }
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

    override fun playVirtualTracks(
        parentTrack: AudioTrack,
        virtualTracks: List<VirtualTrack>,
        startIndex: Int,
        cuePath: String?,
    ) {
        _isRestored.value = true
        val server = _sessionState.value.activeServer ?: return
        if (virtualTracks.isEmpty()) return

        val cleanParentTrack = parentTrack.copy(id = parentTrack.id.substringBefore("#cue_"))
        val validStartIndex = startIndex.coerceIn(0, virtualTracks.lastIndex)

        val isSameParentTrackActive =
            virtualTimelineEngine.isActive &&
                activeParentAudioTrack?.serverId == cleanParentTrack.serverId &&
                activeParentAudioTrack?.remotePath == cleanParentTrack.remotePath &&
                playerEngine.playbackState.value !is PlaybackState.Idle

        if (isSameParentTrackActive) {
            val seekResult = virtualTimelineEngine.seekToTrackIndex(validStartIndex)
            if (seekResult != null) {
                applyVirtualTrackSeek(seekResult)
                if (!_sessionState.value.isPlaying) {
                    playerEngine.play()
                }
                return
            }
        }

        activeParentAudioTrack = cleanParentTrack
        activeCuePath = cuePath ?: (parentTrack.remotePath.substringBeforeLast('.') + ".cue")
        activePhysicalTracks = emptyList()

        val mappedTracks =
            virtualTracks.map { vt ->
                AudioTrack(
                    id = "${activeParentAudioTrack!!.id}#cue_${vt.trackNumber}",
                    serverId = parentTrack.serverId,
                    remotePath = parentTrack.remotePath,
                    title = vt.title,
                    artist = vt.performer?.takeIf { it.isNotBlank() } ?: parentTrack.artist,
                    album = parentTrack.album,
                    durationMs = vt.durationMs,
                    size = parentTrack.size,
                    format = parentTrack.format,
                    coverThumbnailPath = parentTrack.coverThumbnailPath,
                )
            }

        val startPositionMs = virtualTimelineEngine.loadTracks(virtualTracks, validStartIndex)
        val initialVirtualTrack = mappedTracks[validStartIndex]
        val queue = PlaybackQueue(tracks = mappedTracks, currentIndex = validStartIndex)

        _playbackProgress.value =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = initialVirtualTrack.durationMs,
                bufferedPositionMs = 0L,
            )
        _sessionState.update {
            it.copy(
                queue = queue,
                durationMs = initialVirtualTrack.durationMs,
                errorMessage = null,
            )
        }

        playerEngine.playTracks(
            server = server,
            tracks = listOf(cleanParentTrack),
            startIndex = 0,
            startPositionMs = startPositionMs,
        )

        playerEngine.updateTrack(0, initialVirtualTrack)
        coroutineScope.launch { flushSession() }
    }

    private fun handleVirtualTrackTransition(
        newIndex: Int,
        newTrack: VirtualTrack,
    ) {
        var enrichedTrack: AudioTrack? = null
        _sessionState.update { current ->
            if (newIndex in current.queue.tracks.indices) {
                val updatedQueue = current.queue.copy(currentIndex = newIndex)
                enrichedTrack = updatedQueue.currentTrack
                current.copy(
                    queue = updatedQueue,
                    durationMs = newTrack.durationMs,
                )
            } else {
                current
            }
        }
        val targetTrack = enrichedTrack ?: return
        playerEngine.updateTrack(0, targetTrack)
        coroutineScope.launch { flushSession() }
    }

    private fun applyVirtualTrackSeek(seekResult: TrackSeekResult) {
        var enrichedTrack: AudioTrack? = null
        _sessionState.update { current ->
            if (seekResult.trackIndex in current.queue.tracks.indices) {
                val updatedQueue = current.queue.copy(currentIndex = seekResult.trackIndex)
                enrichedTrack = updatedQueue.currentTrack
                current.copy(
                    queue = updatedQueue,
                    durationMs = seekResult.track.durationMs,
                )
            } else {
                current
            }
        }
        val targetTrack = enrichedTrack
        if (targetTrack != null) {
            playerEngine.updateTrack(0, targetTrack)
        }
        _playbackProgress.value =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = seekResult.track.durationMs,
                bufferedPositionMs = 0L,
            )
        playerEngine.seekTo(seekResult.targetPositionMs)
        coroutineScope.launch { flushSession() }
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
                            if (virtualTimelineEngine.isActive) {
                                val startMs = virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L
                                val relPos = (pos - startMs).coerceAtLeast(0L)
                                sessionStore?.savePosition(pos, relPos)
                            } else {
                                sessionStore?.savePosition(pos)
                            }
                        }
                    }
                    flushSession()
                }
            }

            current.isPaused -> {
                if (playerEngine.playbackState.value is PlaybackState.Idle && current.currentTrack != null &&
                    current.activeServer != null
                ) {
                    if (virtualTimelineEngine.isActive && activeParentAudioTrack != null) {
                        val resumePos =
                            (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L) +
                                _playbackProgress.value.currentPositionMs
                        playerEngine.playTracks(
                            server = current.activeServer,
                            tracks = listOf(activeParentAudioTrack!!),
                            startIndex = 0,
                            startPositionMs = resumePos,
                        )
                        current.currentTrack?.let { playerEngine.updateTrack(0, it) }
                    } else {
                        playerEngine.playTracks(
                            server = current.activeServer,
                            tracks = current.queue.tracks,
                            startIndex = current.queue.currentIndex.coerceAtLeast(0),
                            startPositionMs = _playbackProgress.value.currentPositionMs,
                        )
                    }
                } else {
                    playerEngine.play()
                }
            }

            current.currentTrack != null -> {
                val server = current.activeServer ?: return
                if (virtualTimelineEngine.isActive && activeParentAudioTrack != null) {
                    val resumePos =
                        (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L) +
                            _playbackProgress.value.currentPositionMs
                    playerEngine.playTracks(
                        server = server,
                        tracks = listOf(activeParentAudioTrack!!),
                        startIndex = 0,
                        startPositionMs = resumePos,
                    )
                    current.currentTrack?.let { playerEngine.updateTrack(0, it) }
                } else {
                    playerEngine.playTracks(
                        server = server,
                        tracks = current.queue.tracks,
                        startIndex = current.queue.currentIndex.coerceAtLeast(0),
                        startPositionMs = _playbackProgress.value.currentPositionMs,
                    )
                }
            }
        }
    }

    override fun play() {
        val current = _sessionState.value
        if (current.isPaused) {
            if (playerEngine.playbackState.value is PlaybackState.Idle && current.currentTrack != null && current.activeServer != null) {
                if (virtualTimelineEngine.isActive && activeParentAudioTrack != null) {
                    val resumePos =
                        (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L) +
                            _playbackProgress.value.currentPositionMs
                    playerEngine.playTracks(
                        server = current.activeServer,
                        tracks = listOf(activeParentAudioTrack!!),
                        startIndex = 0,
                        startPositionMs = resumePos,
                    )
                    current.currentTrack?.let { playerEngine.updateTrack(0, it) }
                } else {
                    playerEngine.playTracks(
                        server = current.activeServer,
                        tracks = current.queue.tracks,
                        startIndex = current.queue.currentIndex.coerceAtLeast(0),
                        startPositionMs = _playbackProgress.value.currentPositionMs,
                    )
                }
            } else {
                playerEngine.play()
            }
        } else if (current.currentTrack != null && current.activeServer != null) {
            if (virtualTimelineEngine.isActive && activeParentAudioTrack != null) {
                val resumePos =
                    (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L) +
                        _playbackProgress.value.currentPositionMs
                playerEngine.playTracks(
                    server = current.activeServer,
                    tracks = listOf(activeParentAudioTrack!!),
                    startIndex = 0,
                    startPositionMs = resumePos,
                )
                current.currentTrack?.let { playerEngine.updateTrack(0, it) }
            } else {
                playerEngine.playTracks(
                    server = current.activeServer,
                    tracks = current.queue.tracks,
                    startIndex = current.queue.currentIndex.coerceAtLeast(0),
                    startPositionMs = _playbackProgress.value.currentPositionMs,
                )
            }
        }
    }

    override fun pause() {
        playerEngine.pause()
        coroutineScope.launch {
            if (_isRestored.value) {
                val pos = playerEngine.currentPositionMs.value
                if (pos > 0L) {
                    if (virtualTimelineEngine.isActive) {
                        val startMs = virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L
                        val relPos = (pos - startMs).coerceAtLeast(0L)
                        sessionStore?.savePosition(pos, relPos)
                    } else {
                        sessionStore?.savePosition(pos)
                    }
                }
            }
            flushSession()
        }
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        if (virtualTimelineEngine.isActive) {
            val globalTargetMs =
                virtualTimelineEngine.calculateSeekTargetMs(
                    clamped,
                    playerEngine.durationMs.value,
                )
            _playbackProgress.update { it.copy(currentPositionMs = clamped) }
            if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                playerEngine.seekTo(globalTargetMs)
            }
            coroutineScope.launch {
                if (_isRestored.value) {
                    sessionStore?.savePosition(globalTargetMs, clamped)
                }
            }
        } else {
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
    }

    override fun skipToNext() {
        if (virtualTimelineEngine.isActive) {
            val seekResult = virtualTimelineEngine.getNextTrackSeekPosition(_sessionState.value.playbackMode)
            if (seekResult != null) {
                applyVirtualTrackSeek(seekResult)
            }
        } else {
            playerEngine.skipToNext()
        }
    }

    override fun skipToPrevious() {
        if (virtualTimelineEngine.isActive) {
            val seekResult =
                virtualTimelineEngine.getPreviousTrackSeekPosition(
                    globalPositionMs = playerEngine.currentPositionMs.value,
                    mode = _sessionState.value.playbackMode,
                )
            if (seekResult != null) {
                applyVirtualTrackSeek(seekResult)
            }
        } else {
            playerEngine.skipToPrevious()
        }
    }

    override fun playQueueIndex(index: Int) {
        if (virtualTimelineEngine.isActive) {
            val seekResult = virtualTimelineEngine.seekToTrackIndex(index)
            if (seekResult != null) {
                applyVirtualTrackSeek(seekResult)
            }
        } else {
            val queue = _sessionState.value.queue
            if (index in queue.tracks.indices) {
                playerEngine.seekToTrack(index)
            }
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
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null
        playerEngine.stop()
        _playbackProgress.value = PlaybackProgress.ZERO
        coroutineScope.launch { flushSession() }
    }

    override fun release() {
        stopPeriodicFlush()
        playerEngine.setSkipHandler(null)
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null
        internalJobs.forEach { it.cancel() }
        internalJobs.clear()
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
                            if (virtualTimelineEngine.isActive) {
                                val startMs = virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L
                                val relPos = (pos - startMs).coerceAtLeast(0L)
                                sessionStore.savePosition(pos, relPos)
                            } else {
                                sessionStore.savePosition(pos)
                            }
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

            // If CUE virtual track session is saved, attempt to reconstruct virtual queue
            if (savedSession.cuePath != null && savedSession.queueTracks.isNotEmpty()) {
                val restoredVirtual = restoreVirtualTrackSession(server, savedSession)
                if (restoredVirtual) {
                    playerEngine.setPlaybackMode(savedSession.playbackMode)
                    return
                }
                // Fall back cleanly to ordinary playback
            }

            restoreOrdinarySession(server, savedSession)
            playerEngine.setPlaybackMode(savedSession.playbackMode)
        } finally {
            _isRestored.value = true
        }
    }

    private suspend fun restoreVirtualTrackSession(
        server: WebDavServer?,
        savedSession: PlaybackSessionData,
    ): Boolean {
        val cuePath = savedSession.cuePath ?: return false
        val parentTrack = savedSession.queueTracks.firstOrNull() ?: return false
        val normalizedParentTrack = parentTrack.copy(id = parentTrack.id.substringBefore("#cue_"))

        // 1. Check in-memory CUE text cache
        var cueText = CueTextCache.get(savedSession.activeServerId, cuePath)

        // 2. Fetch from WebDavClient if not cached
        if (cueText.isNullOrBlank() && server != null && webDavClient != null) {
            cueText =
                try {
                    webDavClient.fetchText(server, cuePath)
                } catch (e: Exception) {
                    null
                }
            if (!cueText.isNullOrBlank()) {
                CueTextCache.put(server.id, cuePath, cueText)
            }
        }

        // 3. Fallback if CUE text is unavailable (deleted / network fail)
        if (cueText.isNullOrBlank()) {
            return false
        }

        // 4. Parse CUE into virtual tracks
        val virtualTracks =
            try {
                CueParser.parse(
                    content = cueText,
                    parentAudioPath = normalizedParentTrack.remotePath,
                    totalDurationMs = normalizedParentTrack.durationMs,
                )
            } catch (e: Exception) {
                emptyList()
            }

        // If CUE parsing failed or returned no tracks, fallback safely
        if (virtualTracks.isEmpty()) {
            return false
        }

        // 5. Identify the target virtual track index
        val targetIndex =
            if (savedSession.virtualTrackNumber != null) {
                virtualTracks
                    .indexOfFirst { it.trackNumber == savedSession.virtualTrackNumber }
                    .takeIf { it >= 0 } ?: 0
            } else {
                savedSession.currentTrackIndex.coerceIn(0, virtualTracks.lastIndex)
            }

        val targetVirtualTrack = virtualTracks[targetIndex]

        val mappedTracks =
            virtualTracks.map { vt ->
                AudioTrack(
                    id = "${normalizedParentTrack.id}#cue_${vt.trackNumber}",
                    serverId = normalizedParentTrack.serverId,
                    remotePath = normalizedParentTrack.remotePath,
                    title = vt.title,
                    artist = vt.performer?.takeIf { it.isNotBlank() } ?: normalizedParentTrack.artist,
                    album = normalizedParentTrack.album,
                    durationMs = vt.durationMs,
                    size = normalizedParentTrack.size,
                    format = normalizedParentTrack.format,
                    coverThumbnailPath = normalizedParentTrack.coverThumbnailPath,
                )
            }

        val targetTrack = mappedTracks[targetIndex]

        virtualTimelineEngine.loadTracks(virtualTracks, targetIndex)
        activeParentAudioTrack = normalizedParentTrack
        activeCuePath = cuePath

        val relPositionMs =
            (savedSession.virtualPositionMs
                ?: (savedSession.positionMs - targetVirtualTrack.startTimeMs).coerceAtLeast(0L))
                .coerceIn(0L, if (targetVirtualTrack.durationMs > 0L) targetVirtualTrack.durationMs else Long.MAX_VALUE)

        val queue = PlaybackQueue(tracks = mappedTracks, currentIndex = targetIndex)

        _sessionState.update {
            it.copy(
                activeServer = server ?: it.activeServer,
                queue = queue,
                playbackState = PlaybackState.Paused,
                playbackMode = savedSession.playbackMode,
                durationMs = targetVirtualTrack.durationMs,
                currentDirectoryPath = savedSession.currentDirectoryPath,
                errorMessage = null,
            )
        }

        _playbackProgress.value =
            PlaybackProgress(
                currentPositionMs = relPositionMs,
                durationMs = targetVirtualTrack.durationMs,
                bufferedPositionMs = 0L,
            )

        playerEngine.updateTrack(0, targetTrack)
        return true
    }

    private suspend fun restoreOrdinarySession(
        server: WebDavServer?,
        savedSession: PlaybackSessionData,
    ) {
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null

        val restoredQueue =
            PlaybackQueue(
                tracks = savedSession.queueTracks,
                currentIndex = savedSession.currentTrackIndex.coerceIn(-1, savedSession.queueTracks.lastIndex),
            )
        val restoredTrack = restoredQueue.currentTrack
        val restoredPlaybackState = if (restoredTrack != null) PlaybackState.Paused else PlaybackState.Idle

        val finalQueue =
            if (server != null) {
                val updatedTracks =
                    savedSession.queueTracks.map { track ->
                        val cachedMeta = trackMetadataRepository?.getCachedMetadata(server.id, track.remotePath)
                        if (cachedMeta != null) track.withMetadata(cachedMeta) else track
                    }
                PlaybackQueue(
                    tracks = updatedTracks,
                    currentIndex = savedSession.currentTrackIndex.coerceIn(-1, updatedTracks.lastIndex),
                )
            } else {
                restoredQueue
            }
        val durationMs = finalQueue.currentTrack?.durationMs ?: restoredTrack?.durationMs ?: 0L

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

        val isVirtual = virtualTimelineEngine.isActive
        val activeVirtualTrack = virtualTimelineEngine.activeTrack

        // Query live engine position if active
        val livePositionMs =
            if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                val enginePos = playerEngine.currentPositionMs.value
                if (enginePos > 0L) {
                    enginePos
                } else if (isVirtual) {
                    (activeVirtualTrack?.startTimeMs ?: 0L) + _playbackProgress.value.currentPositionMs
                } else {
                    _playbackProgress.value.currentPositionMs
                }
            } else if (isVirtual) {
                (activeVirtualTrack?.startTimeMs ?: 0L) + _playbackProgress.value.currentPositionMs
            } else {
                _playbackProgress.value.currentPositionMs
            }

        val virtualPosMs =
            if (isVirtual) {
                if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                    val enginePos = playerEngine.currentPositionMs.value
                    val startMs = activeVirtualTrack?.startTimeMs ?: 0L
                    if (enginePos >= startMs) {
                        (enginePos - startMs)
                    } else {
                        _playbackProgress.value.currentPositionMs
                    }
                } else {
                    _playbackProgress.value.currentPositionMs
                }
            } else {
                null
            }

        val cuePath = if (isVirtual) activeCuePath else null
        val virtualTrackNumber = if (isVirtual) activeVirtualTrack?.trackNumber else null

        val queueTracksToSave =
            if (isVirtual && activeParentAudioTrack != null) {
                listOf(activeParentAudioTrack!!)
            } else if (isVirtual && current.queue.currentTrack != null) {
                val ct = current.queue.currentTrack!!
                listOf(ct.copy(id = ct.id.substringBefore("#cue_"), title = ct.fileName))
            } else {
                current.queue.tracks
            }

        val currentTrackIndexToSave = if (isVirtual) 0 else current.queue.currentIndex

        val sessionData =
            PlaybackSessionData(
                activeServerId = current.activeServer?.id,
                currentDirectoryPath = current.currentDirectoryPath,
                queueTracks = queueTracksToSave,
                currentTrackIndex = currentTrackIndexToSave,
                positionMs = livePositionMs,
                playbackMode = current.playbackMode,
                serverLastDirectories = serverLastDirectories.toMap(),
                cuePath = cuePath,
                virtualTrackNumber = virtualTrackNumber,
                virtualPositionMs = virtualPosMs,
            )
        store.saveSession(sessionData)
    }
}
