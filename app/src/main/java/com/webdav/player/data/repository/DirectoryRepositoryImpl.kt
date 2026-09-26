package com.webdav.player.data.repository

import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import java.util.concurrent.ConcurrentHashMap

class DirectoryRepositoryImpl(
    private val webDavClient: WebDavClient,
    private val directoryCacheDao: DirectoryCacheDao? = null
) : DirectoryRepository {

    private val memoryCache = ConcurrentHashMap<String, RemoteDirectory>()

    override suspend fun getCachedDirectory(
        server: WebDavServer,
        path: String
    ): RemoteDirectory? {
        val normalizedPath = normalizePath(path)
        val cacheKey = "${server.id}:$normalizedPath"

        // 1. Check L1 memory cache
        val inMemory = memoryCache[cacheKey]
        if (inMemory != null) {
            return inMemory
        }

        // 2. Check L2 Room persistent cache
        val cachedEntity = directoryCacheDao?.getCache(server.id, normalizedPath)
        if (cachedEntity != null) {
            val deserialized = RemoteDirectoryJsonSerializer.deserialize(cachedEntity.dataJson)
            if (deserialized != null) {
                memoryCache[cacheKey] = deserialized
                return deserialized
            }
        }

        return null
    }

    override suspend fun listDirectory(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean
    ): ListDirectoryResult {
        val normalizedPath = normalizePath(path)
        val cacheKey = "${server.id}:$normalizedPath"

        if (!forceRefresh) {
            val cached = getCachedDirectory(server, normalizedPath)
            if (cached != null) {
                return ListDirectoryResult.Success(cached)
            }
        }

        val result = webDavClient.listDirectory(server, normalizedPath)
        if (result is ListDirectoryResult.Success) {
            memoryCache[cacheKey] = result.directory
            val json = RemoteDirectoryJsonSerializer.serialize(result.directory)
            directoryCacheDao?.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = server.id,
                    path = normalizedPath,
                    dataJson = json,
                    lastUpdatedMs = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    override suspend fun clearCache() {
        memoryCache.clear()
        directoryCacheDao?.clearAll()
    }

    override fun clearMemoryCache() {
        memoryCache.clear()
    }

    override suspend fun clearCacheForServer(serverId: Long) {
        val prefix = "$serverId:"
        val keysToRemove = memoryCache.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { memoryCache.remove(it) }
        directoryCacheDao?.deleteCacheByServerId(serverId)
    }

    private fun normalizePath(path: String): String {
        var p = path.replace('\\', '/')
        if (!p.startsWith("/")) p = "/$p"
        if (!p.endsWith("/")) p = "$p/"
        return p
    }
}
