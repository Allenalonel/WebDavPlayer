package com.webdav.player.data.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.webdav.player.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class WebDavNotificationProviderTest {
    private lateinit var context: Context
    private lateinit var player: Player
    private lateinit var session: MediaSession
    private lateinit var provider: WebDavNotificationProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        player = ExoPlayer.Builder(context).build()
        session = MediaSession.Builder(context, player).setId("test_session").build()
        provider = WebDavNotificationProvider(context)
    }

    @After
    fun tearDown() {
        session.release()
        player.release()
    }

    @Test
    fun notificationChannel_isCreatedWithLowImportance() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(WebDavNotificationProvider.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(WebDavNotificationProvider.CHANNEL_NAME, channel.name.toString())
    }

    @Test
    fun buildNotification_containsTrackMetadata_andThreeActions() {
        val mediaMetadata =
            MediaMetadata
                .Builder()
                .setTitle("Test Title")
                .setArtist("Test Artist")
                .setAlbumTitle("Test Album")
                .build()

        val mediaItem =
            MediaItem
                .Builder()
                .setUri("http://example.com/audio.mp3")
                .setMediaId("test:1")
                .setMediaMetadata(mediaMetadata)
                .build()

        player.setMediaItem(mediaItem)
        player.prepare()

        val notification = provider.buildNotification(session)

        assertEquals("Test Title", notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Test Artist", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals("Test Album", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(R.drawable.ic_notification_playback, notification.smallIcon.resId)

        // Verify actions: Previous, Play, Next
        assertNotNull(notification.actions)
        assertEquals(3, notification.actions.size)
        assertEquals("Previous", notification.actions[0].title.toString())
        assertEquals("Play", notification.actions[1].title.toString())
        assertEquals("Next", notification.actions[2].title.toString())
    }

    @Test
    fun buildNotification_whenPlaying_actionIsPause_andOngoingIsTrue() {
        val mediaItem =
            MediaItem
                .Builder()
                .setUri("http://example.com/audio.mp3")
                .setMediaId("test:1")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Playing Song").build())
                .build()

        player.setMediaItem(mediaItem)
        player.playWhenReady = true

        val notification = provider.buildNotification(session)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Pause", notification.actions[1].title.toString())
    }

    @Test
    fun buildNotification_loadsArtworkBitmap_fromArtworkData() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()

        val mediaMetadata =
            MediaMetadata
                .Builder()
                .setTitle("With Artwork")
                .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()

        val mediaItem =
            MediaItem
                .Builder()
                .setUri("http://example.com/audio.mp3")
                .setMediaId("test:art")
                .setMediaMetadata(mediaMetadata)
                .build()

        player.setMediaItem(mediaItem)

        val notification = provider.buildNotification(session)
        val largeIcon = notification.getLargeIcon()
        assertNotNull(largeIcon)
    }

    @Test
    fun buildNotification_loadsArtworkBitmap_fromArtworkUri() {
        val coverFile = File(context.cacheDir, "test_cover.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val mediaMetadata =
            MediaMetadata
                .Builder()
                .setTitle("With Uri Artwork")
                .setArtworkUri(android.net.Uri.fromFile(coverFile))
                .build()

        val mediaItem =
            MediaItem
                .Builder()
                .setUri("http://example.com/audio.mp3")
                .setMediaId("test:uri_art")
                .setMediaMetadata(mediaMetadata)
                .build()

        player.setMediaItem(mediaItem)

        val notification = provider.buildNotification(session)
        val largeIcon = notification.getLargeIcon()
        assertNotNull(largeIcon)
    }

    @Test
    fun createNotification_returnsMediaNotification_withMatchingId() {
        val mediaItem =
            MediaItem
                .Builder()
                .setUri("http://example.com/audio.mp3")
                .setMediaId("test:create")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Provider Test").build())
                .build()
        player.setMediaItem(mediaItem)

        val mediaNotification =
            provider.createNotification(
                mediaSession = session,
                customLayout = ImmutableList.of(),
                actionFactory =
                    object : androidx.media3.session.MediaNotification.ActionFactory {
                        override fun createMediaAction(
                            session: MediaSession,
                            customAction: androidx.core.graphics.drawable.IconCompat,
                            actionLabel: CharSequence,
                            actionCode: Int,
                        ): androidx.core.app.NotificationCompat.Action =
                            androidx.core.app.NotificationCompat.Action
                                .Builder(
                                    android.R.drawable.ic_media_play,
                                    actionLabel,
                                    null,
                                ).build()

                        override fun createCustomAction(
                            session: MediaSession,
                            customAction: androidx.core.graphics.drawable.IconCompat,
                            actionLabel: CharSequence,
                            customActionKey: String,
                            customExtras: android.os.Bundle,
                        ): androidx.core.app.NotificationCompat.Action =
                            androidx.core.app.NotificationCompat.Action
                                .Builder(
                                    android.R.drawable.ic_media_play,
                                    actionLabel,
                                    null,
                                ).build()

                        override fun createCustomActionFromCustomCommandButton(
                            session: MediaSession,
                            customCommandButton: androidx.media3.session.CommandButton,
                        ): androidx.core.app.NotificationCompat.Action =
                            androidx.core.app.NotificationCompat.Action
                                .Builder(
                                    android.R.drawable.ic_media_play,
                                    customCommandButton.displayName,
                                    null,
                                ).build()

                        override fun createMediaActionPendingIntent(
                            session: MediaSession,
                            actionToken: Long,
                        ): android.app.PendingIntent =
                            android.app.PendingIntent.getActivity(
                                context,
                                0,
                                android.content.Intent(),
                                android.app.PendingIntent.FLAG_IMMUTABLE,
                            )
                    },
                onNotificationChangedListener =
                    object : androidx.media3.session.MediaNotification.Provider.Callback {
                        override fun onNotificationChanged(notification: androidx.media3.session.MediaNotification) {}
                    },
            )

        assertEquals(WebDavNotificationProvider.NOTIFICATION_ID, mediaNotification.notificationId)
        assertNotNull(mediaNotification.notification)
        assertEquals(
            "Provider Test",
            mediaNotification.notification.extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString(),
        )
    }

    @Test
    fun vectorNotificationIcon_resolvesAndInflatesCleanly() {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notification_playback)
        assertNotNull("Vector notification icon must resolve and inflate cleanly", drawable)
    }

    @Test
    fun buildNotification_usesVectorNotificationPlaybackAsSmallIcon() {
        val mediaMetadata =
            MediaMetadata
                .Builder()
                .setTitle("Vector Icon Check")
                .build()
        val mediaItem =
            MediaItem
                .Builder()
                .setUri("http://example.com/audio.mp3")
                .setMediaId("test:vector_icon")
                .setMediaMetadata(mediaMetadata)
                .build()
        player.setMediaItem(mediaItem)

        val notification = provider.buildNotification(session)
        assertEquals(R.drawable.ic_notification_playback, notification.smallIcon.resId)
    }

    @Test
    fun buildNotification_whenArtworkUriFileDoesNotExist_doesNotThrowAndFallsBackToCleanVectorIcon() {
        val nonExistentFile = File(context.cacheDir, "non_existent_art.jpg")
        val mediaMetadata =
            MediaMetadata
                .Builder()
                .setTitle("Missing Art Song")
                .setArtist("Clean Fallback Artist")
                .setArtworkUri(android.net.Uri.fromFile(nonExistentFile))
                .build()
        val mediaItem =
            MediaItem
                .Builder()
                .setUri("http://example.com/audio.mp3")
                .setMediaId("test:missing_art")
                .setMediaMetadata(mediaMetadata)
                .build()
        player.setMediaItem(mediaItem)

        val notification = provider.buildNotification(session)
        assertNotNull(notification)
        assertEquals(R.drawable.ic_notification_playback, notification.smallIcon.resId)
        assertNull("Large icon should be null when artwork file does not exist", notification.getLargeIcon())
    }
}
