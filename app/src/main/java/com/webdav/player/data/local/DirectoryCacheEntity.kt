package com.webdav.player.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "directory_cache",
    primaryKeys = ["serverId", "path"],
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["serverId", "path"])
    ]
)
data class DirectoryCacheEntity(
    val serverId: Long,
    val path: String,
    val dataJson: String,
    val lastUpdatedMs: Long
)
