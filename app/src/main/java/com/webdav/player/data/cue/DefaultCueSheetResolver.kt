package com.webdav.player.data.cue

import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default implementation of [CueSheetResolver].
 *
 * Encapsulates:
 * 1. Thread-safe bounded LRU in-memory cache for raw CUE text (default capacity: 50 items).
 * 2. Transient zero-disk retention strictly conforming to ADR-0002.
 * 3. File association heuristics matching CUE sheets to physical parent audio files.
 * 4. Streaming CUE sheet AST parsing with millisecond interval calculations.
 */
class DefaultCueSheetResolver(
    private val webDavClient: WebDavClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
) : CueSheetResolver {
    companion object {
        const val DEFAULT_CACHE_CAPACITY = 50
    }

    private class BoundedLruCache<K, V>(
        private val maxEntries: Int,
    ) {
        private val map =
            object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
            }

        @Synchronized
        fun get(key: K): V? = map[key]

        @Synchronized
        fun put(
            key: K,
            value: V,
        ) {
            map[key] = value
        }

        @Synchronized
        fun remove(key: K): V? = map.remove(key)

        @Synchronized
        fun clear() {
            map.clear()
        }

        @Synchronized
        fun size(): Int = map.size
    }

    private val textCache = BoundedLruCache<String, String>(cacheCapacity)

    private fun cacheKey(
        serverId: Long?,
        cuePath: String,
    ): String = "${serverId ?: 0L}:$cuePath"

    override suspend fun resolveDirectoryCueAlbums(
        server: WebDavServer,
        directory: RemoteDirectory,
        parentDurationsByPath: Map<String, Long>,
    ): List<CueAlbumItem> =
        withContext(ioDispatcher) {
            val cueFiles = directory.files.filter { it.isCue }
            val audioFiles = directory.files.filter { it.isAudio }

            if (cueFiles.isEmpty()) {
                return@withContext emptyList()
            }

            cueFiles.map { cueFile ->
                try {
                    val key = cacheKey(server.id, cueFile.path)
                    val cueContent =
                        textCache.get(key)
                            ?: webDavClient.fetchText(server, cueFile.path)?.also {
                                if (it.isNotBlank()) {
                                    textCache.put(key, it)
                                }
                            }

                    if (cueContent.isNullOrBlank()) {
                        CueAlbumItem(
                            cueFile = cueFile,
                            audioFile = null,
                            tracks = emptyList(),
                            isExpanded = false,
                            isLoading = false,
                            errorMessage = "无法读取 CUE 文件内容",
                        )
                    } else {
                        val referencedFiles = CueParser.extractReferencedFiles(cueContent)
                        val matchedAudio =
                            CueAssociationHelper.findMatchingAudioFile(
                                cueFile = cueFile,
                                audioFiles = audioFiles,
                                referencedFileNames = referencedFiles,
                                totalCueFilesCount = cueFiles.size,
                            ) ?: if (referencedFiles.isEmpty()) {
                                CueAssociationHelper.findMatchingAudioFile(
                                    cueFile = cueFile,
                                    audioFiles = audioFiles,
                                    totalCueFilesCount = cueFiles.size,
                                )
                            } else {
                                null
                            }

                        if (matchedAudio == null) {
                            CueAlbumItem(
                                cueFile = cueFile,
                                audioFile = null,
                                tracks = emptyList(),
                                isExpanded = false,
                                isLoading = false,
                                errorMessage = "未找到 CUE 指向的目标音频文件",
                            )
                        } else {
                            val parentDurationMs = parentDurationsByPath[matchedAudio.path]
                            val tracks =
                                CueParser.parse(
                                    content = cueContent,
                                    parentAudioPath = matchedAudio.path,
                                    totalDurationMs = parentDurationMs,
                                    allowMissingFileTag = true,
                                )

                            if (tracks.isEmpty()) {
                                CueAlbumItem(
                                    cueFile = cueFile,
                                    audioFile = matchedAudio,
                                    tracks = emptyList(),
                                    isExpanded = false,
                                    isLoading = false,
                                    errorMessage = "CUE 文件未包含有效分轨",
                                )
                            } else {
                                CueAlbumItem(
                                    cueFile = cueFile,
                                    audioFile = matchedAudio,
                                    tracks = tracks,
                                    isExpanded = false,
                                    isLoading = false,
                                    errorMessage = null,
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    CueAlbumItem(
                        cueFile = cueFile,
                        audioFile = null,
                        tracks = emptyList(),
                        isExpanded = false,
                        isLoading = false,
                        errorMessage = e.message ?: "解析 CUE 文件失败",
                    )
                }
            }
        }

    override suspend fun resolveVirtualTracks(
        server: WebDavServer,
        cuePath: String,
        parentAudioPath: String,
        totalDurationMs: Long?,
    ): List<VirtualTrack> =
        withContext(ioDispatcher) {
            val key = cacheKey(server.id, cuePath)
            val cueText =
                textCache.get(key)
                    ?: webDavClient.fetchText(server, cuePath)?.also {
                        if (it.isNotBlank()) {
                            textCache.put(key, it)
                        }
                    }

            if (cueText.isNullOrBlank()) {
                return@withContext emptyList()
            }

            try {
                CueParser.parse(
                    content = cueText,
                    parentAudioPath = parentAudioPath,
                    totalDurationMs = totalDurationMs,
                    allowMissingFileTag = true,
                )
            } catch (_: Exception) {
                emptyList()
            }
        }

    override fun clearCache() {
        textCache.clear()
    }
}
