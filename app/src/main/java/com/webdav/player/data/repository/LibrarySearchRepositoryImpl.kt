package com.webdav.player.data.repository

import android.content.Context
import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.LibrarySearchResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.LibrarySearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray

class LibrarySearchRepositoryImpl(
    private val directoryCacheDao: DirectoryCacheDao,
    private val trackMetadataDao: TrackMetadataDao,
    private val cueSheetResolver: CueSheetResolver? = null,
    private val context: Context? = null,
    private val prefsName: String = "webdav_search_history",
) : LibrarySearchRepository {
    companion object {
        private const val MAX_HISTORY_SIZE = 20
        private const val KEY_HISTORY_JSON = "history_json"
        private const val MAX_CACHE_ENTRIES = 100
    }

    private val inMemoryHistory = mutableListOf<String>()

    private val prefs by lazy {
        context?.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    private val directoryCache =
        object : LinkedHashMap<String, RemoteDirectory>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RemoteDirectory>?): Boolean = size > MAX_CACHE_ENTRIES
        }

    private fun getOrDeserialize(dirEntity: DirectoryCacheEntity): RemoteDirectory? {
        val cacheKey = "${dirEntity.serverId}:${dirEntity.path}:${dirEntity.lastUpdatedMs}:${dirEntity.dataJson.length}"
        synchronized(directoryCache) {
            directoryCache[cacheKey]?.let { return it }
        }
        val deserialized = RemoteDirectoryJsonSerializer.deserialize(dirEntity.dataJson) ?: return null
        synchronized(directoryCache) {
            directoryCache[cacheKey] = deserialized
        }
        return deserialized
    }

    private fun saveHistory(history: List<String>) {
        val sharedPrefs = prefs
        if (sharedPrefs != null) {
            sharedPrefs.edit().putString(KEY_HISTORY_JSON, JSONArray(history).toString()).apply()
        } else {
            synchronized(inMemoryHistory) {
                inMemoryHistory.clear()
                inMemoryHistory.addAll(history)
            }
        }
    }

    override fun getSearchHistory(): List<String> {
        val sharedPrefs = prefs ?: return synchronized(inMemoryHistory) { inMemoryHistory.toList() }
        val raw = sharedPrefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun recordSearchQuery(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return getSearchHistory()
        val current = getSearchHistory().filter { it != trimmed }
        val updated = (listOf(trimmed) + current).take(MAX_HISTORY_SIZE)
        saveHistory(updated)
        return updated
    }

    override fun removeSearchHistory(query: String): List<String> {
        val updated = getSearchHistory().filter { it != query }
        saveHistory(updated)
        return updated
    }

    override fun clearSearchHistory() {
        val sharedPrefs = prefs
        if (sharedPrefs != null) {
            sharedPrefs.edit().remove(KEY_HISTORY_JSON).apply()
        } else {
            synchronized(inMemoryHistory) {
                inMemoryHistory.clear()
            }
        }
    }

    private fun isMatch(
        query: String,
        vararg candidates: String?,
    ): Boolean = candidates.any { it?.contains(query, ignoreCase = true) == true }

    private fun createAudioTrack(
        serverId: Long,
        remotePath: String,
        fileName: String,
        fileSize: Long,
        format: AudioFormat,
        metadata: TrackMetadataEntity?,
        albumFallback: String? = null,
    ): AudioTrack =
        AudioTrack(
            id = "$serverId:$remotePath",
            serverId = serverId,
            remotePath = remotePath,
            title = metadata?.title?.takeIf { it.isNotBlank() } ?: fileName,
            artist = metadata?.artist?.takeIf { it.isNotBlank() },
            album = metadata?.album?.takeIf { it.isNotBlank() } ?: albumFallback,
            durationMs = metadata?.durationMs ?: 0L,
            size = fileSize,
            format = format,
            coverThumbnailPath = metadata?.coverThumbnailPath,
        )

    override fun search(
        query: String,
        serverId: Long?,
    ): Flow<LibrarySearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return flowOf(LibrarySearchResult())
        }

        val dirFlow = directoryCacheDao.searchDirectoryCacheFlow(cleanQuery, serverId)
        val metaFlow = trackMetadataDao.searchMetadataFlow(cleanQuery, serverId)

        return combine(dirFlow, metaFlow) { dirEntities, metaEntities ->
            val metadataMap = metaEntities.associateBy { "${it.serverId}:${it.remotePath}" }
            val matchedDirs = mutableMapOf<String, RemoteDirectory>()
            val matchedTracks = mutableMapOf<String, AudioTrack>()

            for (dirEntity in dirEntities) {
                val isCueCandidate = cueSheetResolver != null && dirEntity.dataJson.contains(".cue", ignoreCase = true)
                val matchesPath = dirEntity.path.contains(cleanQuery, ignoreCase = true)
                val matchesJson = dirEntity.dataJson.contains(cleanQuery, ignoreCase = true)
                if (!matchesPath && !matchesJson && !isCueCandidate) {
                    continue
                }

                val remoteDir = getOrDeserialize(dirEntity) ?: continue

                if (isMatch(cleanQuery, remoteDir.name, remoteDir.path)) {
                    matchedDirs["${dirEntity.serverId}:${remoteDir.path}"] = remoteDir.copy(serverId = dirEntity.serverId)
                }

                for (sub in remoteDir.subDirectories) {
                    if (isMatch(cleanQuery, sub.name, sub.path)) {
                        matchedDirs["${dirEntity.serverId}:${sub.path}"] = sub.copy(serverId = dirEntity.serverId)
                    }
                }

                val audioFiles = remoteDir.files.filter { it.isAudio }
                val cueFiles = remoteDir.files.filter { it.isCue }

                for (file in audioFiles) {
                    val metaKey = "${dirEntity.serverId}:${file.path}"
                    val meta = metadataMap[metaKey]
                    val format = (file.fileType as? RemoteFileType.Audio)?.format ?: AudioFormat.MP3
                    val track = createAudioTrack(dirEntity.serverId, file.path, file.name, file.size, format, meta)

                    if (isMatch(cleanQuery, track.title, track.artist, track.album, file.name, file.path)) {
                        matchedTracks[track.id] = track
                    }
                }

                if (cueFiles.isNotEmpty() && audioFiles.isNotEmpty() && cueSheetResolver != null) {
                    val server = WebDavServer(id = dirEntity.serverId, name = "", url = "", port = 0)
                    val parentDurations =
                        audioFiles.associate {
                            it.path to (metadataMap["${dirEntity.serverId}:${it.path}"]?.durationMs ?: 0L)
                        }
                    val albums = cueSheetResolver.resolveDirectoryCueAlbums(server, remoteDir, parentDurations)
                    for (album in albums) {
                        val matchedAudioFile = album.audioFile ?: continue
                        if (album.tracks.isEmpty()) continue

                        val parentMeta = metadataMap["${dirEntity.serverId}:${matchedAudioFile.path}"]
                        val parentFormat = (matchedAudioFile.fileType as? RemoteFileType.Audio)?.format ?: AudioFormat.FLAC
                        val parentTrack =
                            createAudioTrack(
                                serverId = dirEntity.serverId,
                                remotePath = matchedAudioFile.path,
                                fileName = matchedAudioFile.name,
                                fileSize = matchedAudioFile.size,
                                format = parentFormat,
                                metadata = parentMeta,
                                albumFallback = album.cueFile.name.substringBeforeLast('.'),
                            )

                        for (vt in album.tracks) {
                            val vtTrack = vt.toAudioTrack(parentTrack, cuePath = album.cueFile.path)
                            if (isMatch(cleanQuery, vt.title, vt.performer, vtTrack.album, parentTrack.title)) {
                                matchedTracks[vtTrack.id] = vtTrack
                            }
                        }
                    }
                }
            }

            for (meta in metaEntities) {
                val metaKey = "${meta.serverId}:${meta.remotePath}"
                if (!matchedTracks.containsKey(metaKey) && isMatch(cleanQuery, meta.title, meta.artist, meta.album, meta.remotePath)) {
                    val fileName = meta.remotePath.substringAfterLast('/')
                    val format = (RemoteFileType.fromFileName(fileName) as? RemoteFileType.Audio)?.format ?: AudioFormat.MP3
                    matchedTracks[metaKey] =
                        createAudioTrack(
                            serverId = meta.serverId,
                            remotePath = meta.remotePath,
                            fileName = fileName,
                            fileSize = 0L,
                            format = format,
                            metadata = meta,
                        )
                }
            }

            LibrarySearchResult(
                matchedDirectories = matchedDirs.values.toList(),
                matchedTracks = matchedTracks.values.toList(),
            )
        }
    }
}
