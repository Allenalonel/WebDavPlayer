package com.webdav.player.domain.repository

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class TrackKey(
    val serverId: Long,
    val remotePath: String,
)

interface PlaylistRepository {
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    suspend fun getAllPlaylists(): List<Playlist>

    fun getPlaylistFlow(playlistId: Long): Flow<Playlist?>

    suspend fun getPlaylist(playlistId: Long): Playlist?

    suspend fun createPlaylist(name: String): Long

    suspend fun renamePlaylist(
        playlistId: Long,
        newName: String,
    )

    suspend fun deletePlaylist(playlistId: Long)

    suspend fun isPlaylistNameExists(
        name: String,
        excludePlaylistId: Long? = null,
    ): Boolean

    fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<PlaylistTrackEntry>>

    suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntry>

    suspend fun addTrackToPlaylist(
        playlistId: Long,
        track: AudioTrack,
        cuePath: String? = null,
    ): Long

    suspend fun addTracksToPlaylist(
        playlistId: Long,
        tracks: List<AudioTrack>,
    ): List<Long>

    suspend fun removeTrackFromPlaylist(trackEntryId: Long)

    suspend fun updateTrackCover(
        trackEntryId: Long,
        coverThumbnailPath: String?,
    ) {}

    suspend fun removeTracksFromPlaylist(
        playlistId: Long,
        trackEntryIds: List<Long>,
    )

    suspend fun updateTrackOrders(
        playlistId: Long,
        orderedTrackIds: List<Long>,
    )

    fun markTrackInactive(
        serverId: Long,
        remotePath: String,
        reason: InactiveReason,
    ) {}

    fun getInactiveTracksFlow(): Flow<Map<TrackKey, InactiveReason>> = emptyFlow()

    suspend fun triggerReachabilitySelfHealing() {}
}
