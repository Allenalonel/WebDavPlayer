package com.webdav.player.data.player

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.annotation.VisibleForTesting

interface StreamingHardwareLockManager {
    val isWakeLockHeld: Boolean
    val isWifiLockHeld: Boolean

    fun acquireLocks()

    fun releaseLocks()

    fun release()
}

class DefaultStreamingHardwareLockManager(
    private val context: Context,
    private val powerManager: PowerManager? = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager,
    private val wifiManager: WifiManager? = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager,
) : StreamingHardwareLockManager {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override val isWakeLockHeld: Boolean
        get() = wakeLock?.isHeld == true

    override val isWifiLockHeld: Boolean
        get() = wifiLock?.isHeld == true

    @Synchronized
    override fun acquireLocks() {
        acquireWakeLock()
        acquireWifiLock()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                wakeLock =
                    powerManager
                        ?.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            WAKE_LOCK_TAG,
                        )?.apply {
                            setReferenceCounted(false)
                        }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
        } catch (_: Throwable) {
            // Gracefully ignore test/runtime permission limitations
        }
    }

    private fun acquireWifiLock() {
        try {
            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock =
                    wifiManager
                        ?.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            WIFI_LOCK_TAG,
                        )?.apply {
                            setReferenceCounted(false)
                        }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        } catch (_: Throwable) {
            // Gracefully ignore test/runtime permission limitations
        }
    }

    @Synchronized
    override fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        }
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    override fun release() {
        releaseLocks()
        wakeLock = null
        wifiLock = null
    }

    companion object {
        const val WAKE_LOCK_TAG = "WebDavPlayer:StreamingWakeLock"
        const val WIFI_LOCK_TAG = "WebDavPlayer:StreamingWifiLock"
    }
}
