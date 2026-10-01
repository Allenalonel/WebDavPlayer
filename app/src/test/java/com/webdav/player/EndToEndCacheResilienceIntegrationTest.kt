package com.webdav.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorageImpl
import com.webdav.player.data.player.DefaultWebDavMediaSourceAdapter
import com.webdav.player.data.player.Media3AudioPlayerEngine
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.DirectoryRepositoryImpl
import com.webdav.player.data.repository.LyricsRepositoryImpl
import com.webdav.player.data.repository.ServerRepositoryImpl
import com.webdav.player.data.repository.TrackMetadataRepositoryImpl
import com.webdav.player.data.service.PlaybackSessionHost
import com.webdav.player.data.service.WebDavNotificationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.session.FakePlaybackSessionStore
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl
import com.webdav.player.ui.browser.DirectoryBrowserViewModel
import com.webdav.player.ui.common.ThumbnailMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * End-to-end integration tests verifying cache invalidation, disk eviction recovery,
 * harmonized directory refresh lifecycle, and dead artwork URI protection.
 *
 * Covers requirements from Issue 05:
 * 1. Simulating app cache clearance and verifying automatic progressive thumbnail
 *    self-healing during directory browsing.
 * 2. Verifying pull-to-refresh correctly re-probes remote folder artwork and updates UI rows.
 * 3. Verifying resuming playback with cleared cache does not emit FileNotFoundException
 *    to system media listeners.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EndToEndCacheResilienceIntegrationTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: AppDatabase
    private lateinit var coverArtStorage: CoverArtStorageImpl
    private lateinit var webDavClient: OkHttpWebDavClient
    private lateinit var serverRepository: ServerRepositoryImpl
    private lateinit var directoryRepository: DirectoryRepositoryImpl
    private lateinit var mediaSourceAdapter: DefaultWebDavMediaSourceAdapter
    private lateinit var trackMetadataRepository: TrackMetadataRepositoryImpl
    private lateinit var lyricsRepository: LyricsRepositoryImpl
    private lateinit var sessionStore: FakePlaybackSessionStore
    private lateinit var testServer: WebDavServer

    private var previousHost: PlaybackSessionHost? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        previousHost = PlaybackSessionHost.currentInstanceForTesting()

        mockWebServer = MockWebServer()
        mockWebServer.start()

        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        coverArtStorage = CoverArtStorageImpl(context)
        webDavClient = OkHttpWebDavClient()
        serverRepository = ServerRepositoryImpl(database.webDavServerDao(), coverArtStorage, webDavClient)
        directoryRepository = DirectoryRepositoryImpl(webDavClient, database.directoryCacheDao())
        mediaSourceAdapter = DefaultWebDavMediaSourceAdapter(context, webDavClient)
        sessionStore = FakePlaybackSessionStore()

        trackMetadataRepository =
            TrackMetadataRepositoryImpl(
                trackMetadataDao = database.trackMetadataDao(),
                webDavClient = webDavClient,
                coverArtStorage = coverArtStorage,
                webDavServerDao = database.webDavServerDao(),
                ioDispatcher = testDispatcher,
            )
        lyricsRepository =
            LyricsRepositoryImpl(
                webDavClient = webDavClient,
                trackMetadataRepository = trackMetadataRepository,
                ioDispatcher = testDispatcher,
            )

        testServer =
            WebDavServer(
                id = 0L,
                name = "Cache Resilience WebDAV",
                url = mockWebServer.url("/dav").toString(),
                port = mockWebServer.port,
                pathPrefix = "/dav",
                username = "resilience_user",
                password = "resilience_password",
                allowSelfSigned = true,
                isDefault = true,
            )
        val savedId = kotlinx.coroutines.runBlocking { serverRepository.saveServer(testServer) }
        testServer = testServer.copy(id = savedId)
    }

    @After
    fun tearDown() {
        ThumbnailMemoryCache.clear()
        PlaybackSessionHost.resetForTesting()
        PlaybackSessionHost.setInstanceForTesting(previousHost)
        database.close()
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    /**
     * Requirement 1:
     * Integration test simulates app cache clearance and verifies automatic
     * progressive thumbnail self-healing during directory browsing.
     *
     * Flow:
     * 1. Audio tracks in /Music/Classics/ have embedded cover artwork.
     * 2. Initial resolution persists metadata in Room DB and writes thumbnails to disk cache.
     * 3. Simulate user clearing app cache in system settings: thumbnail files on disk are wiped,
     *    while Room database entities remain intact.
     * 4. User re-launches app / browses /Music/Classics/:
     *    DirectoryBrowserViewModel triggers progressive metadata resolution with forceRefresh = false.
     * 5. TrackMetadataRepository detects physical thumbnail absence on disk and schedules self-healing.
     * 6. CoverArtStorage recreates missing directory structure, writes new thumbnails to disk,
     *    updates Room DB, and DirectoryBrowserViewModel.uiState.metadataMap progressively updates.
     * 7. Verifies zero FileNotFoundException occurs, thumbnails are valid non-empty files,
     *    and can be decoded into ThumbnailMemoryCache.
     */
    @Test
    fun e2e_appCacheClearance_automaticProgressiveThumbnailSelfHealing_duringDirectoryBrowsing() =
        runTest(testDispatcher) {
            val sampleJpeg = createTestJpegBytes()

            val flacBytes =
                buildSampleFlacBytes(
                    title = "Symphony No. 5",
                    artist = "Ludwig van Beethoven",
                    album = "Classical Masterpieces",
                    artworkBytes = sampleJpeg,
                )
            val mp3Bytes =
                buildSampleId3v2Bytes(
                    title = "Allegro Molto",
                    artist = "Wolfgang Amadeus Mozart",
                    album = "Classical Masterpieces",
                    artworkBytes = sampleJpeg,
                )

            val directoryXml =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/dav/Music/Classics/</D:href>
                    <D:propstat>
                      <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/Classics/01-Symphony.flac</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/flac</D:getcontenttype>
                        <D:getcontentlength>${flacBytes.size}</D:getcontentlength>
                        <D:getlastmodified>Sun, 27 Sep 2026 12:00:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/Classics/02-Allegro.mp3</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/mpeg</D:getcontenttype>
                        <D:getcontentlength>${mp3Bytes.size}</D:getcontentlength>
                        <D:getlastmodified>Sun, 27 Sep 2026 12:05:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent()

            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path?.substringBefore('?') ?: ""
                        val range = request.getHeader("Range")
                        return when {
                            path == "/dav/Music/Classics/" -> {
                                MockResponse()
                                    .setResponseCode(207)
                                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                                    .setBody(directoryXml)
                            }

                            path == "/dav/Music/Classics/01-Symphony.flac" -> {
                                createRangeMockResponse(flacBytes, range)
                            }

                            path == "/dav/Music/Classics/02-Allegro.mp3" -> {
                                createRangeMockResponse(mp3Bytes, range)
                            }

                            else -> {
                                MockResponse().setResponseCode(404)
                            }
                        }
                    }
                }

            val flacFile =
                RemoteFile(
                    name = "01-Symphony.flac",
                    path = "/Music/Classics/01-Symphony.flac",
                    size = flacBytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val mp3File =
                RemoteFile(
                    name = "02-Allegro.mp3",
                    path = "/Music/Classics/02-Allegro.mp3",
                    size = mp3Bytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val directory =
                RemoteDirectory(
                    path = "/Music/Classics/",
                    name = "Classics",
                    files = listOf(flacFile, mp3File),
                )

            // Step 1: Initial resolution populates Room DB and writes thumbnails to disk
            val initialList = directoryRepository.listDirectory(testServer, "/Music/Classics/", forceRefresh = true)
            assertTrue("Initial directory listing must succeed", initialList is ListDirectoryResult.Success)

            trackMetadataRepository.resolveMetadata(testServer, directory.files, forceRefresh = false)
            advanceUntilIdle()

            val initialMeta1 = trackMetadataRepository.getCachedMetadata(testServer.id, flacFile.path)
            val initialMeta2 = trackMetadataRepository.getCachedMetadata(testServer.id, mp3File.path)
            assertNotNull("Flac metadata must be cached in Room", initialMeta1)
            assertNotNull("Mp3 metadata must be cached in Room", initialMeta2)

            val initialThumb1 = initialMeta1!!.coverThumbnailPath
            val initialThumb2 = initialMeta2!!.coverThumbnailPath
            assertNotNull("Flac initial cover thumbnail path must not be null", initialThumb1)
            assertNotNull("Mp3 initial cover thumbnail path must not be null", initialThumb2)
            assertTrue("Flac initial thumbnail file must exist on disk", File(initialThumb1!!).exists())
            assertTrue("Mp3 initial thumbnail file must exist on disk", File(initialThumb2!!).exists())
            assertTrue("Storage layer confirms initial thumbnail 1 is valid", coverArtStorage.isValidThumbnailFile(initialThumb1))
            assertTrue("Storage layer confirms initial thumbnail 2 is valid", coverArtStorage.isValidThumbnailFile(initialThumb2))

            // Step 2: Simulate Android OS / User Cache Clearance in System Settings.
            // All thumbnail files and covers directory in cacheDir are wiped out,
            // while Room database and ServerRepository records remain intact.
            val coversDir = File(context.cacheDir, "covers")
            assertTrue("Covers directory must exist before purge", coversDir.exists())
            coversDir.deleteRecursively()
            assertFalse("Covers directory must be deleted after cache clearance", coversDir.exists())
            assertFalse("Thumbnail 1 file must no longer exist on disk", File(initialThumb1).exists())
            assertFalse("Thumbnail 2 file must no longer exist on disk", File(initialThumb2).exists())
            assertFalse(
                "isValidThumbnailFile must report false for purged thumbnail 1",
                coverArtStorage.isValidThumbnailFile(initialThumb1),
            )
            assertFalse(
                "isValidThumbnailFile must report false for purged thumbnail 2",
                coverArtStorage.isValidThumbnailFile(initialThumb2),
            )

            // Confirm Room database still preserves the metadata entities with dead paths
            val preservedEntity1 = database.trackMetadataDao().getMetadata(testServer.id, flacFile.path)
            assertNotNull("Room DB must still preserve flac metadata record", preservedEntity1)
            assertEquals(initialThumb1, preservedEntity1?.coverThumbnailPath)

            // Deepened TrackMetadataRepository guarantees non-null thumbnails are valid on disk; returns null when cleared
            val sanitizedMeta1 = trackMetadataRepository.getCachedMetadata(testServer.id, flacFile.path)
            assertNotNull("Repository returns metadata", sanitizedMeta1)
            assertNull("Repository must sanitize missing thumbnail to null", sanitizedMeta1?.coverThumbnailPath)

            // Step 3: User launches app and browses /Music/Classics/
            // DirectoryBrowserViewModel initializes and observes directory & metadata
            val engine =
                Media3AudioPlayerEngine(
                    context = context,
                    mediaSourceAdapter = mediaSourceAdapter,
                    coroutineScope = this,
                )
            val sessionScope = TestScope(testDispatcher)
            val appSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine,
                    serverRepository = serverRepository,
                    trackMetadataRepository = trackMetadataRepository,
                    lyricsRepository = lyricsRepository,
                    sessionStore = sessionStore,
                    coroutineScope = sessionScope,
                    progressDispatcher = testDispatcher,
                )

            appSession.setActiveServer(testServer)
            appSession.setCurrentDirectoryPath("/Music/Classics/")
            advanceUntilIdle()

            val viewModel =
                DirectoryBrowserViewModel(
                    serverRepository = serverRepository,
                    directoryRepository = directoryRepository,
                    musicPlayerAppSession = appSession,
                    trackMetadataRepository = trackMetadataRepository,
                )

            // Collect UI state updates
            val collectedStates = mutableListOf<com.webdav.player.ui.browser.DirectoryBrowserUiState>()
            val stateCollectorJob = launch { viewModel.uiState.toList(collectedStates) }

            advanceUntilIdle()

            // Wait for progressive metadata resolution to complete over WebDAV
            var attempts = 0
            while (attempts < 100) {
                val currentMeta1 = viewModel.uiState.value.metadataMap[flacFile.path]
                val currentMeta2 = viewModel.uiState.value.metadataMap[mp3File.path]
                val file1Exists = currentMeta1?.coverThumbnailPath?.let { File(it).exists() } == true
                val file2Exists = currentMeta2?.coverThumbnailPath?.let { File(it).exists() } == true
                if (file1Exists && file2Exists) {
                    break
                }
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                attempts++
            }

            val finalState = viewModel.uiState.value
            assertNotNull("Directory must be loaded", finalState.currentDirectory)
            assertEquals(2, finalState.currentDirectory?.files?.size)
            assertFalse("Loading indicator must be dismissed", finalState.isLoading)
            assertFalse("Refreshing indicator must be dismissed", finalState.isRefreshing)
            assertNull("Error message must be null", finalState.errorMessage)

            // Step 4: Verify automatic progressive thumbnail self-healing
            val healedMeta1 = finalState.metadataMap[flacFile.path]
            val healedMeta2 = finalState.metadataMap[mp3File.path]
            assertNotNull("Flac track must have healed metadata in UI state", healedMeta1)
            assertNotNull("Mp3 track must have healed metadata in UI state", healedMeta2)

            val healedThumb1 = healedMeta1?.coverThumbnailPath
            val healedThumb2 = healedMeta2?.coverThumbnailPath
            assertNotNull("Flac healed thumbnail path must not be null", healedThumb1)
            assertNotNull("Mp3 healed thumbnail path must not be null", healedThumb2)

            // Step 5: Verify files were recreated on disk in dynamically created storage directory
            val healedFile1 = File(healedThumb1!!)
            val healedFile2 = File(healedThumb2!!)
            assertTrue("Healed thumbnail 1 file must physically exist on disk", healedFile1.exists())
            assertTrue("Healed thumbnail 1 file must be non-empty", healedFile1.length() > 0)
            assertTrue("Healed thumbnail 2 file must physically exist on disk", healedFile2.exists())
            assertTrue("Healed thumbnail 2 file must be non-empty", healedFile2.length() > 0)

            assertTrue("Storage layer confirms healed thumbnail 1 is valid", coverArtStorage.isValidThumbnailFile(healedThumb1))
            assertTrue("Storage layer confirms healed thumbnail 2 is valid", coverArtStorage.isValidThumbnailFile(healedThumb2))

            // Step 6: Verify bitmap rendering into ThumbnailMemoryCache with zero exceptions
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val bitmap1 = BitmapFactory.decodeFile(healedFile1.absolutePath, options)
            if (bitmap1 != null) {
                ThumbnailMemoryCache.put(healedThumb1, bitmap1.asImageBitmap())
                assertNotNull("ThumbnailMemoryCache must store and retrieve decoded artwork", ThumbnailMemoryCache.get(healedThumb1))
            } else {
                assertTrue("Thumbnail file must contain uncorrupted bytes", healedFile1.length() >= 64)
            }

            stateCollectorJob.cancel()
            appSession.release()
            engine.release()
        }

    /**
     * Requirement 2:
     * Integration test verifies that pull-to-refresh correctly re-probes remote folder
     * artwork, clears in-memory negative cache sentinels, maintains the isRefreshing
     * indicator throughout the entire lifecycle, and updates UI rows with the new cover art.
     */
    @Test
    fun e2e_pullToRefresh_clearsNegativeCache_reProbesRemoteFolderArtwork_andUpdatesUiRows() =
        runTest(testDispatcher) {
            val audioBytes1 =
                buildSampleId3v2Bytes(
                    title = "Acoustic Melody 1",
                    artist = "Soloist",
                    album = "Unplugged Sessions",
                    artworkBytes = null, // No embedded artwork
                )
            val audioBytes2 =
                buildSampleFlacBytes(
                    title = "Acoustic Melody 2",
                    artist = "Soloist",
                    album = "Unplugged Sessions",
                    artworkBytes = null, // No embedded artwork
                )

            val folderCoverJpeg = createTestJpegBytes()

            val directoryXml =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/dav/Music/Acoustic/</D:href>
                    <D:propstat>
                      <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/Acoustic/01-Track.mp3</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/mpeg</D:getcontenttype>
                        <D:getcontentlength>${audioBytes1.size}</D:getcontentlength>
                        <D:getlastmodified>Mon, 28 Sep 2026 10:00:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/Acoustic/02-Track.flac</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/flac</D:getcontenttype>
                        <D:getcontentlength>${audioBytes2.size}</D:getcontentlength>
                        <D:getlastmodified>Mon, 28 Sep 2026 10:05:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent()

            var remoteCoverAvailable = false

            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path?.substringBefore('?') ?: ""
                        val range = request.getHeader("Range")
                        return when {
                            path == "/dav/Music/Acoustic/" -> {
                                MockResponse()
                                    .setResponseCode(207)
                                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                                    .setBody(directoryXml)
                            }

                            path == "/dav/Music/Acoustic/01-Track.mp3" -> {
                                createRangeMockResponse(audioBytes1, range)
                            }

                            path == "/dav/Music/Acoustic/02-Track.flac" -> {
                                createRangeMockResponse(audioBytes2, range)
                            }

                            path == "/dav/Music/Acoustic/cover.jpg" || path == "/dav/Music/Acoustic/folder.jpg" -> {
                                if (remoteCoverAvailable) {
                                    MockResponse()
                                        .setResponseCode(200)
                                        .setHeader("Content-Type", "image/jpeg")
                                        .setHeader("Content-Length", folderCoverJpeg.size.toString())
                                        .setBody(Buffer().write(folderCoverJpeg))
                                } else {
                                    MockResponse().setResponseCode(404)
                                }
                            }

                            else -> MockResponse().setResponseCode(404)
                        }
                    }
                }

            val file1 =
                RemoteFile(
                    name = "01-Track.mp3",
                    path = "/Music/Acoustic/01-Track.mp3",
                    size = audioBytes1.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val file2 =
                RemoteFile(
                    name = "02-Track.flac",
                    path = "/Music/Acoustic/02-Track.flac",
                    size = audioBytes2.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val directory =
                RemoteDirectory(
                    path = "/Music/Acoustic/",
                    name = "Acoustic",
                    files = listOf(file1, file2),
                )

            // Step 1: Browse directory initially while remote cover.jpg does not exist.
            // Negative caching stores NO_FOLDER_ARTWORK_SENTINEL.
            val initialList = directoryRepository.listDirectory(testServer, "/Music/Acoustic/", forceRefresh = true)
            assertTrue("Initial directory listing must succeed", initialList is ListDirectoryResult.Success)

            trackMetadataRepository.resolveMetadata(testServer, directory.files, forceRefresh = false)
            advanceUntilIdle()

            val initialMeta1 = trackMetadataRepository.getCachedMetadata(testServer.id, file1.path)
            val initialMeta2 = trackMetadataRepository.getCachedMetadata(testServer.id, file2.path)
            assertNotNull("Initial meta 1 cached", initialMeta1)
            assertNotNull("Initial meta 2 cached", initialMeta2)
            assertNull("Initial meta 1 has null cover thumbnail", initialMeta1?.coverThumbnailPath)
            assertNull("Initial meta 2 has null cover thumbnail", initialMeta2?.coverThumbnailPath)

            // Step 2: Initialize DirectoryBrowserViewModel
            val engine =
                Media3AudioPlayerEngine(
                    context = context,
                    mediaSourceAdapter = mediaSourceAdapter,
                    coroutineScope = this,
                )
            val sessionScope = TestScope(testDispatcher)
            val appSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine,
                    serverRepository = serverRepository,
                    trackMetadataRepository = trackMetadataRepository,
                    lyricsRepository = lyricsRepository,
                    sessionStore = sessionStore,
                    coroutineScope = sessionScope,
                    progressDispatcher = testDispatcher,
                )

            appSession.setActiveServer(testServer)
            appSession.setCurrentDirectoryPath("/Music/Acoustic/")
            advanceUntilIdle()

            val viewModel =
                DirectoryBrowserViewModel(
                    serverRepository = serverRepository,
                    directoryRepository = directoryRepository,
                    musicPlayerAppSession = appSession,
                    trackMetadataRepository = trackMetadataRepository,
                )

            val collectedStates = mutableListOf<com.webdav.player.ui.browser.DirectoryBrowserUiState>()
            val stateCollectorJob = launch { viewModel.uiState.toList(collectedStates) }

            advanceUntilIdle()

            // Verify initial UI state has no artwork
            val stateBeforeRefresh = viewModel.uiState.value
            assertEquals("/Music/Acoustic/", stateBeforeRefresh.currentPath)
            assertFalse("isRefreshing must be false before user refresh", stateBeforeRefresh.isRefreshing)
            assertNull(stateBeforeRefresh.metadataMap[file1.path]?.coverThumbnailPath)
            assertNull(stateBeforeRefresh.metadataMap[file2.path]?.coverThumbnailPath)

            // Step 3: Now the remote server gets cover.jpg added!
            remoteCoverAvailable = true

            // User triggers pull-to-refresh
            viewModel.onRefresh()
            testDispatcher.scheduler.runCurrent()

            // Verify isRefreshing immediately becomes true
            assertTrue("isRefreshing must be active immediately after pull-to-refresh gesture", viewModel.uiState.value.isRefreshing)

            // Wait for pull-to-refresh and metadata re-resolution to finish
            var attempts = 0
            while (attempts < 100) {
                val currentMeta1 = viewModel.uiState.value.metadataMap[file1.path]
                val currentMeta2 = viewModel.uiState.value.metadataMap[file2.path]
                val file1HasArt = currentMeta1?.coverThumbnailPath?.let { File(it).exists() } == true
                val file2HasArt = currentMeta2?.coverThumbnailPath?.let { File(it).exists() } == true
                if (file1HasArt && file2HasArt && !viewModel.uiState.value.isRefreshing) {
                    break
                }
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                attempts++
            }

            val refreshedState = viewModel.uiState.value
            assertFalse("isRefreshing must transition to false once both directory and metadata complete", refreshedState.isRefreshing)
            assertFalse("isLoading must be false", refreshedState.isLoading)
            assertNull("No error message on successful refresh", refreshedState.errorMessage)

            // Step 4: Verify UI row metadata updated with re-probed folder cover
            val refreshedMeta1 = refreshedState.metadataMap[file1.path]
            val refreshedMeta2 = refreshedState.metadataMap[file2.path]
            assertNotNull("Refreshed meta 1 must exist", refreshedMeta1)
            assertNotNull("Refreshed meta 2 must exist", refreshedMeta2)

            val coverPath1 = refreshedMeta1?.coverThumbnailPath
            val coverPath2 = refreshedMeta2?.coverThumbnailPath
            assertNotNull("Track 1 must have resolved folder artwork after refresh", coverPath1)
            assertNotNull("Track 2 must have resolved folder artwork after refresh", coverPath2)
            assertEquals("Both tracks must share the same folder artwork thumbnail", coverPath1, coverPath2)

            val coverFile = File(coverPath1!!)
            assertTrue("Folder cover thumbnail must physically exist on disk", coverFile.exists())
            assertTrue("Folder cover thumbnail must be non-empty", coverFile.length() > 0)
            assertTrue("Storage layer confirms folder cover thumbnail is valid", coverArtStorage.isValidThumbnailFile(coverPath1))

            stateCollectorJob.cancel()
            appSession.release()
            engine.release()
        }

    /**
     * Requirement 3:
     * Integration test verifies that resuming playback with a cleared cache does not emit
     * FileNotFoundException to system media listeners, presents clean fallbacks to the
     * notification shade, and transparently heals artwork in the background.
     */
    @Test
    fun e2e_resumingPlayback_withClearedCache_doesNotEmitFileNotFoundException_andHealsArtworkInBackground() =
        runTest(testDispatcher) {
            val sampleJpeg = createTestJpegBytes()

            val flacBytes =
                buildSampleFlacBytes(
                    title = "Symphony No. 5",
                    artist = "Ludwig van Beethoven",
                    album = "Classical Masterpieces",
                    artworkBytes = sampleJpeg,
                )

            val directoryXml =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/dav/Music/Classics/</D:href>
                    <D:propstat>
                      <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/Classics/01-Symphony.flac</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/flac</D:getcontenttype>
                        <D:getcontentlength>${flacBytes.size}</D:getcontentlength>
                        <D:getlastmodified>Sun, 27 Sep 2026 12:00:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent()

            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path?.substringBefore('?') ?: ""
                        val range = request.getHeader("Range")
                        return when {
                            path == "/dav/Music/Classics/" -> {
                                MockResponse()
                                    .setResponseCode(207)
                                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                                    .setBody(directoryXml)
                            }

                            path == "/dav/Music/Classics/01-Symphony.flac" -> {
                                createRangeMockResponse(flacBytes, range)
                            }

                            else -> MockResponse().setResponseCode(404)
                        }
                    }
                }

            val flacFile =
                RemoteFile(
                    name = "01-Symphony.flac",
                    path = "/Music/Classics/01-Symphony.flac",
                    size = flacBytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )

            // Step 1: Initial resolution persists metadata and writes thumbnail file to disk
            val initialThumbPath = coverArtStorage.saveThumbnail(testServer.id, flacFile.path, sampleJpeg)
            assertNotNull("Initial thumbnail must be written", initialThumbPath)
            assertTrue("Initial thumbnail file must exist on disk", File(initialThumbPath!!).exists())
            assertTrue(coverArtStorage.isValidThumbnailFile(initialThumbPath))

            val initialTrack =
                AudioTrack(
                    id = "${testServer.id}:${flacFile.path}",
                    serverId = testServer.id,
                    remotePath = flacFile.path,
                    title = "Symphony No. 5",
                    artist = "Ludwig van Beethoven",
                    album = "Classical Masterpieces",
                    durationMs = 240000L,
                    format = AudioFormat.FLAC,
                    coverThumbnailPath = initialThumbPath,
                )

            // Persist initial metadata in Room DB
            trackMetadataRepository.resolveMetadata(testServer, listOf(flacFile), forceRefresh = false)
            advanceUntilIdle()

            // Save playback session pointing to initial thumbnail path
            val savedSessionData =
                PlaybackSessionData(
                    activeServerId = testServer.id,
                    currentDirectoryPath = "/Music/Classics/",
                    queueTracks = listOf(initialTrack),
                    currentTrackIndex = 0,
                    positionMs = 35000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    serverLastDirectories = mapOf(testServer.id to "/Music/Classics/"),
                )
            sessionStore.saveSession(savedSessionData)

            // Step 2: Simulate OS or User clearing application cache in system settings.
            // The thumbnail file on disk is deleted, but DataStore session and Room DB remain intact.
            val coversDir = File(context.cacheDir, "covers")
            coversDir.deleteRecursively()
            assertFalse("Thumbnail file must be evicted from disk", File(initialThumbPath).exists())
            assertFalse("Storage reports thumbnail as invalid", coverArtStorage.isValidThumbnailFile(initialThumbPath))

            // Step 3: Setup AudioPlayerEngine, WebDavNotificationProvider, and MediaSession
            val engine =
                Media3AudioPlayerEngine(
                    context = context,
                    mediaSourceAdapter = mediaSourceAdapter,
                    coroutineScope = this,
                )

            val notificationProvider = WebDavNotificationProvider(context)
            val mediaSession = MediaSession.Builder(context, engine.player).setId("resilience_test_session").build()

            var listenerException: Throwable? = null
            engine.player.addListener(
                object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        listenerException = error
                    }
                },
            )

            // Step 4: Launch MusicPlayerAppSessionImpl to trigger cold start session resumption
            val sessionJob = SupervisorJob()
            val sessionScope = kotlinx.coroutines.CoroutineScope(sessionJob + testDispatcher)
            val appSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine,
                    serverRepository = serverRepository,
                    trackMetadataRepository = trackMetadataRepository,
                    lyricsRepository = lyricsRepository,
                    sessionStore = sessionStore,
                    coroutineScope = sessionScope,
                    coverArtStorage = coverArtStorage,
                    progressDispatcher = testDispatcher,
                )

            advanceUntilIdle()

            var restoreAttempts = 0
            while (!appSession.isRestored.value && restoreAttempts < 50) {
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                restoreAttempts++
            }

            // Verify session is restored
            assertTrue("App session must be restored", appSession.isRestored.value)
            val restoredState = appSession.sessionState.value
            assertTrue("App session has active track", restoredState.hasTrack)
            assertEquals("Symphony No. 5", restoredState.currentTrack?.title)

            // CRITICAL VERIFICATION:
            // Dead artwork URI must be sanitized to null during restoration, avoiding FileNotFoundException!
            assertNull("Dead artwork URI must be stripped to null on restored track", restoredState.currentTrack?.coverThumbnailPath)

            // Start/resume playback with the restored track
            appSession.togglePlayPause()
            testDispatcher.scheduler.runCurrent()

            // Verify system media item metadata does NOT contain dead file URI
            val currentMediaItem = engine.player.currentMediaItem
            val currentArtworkUri = currentMediaItem?.mediaMetadata?.artworkUri
            assertNull("Current media item artwork URI must be null when restored from evicted cache", currentArtworkUri)

            // Verify system notification builds cleanly without throwing FileNotFoundException
            val initialNotification = notificationProvider.buildNotification(mediaSession)
            assertNotNull("Notification must build cleanly", initialNotification)
            assertEquals(com.webdav.player.R.drawable.ic_notification_playback, initialNotification.smallIcon.resId)
            assertNull("Zero unhandled listener exceptions should occur", listenerException)

            // Step 5: Wait for transparent background self-healing to enrich restored track
            var attempts = 0
            while (attempts < 100) {
                val currentTrack = appSession.sessionState.value.currentTrack
                val thumb = currentTrack?.coverThumbnailPath
                if (thumb != null && File(thumb).exists()) {
                    break
                }
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                attempts++
            }

            // Step 6: Verify healed artwork
            val healedTrack = appSession.sessionState.value.currentTrack
            assertNotNull("Healed track must be present", healedTrack)
            val healedThumbPath = healedTrack?.coverThumbnailPath
            assertNotNull("Active track must have non-null healed cover thumbnail path", healedThumbPath)

            val healedFile = File(healedThumbPath!!)
            assertTrue("Healed thumbnail file must physically exist on disk", healedFile.exists())
            assertTrue("Healed thumbnail file must be non-empty", healedFile.length() > 0)
            assertTrue("Storage layer confirms healed thumbnail is valid", coverArtStorage.isValidThumbnailFile(healedThumbPath))

            advanceUntilIdle()

            // Verify player media metadata now has the healed artwork URI
            val playerArtworkUri =
                engine.player.playlistMetadata.artworkUri
                    ?: engine.player.mediaMetadata.artworkUri
                    ?: engine.player.currentMediaItem?.mediaMetadata?.artworkUri
            assertEquals(Uri.fromFile(healedFile), playerArtworkUri)

            // Verify notification now renders the healed artwork without error
            val healedNotification = notificationProvider.buildNotification(mediaSession)
            assertNotNull("Healed notification must build cleanly", healedNotification)
            val largeIcon = healedNotification.getLargeIcon()
            assertNotNull("Notification must display healed artwork bitmap", largeIcon)
            assertNull("No player listener exceptions during entire flow", listenerException)

            mediaSession.release()
            appSession.release()
            sessionJob.cancel()
            engine.release()
        }

    // Helper functions

    private fun createTestJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        bitmap.recycle()
        return bytes
    }

    private fun buildSampleId3v2Bytes(
        title: String,
        artist: String,
        album: String,
        artworkBytes: ByteArray? = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
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
            picBody.write(0)
            picBody.write(0)
            picBody.write(0)
            picBody.write(3) // Front cover
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

    private fun createRangeMockResponse(
        fullBytes: ByteArray,
        rangeHeader: String?,
    ): MockResponse {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", fullBytes.size.toString())
                .setBody(Buffer().write(fullBytes))
        }
        val parts = rangeHeader.removePrefix("bytes=").split("-")
        val start = parts[0].toLongOrNull() ?: 0L
        val end = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (fullBytes.size - 1L)
        val startIndex = start.toInt().coerceIn(0, fullBytes.size)
        val endIndex = (end.toInt() + 1).coerceIn(startIndex, fullBytes.size)
        val length = (endIndex - startIndex).coerceAtLeast(0)
        val slice = fullBytes.copyOfRange(startIndex, endIndex)
        return MockResponse()
            .setResponseCode(206)
            .setHeader("Content-Range", "bytes $startIndex-${endIndex - 1}/${fullBytes.size}")
            .setHeader("Content-Length", length.toString())
            .setBody(Buffer().write(slice))
    }
}
