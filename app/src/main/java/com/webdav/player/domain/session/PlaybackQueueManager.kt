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

    fun resolveDirectoryCueMap(
        directory: com.webdav.player.domain.model.RemoteDirectory,
        audioFiles: List<com.webdav.player.domain.model.RemoteFile>,
        server: com.webdav.player.domain.model.WebDavServer,
        cueSheetResolver: com.webdav.player.domain.cue.CueSheetResolver?,
    ): Map<String, List<com.webdav.player.domain.model.VirtualTrack>> {
        val cueFiles = directory.files.filter { it.isCue }
        if (cueFiles.isEmpty() || cueSheetResolver == null) return emptyMap()
        val map = mutableMapOf<String, List<com.webdav.player.domain.model.VirtualTrack>>()
        for (cueFile in cueFiles) {
            val matchedAudio =
                com.webdav.player.data.cue.CueAssociationHelper.findMatchingAudioFile(
                    cueFile = cueFile,
                    audioFiles = audioFiles,
                    totalCueFilesCount = cueFiles.size,
                )
            if (matchedAudio != null) {
                val cachedTracks =
                    cueSheetResolver.getCachedVirtualTracks(
                        server = server,
                        cuePath = cueFile.path,
                        parentAudioPath = matchedAudio.path,
                    )
                if (!cachedTracks.isNullOrEmpty()) {
                    map[matchedAudio.path] = cachedTracks
                }
            }
        }
        return map
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

    fun buildDirectoryQueue(
        directory: com.webdav.player.domain.model.RemoteDirectory,
        selectedFile: com.webdav.player.domain.model.RemoteFile,
        initialMetadata: Map<String, com.webdav.player.domain.model.TrackMetadata>,
        virtualTracksByAudioPath: Map<String, List<com.webdav.player.domain.model.VirtualTrack>>,
        server: com.webdav.player.domain.model.WebDavServer,
        cueSheetResolver: com.webdav.player.domain.cue.CueSheetResolver?,
    ): QueueLoadRequest? {
        val audioFiles = directory.files.filter { it.isAudio }
        if (audioFiles.isEmpty()) return null
        val tracks = audioFiles.mapNotNull { AudioTrack.fromRemoteFile(server, it, initialMetadata[it.path]) }
        if (tracks.isEmpty()) return null

        val selectedIndex = tracks.indexOfFirst { it.remotePath == selectedFile.path }.takeIf { it >= 0 } ?: 0
        val resolvedVirtualMap =
            if (virtualTracksByAudioPath.isNotEmpty()) {
                virtualTracksByAudioPath
            } else {
                resolveDirectoryCueMap(directory, audioFiles, server, cueSheetResolver)
            }

        val queue = PlaybackQueue.fromTracks(tracks = tracks, virtualTracksMap = resolvedVirtualMap, selectedIndex = selectedIndex)
        val selectedTrack = queue.currentTrack ?: return null

        return if (selectedTrack.isVirtualTrack) {
            val parentTrack =
                tracks.firstOrNull { it.remotePath == selectedTrack.remotePath }
                    ?: selectedTrack.copy(id = selectedTrack.id.substringBefore("#cue_"))
            val virtualTracks = resolvedVirtualMap[selectedTrack.remotePath] ?: emptyList()
            val targetVirtualIndex =
                virtualTracks.indexOfFirst { it.trackNumber == selectedTrack.id.substringAfter("#cue_").toIntOrNull() }.takeIf { it >= 0 } ?: 0
            val cuePath = directory.files.firstOrNull { it.isCue }?.path ?: (parentTrack.remotePath.substringBeforeLast('.') + ".cue")
            QueueLoadRequest(
                queue = queue,
                targetTrack = selectedTrack,
                parentTrack = parentTrack,
                cuePath = cuePath,
                virtualTracks = virtualTracks,
                startPositionMs = virtualTracks.getOrNull(targetVirtualIndex)?.startTimeMs ?: 0L,
            )
        } else {
            QueueLoadRequest(
                queue = queue,
                targetTrack = selectedTrack,
            )
        }
    }

    suspend fun buildTrackQueue(
        track: AudioTrack,
        server: com.webdav.player.domain.model.WebDavServer?,
        serverRepository: com.webdav.player.domain.repository.ServerRepository?,
        cueSheetResolver: com.webdav.player.domain.cue.CueSheetResolver?,
    ): QueueLoadRequest {
        val queue = PlaybackQueue(tracks = listOf(track), currentIndex = 0, origin = com.webdav.player.domain.model.QueueOrigin.AD_HOC)
        val (vt, startPositionMs) = resolveVirtualTrackOffset(track, server, serverRepository, cueSheetResolver)
        return if (vt != null) {
            val parentTrack = track.copy(id = track.id.substringBefore("#cue_"))
            val cuePath = track.cuePath ?: (track.remotePath.substringBeforeLast('.') + ".cue")
            QueueLoadRequest(
                queue = queue,
                targetTrack = track,
                parentTrack = parentTrack,
                cuePath = cuePath,
                virtualTracks = listOf(vt),
                startPositionMs = startPositionMs,
            )
        } else {
            QueueLoadRequest(
                queue = queue,
                targetTrack = track,
                startPositionMs = 0L,
            )
        }
    }

    fun buildVirtualTracksQueue(
        parentTrack: AudioTrack,
        virtualTracks: List<com.webdav.player.domain.model.VirtualTrack>,
        startIndex: Int,
        cuePath: String?,
        playbackMode: PlaybackMode,
    ): QueueLoadRequest {
        val cleanParentTrack = parentTrack.copy(id = parentTrack.id.substringBefore("#cue_"))
        val validStartIndex = startIndex.coerceIn(0, virtualTracks.lastIndex)
        val resolvedCuePath = cuePath ?: (parentTrack.remotePath.substringBeforeLast('.') + ".cue")
        val mappedTracks = virtualTracks.map { it.toAudioTrack(cleanParentTrack, resolvedCuePath) }
        val initialVirtualTrack = mappedTracks[validStartIndex]
        val rawQueue = PlaybackQueue(tracks = mappedTracks, currentIndex = validStartIndex)
        val queue = if (playbackMode == PlaybackMode.SHUFFLE) rawQueue.withGeneratedShuffleOrder(validStartIndex) else rawQueue
        return QueueLoadRequest(
            queue = queue,
            targetTrack = initialVirtualTrack,
            parentTrack = cleanParentTrack,
            cuePath = resolvedCuePath,
            virtualTracks = virtualTracks,
            startPositionMs = virtualTracks[validStartIndex].startTimeMs,
        )
    }

    suspend fun buildPlaylistQueue(
        entries: List<com.webdav.player.domain.model.PlaylistTrackEntry>,
        startIndex: Int,
        server: com.webdav.player.domain.model.WebDavServer?,
        serverRepository: com.webdav.player.domain.repository.ServerRepository?,
        cueSheetResolver: com.webdav.player.domain.cue.CueSheetResolver?,
    ): QueueLoadRequest? {
        if (entries.isEmpty()) return null
        val tracks = entries.map { it.toAudioTrack() }
        val safeStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val queue = PlaybackQueue.fromTracks(tracks = tracks, selectedIndex = safeStartIndex, origin = com.webdav.player.domain.model.QueueOrigin.PLAYLIST)
        val currentTrack = queue.currentTrack ?: tracks[safeStartIndex]
        val (matchedVt, startPositionMs) = resolveVirtualTrackOffset(currentTrack, server, serverRepository, cueSheetResolver)
        return if (matchedVt != null) {
            val parentTrack = currentTrack.copy(id = currentTrack.id.substringBefore("#cue_"))
            val cuePath = currentTrack.cuePath ?: (currentTrack.remotePath.substringBeforeLast('.') + ".cue")
            QueueLoadRequest(
                queue = queue,
                targetTrack = currentTrack,
                parentTrack = parentTrack,
                cuePath = cuePath,
                virtualTracks = listOf(matchedVt),
                startPositionMs = startPositionMs,
            )
        } else {
            QueueLoadRequest(
                queue = queue,
                targetTrack = currentTrack,
                startPositionMs = 0L,
            )
        }
    }

    suspend fun resolveVirtualTrackOffset(
        track: AudioTrack,
        server: com.webdav.player.domain.model.WebDavServer?,
        serverRepository: com.webdav.player.domain.repository.ServerRepository?,
        cueSheetResolver: com.webdav.player.domain.cue.CueSheetResolver?,
    ): Pair<com.webdav.player.domain.model.VirtualTrack?, Long> {
        if (!track.isVirtualTrack) return null to 0L
        val trackNum = track.id.substringAfter("#cue_").toIntOrNull() ?: return null to 0L
        val cuePath = track.cuePath ?: (track.remotePath.substringBeforeLast('.') + ".cue")
        val targetServer =
            server?.takeIf { it.id == track.serverId }
                ?: serverRepository?.getServerById(track.serverId)
                ?: return null to 0L

        val virtualTracks =
            cueSheetResolver?.resolveVirtualTracks(
                server = targetServer,
                cuePath = cuePath,
                parentAudioPath = track.remotePath,
                totalDurationMs = track.durationMs,
            ) ?: emptyList()

        val matched = virtualTracks.firstOrNull { it.trackNumber == trackNum }
        return matched to (matched?.startTimeMs ?: 0L)
    }
}

internal data class QueueLoadRequest(
    val queue: PlaybackQueue,
    val targetTrack: AudioTrack,
    val parentTrack: AudioTrack? = null,
    val cuePath: String? = null,
    val virtualTracks: List<com.webdav.player.domain.model.VirtualTrack>? = null,
    val startPositionMs: Long = 0L,
)
