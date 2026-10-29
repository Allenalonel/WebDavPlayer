package com.webdav.player.data.repository

import com.webdav.player.data.cue.CueAssociationHelper
import com.webdav.player.data.cue.CueParser
import com.webdav.player.data.cue.CueTextCache
import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.LibrarySearchResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.repository.LibrarySearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class LibrarySearchRepositoryImpl(
    private val directoryCacheDao: DirectoryCacheDao,
    private val trackMetadataDao: TrackMetadataDao,
    private val cueTextProvider: ((serverId: Long, cuePath: String) -> String?) = { serverId, cuePath ->
        CueTextCache.get(serverId, cuePath)
    },
) : LibrarySearchRepository {
    override fun search(
        query: String,
        serverId: Long?,
    ): Flow<LibrarySearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return flowOf(LibrarySearchResult())
        }

        val dirFlow =
            if (serverId != null) {
                directoryCacheDao.getAllByServerIdFlow(serverId)
            } else {
                directoryCacheDao.getAllFlow()
            }

        val metaFlow =
            if (serverId != null) {
                trackMetadataDao.getAllMetadataForServerFlow(serverId)
            } else {
                trackMetadataDao.getAllMetadataFlow()
            }

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
                    matchedDirs["${dirEntity.serverId}:${remoteDir.path}"] = remoteDir
                }

                // Check subdirectories
                for (sub in remoteDir.subDirectories) {
                    if (sub.name.contains(cleanQuery, ignoreCase = true) ||
                        sub.path.contains(cleanQuery, ignoreCase = true)
                    ) {
                        matchedDirs["${dirEntity.serverId}:${sub.path}"] = sub
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
                if (cueFiles.isNotEmpty() && audioFiles.isNotEmpty()) {
                    for (cueFile in cueFiles) {
                        val cueText = cueTextProvider(dirEntity.serverId, cueFile.path) ?: continue
                        if (cueText.isBlank()) continue

                        val referenced = CueParser.extractReferencedFiles(cueText)
                        val matchedAudioFile =
                            CueAssociationHelper.findMatchingAudioFile(
                                cueFile = cueFile,
                                audioFiles = audioFiles,
                                referencedFileNames = referenced,
                                totalCueFilesCount = cueFiles.size,
                            ) ?: continue

                        val parsedVirtualTracks =
                            CueParser.parse(
                                content = cueText,
                                parentAudioPath = matchedAudioFile.path,
                                allowMissingFileTag = true,
                            )
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
                                        ?: cueFile.name.substringBeforeLast('.'),
                                durationMs = parentMeta?.durationMs ?: 0L,
                                size = matchedAudioFile.size,
                                format = parentFormat,
                                coverThumbnailPath = parentMeta?.coverThumbnailPath,
                            )

                        for (vt in parsedVirtualTracks) {
                            val vtTrack = vt.toAudioTrack(parentTrack)
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
