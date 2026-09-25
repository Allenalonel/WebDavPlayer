package com.webdav.player.data.remote

import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.WebDavServer

sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null
    ) : ConnectionResult
}

interface WebDavClient {
    suspend fun testConnection(server: WebDavServer): ConnectionResult
    suspend fun listDirectory(server: WebDavServer, path: String): ListDirectoryResult
}
