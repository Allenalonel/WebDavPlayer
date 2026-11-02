package com.webdav.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long): Int

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistByIdFlow(playlistId: Long): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT p.id, p.name, p.createdAtMs, p.updatedAtMs, COUNT(t.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks t ON p.id = t.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAtMs DESC
        """,
    )
    fun getAllPlaylistsWithTrackCountFlow(): Flow<List<PlaylistWithTrackCount>>

    @Query(
        """
        SELECT p.id, p.name, p.createdAtMs, p.updatedAtMs, COUNT(t.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks t ON p.id = t.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAtMs DESC
        """,
    )
    suspend fun getAllPlaylistsWithTrackCount(): List<PlaylistWithTrackCount>

    @Query(
        """
        SELECT p.id, p.name, p.createdAtMs, p.updatedAtMs, COUNT(t.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks t ON p.id = t.playlistId
        WHERE p.id = :playlistId
        GROUP BY p.id
        """,
    )
    fun getPlaylistWithTrackCountFlow(playlistId: Long): Flow<PlaylistWithTrackCount?>

    @Query(
        """
        SELECT p.id, p.name, p.createdAtMs, p.updatedAtMs, COUNT(t.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks t ON p.id = t.playlistId
        WHERE p.id = :playlistId
        GROUP BY p.id
        """,
    )
    suspend fun getPlaylistWithTrackCount(playlistId: Long): PlaylistWithTrackCount?

    @Query("SELECT * FROM playlists WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getPlaylistByName(name: String): PlaylistEntity?

    @Query("UPDATE playlists SET name = :newName, updatedAtMs = :updatedAtMs WHERE id = :playlistId")
    suspend fun renamePlaylist(
        playlistId: Long,
        newName: String,
        updatedAtMs: Long,
    ): Int

    @Query("UPDATE playlists SET updatedAtMs = :updatedAtMs WHERE id = :playlistId")
    suspend fun updatePlaylistTimestamp(
        playlistId: Long,
        updatedAtMs: Long,
    ): Int

    // Playlist Tracks Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>): List<Long>

    @Update
    suspend fun updateTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE id = :trackEntryId")
    suspend fun deleteTrackById(trackEntryId: Long): Int

    @Query("DELETE FROM playlist_tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>): Int

    @Query("UPDATE playlist_tracks SET coverThumbnailPath = :coverThumbnailPath WHERE id = :trackEntryId")
    suspend fun updateTrackCover(
        trackEntryId: Long,
        coverThumbnailPath: String?,
    ): Int

    @Query(
        "UPDATE playlist_tracks SET isInactive = :isInactive, inactiveReason = :reason WHERE serverId = :serverId AND remotePath = :remotePath",
    )
    suspend fun updateTrackInactiveStatus(
        serverId: Long,
        remotePath: String,
        isInactive: Boolean,
        reason: String?,
    ): Int

    @Query("SELECT DISTINCT serverId FROM playlist_tracks WHERE isInactive = 1 AND inactiveReason = :reason")
    suspend fun getDistinctServerIdsForInactiveReason(reason: String): List<Long>

    @Query("UPDATE playlist_tracks SET isInactive = 0, inactiveReason = NULL WHERE serverId = :serverId AND inactiveReason = :reason")
    suspend fun clearServerUnreachableStatus(
        serverId: Long,
        reason: String = "SERVER_UNREACHABLE",
    ): Int

    @Query("SELECT * FROM playlist_tracks WHERE id = :trackEntryId LIMIT 1")
    suspend fun getTrackById(trackEntryId: Long): PlaylistTrackEntity?

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksByPlaylistId(playlistId: Long): Int

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT MAX(sortOrder) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxSortOrder(playlistId: Long): Int?

    @Query(
        """
        SELECT * FROM playlist_tracks 
        WHERE playlistId = :playlistId 
          AND serverId = :serverId 
          AND remotePath = :remotePath 
          AND ((virtualTrackId IS NULL AND :virtualTrackId IS NULL) OR virtualTrackId = :virtualTrackId)
        LIMIT 1
        """,
    )
    suspend fun findTrackInPlaylist(
        playlistId: Long,
        serverId: Long,
        remotePath: String,
        virtualTrackId: String?,
    ): PlaylistTrackEntity?
}
