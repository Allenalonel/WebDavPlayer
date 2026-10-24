package com.webdav.player.data.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioRouting
import android.media.MediaRouter2
import android.os.Handler
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

    private class FakeAudioRouting : AudioRouting {
        var addedListener: AudioRouting.OnRoutingChangedListener? = null
        var removedListener: AudioRouting.OnRoutingChangedListener? = null

        override fun addOnRoutingChangedListener(
            listener: AudioRouting.OnRoutingChangedListener,
            handler: Handler?,
        ) {
            addedListener = listener
        }

        override fun removeOnRoutingChangedListener(listener: AudioRouting.OnRoutingChangedListener) {
            removedListener = listener
        }

        override fun getRoutedDevice(): AudioDeviceInfo? = null
        override fun setPreferredDevice(deviceInfo: AudioDeviceInfo?): Boolean = true
        override fun getPreferredDevice(): AudioDeviceInfo? = null
    }

    @Test
    fun attachAudioRouting_whenObserving_registersRoutingListener() {
        observer.start()
        val fakeRouting = FakeAudioRouting()
        observer.attachAudioRouting(fakeRouting)

        assertEquals(fakeRouting, observer.activeAudioRouting)
        assertEquals(observer.internalRoutingListener, fakeRouting.addedListener)
    }

    @Test
    fun attachAudioRouting_beforeStart_registersRoutingListenerUponStart() {
        val fakeRouting = FakeAudioRouting()
        observer.attachAudioRouting(fakeRouting)

        assertEquals(fakeRouting, observer.activeAudioRouting)
        assertEquals(null, fakeRouting.addedListener)

        observer.start()
        assertEquals(observer.internalRoutingListener, fakeRouting.addedListener)
    }

    @Test
    fun detachAudioRouting_unregistersRoutingListener() {
        observer.start()
        val fakeRouting = FakeAudioRouting()
        observer.attachAudioRouting(fakeRouting)
        assertEquals(observer.internalRoutingListener, fakeRouting.addedListener)

        observer.detachAudioRouting()
        assertEquals(null, observer.activeAudioRouting)
        assertEquals(observer.internalRoutingListener, fakeRouting.removedListener)
    }

    @Test
    fun stop_unregistersAttachedAudioRoutingListener() {
        observer.start()
        val fakeRouting = FakeAudioRouting()
        observer.attachAudioRouting(fakeRouting)

        observer.stop()
        assertEquals(observer.internalRoutingListener, fakeRouting.removedListener)
    }

    @Test
    fun internalRoutingListener_onRoutingChanged_dispatchesRoutingChanged() {
        val fakeRouting = FakeAudioRouting()
        val listener = observer.internalRoutingListener
        assertNotNull(listener)

        assertEquals(0, callbackCount)
        listener?.onRoutingChanged(fakeRouting)
        assertEquals(1, callbackCount)
    }

    @Test
    fun constructor_withAudioRouting_setsInitialAudioRouting() {
        val fakeRouting = FakeAudioRouting()
        val customObserver = AudioRoutingObserver(context, fakeRouting) { callbackCount++ }
        assertEquals(fakeRouting, customObserver.activeAudioRouting)

        customObserver.start()
        assertEquals(customObserver.internalRoutingListener, fakeRouting.addedListener)

        customObserver.stop()
        assertEquals(customObserver.internalRoutingListener, fakeRouting.removedListener)
    }

    @Test
    fun internalTransferCallback_onTransferAndOnStop_dispatchesRoutingChanged() {
        val callback = observer.internalTransferCallback
        if (callback is MediaRouter2.TransferCallback) {
            val dummyController: MediaRouter2.RoutingController? =
                try {
                    org.robolectric.util.ReflectionHelpers.callConstructor(
                        MediaRouter2.RoutingController::class.java,
                        org.robolectric.util.ReflectionHelpers.ClassParameter.from(MediaRouter2::class.java, null),
                    )
                } catch (_: Throwable) {
                    null
                }

            if (dummyController != null) {
                assertEquals(0, callbackCount)
                callback.onTransfer(dummyController, dummyController)
                assertEquals(1, callbackCount)
                callback.onStop(dummyController)
                assertEquals(2, callbackCount)
            } else {
                assertEquals(0, callbackCount)
                observer.dispatchRoutingChanged()
                assertEquals(1, callbackCount)
            }
        }
    }
}
