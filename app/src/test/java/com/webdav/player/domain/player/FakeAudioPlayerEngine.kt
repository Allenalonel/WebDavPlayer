package com.webdav.player.domain.player

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAudioPlayerEngine : AudioPlayerEngine {

    val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    val _currentTrackIndex = MutableStateFlow(-1)
    override val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    var lastServer: WebDavServer? = null
    var lastTracks: List<AudioTrack> = emptyList()
    var lastStartIndex: Int = -1
    var playCount = 0
    var pauseCount = 0
    var stopCount = 0
    var seekToPosition: Long? = null
    var released = false

    override fun playTracks(
        server: WebDavServer,
        tracks: List<AudioTrack>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        lastServer = server
        lastTracks = tracks
        lastStartIndex = startIndex
        _currentTrackIndex.value = startIndex
        _currentPositionMs.value = startPositionMs
        _playbackState.value = PlaybackState.Playing
    }

    override fun play() {
        playCount++
        _playbackState.value = PlaybackState.Playing
    }

    override fun pause() {
        pauseCount++
        _playbackState.value = PlaybackState.Paused
    }

    override fun seekTo(positionMs: Long) {
        seekToPosition = positionMs
        _currentPositionMs.value = positionMs
    }

    override fun skipToNext() {
        if (_currentTrackIndex.value < lastTracks.size - 1) {
            _currentTrackIndex.value++
        }
    }

    override fun skipToPrevious() {
        if (_currentTrackIndex.value > 0) {
            _currentTrackIndex.value--
        }
    }

    override fun seekToTrack(index: Int, positionMs: Long) {
        if (index in lastTracks.indices) {
            _currentTrackIndex.value = index
            _currentPositionMs.value = positionMs
        }
    }

    override fun stop() {
        stopCount++
        _playbackState.value = PlaybackState.Idle
    }

    override fun release() {
        released = true
    }
}
