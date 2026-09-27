package com.webdav.player.domain.player

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerEngine {
    val playbackState: StateFlow<PlaybackState>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val bufferedPositionMs: StateFlow<Long>
        get() = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val currentTrackIndex: StateFlow<Int>
    val playbackMode: StateFlow<PlaybackMode>

    fun playTracks(
        server: WebDavServer,
        tracks: List<AudioTrack>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
    )

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun skipToNext()

    fun skipToPrevious()

    fun seekToTrack(
        index: Int,
        positionMs: Long = 0L,
    )

    fun setPlaybackMode(mode: PlaybackMode)

    fun removeTrack(index: Int)

    fun insertTrack(
        index: Int,
        server: WebDavServer,
        track: AudioTrack,
    ) {}

    fun updateTrack(
        index: Int,
        track: AudioTrack,
    ) {}

    fun stop()

    fun release()

    fun setVolume(volume: Float) {}

    fun getVolume(): Float = 1.0f
}
