package com.webdav.player.domain.model

data class Playlist(
    val id: Long = 0L,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val trackCount: Int = 0,
)
