package com.webdav.player.data.repository

import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DirectoryRepositoryTest {

    private lateinit var fakeClient: FakeWebDavClient
    private lateinit var fakeDao: FakeDirectoryCacheDao
    private lateinit var repository: DirectoryRepositoryImpl

    private val testServer = WebDavServer(
        id = 1L,
        name = "Test Server",
        url = "http://localhost",
        port = 8080
    )

    private val secondServer = WebDavServer(
        id = 2L,
        name = "Second Server",
        url = "http://remotehost",
        port = 8080
    )

    @Before
    fun setUp() {
        fakeClient = FakeWebDavClient()
        fakeDao = FakeDirectoryCacheDao()
        repository = DirectoryRepositoryImpl(fakeClient, fakeDao)
    }

    @Test
    fun listDirectory_returnsCachedDirectory_whenCalledTwiceWithoutForceRefresh() = runTest {
        val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)

        val result1 = repository.listDirectory(testServer, "/Music/")
        val result2 = repository.listDirectory(testServer, "/Music/")

        assertTrue(result1 is ListDirectoryResult.Success)
        assertTrue(result2 is ListDirectoryResult.Success)
        assertEquals(1, fakeClient.callCount["/Music/"])
    }

    @Test
    fun listDirectory_queriesClientAgain_whenForceRefreshIsTrue() = runTest {
        val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)

        repository.listDirectory(testServer, "/Music/")
        val result2 = repository.listDirectory(testServer, "/Music/", forceRefresh = true)

        assertTrue(result2 is ListDirectoryResult.Success)
        assertEquals(2, fakeClient.callCount["/Music/"])
    }

    @Test
    fun clearCache_invalidatesCachedDirectories() = runTest {
        val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)

        repository.listDirectory(testServer, "/Music/")
        repository.clearCache()
        repository.listDirectory(testServer, "/Music/")

        assertEquals(2, fakeClient.callCount["/Music/"])
    }

    @Test
    fun listDirectory_doesNotCache_whenResultIsFailure() = runTest {
        fakeClient.results["/Music/"] = ListDirectoryResult.Failure("Network error")

        val result1 = repository.listDirectory(testServer, "/Music/")
        assertTrue(result1 is ListDirectoryResult.Failure)

        fakeClient.results["/Music/"] = ListDirectoryResult.Success(RemoteDirectory(path = "/Music/", name = "Music"))
        val result2 = repository.listDirectory(testServer, "/Music/")
        assertTrue(result2 is ListDirectoryResult.Success)

        assertEquals(2, fakeClient.callCount["/Music/"])
    }

    @Test
    fun getCachedDirectory_restoresFromDatabase_whenMemoryCacheIsEmpty() = runTest {
        val originalDir = RemoteDirectory(
            path = "/Music/",
            name = "Music",
            subDirectories = listOf(RemoteDirectory(path = "/Music/Pop/", name = "Pop")),
            files = listOf(RemoteFile(name = "test.mp3", path = "/Music/test.mp3", size = 12345))
        )
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(originalDir)

        // First list call populates memory and DB
        repository.listDirectory(testServer, "/Music/")
        assertEquals(1, fakeClient.callCount["/Music/"])
        assertEquals(1, fakeDao.cacheMap.size)

        // Clear memory cache only (simulating process recreation or memory eviction)
        repository.clearMemoryCache()

        // getCachedDirectory should now read from DAO
        val cached = repository.getCachedDirectory(testServer, "/Music/")
        assertNotNull(cached)
        assertEquals("/Music/", cached?.path)
        assertEquals("Music", cached?.name)
        assertEquals(1, cached?.subDirectories?.size)
        assertEquals("Pop", cached?.subDirectories?.first()?.name)
        assertEquals(1, cached?.files?.size)
        assertEquals("test.mp3", cached?.files?.first()?.name)
        assertTrue(cached?.files?.first()?.isAudio == true)

        // listDirectory without force refresh should hit L1 (now repopulated) and not call remote
        val result = repository.listDirectory(testServer, "/Music/")
        assertTrue(result is ListDirectoryResult.Success)
        assertEquals(1, fakeClient.callCount["/Music/"])
    }

    @Test
    fun clearCacheForServer_removesOnlyTargetServer() = runTest {
        val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
        val dir2 = RemoteDirectory(path = "/Jazz/", name = "Jazz")

        fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)
        fakeClient.results["/Jazz/"] = ListDirectoryResult.Success(dir2)

        repository.listDirectory(testServer, "/Music/")
        repository.listDirectory(secondServer, "/Jazz/")

        repository.clearCacheForServer(testServer.id)

        // testServer should be gone from DB & memory
        assertNull(repository.getCachedDirectory(testServer, "/Music/"))
        // secondServer should remain intact
        assertNotNull(repository.getCachedDirectory(secondServer, "/Jazz/"))
    }

    @Test
    fun serializerRoundTrip_preservesAllMetadata() {
        val original = RemoteDirectory(
            path = "/FLAC/Artist/",
            name = "Artist",
            subDirectories = listOf(
                RemoteDirectory(path = "/FLAC/Artist/Album1/", name = "Album1")
            ),
            files = listOf(
                RemoteFile(name = "track01.flac", path = "/FLAC/Artist/track01.flac", size = 45000000L, lastModified = "Mon, 25 Sep 2026 12:00:00 GMT", contentType = "audio/flac"),
                RemoteFile(name = "track01.lrc", path = "/FLAC/Artist/track01.lrc", size = 1500L, lastModified = null, contentType = "text/plain")
            )
        )

        val json = RemoteDirectoryJsonSerializer.serialize(original)
        val deserialized = RemoteDirectoryJsonSerializer.deserialize(json)

        assertNotNull(deserialized)
        assertEquals(original.path, deserialized?.path)
        assertEquals(original.name, deserialized?.name)
        assertEquals(1, deserialized?.subDirectories?.size)
        assertEquals("Album1", deserialized?.subDirectories?.first()?.name)
        assertEquals(2, deserialized?.files?.size)

        val audioFile = deserialized?.files?.first { it.name == "track01.flac" }
        assertNotNull(audioFile)
        assertTrue(audioFile?.isAudio == true)
        assertEquals(45000000L, audioFile?.size)

        val lrcFile = deserialized?.files?.first { it.name == "track01.lrc" }
        assertNotNull(lrcFile)
        assertTrue(lrcFile?.isLyrics == true)
    }

    @Test
    fun memoryCache_whenExceedingCapacity_evictsLruAndFallsBackToRoomL2() = runTest {
        // Insert 50 directories (/dir0/ to /dir49/)
        for (i in 0 until 50) {
            val dir = RemoteDirectory(path = "/dir$i/", name = "dir$i")
            fakeClient.results["/dir$i/"] = ListDirectoryResult.Success(dir)
            repository.listDirectory(testServer, "/dir$i/")
        }

        // Clear DAO call count so we measure accesses after initial population
        fakeDao.getCacheCallCount.clear()

        // Initially, all 50 should be in L1 memory cache.
        // Access /dir0/ to make it MRU (most recently used).
        val cached0 = repository.getCachedDirectory(testServer, "/dir0/")
        assertNotNull(cached0)
        assertEquals(0, fakeDao.getCacheCallCount["/dir0/"] ?: 0) // Hit L1, no L2 query

        // Insert 51st directory (/dir50/). Since /dir0/ was refreshed, /dir1/ is the LRU entry.
        val dir50 = RemoteDirectory(path = "/dir50/", name = "dir50")
        fakeClient.results["/dir50/"] = ListDirectoryResult.Success(dir50)
        repository.listDirectory(testServer, "/dir50/")

        // /dir0/ should still be in L1 memory cache
        val cached0Again = repository.getCachedDirectory(testServer, "/dir0/")
        assertNotNull(cached0Again)
        assertEquals(0, fakeDao.getCacheCallCount["/dir0/"] ?: 0) // Still L1 hit

        // /dir1/ was evicted from L1. Querying it should hit L2 (Room DAO) transparently without calling network!
        val clientCallsBefore = fakeClient.callCount["/dir1/"] ?: 0
        val cached1 = repository.getCachedDirectory(testServer, "/dir1/")
        assertNotNull(cached1)
        assertEquals("dir1", cached1?.name)
        // Verify L2 was queried
        assertEquals(1, fakeDao.getCacheCallCount["/dir1/"] ?: 0)
        // Verify client was NOT queried again (instant 0ms without network)
        assertEquals(clientCallsBefore, fakeClient.callCount["/dir1/"] ?: 0)
    }

    @Test
    fun observeDirectory_withCacheMiss_queriesRemote_emitsRemoteResult_andUpdatesCaches() = runTest {
        val dir = RemoteDirectory(path = "/Music/", name = "Music")
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir)

        val emissions = repository.observeDirectory(testServer, "/Music/").toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ListDirectoryResult.Success)
        assertEquals(dir, (emissions[0] as ListDirectoryResult.Success).directory)
        assertEquals(1, fakeClient.callCount["/Music/"])

        // Verify caches populated
        assertNotNull(repository.getCachedDirectory(testServer, "/Music/"))
        assertNotNull(fakeDao.cacheMap["${testServer.id}:/Music/"])
    }

    @Test
    fun observeDirectory_withCacheHit_emitsCachedImmediately_thenEmitsUpdatedDirectoryWhenRemoteDiffers() = runTest {
        val cachedDir = RemoteDirectory(
            path = "/Music/",
            name = "Music",
            files = listOf(RemoteFile(name = "old.mp3", path = "/Music/old.mp3", size = 100))
        )
        val freshDir = RemoteDirectory(
            path = "/Music/",
            name = "Music",
            files = listOf(
                RemoteFile(name = "old.mp3", path = "/Music/old.mp3", size = 100),
                RemoteFile(name = "new.mp3", path = "/Music/new.mp3", size = 200)
            )
        )
        // Prepopulate cache
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
        repository.listDirectory(testServer, "/Music/")

        // Remote now has freshDir
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(freshDir)

        val emissions = repository.observeDirectory(testServer, "/Music/").toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is ListDirectoryResult.Success)
        assertEquals(cachedDir, (emissions[0] as ListDirectoryResult.Success).directory)
        assertTrue(emissions[1] is ListDirectoryResult.Success)
        assertEquals(freshDir, (emissions[1] as ListDirectoryResult.Success).directory)

        // Verify caches updated to freshDir
        assertEquals(freshDir, repository.getCachedDirectory(testServer, "/Music/"))
    }

    @Test
    fun observeDirectory_withCacheHit_whenRemoteIsIdentical_emitsOnlyCachedOnce() = runTest {
        val dir = RemoteDirectory(
            path = "/Music/",
            name = "Music",
            files = listOf(RemoteFile(name = "track.mp3", path = "/Music/track.mp3", size = 100))
        )
        // Prepopulate cache
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir)
        repository.listDirectory(testServer, "/Music/")
        val clientCallsBefore = fakeClient.callCount["/Music/"] ?: 0

        val emissions = repository.observeDirectory(testServer, "/Music/").toList()

        // Remote revalidation took place
        assertEquals(clientCallsBefore + 1, fakeClient.callCount["/Music/"])
        // Only 1 emission since remote matches cache
        assertEquals(1, emissions.size)
        assertEquals(dir, (emissions[0] as ListDirectoryResult.Success).directory)
    }

    @Test
    fun observeDirectory_withCacheHit_whenRemoteFails_emitsCachedAndSuppressesNetworkError() = runTest {
        val cachedDir = RemoteDirectory(path = "/Music/", name = "Music")
        // Prepopulate cache
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
        repository.listDirectory(testServer, "/Music/")

        // Remote fails with network error
        fakeClient.results["/Music/"] = ListDirectoryResult.Failure("Network timeout", 408)

        val emissions = repository.observeDirectory(testServer, "/Music/").toList()

        // Emits cached directory, suppresses transient network error
        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ListDirectoryResult.Success)
        assertEquals(cachedDir, (emissions[0] as ListDirectoryResult.Success).directory)
        // Cache remains intact
        assertEquals(cachedDir, repository.getCachedDirectory(testServer, "/Music/"))
    }

    @Test
    fun observeDirectory_withCacheMiss_whenRemoteFails_emitsFailure() = runTest {
        fakeClient.results["/Music/"] = ListDirectoryResult.Failure("404 Not Found", 404)

        val emissions = repository.observeDirectory(testServer, "/Music/").toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ListDirectoryResult.Failure)
        assertEquals("404 Not Found", (emissions[0] as ListDirectoryResult.Failure).message)
        assertNull(repository.getCachedDirectory(testServer, "/Music/"))
    }

    @Test
    fun observeDirectory_withForceRefresh_bypassesCache_andEmitsRemoteDirectly() = runTest {
        val cachedDir = RemoteDirectory(path = "/Music/", name = "Cached Music")
        val freshDir = RemoteDirectory(path = "/Music/", name = "Fresh Music")

        fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
        repository.listDirectory(testServer, "/Music/")

        fakeClient.results["/Music/"] = ListDirectoryResult.Success(freshDir)

        val emissions = repository.observeDirectory(testServer, "/Music/", forceRefresh = true).toList()

        // Bypasses cache: only 1 emission containing freshDir
        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ListDirectoryResult.Success)
        assertEquals(freshDir, (emissions[0] as ListDirectoryResult.Success).directory)
        assertEquals(freshDir, repository.getCachedDirectory(testServer, "/Music/"))
    }

    @Test
    fun observeDirectory_withForceRefresh_whenRemoteFails_emitsFailure() = runTest {
        val cachedDir = RemoteDirectory(path = "/Music/", name = "Cached Music")
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
        repository.listDirectory(testServer, "/Music/")

        fakeClient.results["/Music/"] = ListDirectoryResult.Failure("Server 500 error", 500)

        val emissions = repository.observeDirectory(testServer, "/Music/", forceRefresh = true).toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is ListDirectoryResult.Failure)
        assertEquals("Server 500 error", (emissions[0] as ListDirectoryResult.Failure).message)
    }

    @Test
    fun observeDirectory_restoresFromDatabaseL2_whenMemoryCacheIsEmpty() = runTest {
        val cachedDir = RemoteDirectory(
            path = "/Music/",
            name = "L2 Music",
            files = listOf(RemoteFile(name = "song.mp3", path = "/Music/song.mp3", size = 100))
        )
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
        repository.listDirectory(testServer, "/Music/")

        // Clear L1 memory cache
        repository.clearMemoryCache()

        val updatedDir = RemoteDirectory(
            path = "/Music/",
            name = "L2 Music",
            files = listOf(
                RemoteFile(name = "song.mp3", path = "/Music/song.mp3", size = 100),
                RemoteFile(name = "song2.mp3", path = "/Music/song2.mp3", size = 200)
            )
        )
        fakeClient.results["/Music/"] = ListDirectoryResult.Success(updatedDir)

        val emissions = repository.observeDirectory(testServer, "/Music/").toList()

        assertEquals(2, emissions.size)
        assertEquals(cachedDir, (emissions[0] as ListDirectoryResult.Success).directory)
        assertEquals(updatedDir, (emissions[1] as ListDirectoryResult.Success).directory)
        // Memory cache should now be repopulated
        assertEquals(updatedDir, repository.getCachedDirectory(testServer, "/Music/"))
    }

    private class FakeDirectoryCacheDao : DirectoryCacheDao {
        val cacheMap = mutableMapOf<String, DirectoryCacheEntity>()
        val getCacheCallCount = mutableMapOf<String, Int>()

        override suspend fun getCache(serverId: Long, path: String): DirectoryCacheEntity? {
            getCacheCallCount[path] = (getCacheCallCount[path] ?: 0) + 1
            return cacheMap["$serverId:$path"]
        }

        override suspend fun insertOrUpdate(entity: DirectoryCacheEntity) {
            cacheMap["${entity.serverId}:${entity.path}"] = entity
        }

        override suspend fun deleteCache(serverId: Long, path: String) {
            cacheMap.remove("$serverId:$path")
        }

        override suspend fun deleteCacheByServerId(serverId: Long) {
            val prefix = "$serverId:"
            val keys = cacheMap.keys.filter { it.startsWith(prefix) }
            keys.forEach { cacheMap.remove(it) }
        }

        override suspend fun clearAll() {
            cacheMap.clear()
        }
    }

    private class FakeWebDavClient : WebDavClient {
        val results = mutableMapOf<String, ListDirectoryResult>()
        val callCount = mutableMapOf<String, Int>()

        override suspend fun testConnection(server: WebDavServer): ConnectionResult {
            return ConnectionResult.Success
        }

        override suspend fun listDirectory(server: WebDavServer, path: String): ListDirectoryResult {
            callCount[path] = (callCount[path] ?: 0) + 1
            return results[path] ?: ListDirectoryResult.Failure("Not found")
        }

        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long
        ): ByteArray? = null
    }
}
