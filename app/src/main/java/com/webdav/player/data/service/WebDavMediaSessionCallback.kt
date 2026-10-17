package com.webdav.player.data.service

import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.session.MusicPlayerAppSession

class WebDavMediaSessionCallback(
    private val playerEngine: AudioPlayerEngine? = null,
    var appSession: MusicPlayerAppSession? = null,
) : MediaSession.Callback {
    constructor(appSession: MusicPlayerAppSession) : this(playerEngine = null, appSession = appSession)

    fun onSkipToNext() {
        appSession?.skipToNext()
    }

    fun onSkipToPrevious() {
        appSession?.skipToPrevious()
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        val handled = handleMediaButtonIntent(intent)
        return if (handled) true else super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    @Suppress("DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        return when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                onSkipToNext()
                SessionResult.RESULT_SUCCESS
            }
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                onSkipToPrevious()
                SessionResult.RESULT_SUCCESS
            }
            else -> super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    fun handleMediaButtonIntent(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) {
            return false
        }
        val keyEvent = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_KEY_EVENT,
            KeyEvent::class.java
        ) ?: return false
        if (keyEvent.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        return when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                val session = appSession
                if (session != null) {
                    session.play()
                } else {
                    playerEngine?.play()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                val session = appSession
                if (session != null) {
                    session.pause()
                } else {
                    playerEngine?.pause()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                val session = appSession
                if (session != null) {
                    session.togglePlayPause()
                } else {
                    if (playerEngine?.playbackState?.value is PlaybackState.Playing) {
                        playerEngine.pause()
                    } else {
                        playerEngine?.play()
                    }
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                onSkipToNext()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                onSkipToPrevious()
                true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                val session = appSession
                if (session != null) {
                    session.stop()
                } else {
                    playerEngine?.stop()
                }
                true
            }
            else -> false
        }
    }
}
