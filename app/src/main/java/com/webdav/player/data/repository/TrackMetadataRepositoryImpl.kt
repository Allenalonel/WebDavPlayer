package com.webdav.player.data.repository

import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.data.metadata.AudioMetadataParser
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class TrackMetadataRepositoryImpl(
    private val trackMetadataDao: TrackMetadataDao,
    private val webDavClient: WebDavClient,
    private val coverArtStorage: CoverArtStorage,
    private val maxConcurrency: Int = 3,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TrackMetadataRepository {

    private val semaphore = Semaphore(maxConcurrency)

    override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> {
        return trackMetadataDao.getAllMetadataForServerFlow(serverId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getMetadataForPathsFlow(serverId: Long, remotePaths: List<String>): Flow<List<TrackMetadata>> {
        return trackMetadataDao.getMetadataForPathsFlow(serverId, remotePaths).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getMetadataFlow(serverId: Long, remotePath: String): Flow<TrackMetadata?> {
        return trackMetadataDao.getMetadataFlow(serverId, remotePath).map { it?.toDomain() }
    }

    override suspend fun getCachedMetadata(serverId: Long, remotePath: String): TrackMetadata? = withContext(ioDispatcher) {
        trackMetadataDao.getMetadata(serverId, remotePath)?.toDomain()
    }

    override suspend fun resolveMetadata(
        server: WebDavServer,
        files: List<RemoteFile>
    ) = withContext(ioDispatcher) {
        val audioFiles = files.filter { it.isAudio }
        if (audioFiles.isEmpty()) return@withContext

        // Query what is already cached in Room
        val cachedEntities = trackMetadataDao.getMetadataForPaths(server.id, audioFiles.map { it.path })
        val cachedPaths = cachedEntities.map { it.remotePath }.toSet()

        val toResolve = audioFiles.filter { it.path !in cachedPaths }
        if (toResolve.isEmpty()) return@withContext

        coroutineScope {
            toResolve.forEach { file ->
                launch {
                    semaphore.withPermit {
                        resolveAndPersist(server, file)
                    }
                }
            }
        }
    }

    override suspend fun resolveSingleTrackMetadata(
        server: WebDavServer,
        file: RemoteFile
    ): TrackMetadata = withContext(ioDispatcher) {
        val cached = getCachedMetadata(server.id, file.path)
        if (cached != null) {
            return@withContext cached
        }
        semaphore.withPermit {
            resolveAndPersist(server, file)
        }
    }

    private suspend fun resolveAndPersist(server: WebDavServer, file: RemoteFile): TrackMetadata {
        val cleanFallbackTitle = file.name.substringBeforeLast('.').ifBlank { file.name }

        try {
            val rangeBytes = webDavClient.fetchRange(server, file.path, 0L, 131071L)
            if (rangeBytes != null && rangeBytes.isNotEmpty()) {
                val audioFormat = (file.fileType as? RemoteFileType.Audio)?.format
                val parsed = AudioMetadataParser.parse(rangeBytes, audioFormat)

                val thumbnailPath = if (parsed.artworkData != null && parsed.artworkData.isNotEmpty()) {
                    coverArtStorage.saveThumbnail(server.id, file.path, parsed.artworkData)
                } else {
                    null
                }

                val title = parsed.title?.takeIf { it.isNotBlank() } ?: cleanFallbackTitle
                val artist = parsed.artist?.takeIf { it.isNotBlank() }
                val album = parsed.album?.takeIf { it.isNotBlank() }

                val metadata = TrackMetadata(
                    serverId = server.id,
                    remotePath = file.path,
                    title = title,
                    artist = artist,
                    album = album,
                    trackNumber = parsed.trackNumber,
                    durationMs = parsed.durationMs,
                    coverThumbnailPath = thumbnailPath,
                    lyrics = parsed.lyrics
                )

                trackMetadataDao.insertOrUpdate(TrackMetadataEntity.fromDomain(metadata))
                return metadata
            }
        } catch (e: Exception) {
            // Graceful fallback on network/parsing error
        }

        // Fallback metadata saved in Room to avoid repetitive probing
        val fallback = TrackMetadata(
            serverId = server.id,
            remotePath = file.path,
            title = cleanFallbackTitle,
            artist = null,
            album = null,
            trackNumber = null,
            durationMs = 0L,
            coverThumbnailPath = null,
            lyrics = null
        )
        trackMetadataDao.insertOrUpdate(TrackMetadataEntity.fromDomain(fallback))
        return fallback
    }
}
