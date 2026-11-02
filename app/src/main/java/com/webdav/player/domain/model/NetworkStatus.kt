package com.webdav.player.domain.model

data class NetworkStatus(
    val isConnected: Boolean,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val isMetered: Boolean = false,
) {
    companion object {
        val DISCONNECTED = NetworkStatus(isConnected = false)
    }
}
