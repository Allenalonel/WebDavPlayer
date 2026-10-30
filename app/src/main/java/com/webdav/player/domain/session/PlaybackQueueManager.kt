package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure internal collaborator managing playback queue state machine transitions,
 * next/previous index calculations, shuffle order generation, ad-hoc queue modifications,
 * and dead-track cycle prevention.
 */
internal class PlaybackQueueManager(
    private val sessionStateFlow: MutableStateFlow<PlayerSessionState>,
    private val onFlushRequested: () -> Unit = {},
) {
    private val failedTrackIdsInCurrentCycle = mutableSetOf<String>()

    val failedTrackCount: Int
        get() = failedTrackIdsInCurrentCycle.size

    fun hasFailedTracks(): Boolean = failedTrackIdsInCurrentCycle.isNotEmpty()

    fun clearFailedTracks() {
        failedTrackIdsInCurrentCycle.clear()
    }

    /**
     * Records a failed track ID and determines whether to proceed with skipping to next track
     * without causing an infinite cycle. Returns true if skipping should proceed; false if
     * the cycle is exhausted and error state must be displayed.
     */
    fun recordFailedTrack(
        trackId: String,
        queueSize: Int,
    ): Boolean {
        failedTrackIdsInCurrentCycle.add(trackId)
        return if (queueSize > 1 && failedTrackIdsInCurrentCycle.size < queueSize) {
            true
        } else {
            failedTrackIdsInCurrentCycle.clear()
            false
        }
    }

    /**
     * Calculates the next track index in the queue under the active playback mode,
     * guarding against infinite loops in SINGLE_LOOP mode if tracks have failed.
     */
    fun calculateNextQueueIndex(
        queue: PlaybackQueue,
        mode: PlaybackMode,
    ): Int {
        if (queue.tracks.size <= 1) return 0
        if (failedTrackIdsInCurrentCycle.isNotEmpty() && mode == PlaybackMode.SINGLE_LOOP) {
            return (queue.currentIndex + 1) % queue.tracks.size
        }
        return queue.getNextIndex(mode) ?: 0
    }

    /**
     * Calculates the previous track index in the queue under the active playback mode.
     */
    fun calculatePreviousQueueIndex(
        queue: PlaybackQueue,
        mode: PlaybackMode,
    ): Int {
        if (queue.tracks.size <= 1) return 0
        return queue.getPreviousIndex(mode) ?: 0
    }

    /**
     * Sets the shuffle order for the active queue.
     */
    fun setShuffleOrder(order: List<Int>) {
        sessionStateFlow.update { it.copy(queue = it.queue.copy(shuffleOrder = order)) }
    }

    /**
     * Updates the playback mode, lazily generating shuffle order if transitioning into SHUFFLE.
     */
    fun setPlaybackMode(mode: PlaybackMode) {
        sessionStateFlow.update { current ->
            val updatedQueue =
                if (mode == PlaybackMode.SHUFFLE && current.queue.shuffleOrder.isEmpty() && current.queue.tracks.isNotEmpty()) {
                    current.queue.withGeneratedShuffleOrder()
                } else {
                    current.queue
                }
            current.copy(playbackMode = mode, queue = updatedQueue)
        }
        onFlushRequested()
    }

    /**
     * Cycles to the next playback mode in order (LIST_LOOP -> SINGLE_LOOP -> SHUFFLE -> LIST_LOOP).
     */
    fun cyclePlaybackMode() {
        val nextMode = sessionStateFlow.value.playbackMode.next()
        setPlaybackMode(nextMode)
    }

    /**
     * Inserts a track immediately after the currently active track in the queue.
     */
    fun playNext(
        track: AudioTrack,
        onPlayTrack: (AudioTrack) -> Unit,
    ) {
        if (sessionStateFlow.value.activeServer == null) return
        val currentQueue = sessionStateFlow.value.queue
        if (currentQueue.isEmpty) {
            onPlayTrack(track)
            return
        }
        val updatedQueue = currentQueue.insertNext(track)
        sessionStateFlow.update { it.copy(queue = updatedQueue) }
        onFlushRequested()
    }

    /**
     * Inserts a track immediately after the active track and immediately advances playback to it.
     */
    fun playNextAndAdvance(
        track: AudioTrack,
        hasServerOrRepo: Boolean,
        onPlayTrack: (AudioTrack) -> Unit,
        onTransitionToIndex: (Int) -> Unit,
    ) {
        if (!hasServerOrRepo) return
        val currentQueue = sessionStateFlow.value.queue
        if (currentQueue.isEmpty) {
            onPlayTrack(track)
            return
        }
        val targetIndex = (currentQueue.currentIndex + 1).coerceAtMost(currentQueue.tracks.size)
        val updatedQueue = currentQueue.insertNext(track)
        sessionStateFlow.update { it.copy(queue = updatedQueue) }
        onTransitionToIndex(targetIndex)
    }

    /**
     * Removes a track at the specified index from the active queue.
     */
    fun removeQueueTrack(
        index: Int,
        onQueueCleared: () -> Unit,
        onCurrentTrackChanged: (AudioTrack) -> Unit,
    ) {
        val currentQueue = sessionStateFlow.value.queue
        if (index !in currentQueue.tracks.indices) return

        val wasCurrentTrack = (index == currentQueue.currentIndex)
        val updatedQueue = currentQueue.removeTrackAt(index)

        if (updatedQueue.isEmpty) {
            sessionStateFlow.update {
                it.copy(
                    queue = PlaybackQueue.EMPTY,
                    playbackState = PlaybackState.Idle,
                    durationMs = 0L,
                    lyrics = null,
                    isLoadingLyrics = false,
                )
            }
            onQueueCleared()
        } else {
            val nextTrack = updatedQueue.currentTrack
            if (wasCurrentTrack && nextTrack != null) {
                sessionStateFlow.update {
                    it.copy(
                        queue = updatedQueue,
                        durationMs = nextTrack.durationMs,
                    )
                }
                onCurrentTrackChanged(nextTrack)
            } else {
                sessionStateFlow.update {
                    it.copy(queue = updatedQueue)
                }
            }
        }
        onFlushRequested()
    }
}
