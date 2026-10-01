package com.webdav.player.data.repository

import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.data.metadata.AudioMetadataParser
import com.webdav.player.data.metadata.ImageHeaderValidator
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.RemoteDirectory
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TrackMetadataRepositoryImpl(
    private val trackMetadataDao: TrackMetadataDao,
    private val webDavClient: WebDavClient,
    private val coverArtStorage: CoverArtStorage,
    private val maxConcurrency: Int = 3,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrackMetadataRepository {
    private val semaphore = Semaphore(maxConcurrency)
    private val folderArtworkCache = ConcurrentHashMap<String, String>()
    private val folderLocks = ConcurrentHashMap<String, Mutex>()

    companion object {
        private const val INITIAL_RANGE_START = 0L
        private const val INITIAL_RANGE_END = 524287L // 512KB (524288 bytes)
        private const val MAX_TAG_SIZE_CAP = 8L * 1024L * 1024L // 8MB to support high-res embedded art
        private const val COVER_PROBE_MAX_SIZE = 8L * 1024L * 1024L // 8MB
        private const val NO_FOLDER_ARTWORK_SENTINEL = "__NO_FOLDER_ARTWORK__"
    }

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
    ) = withContext(ioDispatcher) {
        val audioFiles = files.filter { it.isAudio }
        if (audioFiles.isEmpty()) return@withContext

        // Query what is already cached in Room
        val cachedEntities = trackMetadataDao.getMetadataForPaths(server.id, audioFiles.map { it.path })
        val cachedPaths = cachedEntities.map { it.remotePath }.toSet()

        val toResolve = audioFiles.filter { it.path !in cachedPaths }
        if (toResolve.isEmpty()) return@withContext

        val buffer = java.util.Collections.synchronizedList(mutableListOf<TrackMetadataEntity>())
        val batchThreshold = 6

        coroutineScope {
            toResolve.forEach { file ->
                launch {
                    val metadata =
                        semaphore.withPermit {
                            parseAndBuildMetadata(server, file)
                        } ?: return@launch

                    val entity = TrackMetadataEntity.fromDomain(metadata)
                    var batchToWrite: List<TrackMetadataEntity>? = null

                    synchronized(buffer) {
                        buffer.add(entity)
                        if (buffer.size >= batchThreshold) {
                            batchToWrite = ArrayList(buffer)
                            buffer.clear()
                        }
                    }

                    if (batchToWrite != null && batchToWrite!!.isNotEmpty()) {
                        trackMetadataDao.insertOrUpdateAll(batchToWrite!!)
                    }
                }
            }
        }

        // Flush remaining entries in buffer
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

    override suspend fun resolveSingleTrackMetadata(
        server: WebDavServer,
        file: RemoteFile,
    ): TrackMetadata =
        withContext(ioDispatcher) {
            val cached = getCachedMetadata(server.id, file.path)
            if (cached != null) {
                return@withContext cached
            }
            val metadata =
                semaphore.withPermit {
                    parseAndBuildMetadata(server, file)
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

    private suspend fun parseAndBuildMetadata(
        server: WebDavServer,
        file: RemoteFile,
    ): TrackMetadata? {
        val cleanFallbackTitle = file.name.substringBeforeLast('.').ifBlank { file.name }

        val rangeBytes =
            try {
                webDavClient.fetchRange(server, file.path, INITIAL_RANGE_START, INITIAL_RANGE_END)
            } catch (e: java.io.IOException) {
                // Transient network failure fetching audio range -> allow retry later
                return null
            } catch (e: Exception) {
                null
            }

        // If rangeBytes is null or empty, audio range could not be read -> transient failure, do not cache
        if (rangeBytes == null || rangeBytes.isEmpty()) {
            return null
        }

        val requiredTagSize = AudioMetadataParser.detectRequiredTagSize(rangeBytes)
        val completeBytes =
            if (requiredTagSize != null && requiredTagSize > rangeBytes.size) {
                try {
                    fetchSecondaryRangeIfNeeded(server, file, rangeBytes)
                } catch (e: java.io.IOException) {
                    // Network failure during secondary range fetch -> transient error, retry later
                    return null
                } catch (e: Exception) {
                    rangeBytes
                }
            } else {
                rangeBytes
            }

        val audioFormat = (file.fileType as? RemoteFileType.Audio)?.format
        val parsed = AudioMetadataParser.parse(completeBytes, audioFormat)

        val embeddedThumb =
            if (parsed.artworkData != null && parsed.artworkData.isNotEmpty()) {
                coverArtStorage.saveThumbnail(server.id, file.path, parsed.artworkData)
            } else {
                null
            }

        val thumbnailPath =
            if (embeddedThumb != null) {
                embeddedThumb
            } else {
                try {
                    resolveFolderArtworkFallback(server, file)
                } catch (e: java.io.IOException) {
                    // Transient network / decode error probing folder artwork -> do not poison cache with null artwork
                    return null
                } catch (e: Exception) {
                    null
                }
            }

        val title = parsed.title?.takeIf { it.isNotBlank() } ?: cleanFallbackTitle
        val artist = parsed.artist?.takeIf { it.isNotBlank() }
        val album = parsed.album?.takeIf { it.isNotBlank() }

        return TrackMetadata(
            serverId = server.id,
            remotePath = file.path,
            title = title,
            artist = artist,
            album = album,
            trackNumber = parsed.trackNumber,
            durationMs = parsed.durationMs,
            coverThumbnailPath = thumbnailPath,
            lyrics = parsed.lyrics,
        )
    }

    private suspend fun fetchSecondaryRangeIfNeeded(
        server: WebDavServer,
        file: RemoteFile,
        initialBytes: ByteArray,
    ): ByteArray {
        val requiredTagSize = AudioMetadataParser.detectRequiredTagSize(initialBytes) ?: return initialBytes
        if (requiredTagSize <= initialBytes.size) return initialBytes

        val targetSize =
            if (file.size > 0L) {
                minOf(requiredTagSize, MAX_TAG_SIZE_CAP, file.size)
            } else {
                minOf(requiredTagSize, MAX_TAG_SIZE_CAP)
            }

        if (targetSize <= initialBytes.size) return initialBytes

        val secondChunk =
            webDavClient.fetchRange(
                server = server,
                remotePath = file.path,
                startByte = initialBytes.size.toLong(),
                endByte = targetSize - 1L,
            ) ?: throw java.io.IOException("Secondary range fetch returned null for ${file.path}")

        if (secondChunk.isEmpty()) {
            throw java.io.IOException("Secondary range fetch returned empty chunk for ${file.path}")
        }

        return if (secondChunk.size >= targetSize && AudioMetadataParser.hasRecognizedAudioHeader(secondChunk)) {
            secondChunk
        } else {
            ByteArray(initialBytes.size + secondChunk.size).apply {
                System.arraycopy(initialBytes, 0, this, 0, initialBytes.size)
                System.arraycopy(secondChunk, 0, this, initialBytes.size, secondChunk.size)
            }
        }
    }

    private suspend fun resolveFolderArtworkFallback(
        server: WebDavServer,
        file: RemoteFile,
    ): String? {
        val parentDir = RemoteDirectory.getParentPath(file.path) ?: ""
        val baseName = file.name.substringBeforeLast('.').ifBlank { file.name }
        val folderKey = "${server.id}:$parentDir"

        // 1. Check track-specific artwork first: "${trackName}.jpg", "${trackName}.png", "${filename}.jpg", "${filename}.png"
        // Track-specific artwork must always take precedence over folder-level shared covers.
        val trackCandidates =
            listOf(
                "$baseName.jpg",
                "$baseName.png",
                "${file.name}.jpg",
                "${file.name}.png",
            )
        val trackResult = probeCandidates(server, parentDir, trackCandidates)
        when (trackResult) {
            is ProbeResult.Found -> {
                return trackResult.path
            }

            is ProbeResult.NetworkError -> {
                throw trackResult.cause
            }

            ProbeResult.NotFound -> { /* Continue to folder-level artwork */ }
        }

        // 2. If folder artwork has already been resolved for this directory, reuse it immediately
        val cachedFolderThumb = folderArtworkCache[folderKey]
        if (cachedFolderThumb != null) {
            return if (cachedFolderThumb == NO_FOLDER_ARTWORK_SENTINEL) null else cachedFolderThumb
        }

        // 3. Folder artwork not yet probed: synchronize probing of shared folder covers
        val mutex = folderLocks.computeIfAbsent(folderKey) { Mutex() }
        return mutex.withLock {
            val doubleCheck = folderArtworkCache[folderKey]
            if (doubleCheck != null) {
                return@withLock if (doubleCheck == NO_FOLDER_ARTWORK_SENTINEL) null else doubleCheck
            }

            val folderCandidates =
                listOf(
                    "cover.jpg",
                    "folder.jpg",
                    "front.jpg",
                    "cover.png",
                )
            val folderResult = probeCandidates(server, parentDir, folderCandidates)
            when (folderResult) {
                is ProbeResult.Found -> {
                    folderArtworkCache[folderKey] = folderResult.path
                    folderResult.path
                }

                ProbeResult.NotFound -> {
                    folderArtworkCache[folderKey] = NO_FOLDER_ARTWORK_SENTINEL
                    null
                }

                is ProbeResult.NetworkError -> {
                    throw folderResult.cause
                }
            }
        }
    }

    private sealed interface ProbeResult {
        data class Found(
            val path: String,
        ) : ProbeResult

        object NotFound : ProbeResult

        data class NetworkError(
            val cause: java.io.IOException,
        ) : ProbeResult
    }

    private suspend fun probeCandidates(
        server: WebDavServer,
        parentDir: String,
        candidateFileNames: List<String>,
    ): ProbeResult {
        for (name in candidateFileNames) {
            val path = buildCandidatePath(parentDir, name)
            try {
                val thumb = probeAndCacheArtwork(server, path)
                if (thumb != null) {
                    return ProbeResult.Found(thumb)
                }
            } catch (e: java.io.IOException) {
                return ProbeResult.NetworkError(e)
            }
        }
        return ProbeResult.NotFound
    }

    private suspend fun probeAndCacheArtwork(
        server: WebDavServer,
        candidatePath: String,
    ): String? {
        val localThumb = coverArtStorage.getThumbnailFile(server.id, candidatePath)
        if (localThumb != null && localThumb.exists()) {
            return localThumb.absolutePath
        }

        val imageBytes =
            webDavClient.fetchRange(
                server = server,
                remotePath = candidatePath,
                startByte = 0L,
                endByte = COVER_PROBE_MAX_SIZE - 1L,
            )

        if (imageBytes != null && imageBytes.isNotEmpty()) {
            if (!ImageHeaderValidator.isCompleteImage(imageBytes)) {
                return null
            }
            return coverArtStorage.saveThumbnail(server.id, candidatePath, imageBytes)
        }
        return null
    }

    private fun buildCandidatePath(
        directory: String,
        fileName: String,
    ): String =
        when {
            directory.isEmpty() -> fileName
            directory == "/" -> "/$fileName"
            directory.endsWith('/') -> "$directory$fileName"
            else -> "$directory/$fileName"
        }
}
