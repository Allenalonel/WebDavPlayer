package com.webdav.player.data.repository

import com.webdav.player.data.lyrics.LrcParser
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Lyrics
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.LyricsRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepositoryImpl(
    private val webDavClient: WebDavClient,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LyricsRepository {
    companion object {
        private const val MAX_LYRICS_CACHE_SIZE = 100
    }

    private val cacheLock = Any()
    private val cache =
        object : LinkedHashMap<String, Lyrics>(MAX_LYRICS_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?): Boolean = size > MAX_LYRICS_CACHE_SIZE
        }

    override suspend fun resolveLyrics(
        server: WebDavServer,
        track: AudioTrack,
    ): Lyrics =
        withContext(ioDispatcher) {
            val cacheKey = "${server.id}:${track.remotePath}"
            synchronized(cacheLock) {
                cache[cacheKey]?.let { return@withContext it }
            }

            // 1. Probe remote directory for ${baseName}.lrc
            val lrcPath = track.remotePath.substringBeforeLast('.') + ".lrc"
            try {
                val remoteText = webDavClient.fetchText(server, lrcPath)
                if (!remoteText.isNullOrBlank()) {
                    val parsed = LrcParser.parse(remoteText)
                    if (parsed.isNotEmpty) {
                        synchronized(cacheLock) {
                            cache[cacheKey] = parsed
                        }
                        return@withContext parsed
                    }
                }
            } catch (e: Exception) {
                // Ignore and fall through to embedded metadata
            }

            // 2. Fall back to embedded metadata via TrackMetadataRepository
            try {
                val file = RemoteFile(name = track.title, path = track.remotePath)
                val embeddedText: String? =
                    trackMetadataRepository.getCachedMetadata(server.id, track.remotePath)?.lyrics?.takeIf { it.isNotBlank() }
                        ?: trackMetadataRepository.resolveSingleTrackMetadata(server, file).lyrics

                if (!embeddedText.isNullOrBlank()) {
                    val parsed = LrcParser.parse(embeddedText)
                    if (parsed.isNotEmpty) {
                        synchronized(cacheLock) {
                            cache[cacheKey] = parsed
                        }
                        return@withContext parsed
                    }
                }
            } catch (e: Exception) {
                // Ignore error
            }

            // 3. No lyrics available from either source
            val empty = Lyrics.EMPTY
            synchronized(cacheLock) {
                cache[cacheKey] = empty
            }
            empty
        }

    override fun clearCache() {
        synchronized(cacheLock) {
            cache.clear()
        }
    }

    override fun clearMemoryCache() {
        synchronized(cacheLock) {
            cache.clear()
        }
    }
}
