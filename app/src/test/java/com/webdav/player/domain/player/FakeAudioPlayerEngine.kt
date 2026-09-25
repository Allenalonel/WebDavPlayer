package com.webdav.player.domain.player

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
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

    val _playbackMode = MutableStateFlow(PlaybackMode.LIST_LOOP)
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    var lastServer: WebDavServer? = null
    var lastTracks: List<AudioTrack> = emptyList()
    var lastStartIndex: Int = -1
    var playCount = 0
    var pauseCount = 0
    var stopCount = 0
    var seekToPosition: Long? = null
    var released = false

    var shufflePermutation: List<Int>? = null

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
        if (lastTracks.isEmpty()) return
        val next = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP, PlaybackMode.LIST_LOOP -> {
                if (_currentTrackIndex.value < lastTracks.size - 1) {
                    _currentTrackIndex.value + 1
                } else {
                    0
                }
            }
            PlaybackMode.SHUFFLE -> {
                val perm = shufflePermutation
                if (!perm.isNullOrEmpty()) {
                    val currPos = perm.indexOf(_currentTrackIndex.value)
                    if (currPos in 0 until perm.lastIndex) {
                        perm[currPos + 1]
                    } else {
                        perm.first()
                    }
                } else {
                    (lastTracks.indices.filter { it != _currentTrackIndex.value }.randomOrNull()) ?: 0
                }
            }
        }
        _currentTrackIndex.value = next
        _currentPositionMs.value = 0L
    }

    override fun skipToPrevious() {
        if (lastTracks.isEmpty()) return
        val prev = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP, PlaybackMode.LIST_LOOP -> {
                if (_currentTrackIndex.value > 0) {
                    _currentTrackIndex.value - 1
                } else {
                    lastTracks.lastIndex
                }
            }
            PlaybackMode.SHUFFLE -> {
                val perm = shufflePermutation
                if (!perm.isNullOrEmpty()) {
                    val currPos = perm.indexOf(_currentTrackIndex.value)
                    if (currPos > 0) {
                        perm[currPos - 1]
                    } else {
                        perm.last()
                    }
                } else {
                    (lastTracks.indices.filter { it != _currentTrackIndex.value }.randomOrNull()) ?: 0
                }
            }
        }
        _currentTrackIndex.value = prev
        _currentPositionMs.value = 0L
    }

    override fun seekToTrack(index: Int, positionMs: Long) {
        if (index in lastTracks.indices) {
            _currentTrackIndex.value = index
            _currentPositionMs.value = positionMs
        }
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
    }

    override fun removeTrack(index: Int) {
        if (index in lastTracks.indices) {
            val mutable = lastTracks.toMutableList().apply { removeAt(index) }
            lastTracks = mutable
            if (mutable.isEmpty()) {
                stop()
                _currentTrackIndex.value = -1
            } else {
                val newIndex = when {
                    index < _currentTrackIndex.value -> _currentTrackIndex.value - 1
                    index == _currentTrackIndex.value -> {
                        if (index < mutable.size) index else mutable.lastIndex
                    }
                    else -> _currentTrackIndex.value
                }
                _currentTrackIndex.value = newIndex
            }
        }
    }

    override fun updateTrack(index: Int, track: AudioTrack) {
        if (index in lastTracks.indices) {
            val mutable = lastTracks.toMutableList()
            mutable[index] = track
            lastTracks = mutable
        }
    }

    fun simulateTrackCompletion() {
        if (lastTracks.isEmpty()) return
        when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> {
                // Replays the same track
                _currentPositionMs.value = 0L
            }
            PlaybackMode.LIST_LOOP, PlaybackMode.SHUFFLE -> {
                skipToNext()
            }
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
