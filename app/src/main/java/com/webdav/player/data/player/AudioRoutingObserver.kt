package com.webdav.player.data.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Audio Routing Observer:
 * Monitors changes in audio output routing devices (via AudioDeviceCallback)
 * and dispatches notifications to synchronize and flush AudioSink buffers.
 *
 * Adheres to GLOSSARY.md domain term "Audio Routing Observer".
 */
class AudioRoutingObserver(
    private val context: Context,
    private val onRoutingChanged: () -> Unit,
) {
    private val isStarted = AtomicBoolean(false)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val deviceCallback: AudioDeviceCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    if (addedDevices?.any { it.isSink } == true) {
                        dispatchRoutingChanged()
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    if (removedDevices?.any { it.isSink } == true) {
                        dispatchRoutingChanged()
                    }
                }
            }
        } else {
            null
        }

    @get:VisibleForTesting
    val internalCallback: AudioDeviceCallback?
        get() = deviceCallback

    val isObserving: Boolean
        get() = isStarted.get()

    fun start() {
        if (isStarted.compareAndSet(false, true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
                try {
                    val handler = Handler(Looper.getMainLooper())
                    audioManager?.registerAudioDeviceCallback(deviceCallback, handler)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stop() {
        if (isStarted.compareAndSet(true, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
                try {
                    audioManager?.unregisterAudioDeviceCallback(deviceCallback)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun dispatchRoutingChanged() {
        onRoutingChanged()
    }
}
