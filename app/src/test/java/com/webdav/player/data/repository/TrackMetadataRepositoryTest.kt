package com.webdav.player.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackMetadataRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TrackMetadataDao
    private lateinit var fakeClient: FakeWebDavRangeClient
    private lateinit var fakeStorage: FakeCoverArtStorage
    private lateinit var repository: TrackMetadataRepositoryImpl

    private val testServer = WebDavServer(
        id = 1L,
        name = "Test Server",
        url = "http://example.com"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.trackMetadataDao()
        fakeClient = FakeWebDavRangeClient()
        fakeStorage = FakeCoverArtStorage()
        repository = TrackMetadataRepositoryImpl(
            trackMetadataDao = dao,
            webDavClient = fakeClient,
            coverArtStorage = fakeStorage,
            maxConcurrency = 2,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolveMetadata_fetchesRange_parsesTags_andSavesToRoom() = runTest {
        val sampleMp3 = buildSampleId3v2Bytes(title = "Starlight", artist = "Muse", album = "Black Holes")
        fakeClient.stubFileBytes("/music/starlight.mp3", sampleMp3)

        val files = listOf(
            RemoteFile(name = "starlight.mp3", path = "/music/starlight.mp3", size = 5000000L)
        )

        repository.resolveMetadata(testServer, files)

        val cached = repository.getCachedMetadata(1L, "/music/starlight.mp3")
        assertNotNull(cached)
        assertEquals("Starlight", cached?.title)
        assertEquals("Muse", cached?.artist)
        assertEquals("Black Holes", cached?.album)
        assertEquals(240000L, cached?.durationMs)
        assertEquals("/fake/covers/cover_1.jpg", cached?.coverThumbnailPath)
    }

    @Test
    fun resolveMetadata_skipsAlreadyCachedFiles() = runTest {
        val sampleMp3 = buildSampleId3v2Bytes(title = "Cached Song", artist = "Artist", album = "Album")
        fakeClient.stubFileBytes("/music/cached.mp3", sampleMp3)

        val file = RemoteFile(name = "cached.mp3", path = "/music/cached.mp3", size = 1000L)

        // Resolve first time
        repository.resolveMetadata(testServer, listOf(file))
        assertEquals(1, fakeClient.fetchCount.get())

        // Resolve second time with same file
        repository.resolveMetadata(testServer, listOf(file))
        // fetchCount should remain 1 because it was already in Room
        assertEquals(1, fakeClient.fetchCount.get())
    }

    @Test
    fun resolveMetadata_failedFetch_gracefullyFallsBackToCleanFileName() = runTest {
        // No stub registered in fake client, returns null
        val file = RemoteFile(name = "broken_song.flac", path = "/music/broken_song.flac", size = 2000L)

        repository.resolveMetadata(testServer, listOf(file))

        val cached = repository.getCachedMetadata(1L, "/music/broken_song.flac")
        assertNotNull(cached)
        assertEquals("broken_song", cached?.title)
        assertNull(cached?.artist)
        assertNull(cached?.coverThumbnailPath)
    }

    @Test
    fun getMetadataForPathsFlow_emitsIncrementalUpdates() = runTest {
        val sampleMp3 = buildSampleId3v2Bytes(title = "Track A", artist = "Artist A", album = "Album A")
        fakeClient.stubFileBytes("/music/trackA.mp3", sampleMp3)

        val files = listOf(
            RemoteFile(name = "trackA.mp3", path = "/music/trackA.mp3", size = 1000L)
        )

        val flow = repository.getMetadataForPathsFlow(1L, listOf("/music/trackA.mp3"))
        val initial = flow.first()
        assertTrue(initial.isEmpty())

        repository.resolveMetadata(testServer, files)

        val updated = flow.first()
        assertEquals(1, updated.size)
        assertEquals("Track A", updated[0].title)
    }

    private fun buildSampleId3v2Bytes(title: String, artist: String, album: String): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0)
        stream.write(0)

        val body = ByteArrayOutputStream()

        fun writeFrame(id: String, text: String) {
            val p = ByteArrayOutputStream()
            p.write(3) // UTF-8
            p.write(text.toByteArray(StandardCharsets.UTF_8))
            val b = p.toByteArray()
            body.write(id.toByteArray(StandardCharsets.US_ASCII))
            body.write((b.size shr 24) and 0xFF)
            body.write((b.size shr 16) and 0xFF)
            body.write((b.size shr 8) and 0xFF)
            body.write(b.size and 0xFF)
            body.write(0)
            body.write(0)
            body.write(b)
        }

        // APIC picture
        val apicBody = ByteArrayOutputStream()
        apicBody.write(0)
        apicBody.write("image/jpeg\u0000".toByteArray(StandardCharsets.ISO_8859_1))
        apicBody.write(3)
        apicBody.write("cover\u0000".toByteArray(StandardCharsets.ISO_8859_1))
        apicBody.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        val apicBytes = apicBody.toByteArray()

        writeFrame("TIT2", title)
        writeFrame("TPE1", artist)
        writeFrame("TALB", album)
        writeFrame("TLEN", "240000")

        body.write("APIC".toByteArray(StandardCharsets.US_ASCII))
        body.write((apicBytes.size shr 24) and 0xFF)
        body.write((apicBytes.size shr 16) and 0xFF)
        body.write((apicBytes.size shr 8) and 0xFF)
        body.write(apicBytes.size and 0xFF)
        body.write(0)
        body.write(0)
        body.write(apicBytes)

        val bodyBytes = body.toByteArray()
        val s = bodyBytes.size
        stream.write((s shr 21) and 0x7F)
        stream.write((s shr 14) and 0x7F)
        stream.write((s shr 7) and 0x7F)
        stream.write(s and 0x7F)
        stream.write(bodyBytes)

        return stream.toByteArray()
    }

    private class FakeWebDavRangeClient : WebDavClient {
        val files = mutableMapOf<String, ByteArray>()
        val fetchCount = AtomicInteger(0)

        fun stubFileBytes(path: String, bytes: ByteArray) {
            files[path] = bytes
        }

        override suspend fun testConnection(server: WebDavServer): ConnectionResult = ConnectionResult.Success

        override suspend fun listDirectory(server: WebDavServer, path: String): ListDirectoryResult =
            ListDirectoryResult.Failure("Not implemented")

        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long
        ): ByteArray? {
            fetchCount.incrementAndGet()
            return files[remotePath]
        }
    }

    private class FakeCoverArtStorage : CoverArtStorage {
        override suspend fun saveThumbnail(
            serverId: Long,
            remotePath: String,
            artworkBytes: ByteArray
        ): String? {
            return "/fake/covers/cover_$serverId.jpg"
        }

        override fun getThumbnailFile(serverId: Long, remotePath: String): File? = null
        override fun deleteThumbnail(serverId: Long, remotePath: String) {}
    }
}
