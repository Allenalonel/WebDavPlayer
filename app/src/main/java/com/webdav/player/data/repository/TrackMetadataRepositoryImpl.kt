package com.webdav.player.data.repository

import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.data.metadata.DefaultTrackMetadataResolver
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.metadata.TrackMetadataResolver
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class TrackMetadataRepositoryImpl(
    private val trackMetadataDao: TrackMetadataDao,
    private val trackMetadataResolver: TrackMetadataResolver,
    private val coverArtStorage: CoverArtStorage,
    private val maxConcurrency: Int = 3,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrackMetadataRepository {
    constructor(
        trackMetadataDao: TrackMetadataDao,
        webDavClient: WebDavClient,
        coverArtStorage: CoverArtStorage,
        maxConcurrency: Int = 3,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        trackMetadataDao = trackMetadataDao,
        trackMetadataResolver = DefaultTrackMetadataResolver(webDavClient, coverArtStorage, ioDispatcher),
        coverArtStorage = coverArtStorage,
        maxConcurrency = maxConcurrency,
        ioDispatcher = ioDispatcher,
    )

    private val semaphore = Semaphore(maxConcurrency)

    override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> =
        trackMetadataDao.getAllMetadataForServerFlow(serverId).map { list ->
            list.map { it.toDomain() }
        }

    override fun getMetadataForPathsFlow(
        serverId: Long,
        remotePaths: List<String>,
    ): Flow<List<TrackMetadata>> =
        trackMetadataDao.getMetadataForPathsFlow(serverId, remotePaths).map { list ->
            list.map { it.toDomain() }
        }

    override fun getMetadataFlow(
        serverId: Long,
        remotePath: String,
    ): Flow<TrackMetadata?> =
        trackMetadataDao.getMetadataFlow(serverId, remotePath).map {
            it?.toDomain()
        }

    override suspend fun getCachedMetadata(
        serverId: Long,
        remotePath: String,
    ): TrackMetadata? =
        withContext(ioDispatcher) {
            trackMetadataDao.getMetadata(serverId, remotePath)?.toDomain()
        }

    override suspend fun resolveMetadata(
        server: WebDavServer,
        files: List<RemoteFile>,
        forceRefresh: Boolean,
    ) = withContext(ioDispatcher) {
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
            val cached = getCachedMetadata(server.id, file.path)
            if (cached != null) {
                if (cached.coverThumbnailPath == null || coverArtStorage.isValidThumbnailFile(cached.coverThumbnailPath)) {
                    return@withContext cached
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
