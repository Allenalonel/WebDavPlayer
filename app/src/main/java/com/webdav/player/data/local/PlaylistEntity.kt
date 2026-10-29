package com.webdav.player.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webdav.player.domain.model.Playlist

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    fun toDomain(trackCount: Int = 0): Playlist =
        Playlist(
            id = id,
            name = name,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            trackCount = trackCount,
        )

    companion object {
        fun fromDomain(domain: Playlist): PlaylistEntity =
            PlaylistEntity(
                id = domain.id,
                name = domain.name,
                createdAtMs = domain.createdAtMs,
                updatedAtMs = domain.updatedAtMs,
            )
    }
}
