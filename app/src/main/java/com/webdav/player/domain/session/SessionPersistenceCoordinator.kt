package com.webdav.player.domain.session

import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.QueueOrigin
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.PlaybackSessionStore
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal collaborator managing playback session persistence, periodic flushing,
 * and cold session reconstruction for both normal and CUE virtual track sessions.
 */
internal class SessionPersistenceCoordinator(
    private val sessionStore: PlaybackSessionStore?,
    private val serverRepository: ServerRepository?,
    private val trackMetadataRepository: TrackMetadataRepository?,
    private val cueSheetResolver: CueSheetResolver?,
    private val playerEngine: AudioPlayerEngine,
    private val virtualTimelineEngine: VirtualTimelineEngine,
    private val sessionStateFlow: MutableStateFlow<PlayerSessionState>,
    private val playbackProgressFlow: MutableStateFlow<PlaybackProgress>,
    private val coroutineScope: CoroutineScope,
    private val periodicDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val getActiveParentAudioTrack: () -> AudioTrack? = { null },
    private val getActiveCuePath: () -> String? = { null },
    private val onCueSessionRestored: (parentTrack: AudioTrack, cuePath: String) -> Unit = { _, _ -> },
    private val onOrdinarySessionRestored: () -> Unit = {},
) {
    private val _isRestored = MutableStateFlow(sessionStore == null)
    val isRestored: StateFlow<Boolean> = _isRestored.asStateFlow()

    private val serverLastDirectories = ConcurrentHashMap<Long, String>()
    private var periodicFlushJob: Job? = null

    fun markRestored() {
        _isRestored.value = true
    }

    fun getLastDirectoryForServer(serverId: Long): String = serverLastDirectories[serverId] ?: "/"

    fun savePosition(
        positionMs: Long,
        virtualPositionMs: Long? = null,
        isCleanExit: Boolean = false,
    ) {
        if (!_isRestored.value) return
        val store = sessionStore ?: return
        coroutineScope.launch {
            store.savePosition(positionMs, virtualPositionMs, isCleanExit)
        }
    }

    fun setCurrentDirectoryPath(path: String) {
        val serverId = sessionStateFlow.value.activeServer?.id
        if (serverId != null) {
            serverLastDirectories[serverId] = path
        }
        sessionStateFlow.update { it.copy(currentDirectoryPath = path) }
        coroutineScope.launch { flushSession() }
    }

    suspend fun restoreSession() {
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

            val current = sessionStateFlow.value
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

        val targetServer =
            server
                ?: sessionStateFlow.value.activeServer?.takeIf { it.id == savedSession.activeServerId }
                ?: savedSession.activeServerId?.let { serverRepository?.getServerById(it) }
                ?: return false

        val virtualTracks =
            cueSheetResolver?.resolveVirtualTracks(
                server = targetServer,
                cuePath = cuePath,
                parentAudioPath = normalizedParentTrack.remotePath,
                totalDurationMs = normalizedParentTrack.durationMs,
            ) ?: emptyList()

        // If CUE parsing failed or returned no tracks, fallback safely
        if (virtualTracks.isEmpty()) {
            return false
        }

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
        onCueSessionRestored(normalizedParentTrack, cuePath)

        val relPositionMs =
            (
                savedSession.virtualPositionMs
                    ?: (savedSession.positionMs - targetVirtualTrack.startTimeMs).coerceAtLeast(0L)
            ).coerceIn(0L, if (targetVirtualTrack.durationMs > 0L) targetVirtualTrack.durationMs else Long.MAX_VALUE)

        val rawQueue = PlaybackQueue(tracks = mappedTracks, currentIndex = targetIndex)
        val queue =
            if (savedSession.playbackMode == PlaybackMode.SHUFFLE) {
                if (savedSession.originalTracks.isNotEmpty()) {
                    PlaybackQueue(tracks = mappedTracks, currentIndex = targetIndex, originalTracks = savedSession.originalTracks)
                } else {
                    rawQueue.toShuffled()
                }
            } else {
                rawQueue
            }

        val wasKilled = !savedSession.cleanExit && savedSession.currentTrackIndex >= 0 && savedSession.queueTracks.isNotEmpty()
        sessionStateFlow.update {
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

        playbackProgressFlow.value =
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
        onOrdinarySessionRestored()

        val isPlaylist =
            savedSession.queueOrigin == QueueOrigin.PLAYLIST ||
                savedSession.queueTracks
                    .map { it.serverId }
                    .distinct()
                    .size > 1
        val origin = if (isPlaylist) QueueOrigin.PLAYLIST else QueueOrigin.DIRECTORY
        val restoredQueue =
            PlaybackQueue(
                tracks = savedSession.queueTracks,
                currentIndex = savedSession.currentTrackIndex.coerceIn(-1, savedSession.queueTracks.lastIndex),
                originalTracks = savedSession.originalTracks,
                origin = origin,
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
                val updatedOriginal =
                    savedSession.originalTracks.map { track ->
                        val cachedMeta = trackMetadataRepository?.getCachedMetadata(server.id, track.remotePath)
                        if (cachedMeta != null) track.withMetadata(cachedMeta) else track
                    }
                PlaybackQueue(
                    tracks = updatedTracks,
                    currentIndex = savedSession.currentTrackIndex.coerceIn(-1, updatedTracks.lastIndex),
                    originalTracks = updatedOriginal,
                    origin = origin,
                )
            } else {
                restoredQueue
            }
        val durationMs = finalQueue.currentTrack?.durationMs ?: restoredTrack?.durationMs ?: 0L

        val wasKilled = !savedSession.cleanExit && savedSession.currentTrackIndex >= 0 && savedSession.queueTracks.isNotEmpty()
        sessionStateFlow.update {
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
        playbackProgressFlow.value =
            PlaybackProgress(
                currentPositionMs = savedSession.positionMs,
                durationMs = durationMs,
                bufferedPositionMs = 0L,
            )
    }

    suspend fun flushSession() {
        val isActivelyPlaying =
            playerEngine.playbackState.value is PlaybackState.Playing ||
                playerEngine.playbackState.value is PlaybackState.Buffering
        flushSessionWithCleanExit(isCleanExit = !isActivelyPlaying)
    }

    suspend fun flushSessionWithCleanExit(isCleanExit: Boolean) {
        if (!_isRestored.value) return
        val store = sessionStore ?: return
        val current = sessionStateFlow.value
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
                    (activeVirtualTrack?.startTimeMs ?: 0L) + playbackProgressFlow.value.currentPositionMs
                } else {
                    playbackProgressFlow.value.currentPositionMs
                }
            } else if (isVirtual) {
                (activeVirtualTrack?.startTimeMs ?: 0L) + playbackProgressFlow.value.currentPositionMs
            } else {
                playbackProgressFlow.value.currentPositionMs
            }

        val virtualPosMs =
            if (isVirtual) {
                if (playerEngine.playbackState.value !is PlaybackState.Idle) {
                    val enginePos = playerEngine.currentPositionMs.value
                    val startMs = activeVirtualTrack?.startTimeMs ?: 0L
                    if (enginePos >= startMs) {
                        (enginePos - startMs)
                    } else {
                        playbackProgressFlow.value.currentPositionMs
                    }
                } else {
                    playbackProgressFlow.value.currentPositionMs
                }
            } else {
                null
            }

        val cuePath = if (isVirtual) getActiveCuePath() else null
        val virtualTrackNumber = if (isVirtual) activeVirtualTrack?.trackNumber else null

        val activeParent = getActiveParentAudioTrack()
        val queueTracksToSave =
            if (isVirtual && activeParent != null) {
                listOf(activeParent)
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
                queueOrigin = current.queue.origin,
                originalTracks = current.queue.originalTracks,
            )
        store.saveSession(sessionData)
    }

    fun flushSessionAsync() {
        coroutineScope.launch {
            flushSession()
        }
    }

    fun startPeriodicFlush() {
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

    fun stopPeriodicFlush() {
        periodicFlushJob?.cancel()
        periodicFlushJob = null
    }
}
