package com.webdav.player.domain.player

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerEngine {
    val playbackState: StateFlow<PlaybackState>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val currentTrackIndex: StateFlow<Int>

    fun playTracks(
        server: WebDavServer,
        tracks: List<AudioTrack>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L
    )

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun seekToTrack(index: Int, positionMs: Long = 0L)
    fun stop()
    fun release()
}
