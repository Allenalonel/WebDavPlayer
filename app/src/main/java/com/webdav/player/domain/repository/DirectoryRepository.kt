package com.webdav.player.domain.repository

import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.WebDavServer

interface DirectoryRepository {
    /**
     * Lists the contents of a remote directory on the given server.
     * If [forceRefresh] is true, bypasses and invalidates the in-memory cache for this path.
     */
    suspend fun listDirectory(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean = false
    ): ListDirectoryResult

    /**
     * Clears all in-memory directory cache.
     */
    fun clearCache()
}
