package com.webdav.player.domain.session

import androidx.annotation.VisibleForTesting
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.LyricsRepository
import com.webdav.player.domain.repository.PlaybackSessionStore
import com.webdav.player.domain.repository.PlaylistRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Domain playback session orchestrator managing queues, streaming resilience, and error state.
 */
class MusicPlayerAppSessionImpl(
    private val playerEngine: AudioPlayerEngine,
    private val serverRepository: ServerRepository? = null,
    trackMetadataRepository: TrackMetadataRepository? = null,
    lyricsRepository: LyricsRepository? = null,
    sessionStore: PlaybackSessionStore? = null,
    private val cueSheetResolver: CueSheetResolver? = null,
    private val playlistRepository: PlaylistRepository? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val progressDispatcher: CoroutineDispatcher =
        (coroutineScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as? CoroutineDispatcher)?.takeIf {
            it !is kotlinx.coroutines.MainCoroutineDispatcher
        } ?: Dispatchers.Default,
    periodicDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MusicPlayerAppSession {
    private val _sessionState = MutableStateFlow(PlayerSessionState())
    override val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(PlaybackProgress.ZERO)
    override val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private val virtualTimelineEngine = VirtualTimelineEngine()
    private var activeParentAudioTrack: AudioTrack? = null
    private var activeCuePath: String? = null
    private var hasStreamLoaded = false
    private val internalJobs = CopyOnWriteArrayList<Job>()

    private val persistenceCoordinator = SessionPersistenceCoordinator(
        sessionStore = sessionStore, serverRepository = serverRepository,
        trackMetadataRepository = trackMetadataRepository, cueSheetResolver = cueSheetResolver,
        playerEngine = playerEngine, virtualTimelineEngine = virtualTimelineEngine,
        sessionStateFlow = _sessionState, playbackProgressFlow = _playbackProgress,
        coroutineScope = coroutineScope, periodicDispatcher = periodicDispatcher,
        getActiveParentAudioTrack = { activeParentAudioTrack }, getActiveCuePath = { activeCuePath },
        onCueSessionRestored = { p, c -> activeParentAudioTrack = p; activeCuePath = c },
        onOrdinarySessionRestored = { activeParentAudioTrack = null; activeCuePath = null },
    )
    override val isRestored: StateFlow<Boolean> = persistenceCoordinator.isRestored

    private val queueManager = PlaybackQueueManager(_sessionState, serverRepository, cueSheetResolver) { coroutineScope.launch { flushSession() } }
    private val faultTolerance = PlaybackFaultTolerance(_sessionState, playerEngine, playlistRepository, queueManager, coroutineScope)
    private val metadataCoordinator = PlaybackMetadataCoordinator(trackMetadataRepository, lyricsRepository, playerEngine, _sessionState, coroutineScope)

    private val _pausedByTransientAudioFocus = AtomicBoolean(false)
    override val pausedByTransientAudioFocus: Boolean get() = _pausedByTransientAudioFocus.get()

    @VisibleForTesting val lastBufferingTimestampMs: AtomicLong get() = faultTolerance.lastBufferingTimestampMs
    @VisibleForTesting val isPrerollAppended = AtomicBoolean(false)

    private fun clearTransientAudioFocusFlag() = _pausedByTransientAudioFocus.set(false)
    private fun clearPrerollIfActive() { if (isPrerollAppended.compareAndSet(true, false)) playerEngine.clearPrerollTrack() }

    private fun resetPlaybackState() {
        clearPrerollIfActive(); stopPeriodicFlush()
        virtualTimelineEngine.clear(); activeParentAudioTrack = null; activeCuePath = null; hasStreamLoaded = false
    }

    private fun loadTrackIntoEngine(server: WebDavServer?, track: AudioTrack, startPositionMs: Long = 0L) {
        clearKilledBySystemFlag(); clearTransientAudioFocusFlag()
        fun dispatch(target: WebDavServer) {
            _sessionState.update { it.copy(activeServer = target) }
            hasStreamLoaded = true
            try { playerEngine.loadTrack(target, track, startPositionMs) }
            catch (e: Throwable) { faultTolerance.handleLoadException(e, track) { skipToNext() }; return }
            coroutineScope.launch { flushSessionWithCleanExit(isCleanExit = false) }
        }
        when {
            server != null && server.id == track.serverId -> dispatch(server)
            serverRepository != null -> coroutineScope.launch {
                serverRepository.getServerById(track.serverId)?.let { dispatch(it) } ?: faultTolerance.handleServerUnavailable(track) { skipToNext() }
            }
            server != null -> dispatch(server)
            else -> coroutineScope.launch { faultTolerance.handleServerUnavailable(track) { skipToNext() } }
        }
    }

    private fun applyCueContextAndGetStartMs(vt: List<VirtualTrack>?, parent: AudioTrack?, cue: String?, fallbackStartMs: Long = 0L, targetIndex: Int = 0): Long =
        if (vt != null) {
            activeParentAudioTrack = parent; activeCuePath = cue
            virtualTimelineEngine.loadTracks(vt, targetIndex)
        } else {
            virtualTimelineEngine.clear(); activeParentAudioTrack = null; activeCuePath = null
            fallbackStartMs
        }

    private fun dispatchQueueLoad(server: WebDavServer, request: QueueLoadRequest, directoryPath: String? = null) {
        clearKilledBySystemFlag(); clearTransientAudioFocusFlag(); faultTolerance.cancelWatchdog()
        persistenceCoordinator.markRestored(); queueManager.clearFailedTracks()
        val startMs = applyCueContextAndGetStartMs(request.virtualTracks, request.parentTrack, request.cuePath, request.startPositionMs, request.queue.currentIndex)
        _playbackProgress.value = PlaybackProgress(0L, request.targetTrack.durationMs, 0L)
        _sessionState.update {
            it.copy(queue = request.queue, durationMs = request.targetTrack.durationMs, currentDirectoryPath = directoryPath ?: it.currentDirectoryPath, errorMessage = null)
        }
        loadTrackIntoEngine(server, request.parentTrack ?: request.targetTrack, startMs)
        if (request.targetTrack.isVirtualTrack) playerEngine.updateTrackMetadata(request.targetTrack)
        coroutineScope.launch { flushSession() }
    }

    private fun transitionToQueueIndex(server: WebDavServer?, targetIndex: Int) {
        faultTolerance.cancelWatchdog()
        val queue = _sessionState.value.queue
        val targetTrack = queue.tracks.getOrNull(targetIndex) ?: return
        _playbackProgress.value = PlaybackProgress(0L, targetTrack.durationMs, 0L)
        _sessionState.update {
            it.copy(queue = queue.copy(currentIndex = targetIndex), durationMs = targetTrack.durationMs, isUnsupportedRange = false, isWeakNetwork = false, canManualRetry = false, isReconnecting = false)
        }
        coroutineScope.launch {
            val (vt, startMs) = queueManager.resolveVirtualTrackOffset(targetTrack, server)
            val parent = if (vt != null) targetTrack.copy(id = targetTrack.id.substringBefore("#cue_")) else null
            val cue = if (vt != null) targetTrack.cuePath ?: (targetTrack.remotePath.substringBeforeLast('.') + ".cue") else null
            val finalStartMs = applyCueContextAndGetStartMs(vt?.let { listOf(it) }, parent, cue, startMs)
            loadTrackIntoEngine(server, targetTrack, finalStartMs)
            if (targetTrack.isVirtualTrack) playerEngine.updateTrackMetadata(targetTrack)
            flushSession()
        }
    }

    init {
        startSessionAndServerObservation()
        startPlaybackStateObservation()
        startTrackTransitionsObservation()
        startProgressPipeline()
        startDurationObservation()
        metadataCoordinator.startMetadataObservation()
        metadataCoordinator.startLyricsObservation()
    }

    private fun startSessionAndServerObservation() {
        internalJobs += coroutineScope.launch {
            try { restoreSession() } finally { persistenceCoordinator.markRestored() }
            if (serverRepository != null) {
                try {
                    serverRepository.getActiveServer().collect { server ->
                        val current = _sessionState.value
                        if (current.activeServer?.id != server?.id && !current.queue.isPlaylistQueue) setActiveServer(server)
                    }
                } catch (e: Throwable) { /* ignore during teardown */ }
            }
        }
    }

    private fun startPlaybackStateObservation() {
        internalJobs += coroutineScope.launch {
            playerEngine.playbackState.collect { state ->
                when (state) {
                    is PlaybackState.Ended -> { handlePlaybackEnded(); return@collect }
                    is PlaybackState.Buffering -> { clearPrerollIfActive(); faultTolerance.onBufferingStarted() }
                    is PlaybackState.Playing -> { faultTolerance.onPlayingStarted(); startPeriodicFlush() }
                    is PlaybackState.Error -> {
                        val handled = faultTolerance.handlePlaybackError(
                            state, _sessionState.value.currentTrack,
                            { playerEngine.pause(); stopPeriodicFlush() }, { skipToNext() }
                        )
                        if (handled) return@collect
                    }
                    else -> { faultTolerance.cancelWatchdog(); stopPeriodicFlush() }
                }
                _sessionState.update { current ->
                    val target = if (state is PlaybackState.Idle && current.isPaused && current.hasTrack && !hasStreamLoaded) current.playbackState else state
                    current.copy(playbackState = target, errorMessage = if (state is PlaybackState.Error) state.message else current.errorMessage)
                }
            }
        }
    }

    private fun startTrackTransitionsObservation() {
        internalJobs += coroutineScope.launch {
            playerEngine.trackTransitions.collect {
                isPrerollAppended.set(false)
                val queue = _sessionState.value.queue
                if (!queue.isEmpty) {
                    val nextIndex = calculateNextQueueIndex(queue, _sessionState.value.playbackMode)
                    val nextTrack = queue.tracks[nextIndex]
                    _playbackProgress.value = PlaybackProgress(0L, nextTrack.durationMs, 0L)
                    _sessionState.update { it.copy(queue = queue.copy(currentIndex = nextIndex), durationMs = nextTrack.durationMs) }
                    flushSession()
                }
            }
        }
    }

    private fun mapEngineProgress(pos: Long, dur: Long, buf: Long): PlaybackProgress {
        if (!virtualTimelineEngine.isActive) return PlaybackProgress(pos, if (dur > 0L) dur else (_sessionState.value.currentTrack?.durationMs ?: 0L), buf)
        if (playerEngine.playbackState.value is PlaybackState.Idle) return _playbackProgress.value
        if (_sessionState.value.queue.isPlaylistQueue) {
            val vt = virtualTimelineEngine.activeTrack
            if (vt?.endTimeMs != null && pos >= vt.endTimeMs) handlePlaybackEnded()
        } else {
            (virtualTimelineEngine.onPositionUpdate(pos) as? VirtualTimelineEngine.TransitionResult.Transitioned)?.let {
                handleVirtualTrackTransition(it.newIndex, it.newTrack)
            }
        }
        return virtualTimelineEngine.mapToVirtualProgress(pos, dur, buf)
    }

    private fun startProgressPipeline() {
        internalJobs += coroutineScope.launch(progressDispatcher) {
            combine(playerEngine.currentPositionMs, playerEngine.durationMs, playerEngine.bufferedPositionMs) { pos, dur, buf ->
                mapEngineProgress(pos, dur, buf)
            }.distinctUntilChanged().collect { progress ->
                if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                    _playbackProgress.value = progress
                    checkAndTriggerAdaptivePreroll(progress)
                }
            }
        }
    }

    private fun startDurationObservation() {
        internalJobs += coroutineScope.launch {
            playerEngine.durationMs.collect { dur ->
                if (playerEngine.playbackState.value !is PlaybackState.Idle && dur > 0L && !virtualTimelineEngine.isActive) {
                    _sessionState.update { current ->
                        val curIdx = current.queue.currentIndex
                        val updated = current.queue.tracks.mapIndexed { idx, trk -> if (idx == curIdx && trk.durationMs != dur) trk.copy(durationMs = dur) else trk }
                        current.copy(durationMs = dur, queue = current.queue.copy(tracks = updated))
                    }
                }
            }
        }
    }

    override fun getLastDirectoryForServer(serverId: Long): String = persistenceCoordinator.getLastDirectoryForServer(serverId)

    override fun setActiveServer(server: WebDavServer?) {
        _sessionState.update { current ->
            if (current.activeServer?.id == server?.id) return@update current.copy(activeServer = server)
            if (current.activeServer == null && current.hasTrack) {
                val path = current.currentDirectoryPath.takeIf { it.isNotBlank() && it != "/" }
                    ?: if (server != null) getLastDirectoryForServer(server.id) else "/"
                return@update current.copy(activeServer = server, currentDirectoryPath = path)
            }
            if (current.queue.isPlaylistQueue) return@update current.copy(activeServer = server)

            if (current.playbackState !is PlaybackState.Idle) playerEngine.stop()
            resetPlaybackState()
            _playbackProgress.value = PlaybackProgress.ZERO
            val lastPath = if (server != null) getLastDirectoryForServer(server.id) else "/"
            current.copy(activeServer = server, queue = PlaybackQueue.EMPTY, playbackState = PlaybackState.Idle, durationMs = 0L, currentDirectoryPath = lastPath, errorMessage = null, lyrics = null, isLoadingLyrics = false)
        }
    }

    override fun playDirectoryTrack(
        directory: RemoteDirectory, selectedFile: RemoteFile,
        initialMetadata: Map<String, TrackMetadata>,
        virtualTracksByAudioPath: Map<String, List<VirtualTrack>>,
    ) {
        val server = _sessionState.value.activeServer ?: return
        val request = queueManager.buildDirectoryQueue(directory, selectedFile, initialMetadata, virtualTracksByAudioPath, server) ?: return
        dispatchQueueLoad(server, request, directory.path)
        metadataCoordinator.enrichQueueTracksFromCache(server, request.queue)
    }

    override fun playTrack(track: AudioTrack) {
        val server = _sessionState.value.activeServer
        if (server == null && serverRepository == null) return
        coroutineScope.launch {
            val request = queueManager.buildTrackQueue(track, server)
            val effectiveServer = server ?: serverRepository?.getServerById(track.serverId) ?: return@launch
            dispatchQueueLoad(effectiveServer, request)
            metadataCoordinator.enrichSingleTrackMetadata(effectiveServer, track)
        }
    }

    override fun playVirtualTracks(parentTrack: AudioTrack, virtualTracks: List<VirtualTrack>, startIndex: Int, cuePath: String?) {
        clearKilledBySystemFlag(); clearTransientAudioFocusFlag()
        val server = _sessionState.value.activeServer ?: return
        if (virtualTracks.isEmpty()) return
        val cleanParent = parentTrack.copy(id = parentTrack.id.substringBefore("#cue_"))
        val validIndex = startIndex.coerceIn(0, virtualTracks.lastIndex)

        if (virtualTimelineEngine.isActive && activeParentAudioTrack?.serverId == cleanParent.serverId && activeParentAudioTrack?.remotePath == cleanParent.remotePath && playerEngine.playbackState.value !is PlaybackState.Idle) {
            virtualTimelineEngine.seekToTrackIndex(validIndex)?.let {
                applyVirtualTrackSeek(it); if (!_sessionState.value.isPlaying) playerEngine.play(); return
            }
        }
        val request = queueManager.buildVirtualTracksQueue(parentTrack, virtualTracks, validIndex, cuePath, _sessionState.value.playbackMode)
        dispatchQueueLoad(server, request)
    }

    private fun handleVirtualTrackTransition(newIndex: Int, newTrack: VirtualTrack) {
        _sessionState.update { current ->
            if (newIndex in current.queue.tracks.indices) current.copy(queue = current.queue.copy(currentIndex = newIndex), durationMs = newTrack.durationMs) else current
        }
        _sessionState.value.queue.currentTrack?.let { track ->
            coroutineScope.launch(Dispatchers.Main) { playerEngine.updateTrackMetadata(track); flushSession() }
        }
    }

    private fun applyVirtualTrackSeek(seekResult: TrackSeekResult) {
        faultTolerance.cancelWatchdog()
        _sessionState.update { current ->
            if (seekResult.trackIndex in current.queue.tracks.indices) {
                current.copy(queue = current.queue.copy(currentIndex = seekResult.trackIndex), durationMs = seekResult.track.durationMs, isUnsupportedRange = false, isWeakNetwork = false, canManualRetry = false, isReconnecting = false)
            } else current
        }
        _sessionState.value.queue.currentTrack?.let { playerEngine.updateTrackMetadata(it) }
        _playbackProgress.value = PlaybackProgress(0L, seekResult.track.durationMs, 0L)
        playerEngine.seekTo(seekResult.targetPositionMs)
        coroutineScope.launch { flushSession() }
    }

    override fun playNext(track: AudioTrack) =
        queueManager.playNext(track, _sessionState.value.activeServer != null || serverRepository != null) { playTrack(it) }

    override fun playNextAndAdvance(track: AudioTrack) {
        queueManager.playNextAndAdvance(
            track = track,
            hasServerOrRepo = (_sessionState.value.activeServer != null || serverRepository != null),
            onPlayTrack = { playTrack(it) },
            onTransitionToIndex = { targetIndex -> transitionToQueueIndex(_sessionState.value.activeServer, targetIndex) },
        )
    }

    private fun resumeCurrentTrack() {
        val current = _sessionState.value
        val server = current.activeServer ?: return
        val currentTrack = current.currentTrack ?: return
        if (virtualTimelineEngine.isActive && activeParentAudioTrack != null) {
            val resumePos = (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L) + _playbackProgress.value.currentPositionMs
            loadTrackIntoEngine(server, activeParentAudioTrack!!, resumePos)
            playerEngine.updateTrackMetadata(currentTrack)
        } else {
            loadTrackIntoEngine(server, currentTrack, _playbackProgress.value.currentPositionMs)
        }
    }

    override fun togglePlayPause() {
        if (_sessionState.value.isPlaying || _sessionState.value.isBuffering) pause() else play()
    }

    override fun play() {
        clearKilledBySystemFlag(); clearTransientAudioFocusFlag()
        val current = _sessionState.value
        if (current.isPaused) {
            if ((!hasStreamLoaded || playerEngine.playbackState.value is PlaybackState.Idle) && current.currentTrack != null && current.activeServer != null) {
                resumeCurrentTrack()
            } else playerEngine.play()
        } else if (current.currentTrack != null && current.activeServer != null) {
            resumeCurrentTrack()
        }
    }

    override fun pause() {
        clearTransientAudioFocusFlag(); pauseInternal()
    }

    private fun pauseInternal() {
        clearPrerollIfActive(); faultTolerance.cancelWatchdog()
        if (hasStreamLoaded) playerEngine.pause()
        coroutineScope.launch {
            val pos = playerEngine.currentPositionMs.value
            if (pos > 0L) {
                val virtPos = if (virtualTimelineEngine.isActive) (pos - (virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L)).coerceAtLeast(0L) else null
                persistenceCoordinator.savePosition(pos, virtPos, isCleanExit = true)
            }
            flushSessionWithCleanExit(isCleanExit = true)
        }
    }

    override fun onAudioFocusTransientLoss() {
        if (_sessionState.value.isPlaying || _sessionState.value.isBuffering) {
            _pausedByTransientAudioFocus.set(true); pauseInternal()
        } else _pausedByTransientAudioFocus.set(false)
    }

    override fun onAudioFocusGain() {
        if (_pausedByTransientAudioFocus.compareAndSet(true, false)) play()
    }

    override fun onAudioFocusLoss() {
        _pausedByTransientAudioFocus.set(false); pauseInternal()
    }

    override fun seekTo(positionMs: Long) {
        clearPrerollIfActive()
        val clamped = positionMs.coerceAtLeast(0L)
        val targetMs = if (virtualTimelineEngine.isActive) virtualTimelineEngine.calculateSeekTargetMs(clamped, playerEngine.durationMs.value) else clamped
        val virtPos = if (virtualTimelineEngine.isActive) clamped else null
        _playbackProgress.update { it.copy(currentPositionMs = clamped) }
        if (playerEngine.playbackState.value !is PlaybackState.Idle) playerEngine.seekTo(targetMs)
        persistenceCoordinator.savePosition(targetMs, virtPos)
    }

    override fun playPlaylist(playlist: Playlist, startIndex: Int) {
        coroutineScope.launch {
            val entries = playlistRepository?.getTracksForPlaylist(playlist.id) ?: emptyList()
            val request = queueManager.buildPlaylistQueue(entries, startIndex, _sessionState.value.activeServer)
            if (request == null) {
                _sessionState.update { it.copy(queue = PlaybackQueue.EMPTY, errorMessage = null) }
                return@launch
            }
            dispatchQueueLoad(_sessionState.value.activeServer ?: return@launch, request)
        }
    }

    override fun skipToNext() {
        clearTransientAudioFocusFlag(); clearPrerollIfActive()
        if (virtualTimelineEngine.isActive && !_sessionState.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.getNextTrackSeekPosition(_sessionState.value.playbackMode)?.let { applyVirtualTrackSeek(it) }
            return
        }
        val queue = _sessionState.value.queue
        if (!queue.isEmpty) transitionToQueueIndex(_sessionState.value.activeServer, queueManager.calculateNextQueueIndex(queue, _sessionState.value.playbackMode))
    }

    override fun skipToPrevious() {
        clearTransientAudioFocusFlag(); clearPrerollIfActive()
        if (virtualTimelineEngine.isActive && !_sessionState.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.getPreviousTrackSeekPosition(playerEngine.currentPositionMs.value, _sessionState.value.playbackMode)?.let { applyVirtualTrackSeek(it) }
            return
        }
        val queue = _sessionState.value.queue
        if (queue.isEmpty) return
        if (playerEngine.currentPositionMs.value > 3000L) { seekTo(0L); return }
        transitionToQueueIndex(_sessionState.value.activeServer, queueManager.calculatePreviousQueueIndex(queue, _sessionState.value.playbackMode))
    }

    override fun playQueueIndex(index: Int) {
        clearTransientAudioFocusFlag(); clearPrerollIfActive()
        if (virtualTimelineEngine.isActive && !_sessionState.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.seekToTrackIndex(index)?.let { applyVirtualTrackSeek(it) }
            return
        }
        if (index in _sessionState.value.queue.tracks.indices) {
            queueManager.clearFailedTracks()
            transitionToQueueIndex(_sessionState.value.activeServer, index)
        }
    }

    override fun cyclePlaybackMode() = queueManager.cyclePlaybackMode()
    override fun setPlaybackMode(mode: PlaybackMode) = queueManager.setPlaybackMode(mode)

    override fun removeQueueTrack(index: Int) {
        clearPrerollIfActive()
        queueManager.removeQueueTrack(
            index = index,
            onQueueCleared = {
                hasStreamLoaded = false; playerEngine.stop(); _playbackProgress.value = PlaybackProgress.ZERO
            },
            onCurrentTrackChanged = { nextTrack ->
                _playbackProgress.value = PlaybackProgress(0L, nextTrack.durationMs, 0L)
                _sessionState.value.activeServer?.let { loadTrackIntoEngine(it, nextTrack, 0L) }
            },
        )
    }

    override fun retryPlayback() {
        faultTolerance.cancelWatchdog()
        _sessionState.update { it.copy(isWeakNetwork = false, canManualRetry = false, isReconnecting = true) }
        resumeCurrentTrack()
    }

    @VisibleForTesting
    fun setWeakNetworkState(isWeakNetwork: Boolean, canManualRetry: Boolean) = faultTolerance.setWeakNetworkState(isWeakNetwork, canManualRetry)

    override fun stop() {
        clearTransientAudioFocusFlag(); resetPlaybackState(); faultTolerance.cancelWatchdog(); playerEngine.stop()
        _playbackProgress.value = PlaybackProgress.ZERO
        _sessionState.update { it.copy(playbackState = PlaybackState.Idle, isWeakNetwork = false, canManualRetry = false, isReconnecting = false) }
        coroutineScope.launch { flushSessionWithCleanExit(isCleanExit = true) }
    }

    override fun clearKilledBySystemFlag() {
        if (_sessionState.value.wasKilledBySystem) _sessionState.update { it.copy(wasKilledBySystem = false) }
    }

    override fun confirmUnsupportedRangeReplay() {
        faultTolerance.confirmUnsupportedRangeReplay onReplay@{
            val server = _sessionState.value.activeServer ?: return@onReplay
            val track = _sessionState.value.currentTrack ?: return@onReplay
            _playbackProgress.value = PlaybackProgress(0L, track.durationMs, 0L)
            val isVirtual = virtualTimelineEngine.isActive && activeParentAudioTrack != null
            val startPos = if (isVirtual) virtualTimelineEngine.activeTrack?.startTimeMs ?: 0L else 0L
            loadTrackIntoEngine(server, if (isVirtual) activeParentAudioTrack!! else track, startPos)
            if (isVirtual) playerEngine.updateTrackMetadata(track)
        }
    }

    override fun dismissUnsupportedRange() = faultTolerance.dismissUnsupportedRange()

    override fun release() {
        resetPlaybackState()
        internalJobs.forEach { it.cancel() }; internalJobs.clear(); playerEngine.release()
        _playbackProgress.value = PlaybackProgress.ZERO
    }

    override fun flushSessionAsync() = persistenceCoordinator.flushSessionAsync()
    private fun startPeriodicFlush() = persistenceCoordinator.startPeriodicFlush()
    private fun stopPeriodicFlush() = persistenceCoordinator.stopPeriodicFlush()
    override fun setCurrentDirectoryPath(path: String) = persistenceCoordinator.setCurrentDirectoryPath(path)
    override suspend fun restoreSession() = persistenceCoordinator.restoreSession()
    override suspend fun flushSession() = persistenceCoordinator.flushSession()
    suspend fun flushSessionWithCleanExit(isCleanExit: Boolean) = persistenceCoordinator.flushSessionWithCleanExit(isCleanExit)

    @VisibleForTesting
    fun setShuffleOrder(order: List<Int>) = queueManager.setShuffleOrder(order)

    private fun calculateNextQueueIndex(queue: PlaybackQueue, mode: PlaybackMode): Int = queueManager.calculateNextQueueIndex(queue, mode)

    private fun checkAndTriggerAdaptivePreroll(progress: PlaybackProgress) {
        faultTolerance.checkAndTriggerAdaptivePreroll(
            progress = progress, queue = _sessionState.value.queue, playbackMode = _sessionState.value.playbackMode,
            activeServer = _sessionState.value.activeServer, currentTrackId = _sessionState.value.currentTrack?.id,
            isPrerollAppended = isPrerollAppended, serverRepository = serverRepository,
            onPreroll = { nextServer, nextTrack -> playerEngine.prerollTrack(nextServer, nextTrack) },
        )
    }

    private fun handlePlaybackEnded() {
        isPrerollAppended.set(false)
        val currentPos = playerEngine.currentPositionMs.value
        val currentTrack = _sessionState.value.currentTrack
        val duration = playerEngine.durationMs.value.takeIf { it > 0L } ?: currentTrack?.durationMs ?: _sessionState.value.durationMs

        if (faultTolerance.isPrematureEof(currentPos, duration)) {
            _sessionState.update { it.copy(playbackState = PlaybackState.Buffering) }
            playerEngine.seekTo(currentPos); playerEngine.play(); return
        }

        if (virtualTimelineEngine.isActive && !_sessionState.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.getNextTrackSeekPosition(_sessionState.value.playbackMode)?.let {
                applyVirtualTrackSeek(it); playerEngine.play()
            } ?: _sessionState.update { it.copy(playbackState = PlaybackState.Ended) }
            return
        }

        val queue = _sessionState.value.queue
        val server = _sessionState.value.activeServer ?: return run { _sessionState.update { it.copy(playbackState = PlaybackState.Ended) } }
        if (queue.isEmpty) return run { _sessionState.update { it.copy(playbackState = PlaybackState.Ended) } }

        if (_sessionState.value.playbackMode == PlaybackMode.SINGLE_LOOP) {
            _playbackProgress.value = PlaybackProgress(0L, duration, 0L)
            playerEngine.seekTo(0L); playerEngine.play()
            coroutineScope.launch { flushSession() }
        } else {
            transitionToQueueIndex(server, calculateNextQueueIndex(queue, _sessionState.value.playbackMode))
        }
    }
}
