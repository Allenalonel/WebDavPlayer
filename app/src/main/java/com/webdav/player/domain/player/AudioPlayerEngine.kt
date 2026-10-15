package com.webdav.player.domain.player

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerEngine {
    val playbackState: StateFlow<PlaybackState>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val bufferedPositionMs: StateFlow<Long>
    val trackTransitions: SharedFlow<Unit> get() = MutableSharedFlow()

    fun loadTrack(
        server: WebDavServer,
        track: AudioTrack,
        startPositionMs: Long = 0L,
    )

    fun prerollTrack(
        server: WebDavServer,
        track: AudioTrack,
    )

    fun clearPrerollTrack()

    fun updateTrackMetadata(track: AudioTrack)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun stop()

    fun release()

    fun setVolume(volume: Float)

    fun getVolume(): Float
}
