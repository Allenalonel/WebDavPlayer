package com.webdav.player.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.NetworkStatus
import com.webdav.player.domain.network.FakeNetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NetworkConnectivityObserverTest {
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun createCapabilities(
        hasInternet: Boolean = false,
        isValidated: Boolean = false,
        isWifi: Boolean = false,
        isCellular: Boolean = false,
        isNotMetered: Boolean = true,
    ): NetworkCapabilities {
        val nc = ShadowNetworkCapabilities.newInstance()
        val shadow = shadowOf(nc)
        if (hasInternet) {
            shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        if (isValidated) {
            shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        if (isNotMetered) {
            shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        if (isWifi) {
            shadow.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        }
        if (isCellular) {
            shadow.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        return nc
    }

    @Test
    fun mapCapabilitiesToStatus_nullCapabilities_returnsDisconnected() {
        val status = DefaultNetworkConnectivityObserver.mapCapabilitiesToStatus(null)
        assertFalse(status.isConnected)
        assertFalse(status.isWifi)
        assertFalse(status.isCellular)
        assertFalse(status.isMetered)
    }

    @Test
    fun mapCapabilitiesToStatus_wifiConnected_correctMapping() {
        val capabilities =
            createCapabilities(
                hasInternet = true,
                isValidated = true,
                isWifi = true,
                isNotMetered = true,
            )
        val status = DefaultNetworkConnectivityObserver.mapCapabilitiesToStatus(capabilities)
        assertTrue(status.isConnected)
        assertTrue(status.isWifi)
        assertFalse(status.isCellular)
        assertFalse(status.isMetered)
    }

    @Test
    fun mapCapabilitiesToStatus_cellularConnected_metered() {
        val capabilities =
            createCapabilities(
                hasInternet = true,
                isValidated = true,
                isCellular = true,
                isNotMetered = false,
            )
        val status = DefaultNetworkConnectivityObserver.mapCapabilitiesToStatus(capabilities)
        assertTrue(status.isConnected)
        assertFalse(status.isWifi)
        assertTrue(status.isCellular)
        assertTrue(status.isMetered)
    }

    @Test
    fun mapCapabilitiesToStatus_cellularWithoutValidation_notConnected() {
        val capabilities =
            createCapabilities(
                hasInternet = true,
                isValidated = false,
                isCellular = true,
                isNotMetered = false,
            )
        val status = DefaultNetworkConnectivityObserver.mapCapabilitiesToStatus(capabilities)
        assertFalse(status.isConnected)
        assertTrue(status.isCellular)
    }

    @Test
    fun mapCapabilitiesToStatus_noInternet_notConnected() {
        val capabilities =
            createCapabilities(
                hasInternet = false,
                isValidated = false,
                isWifi = true,
            )
        val status = DefaultNetworkConnectivityObserver.mapCapabilitiesToStatus(capabilities)
        assertFalse(status.isConnected)
        assertTrue(status.isWifi)
    }

    @Test
    fun networkCallback_onCapabilitiesChanged_updatesStateFlow() =
        runTest {
            val observer =
                DefaultNetworkConnectivityObserver(
                    context = context,
                    coroutineScope = backgroundScope,
                    debounceTimeoutMs = 0L,
                    connectivityManager = connectivityManager,
                )

            val wifiCaps = createCapabilities(hasInternet = true, isValidated = true, isWifi = true)
            observer.getNetworkCallback().onCapabilitiesChanged(
                org.robolectric.shadow.api.Shadow
                    .newInstanceOf(Network::class.java),
                wifiCaps,
            )

            val status = observer.networkStatus.value
            assertTrue(status.isConnected)
            assertTrue(status.isWifi)
            assertFalse(status.isCellular)

            observer.close()
        }

    @Test
    fun networkCallback_onLost_updatesStateFlowToDisconnected() =
        runTest {
            val observer =
                DefaultNetworkConnectivityObserver(
                    context = context,
                    coroutineScope = backgroundScope,
                    debounceTimeoutMs = 0L,
                    connectivityManager = connectivityManager,
                )

            val wifiCaps = createCapabilities(hasInternet = true, isValidated = true, isWifi = true)
            val network =
                org.robolectric.shadow.api.Shadow
                    .newInstanceOf(Network::class.java)
            observer.getNetworkCallback().onCapabilitiesChanged(network, wifiCaps)
            assertTrue(observer.networkStatus.value.isConnected)

            observer.getNetworkCallback().onLost(network)
            assertFalse(observer.networkStatus.value.isConnected)

            observer.close()
        }

    @Test
    fun debounce_filtersRapidNetworkFlapping() =
        runTest {
            val observer =
                DefaultNetworkConnectivityObserver(
                    context = context,
                    coroutineScope = backgroundScope,
                    debounceTimeoutMs = 500L,
                    connectivityManager = null,
                )

            val connected = NetworkStatus(isConnected = true, isWifi = true)
            val disconnected = NetworkStatus.DISCONNECTED

            assertEquals(disconnected, observer.networkStatus.value)

            // Rapid flapping: connected at t=0
            observer.updateStatusDirectly(connected)
            advanceTimeBy(200L)
            runCurrent()
            // Still debounced
            assertEquals(disconnected, observer.networkStatus.value)

            // Flap back to disconnected at t=200ms
            observer.updateStatusDirectly(disconnected)
            advanceTimeBy(200L)
            runCurrent()
            assertEquals(disconnected, observer.networkStatus.value)

            // Reconnected at t=400ms and stable
            observer.updateStatusDirectly(connected)
            advanceTimeBy(300L)
            runCurrent()
            // 300ms since last update, total 700ms - not yet 500ms from the t=400 update
            assertEquals(disconnected, observer.networkStatus.value)

            advanceTimeBy(250L) // now 550ms since last update
            runCurrent()
            assertEquals(connected, observer.networkStatus.value)

            observer.close()
        }

    @Test
    fun fakeNetworkConnectivityObserver_emitsExpectedValues() =
        runTest {
            val fake = FakeNetworkConnectivityObserver(NetworkStatus.DISCONNECTED)
            assertFalse(fake.networkStatus.first().isConnected)

            val newStatus = NetworkStatus(isConnected = true, isWifi = false, isCellular = true, isMetered = true)
            fake.emitStatus(newStatus)
            assertEquals(newStatus, fake.networkStatus.first())
        }
}
