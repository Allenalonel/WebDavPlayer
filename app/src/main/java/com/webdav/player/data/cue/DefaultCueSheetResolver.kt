package com.webdav.player.data.cue

import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
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

    override fun resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem> {
        val cueFiles = directory.files.filter { it.isCue }
        val audioFiles = directory.files.filter { it.isAudio }
        if (cueFiles.isEmpty()) return emptyList()

        return cueFiles.map { cueFile ->
            val matchedAudio =
                findMatchingAudioFile(
                    cueFile = cueFile,
                    audioFiles = audioFiles,
                    totalCueFilesCount = cueFiles.size,
                )
            CueAlbumItem(
                cueFile = cueFile,
                audioFile = matchedAudio,
                tracks = emptyList(),
                isExpanded = false,
                isLoading = true,
                errorMessage = null,
            )
        }
    }

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
                            findMatchingAudioFile(
                                cueFile = cueFile,
                                audioFiles = audioFiles,
                                referencedFileNames = referencedFiles,
                                totalCueFilesCount = cueFiles.size,
                            )

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

    override fun getCachedVirtualTracks(
        server: WebDavServer,
        cuePath: String,
        parentAudioPath: String,
    ): List<VirtualTrack>? {
        val key = cacheKey(server.id, cuePath)
        val cueText = textCache.get(key) ?: return null
        if (cueText.isBlank()) return null
        return try {
            CueParser.parse(
                content = cueText,
                parentAudioPath = parentAudioPath,
                allowMissingFileTag = true,
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun clearCache() {
        textCache.clear()
    }

    private fun findMatchingAudioFile(
        cueFile: RemoteFile,
        audioFiles: List<RemoteFile>,
        referencedFileNames: List<String> = emptyList(),
        totalCueFilesCount: Int = 1,
    ): RemoteFile? {
        if (audioFiles.isEmpty()) return null

        // 1. Check referenced file names from CUE FILE directives
        for (ref in referencedFileNames) {
            val cleanRef = ref.trim().replace('\\', '/').substringAfterLast('/')
            // Exact match
            val exact = audioFiles.firstOrNull { it.name.equals(cleanRef, ignoreCase = true) }
            if (exact != null) return exact

            // Base name match with different extension (e.g. CUE says CDImage.wav, but file is CDImage.flac)
            val refBase = cleanRef.substringBeforeLast('.')
            val baseMatch =
                audioFiles.firstOrNull {
                    it.name.substringBeforeLast('.').equals(refBase, ignoreCase = true)
                }
            if (baseMatch != null) return baseMatch
        }

        // 2. Base name match between CUE file and audio file
        val cueBase = cueFile.name.substringBeforeLast('.')
        val sameBaseAudio =
            audioFiles.firstOrNull {
                it.name.substringBeforeLast('.').equals(cueBase, ignoreCase = true)
            }
        if (sameBaseAudio != null) return sameBaseAudio

        // 3. Fallback: If only 1 CUE file and 1 audio file exist in the remote directory, associate them
        if (referencedFileNames.isEmpty() && totalCueFilesCount == 1 && audioFiles.size == 1) {
            return audioFiles.first()
        }

        // 4. Prefix match: if cue file name starts with audio file name base or vice versa
        if (referencedFileNames.isEmpty()) {
            val prefixMatch =
                audioFiles.firstOrNull {
                    val audioBase = it.name.substringBeforeLast('.')
                    cueBase.startsWith(audioBase, ignoreCase = true) || audioBase.startsWith(cueBase, ignoreCase = true)
                }
            if (prefixMatch != null) return prefixMatch
        }

        return null
    }
}
