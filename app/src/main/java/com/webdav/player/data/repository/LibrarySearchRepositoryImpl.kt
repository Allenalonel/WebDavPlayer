package com.webdav.player.data.repository

import android.content.Context
import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.local.TrackMetadataDao
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
    }

    private val inMemoryHistory = mutableListOf<String>()

    private val prefs by lazy {
        context?.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    override fun getSearchHistory(): List<String> {
        val sharedPrefs = prefs
        if (sharedPrefs != null) {
            val raw = sharedPrefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        return synchronized(inMemoryHistory) {
            inMemoryHistory.toList()
        }
    }

    override fun recordSearchQuery(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return getSearchHistory()
        val current = getSearchHistory().filter { it != trimmed }
        val updated = (listOf(trimmed) + current).take(MAX_HISTORY_SIZE)

        val sharedPrefs = prefs
        if (sharedPrefs != null) {
            val arr = JSONArray(updated)
            sharedPrefs.edit().putString(KEY_HISTORY_JSON, arr.toString()).apply()
        } else {
            synchronized(inMemoryHistory) {
                inMemoryHistory.clear()
                inMemoryHistory.addAll(updated)
            }
        }
        return updated
    }

    override fun removeSearchHistory(query: String): List<String> {
        val updated = getSearchHistory().filter { it != query }
        val sharedPrefs = prefs
        if (sharedPrefs != null) {
            val arr = JSONArray(updated)
            sharedPrefs.edit().putString(KEY_HISTORY_JSON, arr.toString()).apply()
        } else {
            synchronized(inMemoryHistory) {
                inMemoryHistory.clear()
                inMemoryHistory.addAll(updated)
            }
        }
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
                val remoteDir = RemoteDirectoryJsonSerializer.deserialize(dirEntity.dataJson) ?: continue

                // Check cached directory itself
                if (remoteDir.name.contains(cleanQuery, ignoreCase = true) ||
                    remoteDir.path.contains(cleanQuery, ignoreCase = true)
                ) {
                    matchedDirs["${dirEntity.serverId}:${remoteDir.path}"] =
                        remoteDir.copy(serverId = dirEntity.serverId)
                }

                // Check subdirectories
                for (sub in remoteDir.subDirectories) {
                    if (sub.name.contains(cleanQuery, ignoreCase = true) ||
                        sub.path.contains(cleanQuery, ignoreCase = true)
                    ) {
                        matchedDirs["${dirEntity.serverId}:${sub.path}"] =
                            sub.copy(serverId = dirEntity.serverId)
                    }
                }

                val audioFiles = remoteDir.files.filter { it.isAudio }
                val cueFiles = remoteDir.files.filter { it.isCue }

                // Match regular audio tracks
                for (file in audioFiles) {
                    val metaKey = "${dirEntity.serverId}:${file.path}"
                    val meta = metadataMap[metaKey]
                    val format = (file.fileType as? RemoteFileType.Audio)?.format ?: AudioFormat.MP3

                    val track =
                        AudioTrack(
                            id = metaKey,
                            serverId = dirEntity.serverId,
                            remotePath = file.path,
                            title = meta?.title?.takeIf { it.isNotBlank() } ?: file.name,
                            artist = meta?.artist?.takeIf { it.isNotBlank() },
                            album = meta?.album?.takeIf { it.isNotBlank() },
                            durationMs = meta?.durationMs ?: 0L,
                            size = file.size,
                            format = format,
                            coverThumbnailPath = meta?.coverThumbnailPath,
                        )

                    val isMatch =
                        track.title.contains(cleanQuery, ignoreCase = true) ||
                            track.artist?.contains(cleanQuery, ignoreCase = true) == true ||
                            track.album?.contains(cleanQuery, ignoreCase = true) == true ||
                            file.name.contains(cleanQuery, ignoreCase = true) ||
                            file.path.contains(cleanQuery, ignoreCase = true)

                    if (isMatch) {
                        matchedTracks[track.id] = track
                    }
                }

                // Match CUE virtual tracks
                if (cueFiles.isNotEmpty() && audioFiles.isNotEmpty() && cueSheetResolver != null) {
                    val server = WebDavServer(id = dirEntity.serverId, name = "", url = "", port = 0)
                    val parentDurations = audioFiles.associate { it.path to (metadataMap["${dirEntity.serverId}:${it.path}"]?.durationMs ?: 0L) }
                    val albums = cueSheetResolver.resolveDirectoryCueAlbums(server, remoteDir, parentDurations)
                    for (album in albums) {
                        val matchedAudioFile = album.audioFile ?: continue
                        val parsedVirtualTracks = album.tracks
                        if (parsedVirtualTracks.isEmpty()) continue

                        val parentMeta = metadataMap["${dirEntity.serverId}:${matchedAudioFile.path}"]
                        val parentFormat =
                            (matchedAudioFile.fileType as? RemoteFileType.Audio)?.format ?: AudioFormat.FLAC
                        val parentTrack =
                            AudioTrack(
                                id = "${dirEntity.serverId}:${matchedAudioFile.path}",
                                serverId = dirEntity.serverId,
                                remotePath = matchedAudioFile.path,
                                title = parentMeta?.title?.takeIf { it.isNotBlank() } ?: matchedAudioFile.name,
                                artist = parentMeta?.artist?.takeIf { it.isNotBlank() },
                                album =
                                    parentMeta?.album?.takeIf { it.isNotBlank() }
                                        ?: album.cueFile.name.substringBeforeLast('.'),
                                durationMs = parentMeta?.durationMs ?: 0L,
                                size = matchedAudioFile.size,
                                format = parentFormat,
                                coverThumbnailPath = parentMeta?.coverThumbnailPath,
                            )

                        for (vt in parsedVirtualTracks) {
                            val vtTrack = vt.toAudioTrack(parentTrack, cuePath = album.cueFile.path)
                            val vtMatch =
                                vt.title.contains(cleanQuery, ignoreCase = true) ||
                                    vt.performer?.contains(cleanQuery, ignoreCase = true) == true ||
                                    vtTrack.album?.contains(cleanQuery, ignoreCase = true) == true ||
                                    parentTrack.title.contains(cleanQuery, ignoreCase = true)

                            if (vtMatch) {
                                matchedTracks[vtTrack.id] = vtTrack
                            }
                        }
                    }
                }
            }

            // Also check standalone track metadata
            for (meta in metaEntities) {
                val metaKey = "${meta.serverId}:${meta.remotePath}"
                if (!matchedTracks.containsKey(metaKey)) {
                    val isMatch =
                        meta.title?.contains(cleanQuery, ignoreCase = true) == true ||
                            meta.artist?.contains(cleanQuery, ignoreCase = true) == true ||
                            meta.album?.contains(cleanQuery, ignoreCase = true) == true ||
                            meta.remotePath.contains(cleanQuery, ignoreCase = true)

                    if (isMatch) {
                        val fileName = meta.remotePath.substringAfterLast('/')
                        val format =
                            RemoteFileType.fromFileName(fileName).let {
                                (it as? RemoteFileType.Audio)?.format ?: AudioFormat.MP3
                            }
                        val track =
                            AudioTrack(
                                id = metaKey,
                                serverId = meta.serverId,
                                remotePath = meta.remotePath,
                                title = meta.title?.takeIf { it.isNotBlank() } ?: fileName,
                                artist = meta.artist?.takeIf { it.isNotBlank() },
                                album = meta.album?.takeIf { it.isNotBlank() },
                                durationMs = meta.durationMs,
                                size = 0L,
                                format = format,
                                coverThumbnailPath = meta.coverThumbnailPath,
                            )
                        matchedTracks[metaKey] = track
                    }
                }
            }

            LibrarySearchResult(
                matchedDirectories = matchedDirs.values.toList(),
                matchedTracks = matchedTracks.values.toList(),
            )
        }
    }
}
