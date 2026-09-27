package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.WebDavServer

@Deprecated("Use WebDavMediaSourceAdapter instead for encapsulated streaming concerns.")
@OptIn(UnstableApi::class)
class WebDavDataSourceFactory(
    val webDavClient: OkHttpWebDavClient = OkHttpWebDavClient(),
) : DataSource.Factory {
    @Volatile
    private var currentServer: WebDavServer? = null

    fun setServer(server: WebDavServer) {
        currentServer = server
    }

    fun getCurrentServer(): WebDavServer? = currentServer

    override fun createDataSource(): DataSource {
        val client =
            currentServer?.let { webDavClient.buildStreamingClientForServer(it) }
                ?: webDavClient.buildStreamingClientForServer(
                    WebDavServer(name = "Default", url = "http://localhost"),
                )
        return OkHttpDataSource
            .Factory(client)
            .setUserAgent("WebDavPlayer/1.0")
            .createDataSource()
    }
}
