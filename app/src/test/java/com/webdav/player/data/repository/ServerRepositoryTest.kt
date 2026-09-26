package com.webdav.player.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorageImpl
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServerRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var coverArtStorage: CoverArtStorageImpl
    private lateinit var repository: ServerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coverArtStorage = CoverArtStorageImpl(context)
        repository = ServerRepositoryImpl(database.webDavServerDao(), coverArtStorage)
    }

    @After
    fun tearDown() {
        database.close()
        File(context.cacheDir, "covers").deleteRecursively()
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

    @Test
    fun deleteServer_cascadesRemovalOfCoverArtFromDisk() = runTest {
        val s1 = WebDavServer(name = "Server 1", url = "http://srv1")
        val s2 = WebDavServer(name = "Server 2", url = "http://srv2")
        val id1 = repository.saveServer(s1)
        val id2 = repository.saveServer(s2)

        // Save cover files for both servers
        val dummyBytes = ByteArray(32) { 0x11 }
        coverArtStorage.saveThumbnail(id1, "/track1.mp3", dummyBytes)
        coverArtStorage.saveThumbnail(id2, "/track2.mp3", dummyBytes)

        assertNotNull(coverArtStorage.getThumbnailFile(id1, "/track1.mp3"))
        assertNotNull(coverArtStorage.getThumbnailFile(id2, "/track2.mp3"))

        // Delete server 1
        repository.deleteServer(id1)

        // Server 1 should be gone from DB and its covers physically gone from disk
        assertNull(repository.getServerById(id1))
        assertNull(coverArtStorage.getThumbnailFile(id1, "/track1.mp3"))

        // Server 2 and its covers should still exist
        assertNotNull(repository.getServerById(id2))
        assertNotNull(coverArtStorage.getThumbnailFile(id2, "/track2.mp3"))
    }

    @Test
    fun saveServer_and_deleteServer_invalidatesWebDavClientCache() = runTest {
        val webDavClient = OkHttpWebDavClient()
        val repoWithClient = ServerRepositoryImpl(
            serverDao = database.webDavServerDao(),
            coverArtStorage = coverArtStorage,
            webDavClient = webDavClient
        )

        val server1 = WebDavServer(name = "Cached Server", url = "http://cached-srv", port = 80)
        val id = repoWithClient.saveServer(server1)
        val savedServer = repoWithClient.getServerById(id)!!

        val clientA = webDavClient.buildClientForServer(savedServer)
        assertEquals(1, webDavClient.cachedClientCount)

        // Updating server invalidates cache
        val updatedServer = savedServer.copy(port = 8080)
        repoWithClient.saveServer(updatedServer)
        assertEquals(0, webDavClient.cachedClientCount)

        // Re-cache and delete
        val clientB = webDavClient.buildClientForServer(updatedServer)
        assertEquals(1, webDavClient.cachedClientCount)
        assertNotSame(clientA, clientB)

        repoWithClient.deleteServer(id)
        assertEquals(0, webDavClient.cachedClientCount)
    }
}
