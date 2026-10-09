package com.webdav.player.data.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.session.MediaSession
import com.webdav.player.MainActivity
import com.webdav.player.data.service.WebDavMediaSessionCallback
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

data class StreamingLoadControlConfig(
    val backBufferDurationMs: Int = 10_000,
    val retainBackBufferFromKeyframe: Boolean = false,
    val minBufferMs: Int = 15_000,
    val maxBufferMs: Int = 50_000,
    val bufferForPlaybackMs: Int = 1_500,
    val bufferForPlaybackAfterRebufferMs: Int = 3_000,
    val prioritizeTimeOverSizeThresholds: Boolean = true,
)

@OptIn(UnstableApi::class)
class Media3AudioPlayerEngine(
    private val context: Context,
    val mediaSourceAdapter: WebDavMediaSourceAdapter = DefaultWebDavMediaSourceAdapter(context),
    customPlayer: Player? = null,
    customMediaSession: MediaSession? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    val loadControlConfig: StreamingLoadControlConfig = StreamingLoadControlConfig(),
) : AudioPlayerEngine {
    private val activeServerRef = AtomicReference<WebDavServer?>(null)
    private var loadedTracks: List<AudioTrack> = emptyList()
    private val prerollTargetTrackIndex = java.util.concurrent.atomic.AtomicInteger(-1)

    val activeServer: WebDavServer?
        get() = activeServerRef.get()

    private val defaultDataSourceFactory = DefaultDataSource.Factory(context)

    @VisibleForTesting
    val delegatingDataSourceFactory: DataSource.Factory =
        DataSource.Factory {
            val server = activeServerRef.get()
            if (server != null) {
                mediaSourceAdapter.getDataSourceFactory(server).createDataSource()
            } else {
                defaultDataSourceFactory.createDataSource()
            }
        }

    val player: Player =
        customPlayer ?: run {
            val loadControl =
                DefaultLoadControl
                    .Builder()
                    .setBackBuffer(
                        loadControlConfig.backBufferDurationMs,
                        loadControlConfig.retainBackBufferFromKeyframe,
                    ).setBufferDurationsMs(
                        loadControlConfig.minBufferMs,
                        loadControlConfig.maxBufferMs,
                        loadControlConfig.bufferForPlaybackMs,
                        loadControlConfig.bufferForPlaybackAfterRebufferMs,
                    ).setPrioritizeTimeOverSizeThresholds(loadControlConfig.prioritizeTimeOverSizeThresholds)
                    .build()

            val renderersFactory =
                object : DefaultRenderersFactory(context) {
                    override fun buildAudioSink(
                        context: Context,
                        enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean,
                    ): AudioSink? =
                        DefaultAudioSink
                            .Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .build()
                }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            val extractorsFactory =
                ExtractorsFactory {
                    arrayOf(
                        *DefaultExtractorsFactory().createExtractors(),
                        AsfExtractor(),
                    )
                }

            val mediaSourceFactory =
                DefaultMediaSourceFactory(delegatingDataSourceFactory, extractorsFactory)
                    .setLoadErrorHandlingPolicy(WebDavLoadErrorHandlingPolicy())

            ExoPlayer
                .Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    // handleAudioFocus =
                    false, // Audio focus is exclusively managed by audioFocusHandler
                ).build()
        }

    private val pendingEnrichedItems = ConcurrentHashMap<Int, MediaItem>()

    private val activeTrackMetadataRef = AtomicReference<MediaMetadata?>(null)
    private val sessionListeners = CopyOnWriteArrayList<Player.Listener>()
    private val skipHandlerRef = AtomicReference<AudioPlayerEngine.SkipHandler?>(null)

    override fun setSkipHandler(handler: AudioPlayerEngine.SkipHandler?) {
        skipHandlerRef.set(handler)
    }

    val sessionPlayer: Player =
        object : ForwardingPlayer(player) {
            override fun addListener(listener: Player.Listener) {
                sessionListeners.add(listener)
                super.addListener(listener)
            }

            override fun removeListener(listener: Player.Listener) {
                sessionListeners.remove(listener)
                super.removeListener(listener)
            }

            override fun getMediaMetadata(): MediaMetadata {
                val active = activeTrackMetadataRef.get()
                return if (active != null && active != MediaMetadata.EMPTY) {
                    active
                } else {
                    super.getMediaMetadata()
                }
            }

            override fun seekToNext() {
                this@Media3AudioPlayerEngine.skipToNext()
            }

            override fun seekToNextMediaItem() {
                this@Media3AudioPlayerEngine.skipToNext()
            }

            override fun seekToPrevious() {
                this@Media3AudioPlayerEngine.skipToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                this@Media3AudioPlayerEngine.skipToPrevious()
            }

            override fun isCommandAvailable(command: Int): Boolean =
                if (player.mediaItemCount > 0 && (
                        command == Player.COMMAND_SEEK_TO_NEXT ||
                            command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                            command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                            command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    )
                ) {
                    true
                } else {
                    super.isCommandAvailable(command)
                }

            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands().buildUpon()
                if (player.mediaItemCount > 0) {
                    commands.addAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                }
                return commands.build()
            }
        }

    val mediaSession: MediaSession =
        customMediaSession ?: run {
            val sessionActivity =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

            MediaSession
                .Builder(context, sessionPlayer)
                .setId("WebDavPlayerSession_${System.currentTimeMillis()}_${(1..99999).random()}")
                .setSessionActivity(sessionActivity)
                .setCallback(WebDavMediaSessionCallback(this))
                .build()
        }

    val audioFocusHandler: AudioFocusHandler = AudioFocusHandler(context, this)

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override fun getVolume(): Float = player.volume

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    override val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.LIST_LOOP)
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private var tickerJob: Job? = null

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updatePlaybackState()
                updatePositionAndDuration()
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                    updatePositionAndDuration()
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                val oldIndex = _currentTrackIndex.value
                val playerIndex = player.currentMediaItemIndex

                if (playerIndex == 1 && player.mediaItemCount >= 2) {
                    val targetIndex = prerollTargetTrackIndex.getAndSet(-1)
                    val nextTrackIndex =
                        if (targetIndex >= 0) {
                            targetIndex
                        } else if (_playbackMode.value == PlaybackMode.SHUFFLE && loadedTracks.size > 1) {
                            (loadedTracks.indices.filter { it != oldIndex }.randomOrNull()) ?: 0
                        } else {
                            if (oldIndex < loadedTracks.lastIndex) oldIndex + 1 else 0
                        }
                    _currentTrackIndex.value = nextTrackIndex
                    player.removeMediaItem(0)
                    commitPendingEnrichedItem(oldIndex)
                    activeTrackMetadataRef.set(null)
                    player.playlistMetadata = MediaMetadata.EMPTY
                    updatePositionAndDuration()
                    return
                }

                val newIndex =
                    if (loadedTracks.isNotEmpty()) {
                        _currentTrackIndex.value
                    } else {
                        player.currentMediaItemIndex
                    }
                if (oldIndex != newIndex) {
                    commitPendingEnrichedItem(oldIndex)
                    activeTrackMetadataRef.set(null)
                    player.playlistMetadata = MediaMetadata.EMPTY
                }
                _currentTrackIndex.value = newIndex
                updatePositionAndDuration()
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackState.value =
                    PlaybackState.Error(
                        error.localizedMessage ?: "Playback error (${error.errorCodeName})",
                    )
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                updatePositionAndDuration()
            }
        }

    init {
        player.addListener(listener)
        applyPlaybackMode(_playbackMode.value)
        updatePlaybackState()
    }

    override fun playTracks(
        server: WebDavServer,
        tracks: List<AudioTrack>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        if (tracks.isEmpty()) return

        pendingEnrichedItems.clear()
        activeTrackMetadataRef.set(null)
        player.playlistMetadata = MediaMetadata.EMPTY
        activeServerRef.set(server)
        loadedTracks = tracks

        val validStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
        _currentTrackIndex.value = validStartIndex
        _currentPositionMs.value = startPositionMs
        applyPlaybackMode(_playbackMode.value)

        val activeTrack = tracks[validStartIndex]
        if (player is ExoPlayer) {
            val mediaSource = mediaSourceAdapter.createMediaSource(server, activeTrack)
            player.setMediaSource(mediaSource, startPositionMs)
        } else {
            val mediaItem = mediaSourceAdapter.createMediaItem(server, activeTrack)
            player.setMediaItem(mediaItem, startPositionMs)
        }
        player.prepare()
        audioFocusHandler.requestAudioFocus()
        player.play()
    }

    override fun play() {
        audioFocusHandler.requestAudioFocus()
        player.play()
    }

    override fun pause() {
        updatePositionAndDuration()
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        player.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    override fun skipToNext() {
        if (skipHandlerRef.get()?.onSkipToNext() == true) {
            return
        }
        if (loadedTracks.isEmpty()) return
        val current = _currentTrackIndex.value
        val next =
            when (_playbackMode.value) {
                PlaybackMode.SINGLE_LOOP -> current
                PlaybackMode.LIST_LOOP -> if (current < loadedTracks.lastIndex) current + 1 else 0
                PlaybackMode.SHUFFLE -> (loadedTracks.indices.filter { it != current }.randomOrNull()) ?: 0
            }
        val server = activeServerRef.get() ?: return
        val nextTrack = loadedTracks[next]
        _currentTrackIndex.value = next
        _currentPositionMs.value = 0L
        if (player is ExoPlayer) {
            val mediaSource = mediaSourceAdapter.createMediaSource(server, nextTrack)
            player.setMediaSource(mediaSource, 0L)
        } else {
            val mediaItem = mediaSourceAdapter.createMediaItem(server, nextTrack)
            player.setMediaItem(mediaItem, 0L)
        }
        player.prepare()
        player.play()
    }

    override fun skipToPrevious() {
        if (skipHandlerRef.get()?.onSkipToPrevious() == true) {
            return
        }
        if (loadedTracks.isEmpty()) return
        val current = _currentTrackIndex.value
        val prev =
            when (_playbackMode.value) {
                PlaybackMode.SINGLE_LOOP -> current
                PlaybackMode.LIST_LOOP, PlaybackMode.SHUFFLE -> if (current > 0) current - 1 else loadedTracks.lastIndex
            }
        val server = activeServerRef.get() ?: return
        val prevTrack = loadedTracks[prev]
        _currentTrackIndex.value = prev
        _currentPositionMs.value = 0L
        if (player is ExoPlayer) {
            val mediaSource = mediaSourceAdapter.createMediaSource(server, prevTrack)
            player.setMediaSource(mediaSource, 0L)
        } else {
            val mediaItem = mediaSourceAdapter.createMediaItem(server, prevTrack)
            player.setMediaItem(mediaItem, 0L)
        }
        player.prepare()
        player.play()
    }

    override fun seekToTrack(
        index: Int,
        positionMs: Long,
    ) {
        if (index in loadedTracks.indices) {
            val server = activeServerRef.get() ?: return
            val track = loadedTracks[index]
            _currentTrackIndex.value = index
            val clamped = positionMs.coerceAtLeast(0L)
            _currentPositionMs.value = clamped
            if (player is ExoPlayer) {
                val mediaSource = mediaSourceAdapter.createMediaSource(server, track)
                player.setMediaSource(mediaSource, clamped)
            } else {
                val mediaItem = mediaSourceAdapter.createMediaItem(server, track)
                player.setMediaItem(mediaItem, clamped)
            }
            player.prepare()
            player.play()
        } else if (player.mediaItemCount == 1) {
            player.seekTo(0, positionMs.coerceAtLeast(0L))
        }
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
        applyPlaybackMode(mode)
    }

    override fun removeTrack(index: Int) {
        if (index in loadedTracks.indices) {
            val updated = loadedTracks.toMutableList().apply { removeAt(index) }
            loadedTracks = updated
            if (updated.isEmpty()) {
                stop()
            } else {
                val currentIndex = _currentTrackIndex.value
                val newIndex =
                    when {
                        index < currentIndex -> currentIndex - 1
                        index == currentIndex -> if (index < updated.size) index else updated.lastIndex
                        else -> currentIndex
                    }
                _currentTrackIndex.value = newIndex
                if (index == currentIndex) {
                    seekToTrack(newIndex, 0L)
                }
            }
        }
    }

    override fun prerollTrack(
        server: WebDavServer,
        track: AudioTrack,
        targetTrackIndex: Int,
    ) {
        if (player.mediaItemCount >= 2) return
        prerollTargetTrackIndex.set(targetTrackIndex)
        if (player is ExoPlayer) {
            val mediaSource = mediaSourceAdapter.createMediaSource(server, track)
            player.addMediaSource(mediaSource)
        } else {
            val mediaItem = mediaSourceAdapter.createMediaItem(server, track)
            player.addMediaItem(mediaItem)
        }
    }

    override fun clearPrerollTrack() {
        prerollTargetTrackIndex.set(-1)
        if (player.mediaItemCount > 1) {
            player.removeMediaItem(1)
        }
    }

    override fun insertTrack(
        index: Int,
        server: WebDavServer,
        track: AudioTrack,
    ) {
        if (activeServerRef.get() == null || player.mediaItemCount == 0) {
            activeServerRef.set(server)
        }
        if (index in 0..player.mediaItemCount) {
            if (player is ExoPlayer) {
                val mediaSource = mediaSourceAdapter.createMediaSource(server, track)
                player.addMediaSource(index, mediaSource)
            } else {
                val mediaItem = mediaSourceAdapter.createMediaItem(server, track)
                player.addMediaItem(index, mediaItem)
            }
        }
    }

    override fun updateTrack(
        index: Int,
        track: AudioTrack,
    ) {
        if (index in loadedTracks.indices) {
            val mutable = loadedTracks.toMutableList()
            mutable[index] = track
            loadedTracks = mutable
        }

        val isCurrentTrack = index == _currentTrackIndex.value
        if (isCurrentTrack && player.mediaItemCount > 0) {
            val currentItem = player.getMediaItemAt(0)
            val currentMeta = currentItem.mediaMetadata

            val isActivelyPlayingOrBuffering =
                _playbackState.value is PlaybackState.Playing ||
                    _playbackState.value is PlaybackState.Buffering ||
                    player.isPlaying ||
                    player.playbackState == Player.STATE_BUFFERING ||
                    (player.playbackState == Player.STATE_READY && player.playWhenReady)

            val effectiveMeta =
                if (player.playlistMetadata != MediaMetadata.EMPTY) {
                    player.playlistMetadata
                } else {
                    currentMeta
                }

            val newArtworkUri = track.coverThumbnailPath?.let { Uri.fromFile(File(it)) }
            val sameTitle = effectiveMeta.title?.toString() == track.title
            val sameArtist = (effectiveMeta.artist?.toString() ?: "") == (track.artist ?: "")
            val sameAlbum = (effectiveMeta.albumTitle?.toString() ?: "") == (track.album ?: "")
            val sameArtwork = effectiveMeta.artworkUri == newArtworkUri

            if (sameTitle && sameArtist && sameAlbum && sameArtwork) {
                return // Metadata identical; bypass to prevent redundant churn
            }

            val metaBuilder =
                currentItem.mediaMetadata
                    .buildUpon()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
            if (newArtworkUri != null) {
                metaBuilder.setArtworkUri(newArtworkUri)
            } else if (currentItem.mediaMetadata.artworkUri != null) {
                metaBuilder.setArtworkUri(null)
            }
            val updatedMetadata = metaBuilder.build()
            val updatedItem =
                currentItem
                    .buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()

            if (isActivelyPlayingOrBuffering) {
                activeTrackMetadataRef.set(updatedMetadata)
                player.playlistMetadata = updatedMetadata
                pendingEnrichedItems[0] = updatedItem

                for (l in sessionListeners) {
                    try {
                        l.onMediaMetadataChanged(updatedMetadata)
                    } catch (_: Throwable) {
                    }
                }
            } else {
                pendingEnrichedItems.remove(0)
                player.replaceMediaItem(0, updatedItem)
            }
        }
    }

    private fun commitPendingEnrichedItem(index: Int) {
        val pendingItem = pendingEnrichedItems.remove(index)
        if (pendingItem != null && index in 0 until player.mediaItemCount) {
            try {
                player.replaceMediaItem(index, pendingItem)
            } catch (_: Throwable) {
            }
        }
    }

    override fun stop() {
        val currentIndex = _currentTrackIndex.value
        commitPendingEnrichedItem(currentIndex)
        activeTrackMetadataRef.set(null)
        audioFocusHandler.abandonAudioFocus()
        stopPositionTicker()
        player.stop()
        player.playlistMetadata = MediaMetadata.EMPTY
        _playbackState.value = PlaybackState.Idle
        _currentPositionMs.value = 0L
        _bufferedPositionMs.value = 0L
    }

    override fun release() {
        pendingEnrichedItems.clear()
        activeTrackMetadataRef.set(null)
        sessionListeners.clear()
        audioFocusHandler.abandonAudioFocus()
        stopPositionTicker()
        player.removeListener(listener)
        mediaSession.release()
        player.release()
    }

    private fun applyPlaybackMode(mode: PlaybackMode) {
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
    }

    private fun updatePlaybackState() {
        val state =
            when (player.playbackState) {
                Player.STATE_IDLE -> {
                    val error = player.playerError
                    if (error != null) {
                        PlaybackState.Error(error.localizedMessage ?: "Playback error")
                    } else {
                        PlaybackState.Idle
                    }
                }

                Player.STATE_BUFFERING -> {
                    PlaybackState.Buffering
                }

                Player.STATE_READY -> {
                    if (player.playWhenReady) {
                        PlaybackState.Playing
                    } else {
                        PlaybackState.Paused
                    }
                }

                Player.STATE_ENDED -> {
                    PlaybackState.Ended
                }

                else -> {
                    PlaybackState.Idle
                }
            }
        _playbackState.value = state
    }

    private fun updatePositionAndDuration() {
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration
        if (dur != C.TIME_UNSET && dur > 0) {
            _durationMs.value = dur
        }
        _bufferedPositionMs.value = player.bufferedPosition.coerceAtLeast(0L)
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob =
            coroutineScope.launch {
                while (isActive) {
                    updatePositionAndDuration()
                    delay(300)
                }
            }
    }

    private fun stopPositionTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
