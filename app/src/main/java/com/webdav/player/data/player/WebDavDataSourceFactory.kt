package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.WebDavServer
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
class WebDavDataSourceFactory(
    val webDavClient: OkHttpWebDavClient = OkHttpWebDavClient(),
) : DataSource.Factory {
    @Volatile
    private var currentServer: WebDavServer? = null

    @Volatile
    private var currentFactory: DataSource.Factory? = null

    fun setServer(server: WebDavServer) {
        synchronized(this) {
            if (currentServer != server) {
                currentServer = server
                val client = webDavClient.buildStreamingClientForServer(server)
                currentFactory =
                    OkHttpDataSource
                        .Factory(client)
                        .setUserAgent("WebDavPlayer/1.0")
            }
        }
    }

    fun getCurrentServer(): WebDavServer? = currentServer

    override fun createDataSource(): DataSource {
        val factory =
            currentFactory ?: synchronized(this) {
                currentFactory ?: run {
                    val fallbackClient =
                        webDavClient.buildStreamingClientForServer(
                            WebDavServer(name = "Default", url = "http://localhost"),
                        )
                    val defaultFactory =
                        OkHttpDataSource
                            .Factory(fallbackClient)
                            .setUserAgent("WebDavPlayer/1.0")
                    currentFactory = defaultFactory
                    defaultFactory
                }
            }
        return factory.createDataSource()
    }
}
