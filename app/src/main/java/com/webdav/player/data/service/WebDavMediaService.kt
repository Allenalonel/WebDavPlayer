package com.webdav.player.data.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.webdav.player.WebDavApplication
import com.webdav.player.data.player.Media3AudioPlayerEngine

@OptIn(UnstableApi::class)
class WebDavMediaService : MediaSessionService() {

    private var activeSession: MediaSession? = null
    var notificationProvider: MediaNotification.Provider? = null
        private set

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WebDavMediaService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                // In background restrictions or tests, handle gracefully
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebDavMediaService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val provider = WebDavNotificationProvider(this)
        notificationProvider = provider
        setMediaNotificationProvider(provider)

        val app = application as? WebDavApplication
        val session = (app?.playerEngine as? Media3AudioPlayerEngine)?.mediaSession
        if (session != null) {
            activeSession = session
            addSession(session)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return activeSession ?: run {
            val app = application as? WebDavApplication
            (app?.playerEngine as? Media3AudioPlayerEngine)?.mediaSession
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? WebDavApplication
        val engine = app?.playerEngine

        when (intent?.action) {
            WebDavNotificationProvider.ACTION_PLAY -> engine?.play()
            WebDavNotificationProvider.ACTION_PAUSE -> engine?.pause()
            WebDavNotificationProvider.ACTION_NEXT -> engine?.skipToNext()
            WebDavNotificationProvider.ACTION_PREVIOUS -> engine?.skipToPrevious()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = activeSession ?: (application as? WebDavApplication)?.let {
            (it.playerEngine as? Media3AudioPlayerEngine)?.mediaSession
        }
        val player = session?.player
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        activeSession?.let {
            removeSession(it)
            activeSession = null
        }
        super.onDestroy()
    }
}
