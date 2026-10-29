package com.webdav.player.data.repository

import com.webdav.player.data.local.PlaylistDao
import com.webdav.player.data.local.PlaylistEntity
import com.webdav.player.data.local.PlaylistTrackEntity
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : PlaylistRepository {
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

    override fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<PlaylistTrackEntry>> =
        playlistDao.getTracksForPlaylistFlow(playlistId).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntry> =
        playlistDao.getTracksForPlaylist(playlistId).map { it.toDomain() }

    override suspend fun addTrackToPlaylist(
        playlistId: Long,
        track: AudioTrack,
        cuePath: String?,
    ): Long {
        val currentMaxSort = playlistDao.getMaxSortOrder(playlistId) ?: -1
        val newSortOrder = currentMaxSort + 1
        val entry =
            PlaylistTrackEntry.fromAudioTrack(
                playlistId = playlistId,
                track = track,
                sortOrder = newSortOrder,
                cuePath = cuePath,
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
        val currentMaxSort = playlistDao.getMaxSortOrder(playlistId) ?: -1
        var nextSort = currentMaxSort + 1
        val entities =
            tracks.map { track ->
                val entry =
                    PlaylistTrackEntry.fromAudioTrack(
                        playlistId = playlistId,
                        track = track,
                        sortOrder = nextSort++,
                    )
                PlaylistTrackEntity.fromDomain(entry)
            }
        val insertedIds = playlistDao.insertTracks(entities)
        playlistDao.updatePlaylistTimestamp(playlistId, timeProvider())
        return insertedIds
    }

    override suspend fun removeTrackFromPlaylist(
        playlistId: Long,
        trackEntryId: Long,
    ) {
        playlistDao.deleteTrackById(trackEntryId)
        playlistDao.updatePlaylistTimestamp(playlistId, timeProvider())
    }

    override suspend fun removeTrackFromPlaylist(trackEntryId: Long) {
        val track = playlistDao.getTrackById(trackEntryId)
        playlistDao.deleteTrackById(trackEntryId)
        if (track != null) {
            playlistDao.updatePlaylistTimestamp(track.playlistId, timeProvider())
        }
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

    override suspend fun updateTrackOrder(
        playlistId: Long,
        trackEntries: List<PlaylistTrackEntry>,
    ) {
        val updatedEntities =
            trackEntries.mapIndexed { index, entry ->
                PlaylistTrackEntity.fromDomain(entry.copy(sortOrder = index))
            }
        playlistDao.updateTracks(updatedEntities)
        playlistDao.updatePlaylistTimestamp(playlistId, timeProvider())
    }
}
