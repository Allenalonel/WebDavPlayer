package com.webdav.player.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServerRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ServerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ServerRepositoryImpl(database.webDavServerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initially_getAllServers_isEmpty_andActiveServer_isNull() = runTest {
        val servers = repository.getAllServers().first()
        val active = repository.getActiveServer().first()
        assertTrue(servers.isEmpty())
        assertNull(active)
    }

    @Test
    fun saveServer_insertsNewServer_andRetrievesById() = runTest {
        val server = WebDavServer(
            name = "NAS",
            url = "http://192.168.1.100",
            port = 5005,
            pathPrefix = "/dav",
            username = "admin",
            password = "pwd",
            allowSelfSigned = true
        )

        val id = repository.saveServer(server)
        assertTrue(id > 0)

        val saved = repository.getServerById(id)
        assertNotNull(saved)
        assertEquals("NAS", saved?.name)
        assertEquals("http://192.168.1.100", saved?.url)
        assertEquals(5005, saved?.port)
        assertEquals("/dav", saved?.pathPrefix)
        assertEquals("admin", saved?.username)
        assertEquals("pwd", saved?.password)
        assertTrue(saved?.allowSelfSigned == true)
        // If it was the first server, it should automatically be active
        assertTrue(saved?.isDefault == true)
    }

    @Test
    fun setActiveServer_switchesActiveServerCorrectly() = runTest {
        val id1 = repository.saveServer(WebDavServer(name = "Server 1", url = "http://srv1"))
        val id2 = repository.saveServer(WebDavServer(name = "Server 2", url = "http://srv2"))

        // Set server 2 as active
        repository.setActiveServer(id2)

        val active = repository.getActiveServer().first()
        assertNotNull(active)
        assertEquals(id2, active?.id)
        assertEquals("Server 2", active?.name)

        val s1 = repository.getServerById(id1)
        val s2 = repository.getServerById(id2)
        assertFalse(s1?.isDefault == true)
        assertTrue(s2?.isDefault == true)
    }

    @Test
    fun updateServer_modifiesExistingRecord() = runTest {
        val id = repository.saveServer(WebDavServer(name = "Original", url = "http://original"))
        repository.saveServer(WebDavServer(id = id, name = "Updated", url = "http://updated", port = 8080))

        val updated = repository.getServerById(id)
        assertEquals("Updated", updated?.name)
        assertEquals("http://updated", updated?.url)
        assertEquals(8080, updated?.port)
    }

    @Test
    fun deleteServer_removesServerFromDatabase() = runTest {
        val id = repository.saveServer(WebDavServer(name = "To Delete", url = "http://delete-me"))
        assertEquals(1, repository.getAllServers().first().size)

        repository.deleteServer(id)

        assertTrue(repository.getAllServers().first().isEmpty())
        assertNull(repository.getServerById(id))
    }
}
