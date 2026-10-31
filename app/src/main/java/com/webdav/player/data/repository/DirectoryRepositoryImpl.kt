package com.webdav.player.data.repository

import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.DirectoryContent
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DirectoryRepositoryImpl(
    private val webDavClient: WebDavClient,
    private val directoryCacheDao: DirectoryCacheDao? = null,
    private val cueSheetResolver: CueSheetResolver? = null,
    private val trackMetadataRepository: TrackMetadataRepository? = null,
) : DirectoryRepository {
    companion object {
        private const val MAX_DIRECTORY_CACHE_SIZE = 50
    }

    private val cacheLock = Any()
    private val memoryCache =
        object : LinkedHashMap<String, RemoteDirectory>(MAX_DIRECTORY_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RemoteDirectory>?): Boolean =
                size > MAX_DIRECTORY_CACHE_SIZE
        }

    override suspend fun getCachedDirectory(
        server: WebDavServer,
        path: String,
    ): RemoteDirectory? {
        val normalizedPath = RemoteDirectory.normalizePath(path)
        val cacheKey = "${server.id}:$normalizedPath"

        // 1. Check L1 memory cache
        val inMemory =
            synchronized(cacheLock) {
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

    override fun observeDirectoryContent(
        server: WebDavServer,
        path: String,
        forceRefresh: Boolean,
    ): Flow<DirectoryContent> =
        flow {
            val normalizedPath = RemoteDirectory.normalizePath(path)
            val cachedDir = if (!forceRefresh) getCachedDirectory(server, normalizedPath) else null

            // 1. Cache hit (0ms)
            if (cachedDir != null) {
                val initialMeta = getCachedMetadataMap(server.id, cachedDir)
                val initialCue = getInitialCueAlbums(cachedDir)
                emit(
                    DirectoryContent(
                        directory = cachedDir,
                        cueAlbums = initialCue,
                        metadataMap = initialMeta,
                        isEnriching = true,
                    ),
                )
            }

            // 2. Network query
            val remoteResult =
                try {
                    webDavClient.listDirectory(server, normalizedPath)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    ListDirectoryResult.Failure(e.message ?: "Unknown error")
                }

            when (remoteResult) {
                is ListDirectoryResult.Success -> {
                    val remoteDir = remoteResult.directory
                    val remoteDiffersFromCache = (cachedDir == null || remoteDir != cachedDir)

                    if (remoteDiffersFromCache) {
                        persistCache(server.id, normalizedPath, remoteDir)
                        val currentMeta = getCachedMetadataMap(server.id, remoteDir)
                        val currentCue = getInitialCueAlbums(remoteDir)
                        emit(
                            DirectoryContent(
                                directory = remoteDir,
                                cueAlbums = currentCue,
                                metadataMap = currentMeta,
                                isEnriching = true,
                            ),
                        )
                    }

                    // 3. Background enrichment
                    val enriched = enrichDirectoryContent(server, remoteDir, forceRefresh)
                    emit(enriched)
                }

                is ListDirectoryResult.Failure -> {
                    if (cachedDir != null && !forceRefresh) {
                        // Suppress transient network failure and finish enrichment lifecycle cleanly
                        val finalMeta = getCachedMetadataMap(server.id, cachedDir)
                        val finalCue = getInitialCueAlbums(cachedDir)
                        emit(
                            DirectoryContent(
                                directory = cachedDir,
                                cueAlbums = finalCue,
                                metadataMap = finalMeta,
                                isEnriching = false,
                            ),
                        )
                    } else {
                        throw IllegalStateException(remoteResult.message)
                    }
                }
            }
        }

    private suspend fun getCachedMetadataMap(
        serverId: Long,
        directory: RemoteDirectory,
    ): Map<String, TrackMetadata> {
        val audioFiles = directory.files.filter { it.isAudio }
        if (audioFiles.isEmpty() || trackMetadataRepository == null) return emptyMap()
        val metaMap = mutableMapOf<String, TrackMetadata>()
        for (file in audioFiles) {
            val meta = trackMetadataRepository.getCachedMetadata(serverId, file.path)
            if (meta != null) {
                metaMap[file.path] = meta
            }
        }
        return metaMap
    }

    private fun getInitialCueAlbums(
        directory: RemoteDirectory,
    ): List<CueAlbumItem> = cueSheetResolver?.resolveInitialCueAlbums(directory) ?: emptyList()

    private suspend fun enrichDirectoryContent(
        server: WebDavServer,
        directory: RemoteDirectory,
        forceRefresh: Boolean,
    ): DirectoryContent {
        val audioFiles = directory.files.filter { it.isAudio }
        val cueFiles = directory.files.filter { it.isCue }

        if (audioFiles.isNotEmpty() && trackMetadataRepository != null) {
            trackMetadataRepository.resolveMetadata(
                server = server,
                files = audioFiles,
                forceRefresh = forceRefresh,
            )
        }

        val metadataMap = getCachedMetadataMap(server.id, directory)

        val cueAlbums =
            if (cueFiles.isNotEmpty() && cueSheetResolver != null) {
                val parentDurations = metadataMap.mapValues { it.value.durationMs }
                cueSheetResolver.resolveDirectoryCueAlbums(
                    server = server,
                    directory = directory,
                    parentDurationsByPath = parentDurations,
                )
            } else {
                getInitialCueAlbums(directory)
            }

        return DirectoryContent(
            directory = directory,
            cueAlbums = cueAlbums,
            metadataMap = metadataMap,
            isEnriching = false,
        )
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
        directory: RemoteDirectory,
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
                lastUpdatedMs = System.currentTimeMillis(),
            ),
        )
    }
}
