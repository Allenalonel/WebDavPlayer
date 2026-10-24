package com.webdav.player.data.service

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.annotation.VisibleForTesting
import com.webdav.player.domain.session.MusicPlayerAppSession
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import android.media.AudioAttributes as AndroidAudioAttributes

/**
 * AudioFocusHandler:
 * Harmonizes Android system audio focus arbitration with unidirectional
 * application session intents (MusicPlayerAppSession).
 *
 * Adheres to ADR-0018 and GLOSSARY.md domain term "Audio Focus":
 * - Translates AUDIOFOCUS_LOSS_TRANSIENT to appSession.onAudioFocusTransientLoss()
 * - Translates AUDIOFOCUS_GAIN to appSession.onAudioFocusGain() (blocked if user paused during call)
 * - Translates AUDIOFOCUS_LOSS to appSession.onAudioFocusLoss() and abandons focus
 * - Leaves ducking attenuation to Android 8.0+ system mixer (setWillPauseWhenDucked = false)
 *   without applying secondary application-level volume degradation.
 */
class AudioFocusHandler(
    private val context: Context,
    appSession: MusicPlayerAppSession? = null,
) : AudioManager.OnAudioFocusChangeListener {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val isDuckedRef = AtomicBoolean(false)
    val isDucked: Boolean
        get() = isDuckedRef.get()

    private val appSessionRef = AtomicReference<MusicPlayerAppSession?>(appSession)
    var appSession: MusicPlayerAppSession?
        get() = appSessionRef.get()
        set(value) {
            appSessionRef.set(value)
        }

    val resumeOnFocusGain: Boolean
        get() = appSessionRef.get()?.pausedByTransientAudioFocus ?: false

    private val audioFocusRequestRef = AtomicReference<AudioFocusRequest?>(null)

    fun bindAppSession(session: MusicPlayerAppSession) {
        appSessionRef.set(session)
    }

    fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true

        val playbackAttributes =
            AndroidAudioAttributes
                .Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this)
                .build()
        audioFocusRequestRef.set(request)
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonAudioFocus() {
        val manager = audioManager ?: return
        audioFocusRequestRef.getAndSet(null)?.let { manager.abandonAudioFocusRequest(it) }
        isDuckedRef.set(false)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        handleFocusChange(focusChange)
    }

    @VisibleForTesting
    fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                appSessionRef.get()?.onAudioFocusTransientLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // System mixer automatically ducks audio stream (setWillPauseWhenDucked = false).
                // Application avoids double-ducking volume reduction.
                isDuckedRef.set(true)
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                isDuckedRef.set(false)
                appSessionRef.get()?.onAudioFocusGain()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                isDuckedRef.set(false)
                appSessionRef.get()?.onAudioFocusLoss()
                abandonAudioFocus()
            }
        }
    }
}
