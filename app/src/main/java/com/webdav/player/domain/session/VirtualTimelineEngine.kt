package com.webdav.player.domain.session

import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.VirtualTrack

data class TrackSeekResult(
    val trackIndex: Int,
    val track: VirtualTrack,
    val targetPositionMs: Long,
)

/**
 * Pure Kotlin virtual timeline mapping engine.
 * Maps global physical stream position and duration to the active virtual track's relative timeline,
 * detects natural track boundary crossings, and intercepts seek and track stepping.
 */
class VirtualTimelineEngine {

    private var _activeTracks: List<VirtualTrack> = emptyList()
    val activeTracks: List<VirtualTrack>
        get() = _activeTracks

    var currentIndex: Int = -1
        private set

    val activeTrack: VirtualTrack?
        get() = _activeTracks.getOrNull(currentIndex)

    val isActive: Boolean
        get() = _activeTracks.isNotEmpty() && currentIndex in _activeTracks.indices

    sealed interface TransitionResult {
        object NoChange : TransitionResult
        data class Transitioned(
            val oldIndex: Int,
            val newIndex: Int,
            val newTrack: VirtualTrack,
        ) : TransitionResult
    }

    /**
     * Loads a list of [VirtualTrack] items and sets the active track index.
     * Returns the start timestamp in milliseconds of the initial track, or 0L if empty.
     */
    fun loadTracks(tracks: List<VirtualTrack>, initialIndex: Int = 0): Long {
        if (tracks.isEmpty()) {
            clear()
            return 0L
        }
        _activeTracks = tracks
        currentIndex = initialIndex.coerceIn(0, tracks.lastIndex)
        return activeTrack?.startTimeMs ?: 0L
    }

    /**
     * Resets the engine state to inactive and clears all tracks.
     */
    fun clear() {
        _activeTracks = emptyList()
        currentIndex = -1
    }

    /**
     * Maps global player stream progress (position, duration, buffered position)
     * to the active virtual track's relative timeline.
     */
    fun mapToVirtualProgress(
        globalPositionMs: Long,
        globalDurationMs: Long = 0L,
        bufferedGlobalPositionMs: Long = 0L,
    ): PlaybackProgress {
        val current = activeTrack
        if (!isActive || current == null) {
            return PlaybackProgress(
                currentPositionMs = globalPositionMs.coerceAtLeast(0L),
                durationMs = globalDurationMs.coerceAtLeast(0L),
                bufferedPositionMs = bufferedGlobalPositionMs.coerceAtLeast(0L),
            )
        }

        val virtualPositionMs = (globalPositionMs - current.startTimeMs).coerceAtLeast(0L)
        val virtualDurationMs =
            if (current.durationMs > 0L) {
                current.durationMs
            } else if (globalDurationMs > current.startTimeMs) {
                globalDurationMs - current.startTimeMs
            } else {
                0L
            }

        val virtualBufferedMs =
            if (bufferedGlobalPositionMs > current.startTimeMs) {
                val relBuffered = bufferedGlobalPositionMs - current.startTimeMs
                if (virtualDurationMs > 0L) relBuffered.coerceAtMost(virtualDurationMs) else relBuffered
            } else {
                0L
            }

        return PlaybackProgress(
            currentPositionMs = virtualPositionMs,
            durationMs = virtualDurationMs,
            bufferedPositionMs = virtualBufferedMs,
        )
    }

    /**
     * Evaluates the global stream playback position, detects natural boundary
     * crossings or seek-induced track index changes, and updates [currentIndex] atomically.
     */
    fun onPositionUpdate(globalPositionMs: Long): TransitionResult {
        if (!isActive || _activeTracks.isEmpty()) {
            return TransitionResult.NoChange
        }

        val current = activeTrack ?: return TransitionResult.NoChange
        val oldIndex = currentIndex

        // Check if global position is already within the active track
        val withinCurrent = globalPositionMs >= current.startTimeMs &&
            (current.endTimeMs == null || globalPositionMs < current.endTimeMs)

        if (withinCurrent) {
            return TransitionResult.NoChange
        }

        // Edge case: on the last track and position is past its endTimeMs
        if (oldIndex == _activeTracks.lastIndex && current.endTimeMs != null && globalPositionMs >= current.endTimeMs) {
            return TransitionResult.NoChange
        }

        // Find the track matching the current globalPositionMs
        val matchingIndex = _activeTracks.indexOfFirst { track ->
            globalPositionMs >= track.startTimeMs && (track.endTimeMs == null || globalPositionMs < track.endTimeMs)
        }

        val targetIndex = when {
            matchingIndex >= 0 -> matchingIndex
            globalPositionMs < _activeTracks.first().startTimeMs -> 0
            else -> _activeTracks.lastIndex
        }

        if (targetIndex != oldIndex) {
            currentIndex = targetIndex
            return TransitionResult.Transitioned(
                oldIndex = oldIndex,
                newIndex = targetIndex,
                newTrack = _activeTracks[targetIndex],
            )
        }

        return TransitionResult.NoChange
    }

