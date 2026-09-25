package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.WebDavServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(UnstableApi::class)
class WebDavDataSourceFactoryTest {

    @Test
    fun createDataSource_withoutServer_returnsValidDataSource() {
        val client = OkHttpWebDavClient()
        val factory = WebDavDataSourceFactory(client)

        assertNull(factory.getCurrentServer())
        val dataSource = factory.createDataSource()
        assertNotNull(dataSource)
    }

    @Test
    fun setServer_updatesCurrentServer_andCreatesDataSource() {
        val client = OkHttpWebDavClient()
        val factory = WebDavDataSourceFactory(client)

        val server = WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "https://nas.example.com",
            port = 443,
            pathPrefix = "/dav",
            username = "user1",
            password = "pwd",
            allowSelfSigned = true
        )

        factory.setServer(server)

        assertEquals(server, factory.getCurrentServer())
        val dataSource = factory.createDataSource()
        assertNotNull(dataSource)
    }
}
