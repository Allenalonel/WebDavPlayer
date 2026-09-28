package com.webdav.player.data.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.webdav.player.data.player.AudioFocusHandler
import com.webdav.player.data.player.DefaultWebDavMediaSourceAdapter
import com.webdav.player.data.player.Media3AudioPlayerEngine
import com.webdav.player.data.player.WebDavMediaSourceAdapter
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackSessionHost(
    private val context: Context,
    val playerEngine: AudioPlayerEngine,
    val mediaSession: MediaSession,
    val audioFocusHandler: AudioFocusHandler,
    val notificationProvider: WebDavNotificationProvider = WebDavNotificationProvider(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AudioPlayerEngine by playerEngine {
    constructor(
        context: Context,
        mediaSourceAdapter: WebDavMediaSourceAdapter = DefaultWebDavMediaSourceAdapter(context),
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    ) : this(
        context = context,
        engine =
            Media3AudioPlayerEngine(
                context = context,
                mediaSourceAdapter = mediaSourceAdapter,
                coroutineScope = coroutineScope,
            ),
        coroutineScope = coroutineScope,
    )

    private constructor(
        context: Context,
        engine: Media3AudioPlayerEngine,
        coroutineScope: CoroutineScope,
    ) : this(
        context = context,
        playerEngine = engine,
        mediaSession = engine.mediaSession,
        audioFocusHandler = engine.audioFocusHandler,
        notificationProvider = WebDavNotificationProvider(context),
        coroutineScope = coroutineScope,
    )

    var attachedService: WebDavMediaService? = null
        private set

    var isForegroundActive: Boolean = false
        private set

    private var hasPlaybackStarted: Boolean = false

    private var stateObserverJob: Job? = null

    init {
        observePlaybackState()
    }

    private fun observePlaybackState() {
        stateObserverJob?.cancel()
        stateObserverJob =
            coroutineScope.launch {
                playerEngine.playbackState.collect { state ->
                    handlePlaybackStateChanged(state)
                }
            }
    }

    @VisibleForTesting
    fun handlePlaybackStateChanged(state: PlaybackState) {
        when (state) {
            is PlaybackState.Playing,
            is PlaybackState.Buffering,
            -> {
                hasPlaybackStarted = true
                elevateForeground(state)
            }

            is PlaybackState.Paused -> {
                demoteForeground(removeNotification = false, playbackState = state)
            }

            is PlaybackState.Error -> {
                demoteForeground(removeNotification = false, playbackState = state)
            }

            is PlaybackState.Idle -> {
                if (hasPlaybackStarted) {
                    hasPlaybackStarted = false
                    demoteForeground(removeNotification = true, playbackState = state)
                }
            }

            is PlaybackState.Ended -> {
                hasPlaybackStarted = false
                demoteForeground(removeNotification = true, playbackState = state)
            }
        }
    }

    fun attachService(service: WebDavMediaService) {
        this.attachedService = service
        this.isForegroundActive = true
        val currentState = playerEngine.playbackState.value
        if (currentState !is PlaybackState.Idle) {
            handlePlaybackStateChanged(currentState)
        }
    }

    fun detachService(service: WebDavMediaService? = null) {
        if (service == null || this.attachedService == service) {
            this.attachedService = null
            this.isForegroundActive = false
            this.hasPlaybackStarted = false
        }
    }

    fun buildCurrentNotification(playbackState: PlaybackState? = null): Notification {
        val targetState = playbackState ?: playerEngine.playbackState.value
        return notificationProvider.buildNotification(mediaSession, targetState)
    }

    fun elevateForeground(playbackState: PlaybackState? = null) {
        val service =
            attachedService ?: run {
                startServiceInternal()
                return
            }

        val notification = buildCurrentNotification(playbackState)
        if (service.startForegroundSafely(notification, notificationProvider.notificationId)) {
            isForegroundActive = true
        }
    }

    fun demoteForeground(
        removeNotification: Boolean,
        playbackState: PlaybackState? = null,
    ) {
        val service = attachedService
        val targetState = playbackState ?: playerEngine.playbackState.value
        if (service != null && isForegroundActive) {
            try {
                if (removeNotification) {
                    service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    service.stopForeground(Service.STOP_FOREGROUND_DETACH)
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    val notification =
                        notificationProvider.buildNotification(mediaSession, targetState)
                    notificationManager?.notify(notificationProvider.notificationId, notification)
                }
            } catch (e: Throwable) {
                // Gracefully handle
            }
            isForegroundActive = false
        } else if (removeNotification) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(notificationProvider.notificationId)
            } catch (e: Throwable) {
                // Gracefully handle
            }
        }
    }

    private fun ensureServiceStarted() {
        if (attachedService == null) {
            startServiceInternal()
        }
    }

    private fun startServiceInternal() {
        val intent = Intent(context, WebDavMediaService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Throwable) {
            // Gracefully handle test or background start exceptions
        }
    }

    override fun playTracks(
        server: WebDavServer,
        tracks: List<AudioTrack>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        ensureServiceStarted()
        playerEngine.playTracks(server, tracks, startIndex, startPositionMs)
    }

    override fun play() {
        ensureServiceStarted()
        playerEngine.play()
    }

    override fun stop() {
        hasPlaybackStarted = false
        playerEngine.stop()
        demoteForeground(removeNotification = true)
    }

    override fun release() {
        stateObserverJob?.cancel()
        stateObserverJob = null
        hasPlaybackStarted = false
        playerEngine.release()
        attachedService = null
        isForegroundActive = false
    }

    override fun setVolume(volume: Float) {
        playerEngine.setVolume(volume)
    }

    override fun getVolume(): Float = playerEngine.getVolume()

    companion object {
        @Volatile
        private var instance: PlaybackSessionHost? = null

        fun getInstance(
            context: Context,
            mediaSourceAdapter: WebDavMediaSourceAdapter? = null,
        ): PlaybackSessionHost =
            instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val adapter = mediaSourceAdapter ?: DefaultWebDavMediaSourceAdapter(appContext)
                    PlaybackSessionHost(appContext, adapter).also { instance = it }
                }
            }

        @VisibleForTesting
        fun currentInstanceForTesting(): PlaybackSessionHost? = instance

        @VisibleForTesting
        fun setInstanceForTesting(host: PlaybackSessionHost?) {
            synchronized(this) {
                instance = host
            }
        }

        @VisibleForTesting
        fun resetForTesting() {
            synchronized(this) {
                instance = null
            }
        }
    }
}
