package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeMusicPlayerAppSession(
    initialState: PlayerSessionState = PlayerSessionState()
) : MusicPlayerAppSession {

    val _sessionState = MutableStateFlow(initialState)
    override val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    private val _isRestored = MutableStateFlow(true)
    override val isRestored: StateFlow<Boolean> = _isRestored.asStateFlow()

    val serverLastDirectories = mutableMapOf<Long, String>()

    override fun getLastDirectoryForServer(serverId: Long): String {
        return serverLastDirectories[serverId] ?: "/"
    }

    var lastPlayDirectory: RemoteDirectory? = null
    var lastPlaySelectedFile: RemoteFile? = null
    var togglePlayPauseCount = 0
    var playCount = 0
    var pauseCount = 0
    var stopCount = 0
    var lastSeekPosition: Long? = null
    var skipNextCount = 0
    var skipPreviousCount = 0
    var lastPlayedQueueIndex: Int? = null
    var removedQueueIndices = mutableListOf<Int>()

    override fun setActiveServer(server: WebDavServer?) {
        _sessionState.value = _sessionState.value.copy(activeServer = server)
    }

    override fun setCurrentDirectoryPath(path: String) {
        val serverId = _sessionState.value.activeServer?.id
        if (serverId != null) {
            serverLastDirectories[serverId] = path
        }
        _sessionState.update { it.copy(currentDirectoryPath = path) }
    }

    var restoreSessionCount = 0
    var flushSessionCount = 0

    override suspend fun restoreSession() {
        restoreSessionCount++
    }

    override suspend fun flushSession() {
        flushSessionCount++
    }

    override fun flushSessionAsync() {
        flushSessionCount++
    }

    var lastPlayTrack: AudioTrack? = null
    var lastPlayNextTrack: AudioTrack? = null

    override fun playDirectoryTrack(directory: RemoteDirectory, selectedFile: RemoteFile) {
        lastPlayDirectory = directory
        lastPlaySelectedFile = selectedFile
    }

    override fun playTrack(track: AudioTrack) {
        lastPlayTrack = track
        val queue = PlaybackQueue(tracks = listOf(track), currentIndex = 0)
        _sessionState.update { it.copy(queue = queue) }
    }

    override fun playNext(track: AudioTrack) {
        lastPlayNextTrack = track
        val currentQueue = _sessionState.value.queue
        val updatedQueue = currentQueue.insertNext(track)
        _sessionState.update { it.copy(queue = updatedQueue) }
    }

    override fun togglePlayPause() {
        togglePlayPauseCount++
    }

    override fun play() {
        playCount++
    }

    override fun pause() {
        pauseCount++
    }

    override fun seekTo(positionMs: Long) {
        lastSeekPosition = positionMs
        _sessionState.update { it.copy(currentPositionMs = positionMs) }
    }

    override fun skipToNext() {
        skipNextCount++
    }

    override fun skipToPrevious() {
        skipPreviousCount++
    }

    override fun playQueueIndex(index: Int) {
        lastPlayedQueueIndex = index
    }

    override fun cyclePlaybackMode() {
        val next = _sessionState.value.playbackMode.next()
        setPlaybackMode(next)
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        _sessionState.update { it.copy(playbackMode = mode) }
    }

    override fun removeQueueTrack(index: Int) {
        removedQueueIndices.add(index)
        val updated = _sessionState.value.queue.removeTrackAt(index)
        _sessionState.update { it.copy(queue = updated) }
    }

    override fun stop() {
        stopCount++
    }

    override fun release() {}
}
