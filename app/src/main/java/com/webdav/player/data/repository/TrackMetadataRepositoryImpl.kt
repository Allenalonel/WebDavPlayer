package com.webdav.player.data.repository

import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.data.local.WebDavServerDao
import com.webdav.player.data.metadata.DefaultTrackMetadataResolver
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class TrackMetadataRepositoryImpl internal constructor(
    private val trackMetadataDao: TrackMetadataDao,
    private val trackMetadataResolver: DefaultTrackMetadataResolver,
    private val coverArtStorage: CoverArtStorage,
    private val webDavServerDao: WebDavServerDao? = null,
    private val maxConcurrency: Int = 3,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
    private val serverLookup: (suspend (Long) -> WebDavServer?)? = null,
) : TrackMetadataRepository {
    constructor(
        trackMetadataDao: TrackMetadataDao,
        webDavClient: WebDavClient,
        coverArtStorage: CoverArtStorage,
        webDavServerDao: WebDavServerDao? = null,
        maxConcurrency: Int = 3,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
        serverLookup: (suspend (Long) -> WebDavServer?)? = null,
    ) : this(
        trackMetadataDao = trackMetadataDao,
        trackMetadataResolver = DefaultTrackMetadataResolver(webDavClient, coverArtStorage, ioDispatcher),
        coverArtStorage = coverArtStorage,
        webDavServerDao = webDavServerDao,
        maxConcurrency = maxConcurrency,
        ioDispatcher = ioDispatcher,
        repositoryScope = repositoryScope,
        serverLookup = serverLookup,
    )

    private val semaphore = Semaphore(maxConcurrency)
    private val knownServers = java.util.concurrent.ConcurrentHashMap<Long, WebDavServer>()
    private val inFlightSelfHealing = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    private suspend fun findServer(serverId: Long): WebDavServer? {
        knownServers[serverId]?.let { return it }
        serverLookup?.invoke(serverId)?.let {
            knownServers[serverId] = it
            return it
        }
        webDavServerDao?.getServerById(serverId)?.toDomain()?.let {
            knownServers[serverId] = it
            return it
        }
        return null
    }

    private fun triggerSelfHealing(
        serverId: Long,
        remotePath: String,
    ) {
        val key = "$serverId:$remotePath"
        if (inFlightSelfHealing.containsKey(key)) return

        val job =
            repositoryScope.launch {
                try {
                    val server = findServer(serverId) ?: return@launch
                    val fileName = remotePath.substringAfterLast('/').ifBlank { "track" }
                    val file = RemoteFile(name = fileName, path = remotePath)
                    val resolved =
                        semaphore.withPermit {
                            trackMetadataResolver.resolve(server, file)
                        }
                    if (resolved != null) {
                        trackMetadataDao.insertOrUpdate(TrackMetadataEntity.fromDomain(resolved))
                    }
                } catch (_: Exception) {
                    // Ignore transient errors during background self-healing
                } finally {
                    inFlightSelfHealing.remove(key)
                }
            }
        val existing = inFlightSelfHealing.putIfAbsent(key, job)
        if (existing != null) {
            job.cancel()
        }
    }

    private fun mapAndSelfHeal(entity: TrackMetadataEntity?): TrackMetadata? {
        if (entity == null) return null
        val thumbPath = entity.coverThumbnailPath
        return if (thumbPath != null) {
            if (coverArtStorage.isValidThumbnailFile(thumbPath)) {
                entity.toDomain()
            } else {
                triggerSelfHealing(entity.serverId, entity.remotePath)
                entity.toDomain().copy(coverThumbnailPath = null)
            }
        } else {
            entity.toDomain()
        }
    }

    override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> =
        trackMetadataDao.getAllMetadataForServerFlow(serverId).map { list ->
            list.mapNotNull { mapAndSelfHeal(it) }
        }

    override fun getMetadataForPathsFlow(
        serverId: Long,
        remotePaths: List<String>,
    ): Flow<List<TrackMetadata>> =
        trackMetadataDao.getMetadataForPathsFlow(serverId, remotePaths).map { list ->
            list.mapNotNull { mapAndSelfHeal(it) }
        }

    override fun getMetadataFlow(
        serverId: Long,
        remotePath: String,
    ): Flow<TrackMetadata?> =
        trackMetadataDao.getMetadataFlow(serverId, remotePath).map {
            mapAndSelfHeal(it)
        }

    override suspend fun getCachedMetadata(
        serverId: Long,
        remotePath: String,
    ): TrackMetadata? =
        withContext(ioDispatcher) {
            mapAndSelfHeal(trackMetadataDao.getMetadata(serverId, remotePath))
        }

    override suspend fun resolveMetadata(
        server: WebDavServer,
        files: List<RemoteFile>,
        forceRefresh: Boolean,
    ) = withContext(ioDispatcher) {
        knownServers[server.id] = server
        val audioFiles = files.filter { it.isAudio }
        if (audioFiles.isEmpty()) return@withContext

        // Query what is already cached in Room
        val cachedEntities = trackMetadataDao.getMetadataForPaths(server.id, audioFiles.map { it.path })
        val cachedEntityMap = cachedEntities.associateBy { it.remotePath }

        val toResolve =
            if (forceRefresh) {
                trackMetadataResolver.clearCache()
                audioFiles
            } else {
                audioFiles.filter { file ->
                    val cached = cachedEntityMap[file.path]
                    if (cached == null) {
                        true
                    } else if (cached.coverThumbnailPath != null && !coverArtStorage.isValidThumbnailFile(cached.coverThumbnailPath)) {
                        // Disk cache was deleted (e.g. user cleared app cache): must re-resolve to restore thumbnail!
                        true
                    } else {
                        false
                    }
                }
            }
        if (toResolve.isEmpty()) return@withContext

        val buffer = java.util.Collections.synchronizedList(mutableListOf<TrackMetadataEntity>())
        val batchThreshold = 6

        try {
            coroutineScope {
                toResolve.forEach { file ->
                    launch {
                        val metadata =
                            semaphore.withPermit {
                                trackMetadataResolver.resolve(server, file)
                            } ?: return@launch

                        val entity = TrackMetadataEntity.fromDomain(metadata)
                        val batchToWrite: List<TrackMetadataEntity>? =
                            synchronized(buffer) {
                                buffer.add(entity)
                                if (buffer.size >= batchThreshold) {
                                    val copy = ArrayList(buffer)
                                    buffer.clear()
                                    copy
                                } else {
                                    null
                                }
                            }

                        if (!batchToWrite.isNullOrEmpty()) {
                            trackMetadataDao.insertOrUpdateAll(batchToWrite)
                        }
                    }
                }
            }
        } finally {
            // Flush remaining entries in buffer even if coroutine was cancelled
            withContext(NonCancellable) {
                val remaining =
                    synchronized(buffer) {
                        val copy = ArrayList(buffer)
                        buffer.clear()
                        copy
                    }
                if (remaining.isNotEmpty()) {
                    trackMetadataDao.insertOrUpdateAll(remaining)
                }
            }
        }
    }

    override suspend fun resolveSingleTrackMetadata(
        server: WebDavServer,
        file: RemoteFile,
    ): TrackMetadata =
        withContext(ioDispatcher) {
            knownServers[server.id] = server
            val entity = trackMetadataDao.getMetadata(server.id, file.path)
            if (entity != null) {
                val thumbPath = entity.coverThumbnailPath
                if (thumbPath == null || coverArtStorage.isValidThumbnailFile(thumbPath)) {
                    return@withContext entity.toDomain()
                }
            }
            val metadata =
                semaphore.withPermit {
                    trackMetadataResolver.resolve(server, file)
                }
            if (metadata != null) {
                trackMetadataDao.insertOrUpdate(TrackMetadataEntity.fromDomain(metadata))
                metadata
            } else {
                // Transient error: do NOT write permanent failure to Room.
                // Return ephemeral fallback for immediate playback/display.
                TrackMetadata(
                    serverId = server.id,
                    remotePath = file.path,
                    title = file.name.substringBeforeLast('.').ifBlank { file.name },
                    artist = null,
                    album = null,
                    trackNumber = null,
                    durationMs = 0L,
                    coverThumbnailPath = null,
                    lyrics = null,
                )
            }
        }
}
