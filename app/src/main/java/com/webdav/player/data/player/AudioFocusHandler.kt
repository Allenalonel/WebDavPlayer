package com.webdav.player.data.player

import android.content.Context
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.player.AudioPlayerEngine

class AudioFocusHandler(
    private val context: Context,
    private val playerEngine: AudioPlayerEngine,
    val duckVolume: Float = 0.2f,
    val normalVolume: Float = 1.0f,
    private val onVolumeChanged: ((Float) -> Unit)? = null
) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    var isDucked: Boolean = false
        private set

    var resumeOnFocusGain: Boolean = false
        private set

    private var audioFocusRequest: AudioFocusRequest? = null

    fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(this)
        }
        resumeOnFocusGain = false
        isDucked = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        handleFocusChange(focusChange)
    }

    @VisibleForTesting
    fun handleFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val wasPlaying = playerEngine.playbackState.value is PlaybackState.Playing
                if (wasPlaying) {
                    resumeOnFocusGain = true
                    playerEngine.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDucked = true
                applyVolume(duckVolume)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDucked) {
                    isDucked = false
                    applyVolume(normalVolume)
                }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    playerEngine.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (isDucked) {
                    isDucked = false
                    applyVolume(normalVolume)
                }
                playerEngine.pause()
            }
        }
    }

    private fun applyVolume(volume: Float) {
        onVolumeChanged?.invoke(volume)
        playerEngine.setVolume(volume)
    }
}
