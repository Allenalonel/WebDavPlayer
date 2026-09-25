package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicPlayerAppSessionImpl(
    private val playerEngine: AudioPlayerEngine,
    serverRepository: ServerRepository? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : MusicPlayerAppSession {

    private val _sessionState = MutableStateFlow(PlayerSessionState())
    override val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    init {
        // Observe active server from repository if provided
        if (serverRepository != null) {
            coroutineScope.launch {
                serverRepository.getActiveServer().collect { server ->
                    setActiveServer(server)
                }
            }
        }

        // Observe player engine state changes
        coroutineScope.launch {
            playerEngine.playbackState.collect { state ->
                _sessionState.update { current ->
                    current.copy(
                        playbackState = state,
                        errorMessage = if (state is PlaybackState.Error) state.message else current.errorMessage
                    )
                }
            }
        }

        coroutineScope.launch {
            playerEngine.currentPositionMs.collect { pos ->
                _sessionState.update { it.copy(currentPositionMs = pos) }
            }
        }

        coroutineScope.launch {
            playerEngine.durationMs.collect { dur ->
                _sessionState.update { it.copy(durationMs = dur) }
            }
        }

        coroutineScope.launch {
            playerEngine.currentTrackIndex.collect { index ->
                _sessionState.update { current ->
                    if (index in current.queue.tracks.indices) {
                        current.copy(queue = current.queue.copy(currentIndex = index))
                    } else {
                        current
                    }
                }
            }
        }

        coroutineScope.launch {
            playerEngine.playbackMode.collect { mode ->
                _sessionState.update { it.copy(playbackMode = mode) }
            }
        }
    }

    override fun setActiveServer(server: WebDavServer?) {
        _sessionState.update { current ->
            if (current.activeServer?.id != server?.id) {
                if (current.playbackState !is PlaybackState.Idle) {
                    playerEngine.stop()
                }
                current.copy(
                    activeServer = server,
                    queue = PlaybackQueue.EMPTY,
                    playbackState = PlaybackState.Idle,
                    currentPositionMs = 0L,
                    durationMs = 0L,
                    errorMessage = null
                )
            } else {
                current.copy(activeServer = server)
            }
        }
    }

    override fun playDirectoryTrack(directory: RemoteDirectory, selectedFile: RemoteFile) {
        val server = _sessionState.value.activeServer ?: return
        val audioFiles = directory.files.filter { it.isAudio }
        if (audioFiles.isEmpty()) return

        val tracks = audioFiles.mapNotNull { AudioTrack.fromRemoteFile(server, it) }
        if (tracks.isEmpty()) return

        val selectedIndex = tracks.indexOfFirst { it.remotePath == selectedFile.path }
            .takeIf { it >= 0 } ?: 0

        val queue = PlaybackQueue(tracks = tracks, currentIndex = selectedIndex)
        _sessionState.update { it.copy(queue = queue, errorMessage = null) }

        playerEngine.playTracks(
            server = server,
            tracks = tracks,
            startIndex = selectedIndex
        )
    }

    override fun playTrack(track: AudioTrack) {
        val server = _sessionState.value.activeServer ?: return
        val queue = PlaybackQueue(tracks = listOf(track), currentIndex = 0)
        _sessionState.update { it.copy(queue = queue, errorMessage = null) }
        playerEngine.playTracks(server = server, tracks = listOf(track), startIndex = 0)
    }

    override fun togglePlayPause() {
        val current = _sessionState.value
        when {
            current.isPlaying -> playerEngine.pause()
            current.isPaused -> playerEngine.play()
            current.currentTrack != null -> {
                val server = current.activeServer ?: return
                playerEngine.playTracks(
                    server = server,
                    tracks = current.queue.tracks,
                    startIndex = current.queue.currentIndex.coerceAtLeast(0)
                )
            }
        }
    }

    override fun play() {
        val current = _sessionState.value
        if (current.isPaused) {
            playerEngine.play()
        } else if (current.currentTrack != null && current.activeServer != null) {
            playerEngine.playTracks(
                server = current.activeServer,
                tracks = current.queue.tracks,
                startIndex = current.queue.currentIndex.coerceAtLeast(0)
            )
        }
    }

    override fun pause() {
        playerEngine.pause()
    }

    override fun seekTo(positionMs: Long) {
        _sessionState.update { it.copy(currentPositionMs = positionMs) }
        playerEngine.seekTo(positionMs)
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
    }

    override fun removeQueueTrack(index: Int) {
        val currentQueue = _sessionState.value.queue
        if (index !in currentQueue.tracks.indices) return

        val updatedQueue = currentQueue.removeTrackAt(index)
        playerEngine.removeTrack(index)

        if (updatedQueue.isEmpty) {
            _sessionState.update {
                it.copy(
                    queue = PlaybackQueue.EMPTY,
                    playbackState = PlaybackState.Idle,
                    currentPositionMs = 0L,
                    durationMs = 0L
                )
            }
        } else {
            _sessionState.update {
                it.copy(queue = updatedQueue)
            }
        }
    }

    override fun stop() {
        playerEngine.stop()
    }

    override fun release() {
        playerEngine.release()
    }
}
