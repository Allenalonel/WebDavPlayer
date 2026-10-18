package com.webdav.player.data.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
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

@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackSessionHostTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

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
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        previousHost = PlaybackSessionHost.currentInstanceForTesting()

        fakeEngine = FakeAudioPlayerEngine()
        exoPlayer = ExoPlayer.Builder(context).build()
        val fakeSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        mediaSession =
            MediaSession
                .Builder(context, exoPlayer)
                .setId("test_session_${System.currentTimeMillis()}")
                .setCallback(WebDavMediaSessionCallback(fakeSession))
                .build()

        host =
            PlaybackSessionHost(
                context = context,
                playerEngine = fakeEngine,
                mediaSession = mediaSession,
                audioFocusHandler =
                    com.webdav.player.data.player
                        .AudioFocusHandler(context, fakeEngine),
                coroutineScope = testScope,
            )
        PlaybackSessionHost.setInstanceForTesting(host)

        serviceController = Robolectric.buildService(WebDavMediaService::class.java)
        service = serviceController.create().get()

        testScope.runCurrent()
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
        Dispatchers.resetMain()
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
        host.loadTrack(testServer, testTrack)
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
    fun callersControlPlaybackExclusively_viaLoadTrack_startsServiceAutonomouslyAndForwardsPipelineMethods() {
        host.detachService(service)
        assertNull(host.attachedService)

        // Caller loads track solely through AudioPlayerEngine interface
        host.loadTrack(testServer, testTrack, startPositionMs = 2500L)
        assertEquals(1, fakeEngine.loadTrackCount)
        assertEquals(testTrack, fakeEngine.loadedTrack)
        assertEquals(2500L, fakeEngine.currentPositionMs.value)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

        // Autonomous service startup intent dispatched
        val nextIntent = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedService
        assertNotNull(nextIntent)
        assertEquals(WebDavMediaService::class.java.name, nextIntent.component?.className)

        // Test preroll forwarding
        val prerollTrack = testTrack.copy(id = "2", title = "Track 2")
        host.prerollTrack(testServer, prerollTrack)
        assertTrue(fakeEngine.isPrerollActive)
        assertEquals(prerollTrack, fakeEngine.prerolledTrack)

        // Test clearPrerollTrack forwarding
        host.clearPrerollTrack()
        assertFalse(fakeEngine.isPrerollActive)
        assertEquals(1, fakeEngine.clearPrerollCount)

        // Test updateTrackMetadata forwarding
        val updatedTrack = testTrack.copy(title = "Updated Title")
        host.updateTrackMetadata(updatedTrack)
        assertEquals(1, fakeEngine.updateTrackMetadataCalls)
        assertEquals(updatedTrack, fakeEngine.loadedTrack)
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
    fun hardwareLocks_acquiredOnPlayingAndBuffering_releasedOnPauseAndStop() {
        assertFalse(host.hardwareLockManager.isWakeLockHeld)
        assertFalse(host.hardwareLockManager.isWifiLockHeld)

        // Buffering acquires both locks
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertTrue(host.hardwareLockManager.isWakeLockHeld)
        assertTrue(host.hardwareLockManager.isWifiLockHeld)

        // Playing keeps both locks held
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.hardwareLockManager.isWakeLockHeld)
        assertTrue(host.hardwareLockManager.isWifiLockHeld)

        // Paused immediately releases both locks to save battery
        host.handlePlaybackStateChanged(PlaybackState.Paused)
        assertFalse(host.hardwareLockManager.isWakeLockHeld)
        assertFalse(host.hardwareLockManager.isWifiLockHeld)

        // Resume playing re-acquires locks
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.hardwareLockManager.isWakeLockHeld)
        assertTrue(host.hardwareLockManager.isWifiLockHeld)

        // Stop releases locks
        host.stop()
        assertFalse(host.hardwareLockManager.isWakeLockHeld)
        assertFalse(host.hardwareLockManager.isWifiLockHeld)
    }

    @Test
    fun playbackPaused_maintainsForegroundAndSchedulesDeferredDemotion() {
        val shadowService = shadowOf(service)

        // First elevate to playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isForegroundActive)

        // Now pause
        host.handlePlaybackStateChanged(PlaybackState.Paused)

        // Foreground should NOT be demoted immediately; 15-min demotion scheduled
        assertTrue(host.isForegroundActive)
        assertTrue(host.isDemotionPending)
        assertFalse(shadowService.isForegroundStopped)

        // Notification should still be retained in NotificationManager for user resumption
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = notificationManager.activeNotifications
        assertTrue(notifications.any { it.id == host.notificationProvider.notificationId })
    }

    @Test
    fun playbackPaused_at14Minutes_maintainsForegroundService() {
        val shadowService = shadowOf(service)

        fakeEngine._playbackState.value = PlaybackState.Playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        fakeEngine._playbackState.value = PlaybackState.Paused
        host.handlePlaybackStateChanged(PlaybackState.Paused)

        assertTrue(host.isForegroundActive)
        assertTrue(host.isDemotionPending)

        // Advance virtual clock by 14 minutes
        testScope.advanceTimeBy(14 * 60 * 1000L)
        testScope.runCurrent()

        // At 14 minutes, still within grace period: foreground remains active
        assertTrue(host.isForegroundActive)
        assertTrue(host.isDemotionPending)
        assertFalse(shadowService.isForegroundStopped)
    }

    @Test
    fun playbackPaused_at15Minutes_triggersDeferredDemotion() {
        val shadowService = shadowOf(service)

        fakeEngine._playbackState.value = PlaybackState.Playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        fakeEngine._playbackState.value = PlaybackState.Paused
        host.handlePlaybackStateChanged(PlaybackState.Paused)

        // Advance virtual clock across the 15-minute threshold
        testScope.advanceTimeBy(15 * 60 * 1000L)
        testScope.runCurrent()

        // After 15 minutes without resumption: foreground demoted to save battery
        assertFalse(host.isForegroundActive)
        assertFalse(host.isDemotionPending)
        assertTrue(shadowService.isForegroundStopped)

        // Notification is detached (retained) for user resumption
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = notificationManager.activeNotifications
        assertTrue(notifications.any { it.id == host.notificationProvider.notificationId })
    }

    @Test
    fun playbackPaused_resumedBeforeTimeout_cancelsDemotionTimerAndMaintainsForeground() {
        val shadowService = shadowOf(service)

        fakeEngine._playbackState.value = PlaybackState.Playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        fakeEngine._playbackState.value = PlaybackState.Paused
        host.handlePlaybackStateChanged(PlaybackState.Paused)
        assertTrue(host.isDemotionPending)

        // User pauses for 5 minutes, then resumes playing
        testScope.advanceTimeBy(5 * 60 * 1000L)
        testScope.runCurrent()
        fakeEngine._playbackState.value = PlaybackState.Playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)

        // Demotion cancelled immediately, foreground uninterrupted
        assertFalse(host.isDemotionPending)
        assertTrue(host.isForegroundActive)
        assertFalse(shadowService.isForegroundStopped)

        // Advance another 15 minutes to guarantee timer is not lingering
        testScope.advanceTimeBy(15 * 60 * 1000L)
        testScope.runCurrent()
        assertTrue(host.isForegroundActive)
        assertFalse(shadowService.isForegroundStopped)
    }

    @Test
    fun playbackPaused_idleOrEnded_cancelsDemotionTimerAndClearsNotification() {
        val shadowService = shadowOf(service)

        fakeEngine._playbackState.value = PlaybackState.Playing
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        fakeEngine._playbackState.value = PlaybackState.Paused
        host.handlePlaybackStateChanged(PlaybackState.Paused)
        assertTrue(host.isDemotionPending)

        // Explicit idle or ended cancels pending demotion and clears notification
        fakeEngine._playbackState.value = PlaybackState.Idle
        host.handlePlaybackStateChanged(PlaybackState.Idle)
        assertFalse(host.isDemotionPending)
        assertFalse(host.isForegroundActive)
        assertTrue(shadowService.isForegroundStopped)
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
        host.loadTrack(testServer, testTrack)
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
        val appSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        val callback = WebDavMediaSessionCallback(appSession)

        val playIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
        assertTrue(callback.handleMediaButtonIntent(playIntent))
        assertEquals(1, appSession.playCount)

        val pauseIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            }
        assertTrue(callback.handleMediaButtonIntent(pauseIntent))
        assertEquals(1, appSession.pauseCount)

        val nextIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
            }
        assertTrue(callback.handleMediaButtonIntent(nextIntent))
        assertEquals(1, appSession.skipNextCount)

        val prevIntent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            }
        assertTrue(callback.handleMediaButtonIntent(prevIntent))
        assertEquals(1, appSession.skipPreviousCount)
    }

    @Test
    fun service_onTaskRemoved_stopsSelfWhenNotPlaying() {
        host.stop()
        service.onTaskRemoved(Intent())
        // Service should stop itself when idle on task remove
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun bindAppSession_enforcesOneTimeImmutableContract() {
        val session1 =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        val session2 =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()

        host.bindAppSession(session1)
        assertEquals(session1, host.appSession)

        val exception =
            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                host.bindAppSession(session2)
            }
        assertEquals("appSession has already been bound", exception.message)
    }

    @Test
    fun becomingNoisy_whenPlaying_registersReceiver_andBroadcastTriggersAppSessionPause() {
        val appSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        host.bindAppSession(appSession)

        assertFalse(host.isBecomingNoisyRegistered)

        // Enter Playing state -> receiver is registered
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isBecomingNoisyRegistered)

        // Send ACTION_AUDIO_BECOMING_NOISY broadcast
        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Assert appSession.pause() was invoked
        assertEquals(1, appSession.pauseCount)
    }

    @Test
    fun becomingNoisy_whenPausedOrIdle_unregistersReceiver_andBroadcastSafelyIgnored() {
        val appSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        host.bindAppSession(appSession)

        // Playing -> registered
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isBecomingNoisyRegistered)

        // Transition to Paused -> unregistered
        host.handlePlaybackStateChanged(PlaybackState.Paused)
        assertFalse(host.isBecomingNoisyRegistered)

        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(0, appSession.pauseCount)

        // Transition back to Playing -> re-registered
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isBecomingNoisyRegistered)

        // Transition to Idle -> unregistered
        host.handlePlaybackStateChanged(PlaybackState.Idle)
        assertFalse(host.isBecomingNoisyRegistered)

        // Transition to Ended -> unregistered
        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isBecomingNoisyRegistered)
        host.handlePlaybackStateChanged(PlaybackState.Ended)
        assertFalse(host.isBecomingNoisyRegistered)
    }

    @Test
    fun becomingNoisy_whenAppSessionNotBound_fallsBackToPlayerEnginePause() {
        assertEquals(0, fakeEngine.pauseCount)

        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isBecomingNoisyRegistered)

        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(1, fakeEngine.pauseCount)
    }
}
