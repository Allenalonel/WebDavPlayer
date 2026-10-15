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

    val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    var lastServer: WebDavServer? = null
    var loadedTrack: AudioTrack? = null
    var lastStartPositionMs: Long = 0L
    var loadTrackCount = 0
    val playTracksCount: Int get() = loadTrackCount
    val lastTracks: List<AudioTrack> get() = loadedTrack?.let { listOf(it) } ?: emptyList()
    var lastStartIndex: Int = -1

    var playCount = 0
    var pauseCount = 0
    var stopCount = 0
    var seekToPosition: Long? = null
    var released = false
    private var currentVolume: Float = 1.0f

    var isPrerollActive: Boolean = false
    var prerolledTrack: AudioTrack? = null
    var clearPrerollCount = 0

    var updateTrackMetadataCalls = 0
    var lastUpdatedTrack: AudioTrack? = null

    override fun loadTrack(
        server: WebDavServer,
        track: AudioTrack,
        startPositionMs: Long,
    ) {
        loadTrackCount++
        lastServer = server
        loadedTrack = track
        lastStartIndex = 0
        lastStartPositionMs = startPositionMs
        _currentPositionMs.value = startPositionMs
        _durationMs.value = track.durationMs
        _playbackState.value = PlaybackState.Playing
    }

    override fun prerollTrack(
        server: WebDavServer,
        track: AudioTrack,
    ) {
        isPrerollActive = true
        prerolledTrack = track
    }

    override fun clearPrerollTrack() {
        isPrerollActive = false
        prerolledTrack = null
        clearPrerollCount++
    }

    override fun updateTrackMetadata(track: AudioTrack) {
        updateTrackMetadataCalls++
        lastUpdatedTrack = track
        loadedTrack = track
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

    fun simulateTrackCompletion() {
        val curDur = _durationMs.value
        val dur = if (curDur > 0L) curDur else (loadedTrack?.durationMs ?: 0L)
        if (dur > 0L) {
            _currentPositionMs.value = dur
        }
        _playbackState.value = PlaybackState.Ended
    }

    override fun stop() {
        stopCount++
        _playbackState.value = PlaybackState.Idle
        _currentPositionMs.value = 0L
        _bufferedPositionMs.value = 0L
    }

    override fun release() {
        released = true
    }

    override fun setVolume(volume: Float) {
        this.currentVolume = volume
    }

    override fun getVolume(): Float = currentVolume
}
