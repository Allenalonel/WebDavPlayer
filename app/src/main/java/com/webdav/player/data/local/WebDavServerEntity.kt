package com.webdav.player.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.webdav.player.domain.model.WebDavServer

@Entity(tableName = "webdav_servers")
data class WebDavServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val url: String,
    val port: Int,
    val pathPrefix: String,
    val username: String,
    val password: String,
    val allowSelfSigned: Boolean,
    val isDefault: Boolean
) {
    fun toDomain(): WebDavServer = WebDavServer(
        id = id,
        name = name,
        url = url,
        port = port,
        pathPrefix = pathPrefix,
        username = username,
        password = password,
        allowSelfSigned = allowSelfSigned,
        isDefault = isDefault
    )

    companion object {
        fun fromDomain(server: WebDavServer): WebDavServerEntity = WebDavServerEntity(
            id = server.id,
            name = server.name,
            url = server.url,
            port = server.port,
            pathPrefix = server.pathPrefix,
            username = server.username,
            password = server.password,
            allowSelfSigned = server.allowSelfSigned,
            isDefault = server.isDefault
        )
    }
}
