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
    suspend fun fetchRange(
        server: WebDavServer,
        remotePath: String,
        startByte: Long = 0L,
        endByte: Long = 131071L
    ): ByteArray?
}
