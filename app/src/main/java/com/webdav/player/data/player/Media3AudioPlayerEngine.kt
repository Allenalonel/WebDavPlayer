package com.webdav.player.data.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
) {
    companion object {
        const val DEFAULT_MIN_BUFFER_MS: Int = 15_000
        const val DEFAULT_MAX_BUFFER_MS: Int = 50_000
        const val EXPANDED_WEAK_NETWORK_MIN_BUFFER_MS: Int = 20_000
        const val EXPANDED_WEAK_NETWORK_MAX_BUFFER_MS: Int = 90_000

        /**
         * Creates an expanded streaming load control configuration optimized for
         * weak networks and fluctuating bandwidth under pure-streaming zero-disk-cache constraints.
         */
        fun createWeakNetworkResilientConfig(): StreamingLoadControlConfig =
            StreamingLoadControlConfig(
                minBufferMs = EXPANDED_WEAK_NETWORK_MIN_BUFFER_MS,
                maxBufferMs = EXPANDED_WEAK_NETWORK_MAX_BUFFER_MS,
            )
    }
}

/**
 * Media3/ExoPlayer implementation of AudioPlayerEngine with transparent streaming resilience.
 */
@OptIn(UnstableApi::class)
class Media3AudioPlayerEngine(
    private val context: Context,
    val mediaSourceAdapter: WebDavMediaSourceAdapter = DefaultWebDavMediaSourceAdapter(context),
    customPlayer: Player? = null,
    customMediaSession: MediaSession? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    val loadControlConfig: StreamingLoadControlConfig = StreamingLoadControlConfig(),
    val hardwareLockManager: StreamingHardwareLockManager =
        (mediaSourceAdapter as? DefaultWebDavMediaSourceAdapter)?.hardwareLockManager
            ?: DefaultStreamingHardwareLockManager(context),
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
                .setWakeMode(C.WAKE_MODE_NETWORK)
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

            override fun seekToNext() {
                mediaSessionCallback.onSkipToNext()
            }

            override fun seekToNextMediaItem() {
                mediaSessionCallback.onSkipToNext()
            }

            override fun seekToPrevious() {
                mediaSessionCallback.onSkipToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                mediaSessionCallback.onSkipToPrevious()
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

    val mediaSessionCallback: WebDavMediaSessionCallback = WebDavMediaSessionCallback(this)

    fun setAppSession(appSession: MusicPlayerAppSession?) {
        mediaSessionCallback.appSession = appSession
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
                .setCallback(mediaSessionCallback)
                .build()
        }

    val audioFocusHandler: AudioFocusHandler = AudioFocusHandler(context, this)

    @Volatile
    private var currentVolume: Float = 1.0f

    private fun runOnApplicationLooper(action: () -> Unit) {
        val applicationLooper =
            try {
                player.applicationLooper
            } catch (_: Throwable) {
                null
            } ?: Looper.getMainLooper()
        if (applicationLooper == null || Looper.myLooper() == applicationLooper) {
            action()
        } else {
            Handler(applicationLooper).post(action)
        }
    }

    override fun setVolume(volume: Float) {
        val coerced = volume.coerceIn(0f, 1f)
        currentVolume = coerced
        runOnApplicationLooper {
            player.volume = coerced
        }
    }

    override fun getVolume(): Float = currentVolume

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _trackTransitions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val trackTransitions: SharedFlow<Unit> = _trackTransitions.asSharedFlow()

    private var tickerJob: Job? = null

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updatePositionAndDuration()
                updatePlaybackState()
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                updatePositionAndDuration()
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePositionAndDuration()
                updatePlaybackState()
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                val playerIndex = player.currentMediaItemIndex

                if (playerIndex == 1 && player.mediaItemCount >= 2) {
                    player.removeMediaItem(0)
                    commitPendingEnrichedItem(0)
                    activeTrackMetadataRef.set(null)
                    player.playlistMetadata = MediaMetadata.EMPTY
                    updatePositionAndDuration()
                    updatePlaybackState()
                    _trackTransitions.tryEmit(Unit)
                    return
                }

                commitPendingEnrichedItem(0)
                activeTrackMetadataRef.set(null)
                player.playlistMetadata = MediaMetadata.EMPTY
                updatePositionAndDuration()
                updatePlaybackState()
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackState.value = toPlaybackError(error)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                updatePositionAndDuration()
                updatePlaybackState()
            }

            override fun onTimelineChanged(
                timeline: androidx.media3.common.Timeline,
                reason: Int,
            ) {
                updatePositionAndDuration()
            }
        }

    init {
        player.addListener(listener)
        currentVolume =
            try {
                player.volume
            } catch (_: Throwable) {
                1.0f
            }
        runOnApplicationLooper {
            updatePositionAndDuration()
            updatePlaybackState()
        }
    }

    override fun loadTrack(
        server: WebDavServer,
        track: AudioTrack,
        startPositionMs: Long,
    ) {
        runOnApplicationLooper {
            pendingEnrichedItems.clear()
            activeTrackMetadataRef.set(null)
            player.playlistMetadata = MediaMetadata.EMPTY
            activeServerRef.set(server)
            _currentPositionMs.value = startPositionMs
            _durationMs.value = if (track.durationMs > 0L) track.durationMs else 0L

            if (player is ExoPlayer) {
                val mediaSource = mediaSourceAdapter.createMediaSource(server, track)
                player.setMediaSource(mediaSource, startPositionMs)
            } else {
                val mediaItem = mediaSourceAdapter.createMediaItem(server, track)
                player.setMediaItem(mediaItem, startPositionMs)
            }
            player.prepare()
            audioFocusHandler.requestAudioFocus()
            player.play()
        }
    }

    override fun play() {
        runOnApplicationLooper {
            audioFocusHandler.requestAudioFocus()
            player.play()
        }
    }

    override fun pause() {
        runOnApplicationLooper {
            updatePositionAndDuration()
            player.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        runOnApplicationLooper {
            val clamped = positionMs.coerceAtLeast(0L)
            player.seekTo(clamped)
            _currentPositionMs.value = clamped
        }
    }

    override fun prerollTrack(
        server: WebDavServer,
        track: AudioTrack,
    ) {
        runOnApplicationLooper {
            if (player.mediaItemCount >= 2) return@runOnApplicationLooper
            if (player is ExoPlayer) {
                val mediaSource = mediaSourceAdapter.createMediaSource(server, track)
                player.addMediaSource(mediaSource)
            } else {
                val mediaItem = mediaSourceAdapter.createMediaItem(server, track)
                player.addMediaItem(mediaItem)
            }
        }
    }

    override fun clearPrerollTrack() {
        runOnApplicationLooper {
            if (player.mediaItemCount > 1) {
                player.removeMediaItem(1)
            }
        }
    }

    override fun updateTrackMetadata(track: AudioTrack) {
        runOnApplicationLooper {
            applyActiveTrackMetadataUpdate(track)
        }
    }

    private fun applyActiveTrackMetadataUpdate(track: AudioTrack) {
        if (player.mediaItemCount <= 0) return

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
        runOnApplicationLooper {
            hardwareLockManager.releaseLocks()
            commitPendingEnrichedItem(0)
            activeTrackMetadataRef.set(null)
            audioFocusHandler.abandonAudioFocus()
            stopPositionTicker()
            player.stop()
            player.playlistMetadata = MediaMetadata.EMPTY
            _playbackState.value = PlaybackState.Idle
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
            _bufferedPositionMs.value = 0L
        }
    }

    override fun release() {
        runOnApplicationLooper {
            hardwareLockManager.release()
            pendingEnrichedItems.clear()
            activeTrackMetadataRef.set(null)
            sessionListeners.clear()
            audioFocusHandler.abandonAudioFocus()
            stopPositionTicker()
            player.removeListener(listener)
            mediaSession.release()
            player.release()
        }
    }

    @VisibleForTesting
    internal fun toPlaybackError(error: PlaybackException): PlaybackState.Error {
        val unsupportedRangeCause =
            generateSequence<Throwable>(error) { it.cause }
                .firstOrNull { it is UnsupportedRangeReconnectionException }
        val message =
            unsupportedRangeCause?.message
                ?: error.localizedMessage
                ?: "Playback error (${error.errorCodeName})"
        return PlaybackState.Error(
            message = message,
            cause = error,
        )
    }

    private fun updatePlaybackState() {
        val state =
            when (player.playbackState) {
                Player.STATE_IDLE -> {
                    val error = player.playerError
                    if (error != null) {
                        toPlaybackError(error)
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

        when (state) {
            is PlaybackState.Playing,
            is PlaybackState.Buffering -> hardwareLockManager.acquireLocks()
            else -> hardwareLockManager.releaseLocks()
        }
        _playbackState.value = state
    }

    private fun updatePositionAndDuration() {
        val dur: Long = player.duration
        if (dur != C.TIME_UNSET && dur > 0) {
            _durationMs.value = dur
        }
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
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
