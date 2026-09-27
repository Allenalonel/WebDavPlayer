package com.webdav.player.domain.repository

import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.Flow

interface DirectoryRepository {
    /**
     * Retrieves the cached directory from L1 (memory) or L2 (database) cache if available.
     * Returns null if not cached.
     */
    suspend fun getCachedDirectory(
        server: WebDavServer,
        path: String
    ): RemoteDirectory? = null

    /**
     * Observes directory contents as a reactive SWR stream.
     * When [forceRefresh] is false and cache exists, immediately emits cached snapshot (0ms),
     * then revalidates in the background against remote WebDAV server and emits update if remote differs.
     * Network errors during background revalidation do not suppress already emitted cached data.
     * When [forceRefresh] is true, cache is bypassed and remote query result is emitted directly.
     */
    fun observeDirectory(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean = false
    ): Flow<ListDirectoryResult>

    /**
     * Lists the contents of a remote directory on the given server.
     * If [forceRefresh] is true, bypasses and invalidates the cache for this path.
     */
    suspend fun listDirectory(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean = false
    ): ListDirectoryResult

    /**
     * Clears all in-memory and persistent directory cache.
     */
    suspend fun clearCache()

    /**
     * Clears in-memory cache only.
     */
    fun clearMemoryCache() {}

    /**
     * Clears cache for a specific server (both memory and persistent).
     */
    suspend fun clearCacheForServer(serverId: Long) {}
}
