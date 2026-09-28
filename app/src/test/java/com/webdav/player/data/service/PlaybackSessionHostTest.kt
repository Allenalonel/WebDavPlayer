package com.webdav.player.data.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackSessionHostTest {
    private lateinit var context: Context
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var exoPlayer: Player
    private lateinit var mediaSession: MediaSession
    private lateinit var host: PlaybackSessionHost
    private lateinit var serviceController: ServiceController<WebDavMediaService>
    private lateinit var service: WebDavMediaService
    private var previousHost: PlaybackSessionHost? = null

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "http://127.0.0.1:8080/dav",
            port = 8080,
            pathPrefix = "/dav",
        )

    private val testTrack =
        AudioTrack(
            id = "1",
            serverId = 1L,
            remotePath = "/track.mp3",
            title = "Track 1",
            format = AudioFormat.MP3,
            size = 1000L,
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        previousHost = PlaybackSessionHost.currentInstanceForTesting()

        fakeEngine = FakeAudioPlayerEngine()
        exoPlayer = ExoPlayer.Builder(context).build()
        mediaSession =
            MediaSession
                .Builder(context, exoPlayer)
                .setId("test_session_${System.currentTimeMillis()}")
                .setCallback(WebDavMediaSessionCallback(fakeEngine))
                .build()

        host =
            PlaybackSessionHost(
                context = context,
                playerEngine = fakeEngine,
                mediaSession = mediaSession,
                audioFocusHandler =
                    com.webdav.player.data.player
                        .AudioFocusHandler(context, fakeEngine),
            )
        PlaybackSessionHost.setInstanceForTesting(host)

        serviceController = Robolectric.buildService(WebDavMediaService::class.java)
        service = serviceController.create().get()
    }

    @After
    fun tearDown() {
        if (::serviceController.isInitialized) {
            serviceController.destroy()
        }
        host.release()
        mediaSession.release()
        exoPlayer.release()
        PlaybackSessionHost.setInstanceForTesting(previousHost)
    }

    @Test
    fun host_initializesCohesively_withoutReverseApplicationDowncasting() {
        assertNotNull(host.playerEngine)
        assertNotNull(host.mediaSession)
        assertNotNull(host.notificationProvider)
        assertNotNull(host.audioFocusHandler)
        // Verify service attached to host during its onCreate
        assertEquals(service, host.attachedService)
        assertTrue(host.isForegroundActive)
    }

    @Test
    fun attachService_establishesIsForegroundActive_immediatelyUponServiceBinding() {
        host.detachService(service)
        assertFalse(host.isForegroundActive)
        assertNull(host.attachedService)

        host.attachService(service)
        assertEquals(service, host.attachedService)
        assertTrue(host.isForegroundActive)
    }

    @Test
    fun initialPreparation_doesNotPrematurelyDemoteForeground() {
        val shadowService = shadowOf(service)
        assertTrue(host.isForegroundActive)
        assertFalse(shadowService.isForegroundStopped)

        // During initial preparation, playerEngine is in Idle state.
        // Transitioning to Idle before active playback starts must not prematurely trigger demoteForeground()
        host.handlePlaybackStateChanged(PlaybackState.Idle)
        assertTrue(host.isForegroundActive)
        assertFalse(shadowService.isForegroundStopped)
        assertNotNull(shadowService.lastForegroundNotification)
    }

    @Test
    fun stateTransitions_bufferingSafelyUpdatesNotification_andKeepsOngoingTrue() {
        val shadowService = shadowOf(service)

        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertTrue(host.isForegroundActive)
        assertFalse(shadowService.isForegroundStopped)

        val notification = shadowService.lastForegroundNotification
        assertNotNull(notification)
        assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun callersControlPlaybackExclusively_viaAudioPlayerEngine_startsServiceAutonomously() {
        // Detach service to simulate cold host before service launch
        host.detachService(service)
        assertNull(host.attachedService)

        // Caller plays track solely through AudioPlayerEngine interface
        host.playTracks(testServer, listOf(testTrack), startIndex = 0)
        assertEquals(listOf(testTrack), fakeEngine.lastTracks)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

        // Caller invokes play solely through AudioPlayerEngine interface
        host.play()
        assertEquals(1, fakeEngine.playCount)

        // Autonomous service startup intent dispatched
        val nextIntent = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedService
        assertNotNull(nextIntent)
        assertEquals(WebDavMediaService::class.java.name, nextIntent.component?.className)
    }

    @Test
    fun playbackPlayingOrBuffering_autonomouslyElevatesForeground() {
        val shadowService = shadowOf(service)

        // Transition state to Buffering
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertTrue(host.isForegroundActive)
        assertFalse(shadowService.isForegroundStopped)
        assertNotNull(shadowService.lastForegroundNotification)

        // Transition state to Playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isForegroundActive)
        assertFalse(shadowService.isForegroundStopped)
    }

    @Test
    fun playbackPaused_gracefullyDemotesForeground_preventsAppIdleTermination() {
        val shadowService = shadowOf(service)

        // First elevate to playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isForegroundActive)

        // Now pause
        host.handlePlaybackStateChanged(PlaybackState.Paused)

        // Foreground should be gracefully demoted (STOP_FOREGROUND_DETACH)
        assertFalse(host.isForegroundActive)
        assertTrue(shadowService.isForegroundStopped)

        // Notification should still be retained in NotificationManager for user resumption
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = notificationManager.activeNotifications
        assertTrue(notifications.any { it.id == host.notificationProvider.notificationId })
    }

    @Test
    fun playbackError_gracefullyDemotesForeground_preventsAppIdleTermination() {
        val shadowService = shadowOf(service)

        // First elevate
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isForegroundActive)

        // Enter error state
        host.handlePlaybackStateChanged(PlaybackState.Error("WebDAV stream connection reset"))

        // Foreground execution demoted to avoid ActivityManager killing the idle process
        assertFalse(host.isForegroundActive)
        assertTrue(shadowService.isForegroundStopped)
    }

    @Test
    fun playbackIdle_stopsForegroundAndClearsNotification() {
        val shadowService = shadowOf(service)

        // Elevate first
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isForegroundActive)

        // Enter Idle / stop
        host.handlePlaybackStateChanged(PlaybackState.Idle)

        assertFalse(host.isForegroundActive)
        assertTrue(shadowService.isForegroundStopped)
    }

    @Test
    fun audioFocus_negotiatesDuckingAndResumption_seamlessly() {
        val focusHandler = host.audioFocusHandler

        // Play track
        host.playTracks(testServer, listOf(testTrack), startIndex = 0)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

        // Transient loss (incoming call) -> pause
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(1, fakeEngine.pauseCount)
        assertTrue(focusHandler.resumeOnFocusGain)

        // Gain focus -> resumes
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, fakeEngine.playCount) // resumed play
        assertFalse(focusHandler.resumeOnFocusGain)

        // Transient duck (navigation prompt)
        var reportedVolume: Float? = null
        val duckHandler =
            com.webdav.player.data.player.AudioFocusHandler(
                context = context,
                playerEngine = host,
                onVolumeChanged = { reportedVolume = it },
            )
        duckHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue(duckHandler.isDucked)
        assertEquals(0.2f, reportedVolume)

        duckHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(duckHandler.isDucked)
        assertEquals(1.0f, reportedVolume)
    }

    @Test
    fun hardwareMediaButtons_dispatchDirectlyToHost() {
        val callback = WebDavMediaSessionCallback(host)

        val playIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
        assertTrue(callback.handleMediaButtonIntent(playIntent))
        assertEquals(1, fakeEngine.playCount)

        val pauseIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            }
        assertTrue(callback.handleMediaButtonIntent(pauseIntent))
        assertEquals(1, fakeEngine.pauseCount)

        val nextIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
            }
        assertTrue(callback.handleMediaButtonIntent(nextIntent))
        assertEquals(1, fakeEngine.nextCount)

        val prevIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            }
        assertTrue(callback.handleMediaButtonIntent(prevIntent))
        assertEquals(1, fakeEngine.previousCount)
    }

    @Test
    fun service_onTaskRemoved_stopsSelfWhenNotPlaying() {
        fakeEngine.stop()
        service.onTaskRemoved(Intent())
        // Service should stop itself when idle on task remove
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
