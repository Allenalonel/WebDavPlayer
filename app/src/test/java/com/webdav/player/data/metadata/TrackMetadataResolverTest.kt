package com.webdav.player.data.metadata

import com.webdav.player.data.local.CoverArtStorage
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class TrackMetadataResolverTest {
    private lateinit var fakeClient: FakeWebDavRangeClient
    private lateinit var fakeStorage: FakeCoverArtStorage
    private lateinit var resolver: DefaultTrackMetadataResolver

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "http://example.com",
        )

    @Before
    fun setUp() {
        fakeClient = FakeWebDavRangeClient()
        fakeStorage = FakeCoverArtStorage()
        resolver =
            DefaultTrackMetadataResolver(
                webDavClient = fakeClient,
                coverArtStorage = fakeStorage,
                ioDispatcher = Dispatchers.Unconfined,
            )
    }

    @Test
    fun resolve_fetchesInitial512Kb_parsesId3v2TagsAndArtwork() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes(title = "Starlight", artist = "Muse", album = "Black Holes")
            fakeClient.stubFileBytes("/music/starlight.mp3", sampleMp3)

            val file = RemoteFile(name = "starlight.mp3", path = "/music/starlight.mp3", size = 5_000_000L)
            val metadata = resolver.resolve(testServer, file)

            assertNotNull(metadata)
            assertEquals("Starlight", metadata?.title)
            assertEquals("Muse", metadata?.artist)
            assertEquals("Black Holes", metadata?.album)
            assertEquals(240000L, metadata?.durationMs)
            assertEquals("/fake/covers/cover_1.jpg", metadata?.coverThumbnailPath)
        }

    @Test
    fun resolve_parsesFlacTagsAndArtwork() =
        runTest {
            val sampleFlac = buildSampleFlacBytes(title = "Time", artist = "Pink Floyd", album = "The Dark Side of the Moon")
            fakeClient.stubFileBytes("/music/time.flac", sampleFlac)

            val file = RemoteFile(name = "time.flac", path = "/music/time.flac", size = 30_000_000L)
            val metadata = resolver.resolve(testServer, file)

            assertNotNull(metadata)
            assertEquals("Time", metadata?.title)
            assertEquals("Pink Floyd", metadata?.artist)
            assertEquals("The Dark Side of the Moon", metadata?.album)
            assertEquals("/fake/covers/cover_1.jpg", metadata?.coverThumbnailPath)
        }

    @Test
    fun resolve_twoStageStitch_fetchesSecondaryChunkWhenTagSizeExceedsInitialChunk() =
        runTest {
            // Build an ID3 tag larger than 512KB (e.g. 700KB with large embedded art)
            val largeArtwork =
                ByteArray(600 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[size - 2] = 0xFF.toByte()
                    this[size - 1] = 0xD9.toByte()
                }
            val largeMp3 =
                buildSampleId3v2Bytes(
                    title = "High Res Song",
                    artist = "Artist",
                    album = "Album",
                    artworkBytes = largeArtwork,
                    forceValidJpeg = false,
                )
            fakeClient.stubFileBytes("/music/highres.mp3", largeMp3)

            val file = RemoteFile(name = "highres.mp3", path = "/music/highres.mp3", size = 10_000_000L)
            val metadata = resolver.resolve(testServer, file)

            assertNotNull(metadata)
            assertEquals("High Res Song", metadata?.title)
            assertEquals("/fake/covers/cover_1.jpg", metadata?.coverThumbnailPath)
            assertTrue("Expected secondary range request", fakeClient.requestedRanges.size >= 2)
            assertEquals(0L, fakeClient.requestedRanges[0].second)
            assertEquals(524287L, fakeClient.requestedRanges[0].third)
            assertEquals(524288L, fakeClient.requestedRanges[1].second)
        }

    @Test
    fun resolve_twoStageStitch_handlesServerReturningFullStreamFromZero() =
        runTest {
            val largeArtwork =
                ByteArray(600 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[size - 2] = 0xFF.toByte()
                    this[size - 1] = 0xD9.toByte()
                }
            val largeMp3 = buildSampleId3v2Bytes("Full Stream Song", "Artist", "Album", artworkBytes = largeArtwork, forceValidJpeg = false)
            fakeClient.stubFileBytes("/music/fullstream.mp3", largeMp3)
            fakeClient.ignoreRangeOnSecondaryFetchPaths.add("/music/fullstream.mp3")

            val file = RemoteFile(name = "fullstream.mp3", path = "/music/fullstream.mp3", size = 10_000_000L)
            val metadata = resolver.resolve(testServer, file)

            assertNotNull(metadata)
            assertEquals("Full Stream Song", metadata?.title)
            assertEquals("/fake/covers/cover_1.jpg", metadata?.coverThumbnailPath)
        }

    @Test
    fun resolve_fallsBackToTrackSpecificArtwork_whenNoEmbeddedArtwork() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Song Without Cover", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/rock/song.mp3", sampleMp3)

            // Track-specific artwork: song.jpg
            val songJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0xFF.toByte(), 0xD9.toByte())
            fakeClient.stubFileBytes("/music/rock/song.jpg", songJpg)

            val file = RemoteFile(name = "song.mp3", path = "/music/rock/song.mp3", size = 5000L)
            val metadata = resolver.resolve(testServer, file)

            assertNotNull(metadata)
            assertEquals("/fake/covers/cover_1.jpg", metadata?.coverThumbnailPath)
            assertTrue(fakeStorage.savedThumbnails.containsKey("1:/music/rock/song.jpg"))
        }

    @Test
    fun resolve_fallsBackToFolderArtwork_whenNoTrackArtwork() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Folder Cover Song", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/pop/track.mp3", sampleMp3)

            val folderCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0xFF.toByte(), 0xD9.toByte())
            fakeClient.stubFileBytes("/music/pop/cover.jpg", folderCover)

            val file = RemoteFile(name = "track.mp3", path = "/music/pop/track.mp3", size = 5000L)
            val metadata = resolver.resolve(testServer, file)

            assertNotNull(metadata)
            assertEquals("/fake/covers/cover_1.jpg", metadata?.coverThumbnailPath)
            assertTrue(fakeStorage.savedThumbnails.containsKey("1:/music/pop/cover.jpg"))
        }

    @Test
    fun resolve_folderArtworkNegativeCaching_doesNotReProbeAbsence() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("No Artwork Track", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/empty/track1.mp3", sampleMp3)
            fakeClient.stubFileBytes("/music/empty/track2.mp3", sampleMp3)

            val file1 = RemoteFile(name = "track1.mp3", path = "/music/empty/track1.mp3", size = 5000L)
            val file2 = RemoteFile(name = "track2.mp3", path = "/music/empty/track2.mp3", size = 5000L)

            val meta1 = resolver.resolve(testServer, file1)
            assertNotNull(meta1)
            assertNull(meta1?.coverThumbnailPath)

            val fetchCountBefore = fakeClient.fetchCount.get()

            val meta2 = resolver.resolve(testServer, file2)
            assertNotNull(meta2)
            assertNull(meta2?.coverThumbnailPath)

            // track2 fetches track2 audio range (1 request) + 4 track-specific artwork candidates,
            // while completely bypassing all 4 shared folder artwork probes (cover.jpg, etc.) due to negative cache
            assertEquals(fetchCountBefore + 5, fakeClient.fetchCount.get())
        }

    @Test
    fun resolve_folderArtworkConcurrencyMutex_preventsProbeStorm() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Storm Track", "Artist", "Album", artworkBytes = null)
            for (i in 1..5) {
                fakeClient.stubFileBytes("/music/storm/track$i.mp3", sampleMp3)
            }
            val coverJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0xFF.toByte(), 0xD9.toByte())
            fakeClient.stubFileBytes("/music/storm/cover.jpg", coverJpg)

            val files =
                (1..5).map { i ->
                    RemoteFile(name = "track$i.mp3", path = "/music/storm/track$i.mp3", size = 5000L)
                }

            val results =
                files
                    .map { file ->
                        async { resolver.resolve(testServer, file) }
                    }.awaitAll()

            assertEquals(5, results.size)
            results.forEach { assertNotNull(it?.coverThumbnailPath) }
            assertEquals(1, fakeStorage.savedThumbnails.size)
        }

    @Test
    fun resolve_transientNetworkError_returnsNullWithoutCrashing() =
        runTest {
            fakeClient.throwOnPaths.add("/music/error.mp3")
            val file = RemoteFile(name = "error.mp3", path = "/music/error.mp3", size = 5000L)

            val result = resolver.resolve(testServer, file)
            assertNull("Transient network failure should return null", result)
        }

    @Test
    fun resolve_emptyOrNullRange_returnsNull() =
        runTest {
            // File not stubbed in client -> returns null range
            val file = RemoteFile(name = "missing.mp3", path = "/music/missing.mp3", size = 5000L)

            val result = resolver.resolve(testServer, file)
            assertNull(result)
        }

    @Test
    fun resolve_cachedFolderArtwork_whenFileDeletedFromDisk_treatsAsCacheMissAndReProbes() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Song 1", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/rock/song1.mp3", sampleMp3)
            fakeClient.stubFileBytes("/music/rock/song2.mp3", sampleMp3)
            val coverJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0xFF.toByte(), 0xD9.toByte())
            fakeClient.stubFileBytes("/music/rock/cover.jpg", coverJpg)

            val file1 = RemoteFile(name = "song1.mp3", path = "/music/rock/song1.mp3", size = 5000L)
            val file2 = RemoteFile(name = "song2.mp3", path = "/music/rock/song2.mp3", size = 5000L)

            // First resolve: resolves cover.jpg and populates folderArtworkCache
            val meta1 = resolver.resolve(testServer, file1)
            assertNotNull(meta1?.coverThumbnailPath)
            val initialFetches = fakeClient.fetchCount.get()

            // Simulate file deletion on disk (e.g. system cache clear while app is in memory)
            fakeStorage.diskFiles.clear()

            // Second resolve for song2: must NOT reuse dead cached path, must treat as cache miss and re-probe
            val meta2 = resolver.resolve(testServer, file2)
            assertNotNull("Must re-probe and restore thumbnail", meta2?.coverThumbnailPath)
            assertTrue("Must have fetched cover from network again", fakeClient.fetchCount.get() > initialFetches)
            assertTrue(fakeStorage.isValidThumbnailFile(meta2?.coverThumbnailPath))
        }

    @Test
    fun resolve_negativeFolderArtworkSentinel_whenCacheCleared_allowsReProbingNetwork() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("No Art Song", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/pop/track1.mp3", sampleMp3)
            fakeClient.stubFileBytes("/music/pop/track2.mp3", sampleMp3)
            // No cover.jpg initially on server

            val file1 = RemoteFile(name = "track1.mp3", path = "/music/pop/track1.mp3", size = 5000L)
            val file2 = RemoteFile(name = "track2.mp3", path = "/music/pop/track2.mp3", size = 5000L)

            val meta1 = resolver.resolve(testServer, file1)
            assertNull("No artwork initially", meta1?.coverThumbnailPath)

            // Track 2 in same folder uses negative cache: 0 network probes for cover
            val meta2 = resolver.resolve(testServer, file2)
            assertNull(meta2?.coverThumbnailPath)

            // User now adds cover.jpg to server and triggers force-refresh / clearCache
            val coverJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0xFF.toByte(), 0xD9.toByte())
            fakeClient.stubFileBytes("/music/pop/cover.jpg", coverJpg)

            // Without clearCache, negative cache sentinel would block finding cover.jpg
            resolver.clearCache()

            val meta3 = resolver.resolve(testServer, file2)
            assertNotNull("After clearCache, re-probing finds newly added cover.jpg", meta3?.coverThumbnailPath)
            assertTrue(fakeStorage.isValidThumbnailFile(meta3?.coverThumbnailPath))
        }

    private fun buildSampleId3v2Bytes(
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray? = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
        forceValidJpeg: Boolean = true,
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0)
        stream.write(0)

        val body = ByteArrayOutputStream()

        fun writeFrame(
            id: String,
            text: String,
        ) {
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

        if (artworkBytes != null) {
            val validArtwork =
                if (forceValidJpeg && artworkBytes.size >= 4 && artworkBytes[0] == 0xFF.toByte() && artworkBytes[1] == 0xD8.toByte()) {
                    artworkBytes.copyOf().apply {
                        this[this.size - 2] = 0xFF.toByte()
                        this[this.size - 1] = 0xD9.toByte()
                    }
                } else {
                    artworkBytes
                }
            val apicBody = ByteArrayOutputStream()
            apicBody.write(0)
            apicBody.write("image/jpeg\u0000".toByteArray(StandardCharsets.ISO_8859_1))
            apicBody.write(3)
            apicBody.write("cover\u0000".toByteArray(StandardCharsets.ISO_8859_1))
            apicBody.write(validArtwork)
            val apicBytes = apicBody.toByteArray()

            body.write("APIC".toByteArray(StandardCharsets.US_ASCII))
            body.write((apicBytes.size shr 24) and 0xFF)
            body.write((apicBytes.size shr 16) and 0xFF)
            body.write((apicBytes.size shr 8) and 0xFF)
            body.write(apicBytes.size and 0xFF)
            body.write(0)
            body.write(0)
            body.write(apicBytes)
        }

        writeFrame("TIT2", title)
        writeFrame("TPE1", artist)
        writeFrame("TALB", album)
        writeFrame("TLEN", "240000")

        val bodyBytes = body.toByteArray()
        val s = bodyBytes.size
        stream.write((s shr 21) and 0x7F)
        stream.write((s shr 14) and 0x7F)
        stream.write((s shr 7) and 0x7F)
        stream.write(s and 0x7F)
        stream.write(bodyBytes)

        return stream.toByteArray()
    }

    private fun buildSampleFlacBytes(
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray? = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

        val vcStream = ByteArrayOutputStream()
        val vendor = "reference libFLAC".toByteArray(StandardCharsets.UTF_8)
        vcStream.write(vendor.size and 0xFF)
        vcStream.write((vendor.size shr 8) and 0xFF)
        vcStream.write((vendor.size shr 16) and 0xFF)
        vcStream.write((vendor.size shr 24) and 0xFF)
        vcStream.write(vendor)

        val comments = listOf("TITLE=$title", "ARTIST=$artist", "ALBUM=$album")
        vcStream.write(comments.size and 0xFF)
        vcStream.write((comments.size shr 8) and 0xFF)
        vcStream.write((comments.size shr 16) and 0xFF)
        vcStream.write((comments.size shr 24) and 0xFF)

        for (c in comments) {
            val b = c.toByteArray(StandardCharsets.UTF_8)
            vcStream.write(b.size and 0xFF)
            vcStream.write((b.size shr 8) and 0xFF)
            vcStream.write((b.size shr 16) and 0xFF)
            vcStream.write((b.size shr 24) and 0xFF)
            vcStream.write(b)
        }

        val vcBytes = vcStream.toByteArray()
        val isLastBlock = artworkBytes == null
        val headerByte = if (isLastBlock) 0x84 else 0x04
        stream.write(headerByte)
        stream.write((vcBytes.size shr 16) and 0xFF)
        stream.write((vcBytes.size shr 8) and 0xFF)
        stream.write(vcBytes.size and 0xFF)
        stream.write(vcBytes)

        if (artworkBytes != null) {
            val validArtwork =
                if (artworkBytes.size >= 4 && artworkBytes[0] == 0xFF.toByte() && artworkBytes[1] == 0xD8.toByte()) {
                    artworkBytes.copyOf().apply {
                        this[this.size - 2] = 0xFF.toByte()
                        this[this.size - 1] = 0xD9.toByte()
                    }
                } else {
                    artworkBytes
                }
            val picBody = ByteArrayOutputStream()
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            picBody.write(3)
            val mime = "image/jpeg".toByteArray(StandardCharsets.US_ASCII)
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            picBody.write(mime.size)
            picBody.write(mime)
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            for (i in 0 until 16) picBody.write(0)
            picBody.write((validArtwork.size shr 24) and 0xFF)
            picBody.write((validArtwork.size shr 16) and 0xFF)
            picBody.write((validArtwork.size shr 8) and 0xFF)
            picBody.write(validArtwork.size and 0xFF)
            picBody.write(validArtwork)

            val picBytes = picBody.toByteArray()
            stream.write(0x86)
            stream.write((picBytes.size shr 16) and 0xFF)
            stream.write((picBytes.size shr 8) and 0xFF)
            stream.write(picBytes.size and 0xFF)
            stream.write(picBytes)
        }

        return stream.toByteArray()
    }

    private class FakeWebDavRangeClient : WebDavClient {
        val files = mutableMapOf<String, ByteArray>()
        val throwOnPaths = mutableSetOf<String>()
        val ignoreRangeOnSecondaryFetchPaths = mutableSetOf<String>()
        val fetchCount = AtomicInteger(0)
        val requestedRanges = mutableListOf<Triple<String, Long, Long>>()

        fun stubFileBytes(
            path: String,
            bytes: ByteArray,
        ) {
            files[path] = bytes
        }

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
        ): ByteArray? {
            fetchCount.incrementAndGet()
            requestedRanges.add(Triple(remotePath, startByte, endByte))
            if (throwOnPaths.contains(remotePath)) {
                throw IOException("Simulated network timeout for $remotePath")
            }
            val full = files[remotePath] ?: return null
            if (startByte > 0 && ignoreRangeOnSecondaryFetchPaths.contains(remotePath)) {
                return full
            }
            if (startByte >= full.size) return byteArrayOf()
            val from = startByte.toInt()
            val to = minOf(full.size, (endByte + 1).toInt())
            return full.copyOfRange(from, to)
        }
    }

    private class FakeCoverArtStorage : CoverArtStorage {
        val savedThumbnails = mutableMapOf<String, ByteArray>()
        val diskFiles = mutableSetOf<String>()

        override suspend fun saveThumbnail(
            serverId: Long,
            remotePath: String,
            artworkBytes: ByteArray,
        ): String? {
            val path = "/fake/covers/cover_$serverId.jpg"
            savedThumbnails["$serverId:$remotePath"] = artworkBytes
            diskFiles.add(path)
            return path
        }

        override fun isValidThumbnailFile(filePath: String?): Boolean {
            if (filePath.isNullOrBlank()) return false
            return diskFiles.contains(filePath)
        }

        override fun getThumbnailFile(
            serverId: Long,
            remotePath: String,
        ): File? =
            if (savedThumbnails.containsKey("$serverId:$remotePath") && diskFiles.contains("/fake/covers/cover_$serverId.jpg")) {
                File("/fake/covers/cover_$serverId.jpg")
            } else {
                null
            }

        override fun deleteThumbnail(
            serverId: Long,
            remotePath: String,
        ) {
            savedThumbnails.remove("$serverId:$remotePath")
            if (savedThumbnails.none { it.key.startsWith("$serverId:") }) {
                diskFiles.remove("/fake/covers/cover_$serverId.jpg")
            }
        }

        override suspend fun deleteServerCovers(serverId: Long) {
            savedThumbnails.keys.filter { it.startsWith("$serverId:") }.forEach { savedThumbnails.remove(it) }
            diskFiles.remove("/fake/covers/cover_$serverId.jpg")
        }
    }
}
