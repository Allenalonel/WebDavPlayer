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
                    )
                    .setBufferDurationsMs(
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
                    ): AudioSink? {
                        return DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .build()
                    }
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
                val newIndex = player.currentMediaItemIndex
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

        val validStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
        _currentTrackIndex.value = validStartIndex
        _currentPositionMs.value = startPositionMs
        applyPlaybackMode(_playbackMode.value)

        val mediaSources = mediaSourceAdapter.createMediaSources(server, tracks)
        if (player is ExoPlayer) {
            player.setMediaSources(mediaSources, validStartIndex, startPositionMs)
        } else {
            val mediaItems = tracks.map { track -> mediaSourceAdapter.createMediaItem(server, track) }
            player.setMediaItems(mediaItems, validStartIndex, startPositionMs)
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
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
        }
    }

    override fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else if (player.mediaItemCount > 0) {
            player.seekTo(player.mediaItemCount - 1, 0L)
        }
    }

    override fun seekToTrack(
        index: Int,
        positionMs: Long,
    ) {
        if (index >= 0 && index < player.mediaItemCount) {
            player.seekTo(index, positionMs.coerceAtLeast(0L))
        }
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
        applyPlaybackMode(mode)
    }

    override fun removeTrack(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
            if (player.mediaItemCount == 0) {
                stop()
            } else {
                _currentTrackIndex.value = player.currentMediaItemIndex
                updatePositionAndDuration()
            }
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
        if (index in 0 until player.mediaItemCount) {
            val currentItem = player.getMediaItemAt(index)
            val currentMeta = currentItem.mediaMetadata

            val isCurrentTrack = index == player.currentMediaItemIndex
            val isActivelyPlayingOrBuffering =
                isCurrentTrack && (
                    _playbackState.value is PlaybackState.Playing ||
                        _playbackState.value is PlaybackState.Buffering ||
                        player.isPlaying ||
                        player.playbackState == Player.STATE_BUFFERING ||
                        (player.playbackState == Player.STATE_READY && player.playWhenReady)
                )

            val effectiveMeta =
                if (isCurrentTrack && player.playlistMetadata != MediaMetadata.EMPTY) {
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
                // Non-disruptive metadata update for actively playing or buffering track:
                // Update activeTrackMetadata and trigger MediaSession listeners so notification, lockscreen,
                // and presentation flows receive enriched tags immediately without destroying decoder pipeline.
                activeTrackMetadataRef.set(updatedMetadata)
                player.playlistMetadata = updatedMetadata
                pendingEnrichedItems[index] = updatedItem

                for (l in sessionListeners) {
                    try {
                        l.onMediaMetadataChanged(updatedMetadata)
                    } catch (_: Throwable) {
                    }
                }
            } else {
                pendingEnrichedItems.remove(index)
                // For non-playing queue items (upcoming or historical tracks, or when player is idle),
                // safely update the playlist item via player.replaceMediaItem() so subsequent track transitions pick up enriched metadata.
                player.replaceMediaItem(index, updatedItem)
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
        when (mode) {
            PlaybackMode.LIST_LOOP -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
            }

            PlaybackMode.SINGLE_LOOP -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
            }

            PlaybackMode.SHUFFLE -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = true
            }
        }
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
