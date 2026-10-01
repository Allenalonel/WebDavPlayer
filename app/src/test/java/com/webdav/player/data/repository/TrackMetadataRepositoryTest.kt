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

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "http://example.com",
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
        fakeClient = FakeWebDavRangeClient()
        fakeStorage = FakeCoverArtStorage()
        repository =
            TrackMetadataRepositoryImpl(
                trackMetadataDao = dao,
                webDavClient = fakeClient,
                coverArtStorage = fakeStorage,
                maxConcurrency = 2,
                ioDispatcher = Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolveMetadata_fetchesRange_parsesTags_andSavesToRoom() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes(title = "Starlight", artist = "Muse", album = "Black Holes")
            fakeClient.stubFileBytes("/music/starlight.mp3", sampleMp3)

            val files =
                listOf(
                    RemoteFile(name = "starlight.mp3", path = "/music/starlight.mp3", size = 5000000L),
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
    fun resolveMetadata_skipsAlreadyCachedFiles() =
        runTest {
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
    fun resolveMetadata_transientNetworkError_doesNotPoisonRoomCache_allowsRetryOnSubsequentVisit() =
        runTest {
            // No stub registered in fake client, returns null (simulating transient network error)
            val file = RemoteFile(name = "broken_song.flac", path = "/music/broken_song.flac", size = 2000L)

            repository.resolveMetadata(testServer, listOf(file))

            // Must NOT poison Room with a null entry
            val cachedAfterFailure = repository.getCachedMetadata(1L, "/music/broken_song.flac")
            assertNull("Transient network failure must not poison Room with permanent null entry", cachedAfterFailure)

            // Simulate network recovery on subsequent visit
            val sampleFlac = buildSampleFlacBytes(title = "Healed Song", artist = "Healed Artist", album = "Healed Album")
            fakeClient.stubFileBytes("/music/broken_song.flac", sampleFlac)

            repository.resolveMetadata(testServer, listOf(file))

            val cachedAfterRecovery = repository.getCachedMetadata(1L, "/music/broken_song.flac")
            assertNotNull("Subsequent visit after network recovery must heal cache and populate Room", cachedAfterRecovery)
            assertEquals("Healed Song", cachedAfterRecovery?.title)
            assertEquals("Healed Artist", cachedAfterRecovery?.artist)
        }

    @Test
    fun resolveMetadata_genuineAbsenceOfTags_persistsCleanFallbackToRoom() =
        runTest {
            // Raw bytes without tags (e.g. 1024 zeroes), successfully fetched from server
            val rawAudioWithoutTags = ByteArray(1024)
            fakeClient.stubFileBytes("/music/clean_song.mp3", rawAudioWithoutTags)

            val file = RemoteFile(name = "clean_song.mp3", path = "/music/clean_song.mp3", size = 1024L)

            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/clean_song.mp3")
            assertNotNull("Genuinely tagless audio file should be cached to avoid repetitive probing", cached)
            assertEquals("clean_song", cached?.title)
            assertNull(cached?.artist)
            assertNull(cached?.coverThumbnailPath)

            val fetchCountBefore = fakeClient.fetchCount.get()
            // Second visit should skip re-fetching because Room has the cached entry
            repository.resolveMetadata(testServer, listOf(file))
            assertEquals("Already cached tagless file must not be re-probed", fetchCountBefore, fakeClient.fetchCount.get())
        }

    @Test
    fun resolveSingleTrackMetadata_transientNetworkError_doesNotPoisonRoomCache() =
        runTest {
            val file = RemoteFile(name = "transient_solo.mp3", path = "/music/transient_solo.mp3", size = 2000L)

            val fallbackResult = repository.resolveSingleTrackMetadata(testServer, file)
            assertEquals("transient_solo", fallbackResult.title)

            // Ephemeral result returned to caller, but Room must remain unpoisoned
            val cached = repository.getCachedMetadata(1L, "/music/transient_solo.mp3")
            assertNull("resolveSingleTrackMetadata must not cache transient failure in Room", cached)
        }

    @Test
    fun resolveMetadata_artworkNetworkError_doesNotPoisonRoomCache_healsWhenArtworkAvailable() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Song Without Embed", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/transient/song.mp3", sampleMp3)

            // Make folder cover probe fail with IOException
            fakeClient.throwOnPaths.add("/music/transient/cover.jpg")

            val file = RemoteFile(name = "song.mp3", path = "/music/transient/song.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            // Must NOT poison Room when folder cover retrieval hits IO error
            val cached = repository.getCachedMetadata(1L, "/music/transient/song.mp3")
            assertNull("Folder cover network failure must not poison Room with permanent entry", cached)

            // Network recovers, cover is provided
            fakeClient.throwOnPaths.remove("/music/transient/cover.jpg")
            val coverJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
            fakeClient.stubFileBytes("/music/transient/cover.jpg", coverJpg)

            repository.resolveMetadata(testServer, listOf(file))

            val healed = repository.getCachedMetadata(1L, "/music/transient/song.mp3")
            assertNotNull("Subsequent visit after network recovery must heal cache with folder artwork", healed)
            assertNotNull(healed?.coverThumbnailPath)
        }

    @Test
    fun resolveMetadata_corruptFolderCover_savesMetadataToRoomWithoutArtworkAndDoesNotRetry() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Corrupt Cover Song", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/corrupt_folder/song.mp3", sampleMp3)

            // Corrupt folder cover image bytes (cannot be decoded or validated)
            val corruptCover = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            fakeClient.stubFileBytes("/music/corrupt_folder/cover.jpg", corruptCover)

            val file = RemoteFile(name = "song.mp3", path = "/music/corrupt_folder/song.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            // Metadata must be persisted to Room with null coverThumbnailPath, avoiding infinite re-fetching
            val cached = repository.getCachedMetadata(1L, "/music/corrupt_folder/song.mp3")
            assertNotNull("Metadata must be persisted to Room even if folder cover is corrupt", cached)
            assertEquals("Corrupt Cover Song", cached?.title)
            assertNull("Corrupt folder cover must result in null thumbnailPath", cached?.coverThumbnailPath)

            // On subsequent visits, cached metadata must be reused without re-fetching
            val fetchCountBefore = fakeClient.fetchCount.get()
            repository.resolveMetadata(testServer, listOf(file))
            assertEquals("Subsequent visit must skip already cached track", fetchCountBefore, fakeClient.fetchCount.get())
        }

    @Test
    fun resolveMetadata_corruptTrackSpecificCover_fallsBackToFolderCover() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Track Artwork Song", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/corrupt_track/song.mp3", sampleMp3)

            // Corrupt track-specific cover
            val corruptTrackCover = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            fakeClient.stubFileBytes("/music/corrupt_track/song.jpg", corruptTrackCover)

            // Valid folder cover
            val validFolderCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
            fakeClient.stubFileBytes("/music/corrupt_track/cover.jpg", validFolderCover)

            val file = RemoteFile(name = "song.mp3", path = "/music/corrupt_track/song.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/corrupt_track/song.mp3")
            assertNotNull("Metadata must be persisted to Room", cached)
            assertEquals("Track Artwork Song", cached?.title)
            assertNotNull("Should fall back to valid folder cover when track cover is corrupt", cached?.coverThumbnailPath)
        }

    @Test
    fun resolveMetadata_bothTrackAndFolderCoversCorrupt_persistsToRoomWithoutArtwork() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Double Corrupt", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/both_corrupt/song.mp3", sampleMp3)

            val corruptBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            fakeClient.stubFileBytes("/music/both_corrupt/song.jpg", corruptBytes)
            fakeClient.stubFileBytes("/music/both_corrupt/cover.jpg", corruptBytes)

            val file = RemoteFile(name = "song.mp3", path = "/music/both_corrupt/song.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/both_corrupt/song.mp3")
            assertNotNull("Metadata must be cached in Room even when all candidate images are corrupt", cached)
            assertEquals("Double Corrupt", cached?.title)
            assertNull("Artwork must be null when all images are corrupt", cached?.coverThumbnailPath)
        }

    @Test
    fun getMetadataForPathsFlow_emitsIncrementalUpdates() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes(title = "Track A", artist = "Artist A", album = "Album A")
            fakeClient.stubFileBytes("/music/trackA.mp3", sampleMp3)

            val files =
                listOf(
                    RemoteFile(name = "trackA.mp3", path = "/music/trackA.mp3", size = 1000L),
                )

            val flow = repository.getMetadataForPathsFlow(1L, listOf("/music/trackA.mp3"))
            val initial = flow.first()
            assertTrue(initial.isEmpty())

            repository.resolveMetadata(testServer, files)

            val updated = flow.first()
            assertEquals(1, updated.size)
            assertEquals("Track A", updated[0].title)
        }

    @Test
    fun resolveMetadata_uses512kbRange_extractsEmbeddedCover() =
        runTest {
            val artwork =
                ByteArray(200 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[2] = 0xFF.toByte()
                    this[this.size - 2] = 0xFF.toByte()
                    this[this.size - 1] = 0xD9.toByte()
                }
            val sampleMp3 = buildSampleId3v2Bytes("Title", "Artist", "Album", artworkBytes = artwork)
            fakeClient.stubFileBytes("/music/test.mp3", sampleMp3)

            val file = RemoteFile(name = "test.mp3", path = "/music/test.mp3", size = sampleMp3.size.toLong() + 1000000L)
            repository.resolveMetadata(testServer, listOf(file))

            val initialRange = fakeClient.requestedRanges.firstOrNull { it.first == "/music/test.mp3" }
            assertNotNull(initialRange)
            assertEquals(0L, initialRange?.second)
            assertEquals(524287L, initialRange?.third)

            val cached = repository.getCachedMetadata(1L, "/music/test.mp3")
            assertNotNull(cached)
            assertEquals("/fake/covers/cover_1.jpg", cached?.coverThumbnailPath)
        }

    @Test
    fun resolveMetadata_oversizedId3Tag_fetchesSecondaryRange() =
        runTest {
            // Tag size ~800KB (> 512KB)
            val artwork =
                ByteArray(800 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[2] = 0xFF.toByte()
                    this[this.size - 2] = 0xFF.toByte()
                    this[this.size - 1] = 0xD9.toByte()
                }
            val sampleMp3 = buildSampleId3v2Bytes("Title", "Artist", "Album", artworkBytes = artwork)
            val totalFileSize = sampleMp3.size.toLong() + 2000000L
            fakeClient.stubFileBytes("/music/oversized.mp3", sampleMp3)

            val file = RemoteFile(name = "oversized.mp3", path = "/music/oversized.mp3", size = totalFileSize)
            repository.resolveMetadata(testServer, listOf(file))

            val ranges = fakeClient.requestedRanges.filter { it.first == "/music/oversized.mp3" }
            assertTrue("Expected secondary range request for oversized tag, but got: $ranges", ranges.size >= 2)
            assertEquals(0L, ranges[0].second)
            assertEquals(524287L, ranges[0].third)
            // Secondary request should start at 524288L
            assertEquals(524288L, ranges[1].second)

            val cached = repository.getCachedMetadata(1L, "/music/oversized.mp3")
            assertNotNull(cached)
            assertEquals("/fake/covers/cover_1.jpg", cached?.coverThumbnailPath)
            assertEquals("Title", cached?.title)
        }

    @Test
    fun resolveMetadata_oversizedTagExceedingCap_capsAtMaxCap() =
        runTest {
            // Tag size ~10MB (> 8MB cap)
            val artwork =
                ByteArray(10 * 1024 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[2] = 0xFF.toByte()
                    this[this.size - 2] = 0xFF.toByte()
                    this[this.size - 1] = 0xD9.toByte()
                }
            val sampleMp3 = buildSampleId3v2Bytes("Title", "Artist", "Album", artworkBytes = artwork)
            val totalFileSize = sampleMp3.size.toLong() + 2000000L
            fakeClient.stubFileBytes("/music/huge.mp3", sampleMp3)

            val file = RemoteFile(name = "huge.mp3", path = "/music/huge.mp3", size = totalFileSize)
            repository.resolveMetadata(testServer, listOf(file))

            val ranges = fakeClient.requestedRanges.filter { it.first == "/music/huge.mp3" }
            assertTrue(ranges.size >= 2)
            assertEquals(524288L, ranges[1].second)
            // End byte capped at 8MB - 1 (8388607L)
            assertEquals(8388607L, ranges[1].third)
        }

    @Test
    fun resolveMetadata_oversizedFlacPicture_fetchesSecondaryRange() =
        runTest {
            // FLAC with 700KB embedded PICTURE (> 512KB)
            val artwork =
                ByteArray(700 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[2] = 0xFF.toByte()
                    this[this.size - 2] = 0xFF.toByte()
                    this[this.size - 1] = 0xD9.toByte()
                }
            val sampleFlac = buildSampleFlacBytes("Flac Song", "Flac Artist", "Flac Album", artworkBytes = artwork)
            val totalFileSize = sampleFlac.size.toLong() + 2000000L
            fakeClient.stubFileBytes("/music/oversized.flac", sampleFlac)

            val file =
                RemoteFile(
                    name = "oversized.flac",
                    path = "/music/oversized.flac",
                    size = totalFileSize,
                    fileType =
                        com.webdav.player.domain.model.RemoteFileType
                            .Audio(com.webdav.player.domain.model.AudioFormat.FLAC),
                )
            repository.resolveMetadata(testServer, listOf(file))

            val ranges = fakeClient.requestedRanges.filter { it.first == "/music/oversized.flac" }
            assertTrue("Expected secondary range request for oversized FLAC tag, but got: $ranges", ranges.size >= 2)
            assertEquals(0L, ranges[0].second)
            assertEquals(524287L, ranges[0].third)
            assertEquals(524288L, ranges[1].second)

            val cached = repository.getCachedMetadata(1L, "/music/oversized.flac")
            assertNotNull(cached)
            assertEquals("/fake/covers/cover_1.jpg", cached?.coverThumbnailPath)
            assertEquals("Flac Song", cached?.title)
            assertEquals("Flac Artist", cached?.artist)
            assertEquals("Flac Album", cached?.album)
        }

    @Test
    fun resolveMetadata_offsetId3WithOversizedTag_whenServerIgnoresRangeOnSecondaryFetch_doesNotDuplicateStream() =
        runTest {
            val artwork =
                ByteArray(800 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[2] = 0xFF.toByte()
                    this[this.size - 2] = 0xFF.toByte()
                    this[this.size - 1] = 0xD9.toByte()
                }
            val rawId3Bytes = buildSampleId3v2Bytes("Padded Bohemian", "Queen", "Opera", artworkBytes = artwork)
            val prependedJunk = ByteArray(128) { 0x55.toByte() }
            val fullMp3Bytes =
                ByteArray(prependedJunk.size + rawId3Bytes.size + 10000).apply {
                    System.arraycopy(prependedJunk, 0, this, 0, prependedJunk.size)
                    System.arraycopy(rawId3Bytes, 0, this, prependedJunk.size, rawId3Bytes.size)
                }

            fakeClient.stubFileBytes("/music/padded_oversized.mp3", fullMp3Bytes)
            fakeClient.ignoreRangeOnSecondaryFetchPaths.add("/music/padded_oversized.mp3")

            val file =
                RemoteFile(
                    name = "padded_oversized.mp3",
                    path = "/music/padded_oversized.mp3",
                    size = fullMp3Bytes.size.toLong(),
                )
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/padded_oversized.mp3")
            assertNotNull("Metadata must be resolved and cached in Room", cached)
            assertEquals("Padded Bohemian", cached?.title)
            assertEquals("Queen", cached?.artist)
            assertEquals("Opera", cached?.album)
            assertNotNull("Cover artwork must be extracted cleanly without duplication corruption", cached?.coverThumbnailPath)
        }

    @Test
    fun resolveMetadata_noEmbeddedCover_fallsBackToFolderArtwork() =
        runTest {
            // MP3 with NO embedded artwork
            val sampleMp3 = buildSampleId3v2Bytes("Song 1", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/album/song1.mp3", sampleMp3)
            fakeClient.stubFileBytes("/music/album/song2.mp3", sampleMp3)

            val coverJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
            fakeClient.stubFileBytes("/music/album/cover.jpg", coverJpg)

            val files =
                listOf(
                    RemoteFile(name = "song1.mp3", path = "/music/album/song1.mp3", size = 1000L),
                    RemoteFile(name = "song2.mp3", path = "/music/album/song2.mp3", size = 1000L),
                )

            repository.resolveMetadata(testServer, files)

            val cached1 = repository.getCachedMetadata(1L, "/music/album/song1.mp3")
            val cached2 = repository.getCachedMetadata(1L, "/music/album/song2.mp3")
            assertNotNull(cached1?.coverThumbnailPath)
            assertNotNull(cached2?.coverThumbnailPath)
            assertEquals(cached1?.coverThumbnailPath, cached2?.coverThumbnailPath)

            // Verify folder cover was fetched once
            val coverFetches = fakeClient.requestedRanges.filter { it.first == "/music/album/cover.jpg" }
            assertEquals(1, coverFetches.size)
        }

    @Test
    fun resolveMetadata_trackSpecificArtwork_takesPrecedenceOverFolderArtwork() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Song A", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/album/songA.mp3", sampleMp3)

            val trackCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)
            val folderCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x02)
            fakeClient.stubFileBytes("/music/album/songA.jpg", trackCover)
            fakeClient.stubFileBytes("/music/album/cover.jpg", folderCover)

            val file = RemoteFile(name = "songA.mp3", path = "/music/album/songA.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/album/songA.mp3")
            assertNotNull(cached?.coverThumbnailPath)

            // Verify track specific cover was saved in storage
            assertTrue(fakeStorage.savedThumbnails.containsKey("1:/music/album/songA.jpg"))
        }

    @Test
    fun resolveSingleTrackMetadata_fallsBackToFolderArtwork() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Solo Song", "Solo Artist", "Solo Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/single/track.mp3", sampleMp3)

            val folderCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
            fakeClient.stubFileBytes("/music/single/cover.jpg", folderCover)

            val file = RemoteFile(name = "track.mp3", path = "/music/single/track.mp3", size = 1000L)
            val metadata = repository.resolveSingleTrackMetadata(testServer, file)

            assertNotNull(metadata.coverThumbnailPath)
            assertEquals("/fake/covers/cover_1.jpg", metadata.coverThumbnailPath)
        }

    @Test
    fun resolveMetadata_trackSpecificArtwork_prioritizedEvenAfterFolderArtworkCached() =
        runTest {
            val sampleMp3A = buildSampleId3v2Bytes("Song A", "Artist", "Album", artworkBytes = null)
            val sampleMp3B = buildSampleId3v2Bytes("Song B", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/album/songA.mp3", sampleMp3A)
            fakeClient.stubFileBytes("/music/album/songB.mp3", sampleMp3B)

            val folderCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x10)
            val trackCoverB = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x20)
            fakeClient.stubFileBytes("/music/album/cover.jpg", folderCover)
            fakeClient.stubFileBytes("/music/album/songB.jpg", trackCoverB)

            val fileA = RemoteFile(name = "songA.mp3", path = "/music/album/songA.mp3", size = 1000L)
            val fileB = RemoteFile(name = "songB.mp3", path = "/music/album/songB.mp3", size = 1000L)

            // 1. Resolve songA first: populates directory-level folderArtworkCache with cover.jpg
            repository.resolveMetadata(testServer, listOf(fileA))
            val cachedA = repository.getCachedMetadata(1L, "/music/album/songA.mp3")
            assertNotNull(cachedA?.coverThumbnailPath)
            assertTrue(fakeStorage.savedThumbnails.containsKey("1:/music/album/cover.jpg"))

            // 2. Resolve songB: has track-specific songB.jpg. It MUST NOT be shadowed by cached cover.jpg
            repository.resolveMetadata(testServer, listOf(fileB))
            val cachedB = repository.getCachedMetadata(1L, "/music/album/songB.mp3")
            assertNotNull(cachedB?.coverThumbnailPath)
            assertTrue(
                "Track-specific artwork songB.jpg must take precedence even if folder cover is cached",
                fakeStorage.savedThumbnails.containsKey("1:/music/album/songB.jpg"),
            )
        }

    @Test
    fun resolveMetadata_trackSpecificArtwork_supportsPngFallback() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Song C", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/album/songC.mp3", sampleMp3)

            val trackCoverPng = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A)
            fakeClient.stubFileBytes("/music/album/songC.png", trackCoverPng)

            val file = RemoteFile(name = "songC.mp3", path = "/music/album/songC.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/album/songC.mp3")
            assertNotNull(cached?.coverThumbnailPath)
            assertTrue(
                "Track-specific artwork must support .png candidate fallback",
                fakeStorage.savedThumbnails.containsKey("1:/music/album/songC.png"),
            )
        }

    @Test
    fun resolveMetadata_trackSpecificArtwork_supportsLiteralFilenameJpgFallback() =
        runTest {
            val sampleFlac = buildSampleFlacBytes("Song D", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/album/songD.flac", sampleFlac)

            val trackCoverJpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
            fakeClient.stubFileBytes("/music/album/songD.flac.jpg", trackCoverJpg)

            val file = RemoteFile(name = "songD.flac", path = "/music/album/songD.flac", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/album/songD.flac")
            assertNotNull(cached?.coverThumbnailPath)
            assertTrue(
                "Track-specific artwork must support literal \${filename}.jpg fallback",
                fakeStorage.savedThumbnails.containsKey("1:/music/album/songD.flac.jpg"),
            )
        }

    @Test
    fun resolveMetadata_noEmbeddedCover_fallsBackToFrontJpg() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Front Song", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/front_album/song.mp3", sampleMp3)

            val frontCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
            fakeClient.stubFileBytes("/music/front_album/front.jpg", frontCover)

            val file = RemoteFile(name = "song.mp3", path = "/music/front_album/song.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/front_album/song.mp3")
            assertNotNull("Folder artwork must fall back to front.jpg when cover/folder.jpg absent", cached?.coverThumbnailPath)
            assertTrue(
                "front.jpg must be saved to storage",
                fakeStorage.savedThumbnails.containsKey("1:/music/front_album/front.jpg"),
            )
        }

    @Test
    fun resolveMetadata_firstFolderCandidateAbsent_fallbackToNextCandidate() =
        runTest {
            val sampleMp3 = buildSampleId3v2Bytes("Fallback Track", "Artist", "Album", artworkBytes = null)
            fakeClient.stubFileBytes("/music/fallback/track.mp3", sampleMp3)

            // Only folder.jpg exists (no cover.jpg, no cover.png)
            val folderCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
            fakeClient.stubFileBytes("/music/fallback/folder.jpg", folderCover)

            val file = RemoteFile(name = "track.mp3", path = "/music/fallback/track.mp3", size = 1000L)
            repository.resolveMetadata(testServer, listOf(file))

            val cached = repository.getCachedMetadata(1L, "/music/fallback/track.mp3")
            assertNotNull("Metadata must be cached even if first folder candidates are absent", cached)
            assertNotNull("Folder artwork must be found via fallback to folder.jpg", cached?.coverThumbnailPath)
            assertTrue(
                "folder.jpg must have been saved to storage",
                fakeStorage.savedThumbnails.containsKey("1:/music/fallback/folder.jpg"),
            )
        }

    private fun buildSampleId3v2Bytes(
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray? = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
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

        // APIC picture
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
        artworkBytes: ByteArray? = null,
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        // "fLaC"
        stream.write(byteArrayOf(0x66, 0x4C, 0x61, 0x43))

        // Block 0: STREAMINFO (type 0, 34 bytes)
        val streamInfo = ByteArray(34)
        streamInfo[10] = 0x0A
        streamInfo[11] = 0xC4.toByte()
        streamInfo[12] = 0x40.toByte() // 44100 Hz
        val isLastVorbis = artworkBytes == null
        stream.write(0x00) // isLast = false, type = 0
        stream.write(0x00)
        stream.write(0x00)
        stream.write(34)
        stream.write(streamInfo)

        // Block 1: VORBIS_COMMENT (type 4)
        val vorbisBody = ByteArrayOutputStream()
        vorbisBody.write(0) // vendor length 0 (4 bytes LE)
        vorbisBody.write(0)
        vorbisBody.write(0)
        vorbisBody.write(0)

        val comments = listOf("TITLE=$title", "ARTIST=$artist", "ALBUM=$album")
        vorbisBody.write(comments.size and 0xFF)
        vorbisBody.write((comments.size shr 8) and 0xFF)
        vorbisBody.write((comments.size shr 16) and 0xFF)
        vorbisBody.write((comments.size shr 24) and 0xFF)

        for (c in comments) {
            val cBytes = c.toByteArray(StandardCharsets.UTF_8)
            vorbisBody.write(cBytes.size and 0xFF)
            vorbisBody.write((cBytes.size shr 8) and 0xFF)
            vorbisBody.write((cBytes.size shr 16) and 0xFF)
            vorbisBody.write((cBytes.size shr 24) and 0xFF)
            vorbisBody.write(cBytes)
        }

        val vorbisBytes = vorbisBody.toByteArray()
        val vorbisHeaderByte = if (isLastVorbis) 0x84 else 0x04
        stream.write(vorbisHeaderByte)
        stream.write((vorbisBytes.size shr 16) and 0xFF)
        stream.write((vorbisBytes.size shr 8) and 0xFF)
        stream.write(vorbisBytes.size and 0xFF)
        stream.write(vorbisBytes)

        // Block 2: PICTURE (type 6) if artworkBytes != null
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
            // pictureType = 3 (Front cover), 4 bytes BE
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            picBody.write(3)
            // mimeLength = 10, mime = "image/jpeg"
            val mime = "image/jpeg".toByteArray(StandardCharsets.US_ASCII)
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            picBody.write(mime.size)
            picBody.write(mime)
            // descLength = 0
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            // width, height, depth, colors (16 bytes)
            for (i in 0 until 16) picBody.write(0)
            // dataLength (4 bytes BE)
            picBody.write((validArtwork.size shr 24) and 0xFF)
            picBody.write((validArtwork.size shr 16) and 0xFF)
            picBody.write((validArtwork.size shr 8) and 0xFF)
            picBody.write(validArtwork.size and 0xFF)
            picBody.write(validArtwork)

            val picBytes = picBody.toByteArray()
            // isLast = true, type = 6 -> 0x86
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
                throw java.io.IOException("Simulated network timeout for $remotePath")
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
        var failSaving = false

        override suspend fun saveThumbnail(
            serverId: Long,
            remotePath: String,
            artworkBytes: ByteArray,
        ): String? {
            if (failSaving) return null
            savedThumbnails["$serverId:$remotePath"] = artworkBytes
            return "/fake/covers/cover_$serverId.jpg"
        }

        override fun getThumbnailFile(
            serverId: Long,
            remotePath: String,
        ): File? =
            if (savedThumbnails.containsKey("$serverId:$remotePath")) {
                File("/fake/covers/cover_$serverId.jpg")
            } else {
                null
            }

        override fun deleteThumbnail(
            serverId: Long,
            remotePath: String,
        ) {
            savedThumbnails.remove("$serverId:$remotePath")
        }

        override suspend fun deleteServerCovers(serverId: Long) {
            savedThumbnails.clear()
        }
    }
}
