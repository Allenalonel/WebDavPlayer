package com.webdav.player.data.player

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
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
    private lateinit var fakeSession: FakeMusicPlayerAppSession
    private lateinit var focusHandler: AudioFocusHandler

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test",
            url = "http://localhost:8080/dav",
            port = 8080,
            pathPrefix = "/dav",
        )

    private val testTrack =
        AudioTrack(
            id = "1",
            serverId = 1L,
            remotePath = "/1.mp3",
            title = "Song 1",
            format = AudioFormat.MP3,
            size = 100L,
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeSession =
            FakeMusicPlayerAppSession(
                initialState =
                    PlayerSessionState(
                        activeServer = testServer,
                        queue = com.webdav.player.domain.model.PlaybackQueue(listOf(testTrack), 0),
                        playbackState = PlaybackState.Playing,
                    ),
            )
        focusHandler = AudioFocusHandler(context, appSession = fakeSession)
    }

    @Test
    fun incomingCall_lossTransient_pausesPlayback_andResumesOnCallEnd() {
        assertTrue(fakeSession.sessionState.value.isPlaying)

        // Incoming call -> AUDIOFOCUS_LOSS_TRANSIENT
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        assertEquals(1, fakeSession.pauseCount)
        assertTrue(fakeSession.sessionState.value.isPaused)
        assertTrue(focusHandler.resumeOnFocusGain)
        assertTrue(fakeSession.pausedByTransientAudioFocus)

        // Call ended -> AUDIOFOCUS_GAIN
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(1, fakeSession.playCount)
        assertFalse(focusHandler.resumeOnFocusGain)
        assertFalse(fakeSession.pausedByTransientAudioFocus)
    }

    @Test
    fun ghostResumptionGuard_userPausesDuringCall_blocksGhostResumption() {
        // 1. Playback interrupted by phone call
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue("Transient loss sets resume flag", focusHandler.resumeOnFocusGain)
        assertTrue(fakeSession.pausedByTransientAudioFocus)

        // 2. User explicitly taps Pause on notification bar / UI while on the call
        fakeSession.pause()
        assertFalse("Explicit pause resets resume flag", focusHandler.resumeOnFocusGain)
        assertFalse("Explicit pause clears session transient focus flag", fakeSession.pausedByTransientAudioFocus)

        // 3. Phone call ends -> AUDIOFOCUS_GAIN
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        // 4. Ghost resumption is strictly blocked: play() is NEVER called
        assertEquals(0, fakeSession.playCount)
        assertFalse(focusHandler.resumeOnFocusGain)
    }

    @Test
    fun ghostResumptionGuard_userTogglesPlayPauseDuringCall_blocksGhostResumption() {
        // Interrupted by call
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(focusHandler.resumeOnFocusGain)

        // User triggers togglePlayPause
        fakeSession.togglePlayPause()
        assertFalse(focusHandler.resumeOnFocusGain)
        assertFalse(fakeSession.pausedByTransientAudioFocus)

        // Regain focus -> no automatic play
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0, fakeSession.playCount)
    }

    @Test
    fun ghostResumptionGuard_userStopsDuringCall_blocksGhostResumption() {
        // Interrupted by call
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertTrue(focusHandler.resumeOnFocusGain)

        // User triggers stop
        fakeSession.stop()
        assertFalse(focusHandler.resumeOnFocusGain)
        assertFalse(fakeSession.pausedByTransientAudioFocus)

        // Regain focus -> no automatic play
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0, fakeSession.playCount)
    }

    @Test
    fun transientLoss_whenAlreadyPaused_doesNotResumeOnFocusGain() {
        // Initially paused
        fakeSession.pause()
        assertEquals(1, fakeSession.pauseCount)

        // Incoming call -> transient loss
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertFalse(focusHandler.resumeOnFocusGain)
        assertFalse(fakeSession.pausedByTransientAudioFocus)

        // Regain focus
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        // Should not have triggered extra play
        assertEquals(0, fakeSession.playCount)
    }

    @Test
    fun permanentLoss_pausesSession_abandonsFocus_andDoesNotResumeOnGain() {
        // Permanent loss (another music app starts)
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        assertEquals(1, fakeSession.pauseCount)
        assertFalse(focusHandler.resumeOnFocusGain)
        assertFalse(fakeSession.pausedByTransientAudioFocus)

        // Regain focus later should not auto-resume
        focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0, fakeSession.playCount)
    }

    @Test
    fun systemNotification_lossTransientCanDuck_setsDucked_andRestoresOnGain() {
        var recordedVolume: Float? = null
        val customHandler =
            AudioFocusHandler(
                context = context,
                appSession = fakeSession,
                onVolumeChanged = { volume -> recordedVolume = volume },
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
    fun requestAndAbandonAudioFocus_succeeds() {
        val granted = focusHandler.requestAudioFocus()
        assertTrue(granted)

        focusHandler.abandonAudioFocus()
        assertFalse(focusHandler.isDucked)
        assertFalse(focusHandler.resumeOnFocusGain)
    }

    @Test
    fun legacyPlayerEngine_backwardCompatibility_worksWhenAppSessionNotProvided() {
        val fakeEngine = FakeAudioPlayerEngine()

        @Suppress("DEPRECATION")
        val legacyHandler = AudioFocusHandler(context, fakeEngine)

        fakeEngine.loadTrack(testServer, testTrack)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

        legacyHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(1, fakeEngine.pauseCount)
        assertTrue(legacyHandler.resumeOnFocusGain)

        legacyHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, fakeEngine.playCount)
        assertFalse(legacyHandler.resumeOnFocusGain)
    }
}
