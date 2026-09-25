package com.webdav.player.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.webdav.player.MainActivity
import java.io.File

@OptIn(UnstableApi::class)
class WebDavNotificationProvider(
    private val context: Context,
    val channelId: String = CHANNEL_ID,
    val notificationId: Int = NOTIFICATION_ID
) : MediaNotification.Provider {

    companion object {
        const val CHANNEL_ID = "webdav_playback_channel"
        const val CHANNEL_NAME = "WebDAV Playback"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PREVIOUS = "com.webdav.player.ACTION_PREVIOUS"
        const val ACTION_PLAY = "com.webdav.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.webdav.player.ACTION_PAUSE"
        const val ACTION_NEXT = "com.webdav.player.ACTION_NEXT"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val existing = notificationManager?.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "WebDAV music player playback controls"
                    setShowBadge(false)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedListener: MediaNotification.Provider.Callback
    ): MediaNotification {
        val notification = buildNotification(mediaSession)
        return MediaNotification(notificationId, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        return false
    }

    fun buildNotification(mediaSession: MediaSession): Notification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata

        val title = metadata.title?.toString()
            ?: (player.currentMediaItem?.mediaMetadata?.title?.toString())
            ?: "WebDAV Player"
        val artist = metadata.artist?.toString()
            ?: (player.currentMediaItem?.mediaMetadata?.artist?.toString())
        val album = metadata.albumTitle?.toString()
            ?: (player.currentMediaItem?.mediaMetadata?.albumTitle?.toString())

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = player.isPlaying || player.playWhenReady

        val prevIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, WebDavMediaService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, WebDavMediaService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            context,
            3,
            Intent(context, WebDavMediaService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // Load artwork if available
        val artworkBitmap = loadArtwork(metadata, player)
        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap)
        }

        return builder.build()
    }

    private fun loadArtwork(
        metadata: androidx.media3.common.MediaMetadata,
        player: androidx.media3.common.Player
    ): Bitmap? {
        val meta = if (metadata.artworkData != null || metadata.artworkUri != null) {
            metadata
        } else {
            player.currentMediaItem?.mediaMetadata
        } ?: return null

        val data = meta.artworkData
        if (data != null && data.isNotEmpty()) {
            try {
                return BitmapFactory.decodeByteArray(data, 0, data.size)
            } catch (e: Throwable) {
                // Ignore decoding errors
            }
        }
        val uri = meta.artworkUri
        if (uri != null && uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    return BitmapFactory.decodeFile(file.absolutePath)
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
        return null
    }
}
