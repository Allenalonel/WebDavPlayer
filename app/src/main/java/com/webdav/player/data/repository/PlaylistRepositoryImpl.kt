package com.webdav.player.data.repository

import com.webdav.player.data.local.PlaylistDao
import com.webdav.player.data.local.PlaylistEntity
import com.webdav.player.data.local.PlaylistTrackEntity
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.PlaylistRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackKey
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val serverRepository: ServerRepository? = null,
    private val trackMetadataRepository: TrackMetadataRepository? = null,
    private val webDavClient: WebDavClient? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PlaylistRepository {
    private val _inactiveTracksFlow = MutableStateFlow<Map<TrackKey, InactiveReason>>(emptyMap())
    private val inFlightCoverHealing = java.util.concurrent.ConcurrentHashMap<Long, Job>()
    private val isSelfHealingInProgress =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    private fun triggerBackgroundCoverHealing(
        trackEntryId: Long,
        serverId: Long,
        remotePath: String,
    ) {
        if (trackMetadataRepository == null || serverRepository == null) return
        val activeJob = inFlightCoverHealing[trackEntryId]
        if (activeJob != null && activeJob.isActive) return

        val job =
            coroutineScope.launch {
                try {
                    val cached = trackMetadataRepository.getCachedMetadata(serverId, remotePath)
                    if (cached?.coverThumbnailPath != null) {
                        playlistDao.updateTrackCover(trackEntryId, cached.coverThumbnailPath)
                        return@launch
                    }
                    val server = serverRepository.getServerById(serverId)
                    if (server != null && server.url.isNotBlank()) {
                        val fileName = remotePath.substringAfterLast('/').ifBlank { "track" }
                        val file = RemoteFile(name = fileName, path = remotePath)
                        val resolved = trackMetadataRepository.resolveSingleTrackMetadata(server, file)
                        if (resolved.coverThumbnailPath != null) {
                            playlistDao.updateTrackCover(trackEntryId, resolved.coverThumbnailPath)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore transient errors during background self-healing
                } finally {
                    inFlightCoverHealing.remove(trackEntryId)
                }
            }
        inFlightCoverHealing[trackEntryId] = job
    }

    override fun getAllPlaylistsFlow(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylistsWithTrackCountFlow().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getAllPlaylists(): List<Playlist> = playlistDao.getAllPlaylistsWithTrackCount().map { it.toDomain() }

    override fun getPlaylistFlow(playlistId: Long): Flow<Playlist?> =
        playlistDao.getPlaylistWithTrackCountFlow(playlistId).map { it?.toDomain() }

    override suspend fun getPlaylist(playlistId: Long): Playlist? = playlistDao.getPlaylistWithTrackCount(playlistId)?.toDomain()

    override suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "歌单名称不能为空" }
        val existing = playlistDao.getPlaylistByName(trimmed)
        require(existing == null) { "歌单 “$trimmed” 已存在" }

        val now = timeProvider()
        val entity =
            PlaylistEntity(
                name = trimmed,
                createdAtMs = now,
                updatedAtMs = now,
            )
        return playlistDao.insertPlaylist(entity)
    }

    override suspend fun renamePlaylist(
        playlistId: Long,
        newName: String,
    ) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "歌单名称不能为空" }
        val existing = playlistDao.getPlaylistByName(trimmed)
        if (existing != null && existing.id != playlistId) {
            throw IllegalArgumentException("歌单 “$trimmed” 已存在")
        }

        val now = timeProvider()
        playlistDao.renamePlaylist(playlistId, trimmed, now)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deleteTracksByPlaylistId(playlistId)
        playlistDao.deletePlaylistById(playlistId)
    }

    override suspend fun isPlaylistNameExists(
        name: String,
        excludePlaylistId: Long?,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val existing = playlistDao.getPlaylistByName(trimmed)
        return existing != null && (excludePlaylistId == null || existing.id != excludePlaylistId)
    }

    override fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<PlaylistTrackEntry>> {
        val serversFlow: Flow<List<WebDavServer>?> = serverRepository?.getAllServers() ?: flowOf(null)

        return combine(
            playlistDao.getTracksForPlaylistFlow(playlistId),
            _inactiveTracksFlow,
            serversFlow,
        ) { entities, inactiveMap, servers ->
            val seenKeys = mutableSetOf<String>()
            val duplicateIds = mutableListOf<Long>()
            val uniqueEntities = mutableListOf<PlaylistTrackEntity>()
            for (entity in entities) {
                val key = "${entity.serverId}:${entity.remotePath}:${entity.virtualTrackId ?: ""}"
                if (seenKeys.add(key)) {
                    uniqueEntities.add(entity)
                } else {
                    duplicateIds.add(entity.id)
                }
            }
            if (duplicateIds.isNotEmpty()) {
                coroutineScope.launch {
                    playlistDao.deleteTracksByIds(duplicateIds)
                }
            }
            val serverMap = servers?.associateBy { it.id }

            val reconciled =
                uniqueEntities.map { entity ->
                    val domain = entity.toDomain()
                    val manualReason = inactiveMap[TrackKey(domain.serverId, domain.remotePath)] ?: domain.inactiveReason
                    val server = serverMap?.get(domain.serverId)
                    val isServerUnreachable = servers != null && (server == null || server.url.isBlank())

                    when {
                        manualReason != null || domain.isInactive -> {
                            domain.copy(isInactive = true, inactiveReason = manualReason)
                        }

                        isServerUnreachable -> {
                            domain.copy(isInactive = true, inactiveReason = InactiveReason.SERVER_UNREACHABLE)
                        }

                        else -> {
                            domain
                        }
                    }
                }

            if (trackMetadataRepository != null) {
                reconciled.map { track ->
                    if (track.coverThumbnailPath.isNullOrBlank()) {
                        val cachedMeta = trackMetadataRepository.getCachedMetadata(track.serverId, track.remotePath)
                        if (cachedMeta?.coverThumbnailPath != null) {
                            coroutineScope.launch {
                                playlistDao.updateTrackCover(track.id, cachedMeta.coverThumbnailPath)
                            }
                            track.copy(coverThumbnailPath = cachedMeta.coverThumbnailPath)
                        } else {
                            if (!track.isInactive) {
                                triggerBackgroundCoverHealing(track.id, track.serverId, track.remotePath)
                            }
                            track
                        }
                    } else {
                        track
                    }
                }
            } else {
                reconciled
            }
        }
    }

    override suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntry> {
        val entities = playlistDao.getTracksForPlaylist(playlistId)
        val seenKeys = mutableSetOf<String>()
        val duplicateIds = mutableListOf<Long>()
        val uniqueEntities = mutableListOf<PlaylistTrackEntity>()
        for (entity in entities) {
            val key = "${entity.serverId}:${entity.remotePath}:${entity.virtualTrackId ?: ""}"
            if (seenKeys.add(key)) {
                uniqueEntities.add(entity)
            } else {
                duplicateIds.add(entity.id)
            }
        }
        if (duplicateIds.isNotEmpty()) {
            coroutineScope.launch {
                playlistDao.deleteTracksByIds(duplicateIds)
            }
        }
        val inactiveMap = _inactiveTracksFlow.value
        val servers = serverRepository?.getAllServers()?.firstOrNull()
        val serverMap = servers?.associateBy { it.id }

        val reconciled =
            uniqueEntities.map { entity ->
                val domain = entity.toDomain()
                val manualReason = inactiveMap[TrackKey(domain.serverId, domain.remotePath)] ?: domain.inactiveReason
                val server = serverMap?.get(domain.serverId)
                val isServerUnreachable = servers != null && (server == null || server.url.isBlank())

                when {
                    manualReason != null || domain.isInactive -> {
                        domain.copy(isInactive = true, inactiveReason = manualReason)
                    }

                    isServerUnreachable -> {
                        domain.copy(isInactive = true, inactiveReason = InactiveReason.SERVER_UNREACHABLE)
                    }

                    else -> {
                        domain
                    }
                }
            }

        return if (trackMetadataRepository != null) {
            reconciled.map { track ->
                if (track.coverThumbnailPath.isNullOrBlank()) {
                    val cachedMeta = trackMetadataRepository.getCachedMetadata(track.serverId, track.remotePath)
                    if (cachedMeta?.coverThumbnailPath != null) {
                        coroutineScope.launch {
                            playlistDao.updateTrackCover(track.id, cachedMeta.coverThumbnailPath)
                        }
                        track.copy(coverThumbnailPath = cachedMeta.coverThumbnailPath)
                    } else {
                        if (!track.isInactive) {
                            triggerBackgroundCoverHealing(track.id, track.serverId, track.remotePath)
                        }
                        track
                    }
                } else {
                    track
                }
            }
        } else {
            reconciled
        }
    }

    override suspend fun addTrackToPlaylist(
        playlistId: Long,
        track: AudioTrack,
        cuePath: String?,
    ): Long {
        val virtualTrackId = if (track.isVirtualTrack) track.id else null
        val existing =
            playlistDao.findTrackInPlaylist(
                playlistId = playlistId,
                serverId = track.serverId,
                remotePath = track.remotePath,
                virtualTrackId = virtualTrackId,
            )
        if (existing != null) {
            return -1L
        }
        val currentMaxSort = playlistDao.getMaxSortOrder(playlistId) ?: -1
        val newSortOrder = currentMaxSort + 1
        val entry =
            PlaylistTrackEntry.fromAudioTrack(
                playlistId = playlistId,
                track = track,
                sortOrder = newSortOrder,
                cuePath = cuePath ?: track.cuePath,
            )
        val insertedId = playlistDao.insertTrack(PlaylistTrackEntity.fromDomain(entry))
        playlistDao.updatePlaylistTimestamp(playlistId, timeProvider())
        return insertedId
    }

    override suspend fun addTracksToPlaylist(
        playlistId: Long,
        tracks: List<AudioTrack>,
    ): List<Long> {
        if (tracks.isEmpty()) return emptyList()
        val existing = playlistDao.getTracksForPlaylist(playlistId)
        val seenKeys = existing.map { "${it.serverId}:${it.remotePath}:${it.virtualTrackId ?: ""}" }.toMutableSet()
        val distinctNewTracks =
            tracks.filter { track ->
                val virtualTrackId = if (track.isVirtualTrack) track.id else null
                seenKeys.add("${track.serverId}:${track.remotePath}:${virtualTrackId ?: ""}")
            }
        if (distinctNewTracks.isEmpty()) return emptyList()

        val startSort = (playlistDao.getMaxSortOrder(playlistId) ?: -1) + 1
        val entities =
            distinctNewTracks.mapIndexed { index, track ->
                val entry =
                    PlaylistTrackEntry.fromAudioTrack(
                        playlistId = playlistId,
                        track = track,
                        sortOrder = startSort + index,
                        cuePath = track.cuePath,
                    )
                PlaylistTrackEntity.fromDomain(entry)
            }
        val insertedIds = playlistDao.insertTracks(entities)
        playlistDao.updatePlaylistTimestamp(playlistId, timeProvider())
        return insertedIds
    }

    override suspend fun removeTrackFromPlaylist(trackEntryId: Long) {
        val track = playlistDao.getTrackById(trackEntryId)
        playlistDao.deleteTrackById(trackEntryId)
        if (track != null) {
            playlistDao.updatePlaylistTimestamp(track.playlistId, timeProvider())
        }
    }

    override suspend fun removeTracksFromPlaylist(
        playlistId: Long,
        trackEntryIds: List<Long>,
    ) {
        if (trackEntryIds.isEmpty()) return
        playlistDao.deleteTracksByIds(trackEntryIds)
        playlistDao.updatePlaylistTimestamp(playlistId, timeProvider())
    }

    override suspend fun updateTrackCover(
        trackEntryId: Long,
        coverThumbnailPath: String?,
    ) {
        playlistDao.updateTrackCover(trackEntryId, coverThumbnailPath)
    }

    override suspend fun updateTrackOrders(
        playlistId: Long,
        orderedTrackIds: List<Long>,
    ) {
        val currentTracks = playlistDao.getTracksForPlaylist(playlistId)
        val trackMap = currentTracks.associateBy { it.id }
        val updatedEntities =
            orderedTrackIds.mapIndexedNotNull { index, id ->
                trackMap[id]?.copy(sortOrder = index)
            }
        playlistDao.updateTracks(updatedEntities)
        playlistDao.updatePlaylistTimestamp(playlistId, timeProvider())
    }

    override fun markTrackInactive(
        serverId: Long,
        remotePath: String,
        reason: InactiveReason,
    ) {
        _inactiveTracksFlow.update { current ->
            current + (TrackKey(serverId, remotePath) to reason)
        }
        coroutineScope.launch {
            playlistDao.updateTrackInactiveStatus(
                serverId = serverId,
                remotePath = remotePath,
                isInactive = true,
                reason = reason.name,
            )
        }
    }

    override fun getInactiveTracksFlow(): Flow<Map<TrackKey, InactiveReason>> = _inactiveTracksFlow.asStateFlow()

    override suspend fun triggerReachabilitySelfHealing() {
        if (webDavClient == null || serverRepository == null) return
        if (!isSelfHealingInProgress.compareAndSet(false, true)) return
        try {
            val serverIds = playlistDao.getDistinctServerIdsForInactiveReason(InactiveReason.SERVER_UNREACHABLE.name)
            if (serverIds.isEmpty()) return

            coroutineScope {
                serverIds
                    .map { serverId ->
                        async {
                            val server = serverRepository.getServerById(serverId)
                            if (server != null && server.url.isNotBlank()) {
                                val result =
                                    try {
                                        webDavClient.testConnection(server)
                                    } catch (_: Throwable) {
                                        ConnectionResult.Failure("Reachability probe failed")
                                    }
                                if (result is ConnectionResult.Success) {
                                    playlistDao.clearServerUnreachableStatus(serverId, InactiveReason.SERVER_UNREACHABLE.name)
                                    _inactiveTracksFlow.update { current ->
                                        current.filterNot { it.key.serverId == serverId && it.value == InactiveReason.SERVER_UNREACHABLE }
                                    }
                                }
                            }
                        }
                    }.awaitAll()
            }
        } catch (_: Throwable) {
            // Ignore transient errors during reachability probe
        } finally {
            isSelfHealingInProgress.set(false)
        }
    }
}
