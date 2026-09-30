package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.StateFlow

interface MusicPlayerAppSession {
    val sessionState: StateFlow<PlayerSessionState>

    val playbackProgress: StateFlow<PlaybackProgress>
        get() = kotlinx.coroutines.flow.MutableStateFlow(PlaybackProgress.ZERO)

    val isRestored: StateFlow<Boolean>
        get() = kotlinx.coroutines.flow.MutableStateFlow(true)

    fun getLastDirectoryForServer(serverId: Long): String = "/"

    fun setActiveServer(server: WebDavServer?)

    fun setCurrentDirectoryPath(path: String)

    suspend fun restoreSession()

    suspend fun flushSession()

    fun flushSessionAsync() {}

    fun playDirectoryTrack(
        directory: RemoteDirectory,
        selectedFile: RemoteFile,
        initialMetadata: Map<String, TrackMetadata> = emptyMap(),
    )

    fun playTrack(track: AudioTrack)

    fun playNext(track: AudioTrack)

    fun togglePlayPause()

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun skipToNext()

    fun skipToPrevious()

    fun playQueueIndex(index: Int)

    fun cyclePlaybackMode()

    fun setPlaybackMode(mode: PlaybackMode)

    fun removeQueueTrack(index: Int)

    fun stop()

    fun release()
}
