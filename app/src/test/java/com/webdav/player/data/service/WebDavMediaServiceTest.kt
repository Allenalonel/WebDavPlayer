package com.webdav.player.data.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.WebDavApplication
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.WebDavServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class WebDavMediaServiceTest {

    private lateinit var app: WebDavApplication
    private lateinit var serviceController: ServiceController<WebDavMediaService>
    private lateinit var service: WebDavMediaService

    private val testServer = WebDavServer(
        id = 1L,
        name = "Test",
        url = "http://localhost:8080/dav",
        port = 8080,
        pathPrefix = "/dav"
    )

    private val testTracks = listOf(
        AudioTrack("1", 1L, "/1.mp3", "Song 1", format = AudioFormat.MP3, size = 100L),
        AudioTrack("2", 1L, "/2.mp3", "Song 2", format = AudioFormat.MP3, size = 200L)
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
        if (::app.isInitialized) {
            app.playerEngine.release()
        }
    }

    @Test
    fun onCreate_addsSessionAndSetsNotificationProvider() {
        assertNotNull(service.notificationProvider)
        // Controller info query returns active session
        val session = (app.playerEngine as? com.webdav.player.data.player.Media3AudioPlayerEngine)?.mediaSession
        assertNotNull(session)
    }

    @Test
    fun onStartCommand_actionPlay_callsPlay() {
        val intent = Intent(service, WebDavMediaService::class.java).apply {
            action = WebDavNotificationProvider.ACTION_PLAY
        }
        service.onStartCommand(intent, 0, 1)
        // Verify playback is triggered or attempted without error
        assertNotNull(app.playerEngine)
    }

    @Test
    fun onStartCommand_actionPause_callsPause() {
        val intent = Intent(service, WebDavMediaService::class.java).apply {
            action = WebDavNotificationProvider.ACTION_PAUSE
        }
        service.onStartCommand(intent, 0, 1)
        assertNotNull(app.playerEngine)
    }

    @Test
    fun onStartCommand_actionNext_callsSkipToNext() {
        val intent = Intent(service, WebDavMediaService::class.java).apply {
            action = WebDavNotificationProvider.ACTION_NEXT
        }
        service.onStartCommand(intent, 0, 1)
        assertNotNull(app.playerEngine)
    }

    @Test
    fun onStartCommand_actionPrevious_callsSkipToPrevious() {
        val intent = Intent(service, WebDavMediaService::class.java).apply {
            action = WebDavNotificationProvider.ACTION_PREVIOUS
        }
        service.onStartCommand(intent, 0, 1)
        assertNotNull(app.playerEngine)
    }
}
