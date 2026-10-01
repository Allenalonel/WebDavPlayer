package com.webdav.player.data.metadata

import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal class DefaultTrackMetadataResolver(
    private val webDavClient: WebDavClient,
    private val coverArtStorage: CoverArtStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val folderArtworkCache = ConcurrentHashMap<String, String>()
    private val folderLocks = ConcurrentHashMap<String, Mutex>()

    companion object {
        private const val INITIAL_RANGE_START = 0L
        private const val INITIAL_RANGE_END = 524287L // 512KB (524288 bytes)
        private const val MAX_TAG_SIZE_CAP = 8L * 1024L * 1024L // 8MB to support high-res embedded art
        private const val COVER_PROBE_MAX_SIZE = 8L * 1024L * 1024L // 8MB
        private const val NO_FOLDER_ARTWORK_SENTINEL = "__NO_FOLDER_ARTWORK__"
    }

    suspend fun resolve(
        server: WebDavServer,
        file: RemoteFile,
    ): TrackMetadata? =
        withContext(ioDispatcher) {
            val cleanFallbackTitle = file.name.substringBeforeLast('.').ifBlank { file.name }

            val rangeBytes =
                try {
                    webDavClient.fetchRange(server, file.path, INITIAL_RANGE_START, INITIAL_RANGE_END)
                } catch (e: IOException) {
                    // Transient network failure fetching audio range -> allow retry later
                    return@withContext null
                } catch (e: Exception) {
                    null
                }

            // If rangeBytes is null or empty, audio range could not be read -> transient failure, do not cache
            if (rangeBytes == null || rangeBytes.isEmpty()) {
                return@withContext null
            }

            val audioFormat = (file.fileType as? RemoteFileType.Audio)?.format
            val initialParsed = AudioMetadataParser.parse(rangeBytes, audioFormat)
            val initialArtworkComplete =
                initialParsed.artworkData != null &&
                    initialParsed.artworkData.isNotEmpty() &&
                    ImageHeaderValidator.isCompleteImage(initialParsed.artworkData)

            val completeBytes =
                if (!initialArtworkComplete) {
                    val requiredTagSize = AudioMetadataParser.detectRequiredTagSize(rangeBytes)
                    if (requiredTagSize != null && requiredTagSize > rangeBytes.size) {
                        try {
                            fetchSecondaryRangeIfNeeded(server, file, rangeBytes)
                        } catch (e: IOException) {
                            // Network failure during secondary range fetch -> transient error, retry later
                            return@withContext null
                        } catch (e: Exception) {
                            rangeBytes
                        }
                    } else {
                        rangeBytes
                    }
                } else {
                    rangeBytes
                }

            val parsed =
                if (completeBytes !== rangeBytes) {
                    AudioMetadataParser.parse(completeBytes, audioFormat)
                } else {
                    initialParsed
                }

            val embeddedThumb =
                if (parsed.artworkData != null && parsed.artworkData.isNotEmpty() &&
                    ImageHeaderValidator.isCompleteImage(parsed.artworkData)
                ) {
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
                    } catch (e: IOException) {
                        // Transient network / decode error probing folder artwork -> do not poison cache with null artwork
                        return@withContext null
                    } catch (e: Exception) {
                        null
                    }
                }

            val title = parsed.title?.takeIf { it.isNotBlank() } ?: cleanFallbackTitle
            val artist = parsed.artist?.takeIf { it.isNotBlank() }
            val album = parsed.album?.takeIf { it.isNotBlank() }

            TrackMetadata(
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

    fun clearCache() {
        folderArtworkCache.clear()
        folderLocks.clear()
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

        val expectedBytes = targetSize - initialBytes.size
        val secondChunk =
            webDavClient.fetchRange(
                server = server,
                remotePath = file.path,
                startByte = initialBytes.size.toLong(),
                endByte = targetSize - 1L,
            ) ?: throw IOException("Secondary range fetch returned null for ${file.path}")

        if (secondChunk.isEmpty()) {
            throw IOException("Secondary range fetch returned empty chunk for ${file.path}")
        }

        if (AudioMetadataParser.hasRecognizedAudioHeader(secondChunk)) {
            if (secondChunk.size >= targetSize) {
                return secondChunk
            } else {
                throw IOException(
                    "Secondary range fetch returned full-stream chunk from byte 0 with insufficient length (${secondChunk.size} < $targetSize)",
                )
            }
        }

        if (secondChunk.size < expectedBytes) {
            throw IOException(
                "Secondary range fetch returned partial chunk (${secondChunk.size} < $expectedBytes)",
            )
        }

        return ByteArray(initialBytes.size + secondChunk.size).apply {
            System.arraycopy(initialBytes, 0, this, 0, initialBytes.size)
            System.arraycopy(secondChunk, 0, this, initialBytes.size, secondChunk.size)
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

        // 2. If folder artwork has already been resolved for this directory, reuse it immediately if physical file exists
        val cachedFolderThumb = folderArtworkCache[folderKey]
        if (cachedFolderThumb != null) {
            if (cachedFolderThumb == NO_FOLDER_ARTWORK_SENTINEL) {
                return null
            }
            if (coverArtStorage.isValidThumbnailFile(cachedFolderThumb)) {
                return cachedFolderThumb
            } else {
                folderArtworkCache.remove(folderKey)
            }
        }

        // 3. Folder artwork not yet probed: synchronize probing of shared folder covers
        val mutex = folderLocks.computeIfAbsent(folderKey) { Mutex() }
        return mutex.withLock {
            val doubleCheck = folderArtworkCache[folderKey]
            if (doubleCheck != null) {
                if (doubleCheck == NO_FOLDER_ARTWORK_SENTINEL) {
                    return@withLock null
                }
                if (coverArtStorage.isValidThumbnailFile(doubleCheck)) {
                    return@withLock doubleCheck
                } else {
                    folderArtworkCache.remove(folderKey)
                }
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

        data object NotFound : ProbeResult

        data class NetworkError(
            val cause: IOException,
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
            } catch (e: IOException) {
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
        if (localThumb != null && coverArtStorage.isValidThumbnailFile(localThumb.absolutePath)) {
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
            directory.endsWith("/") -> "$directory$fileName"
            else -> "$directory/$fileName"
        }
}
