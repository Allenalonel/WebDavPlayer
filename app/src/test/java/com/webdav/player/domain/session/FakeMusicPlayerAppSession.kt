package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMusicPlayerAppSession(
    initialState: PlayerSessionState = PlayerSessionState()
) : MusicPlayerAppSession {

    val _sessionState = MutableStateFlow(initialState)
    override val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    var lastPlayDirectory: RemoteDirectory? = null
    var lastPlaySelectedFile: RemoteFile? = null
    var togglePlayPauseCount = 0
    var playCount = 0
    var pauseCount = 0
    var stopCount = 0

    override fun setActiveServer(server: WebDavServer?) {
        _sessionState.value = _sessionState.value.copy(activeServer = server)
    }

    override fun playDirectoryTrack(directory: RemoteDirectory, selectedFile: RemoteFile) {
        lastPlayDirectory = directory
        lastPlaySelectedFile = selectedFile
    }

    override fun playTrack(track: AudioTrack) {}

    override fun togglePlayPause() {
        togglePlayPauseCount++
    }

    override fun play() {
        playCount++
    }

    override fun pause() {
        pauseCount++
    }

    override fun seekTo(positionMs: Long) {}

    override fun skipToNext() {}

    override fun skipToPrevious() {}

    override fun playQueueIndex(index: Int) {}

    override fun stop() {
        stopCount++
    }

    override fun release() {}
}
