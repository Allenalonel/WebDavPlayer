package com.webdav.player.data.service

import android.content.Intent
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import com.webdav.player.domain.session.MusicPlayerAppSession
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

    private class RecordingAppSession : MusicPlayerAppSession by FakeMusicPlayerAppSession() {
        var skipToNextCount = 0
        var skipToPreviousCount = 0

        override fun skipToNext() {
            skipToNextCount++
        }

        override fun skipToPrevious() {
            skipToPreviousCount++
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateInstance(clazz: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstanceMethod.invoke(unsafe, clazz) as T
    }

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

    @Test
    fun handleMediaButton_next_withAppSession_directlyCallsAppSessionSkipToNextWithoutEngine() {
        val appSession = RecordingAppSession()
        callback.appSession = appSession

        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, appSession.skipToNextCount)
        assertEquals(0, fakeEngine.nextCount)
    }

    @Test
    fun handleMediaButton_previous_withAppSession_directlyCallsAppSessionSkipToPreviousWithoutEngine() {
        val appSession = RecordingAppSession()
        callback.appSession = appSession

        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, appSession.skipToPreviousCount)
        assertEquals(0, fakeEngine.previousCount)
    }

    @Test
    fun handleMediaButton_nextAndPrevious_withNullAppSession_fallsBackToPlayerEngine() {
        callback.appSession = null

        val nextIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT)
        val handledNext = callback.handleMediaButtonIntent(nextIntent)
        assertTrue(handledNext)
        assertEquals(1, fakeEngine.nextCount)

        val prevIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val handledPrev = callback.handleMediaButtonIntent(prevIntent)
        assertTrue(handledPrev)
        assertEquals(1, fakeEngine.previousCount)
    }

    @Test
    fun onPlayerCommandRequest_seekToNext_triggersAppSessionSkipToNext() {
        val appSession = RecordingAppSession()
        callback.appSession = appSession
        val session = allocateInstance(MediaSession::class.java)
        val controller = allocateInstance(MediaSession.ControllerInfo::class.java)

        val resultNext = callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_NEXT)
        assertEquals(SessionResult.RESULT_SUCCESS, resultNext)
        assertEquals(1, appSession.skipToNextCount)

        val resultNextItem = callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        assertEquals(SessionResult.RESULT_SUCCESS, resultNextItem)
        assertEquals(2, appSession.skipToNextCount)
        assertEquals(0, fakeEngine.nextCount)
    }

    @Test
    fun onPlayerCommandRequest_seekToPrevious_triggersAppSessionSkipToPrevious() {
        val appSession = RecordingAppSession()
        callback.appSession = appSession
        val session = allocateInstance(MediaSession::class.java)
        val controller = allocateInstance(MediaSession.ControllerInfo::class.java)

        val resultPrev = callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_PREVIOUS)
        assertEquals(SessionResult.RESULT_SUCCESS, resultPrev)
        assertEquals(1, appSession.skipToPreviousCount)

        val resultPrevItem = callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        assertEquals(SessionResult.RESULT_SUCCESS, resultPrevItem)
        assertEquals(2, appSession.skipToPreviousCount)
        assertEquals(0, fakeEngine.previousCount)
    }

    @Test
    fun onPlayerCommandRequest_withNullAppSession_returnsSuccessWithoutCallingSkip() {
        callback.appSession = null
        val session = allocateInstance(MediaSession::class.java)
        val controller = allocateInstance(MediaSession.ControllerInfo::class.java)

        val resultNext = callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_NEXT)
        assertEquals(SessionResult.RESULT_SUCCESS, resultNext)
        assertEquals(0, fakeEngine.nextCount)

        val resultPrev = callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_PREVIOUS)
        assertEquals(SessionResult.RESULT_SUCCESS, resultPrev)
        assertEquals(0, fakeEngine.previousCount)
    }

    @Test
    fun constructor_acceptsAppSession() {
        val appSession = RecordingAppSession()
        val callbackWithSession = WebDavMediaSessionCallback(fakeEngine, appSession)
        assertEquals(appSession, callbackWithSession.appSession)
    }
}
