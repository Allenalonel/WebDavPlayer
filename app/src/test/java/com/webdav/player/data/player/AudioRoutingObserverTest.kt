package com.webdav.player.data.player

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.AudioDeviceInfoBuilder

@RunWith(RobolectricTestRunner::class)
class AudioRoutingObserverTest {
    private lateinit var context: Context
    private var callbackCount = 0
    private lateinit var observer: AudioRoutingObserver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        callbackCount = 0
        observer =
            AudioRoutingObserver(context) {
                callbackCount++
            }
    }

    private fun createDeviceInfo(
        type: Int,
        isSink: Boolean,
    ): AudioDeviceInfo {
        val device = AudioDeviceInfoBuilder.newBuilder().setType(type).build()
        val port =
            org.robolectric.util.ReflectionHelpers
                .getField<Any>(device, "mPort")
        org.robolectric.util.ReflectionHelpers
            .setField(port, "mRole", if (isSink) 2 else 1)
        return device
    }

    @Test
    fun startAndStop_togglesObservingState() {
        assertFalse(observer.isObserving)
        observer.start()
        assertTrue(observer.isObserving)
        observer.stop()
        assertFalse(observer.isObserving)
    }

    @Test
    fun dispatchRoutingChanged_invokesCallbackDirectly() {
        assertEquals(0, callbackCount)
        observer.dispatchRoutingChanged()
        assertEquals(1, callbackCount)
    }

    @Test
    fun onAudioDevicesAdded_withSinkDevice_dispatchesRoutingChanged() {
        val callback = observer.internalCallback
        assertNotNull(callback)

        val bluetoothSink = createDeviceInfo(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, isSink = true)
        assertTrue(bluetoothSink.isSink)

        callback?.onAudioDevicesAdded(arrayOf(bluetoothSink))
        assertEquals(1, callbackCount)
    }

    @Test
    fun onAudioDevicesAdded_withNonSinkDevice_doesNotDispatchRoutingChanged() {
        val callback = observer.internalCallback
        assertNotNull(callback)

        val micSource = createDeviceInfo(AudioDeviceInfo.TYPE_BUILTIN_MIC, isSink = false)
        assertFalse(micSource.isSink)

        callback?.onAudioDevicesAdded(arrayOf(micSource))
        assertEquals(0, callbackCount)
    }

    @Test
    fun onAudioDevicesRemoved_withSinkDevice_dispatchesRoutingChanged() {
        val callback = observer.internalCallback
        assertNotNull(callback)

        val wiredHeadset = createDeviceInfo(AudioDeviceInfo.TYPE_WIRED_HEADSET, isSink = true)
        assertTrue(wiredHeadset.isSink)

        callback?.onAudioDevicesRemoved(arrayOf(wiredHeadset))
        assertEquals(1, callbackCount)
    }
}
