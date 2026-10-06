package com.webdav.player.data.service

import android.content.Intent
import android.view.KeyEvent
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
class WebDavMediaSessionCallbackTest {

    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var callback: WebDavMediaSessionCallback

    private val testServer = WebDavServer(
        id = 1L,
        name = "Test",
        url = "http://localhost:8080/dav",
        port = 8080,
        pathPrefix = "/dav"
    )

    private val testTracks = listOf(
        AudioTrack("1", 1L, "/1.mp3", "Song 1", format = AudioFormat.MP3, size = 100L),
        AudioTrack("2", 1L, "/2.mp3", "Song 2", format = AudioFormat.MP3, size = 200L),
        AudioTrack("3", 1L, "/3.mp3", "Song 3", format = AudioFormat.MP3, size = 300L)
    )

    @Before
    fun setUp() {
        fakeEngine = FakeAudioPlayerEngine()
        callback = WebDavMediaSessionCallback(fakeEngine)
    }

    private fun createMediaButtonIntent(keyCode: Int, action: Int = KeyEvent.ACTION_DOWN): Intent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val event = KeyEvent(action, keyCode)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, event)
        return intent
    }

    @Test
    fun handleMediaButton_play_callsPlay() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeEngine.playCount)
    }

    @Test
    fun handleMediaButton_pause_callsPause() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeEngine.pauseCount)
    }

    @Test
    fun handleMediaButton_playPauseToggle_whenPlaying_callsPause() {
        fakeEngine.playTracks(testServer, testTracks, startIndex = 0)
        assertEquals(PlaybackState.Playing, fakeEngine.playbackState.value)

        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeEngine.pauseCount)
    }

    @Test
    fun handleMediaButton_playPauseToggle_whenPaused_callsPlay() {
        fakeEngine.playTracks(testServer, testTracks, startIndex = 0)
        fakeEngine.pause()
        assertEquals(PlaybackState.Paused, fakeEngine.playbackState.value)

        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        // playTracks set it once, and now toggled play once more
        assertEquals(1, fakeEngine.playCount)
    }

    @Test
    fun handleMediaButton_headsetHook_togglesPlayPause() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_HEADSETHOOK)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeEngine.playCount)
    }

    @Test
    fun handleMediaButton_next_callsSkipToNext() {
        fakeEngine.playTracks(testServer, testTracks, startIndex = 0)

        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeEngine.currentTrackIndex.value)
    }

    @Test
    fun handleMediaButton_previous_callsSkipToPrevious() {
        fakeEngine.playTracks(testServer, testTracks, startIndex = 1)

        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(0, fakeEngine.currentTrackIndex.value)
    }

    @Test
    fun handleMediaButton_stop_callsStop() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_STOP)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeEngine.stopCount)
    }

    @Test
    fun handleMediaButton_actionUp_isIgnored() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY, action = KeyEvent.ACTION_UP)
        val handled = callback.handleMediaButtonIntent(intent)

        assertFalse(handled)
        assertEquals(0, fakeEngine.playCount)
    }

    @Test
    fun handleMediaButton_unknownKey_isNotHandled() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_VOLUME_UP)
        val handled = callback.handleMediaButtonIntent(intent)

        assertFalse(handled)
    }

    @Test
    fun handleMediaButton_nonMediaButtonAction_isNotHandled() {
        val intent = Intent(Intent.ACTION_VIEW)
        val handled = callback.handleMediaButtonIntent(intent)

        assertFalse(handled)
    }

    @Test
    fun handleMediaButton_next_and_previous_delegatesToSkipHandler_whenActive() {
        fakeEngine.playTracks(testServer, testTracks, startIndex = 0)

        var nextCount = 0
        var prevCount = 0
        fakeEngine.setSkipHandler(
            object : com.webdav.player.domain.player.AudioPlayerEngine.SkipHandler {
                override fun onSkipToNext(): Boolean {
                    nextCount++
                    return true
                }

                override fun onSkipToPrevious(): Boolean {
                    prevCount++
                    return true
                }
            },
        )

        val nextIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT)
        val handledNext = callback.handleMediaButtonIntent(nextIntent)
        assertTrue(handledNext)
        assertEquals(1, nextCount)

        val prevIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val handledPrev = callback.handleMediaButtonIntent(prevIntent)
        assertTrue(handledPrev)
        assertEquals(1, prevCount)
    }
}
