package com.webdav.player.data.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.session.MusicPlayerAppSession
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import android.media.AudioAttributes as AndroidAudioAttributes

class AudioFocusHandler(
    private val context: Context,
    appSession: MusicPlayerAppSession? = null,
    val duckVolume: Float = 0.2f,
    val normalVolume: Float = 1.0f,
    private val onVolumeChanged: ((Float) -> Unit)? = null,
) : AudioManager.OnAudioFocusChangeListener {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val isDuckedRef = AtomicBoolean(false)
    val isDucked: Boolean
        get() = isDuckedRef.get()

    private val internalResumeOnFocusGain = AtomicBoolean(false)

    private val appSessionRef = AtomicReference<MusicPlayerAppSession?>(appSession)
    var appSession: MusicPlayerAppSession?
        get() = appSessionRef.get()
        set(value) {
            appSessionRef.set(value)
        }

    val resumeOnFocusGain: Boolean
        get() = appSessionRef.get()?.pausedByTransientAudioFocus ?: internalResumeOnFocusGain.get()

    private val audioFocusRequestRef = AtomicReference<AudioFocusRequest?>(null)
    private val legacyPlayerEngineRef = AtomicReference<AudioPlayerEngine?>(null)

    fun bindAppSession(session: MusicPlayerAppSession) {
        appSessionRef.set(session)
    }

    @Deprecated("Use appSession instead of playerEngine")
    constructor(
        context: Context,
        playerEngine: AudioPlayerEngine,
        duckVolume: Float = 0.2f,
        normalVolume: Float = 1.0f,
        onVolumeChanged: ((Float) -> Unit)? = null,
    ) : this(
        context = context,
        appSession = null,
        duckVolume = duckVolume,
        normalVolume = normalVolume,
        onVolumeChanged = onVolumeChanged,
    ) {
        legacyPlayerEngineRef.set(playerEngine)
    }

    fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true

        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequestRef.getAndSet(null)?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(this)
        }
        internalResumeOnFocusGain.set(false)
        isDuckedRef.set(false)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        handleFocusChange(focusChange)
    }

    @VisibleForTesting
    fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val session = appSessionRef.get()
                if (session != null) {
                    session.onAudioFocusTransientLoss()
                } else {
                    val wasPlaying = legacyPlayerEngineRef.get()?.playbackState?.value is PlaybackState.Playing
                    if (wasPlaying) {
                        internalResumeOnFocusGain.set(true)
                        legacyPlayerEngineRef.get()?.pause()
                    }
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDuckedRef.set(true)
                onVolumeChanged?.invoke(duckVolume)
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDuckedRef.compareAndSet(true, false)) {
                    onVolumeChanged?.invoke(normalVolume)
                }
                val session = appSessionRef.get()
                if (session != null) {
                    session.onAudioFocusGain()
                } else if (internalResumeOnFocusGain.compareAndSet(true, false)) {
                    legacyPlayerEngineRef.get()?.play()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                if (isDuckedRef.compareAndSet(true, false)) {
                    onVolumeChanged?.invoke(normalVolume)
                }
                internalResumeOnFocusGain.set(false)
                val session = appSessionRef.get()
                if (session != null) {
                    session.onAudioFocusLoss()
                } else {
                    legacyPlayerEngineRef.get()?.pause()
                }
                abandonAudioFocus()
            }
        }
    }
}
