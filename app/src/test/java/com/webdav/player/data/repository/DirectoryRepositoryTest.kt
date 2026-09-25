package com.webdav.player.data.repository

import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DirectoryRepositoryTest {

    private lateinit var fakeClient: FakeWebDavClient
    private lateinit var repository: DirectoryRepositoryImpl

    private val testServer = WebDavServer(
        id = 1L,
        name = "Test Server",
        url = "http://localhost",
        port = 8080
    )

    @Before
    fun setUp() {
        fakeClient = FakeWebDavClient()
        repository = DirectoryRepositoryImpl(fakeClient)
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
