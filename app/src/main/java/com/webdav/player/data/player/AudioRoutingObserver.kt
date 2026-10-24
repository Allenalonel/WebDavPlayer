package com.webdav.player.data.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Audio Routing Observer:
 * Monitors changes in audio output routing devices across dual tracks:
 * 1. Physical hardware plug/unplug events (via AudioDeviceCallback)
 * 2. Active audio output routing / Media Output Switcher changes (via AudioRouting.OnRoutingChangedListener and MediaRouter2.TransferCallback)
 *
 * When audio output shifts or device changes occur, dispatches notifications
 * to synchronize and flush AudioSink buffers.
 *
 * Adheres to GLOSSARY.md domain term "Audio Routing Observer".
 */
class AudioRoutingObserver(
    private val context: Context,
    audioRouting: AudioRouting? = null,
    private val onRoutingChanged: () -> Unit,
) {
    constructor(
        context: Context,
        onRoutingChanged: () -> Unit,
    ) : this(context, null, onRoutingChanged)

    private val isStarted = AtomicBoolean(false)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val attachedAudioRoutingRef = AtomicReference<AudioRouting?>(audioRouting)

    val activeAudioRouting: AudioRouting?
        get() = attachedAudioRoutingRef.get()

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

    private val routingListener: AudioRouting.OnRoutingChangedListener? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AudioRouting.OnRoutingChangedListener {
                dispatchRoutingChanged()
            }
        } else {
            null
        }

    private val transferCallback: MediaRouter2.TransferCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            object : MediaRouter2.TransferCallback() {
                override fun onTransfer(
                    oldController: MediaRouter2.RoutingController,
                    newController: MediaRouter2.RoutingController,
                ) {
                    dispatchRoutingChanged()
                }

                override fun onStop(controller: MediaRouter2.RoutingController) {
                    dispatchRoutingChanged()
                }
            }
        } else {
            null
        }

    @get:VisibleForTesting
    val internalCallback: AudioDeviceCallback?
        get() = deviceCallback

    @get:VisibleForTesting
    val internalRoutingListener: AudioRouting.OnRoutingChangedListener?
        get() = routingListener

    @get:VisibleForTesting
    val internalTransferCallback: Any?
        get() = transferCallback

    val isObserving: Boolean
        get() = isStarted.get()

    fun attachAudioRouting(audioRouting: AudioRouting?) {
        val old = attachedAudioRoutingRef.getAndSet(audioRouting)
        if (old === audioRouting) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && routingListener != null) {
            if (isStarted.get() && old != null) {
                try {
                    old.removeOnRoutingChangedListener(routingListener)
                } catch (_: Throwable) {
                }
            }
            if (isStarted.get() && audioRouting != null) {
                try {
                    val handler = Handler(Looper.getMainLooper())
                    audioRouting.addOnRoutingChangedListener(routingListener, handler)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun detachAudioRouting() {
        attachAudioRouting(null)
    }

    fun start() {
        if (isStarted.compareAndSet(false, true)) {
            // Track 1: Physical hardware device callbacks
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
                try {
                    val handler = Handler(Looper.getMainLooper())
                    audioManager?.registerAudioDeviceCallback(deviceCallback, handler)
                } catch (_: Throwable) {
                }
            }

            // Track 2a: Active AudioRouting dynamic routing listener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && routingListener != null) {
                attachedAudioRoutingRef.get()?.let { routing ->
                    try {
                        val handler = Handler(Looper.getMainLooper())
                        routing.addOnRoutingChangedListener(routingListener, handler)
                    } catch (_: Throwable) {
                    }
                }
            }

            // Track 2b: MediaRouter2 system Media Output Switcher transfer callback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && transferCallback != null) {
                try {
                    val mediaRouter2 = MediaRouter2.getInstance(context)
                    mediaRouter2.registerTransferCallback(
                        ContextCompat.getMainExecutor(context),
                        transferCallback,
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stop() {
        if (isStarted.compareAndSet(true, false)) {
            // Track 1: Physical hardware device callbacks
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
                try {
                    audioManager?.unregisterAudioDeviceCallback(deviceCallback)
                } catch (_: Throwable) {
                }
            }

            // Track 2a: Active AudioRouting listener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && routingListener != null) {
                attachedAudioRoutingRef.get()?.let { routing ->
                    try {
                        routing.removeOnRoutingChangedListener(routingListener)
                    } catch (_: Throwable) {
                    }
                }
            }

            // Track 2b: MediaRouter2 transfer callback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && transferCallback != null) {
                try {
                    val mediaRouter2 = MediaRouter2.getInstance(context)
                    mediaRouter2.unregisterTransferCallback(transferCallback)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun dispatchRoutingChanged() {
        onRoutingChanged()
    }
}
