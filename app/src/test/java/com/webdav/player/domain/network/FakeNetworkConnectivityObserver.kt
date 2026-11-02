package com.webdav.player.domain.network

import com.webdav.player.domain.model.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeNetworkConnectivityObserver(
    initialStatus: NetworkStatus = NetworkStatus(isConnected = true, isWifi = true),
) : NetworkConnectivityObserver {
    private val _networkStatus = MutableStateFlow(initialStatus)
    override val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    fun emitStatus(status: NetworkStatus) {
        _networkStatus.value = status
    }
}
