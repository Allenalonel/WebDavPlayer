package com.webdav.player.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.data.local.WebDavServerEntity
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LyricsRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TrackMetadataDao
    private lateinit var fakeClient: FakeLyricsWebDavClient
    private lateinit var fakeStorage: FakeCoverArtStorage
    private lateinit var repository: TrackMetadataRepositoryImpl

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "http://example.com",
        )

    private val testTrack =
        AudioTrack(
            id = "1:/music/Queen/Bohemian Rhapsody.mp3",
            serverId = 1L,
            remotePath = "/music/Queen/Bohemian Rhapsody.mp3",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            format = AudioFormat.MP3,
        )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.trackMetadataDao()
        kotlinx.coroutines.runBlocking {
            database.webDavServerDao().insertServer(WebDavServerEntity.fromDomain(testServer))
        }
        fakeClient = FakeLyricsWebDavClient()
        fakeStorage = FakeCoverArtStorage()
        repository =
            TrackMetadataRepositoryImpl(
                trackMetadataDao = dao,
                webDavClient = fakeClient,
                coverArtStorage = fakeStorage,
                webDavServerDao = database.webDavServerDao(),
                maxConcurrency = 2,
                ioDispatcher = Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolveLyrics_whenRemoteLrcExists_returnsRemoteLyrics_andPrioritizesOverEmbedded() =
        runTest {
            val expectedLrcPath = "/music/Queen/Bohemian Rhapsody.lrc"
            val remoteLrcContent =
                """
                [00:01.00]Is this the real life?
                [00:05.00]Is this just fantasy?
                """.trimIndent()

            fakeClient.textFiles[expectedLrcPath] = remoteLrcContent
            dao.insertOrUpdate(
                TrackMetadataEntity(
                    serverId = testServer.id,
                    remotePath = testTrack.remotePath,
                    title = testTrack.title,
                    artist = testTrack.artist,
                    album = testTrack.album,
                    trackNumber = null,
                    durationMs = 0L,
                    coverThumbnailPath = null,
                    lyrics = "[00:10.00]Should not be used",
                ),
            )

            val lyrics = repository.resolveLyrics(testServer, testTrack)

            assertTrue(lyrics.isSynchronized)
            assertEquals(2, lyrics.lines.size)
            assertEquals(1000L, lyrics.lines[0].timestampMs)
            assertEquals("Is this the real life?", lyrics.lines[0].text)
            assertEquals(5000L, lyrics.lines[1].timestampMs)
            assertEquals("Is this just fantasy?", lyrics.lines[1].text)
            assertEquals(1, fakeClient.fetchTextCount.get())
        }

    @Test
    fun resolveLyrics_whenRemoteLrcNotFound_fallsBackToEmbeddedLyrics() =
        runTest {
            val embeddedLyrics =
                """
                [00:10.00]Embedded line 1
                [00:20.00]Embedded line 2
                """.trimIndent()
            dao.insertOrUpdate(
                TrackMetadataEntity(
                    serverId = testServer.id,
                    remotePath = testTrack.remotePath,
                    title = testTrack.title,
                    artist = testTrack.artist,
                    album = testTrack.album,
                    trackNumber = null,
                    durationMs = 0L,
                    coverThumbnailPath = null,
                    lyrics = embeddedLyrics,
                ),
            )

            val lyrics = repository.resolveLyrics(testServer, testTrack)

            assertTrue(lyrics.isSynchronized)
            assertEquals(2, lyrics.lines.size)
            assertEquals(10000L, lyrics.lines[0].timestampMs)
            assertEquals("Embedded line 1", lyrics.lines[0].text)
            assertEquals(20000L, lyrics.lines[1].timestampMs)
            assertEquals("Embedded line 2", lyrics.lines[1].text)
        }

    @Test
    fun resolveLyrics_whenNeitherSourceExists_returnsEmptyLyrics() =
        runTest {
            val lyrics = repository.resolveLyrics(testServer, testTrack)

            assertTrue(lyrics.isEmpty)
            assertFalse(lyrics.isSynchronized)
        }

    @Test
    fun resolveLyrics_cachesResultInMemory_avoidsRepeatedNetworkCalls() =
        runTest {
            val expectedLrcPath = "/music/Queen/Bohemian Rhapsody.lrc"
            fakeClient.textFiles[expectedLrcPath] = "[00:01.00]Only once"

            val firstCall = repository.resolveLyrics(testServer, testTrack)
            val secondCall = repository.resolveLyrics(testServer, testTrack)

            assertEquals(firstCall.lines.size, secondCall.lines.size)
            assertEquals(firstCall.lines[0].text, secondCall.lines[0].text)
            assertEquals(1, fakeClient.fetchTextCount.get())
        }

    @Test
    fun clearCache_evictsCachedLyrics() =
        runTest {
            val lrcPath = "/music/Queen/Bohemian Rhapsody.lrc"
            fakeClient.textFiles[lrcPath] = "[00:01.00]Only once"

            repository.resolveLyrics(testServer, testTrack)
            assertEquals(1, fakeClient.fetchTextCount.get())

            repository.clearCache()

            repository.resolveLyrics(testServer, testTrack)
            assertEquals(2, fakeClient.fetchTextCount.get())
        }

    private class FakeLyricsWebDavClient : WebDavClient {
        val textFiles = mutableMapOf<String, String>()
        val fetchTextCount = AtomicInteger(0)

        override suspend fun testConnection(server: WebDavServer): ConnectionResult = ConnectionResult.Success

        override suspend fun listDirectory(
            server: WebDavServer,
            path: String,
        ): ListDirectoryResult = ListDirectoryResult.Failure("Not implemented")

        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long,
        ): ByteArray? = null

        override suspend fun fetchText(
            server: WebDavServer,
            remotePath: String,
        ): String? {
            fetchTextCount.incrementAndGet()
            return textFiles[remotePath]
        }
    }

    private class FakeCoverArtStorage : CoverArtStorage {
        override suspend fun saveThumbnail(
            serverId: Long,
            remotePath: String,
            artworkBytes: ByteArray,
        ): String? = "/fake/covers/cover_$serverId.jpg"

        override fun getThumbnailFile(
            serverId: Long,
            remotePath: String,
        ): java.io.File? = null

        override fun deleteThumbnail(
            serverId: Long,
            remotePath: String,
        ) {}

        override suspend fun deleteServerCovers(serverId: Long) {}

        override fun isValidThumbnailFile(filePath: String?): Boolean = true
    }
}
