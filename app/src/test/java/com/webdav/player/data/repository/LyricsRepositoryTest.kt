package com.webdav.player.data.repository

import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.LyricsRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LyricsRepositoryTest {

    private lateinit var fakeClient: FakeLyricsWebDavClient
    private lateinit var fakeMetadataRepo: FakeTrackMetadataRepository
    private lateinit var lyricsRepository: LyricsRepository

    private val testServer = WebDavServer(
        id = 1L,
        name = "Test Server",
        url = "http://example.com"
    )

    private val testTrack = AudioTrack(
        id = "1:/music/Queen/Bohemian Rhapsody.mp3",
        serverId = 1L,
        remotePath = "/music/Queen/Bohemian Rhapsody.mp3",
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        format = AudioFormat.MP3
    )

    @Before
    fun setUp() {
        fakeClient = FakeLyricsWebDavClient()
        fakeMetadataRepo = FakeTrackMetadataRepository()
        lyricsRepository = LyricsRepositoryImpl(
            webDavClient = fakeClient,
            trackMetadataRepository = fakeMetadataRepo
        )
    }

    @Test
    fun resolveLyrics_whenRemoteLrcExists_returnsRemoteLyrics_andPrioritizesOverEmbedded() = runTest {
        val expectedLrcPath = "/music/Queen/Bohemian Rhapsody.lrc"
        val remoteLrcContent = """
            [00:01.00]Is this the real life?
            [00:05.00]Is this just fantasy?
        """.trimIndent()

        fakeClient.textFiles[expectedLrcPath] = remoteLrcContent
        fakeMetadataRepo.cachedMetadata[testTrack.remotePath] = TrackMetadata(
            serverId = testServer.id,
            remotePath = testTrack.remotePath,
            title = testTrack.title,
            lyrics = "[00:10.00]Should not be used"
        )

        val lyrics = lyricsRepository.resolveLyrics(testServer, testTrack)

        assertTrue(lyrics.isSynchronized)
        assertEquals(2, lyrics.lines.size)
        assertEquals(1000L, lyrics.lines[0].timestampMs)
        assertEquals("Is this the real life?", lyrics.lines[0].text)
        assertEquals(5000L, lyrics.lines[1].timestampMs)
        assertEquals("Is this just fantasy?", lyrics.lines[1].text)

        // Verify webDavClient was probed
        assertEquals(1, fakeClient.fetchTextCount.get())
        // Verify embedded metadata was NOT queried because remote succeeded
        assertEquals(0, fakeMetadataRepo.getCachedMetadataCount.get())
    }

    @Test
    fun resolveLyrics_whenRemoteLrcNotFound_fallsBackToEmbeddedLyrics() = runTest {
        // Remote .lrc not present in fakeClient
        val embeddedLyrics = """
            [00:10.00]Embedded line 1
            [00:20.00]Embedded line 2
        """.trimIndent()
        fakeMetadataRepo.cachedMetadata[testTrack.remotePath] = TrackMetadata(
            serverId = testServer.id,
            remotePath = testTrack.remotePath,
            title = testTrack.title,
            lyrics = embeddedLyrics
        )

        val lyrics = lyricsRepository.resolveLyrics(testServer, testTrack)

        assertTrue(lyrics.isSynchronized)
        assertEquals(2, lyrics.lines.size)
        assertEquals(10000L, lyrics.lines[0].timestampMs)
        assertEquals("Embedded line 1", lyrics.lines[0].text)
        assertEquals(20000L, lyrics.lines[1].timestampMs)
        assertEquals("Embedded line 2", lyrics.lines[1].text)
    }

    @Test
    fun resolveLyrics_whenNeitherSourceExists_returnsEmptyLyrics() = runTest {
        val lyrics = lyricsRepository.resolveLyrics(testServer, testTrack)

        assertTrue(lyrics.isEmpty)
        assertFalse(lyrics.isSynchronized)
    }

    @Test
    fun resolveLyrics_cachesResultInMemory_avoidsRepeatedNetworkCalls() = runTest {
        val expectedLrcPath = "/music/Queen/Bohemian Rhapsody.lrc"
        fakeClient.textFiles[expectedLrcPath] = "[00:01.00]Only once"

        val firstCall = lyricsRepository.resolveLyrics(testServer, testTrack)
        val secondCall = lyricsRepository.resolveLyrics(testServer, testTrack)

        assertEquals(firstCall.lines.size, secondCall.lines.size)
        assertEquals(firstCall.lines[0].text, secondCall.lines[0].text)
        assertEquals(1, fakeClient.fetchTextCount.get())
    }

    private class FakeLyricsWebDavClient : WebDavClient {
        val textFiles = mutableMapOf<String, String>()
        val fetchTextCount = AtomicInteger(0)

        override suspend fun testConnection(server: WebDavServer): ConnectionResult = ConnectionResult.Success
        override suspend fun listDirectory(server: WebDavServer, path: String): ListDirectoryResult =
            ListDirectoryResult.Failure("Not implemented")
        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long
        ): ByteArray? = null

        override suspend fun fetchText(server: WebDavServer, remotePath: String): String? {
            fetchTextCount.incrementAndGet()
            return textFiles[remotePath]
        }
    }

    private class FakeTrackMetadataRepository : TrackMetadataRepository {
        val cachedMetadata = mutableMapOf<String, TrackMetadata>()
        val getCachedMetadataCount = AtomicInteger(0)

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> = emptyFlow()
        override fun getMetadataForPathsFlow(serverId: Long, remotePaths: List<String>): Flow<List<TrackMetadata>> = emptyFlow()
        override fun getMetadataFlow(serverId: Long, remotePath: String): Flow<TrackMetadata?> = emptyFlow()

        override suspend fun getCachedMetadata(serverId: Long, remotePath: String): TrackMetadata? {
            getCachedMetadataCount.incrementAndGet()
            return cachedMetadata[remotePath]
        }

        override suspend fun resolveMetadata(server: WebDavServer, files: List<RemoteFile>) {}
        override suspend fun resolveSingleTrackMetadata(server: WebDavServer, file: RemoteFile): TrackMetadata {
            return cachedMetadata[file.path] ?: TrackMetadata(
                serverId = server.id,
                remotePath = file.path,
                title = file.name
            )
        }
    }
}
