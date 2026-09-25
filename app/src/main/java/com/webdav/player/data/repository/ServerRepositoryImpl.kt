package com.webdav.player.data.repository

import com.webdav.player.data.local.WebDavServerDao
import com.webdav.player.data.local.WebDavServerEntity
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepositoryImpl(
    private val serverDao: WebDavServerDao
) : ServerRepository {

    override fun getAllServers(): Flow<List<WebDavServer>> {
        return serverDao.getAllServersFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getActiveServer(): Flow<WebDavServer?> {
        return serverDao.getActiveServerFlow().map { it?.toDomain() }
    }

    override suspend fun getServerById(id: Long): WebDavServer? {
        return serverDao.getServerById(id)?.toDomain()
    }

    override suspend fun saveServer(server: WebDavServer): Long {
        val totalCount = serverDao.getServerCount()
        // If this is the very first server or explicitly marked as default
        val shouldBeDefault = server.isDefault || totalCount == 0

        val entity = WebDavServerEntity.fromDomain(server.copy(isDefault = shouldBeDefault))

        return if (server.id == 0L) {
            val newId = serverDao.insertServer(entity)
            if (shouldBeDefault) {
                serverDao.setActiveServer(newId)
            }
            newId
        } else {
            if (server.isDefault) {
                serverDao.setActiveServer(server.id)
            }
            serverDao.updateServer(entity)
            server.id
        }
    }

    override suspend fun deleteServer(id: Long) {
        val serverToDelete = serverDao.getServerById(id)
        serverDao.deleteServerById(id)

        if (serverToDelete?.isDefault == true) {
            // Pick any remaining server to be active if available
            // Wait, if no server is left, active server simply becomes null
        }
    }

    override suspend fun setActiveServer(id: Long) {
        serverDao.setActiveServer(id)
    }
}
