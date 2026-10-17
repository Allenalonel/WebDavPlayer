package com.webdav.player.data.service

import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.WebDavApplication
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class WebDavMediaServiceTest {
    private lateinit var app: WebDavApplication
    private lateinit var serviceController: ServiceController<WebDavMediaService>
    private lateinit var service: WebDavMediaService

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test",
            url = "http://localhost:8080/dav",
            port = 8080,
            pathPrefix = "/dav",
        )

    private val testTracks =
        listOf(
            AudioTrack("1", 1L, "/1.mp3", "Song 1", format = AudioFormat.MP3, size = 100L),
            AudioTrack("2", 1L, "/2.mp3", "Song 2", format = AudioFormat.MP3, size = 200L),
        )

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        serviceController = Robolectric.buildService(WebDavMediaService::class.java)
        service = serviceController.create().get()
    }

    @After
    fun tearDown() {
        serviceController.destroy()
        PlaybackSessionHost.resetForTesting()
    }

    @Test
    fun onCreate_addsSessionAndSetsNotificationProvider() {
        assertNotNull(service.notificationProvider)
        // Service owns its mediaSession and playerEngine cohesively without app downcast
        assertNotNull(service.mediaSession)
        assertNotNull(service.playerEngine)
    }

    @Test
    fun onCreate_synchronouslyInvokesStartForeground_withValidNotificationOnFirstLifecycleFrame() {
        val shadowService = shadowOf(service)
        val host = PlaybackSessionHost.getInstance(app)

        // Verifies immediate synchronous foreground elevation on frame 0
        assertNotNull(shadowService.lastForegroundNotification)
        assertEquals(host.notificationProvider.notificationId, shadowService.lastForegroundNotificationId)
        assertFalse(shadowService.isForegroundStopped)

        // Lifecycle seam state is harmonized
        assertEquals(service, host.attachedService)
        assertTrue(host.isForegroundActive)
    }

    @Test
    fun onStartCommand_actionPlay_callsAppSessionPlay() {
        val fakeSession = FakeMusicPlayerAppSession()
        val host = PlaybackSessionHost.getInstance(app)
        host.bindAppSession(fakeSession)

        val intent =
            Intent(service, WebDavMediaService::class.java).apply {
                action = WebDavNotificationProvider.ACTION_PLAY
            }
        service.onStartCommand(intent, 0, 1)
        assertEquals(1, fakeSession.playCount)
    }

    @Test
    fun onStartCommand_actionPause_callsAppSessionPause() {
        val fakeSession = FakeMusicPlayerAppSession()
        val host = PlaybackSessionHost.getInstance(app)
        host.bindAppSession(fakeSession)

        val intent =
            Intent(service, WebDavMediaService::class.java).apply {
                action = WebDavNotificationProvider.ACTION_PAUSE
            }
        service.onStartCommand(intent, 0, 1)
        assertEquals(1, fakeSession.pauseCount)
    }

    @Test
    fun onStartCommand_actionNext_callsAppSessionSkipToNext() {
        val fakeSession = FakeMusicPlayerAppSession()
        val host = PlaybackSessionHost.getInstance(app)
        host.bindAppSession(fakeSession)

        val intent =
            Intent(service, WebDavMediaService::class.java).apply {
                action = WebDavNotificationProvider.ACTION_NEXT
            }
        service.onStartCommand(intent, 0, 1)
        assertEquals(1, fakeSession.skipNextCount)
    }

    @Test
    fun onStartCommand_actionPrevious_callsAppSessionSkipToPrevious() {
        val fakeSession = FakeMusicPlayerAppSession()
        val host = PlaybackSessionHost.getInstance(app)
        host.bindAppSession(fakeSession)

        val intent =
            Intent(service, WebDavMediaService::class.java).apply {
                action = WebDavNotificationProvider.ACTION_PREVIOUS
            }
        service.onStartCommand(intent, 0, 1)
        assertEquals(1, fakeSession.skipPreviousCount)
    }

    @Test
    fun onStartCommand_returnsStartSticky() {
        val intent = Intent(service, WebDavMediaService::class.java)
        val result = service.onStartCommand(intent, 0, 1)
        assertEquals(Service.START_STICKY, result)
    }

    @Test
    fun onTaskRemoved_whenPlayingOrDemotionPending_doesNotStopSelf() {
        val host = PlaybackSessionHost.getInstance(app)
        host.handlePlaybackStateChanged(PlaybackState.Playing)

        service.onTaskRemoved(Intent())
        assertFalse(shadowOf(service).isStoppedBySelf)

        host.handlePlaybackStateChanged(PlaybackState.Paused)
        assertTrue(host.isForegroundActive)
        service.onTaskRemoved(Intent())
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun onTaskRemoved_whenDemotedAndIdle_stopsSelf() {
        val host = PlaybackSessionHost.getInstance(app)
        host.stop()

        service.onTaskRemoved(Intent())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
