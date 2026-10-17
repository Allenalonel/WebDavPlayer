package com.webdav.player.data.service

import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.webdav.player.domain.session.MusicPlayerAppSession

class WebDavMediaSessionCallback(
    val appSession: MusicPlayerAppSession,
) : MediaSession.Callback {
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        val handled = handleMediaButtonIntent(intent)
        return if (handled) true else super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    @Suppress("DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int =
        when (playerCommand) {
            Player.COMMAND_PLAY_PAUSE -> {
                appSession.togglePlayPause()
                SessionResult.RESULT_SUCCESS
            }

            Player.COMMAND_STOP -> {
                appSession.stop()
                SessionResult.RESULT_SUCCESS
            }

            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> {
                appSession.skipToNext()
                SessionResult.RESULT_SUCCESS
            }

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> {
                appSession.skipToPrevious()
                SessionResult.RESULT_SUCCESS
            }

            else -> {
                super.onPlayerCommandRequest(session, controller, playerCommand)
            }
        }

    fun handleMediaButtonIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) {
            return false
        }
        val keyEvent =
            IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_KEY_EVENT,
                KeyEvent::class.java,
            ) ?: return false
        if (keyEvent.action == KeyEvent.ACTION_UP) {
            return true
        }
        if (keyEvent.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        return when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                appSession.play()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                appSession.pause()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> {
                appSession.togglePlayPause()
                true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                appSession.skipToNext()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                appSession.skipToPrevious()
                true
            }

            KeyEvent.KEYCODE_MEDIA_STOP -> {
                appSession.stop()
                true
            }

            else -> {
                false
            }
        }
    }
}
