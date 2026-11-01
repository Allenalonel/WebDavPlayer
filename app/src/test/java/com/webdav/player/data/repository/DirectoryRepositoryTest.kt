package com.webdav.player.data.repository

import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.DirectoryContent
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var fakeCueSheetResolver: FakeCueSheetResolver
    private lateinit var fakeTrackMetadataRepository: FakeTrackMetadataRepository
    private lateinit var repository: DirectoryRepositoryImpl

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "http://localhost",
            port = 8080,
        )

    private val secondServer =
        WebDavServer(
            id = 2L,
            name = "Second Server",
            url = "http://remotehost",
            port = 8080,
        )

    @Before
    fun setUp() {
        fakeClient = FakeWebDavClient()
        fakeDao = FakeDirectoryCacheDao()
        fakeCueSheetResolver = FakeCueSheetResolver()
        fakeTrackMetadataRepository = FakeTrackMetadataRepository()
        repository =
            DirectoryRepositoryImpl(
                webDavClient = fakeClient,
                directoryCacheDao = fakeDao,
                cueSheetResolver = fakeCueSheetResolver,
                trackMetadataRepository = fakeTrackMetadataRepository,
            )
    }

    @Test
    fun observeDirectoryContent_returnsCachedDirectory_whenCalledTwiceWithoutForceRefresh() =
        runTest {
            val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)

            repository.observeDirectoryContent(testServer, "/Music/").toList()
            val initialCalls = fakeClient.callCount["/Music/"] ?: 0

            val emissions = repository.observeDirectoryContent(testServer, "/Music/").toList()
            assertTrue(emissions.isNotEmpty())
            assertEquals(dir1, emissions.first().directory)
            assertEquals(initialCalls + 1, fakeClient.callCount["/Music/"])
        }

    @Test
    fun observeDirectoryContent_queriesClientAgain_whenForceRefreshIsTrue() =
        runTest {
            val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)

            repository.observeDirectoryContent(testServer, "/Music/").toList()
            val callsBefore = fakeClient.callCount["/Music/"] ?: 0

            val result2 = repository.observeDirectoryContent(testServer, "/Music/", forceRefresh = true).toList()
            assertTrue(result2.isNotEmpty())
            assertEquals(callsBefore + 1, fakeClient.callCount["/Music/"])
        }

    @Test
    fun clearCache_invalidatesCachedDirectories() =
        runTest {
            val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)

            repository.observeDirectoryContent(testServer, "/Music/").toList()
            val cachedBefore = repository.getCachedDirectory(testServer, "/Music/")
            assertNotNull(cachedBefore)

            repository.clearCache()
            assertNull(repository.getCachedDirectory(testServer, "/Music/"))
        }

    @Test
    fun observeDirectoryContent_doesNotCache_whenResultIsFailure() =
        runTest {
            fakeClient.results["/Music/"] = ListDirectoryResult.Failure("Network error")

            try {
                repository.observeDirectoryContent(testServer, "/Music/").toList()
            } catch (_: Exception) {
            }
            assertNull(repository.getCachedDirectory(testServer, "/Music/"))

            fakeClient.results["/Music/"] = ListDirectoryResult.Success(RemoteDirectory(path = "/Music/", name = "Music"))
            repository.observeDirectoryContent(testServer, "/Music/").toList()
            assertNotNull(repository.getCachedDirectory(testServer, "/Music/"))
        }

    @Test
    fun getCachedDirectory_restoresFromDatabase_whenMemoryCacheIsEmpty() =
        runTest {
            val originalDir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    subDirectories = listOf(RemoteDirectory(path = "/Music/Pop/", name = "Pop")),
                    files = listOf(RemoteFile(name = "test.mp3", path = "/Music/test.mp3", size = 12345)),
                )
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(originalDir)

            // First call populates memory and DB
            repository.observeDirectoryContent(testServer, "/Music/").toList()
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
        }

    @Test
    fun clearCacheForServer_removesOnlyTargetServer() =
        runTest {
            val dir1 = RemoteDirectory(path = "/Music/", name = "Music")
            val dir2 = RemoteDirectory(path = "/Jazz/", name = "Jazz")

            fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir1)
            fakeClient.results["/Jazz/"] = ListDirectoryResult.Success(dir2)

            repository.observeDirectoryContent(testServer, "/Music/").toList()
            repository.observeDirectoryContent(secondServer, "/Jazz/").toList()

            repository.clearCacheForServer(testServer.id)

            // testServer should be gone from DB & memory
            assertNull(repository.getCachedDirectory(testServer, "/Music/"))
            // secondServer should remain intact
            assertNotNull(repository.getCachedDirectory(secondServer, "/Jazz/"))
        }

    @Test
    fun serializerRoundTrip_preservesAllMetadata() {
        val original =
            RemoteDirectory(
                path = "/FLAC/Artist/",
                name = "Artist",
                subDirectories =
                    listOf(
                        RemoteDirectory(path = "/FLAC/Artist/Album1/", name = "Album1"),
                    ),
                files =
                    listOf(
                        RemoteFile(
                            name = "track01.flac",
                            path = "/FLAC/Artist/track01.flac",
                            size = 45000000L,
                            lastModified = "Mon, 25 Sep 2026 12:00:00 GMT",
                            contentType = "audio/flac",
                        ),
                        RemoteFile(
                            name = "track01.lrc",
                            path = "/FLAC/Artist/track01.lrc",
                            size = 1500L,
                            lastModified = null,
                            contentType = "text/plain",
                        ),
                    ),
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
    fun memoryCache_whenExceedingCapacity_evictsLruAndFallsBackToRoomL2() =
        runTest {
            // Insert 50 directories (/dir0/ to /dir49/)
            for (i in 0 until 50) {
                val dir = RemoteDirectory(path = "/dir$i/", name = "dir$i")
                fakeClient.results["/dir$i/"] = ListDirectoryResult.Success(dir)
                repository.observeDirectoryContent(testServer, "/dir$i/").toList()
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
            repository.observeDirectoryContent(testServer, "/dir50/").toList()

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
    fun observeDirectoryContent_withCacheMiss_emitsInitialEnriching_thenEmitsEnrichedFinished() =
        runTest {
            val audioFile = RemoteFile(name = "song.mp3", path = "/Music/song.mp3", size = 1000)
            val dir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(audioFile))
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir)

            val emissions = repository.observeDirectoryContent(testServer, "/Music/").toList()

            assertEquals(2, emissions.size)
            // First emission: remote directory fetched, isEnriching = true
            assertTrue(emissions[0].isEnriching)
            assertEquals(dir, emissions[0].directory)
            assertTrue(emissions[0].metadataMap.isEmpty())

            // Second emission: background metadata enriched, isEnriching = false
            assertFalse(emissions[1].isEnriching)
            assertEquals(dir, emissions[1].directory)
            assertEquals(1, emissions[1].metadataMap.size)
            assertEquals("song", emissions[1].metadataMap["/Music/song.mp3"]?.title)
            assertEquals(1, fakeTrackMetadataRepository.resolveCount)
            assertEquals(dir.files, fakeTrackMetadataRepository.lastDirectoryFiles)
        }

    @Test
    fun observeDirectoryContent_withCacheHitAndDiff_emitsCached_thenRemoteUpdate_thenEnrichedSnapshot() =
        runTest {
            val cachedFile = RemoteFile(name = "old.mp3", path = "/Music/old.mp3", size = 100)
            val cachedDir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(cachedFile))
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
            repository.observeDirectoryContent(testServer, "/Music/").toList()

            val freshFile1 = RemoteFile(name = "old.mp3", path = "/Music/old.mp3", size = 100)
            val freshFile2 = RemoteFile(name = "new.mp3", path = "/Music/new.mp3", size = 200)
            val freshDir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(freshFile1, freshFile2))
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(freshDir)

            val emissions = repository.observeDirectoryContent(testServer, "/Music/").toList()

            assertEquals(3, emissions.size)
            // 1st emission: cached dir at 0ms (isEnriching = true)
            assertTrue(emissions[0].isEnriching)
            assertEquals(cachedDir, emissions[0].directory)

            // 2nd emission: remote update received (isEnriching = true)
            assertTrue(emissions[1].isEnriching)
            assertEquals(freshDir, emissions[1].directory)

            // 3rd emission: enrichment finished (isEnriching = false)
            assertFalse(emissions[2].isEnriching)
            assertEquals(freshDir, emissions[2].directory)
            assertEquals(2, emissions[2].metadataMap.size)
        }

    @Test
    fun observeDirectoryContent_withCacheHitIdentical_emitsCached_thenEnrichedSnapshot() =
        runTest {
            val file = RemoteFile(name = "track.mp3", path = "/Music/track.mp3", size = 500)
            val dir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(file))
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir)
            repository.observeDirectoryContent(testServer, "/Music/").toList()

            val emissions = repository.observeDirectoryContent(testServer, "/Music/").toList()

            assertEquals(2, emissions.size)
            assertTrue(emissions[0].isEnriching)
            assertEquals(dir, emissions[0].directory)

            assertFalse(emissions[1].isEnriching)
            assertEquals(dir, emissions[1].directory)
            assertEquals(1, emissions[1].metadataMap.size)
        }

    @Test
    fun observeDirectoryContent_withCacheHit_whenRemoteFails_emitsCachedAndSuppressesError() =
        runTest {
            val file = RemoteFile(name = "offline.mp3", path = "/Music/offline.mp3", size = 800)
            val cachedDir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(file))
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
            repository.observeDirectoryContent(testServer, "/Music/").toList()

            // Remote now fails with network error
            fakeClient.results["/Music/"] = ListDirectoryResult.Failure("Network timeout", 408)

            val emissions = repository.observeDirectoryContent(testServer, "/Music/").toList()

            assertEquals(2, emissions.size)
            // 1st emission: cached directory (isEnriching = true)
            assertTrue(emissions[0].isEnriching)
            assertEquals(cachedDir, emissions[0].directory)

            // 2nd emission: cached directory retained, isEnriching set to false to dismiss spinner cleanly!
            assertFalse(emissions[1].isEnriching)
            assertEquals(cachedDir, emissions[1].directory)
        }

    @Test
    fun observeDirectoryContent_withCacheMiss_whenRemoteFails_throwsException() =
        runTest {
            fakeClient.results["/Music/"] = ListDirectoryResult.Failure("404 Not Found", 404)

            var caughtError: Throwable? = null
            repository
                .observeDirectoryContent(testServer, "/Music/")
                .catch { caughtError = it }
                .collect {}

            assertNotNull(caughtError)
            assertTrue(caughtError?.message?.contains("404") == true)
        }

    @Test
    fun observeDirectoryContent_withForceRefresh_bypassesCacheAndEnriches() =
        runTest {
            val cachedDir = RemoteDirectory(path = "/Music/", name = "Cached Music")
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(cachedDir)
            repository.observeDirectoryContent(testServer, "/Music/").toList()

            val freshFile = RemoteFile(name = "fresh.mp3", path = "/Music/fresh.mp3", size = 123)
            val freshDir = RemoteDirectory(path = "/Music/", name = "Fresh Music", files = listOf(freshFile))
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(freshDir)

            val emissions = repository.observeDirectoryContent(testServer, "/Music/", forceRefresh = true).toList()

            assertEquals(2, emissions.size)
            // 1st emission: freshDir directly without emitting stale cachedDir
            assertTrue(emissions[0].isEnriching)
            assertEquals(freshDir, emissions[0].directory)

            // 2nd emission: enriched freshDir
            assertFalse(emissions[1].isEnriching)
            assertEquals(freshDir, emissions[1].directory)
        }

    @Test
    fun observeDirectoryContent_withCueAlbums_initialEmitsPlaceholders_thenResolvesCueAlbums() =
        runTest {
            val cueFile = RemoteFile(name = "album.cue", path = "/Music/album.cue", size = 100)
            val audioFile = RemoteFile(name = "album.flac", path = "/Music/album.flac", size = 5000)
            val dir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(cueFile, audioFile))
            fakeClient.results["/Music/"] = ListDirectoryResult.Success(dir)

            val placeholder =
                CueAlbumItem(
                    cueFile = cueFile,
                    audioFile = audioFile,
                    tracks = emptyList(),
                    isExpanded = false,
                    isLoading = true,
                )
            fakeCueSheetResolver.initialAlbumsGenerator = { listOf(placeholder) }

            val cueItem =
                CueAlbumItem(
                    cueFile = cueFile,
                    audioFile = audioFile,
                    tracks =
                        listOf(
                            VirtualTrack(
                                trackNumber = 1,
                                title = "Track 1",
                                performer = "Artist",
                                startTimeMs = 0L,
                                endTimeMs = 180000L,
                                parentAudioPath = audioFile.path,
                            ),
                        ),
                    isExpanded = false,
                    isLoading = false,
                )
            fakeCueSheetResolver.resolvedAlbums.add(cueItem)

            val emissions = repository.observeDirectoryContent(testServer, "/Music/").toList()

            assertEquals(2, emissions.size)
            // First emission: initial placeholder generated via CueSheetResolver
            assertTrue(emissions[0].isEnriching)
            assertEquals(listOf(placeholder), emissions[0].cueAlbums)

            // Second emission: resolved CUE album
            val finalContent = emissions[1]
            assertFalse(finalContent.isEnriching)
            assertEquals(1, finalContent.cueAlbums.size)
            assertEquals(
                "album.cue",
                finalContent.cueAlbums
                    .first()
                    .cueFile.name,
            )
            assertEquals(
                1,
                finalContent.cueAlbums
                    .first()
                    .tracks.size,
            )
            assertEquals(
                "Track 1",
                finalContent.cueAlbums
                    .first()
                    .tracks
                    .first()
                    .title,
            )
        }

    private class FakeDirectoryCacheDao : DirectoryCacheDao {
        val cacheMap = mutableMapOf<String, DirectoryCacheEntity>()
        val getCacheCallCount = mutableMapOf<String, Int>()

        override suspend fun getCache(
            serverId: Long,
            path: String,
        ): DirectoryCacheEntity? {
            getCacheCallCount[path] = (getCacheCallCount[path] ?: 0) + 1
            return cacheMap["$serverId:$path"]
        }

        override suspend fun insertOrUpdate(entity: DirectoryCacheEntity) {
            cacheMap["${entity.serverId}:${entity.path}"] = entity
        }

        override suspend fun deleteCache(
            serverId: Long,
            path: String,
        ) {
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

        override fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<DirectoryCacheEntity>> =
            kotlinx.coroutines.flow.flowOf(cacheMap.values.toList())

        override fun searchDirectoryCacheFlow(
            query: String,
            serverId: Long?,
        ): kotlinx.coroutines.flow.Flow<List<DirectoryCacheEntity>> =
            kotlinx.coroutines.flow.flowOf(
                cacheMap.values.filter { entity ->
                    (serverId == null || entity.serverId == serverId) &&
                        (
                            entity.path.contains(query, ignoreCase = true) ||
                                entity.dataJson.contains(query, ignoreCase = true) ||
                                entity.dataJson.contains(".cue", ignoreCase = true)
                        )
                },
            )

        override fun getAllByServerIdFlow(serverId: Long): kotlinx.coroutines.flow.Flow<List<DirectoryCacheEntity>> =
            kotlinx.coroutines.flow.flowOf(cacheMap.values.filter { it.serverId == serverId })

        override suspend fun getAll(): List<DirectoryCacheEntity> = cacheMap.values.toList()

        override suspend fun getAllByServerId(serverId: Long): List<DirectoryCacheEntity> =
            cacheMap.values.filter { it.serverId == serverId }
    }

    private class FakeWebDavClient : WebDavClient {
        val results = mutableMapOf<String, ListDirectoryResult>()
        val callCount = mutableMapOf<String, Int>()

        override suspend fun testConnection(server: WebDavServer): ConnectionResult = ConnectionResult.Success

        override suspend fun listDirectory(
            server: WebDavServer,
            path: String,
        ): ListDirectoryResult {
            callCount[path] = (callCount[path] ?: 0) + 1
            return results[path] ?: ListDirectoryResult.Failure("Not found")
        }

        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long,
        ): ByteArray? = null
    }

    private class FakeTrackMetadataRepository : TrackMetadataRepository {
        val metadataStore = mutableMapOf<String, TrackMetadata>()
        var resolveCount = 0
        var lastDirectoryFiles: Collection<RemoteFile>? = null

        override suspend fun resolveMetadata(
            server: WebDavServer,
            files: List<RemoteFile>,
            forceRefresh: Boolean,
            directoryFiles: Collection<RemoteFile>?,
        ) {
            lastDirectoryFiles = directoryFiles
            resolveMetadata(server, files, forceRefresh)
        }

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> =
            flowOf(metadataStore.values.filter { it.serverId == serverId })

        override fun getMetadataForPathsFlow(
            serverId: Long,
            remotePaths: List<String>,
        ): Flow<List<TrackMetadata>> = flowOf(remotePaths.mapNotNull { metadataStore["$serverId:$it"] })

        override fun getMetadataFlow(
            serverId: Long,
            remotePath: String,
        ): Flow<TrackMetadata?> = flowOf(metadataStore["$serverId:$remotePath"])

        override suspend fun getCachedMetadata(
            serverId: Long,
            remotePath: String,
        ): TrackMetadata? = metadataStore["$serverId:$remotePath"]

        override suspend fun resolveMetadata(
            server: WebDavServer,
            files: List<RemoteFile>,
            forceRefresh: Boolean,
        ) {
            resolveCount++
            for (file in files) {
                if (file.isAudio && !metadataStore.containsKey("${server.id}:${file.path}")) {
                    metadataStore["${server.id}:${file.path}"] =
                        TrackMetadata(
                            serverId = server.id,
                            remotePath = file.path,
                            title = file.name.substringBeforeLast('.'),
                            artist = "Resolved Artist",
                            album = "Resolved Album",
                            durationMs = 180000L,
                        )
                }
            }
        }

        override suspend fun resolveSingleTrackMetadata(
            server: WebDavServer,
            file: RemoteFile,
        ): TrackMetadata =
            TrackMetadata(
                serverId = server.id,
                remotePath = file.path,
                title = file.name,
                durationMs = 180000L,
            )
    }

    private class FakeCueSheetResolver : CueSheetResolver {
        val resolvedAlbums = mutableListOf<CueAlbumItem>()
        var initialAlbumsGenerator: ((RemoteDirectory) -> List<CueAlbumItem>)? = null

        override fun resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem> =
            initialAlbumsGenerator?.invoke(directory) ?: emptyList()

        override suspend fun resolveDirectoryCueAlbums(
            server: WebDavServer,
            directory: RemoteDirectory,
            parentDurationsByPath: Map<String, Long>,
        ): List<CueAlbumItem> = resolvedAlbums

        override suspend fun resolveVirtualTracks(
            server: WebDavServer,
            cuePath: String,
            parentAudioPath: String,
            totalDurationMs: Long?,
        ): List<VirtualTrack> = emptyList()

        override fun clearCache() {}
    }
}
