package com.webdav.player.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.VisibleForTesting
import com.webdav.player.domain.model.NetworkStatus
import com.webdav.player.domain.network.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class DefaultNetworkConnectivityObserver(
    context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val debounceTimeoutMs: Long = DEFAULT_DEBOUNCE_MS,
    connectivityManager: ConnectivityManager? = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager,
) : NetworkConnectivityObserver,
    Closeable {
    private val cm = connectivityManager
    private val isRegistered = AtomicBoolean(false)

    private val _rawStatus =
        MutableStateFlow(
            mapCapabilitiesToStatus(cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }),
        )

    @OptIn(FlowPreview::class)
    override val networkStatus: StateFlow<NetworkStatus> =
        if (debounceTimeoutMs > 0L) {
            _rawStatus
                .debounce(debounceTimeoutMs)
                .distinctUntilChanged()
                .stateIn(
                    scope = coroutineScope,
                    started = SharingStarted.Eagerly,
                    initialValue = _rawStatus.value,
                )
        } else {
            _rawStatus.asStateFlow()
        }

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = cm?.getNetworkCapabilities(network)
                _rawStatus.value = mapCapabilitiesToStatus(capabilities)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                _rawStatus.value = mapCapabilitiesToStatus(networkCapabilities)
            }

            override fun onLost(network: Network) {
                val activeNetwork = cm?.activeNetwork
                val capabilities = activeNetwork?.let { cm?.getNetworkCapabilities(it) }
                _rawStatus.value = mapCapabilitiesToStatus(capabilities)
            }

            override fun onUnavailable() {
                _rawStatus.value = NetworkStatus.DISCONNECTED
            }
        }

    init {
        registerCallbackSafely()
    }

    private fun registerCallbackSafely() {
        if (cm != null && isRegistered.compareAndSet(false, true)) {
            try {
                cm.registerDefaultNetworkCallback(callback)
            } catch (_: Throwable) {
                isRegistered.set(false)
            }
        }
    }

    override fun close() {
        if (cm != null && isRegistered.compareAndSet(true, false)) {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Throwable) {
                // Ignore if already unregistered or system service died
            }
        }
        coroutineScope.coroutineContext.cancelChildren()
    }

    @VisibleForTesting
    fun updateStatusDirectly(status: NetworkStatus) {
        _rawStatus.value = status
    }

    @VisibleForTesting
    fun getNetworkCallback(): ConnectivityManager.NetworkCallback = callback

    companion object {
        const val DEFAULT_DEBOUNCE_MS: Long = 500L

        fun mapCapabilitiesToStatus(capabilities: NetworkCapabilities?): NetworkStatus {
            if (capabilities == null) return NetworkStatus.DISCONNECTED
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

            val isConnected = (hasInternet && isValidated) || (hasInternet && isWifi)
            return NetworkStatus(
                isConnected = isConnected,
                isWifi = isWifi,
                isCellular = isCellular,
                isMetered = isMetered,
            )
        }
    }
}
