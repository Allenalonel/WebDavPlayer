package com.webdav.player.data.service

import android.content.Intent
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
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
    private lateinit var fakeSession: FakeMusicPlayerAppSession
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

    @Before
    fun setUp() {
        fakeSession = FakeMusicPlayerAppSession()
        callback = WebDavMediaSessionCallback(fakeSession)
    }

    private fun createMediaButtonIntent(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
    ): Intent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val event = KeyEvent(action, keyCode)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, event)
        return intent
    }

    @Test
    fun handleMediaButton_play_callsAppSessionPlay() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeSession.playCount)
    }

    @Test
    fun handleMediaButton_pause_callsAppSessionPause() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeSession.pauseCount)
    }

    @Test
    fun handleMediaButton_playPauseToggle_callsAppSessionTogglePlayPause() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeSession.togglePlayPauseCount)
    }

    @Test
    fun handleMediaButton_headsetHook_callsAppSessionTogglePlayPause() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_HEADSETHOOK)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeSession.togglePlayPauseCount)
    }

    @Test
    fun handleMediaButton_next_callsAppSessionSkipToNext() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeSession.skipNextCount)
    }

    @Test
    fun handleMediaButton_previous_callsAppSessionSkipToPrevious() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeSession.skipPreviousCount)
    }

    @Test
    fun handleMediaButton_stop_callsAppSessionStop() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_STOP)
        val handled = callback.handleMediaButtonIntent(intent)

        assertTrue(handled)
        assertEquals(1, fakeSession.stopCount)
    }

    @Test
    fun handleMediaButton_actionUp_isIgnored() {
        val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY, action = KeyEvent.ACTION_UP)
        val handled = callback.handleMediaButtonIntent(intent)

        assertFalse(handled)
        assertEquals(0, fakeSession.playCount)
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
    fun onPlayerCommandRequest_seekToNext_triggersAppSessionSkipToNext() {
        val appSession = RecordingAppSession()
        val callbackWithSession = WebDavMediaSessionCallback(appSession)
        val session = allocateInstance(MediaSession::class.java)
        val controller = allocateInstance(MediaSession.ControllerInfo::class.java)

        val resultNext = callbackWithSession.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_NEXT)
        assertEquals(SessionResult.RESULT_SUCCESS, resultNext)
        assertEquals(1, appSession.skipToNextCount)

        val resultNextItem = callbackWithSession.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        assertEquals(SessionResult.RESULT_SUCCESS, resultNextItem)
        assertEquals(2, appSession.skipToNextCount)
    }

    @Test
    fun onPlayerCommandRequest_seekToPrevious_triggersAppSessionSkipToPrevious() {
        val appSession = RecordingAppSession()
        val callbackWithSession = WebDavMediaSessionCallback(appSession)
        val session = allocateInstance(MediaSession::class.java)
        val controller = allocateInstance(MediaSession.ControllerInfo::class.java)

        val resultPrev = callbackWithSession.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_PREVIOUS)
        assertEquals(SessionResult.RESULT_SUCCESS, resultPrev)
        assertEquals(1, appSession.skipToPreviousCount)

        val resultPrevItem = callbackWithSession.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        assertEquals(SessionResult.RESULT_SUCCESS, resultPrevItem)
        assertEquals(2, appSession.skipToPreviousCount)
    }

    @Test
    fun constructor_acceptsAppSession() {
        val appSession = RecordingAppSession()
        val callbackWithSession = WebDavMediaSessionCallback(appSession)
        assertEquals(appSession, callbackWithSession.appSession)
    }

    @Test
    fun onSkipToNext_directlyInvokesAppSessionSkipToNext() {
        val appSession = RecordingAppSession()
        val callbackWithSession = WebDavMediaSessionCallback(appSession)

        callbackWithSession.onSkipToNext()
        assertEquals(1, appSession.skipToNextCount)
    }

    @Test
    fun onSkipToPrevious_directlyInvokesAppSessionSkipToPrevious() {
        val appSession = RecordingAppSession()
        val callbackWithSession = WebDavMediaSessionCallback(appSession)

        callbackWithSession.onSkipToPrevious()
        assertEquals(1, appSession.skipToPreviousCount)
    }
}
