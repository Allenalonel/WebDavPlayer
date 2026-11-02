package com.webdav.player.data.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackSessionHostTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var exoPlayer: Player
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

        host =
            PlaybackSessionHost(
                context = context,
                playerEngine = fakeEngine,
                audioFocusHandler = AudioFocusHandler(context),
                coroutineScope = testScope,
                sessionPlayer = exoPlayer,
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
        assertNotNull(host.hardwareLockManager)
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

        // Caller plays track directly on AudioPlayerEngine
        fakeEngine.loadTrack(testServer, testTrack)
        testScope.runCurrent()
        assertEquals(testTrack, fakeEngine.loadedTrack)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

        // Autonomous service startup intent dispatched via state observer
        val nextIntent = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedService
        assertNotNull(nextIntent)
        assertEquals(WebDavMediaService::class.java.name, nextIntent.component?.className)
    }

    @Test
    fun enginePlaybackStateTransitions_startServiceAutonomously_whenServiceDetached() {
        host.detachService(service)
        assertNull(host.attachedService)

        // Engine starts playback with start position
        fakeEngine.loadTrack(testServer, testTrack, startPositionMs = 2500L)
        testScope.runCurrent()
        assertEquals(1, fakeEngine.loadTrackCount)
        assertEquals(testTrack, fakeEngine.loadedTrack)
        assertEquals(2500L, fakeEngine.currentPositionMs.value)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

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
    fun hardwareLocks_acquiredOnPlayingAndActiveBuffering_releasedOnPausedErrorIdleEndedDetachStopAndRelease() {
        fun assertLocksHeld(expected: Boolean) {
            assertEquals(expected, host.hardwareLockManager.isWakeLockHeld)
            assertEquals(expected, host.hardwareLockManager.isWifiLockHeld)
        }

        fun resumePlayingAndAssertLocksHeld() {
            fakeEngine.customPlayWhenReady = true
            host.handlePlaybackStateChanged(PlaybackState.Playing)
            assertLocksHeld(true)
        }

        // Initial state: locks not held
        assertLocksHeld(false)

        // 1. Active Buffering (playWhenReady = true) acquires both locks
        fakeEngine.customPlayWhenReady = true
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertLocksHeld(true)

        // 2. Inactive Buffering (playWhenReady = false) releases both locks
        fakeEngine.customPlayWhenReady = false
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertLocksHeld(false)

        // 3. Playing keeps both locks held
        resumePlayingAndAssertLocksHeld()

        // 4. Paused immediately releases both locks to save battery
        host.handlePlaybackStateChanged(PlaybackState.Paused)
        assertLocksHeld(false)

        // 5. Error immediately releases both locks
        resumePlayingAndAssertLocksHeld()
        host.handlePlaybackStateChanged(PlaybackState.Error("Stream connection timeout"))
        assertLocksHeld(false)

        // 6. Idle immediately releases both locks
        resumePlayingAndAssertLocksHeld()
        host.handlePlaybackStateChanged(PlaybackState.Idle)
        assertLocksHeld(false)

        // 7. Ended immediately releases both locks
        resumePlayingAndAssertLocksHeld()
        host.handlePlaybackStateChanged(PlaybackState.Ended)
        assertLocksHeld(false)

        // 8. Service detachment releases both locks
        resumePlayingAndAssertLocksHeld()
        host.detachService(service)
        assertLocksHeld(false)
        host.attachService(service)

        // 9. stop() releases both locks
        resumePlayingAndAssertLocksHeld()
        host.stop()
        assertLocksHeld(false)

        // 10. release() releases both locks
        resumePlayingAndAssertLocksHeld()
        host.release()
        assertLocksHeld(false)
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
        val appSession =
            com.webdav.player.domain.session.FakeMusicPlayerAppSession(
                initialState =
                    com.webdav.player.domain.model.PlayerSessionState(
                        playbackState = PlaybackState.Playing,
                    ),
            )
        host.bindAppSession(appSession)
        val focusHandler = host.audioFocusHandler

        // Play track
        fakeEngine.loadTrack(testServer, testTrack)
        testScope.runCurrent()

        // Transient loss (incoming call) -> pause
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(1, appSession.pauseCount)
        assertTrue(focusHandler.resumeOnFocusGain)

        // Gain focus -> resumes
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, appSession.playCount) // resumed play
        assertFalse(focusHandler.resumeOnFocusGain)

        // Transient duck (navigation prompt) -> normalized system ducking, application marks isDucked without double-ducking
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue(focusHandler.isDucked)

        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(focusHandler.isDucked)
    }

    @Test
    fun audioFocus_withBoundAppSession_transientLossAndGhostResumptionGuard() {
        val appSession =
            com.webdav.player.domain.session.FakeMusicPlayerAppSession(
                initialState =
                    com.webdav.player.domain.model.PlayerSessionState(
                        playbackState = PlaybackState.Playing,
                    ),
            )
        host.bindAppSession(appSession)

        // Transient loss (call comes in)
        host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(1, appSession.pauseCount)
        assertTrue(host.audioFocusHandler.resumeOnFocusGain)
        assertTrue(appSession.pausedByTransientAudioFocus)

        // User actively pauses during call
        appSession.pause()
        assertFalse(host.audioFocusHandler.resumeOnFocusGain)
        assertFalse(appSession.pausedByTransientAudioFocus)

        // Focus gained (call ends)
        host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0, appSession.playCount) // Ghost resumption blocked!
    }

    @Test
    fun audioFocus_withBoundAppSession_permanentLoss_pausesAndAbandonsFocus() {
        val appSession =
            com.webdav.player.domain.session.FakeMusicPlayerAppSession(
                initialState =
                    com.webdav.player.domain.model.PlayerSessionState(
                        playbackState = PlaybackState.Playing,
                    ),
            )
        host.bindAppSession(appSession)

        // Permanent loss
        host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(1, appSession.pauseCount)
        assertFalse(host.audioFocusHandler.resumeOnFocusGain)
        assertFalse(appSession.pausedByTransientAudioFocus)

        // Gain focus later
        host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0, appSession.playCount)
    }

    @Test
    fun hardwareMediaButtons_dispatchDirectlyToHost() {
        val appSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        host.bindAppSession(appSession)
        val callback = requireNotNull(host.mediaSessionCallback)

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
    fun host_assemblesMediaSessionDirectly_andRoutesCommandsWithoutEngineCallbacks() {
        val media3Engine =
            com.webdav.player.data.player.Media3AudioPlayerEngine(
                context = context,
                coroutineScope = testScope,
            )
        val standaloneHost =
            PlaybackSessionHost(
                context = context,
                engine = media3Engine,
                audioFocusHandler = AudioFocusHandler(context),
                coroutineScope = testScope,
            )
        try {
            assertNotNull(standaloneHost.sessionPlayer)
            assertNotNull(standaloneHost.mediaSession)
            assertEquals(standaloneHost.sessionPlayer, standaloneHost.mediaSession.player)
            assertEquals(
                media3Engine.player,
                (standaloneHost.sessionPlayer as androidx.media3.common.ForwardingPlayer).wrappedPlayer,
            )

            // Before any track is loaded, next/previous media item commands are not forced available
            assertFalse(standaloneHost.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertFalse(standaloneHost.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

            val appSession =
                com.webdav.player.domain.session
                    .FakeMusicPlayerAppSession()
            standaloneHost.bindAppSession(appSession)
            assertEquals(appSession, standaloneHost.appSession)

            var observedSessionMeta: androidx.media3.common.MediaMetadata? = null
            val metadataListener =
                object : Player.Listener {
                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                        observedSessionMeta = mediaMetadata
                    }
                }
            standaloneHost.sessionPlayer.addListener(metadataListener)

            // Verify non-disruptive metadata enrichment flows into host's sessionPlayer, MediaSession, and Notification
            media3Engine.loadTrack(testServer, testTrack, startPositionMs = 5000L)
            org.robolectric.shadows.ShadowLooper
                .idleMainLooper()

            // Single loaded item enables seek-to-next/previous commands on host's sessionPlayer
            assertTrue(standaloneHost.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
            assertTrue(standaloneHost.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertTrue(standaloneHost.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
            assertTrue(standaloneHost.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            assertTrue(standaloneHost.sessionPlayer.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertTrue(standaloneHost.sessionPlayer.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

            // Preroll a second track and verify seekToNext/Previous on sessionPlayer route to appSession
            val secondTrack = testTrack.copy(id = "2", remotePath = "/track2.mp3", title = "Track 2")
            media3Engine.prerollTrack(testServer, secondTrack)
            org.robolectric.shadows.ShadowLooper
                .idleMainLooper()
            assertEquals(2, media3Engine.player.mediaItemCount)
            standaloneHost.sessionPlayer.seekToNext()
            assertEquals(1, appSession.skipNextCount)
            standaloneHost.sessionPlayer.seekToPrevious()
            assertEquals(1, appSession.skipPreviousCount)
            standaloneHost.sessionPlayer.seekToNextMediaItem()
            assertEquals(2, appSession.skipNextCount)
            standaloneHost.sessionPlayer.seekToPreviousMediaItem()
            assertEquals(2, appSession.skipPreviousCount)
            assertEquals(0, media3Engine.player.currentMediaItemIndex)
            assertEquals(5000L, media3Engine.currentPositionMs.value)

            val enrichedTrack =
                testTrack.copy(
                    title = "Host Enriched Title",
                    artist = "Host Enriched Artist",
                    album = "Host Enriched Album",
                )
            media3Engine.updateTrackMetadata(enrichedTrack)
            org.robolectric.shadows.ShadowLooper
                .idleMainLooper()

            assertEquals(
                "Host Enriched Title",
                standaloneHost.mediaSession.player.mediaMetadata.title
                    ?.toString(),
            )
            assertEquals(
                "Host Enriched Artist",
                standaloneHost.mediaSession.player.mediaMetadata.artist
                    ?.toString(),
            )
            assertEquals(
                "Host Enriched Album",
                standaloneHost.mediaSession.player.mediaMetadata.albumTitle
                    ?.toString(),
            )
            assertEquals("Host Enriched Title", observedSessionMeta?.title?.toString())
            assertEquals("Host Enriched Artist", observedSessionMeta?.artist?.toString())
            assertEquals("Host Enriched Album", observedSessionMeta?.albumTitle?.toString())
            standaloneHost.sessionPlayer.removeListener(metadataListener)

            val notification = standaloneHost.buildCurrentNotification()
            assertEquals(
                "Host Enriched Title",
                notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
            )
            assertEquals(
                "Host Enriched Artist",
                notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
            )

            // Verify media buttons and player commands route to appSession without engine callbacks
            val callback = requireNotNull(standaloneHost.mediaSessionCallback)
            val nextIntent =
                Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
                }
            assertTrue(callback.handleMediaButtonIntent(nextIntent))
            assertEquals(3, appSession.skipNextCount)

            val prevIntent =
                Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                }
            assertTrue(callback.handleMediaButtonIntent(prevIntent))
            assertEquals(3, appSession.skipPreviousCount)
        } finally {
            standaloneHost.release()
        }
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
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

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
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
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
    fun becomingNoisy_whenAppSessionNotBound_doesNotBypassSessionSeam() {
        assertEquals(0, fakeEngine.pauseCount)

        host.handlePlaybackStateChanged(PlaybackState.Playing)
        assertTrue(host.isBecomingNoisyRegistered)

        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        // Strict ADR-0018 conformance: no Option B bypass to playerEngine
        assertEquals(0, fakeEngine.pauseCount)
    }

    @Test
    fun becomingNoisy_whenBufferingAndPlayWhenReady_registersReceiver_andBroadcastTriggersAppSessionPause() {
        val appSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        host.bindAppSession(appSession)

        assertFalse(host.isBecomingNoisyRegistered)

        // Enter Buffering state with playWhenReady = true -> receiver should be registered
        fakeEngine.customPlayWhenReady = true
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertTrue(host.isBecomingNoisyRegistered)

        // Send ACTION_AUDIO_BECOMING_NOISY broadcast
        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        // Assert appSession.pause() was invoked
        assertEquals(1, appSession.pauseCount)
    }

    @Test
    fun becomingNoisy_whenBufferingAndPlayWhenReadyFalse_unregistersReceiver() {
        val appSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        host.bindAppSession(appSession)

        // Enter Buffering with playWhenReady = true -> registered
        fakeEngine.customPlayWhenReady = true
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertTrue(host.isBecomingNoisyRegistered)

        // State change when playWhenReady = false in Buffering -> unregisters
        fakeEngine.customPlayWhenReady = false
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertFalse(host.isBecomingNoisyRegistered)
    }

    @Test
    fun becomingNoisy_duringBuffering_interceptsBroadcast_andSafelyDegradesToPaused() {
        val appSession =
            com.webdav.player.domain.session
                .FakeMusicPlayerAppSession()
        host.bindAppSession(appSession)

        // Enter Buffering state with playWhenReady = true
        fakeEngine.customPlayWhenReady = true
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertTrue(host.isBecomingNoisyRegistered)
        assertTrue(host.hardwareLockManager.isWakeLockHeld)
        assertTrue(host.hardwareLockManager.isWifiLockHeld)

        // System dispatches ACTION_AUDIO_BECOMING_NOISY
        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        // AppSession receives pause
        assertEquals(1, appSession.pauseCount)

        // Session and engine converge to Paused
        fakeEngine.pause()
        host.handlePlaybackStateChanged(fakeEngine.playbackState.value)

        // Safe degradation asserted
        assertFalse(host.isBecomingNoisyRegistered)
        assertFalse(host.hardwareLockManager.isWakeLockHeld)
        assertFalse(host.hardwareLockManager.isWifiLockHeld)
        assertTrue(host.isDemotionPending)
    }

    @Test
    fun becomingNoisy_whenBufferingAndAppSessionNotBound_doesNotBypassSessionSeam() {
        assertEquals(0, fakeEngine.pauseCount)

        fakeEngine.customPlayWhenReady = true
        host.handlePlaybackStateChanged(PlaybackState.Buffering)
        assertTrue(host.isBecomingNoisyRegistered)

        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        // Strict ADR-0018 conformance: no Option B bypass to playerEngine
        assertEquals(0, fakeEngine.pauseCount)
    }

    @Test
    fun buildCurrentNotification_acrossPlaybackStatesAndWeakNetwork_constructsAccurateNotificationContentAndActions() {
        val mediaItem =
            androidx.media3.common.MediaItem
                .Builder()
                .setUri("http://127.0.0.1:8080/dav/track.mp3")
                .setMediaId(testTrack.id)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata
                        .Builder()
                        .setTitle("Symphony No. 5")
                        .setArtist("Beethoven")
                        .setAlbumTitle("Classical Collection")
                        .build(),
                ).build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // 1. Playing state -> ongoing = true, action = Pause, album subtext
        val playingNotification = host.buildCurrentNotification(PlaybackState.Playing)
        assertEquals("Symphony No. 5", playingNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString())
        assertEquals("Beethoven", playingNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString())
        assertEquals(
            "Classical Collection",
            playingNotification.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString(),
        )
        assertTrue(playingNotification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Pause", playingNotification.actions[1].title.toString())

        // 2. Buffering with weak network -> ongoing = true, weak network subtext
        val weakNetNotification = host.buildCurrentNotification(PlaybackState.Buffering, isWeakNetwork = true)
        assertEquals("网络受阻，正在重连...", weakNetNotification.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString())
        assertTrue(weakNetNotification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Pause", weakNetNotification.actions[1].title.toString())

        // 3. Paused state -> ongoing = false, action = Play
        val pausedNotification = host.buildCurrentNotification(PlaybackState.Paused)
        assertEquals(0, pausedNotification.flags and android.app.Notification.FLAG_ONGOING_EVENT)
        assertEquals("Play", pausedNotification.actions[1].title.toString())

        // 4. Error state -> ongoing = false, error message as artist text, action = Play
        val errorNotification = host.buildCurrentNotification(PlaybackState.Error("Stream timeout"))
        assertEquals(0, errorNotification.flags and android.app.Notification.FLAG_ONGOING_EVENT)
        assertEquals("Stream timeout", errorNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString())
        assertEquals("Play", errorNotification.actions[1].title.toString())
    }

    @Test
    fun host_doesNotExposeAudioRoutingLeakageAcrossEngineSeam() {
        val hostMethods =
            PlaybackSessionHost::class.java.declaredMethods
                .map { it.name }
                .toSet()
        val engineMethods =
            com.webdav.player.domain.player.AudioPlayerEngine::class.java.declaredMethods
                .map { it.name }
                .toSet()

        assertFalse("PlaybackSessionHost must not expose getAudioRoutingObserver", "getAudioRoutingObserver" in hostMethods)
        assertFalse("PlaybackSessionHost must not expose onAudioRoutingChanged", "onAudioRoutingChanged" in hostMethods)
        assertFalse("PlaybackSessionHost must not expose attachAudioRouting", "attachAudioRouting" in hostMethods)
        assertFalse("PlaybackSessionHost must not expose detachAudioRouting", "detachAudioRouting" in hostMethods)
        assertFalse("AudioPlayerEngine must not expose onAudioRoutingChanged", "onAudioRoutingChanged" in engineMethods)
        assertFalse(
            "PlaybackSessionHost must not implement AudioPlayerEngine",
            com.webdav.player.domain.player.AudioPlayerEngine::class.java.isAssignableFrom(PlaybackSessionHost::class.java),
        )
    }

    @Test
    fun elevateForeground_whenServiceNull_recordsPendingElevationAndHandlesExceptionResiliently() {
        host.detachService(service)
        assertNull(host.attachedService)
        assertFalse(host.isForegroundActive)
        assertFalse(host.isElevationPending)

        fakeEngine._playbackState.value = PlaybackState.Playing
        host.elevateForeground(PlaybackState.Playing)

        assertTrue("Elevation must be marked pending when service is detached", host.isElevationPending)
        assertFalse("Foreground should not be active without service", host.isForegroundActive)
        assertEquals(PlaybackState.Playing, host.playerEngine.playbackState.value)
    }

    @Test
    fun prewarmService_whenElevationPending_reconcilesForegroundElevation() {
        host.detachService(service)
        fakeEngine._playbackState.value = PlaybackState.Playing
        host.elevateForeground(PlaybackState.Playing)
        assertTrue(host.isElevationPending)

        host.attachService(service)
        host.prewarmService()

        assertFalse("Pending flag should clear once reconciled", host.isElevationPending)
        assertTrue("Foreground should become active", host.isForegroundActive)
        assertEquals(service, host.attachedService)
    }

    @Test
    fun prewarmService_whenActivelyPlayingAndNotForegroundActive_elevatesForeground() {
        host.detachService(service)
        host.attachService(service)
        host.demoteForeground(removeNotification = false)
        assertFalse(host.isForegroundActive)

        fakeEngine._playbackState.value = PlaybackState.Playing
        host.prewarmService()

        assertTrue("prewarmService must elevate foreground if actively playing", host.isForegroundActive)
        assertEquals(service, host.attachedService)
    }
}
