package com.webdav.player.domain.repository

import com.webdav.player.domain.model.DirectoryContent
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
     * Observes enriched directory content (files, parsed CUE albums, track metadata tags, and lifecycle state).
     *
     * SWR Lifecycle:
     * 1. Cache hit (0ms): Immediately emits cached directory content if available.
     * 2. Remote update: Emits newly fetched remote file list with [DirectoryContent.isEnriching] = true.
     * 3. Background enrichment: Resolves CUE virtual tracks and metadata tags in the background; emits
     *    final enriched snapshot with [DirectoryContent.isEnriching] = false upon completion.
     * 4. Error tolerance: Transient network errors during background revalidation do not suppress previously
     *    rendered cached content.
     */
    fun observeDirectoryContent(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean = false,
    ): Flow<DirectoryContent> = emptyFlow()

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
