package com.webdav.player.domain.repository

import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun getAllServers(): Flow<List<WebDavServer>>
    fun getActiveServer(): Flow<WebDavServer?>
    suspend fun getServerById(id: Long): WebDavServer?
    suspend fun saveServer(server: WebDavServer): Long
    suspend fun deleteServer(id: Long)
    suspend fun setActiveServer(id: Long)
}
