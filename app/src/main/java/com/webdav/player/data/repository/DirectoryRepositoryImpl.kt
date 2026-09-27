package com.webdav.player.data.repository

import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DirectoryRepositoryImpl(
    private val webDavClient: WebDavClient,
    private val directoryCacheDao: DirectoryCacheDao? = null
) : DirectoryRepository {

    companion object {
        private const val MAX_DIRECTORY_CACHE_SIZE = 50
    }

    private val cacheLock = Any()
    private val memoryCache = object : LinkedHashMap<String, RemoteDirectory>(MAX_DIRECTORY_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RemoteDirectory>?): Boolean {
            return size > MAX_DIRECTORY_CACHE_SIZE
        }
    }

    override suspend fun getCachedDirectory(
        server: WebDavServer,
        path: String
    ): RemoteDirectory? {
        val normalizedPath = normalizePath(path)
        val cacheKey = "${server.id}:$normalizedPath"

        // 1. Check L1 memory cache
        val inMemory = synchronized(cacheLock) {
            memoryCache[cacheKey]
        }
        if (inMemory != null) {
            return inMemory
        }

        // 2. Check L2 Room persistent cache
        val cachedEntity = directoryCacheDao?.getCache(server.id, normalizedPath)
        if (cachedEntity != null) {
            val deserialized = RemoteDirectoryJsonSerializer.deserialize(cachedEntity.dataJson)
            if (deserialized != null) {
                synchronized(cacheLock) {
                    memoryCache[cacheKey] = deserialized
                }
                return deserialized
            }
        }

        return null
    }

    override fun observeDirectory(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean
    ): Flow<ListDirectoryResult> = flow {
        val normalizedPath = normalizePath(path)
        var cachedDir: RemoteDirectory? = null

        if (!forceRefresh) {
            cachedDir = getCachedDirectory(server, normalizedPath)
            if (cachedDir != null) {
                emit(ListDirectoryResult.Success(cachedDir))
            }
        }

        val remoteResult = try {
            webDavClient.listDirectory(server, normalizedPath)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            ListDirectoryResult.Failure(e.message ?: "Unknown error")
        }

        when (remoteResult) {
            is ListDirectoryResult.Success -> {
                if (cachedDir == null || remoteResult.directory != cachedDir) {
                    persistCache(server.id, normalizedPath, remoteResult.directory)
                    emit(remoteResult)
                }
            }
            is ListDirectoryResult.Failure -> {
                // Transient network errors during background revalidation do not suppress or crash already-rendered cached data.
                if (cachedDir == null || forceRefresh) {
                    emit(remoteResult)
                }
            }
        }
    }

    override suspend fun listDirectory(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean
    ): ListDirectoryResult {
        val normalizedPath = normalizePath(path)

        if (!forceRefresh) {
            val cached = getCachedDirectory(server, normalizedPath)
            if (cached != null) {
                return ListDirectoryResult.Success(cached)
            }
        }

        val result = webDavClient.listDirectory(server, normalizedPath)
        if (result is ListDirectoryResult.Success) {
            persistCache(server.id, normalizedPath, result.directory)
        }
        return result
    }

    override suspend fun clearCache() {
        synchronized(cacheLock) {
            memoryCache.clear()
        }
        directoryCacheDao?.clearAll()
    }

    override fun clearMemoryCache() {
        synchronized(cacheLock) {
            memoryCache.clear()
        }
    }

    override suspend fun clearCacheForServer(serverId: Long) {
        val prefix = "$serverId:"
        synchronized(cacheLock) {
            val keysToRemove = memoryCache.keys.filter { it.startsWith(prefix) }
            keysToRemove.forEach { memoryCache.remove(it) }
        }
        directoryCacheDao?.deleteCacheByServerId(serverId)
    }

    private suspend fun persistCache(
        serverId: Long,
        normalizedPath: String,
        directory: RemoteDirectory
    ) {
        val cacheKey = "$serverId:$normalizedPath"
        synchronized(cacheLock) {
            memoryCache[cacheKey] = directory
        }
        val json = RemoteDirectoryJsonSerializer.serialize(directory)
        directoryCacheDao?.insertOrUpdate(
            DirectoryCacheEntity(
                serverId = serverId,
                path = normalizedPath,
                dataJson = json,
                lastUpdatedMs = System.currentTimeMillis()
            )
        )
    }

    private fun normalizePath(path: String): String {
        var p = path.replace('\\', '/')
        if (!p.startsWith("/")) p = "/$p"
        if (!p.endsWith("/")) p = "$p/"
        return p
    }
}
