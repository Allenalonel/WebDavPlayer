package com.webdav.player.data.repository

import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import java.util.concurrent.ConcurrentHashMap

class DirectoryRepositoryImpl(
    private val webDavClient: WebDavClient
) : DirectoryRepository {

    private val memoryCache = ConcurrentHashMap<String, RemoteDirectory>()

    override suspend fun listDirectory(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean
    ): ListDirectoryResult {
        val normalizedPath = normalizePath(path)
        val cacheKey = "${server.id}:$normalizedPath"

        if (!forceRefresh) {
            val cached = memoryCache[cacheKey]
            if (cached != null) {
                return ListDirectoryResult.Success(cached)
            }
        }

        val result = webDavClient.listDirectory(server, normalizedPath)
        if (result is ListDirectoryResult.Success) {
            memoryCache[cacheKey] = result.directory
        }
        return result
    }

    override fun clearCache() {
        memoryCache.clear()
    }

    private fun normalizePath(path: String): String {
        var p = path.replace('\\', '/')
        if (!p.startsWith("/")) p = "/$p"
        if (!p.endsWith("/")) p = "$p/"
        return p
    }
}
