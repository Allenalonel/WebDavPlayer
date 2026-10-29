package com.webdav.player.data.local

import com.webdav.player.domain.model.Playlist

data class PlaylistWithTrackCount(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val trackCount: Int,
) {
    fun toDomain(): Playlist =
        Playlist(
            id = id,
            name = name,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs,
            trackCount = trackCount,
        )
}
