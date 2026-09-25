package com.webdav.player.data.player

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioFocusHandlerTest {

    private lateinit var context: Context
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var focusHandler: AudioFocusHandler

    private val testServer = WebDavServer(
        id = 1L,
        name = "Test",
        url = "http://localhost:8080/dav",
        port = 8080,
        pathPrefix = "/dav"
    )

    private val testTrack = AudioTrack(
        id = "1",
        serverId = 1L,
        remotePath = "/1.mp3",
        title = "Song 1",
        format = AudioFormat.MP3,
        size = 100L
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeEngine = FakeAudioPlayerEngine()
        focusHandler = AudioFocusHandler(context, fakeEngine)
    }

    @Test
    fun incomingCall_lossTransient_pausesPlayback_andResumesOnCallEnd() {
        // Start playing
        fakeEngine.playTracks(testServer, listOf(testTrack), startIndex = 0)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

        // Incoming call -> AUDIOFOCUS_LOSS_TRANSIENT
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        assertEquals(1, fakeEngine.pauseCount)
        assertTrue(focusHandler.resumeOnFocusGain)

        // Call ended -> AUDIOFOCUS_GAIN
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(1, fakeEngine.playCount)
        assertFalse(focusHandler.resumeOnFocusGain)
    }

    @Test
    fun transientLoss_whenAlreadyPaused_doesNotResumeOnFocusGain() {
        // App is paused
        fakeEngine.playTracks(testServer, listOf(testTrack), startIndex = 0)
        fakeEngine.pause()
        assertEquals(PlaybackState.Paused, fakeEngine.playbackState.value)

        // Loss transient
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse(focusHandler.resumeOnFocusGain)

        // Regain focus
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        // Should not have triggered extra play
        assertEquals(0, fakeEngine.playCount)
    }

    @Test
    fun systemNotification_lossTransientCanDuck_setsDucked_andRestoresOnGain() {
        var recordedVolume: Float? = null
        val customHandler = AudioFocusHandler(
            context = context,
            playerEngine = fakeEngine,
            onVolumeChanged = { volume -> recordedVolume = volume }
        )

        // System notification / navigation prompt
        customHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertTrue(customHandler.isDucked)
        assertEquals(0.2f, recordedVolume)

        // Prompt finished -> AUDIOFOCUS_GAIN
        customHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertFalse(customHandler.isDucked)
        assertEquals(1.0f, recordedVolume)
    }

    @Test
    fun permanentLoss_pausesPlayback_andDoesNotResumeOnGain() {
        fakeEngine.playTracks(testServer, listOf(testTrack), startIndex = 0)

        // Permanent loss (another music app starts)
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        assertEquals(1, fakeEngine.pauseCount)
        assertFalse(focusHandler.resumeOnFocusGain)

        // Regain focus later should not auto-resume
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0, fakeEngine.playCount)
    }

    @Test
    fun requestAndAbandonAudioFocus_succeeds() {
        val granted = focusHandler.requestAudioFocus()
        assertTrue(granted)

        focusHandler.abandonAudioFocus()
        assertFalse(focusHandler.isDucked)
        assertFalse(focusHandler.resumeOnFocusGain)
    }
}
