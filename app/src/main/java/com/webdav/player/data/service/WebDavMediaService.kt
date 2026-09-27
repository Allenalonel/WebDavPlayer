package com.webdav.player.data.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.webdav.player.domain.player.AudioPlayerEngine

@OptIn(UnstableApi::class)
class WebDavMediaService : MediaSessionService() {
    private var sessionHost: PlaybackSessionHost? = null

    val notificationProvider: MediaNotification.Provider?
        get() = sessionHost?.notificationProvider ?: PlaybackSessionHost.getInstance(applicationContext).notificationProvider

    val mediaSession: MediaSession?
        get() = sessionHost?.mediaSession ?: PlaybackSessionHost.getInstance(applicationContext).mediaSession

    val playerEngine: AudioPlayerEngine
        get() = sessionHost ?: PlaybackSessionHost.getInstance(applicationContext)

    override fun onCreate() {
        super.onCreate()
        val host = PlaybackSessionHost.getInstance(applicationContext)
        sessionHost = host
        host.attachService(this)
        setMediaNotificationProvider(host.notificationProvider)
        addSession(host.mediaSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        sessionHost?.mediaSession ?: PlaybackSessionHost.getInstance(applicationContext).mediaSession

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val host = sessionHost ?: PlaybackSessionHost.getInstance(applicationContext)

        when (intent?.action) {
            WebDavNotificationProvider.ACTION_PLAY -> host.play()
            WebDavNotificationProvider.ACTION_PAUSE -> host.pause()
            WebDavNotificationProvider.ACTION_NEXT -> host.skipToNext()
            WebDavNotificationProvider.ACTION_PREVIOUS -> host.skipToPrevious()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val host = sessionHost ?: PlaybackSessionHost.getInstance(applicationContext)
        val player = host.mediaSession.player
        if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sessionHost?.let { host ->
            host.detachService(this)
            removeSession(host.mediaSession)
        }
        sessionHost = null
        super.onDestroy()
    }
}