    /**
     * Translates a relative seek offset in milliseconds within the active virtual track
     * to the absolute physical stream millisecond position, clamping within the track boundaries.
     */
    fun calculateSeekTargetMs(
        relativeOffsetMs: Long,
        globalDurationMs: Long = 0L,
    ): Long {
        val current = activeTrack
        if (!isActive || current == null) {
            return relativeOffsetMs.coerceAtLeast(0L)
        }

        val maxRelativeOffset =
            if (current.durationMs > 0L) {
                current.durationMs
            } else if (globalDurationMs > current.startTimeMs) {
                globalDurationMs - current.startTimeMs
            } else {
                Long.MAX_VALUE
            }

        val clampedOffset = relativeOffsetMs.coerceIn(0L, maxRelativeOffset)
        return current.startTimeMs + clampedOffset
    }

    /**
     * Determines the target track and seek timestamp for skipping to the next track.
     */
    fun getNextTrackSeekPosition(mode: PlaybackMode = PlaybackMode.LIST_LOOP): TrackSeekResult? {
        if (!isActive || _activeTracks.isEmpty()) return null

        if (mode == PlaybackMode.SINGLE_LOOP) {
            val current = activeTrack ?: return null
            return TrackSeekResult(currentIndex, current, current.startTimeMs)
        }

        if (currentIndex < _activeTracks.lastIndex) {
            val targetIndex = currentIndex + 1
            currentIndex = targetIndex
            val targetTrack = _activeTracks[targetIndex]
            return TrackSeekResult(targetIndex, targetTrack, targetTrack.startTimeMs)
        }

        val targetIndex = 0
        currentIndex = targetIndex
        val targetTrack = _activeTracks[targetIndex]
        return TrackSeekResult(targetIndex, targetTrack, targetTrack.startTimeMs)
    }

    /**
     * Determines the target track and seek timestamp for skipping to the previous track.
     * If relative playback time > 3000ms, restarts current track.
     * If relative playback time <= 3000ms, jumps to previous track (or loops to last track in LIST_LOOP).
     */
    fun getPreviousTrackSeekPosition(
        globalPositionMs: Long,
        mode: PlaybackMode = PlaybackMode.LIST_LOOP,
    ): TrackSeekResult? {
        if (!isActive || _activeTracks.isEmpty()) return null
        val current = activeTrack ?: return null

        val relativePos = (globalPositionMs - current.startTimeMs).coerceAtLeast(0L)
        if (relativePos > 3000L) {
            return TrackSeekResult(currentIndex, current, current.startTimeMs)
        }

        if (currentIndex > 0) {
            val targetIndex = currentIndex - 1
            currentIndex = targetIndex
            val targetTrack = _activeTracks[targetIndex]
            return TrackSeekResult(targetIndex, targetTrack, targetTrack.startTimeMs)
        }

        return when (mode) {
            PlaybackMode.LIST_LOOP, PlaybackMode.SHUFFLE -> {
                val targetIndex = _activeTracks.lastIndex
                currentIndex = targetIndex
                val targetTrack = _activeTracks[targetIndex]
                TrackSeekResult(targetIndex, targetTrack, targetTrack.startTimeMs)
            }

            PlaybackMode.SINGLE_LOOP -> {
                TrackSeekResult(0, current, current.startTimeMs)
            }
        }
    }

    /**
     * Seeks to a specific track index directly.
     */
    fun seekToTrackIndex(index: Int): TrackSeekResult? {
        if (!isActive || index !in _activeTracks.indices) return null
        currentIndex = index
        val targetTrack = _activeTracks[index]
        return TrackSeekResult(index, targetTrack, targetTrack.startTimeMs)
    }
}
