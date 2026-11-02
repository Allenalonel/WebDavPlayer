package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Internal collaborator encapsulating physical ExoPlayer stream observations,
 * high-frequency progress polling, gapless transitions, and CUE virtual timeline arithmetic.
 */
internal class PlaybackTimelineCoordinator(
    private val playerEngine: AudioPlayerEngine,
    private val serverRepository: ServerRepository?,
    private val sessionStateFlow: MutableStateFlow<PlayerSessionState>,
    private val playbackProgressFlow: MutableStateFlow<PlaybackProgress>,
    private val virtualTimelineEngine: VirtualTimelineEngine,
    private val faultTolerance: PlaybackFaultTolerance,
    private val queueManager: PlaybackQueueManager,
    private val persistenceCoordinator: SessionPersistenceCoordinator,
    private val coroutineScope: CoroutineScope,
    private val progressDispatcher: CoroutineDispatcher,
    private val isPrerollAppended: AtomicBoolean,
    private val getHasStreamLoaded: () -> Boolean,
    private val clearPrerollIfActive: () -> Unit,
    private val onRestoreSession: suspend () -> Unit,
    private val onSetActiveServer: (WebDavServer?) -> Unit,
    private val onSkipToNext: () -> Unit,
    private val onTransitionToQueueIndex: (WebDavServer?, Int) -> Unit,
) {
    var activeParentAudioTrack: AudioTrack? = null
    var activeCuePath: String? = null

    val isVirtualTimelineActive: Boolean
        get() = virtualTimelineEngine.isActive

    private val observationJobs = CopyOnWriteArrayList<Job>()

    fun startObservations() {
        startSessionAndServerObservation()
        startPlaybackStateObservation()
        startTrackTransitionsObservation()
        startProgressPipeline()
        startDurationObservation()
    }

    fun cancelObservations() {
        observationJobs.forEach { it.cancel() }
        observationJobs.clear()
    }

    fun resetVirtualTimeline() {
        virtualTimelineEngine.clear()
        activeParentAudioTrack = null
        activeCuePath = null
    }

    fun applyCueContextAndGetStartMs(
        vt: List<VirtualTrack>?,
        parent: AudioTrack?,
        cue: String?,
        fallbackStartMs: Long = 0L,
        targetIndex: Int = 0,
    ): Long =
        if (vt != null) {
            activeParentAudioTrack = parent
            activeCuePath = cue
            virtualTimelineEngine.loadTracks(vt, targetIndex)
        } else {
            resetVirtualTimeline()
            fallbackStartMs
        }

    fun calculateSeekTargetMs(positionMs: Long): Long =
        if (virtualTimelineEngine.isActive) {
            virtualTimelineEngine.calculateSeekTargetMs(positionMs, playerEngine.durationMs.value)
        } else {
            positionMs
        }

    fun mapToVirtualPosition(positionMs: Long): Long? = if (virtualTimelineEngine.isActive) positionMs else null

    fun trySkipVirtualTrackNext(mode: PlaybackMode): Boolean {
        if (virtualTimelineEngine.isActive && !sessionStateFlow.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.getNextTrackSeekPosition(mode)?.let { applyVirtualTrackSeek(it) }
            return true
        }
        return false
    }

    fun trySkipVirtualTrackPrevious(
        currentEnginePosMs: Long,
        mode: PlaybackMode,
    ): Boolean {
        if (virtualTimelineEngine.isActive && !sessionStateFlow.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.getPreviousTrackSeekPosition(currentEnginePosMs, mode)?.let {
                applyVirtualTrackSeek(it)
            }
            return true
        }
        return false
    }

    fun trySeekVirtualTrack(index: Int): Boolean {
        if (virtualTimelineEngine.isActive && !sessionStateFlow.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.seekToTrackIndex(index)?.let { applyVirtualTrackSeek(it) }
            return true
        }
        return false
    }

    fun trySeekActiveVirtualTrack(
        parentTrack: AudioTrack,
        validIndex: Int,
    ): Boolean {
        val cleanParent = parentTrack.copy(id = parentTrack.id.substringBefore("#cue_"))
        if (virtualTimelineEngine.isActive &&
            activeParentAudioTrack?.serverId == cleanParent.serverId &&
            activeParentAudioTrack?.remotePath == cleanParent.remotePath &&
            playerEngine.playbackState.value !is PlaybackState.Idle
        ) {
            virtualTimelineEngine.seekToTrackIndex(validIndex)?.let {
                applyVirtualTrackSeek(it)
                if (!sessionStateFlow.value.isPlaying) playerEngine.play()
                return true
            }
        }
        return false
    }

    fun applyVirtualTrackSeek(seekResult: TrackSeekResult) {
        faultTolerance.cancelWatchdog()
        sessionStateFlow.update { current ->
            if (seekResult.trackIndex in current.queue.tracks.indices) {
                current.copy(
                    queue = current.queue.copy(currentIndex = seekResult.trackIndex),
                    durationMs = seekResult.track.durationMs,
                    isUnsupportedRange = false,
                    isWeakNetwork = false,
                    canManualRetry = false,
                    isReconnecting = false,
                )
            } else {
                current
            }
        }
        sessionStateFlow.value.queue.currentTrack
            ?.let { playerEngine.updateTrackMetadata(it) }
        playbackProgressFlow.value = PlaybackProgress(0L, seekResult.track.durationMs, 0L)
        playerEngine.seekTo(seekResult.targetPositionMs)
        coroutineScope.launch { persistenceCoordinator.flushSession() }
    }

    fun mapEngineProgress(
        pos: Long,
        dur: Long,
        buf: Long,
    ): PlaybackProgress {
        if (!virtualTimelineEngine.isActive) {
            val trackDur = sessionStateFlow.value.currentTrack?.durationMs ?: 0L
            return PlaybackProgress(pos, if (dur > 0L) dur else trackDur, buf)
        }
        if (playerEngine.playbackState.value is PlaybackState.Idle) return playbackProgressFlow.value
        if (sessionStateFlow.value.queue.isPlaylistQueue) {
            val vt = virtualTimelineEngine.activeTrack
            if (vt?.endTimeMs != null && pos >= vt.endTimeMs) handlePlaybackEnded()
        } else {
            (virtualTimelineEngine.onPositionUpdate(pos) as? VirtualTimelineEngine.TransitionResult.Transitioned)?.let {
                handleVirtualTrackTransition(it.newIndex, it.newTrack)
            }
        }
        return virtualTimelineEngine.mapToVirtualProgress(pos, dur, buf)
    }

    fun handleVirtualTrackTransition(
        newIndex: Int,
        newTrack: VirtualTrack,
    ) {
        sessionStateFlow.update { current ->
            if (newIndex in current.queue.tracks.indices) {
                current.copy(queue = current.queue.copy(currentIndex = newIndex), durationMs = newTrack.durationMs)
            } else {
                current
            }
        }
        sessionStateFlow.value.queue.currentTrack?.let { track ->
            coroutineScope.launch(Dispatchers.Main) {
                playerEngine.updateTrackMetadata(track)
                persistenceCoordinator.flushSession()
            }
        }
    }

    fun handlePlaybackEnded() {
        isPrerollAppended.set(false)
        val currentPos = playerEngine.currentPositionMs.value
        val currentTrack = sessionStateFlow.value.currentTrack
        val duration =
            playerEngine.durationMs.value.takeIf { it > 0L }
                ?: currentTrack?.durationMs
                ?: sessionStateFlow.value.durationMs

        if (faultTolerance.isPrematureEof(currentPos, duration)) {
            sessionStateFlow.update { it.copy(playbackState = PlaybackState.Buffering) }
            playerEngine.seekTo(currentPos)
            playerEngine.play()
            return
        }

        if (virtualTimelineEngine.isActive && !sessionStateFlow.value.queue.isPlaylistQueue) {
            virtualTimelineEngine.getNextTrackSeekPosition(sessionStateFlow.value.playbackMode)?.let {
                applyVirtualTrackSeek(it)
                playerEngine.play()
            } ?: sessionStateFlow.update { it.copy(playbackState = PlaybackState.Ended) }
            return
        }

        val queue = sessionStateFlow.value.queue
        val server =
            sessionStateFlow.value.activeServer
                ?: return run { sessionStateFlow.update { it.copy(playbackState = PlaybackState.Ended) } }
        if (queue.isEmpty) return run { sessionStateFlow.update { it.copy(playbackState = PlaybackState.Ended) } }

        if (sessionStateFlow.value.playbackMode == PlaybackMode.SINGLE_LOOP) {
            playbackProgressFlow.value = PlaybackProgress(0L, duration, 0L)
            playerEngine.seekTo(0L)
            playerEngine.play()
            coroutineScope.launch { persistenceCoordinator.flushSession() }
        } else if (sessionStateFlow.value.playbackMode == PlaybackMode.SHUFFLE && queue.currentIndex >= queue.tracks.lastIndex) {
            val reshuffledQueue = queue.reshuffle()
            sessionStateFlow.update { it.copy(queue = reshuffledQueue) }
            onTransitionToQueueIndex(server, 0)
        } else {
            onTransitionToQueueIndex(server, queueManager.calculateNextQueueIndex(queue, sessionStateFlow.value.playbackMode))
        }
    }

    private fun startSessionAndServerObservation() {
        observationJobs +=
            coroutineScope.launch {
                try {
                    onRestoreSession()
                } finally {
                    persistenceCoordinator.markRestored()
                }
                if (serverRepository != null) {
                    try {
                        serverRepository.getActiveServer().collect { server ->
                            val current = sessionStateFlow.value
                            if (current.activeServer?.id != server?.id && !current.queue.isPlaylistQueue) {
                                onSetActiveServer(server)
                            }
                        }
                    } catch (e: Throwable) {
                        // ignore during teardown
                    }
                }
            }
    }

    private fun startPlaybackStateObservation() {
        observationJobs +=
            coroutineScope.launch {
                playerEngine.playbackState.collect { state ->
                    when (state) {
                        is PlaybackState.Ended -> {
                            handlePlaybackEnded()
                            return@collect
                        }

                        is PlaybackState.Buffering -> {
                            clearPrerollIfActive()
                            faultTolerance.onBufferingStarted()
                        }

                        is PlaybackState.Playing -> {
                            faultTolerance.onPlayingStarted()
                            persistenceCoordinator.startPeriodicFlush()
                        }

                        is PlaybackState.Error -> {
                            val handled =
                                faultTolerance.handlePlaybackError(
                                    state,
                                    sessionStateFlow.value.currentTrack,
                                    {
                                        playerEngine.pause()
                                        persistenceCoordinator.stopPeriodicFlush()
                                    },
                                    { onSkipToNext() },
                                )
                            if (handled) return@collect
                        }

                        else -> {
                            faultTolerance.cancelWatchdog()
                            persistenceCoordinator.stopPeriodicFlush()
                        }
                    }
                    sessionStateFlow.update { current ->
                        val target =
                            if (state is PlaybackState.Idle && current.isPaused && current.hasTrack && !getHasStreamLoaded()) {
                                current.playbackState
                            } else {
                                state
                            }
                        current.copy(
                            playbackState = target,
                            errorMessage = if (state is PlaybackState.Error) state.message else current.errorMessage,
                        )
                    }
                }
            }
    }

    private fun startTrackTransitionsObservation() {
        observationJobs +=
            coroutineScope.launch {
                playerEngine.trackTransitions.collect {
                    isPrerollAppended.set(false)
                    val queue = sessionStateFlow.value.queue
                    if (!queue.isEmpty) {
                        val nextIndex = queueManager.calculateNextQueueIndex(queue, sessionStateFlow.value.playbackMode)
                        val nextTrack = queue.tracks[nextIndex]
                        val currentServer = sessionStateFlow.value.activeServer
                        val resolvedServer =
                            if (serverRepository != null && (currentServer == null || nextTrack.serverId != currentServer.id)) {
                                try {
                                    serverRepository.getServerById(nextTrack.serverId)?.also {
                                        playerEngine.setActiveServer(it)
                                    }
                                } catch (_: Throwable) {
                                    null
                                }
                            } else {
                                null
                            }
                        playbackProgressFlow.value = PlaybackProgress(0L, nextTrack.durationMs, 0L)
                        sessionStateFlow.update {
                            it.copy(
                                queue = queue.copy(currentIndex = nextIndex),
                                durationMs = nextTrack.durationMs,
                                activeServer = resolvedServer ?: it.activeServer,
                            )
                        }
                        persistenceCoordinator.flushSession()
                    }
                }
            }
    }

    private fun startProgressPipeline() {
        observationJobs +=
            coroutineScope.launch(progressDispatcher) {
                combine(playerEngine.currentPositionMs, playerEngine.durationMs, playerEngine.bufferedPositionMs) { pos, dur, buf ->
                    mapEngineProgress(pos, dur, buf)
                }.distinctUntilChanged().collect { progress ->
                    if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                        playbackProgressFlow.value = progress
                        checkAndTriggerAdaptivePreroll(progress)
                    }
                }
            }
    }

    private fun startDurationObservation() {
        observationJobs +=
            coroutineScope.launch {
                playerEngine.durationMs.collect { dur ->
                    if (playerEngine.playbackState.value !is PlaybackState.Idle && dur > 0L && !virtualTimelineEngine.isActive) {
                        sessionStateFlow.update { current ->
                            val curIdx = current.queue.currentIndex
                            val updated =
                                current.queue.tracks.mapIndexed { idx, trk ->
                                    if (idx == curIdx && trk.durationMs != dur) {
                                        trk.copy(durationMs = dur)
                                    } else {
                                        trk
                                    }
                                }
                            current.copy(durationMs = dur, queue = current.queue.copy(tracks = updated))
                        }
                    }
                }
            }
    }

    private fun checkAndTriggerAdaptivePreroll(progress: PlaybackProgress) {
        faultTolerance.checkAndTriggerAdaptivePreroll(
            progress = progress,
            queue = sessionStateFlow.value.queue,
            playbackMode = sessionStateFlow.value.playbackMode,
            activeServer = sessionStateFlow.value.activeServer,
            currentTrackId = sessionStateFlow.value.currentTrack?.id,
            isPrerollAppended = isPrerollAppended,
            serverRepository = serverRepository,
            onPreroll = { nextServer, nextTrack -> playerEngine.prerollTrack(nextServer, nextTrack) },
        )
    }
}
