package com.webdav.player.data.remote

sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null
    ) : ConnectionResult
}

interface WebDavClient {
    suspend fun testConnection(server: com.webdav.player.domain.model.WebDavServer): ConnectionResult
}
