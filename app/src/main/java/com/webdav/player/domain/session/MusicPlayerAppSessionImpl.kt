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
    private var hasStreamLoaded = false

    private fun loadTrackIntoEngine(
        server: WebDavServer,
        track: AudioTrack,
        startPositionMs: Long = 0L,
    ) {
        clearKilledBySystemFlag()
        hasStreamLoaded = true
        playerEngine.loadTrack(server, track, startPositionMs)
        coroutineScope.launch { flushSessionWithCleanExit(isCleanExit = false) }
    }

    private val bufferingWatchdogJobRef =
        java.util.concurrent.atomic
            .AtomicReference<Job?>(null)

    @androidx.annotation.VisibleForTesting
    val lastBufferingTimestampMs =
        java.util.concurrent.atomic
            .AtomicLong(0L)

    @androidx.annotation.VisibleForTesting
    val isPrerollAppended =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    private fun clearPrerollIfActive() {
        if (isPrerollAppended.compareAndSet(true, false)) {
            playerEngine.clearPrerollTrack()
        }
    }

    private fun transitionToQueueIndex(
        server: WebDavServer,
        targetIndex: Int,
    ) {
        val queue = _sessionState.value.queue
        val targetTrack = queue.tracks[targetIndex]
        val updatedQueue = queue.copy(currentIndex = targetIndex)
        _playbackProgress.value = PlaybackProgress(0L, targetTrack.durationMs, 0L)
        _sessionState.update {
            it.copy(
                queue = updatedQueue,
                durationMs = targetTrack.durationMs,
            )
        }

        loadTrackIntoEngine(
            server = server,
            track = targetTrack,
            startPositionMs = 0L,
        )
        coroutineScope.launch { flushSession() }
    }

    init {
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
                    if (state is PlaybackState.Ended) {
                        handlePlaybackEnded()
                        return@collect
                    }
                    if (state is PlaybackState.Buffering) {
                        lastBufferingTimestampMs.set(System.currentTimeMillis())
                        clearPrerollIfActive()
                        if (bufferingWatchdogJobRef.get()?.isActive != true) {
                            val job =
                                coroutineScope.launch {
                                    delay(15_000L)
                                    if (isActive) {
                                        _sessionState.update {
                                            it.copy(isWeakNetwork = true, canManualRetry = false, isReconnecting = true)
                                        }
                                        delay(15_000L)
                                        if (isActive) {
                                            _sessionState.update {
                                                it.copy(isWeakNetwork = true, canManualRetry = true, isReconnecting = true)
                                            }
                                        }
                                    }
                                }
                            bufferingWatchdogJobRef.set(job)
                        }
                    } else {
                        bufferingWatchdogJobRef.getAndSet(null)?.cancel()
                        if (_sessionState.value.isWeakNetwork || _sessionState.value.canManualRetry || _sessionState.value.isReconnecting) {
                            _sessionState.update {
                                it.copy(isWeakNetwork = false, canManualRetry = false, isReconnecting = false)
                            }
                        }
                    }
                    _sessionState.update { current ->
                        val targetState =
                            if (state is PlaybackState.Idle && current.isPaused && current.hasTrack && !hasStreamLoaded) {
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

        // Observe seamless preroll track transitions from engine
        internalJobs +=
            coroutineScope.launch {
                playerEngine.trackTransitions.collect {
                    isPrerollAppended.set(false)
                    val queue = _sessionState.value.queue
                    if (!queue.isEmpty) {
                        val nextIndex = calculateNextQueueIndex(queue, _sessionState.value.playbackMode)
                        val nextTrack = queue.tracks[nextIndex]
                        val updatedQueue = queue.copy(currentIndex = nextIndex)
                        _playbackProgress.value = PlaybackProgress(0L, nextTrack.durationMs, 0L)
                        _sessionState.update {
                            it.copy(
                                queue = updatedQueue,
                                durationMs = nextTrack.durationMs,
                            )
                        }
                        flushSession()
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
                            checkAndTriggerAdaptivePreroll(progress)
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
                                            val currentQueueIndex = _sessionState.value.queue.currentIndex
                                            val currentEnriched = tracksToUpdate.firstOrNull { it.first == currentQueueIndex }?.second
                                            if (currentEnriched != null) {
                                                playerEngine.updateTrackMetadata(currentEnriched)
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
                hasStreamLoaded = false
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
                        val cachedText =
                            com.webdav.player.data.cue.CueTextCache
                                .get(server.id, cueFile.path)
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

            loadTrackIntoEngine(
                server = server,
                track = parentTrack,
                startPositionMs = startPositionMs,
            )

            playerEngine.updateTrackMetadata(selectedTrack)
        } else {
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

            loadTrackIntoEngine(
                server = server,
                track = selectedTrack,
                startPositionMs = 0L,
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
                    val currentEnriched = tracksToUpdate.firstOrNull { it.first == queue.currentIndex }?.second
                    if (currentEnriched != null) {
                        playerEngine.updateTrackMetadata(currentEnriched)
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
        val server = _sessionState.value.activeServer ?: return
        val queue = PlaybackQueue(tracks = listOf(track), currentIndex = 0)
        _playbackProgress.value =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                bufferedPositionMs = 0L,
            )
        _sessionState.update { it.copy(queue = queue, errorMessage = null) }
        loadTrackIntoEngine(server = server, track = track, startPositionMs = 0L)

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
                var enrichedTrack: AudioTrack? = null

                _sessionState.update { current ->
                    var changed = false
                    val updated =
                        current.queue.tracks.map { t ->
                            if (t.id == track.id) {
                                val enriched = t.withMetadata(meta)
                                if (enriched != t) {
                                    changed = true
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

                if (enrichedTrack != null) {
                    playerEngine.updateTrackMetadata(enrichedTrack!!)
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
        val rawQueue = PlaybackQueue(tracks = mappedTracks, currentIndex = validStartIndex)
        val queue =
            if (_sessionState.value.playbackMode == PlaybackMode.SHUFFLE) {
                rawQueue.withGeneratedShuffleOrder(validStartIndex)
            } else {
                rawQueue
            }

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

        loadTrackIntoEngine(
            server = server,
            track = cleanParentTrack,
            startPositionMs = startPositionMs,
        )

        playerEngine.updateTrackMetadata(initialVirtualTrack)
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
        coroutineScope.launch(Dispatchers.Main) {
            playerEngine.updateTrackMetadata(targetTrack)
            flushSession()
        }
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
            playerEngine.updateTrackMetadata(targetTrack)
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
        if (_sessionState.value.activeServer == null) return
        val currentQueue = _sessionState.value.queue
        if (currentQueue.isEmpty) {
            playTrack(track)
            return
        }
        val updatedQueue = currentQueue.insertNext(track)
        _sessionState.update { it.copy(queue = updatedQueue) }
        coroutineScope.launch { flushSession() }
    }

    override fun togglePlayPause() {
        clearKilledBySystemFlag()
        val current = _sessionState.value
        when {
            current.isPlaying -> {
                clearPrerollIfActive()
                playerEngine.pause()
                coroutineScope.launch {
                    if (_isRestored.value) {
                        val pos = playerEngine.currentPositionMs.value
                        if (pos > 0L) {
                            if (virtualTimelineEngine.isActive) {
                                val startMs = virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L
                                val relPos = (pos - startMs).coerceAtLeast(0L)
                                sessionStore?.savePosition(pos, relPos, isCleanExit = true)
                            } else {
                                sessionStore?.savePosition(pos, null, isCleanExit = true)
                            }
                        }
                    }
                    flushSessionWithCleanExit(isCleanExit = true)
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
                        loadTrackIntoEngine(
                            server = current.activeServer,
                            track = activeParentAudioTrack!!,
                            startPositionMs = resumePos,
                        )
                        current.currentTrack?.let { playerEngine.updateTrackMetadata(it) }
                    } else {
                        loadTrackIntoEngine(
                            server = current.activeServer,
                            track = current.currentTrack!!,
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
                    loadTrackIntoEngine(
                        server = server,
                        track = activeParentAudioTrack!!,
                        startPositionMs = resumePos,
                    )
                    current.currentTrack?.let { playerEngine.updateTrackMetadata(it) }
                } else {
                    loadTrackIntoEngine(
                        server = server,
                        track = current.currentTrack!!,
                        startPositionMs = _playbackProgress.value.currentPositionMs,
                    )
                }
            }
        }
    }

    override fun play() {
        clearKilledBySystemFlag()
        val current = _sessionState.value
        if (current.isPaused) {
            if (playerEngine.playbackState.value is PlaybackState.Idle && current.currentTrack != null && current.activeServer != null) {
                if (virtualTimelineEngine.isActive && activeParentAudioTrack != null) {
                    val resumePos =
                        (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L) +
                            _playbackProgress.value.currentPositionMs
                    loadTrackIntoEngine(
                        server = current.activeServer,
                        track = activeParentAudioTrack!!,
                        startPositionMs = resumePos,
                    )
                    current.currentTrack?.let { playerEngine.updateTrackMetadata(it) }
                } else {
                    loadTrackIntoEngine(
                        server = current.activeServer,
                        track = current.currentTrack!!,
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
                loadTrackIntoEngine(
                    server = current.activeServer,
                    track = activeParentAudioTrack!!,
                    startPositionMs = resumePos,
                )
                current.currentTrack?.let { playerEngine.updateTrackMetadata(it) }
            } else {
                loadTrackIntoEngine(
                    server = current.activeServer,
                    track = current.currentTrack!!,
                    startPositionMs = _playbackProgress.value.currentPositionMs,
                )
            }
        }
    }

    override fun pause() {
        clearPrerollIfActive()
        playerEngine.pause()
        coroutineScope.launch {
            if (_isRestored.value) {
                val pos = playerEngine.currentPositionMs.value
                if (pos > 0L) {
                    if (virtualTimelineEngine.isActive) {
                        val startMs = virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L
                        val relPos = (pos - startMs).coerceAtLeast(0L)
                        sessionStore?.savePosition(pos, relPos, isCleanExit = true)
                    } else {
                        sessionStore?.savePosition(pos, null, isCleanExit = true)
                    }
                }
            }
            flushSessionWithCleanExit(isCleanExit = true)
        }
    }

    override fun seekTo(positionMs: Long) {
        clearPrerollIfActive()
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
        clearPrerollIfActive()
        if (virtualTimelineEngine.isActive) {
            val seekResult = virtualTimelineEngine.getNextTrackSeekPosition(_sessionState.value.playbackMode)
            if (seekResult != null) {
                applyVirtualTrackSeek(seekResult)
            }
            return
        }
        val server = _sessionState.value.activeServer ?: return
        val queue = _sessionState.value.queue
        if (queue.isEmpty) return

        val nextIndex = calculateNextQueueIndex(queue, _sessionState.value.playbackMode)
        transitionToQueueIndex(server, nextIndex)
    }

    override fun skipToPrevious() {
        clearPrerollIfActive()
        if (virtualTimelineEngine.isActive) {
            val seekResult =
                virtualTimelineEngine.getPreviousTrackSeekPosition(
                    globalPositionMs = playerEngine.currentPositionMs.value,
                    mode = _sessionState.value.playbackMode,
                )
            if (seekResult != null) {
                applyVirtualTrackSeek(seekResult)
            }
            return
        }
        val server = _sessionState.value.activeServer ?: return
        val queue = _sessionState.value.queue
        if (queue.isEmpty) return

        val currentPos = playerEngine.currentPositionMs.value
        if (currentPos > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = queue.getPreviousIndex(_sessionState.value.playbackMode) ?: 0
        transitionToQueueIndex(server, prevIndex)
    }

    override fun playQueueIndex(index: Int) {
        clearPrerollIfActive()
        if (virtualTimelineEngine.isActive) {
            val seekResult = virtualTimelineEngine.seekToTrackIndex(index)
            if (seekResult != null) {
                applyVirtualTrackSeek(seekResult)
            }
            return
        }
        val server = _sessionState.value.activeServer ?: return
        val queue = _sessionState.value.queue
        if (index !in queue.tracks.indices) return

        transitionToQueueIndex(server, index)
    }

    override fun cyclePlaybackMode() {
        val nextMode = _sessionState.value.playbackMode.next()
        setPlaybackMode(nextMode)
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        _sessionState.update { current ->
            val updatedQueue =
                if (mode == PlaybackMode.SHUFFLE && current.queue.shuffleOrder.isEmpty() && current.queue.tracks.isNotEmpty()) {
                    current.queue.withGeneratedShuffleOrder()
                } else {
                    current.queue
                }
            current.copy(playbackMode = mode, queue = updatedQueue)
        }
        coroutineScope.launch { flushSession() }
    }

    override fun removeQueueTrack(index: Int) {
        clearPrerollIfActive()
        val currentQueue = _sessionState.value.queue
        if (index !in currentQueue.tracks.indices) return

        val wasCurrentTrack = (index == currentQueue.currentIndex)
        val updatedQueue = currentQueue.removeTrackAt(index)

        if (updatedQueue.isEmpty) {
            hasStreamLoaded = false
            playerEngine.stop()
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
            val nextTrack = updatedQueue.currentTrack
            if (wasCurrentTrack && nextTrack != null) {
                _playbackProgress.value = PlaybackProgress(0L, nextTrack.durationMs, 0L)
                _sessionState.update {
                    it.copy(
                        queue = updatedQueue,
                        durationMs = nextTrack.durationMs,
                    )
                }
                val server = _sessionState.value.activeServer
                if (server != null) {
                    loadTrackIntoEngine(server, nextTrack, 0L)
                }
            } else {
                _sessionState.update {
                    it.copy(queue = updatedQueue)
                }
            }
        }
        coroutineScope.launch { flushSession() }
    }

    override fun retryPlayback() {
        val server = _sessionState.value.activeServer ?: return
        val currentTrack = _sessionState.value.currentTrack ?: return
        val currentPos = _playbackProgress.value.currentPositionMs

        if (virtualTimelineEngine.isActive && activeParentAudioTrack != null) {
            val resumePos =
                (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L) + currentPos
            loadTrackIntoEngine(
                server = server,
                track = activeParentAudioTrack!!,
                startPositionMs = resumePos,
            )
            playerEngine.updateTrackMetadata(currentTrack)
        } else {
            loadTrackIntoEngine(
                server = server,
                track = currentTrack,
                startPositionMs = currentPos,
            )
            playerEngine.updateTrackMetadata(currentTrack)
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setWeakNetworkState(
        isWeakNetwork: Boolean,
        canManualRetry: Boolean,
    ) {
        _sessionState.update {
            it.copy(isWeakNetwork = isWeakNetwork, canManualRetry = canManualRetry, isReconnecting = isWeakNetwork)
        }
    }

    override fun stop() {
        clearPrerollIfActive()
        stopPeriodicFlush()
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null
        hasStreamLoaded = false
        playerEngine.stop()
        _playbackProgress.value = PlaybackProgress.ZERO
        _sessionState.update { it.copy(playbackState = PlaybackState.Idle) }
        coroutineScope.launch { flushSessionWithCleanExit(isCleanExit = true) }
    }

    override fun clearKilledBySystemFlag() {
        if (_sessionState.value.wasKilledBySystem) {
            _sessionState.update { it.copy(wasKilledBySystem = false) }
        }
    }

    override fun release() {
        clearPrerollIfActive()
        stopPeriodicFlush()
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null
        hasStreamLoaded = false
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
                                sessionStore.savePosition(pos, relPos, isCleanExit = false)
                            } else {
                                sessionStore.savePosition(pos, null, isCleanExit = false)
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
                    return
                }
                // Fall back cleanly to ordinary playback
            }

            restoreOrdinarySession(server, savedSession)
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
            (
                savedSession.virtualPositionMs
                    ?: (savedSession.positionMs - targetVirtualTrack.startTimeMs).coerceAtLeast(0L)
            ).coerceIn(0L, if (targetVirtualTrack.durationMs > 0L) targetVirtualTrack.durationMs else Long.MAX_VALUE)

        val queue = PlaybackQueue(tracks = mappedTracks, currentIndex = targetIndex)

        val wasKilled = !savedSession.cleanExit && savedSession.currentTrackIndex >= 0 && savedSession.queueTracks.isNotEmpty()
        _sessionState.update {
            it.copy(
                activeServer = server ?: it.activeServer,
                queue = queue,
                playbackState = PlaybackState.Paused,
                playbackMode = savedSession.playbackMode,
                durationMs = targetVirtualTrack.durationMs,
                currentDirectoryPath = savedSession.currentDirectoryPath,
                errorMessage = null,
                wasKilledBySystem = wasKilled,
            )
        }

        _playbackProgress.value =
            PlaybackProgress(
                currentPositionMs = relPositionMs,
                durationMs = targetVirtualTrack.durationMs,
                bufferedPositionMs = 0L,
            )

        playerEngine.updateTrackMetadata(targetTrack)
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

        val wasKilled = !savedSession.cleanExit && savedSession.currentTrackIndex >= 0 && savedSession.queueTracks.isNotEmpty()
        _sessionState.update {
            it.copy(
                activeServer = server ?: it.activeServer,
                queue = finalQueue,
                playbackState = restoredPlaybackState,
                playbackMode = savedSession.playbackMode,
                durationMs = durationMs,
                currentDirectoryPath = savedSession.currentDirectoryPath,
                errorMessage = null,
                wasKilledBySystem = wasKilled,
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
        val isActivelyPlaying =
            playerEngine.playbackState.value is PlaybackState.Playing ||
                playerEngine.playbackState.value is PlaybackState.Buffering
        flushSessionWithCleanExit(isCleanExit = !isActivelyPlaying)
    }

    suspend fun flushSessionWithCleanExit(isCleanExit: Boolean) {
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
                cleanExit = isCleanExit,
            )
        store.saveSession(sessionData)
    }

    companion object {
        const val DURATION_COMPLETION_TOLERANCE_MS: Long = 5000L
        const val COMPLETION_PROGRESS_THRESHOLD: Double = 0.95
        const val PREROLL_TRIGGER_WINDOW_MS: Long = 10_000L
        const val HEALTHY_NETWORK_QUIET_PERIOD_MS: Long = 30_000L
    }

    @androidx.annotation.VisibleForTesting
    fun setShuffleOrder(order: List<Int>) {
        _sessionState.update { it.copy(queue = it.queue.copy(shuffleOrder = order)) }
    }

    private fun calculateNextQueueIndex(
        queue: PlaybackQueue,
        mode: PlaybackMode,
    ): Int {
        if (queue.tracks.size <= 1) return 0
        return queue.getNextIndex(mode) ?: 0
    }

    private fun checkAndTriggerAdaptivePreroll(progress: PlaybackProgress) {
        if (virtualTimelineEngine.isActive || isPrerollAppended.get()) return
        val queue = _sessionState.value.queue
        if (queue.tracks.size <= 1) return
        val duration = progress.durationMs
        val position = progress.currentPositionMs
        val remainingMs = duration - position

        if (remainingMs in 1..PREROLL_TRIGGER_WINDOW_MS) {
            val isPlaying = _sessionState.value.playbackState is PlaybackState.Playing
            val isHealthyNetwork = (System.currentTimeMillis() - lastBufferingTimestampMs.get()) >= HEALTHY_NETWORK_QUIET_PERIOD_MS

            if (isPlaying && isHealthyNetwork) {
                if (isPrerollAppended.compareAndSet(false, true)) {
                    val server =
                        _sessionState.value.activeServer ?: run {
                            isPrerollAppended.set(false)
                            return
                        }
                    val nextIndex = calculateNextQueueIndex(queue, _sessionState.value.playbackMode)
                    val nextTrack = queue.tracks[nextIndex]
                    val currentTrackId = _sessionState.value.currentTrack?.id
                    coroutineScope.launch(Dispatchers.Main) {
                        // Atomic double-check on Main thread before invoking playerEngine:
                        // Ensure pre-roll is still expected, playback is still actively playing, and current track hasn't changed.
                        if (isPrerollAppended.get() &&
                            _sessionState.value.playbackState is PlaybackState.Playing &&
                            _sessionState.value.currentTrack?.id == currentTrackId
                        ) {
                            playerEngine.prerollTrack(server, nextTrack)
                        } else {
                            isPrerollAppended.set(false)
                        }
                    }
                }
            }
        }
    }

    private fun handlePlaybackEnded() {
        isPrerollAppended.set(false)
        val currentPos = playerEngine.currentPositionMs.value
        val currentTrack = _sessionState.value.currentTrack
        val duration =
            playerEngine.durationMs.value.takeIf { it > 0L }
                ?: currentTrack?.durationMs
                ?: _sessionState.value.durationMs

        // Multi-tier completion arbitration:
        // Tier 1 (Physical Match): currentPos >= duration - DURATION_COMPLETION_TOLERANCE_MS
        val isPhysicalMatch = duration <= 0L || currentPos >= (duration - DURATION_COMPLETION_TOLERANCE_MS)
        // Tier 2 (Completion Progress Threshold): 95%+ of duration reached (e.g. metadata tag inflation)
        val isProgressMatch =
            duration > 0L && (currentPos.toDouble() / duration.toDouble()) >= COMPLETION_PROGRESS_THRESHOLD

        if (!isPhysicalMatch && !isProgressMatch) {
            // Tier 3 (Premature EOF): premature network cut-off detected.
            // Do NOT advance queue, preserve current track index, seek to cut-off position,
            // and transition to PlaybackState.Buffering to attempt in-place resumption.
            _sessionState.update { it.copy(playbackState = PlaybackState.Buffering) }
            playerEngine.seekTo(currentPos)
            playerEngine.play()
            return
        }

        // Genuine completion!
        if (virtualTimelineEngine.isActive) {
            val nextSeek = virtualTimelineEngine.getNextTrackSeekPosition(_sessionState.value.playbackMode)
            if (nextSeek != null) {
                applyVirtualTrackSeek(nextSeek)
                playerEngine.play()
            } else {
                _sessionState.update { it.copy(playbackState = PlaybackState.Ended) }
            }
            return
        }

        val queue = _sessionState.value.queue
        val server = _sessionState.value.activeServer
        if (server == null || queue.isEmpty) {
            _sessionState.update { it.copy(playbackState = PlaybackState.Ended) }
            return
        }

        when (_sessionState.value.playbackMode) {
            PlaybackMode.SINGLE_LOOP -> {
                _playbackProgress.value = PlaybackProgress(0L, duration, 0L)
                playerEngine.seekTo(0L)
                playerEngine.play()
                coroutineScope.launch { flushSession() }
            }

            PlaybackMode.LIST_LOOP, PlaybackMode.SHUFFLE -> {
                val nextIndex = calculateNextQueueIndex(queue, _sessionState.value.playbackMode)
                transitionToQueueIndex(server, nextIndex)
            }
        }
    }
}
