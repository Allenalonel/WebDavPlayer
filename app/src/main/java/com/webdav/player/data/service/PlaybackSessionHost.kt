package com.webdav.player.data.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.webdav.player.data.player.AudioFocusHandler
import com.webdav.player.data.player.AudioRoutingObserver
import com.webdav.player.data.player.DefaultStreamingHardwareLockManager
import com.webdav.player.data.player.DefaultWebDavMediaSourceAdapter
import com.webdav.player.data.player.Media3AudioPlayerEngine
import com.webdav.player.data.player.StreamingHardwareLockManager
import com.webdav.player.data.player.WebDavMediaSourceAdapter
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackSessionHost(
    val context: Context,
    val playerEngine: AudioPlayerEngine,
    val mediaSession: MediaSession,
    val audioFocusHandler: AudioFocusHandler,
    val notificationProvider: WebDavNotificationProvider = WebDavNotificationProvider(context),
    mediaSessionCallback: WebDavMediaSessionCallback? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    val demotionTimeoutMs: Long = DEFAULT_DEMOTION_TIMEOUT_MS,
    val hardwareLockManager: StreamingHardwareLockManager =
        (playerEngine as? Media3AudioPlayerEngine)?.hardwareLockManager
            ?: DefaultStreamingHardwareLockManager(context),
) : AudioPlayerEngine by playerEngine {
    var mediaSessionCallback: WebDavMediaSessionCallback? = mediaSessionCallback
        private set

    val appSession: MusicPlayerAppSession?
        get() = mediaSessionCallback?.appSession

    val audioRoutingObserver: AudioRoutingObserver?
        get() = (playerEngine as? Media3AudioPlayerEngine)?.audioRoutingObserver

    fun onAudioRoutingChanged() {
        (playerEngine as? Media3AudioPlayerEngine)?.onAudioRoutingChanged()
    }
    constructor(
        context: Context,
        mediaSourceAdapter: WebDavMediaSourceAdapter = DefaultWebDavMediaSourceAdapter(context),
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        demotionTimeoutMs: Long = DEFAULT_DEMOTION_TIMEOUT_MS,
        hardwareLockManager: StreamingHardwareLockManager =
            (mediaSourceAdapter as? DefaultWebDavMediaSourceAdapter)?.hardwareLockManager
                ?: DefaultStreamingHardwareLockManager(context),
    ) : this(
        context = context,
        engine =
            Media3AudioPlayerEngine(
                context = context,
                mediaSourceAdapter = mediaSourceAdapter,
                coroutineScope = coroutineScope,
                hardwareLockManager = hardwareLockManager,
            ),
        coroutineScope = coroutineScope,
        demotionTimeoutMs = demotionTimeoutMs,
        hardwareLockManager = hardwareLockManager,
    )

    private constructor(
        context: Context,
        engine: Media3AudioPlayerEngine,
        coroutineScope: CoroutineScope,
        demotionTimeoutMs: Long = DEFAULT_DEMOTION_TIMEOUT_MS,
        hardwareLockManager: StreamingHardwareLockManager = engine.hardwareLockManager,
    ) : this(
        context = context,
        playerEngine = engine,
        mediaSession = engine.mediaSession,
        audioFocusHandler = engine.audioFocusHandler,
        notificationProvider = WebDavNotificationProvider(context),
        mediaSessionCallback = null,
        coroutineScope = coroutineScope,
        demotionTimeoutMs = demotionTimeoutMs,
        hardwareLockManager = hardwareLockManager,
    )

    fun bindAppSession(session: MusicPlayerAppSession) {
        check(mediaSessionCallback == null) { "appSession has already been bound" }
        val callback = WebDavMediaSessionCallback(appSession = session)
        this.mediaSessionCallback = callback
        (playerEngine as? Media3AudioPlayerEngine)?.let { engine ->
            engine.sessionCallback = callback
            engine.onSkipToNext = { session.skipToNext() }
            engine.onSkipToPrevious = { session.skipToPrevious() }
        }
    }

    var attachedService: WebDavMediaService? = null
        private set

    var isForegroundActive: Boolean = false
        private set

    private var hasPlaybackStarted: Boolean = false

    private var stateObserverJob: Job? = null
    private var demotionJob: Job? = null

    val isDemotionPending: Boolean
        get() = demotionJob?.isActive == true

    private var isBecomingNoisyRegisteredInternal: Boolean = false

    @get:VisibleForTesting
    val isBecomingNoisyRegistered: Boolean
        get() = isBecomingNoisyRegisteredInternal

    private val becomingNoisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    val targetSession = appSession
                    if (targetSession != null) {
                        targetSession.pause()
                    } else {
                        playerEngine.pause()
                    }
                }
            }
        }

    private fun registerBecomingNoisyReceiver() {
        if (!isBecomingNoisyRegisteredInternal) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                context.registerReceiver(becomingNoisyReceiver, filter)
                isBecomingNoisyRegisteredInternal = true
            } catch (_: Throwable) {
            }
        }
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (isBecomingNoisyRegisteredInternal) {
            try {
                context.unregisterReceiver(becomingNoisyReceiver)
            } catch (_: Throwable) {
            } finally {
                isBecomingNoisyRegisteredInternal = false
            }
        }
    }

    init {
        observePlaybackState()
        mediaSessionCallback?.let { callback ->
            (playerEngine as? Media3AudioPlayerEngine)?.let { engine ->
                engine.sessionCallback = callback
                engine.onSkipToNext = { callback.appSession.skipToNext() }
                engine.onSkipToPrevious = { callback.appSession.skipToPrevious() }
            }
        }
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
            is PlaybackState.Playing -> {
                hardwareLockManager.acquireLocks()
                cancelDemotionTimer()
                hasPlaybackStarted = true
                registerBecomingNoisyReceiver()
                elevateForeground(state)
            }

            is PlaybackState.Buffering -> {
                hardwareLockManager.acquireLocks()
                cancelDemotionTimer()
                hasPlaybackStarted = true
                elevateForeground(state)
            }

            is PlaybackState.Paused -> {
                hardwareLockManager.releaseLocks()
                unregisterBecomingNoisyReceiver()
                scheduleDeferredDemotion(state)
            }

            is PlaybackState.Error -> {
                hardwareLockManager.releaseLocks()
                unregisterBecomingNoisyReceiver()
                cancelDemotionTimer()
                demoteForeground(removeNotification = false, playbackState = state)
            }

            is PlaybackState.Idle -> {
                hardwareLockManager.releaseLocks()
                unregisterBecomingNoisyReceiver()
                cancelDemotionTimer()
                if (hasPlaybackStarted) {
                    hasPlaybackStarted = false
                    demoteForeground(removeNotification = true, playbackState = state)
                }
            }

            is PlaybackState.Ended -> {
                hardwareLockManager.releaseLocks()
                unregisterBecomingNoisyReceiver()
                cancelDemotionTimer()
                hasPlaybackStarted = false
                demoteForeground(removeNotification = true, playbackState = state)
            }
        }
    }

    private fun scheduleDeferredDemotion(state: PlaybackState) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notification = buildCurrentNotification(state)
        notificationManager?.notify(notificationProvider.notificationId, notification)

        cancelDemotionTimer()
        demotionJob =
            coroutineScope.launch {
                delay(demotionTimeoutMs)
                performDeferredDemotion(state)
            }
    }

    private fun performDeferredDemotion(state: PlaybackState) {
        demotionJob = null
        demoteForeground(removeNotification = false, playbackState = state)
    }

    fun cancelDemotionTimer() {
        demotionJob?.cancel()
        demotionJob = null
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
            hardwareLockManager.releaseLocks()
            cancelDemotionTimer()
            unregisterBecomingNoisyReceiver()
            this.attachedService = null
            this.isForegroundActive = false
            this.hasPlaybackStarted = false
        }
    }

    fun buildCurrentNotification(
        playbackState: PlaybackState? = null,
        isWeakNetwork: Boolean = false,
    ): Notification {
        val targetState = playbackState ?: playerEngine.playbackState.value
        return notificationProvider.buildNotification(mediaSession, targetState, isWeakNetwork)
    }

    fun elevateForeground(playbackState: PlaybackState? = null) {
        cancelDemotionTimer()
        val targetState = playbackState ?: playerEngine.playbackState.value
        if (targetState is PlaybackState.Playing) {
            registerBecomingNoisyReceiver()
        }
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
        if (removeNotification) {
            unregisterBecomingNoisyReceiver()
        }
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

    fun prewarmService() {
        ensureServiceStarted()
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

    override fun loadTrack(
        server: WebDavServer,
        track: AudioTrack,
        startPositionMs: Long,
    ) {
        ensureServiceStarted()
        playerEngine.loadTrack(server, track, startPositionMs)
    }

    override fun prerollTrack(
        server: WebDavServer,
        track: AudioTrack,
    ) {
        playerEngine.prerollTrack(server, track)
    }

    override fun clearPrerollTrack() {
        playerEngine.clearPrerollTrack()
    }

    override fun updateTrackMetadata(track: AudioTrack) {
        playerEngine.updateTrackMetadata(track)
    }

    override fun play() {
        ensureServiceStarted()
        playerEngine.play()
    }

    override fun stop() {
        hardwareLockManager.releaseLocks()
        cancelDemotionTimer()
        unregisterBecomingNoisyReceiver()
        hasPlaybackStarted = false
        playerEngine.stop()
        demoteForeground(removeNotification = true)
    }

    override fun release() {
        hardwareLockManager.release()
        cancelDemotionTimer()
        unregisterBecomingNoisyReceiver()
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
        const val DEFAULT_DEMOTION_TIMEOUT_MS: Long = 15 * 60 * 1000L

        @Volatile
        private var instance: PlaybackSessionHost? = null

        fun getInstance(
            context: Context,
            mediaSourceAdapter: WebDavMediaSourceAdapter? = null,
        ): PlaybackSessionHost {
            val targetContext = context.applicationContext ?: context
            return instance?.takeIf { it.context === targetContext } ?: synchronized(this) {
                instance?.takeIf { it.context === targetContext } ?: run {
                    val adapter = mediaSourceAdapter ?: DefaultWebDavMediaSourceAdapter(targetContext)
                    PlaybackSessionHost(targetContext, adapter).also { instance = it }
                }
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
