package com.webdav.player.data.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.session.MediaSession
import com.webdav.player.MainActivity
import com.webdav.player.data.service.WebDavMediaService
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

@OptIn(UnstableApi::class)
class Media3AudioPlayerEngine(
    private val context: Context,
    val dataSourceFactory: WebDavDataSourceFactory = WebDavDataSourceFactory(),
    customPlayer: Player? = null,
    customMediaSession: MediaSession? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : AudioPlayerEngine {

    val player: Player = customPlayer ?: run {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val extractorsFactory = ExtractorsFactory {
            arrayOf(
                *DefaultExtractorsFactory().createExtractors(),
                AsfExtractor()
            )
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false // Audio focus is exclusively managed by audioFocusHandler
            )
            .build()
    }

    val mediaSession: MediaSession = customMediaSession ?: run {
        val sessionActivity = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        MediaSession.Builder(context, player)
            .setId("WebDavPlayerSession_${System.currentTimeMillis()}_${(1..99999).random()}")
            .setSessionActivity(sessionActivity)
            .setCallback(WebDavMediaSessionCallback(this))
            .build()
    }

    val audioFocusHandler: AudioFocusHandler = AudioFocusHandler(context, this)

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun getVolume(): Float = player.volume

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    override val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.LIST_LOOP)
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private var tickerJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            updatePlaybackState()
            updatePositionAndDuration()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentTrackIndex.value = player.currentMediaItemIndex
            updatePositionAndDuration()
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = PlaybackState.Error(
                error.localizedMessage ?: "Playback error (${error.errorCodeName})"
            )
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
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
        startPositionMs: Long
    ) {
        if (tracks.isEmpty()) return
        dataSourceFactory.setServer(server)

        val mediaItems = tracks.map { track ->
            buildMediaItem(server, track)
        }

        val validStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
        _currentTrackIndex.value = validStartIndex
        _currentPositionMs.value = startPositionMs
        applyPlaybackMode(_playbackMode.value)
        player.setMediaItems(mediaItems, validStartIndex, startPositionMs)
        player.prepare()
        audioFocusHandler.requestAudioFocus()
        WebDavMediaService.start(context)
        player.play()
    }

    override fun play() {
        audioFocusHandler.requestAudioFocus()
        WebDavMediaService.start(context)
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

    override fun seekToTrack(index: Int, positionMs: Long) {
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

    override fun insertTrack(index: Int, server: WebDavServer, track: AudioTrack) {
        if (index in 0..player.mediaItemCount) {
            val mediaItem = buildMediaItem(server, track)
            player.addMediaItem(index, mediaItem)
        }
    }

    private fun buildMediaItem(server: WebDavServer, track: AudioTrack): MediaItem {
        val uri = Uri.parse(track.streamUrl(server))
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        track.coverThumbnailPath?.let { path ->
            metaBuilder.setArtworkUri(Uri.fromFile(File(path)))
        }

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id)
            .setMimeType(track.format.mimeType)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    override fun updateTrack(index: Int, track: AudioTrack) {
        if (index in 0 until player.mediaItemCount) {
            val currentItem = player.getMediaItemAt(index)
            val currentMeta = currentItem.mediaMetadata

            val newArtworkUri = track.coverThumbnailPath?.let { Uri.fromFile(File(it)) }
            val sameTitle = currentMeta.title?.toString() == track.title
            val sameArtist = (currentMeta.artist?.toString() ?: "") == (track.artist ?: "")
            val sameAlbum = (currentMeta.albumTitle?.toString() ?: "") == (track.album ?: "")
            val sameArtwork = currentMeta.artworkUri == newArtworkUri

            if (sameTitle && sameArtist && sameAlbum && sameArtwork) {
                return // Metadata identical; bypass replaceMediaItem to prevent player re-buffering & notification churn
            }

            val metaBuilder = currentMeta.buildUpon()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
            if (newArtworkUri != null) {
                metaBuilder.setArtworkUri(newArtworkUri)
            } else if (currentMeta.artworkUri != null) {
                metaBuilder.setArtworkUri(null)
            }
            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(metaBuilder.build())
                .build()
            player.replaceMediaItem(index, updatedItem)
        }
    }

    override fun stop() {
        audioFocusHandler.abandonAudioFocus()
        stopPositionTicker()
        player.stop()
        _playbackState.value = PlaybackState.Idle
        _currentPositionMs.value = 0L
    }

    override fun release() {
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
        val state = when (player.playbackState) {
            Player.STATE_IDLE -> {
                val error = player.playerError
                if (error != null) {
                    PlaybackState.Error(error.localizedMessage ?: "Playback error")
                } else {
                    PlaybackState.Idle
                }
            }
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> {
                if (player.playWhenReady) {
                    PlaybackState.Playing
                } else {
                    PlaybackState.Paused
                }
            }
            Player.STATE_ENDED -> PlaybackState.Ended
            else -> PlaybackState.Idle
        }
        _playbackState.value = state
    }

    private fun updatePositionAndDuration() {
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration
        if (dur != C.TIME_UNSET && dur > 0) {
            _durationMs.value = dur
        }
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = coroutineScope.launch {
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
