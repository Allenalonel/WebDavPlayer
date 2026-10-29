package com.webdav.player.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.PlaylistTrackEntry

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "sortOrder"]),
    ],
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val playlistId: Long,
    val serverId: Long,
    val remotePath: String,
    val sortOrder: Int,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val size: Long = 0L,
    val format: String = "MP3",
    val coverThumbnailPath: String? = null,
    val cuePath: String? = null,
    val virtualTrackId: String? = null,
) {
    fun toDomain(): PlaylistTrackEntry =
        PlaylistTrackEntry(
            id = id,
            playlistId = playlistId,
            serverId = serverId,
            remotePath = remotePath,
            sortOrder = sortOrder,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            size = size,
            format =
                AudioFormat.entries.firstOrNull { it.name.equals(format, ignoreCase = true) }
                    ?: AudioFormat.fromExtension(format)
                    ?: AudioFormat.MP3,
            coverThumbnailPath = coverThumbnailPath,
            cuePath = cuePath,
            virtualTrackId = virtualTrackId,
        )

    companion object {
        fun fromDomain(domain: PlaylistTrackEntry): PlaylistTrackEntity =
            PlaylistTrackEntity(
                id = domain.id,
                playlistId = domain.playlistId,
                serverId = domain.serverId,
                remotePath = domain.remotePath,
                sortOrder = domain.sortOrder,
                title = domain.title,
                artist = domain.artist,
                album = domain.album,
                durationMs = domain.durationMs,
                size = domain.size,
                format = domain.format.name,
                coverThumbnailPath = domain.coverThumbnailPath,
                cuePath = domain.cuePath,
                virtualTrackId = domain.virtualTrackId,
            )
    }
}
