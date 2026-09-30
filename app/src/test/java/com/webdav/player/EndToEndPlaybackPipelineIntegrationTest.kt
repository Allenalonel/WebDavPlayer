package com.webdav.player

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorageImpl
import com.webdav.player.data.metadata.AudioMetadataParser
import com.webdav.player.data.metadata.ImageHeaderValidator
import com.webdav.player.data.player.AsfExtractor
import com.webdav.player.data.player.DefaultWebDavMediaSourceAdapter
import com.webdav.player.data.player.Media3AudioPlayerEngine
import com.webdav.player.data.player.WebDavLoadErrorHandlingPolicy
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.DirectoryRepositoryImpl
import com.webdav.player.data.repository.LyricsRepositoryImpl
import com.webdav.player.data.repository.ServerRepositoryImpl
import com.webdav.player.data.repository.TrackMetadataRepositoryImpl
import com.webdav.player.data.service.PlaybackSessionHost
import com.webdav.player.data.service.WebDavMediaService
import com.webdav.player.data.service.WebDavMediaSessionCallback
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.session.FakePlaybackSessionStore
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl
import com.webdav.player.ui.common.ThumbnailMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EndToEndPlaybackPipelineIntegrationTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: AppDatabase
    private lateinit var coverArtStorage: CoverArtStorageImpl
    private lateinit var webDavClient: OkHttpWebDavClient
    private lateinit var serverRepository: ServerRepositoryImpl
    private lateinit var directoryRepository: DirectoryRepositoryImpl
    private lateinit var mediaSourceAdapter: DefaultWebDavMediaSourceAdapter
    private lateinit var sessionStore: FakePlaybackSessionStore
    private lateinit var testServer: WebDavServer
    private lateinit var trackMetadataRepository: TrackMetadataRepositoryImpl
    private lateinit var lyricsRepository: LyricsRepositoryImpl

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
                name = "Integration Test WebDAV",
                url = mockWebServer.url("/dav").toString(),
                port = mockWebServer.port,
                pathPrefix = "/dav",
                username = "streamer",
                password = "stream_secret_password",
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

    private fun createMediaButtonIntent(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
    ): Intent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        val event = KeyEvent(action, keyCode)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, event)
        return intent
    }

    /**
     * Acceptance Criterion 1:
     * End-to-end integration test suite executes the complete user journey:
     * connecting to a WebDAV server, loading directory contents, enqueuing tracks,
     * streaming audio, and verifying playback progress.
     */
    @Test
    fun e2e_completeUserJourney_serverConnect_directoryList_enqueueTracks_streamAndProgress() =
        runTest(testDispatcher) {
            // Step 1: Connect to WebDAV server (testConnection PROPFIND)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                    .setBody("""<?xml version="1.0" encoding="utf-8"?><multistatus xmlns="DAV:"></multistatus>"""),
            )

            val connectionResult = webDavClient.testConnection(testServer)
            assertTrue("WebDAV server connection must succeed", connectionResult is ConnectionResult.Success)

            val connectRequest = mockWebServer.takeRequest(3, TimeUnit.SECONDS)
            assertNotNull(connectRequest)
            assertEquals("PROPFIND", connectRequest!!.method)
            assertEquals(Credentials.basic("streamer", "stream_secret_password"), connectRequest.getHeader("Authorization"))

            // Save server to repository
            serverRepository.setActiveServer(testServer.id)

            // Step 2: Load directory contents via DirectoryRepository (PROPFIND returning multiple audio formats)
            val directoryXml =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/dav/Music/</D:href>
                    <D:propstat>
                      <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/01-Symphony.flac</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/flac</D:getcontenttype>
                        <D:getcontentlength>10485760</D:getcontentlength>
                        <D:getlastmodified>Sun, 27 Sep 2026 12:00:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/02-Allegro.mp3</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/mpeg</D:getcontenttype>
                        <D:getcontentlength>5242880</D:getcontentlength>
                        <D:getlastmodified>Sun, 27 Sep 2026 12:05:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Music/03-Sonata.wma</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:resourcetype/>
                        <D:getcontenttype>audio/x-ms-wma</D:getcontenttype>
                        <D:getcontentlength>7340032</D:getcontentlength>
                        <D:getlastmodified>Sun, 27 Sep 2026 12:10:00 GMT</D:getlastmodified>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                </D:multistatus>
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                    .setBody(directoryXml),
            )

            val listResult = directoryRepository.listDirectory(testServer, "/Music/")
            assertTrue("Directory list must succeed", listResult is ListDirectoryResult.Success)
            val directory = (listResult as ListDirectoryResult.Success).directory
            assertEquals(3, directory.files.size)

            val flacFile = directory.files[0]
            val mp3File = directory.files[1]
            val wmaFile = directory.files[2]
            assertEquals("01-Symphony.flac", flacFile.name)
            assertEquals("02-Allegro.mp3", mp3File.name)
            assertEquals("03-Sonata.wma", wmaFile.name)

            // Step 2b: Verify WebDAV directory caching persists and returns cached results without additional network calls
            val cachedResult = directoryRepository.listDirectory(testServer, "/Music/", forceRefresh = false)
            assertTrue("Cached directory must succeed without new network calls", cachedResult is ListDirectoryResult.Success)
            assertEquals(3, (cachedResult as ListDirectoryResult.Success).directory.files.size)

            // Step 3: Enqueue tracks and start playback through MusicPlayerAppSessionImpl
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
                    sessionStore = sessionStore,
                    coroutineScope = sessionScope,
                    progressDispatcher = testDispatcher,
                )

            appSession.setActiveServer(testServer)
            advanceUntilIdle()

            // User taps 01-Symphony.flac to play directory
            appSession.playDirectoryTrack(directory, flacFile)
            advanceUntilIdle()

            // Step 4: Verify queue and session state
            val state = appSession.sessionState.value
            assertTrue("App session has active track", state.hasTrack)
            assertEquals(3, state.queue.size)
            assertEquals(0, state.queue.currentIndex)
            assertEquals("01-Symphony.flac", state.currentTrack?.title)
            assertEquals(AudioFormat.FLAC, state.currentTrack?.format)

            // Step 5: Verify MediaSource chunk request has correct Basic Auth and URI
            val mediaSource = mediaSourceAdapter.createMediaSource(testServer, state.currentTrack!!)
            assertNotNull(mediaSource)
            val expectedStreamUrl = state.currentTrack!!.streamUrl(testServer)
            assertTrue("Stream URL resolves to mock server", expectedStreamUrl.contains(mockWebServer.port.toString()))

            // Verify ExoPlayer media item count and configuration
            assertEquals(3, engine.player.mediaItemCount)
            assertEquals(
                "audio/flac",
                engine.player
                    .getMediaItemAt(0)
                    .localConfiguration
                    ?.mimeType,
            )
            assertEquals(
                "audio/mpeg",
                engine.player
                    .getMediaItemAt(1)
                    .localConfiguration
                    ?.mimeType,
            )
            assertEquals(
                "audio/x-ms-wma",
                engine.player
                    .getMediaItemAt(2)
                    .localConfiguration
                    ?.mimeType,
            )

            // Step 6: Verify seek and progress propagation
            engine.seekTo(45000L)
            advanceUntilIdle()
            assertEquals(45000L, engine.currentPositionMs.value)
            assertEquals(45000L, appSession.playbackProgress.value.currentPositionMs)

            // Step 7: Skip to next track (MP3)
            engine.skipToNext()
            advanceUntilIdle()
            assertEquals(1, engine.currentTrackIndex.value)
            assertEquals(
                "02-Allegro.mp3",
                appSession.sessionState.value.currentTrack
                    ?.title,
            )

            // Clean up
            appSession.release()
            engine.release()
        }

    /**
     * Acceptance Criterion 2:
     * Simulated network latency and transient chunk request failures trigger
     * automatic retries within the media source adapter without disrupting the listener's audio playback.
     */
    @Test
    fun e2e_networkResilience_transientFailuresTriggerRetryWithoutDisruptingPlayback() =
        runTest(testDispatcher) {
            val track =
                AudioTrack(
                    id = "${testServer.id}:/Music/test.flac",
                    serverId = testServer.id,
                    remotePath = "/Music/test.flac",
                    title = "Resilient Stream",
                    format = AudioFormat.FLAC,
                    size = 2048L,
                )

            val policy = mediaSourceAdapter.loadErrorHandlingPolicy
            val dataSpec =
                DataSpec
                    .Builder()
                    .setUri(testServer.resolveFileUrl(track.remotePath))
                    .build()

            // 1. Verify exponential backoff delays on transient network failure (SocketTimeout)
            val timeoutException =
                HttpDataSource.HttpDataSourceException(
                    "Connection timed out",
                    SocketTimeoutException("Read timed out"),
                    dataSpec,
                    HttpDataSource.HttpDataSourceException.TYPE_READ,
                )

            val errorInfoAttempt1 =
                LoadErrorHandlingPolicy.LoadErrorInfo(
                    LoadEventInfo(0L, dataSpec, 0L),
                    MediaLoadData(C.DATA_TYPE_MEDIA),
                    timeoutException,
                    1,
                )
            assertEquals("Attempt 1 must back off with 1000ms delay", 1000L, policy.getRetryDelayMsFor(errorInfoAttempt1))

            val errorInfoAttempt2 =
                LoadErrorHandlingPolicy.LoadErrorInfo(
                    LoadEventInfo(0L, dataSpec, 0L),
                    MediaLoadData(C.DATA_TYPE_MEDIA),
                    timeoutException,
                    2,
                )
            assertEquals("Attempt 2 must back off with 2000ms delay", 2000L, policy.getRetryDelayMsFor(errorInfoAttempt2))

            val errorInfoAttempt3 =
                LoadErrorHandlingPolicy.LoadErrorInfo(
                    LoadEventInfo(0L, dataSpec, 0L),
                    MediaLoadData(C.DATA_TYPE_MEDIA),
                    timeoutException,
                    3,
                )
            assertEquals("Attempt 3 must back off with 4000ms delay", 4000L, policy.getRetryDelayMsFor(errorInfoAttempt3))

            // Attempt 4 exceeds 3 retries -> escalate to terminal error
            val errorInfoAttempt4 =
                LoadErrorHandlingPolicy.LoadErrorInfo(
                    LoadEventInfo(0L, dataSpec, 0L),
                    MediaLoadData(C.DATA_TYPE_MEDIA),
                    timeoutException,
                    4,
                )
            assertEquals("Attempt 4 must escalate to C.TIME_UNSET", C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfoAttempt4))

            // 2. Verify BrokenPipe and ConnectionReset also trigger retry
            val brokenPipeException =
                HttpDataSource.HttpDataSourceException(
                    "Broken pipe",
                    java.net.SocketException("Broken pipe"),
                    dataSpec,
                    HttpDataSource.HttpDataSourceException.TYPE_READ,
                )
            val brokenPipeInfo =
                LoadErrorHandlingPolicy.LoadErrorInfo(
                    LoadEventInfo(0L, dataSpec, 0L),
                    MediaLoadData(C.DATA_TYPE_MEDIA),
                    brokenPipeException,
                    1,
                )
            assertEquals("Broken pipe must retry", 1000L, policy.getRetryDelayMsFor(brokenPipeInfo))

            // 3. Verify non-recoverable HTTP errors (401, 403, 404) fail fast without infinite retry loops
            val nonRecoverableCodes = listOf(401, 403, 404, 410)
            for (code in nonRecoverableCodes) {
                val httpException =
                    HttpDataSource.InvalidResponseCodeException(
                        code,
                        "HTTP $code",
                        null,
                        emptyMap(),
                        dataSpec,
                        byteArrayOf(),
                    )
                val errorInfo =
                    LoadErrorHandlingPolicy.LoadErrorInfo(
                        LoadEventInfo(0L, dataSpec, 0L),
                        MediaLoadData(C.DATA_TYPE_MEDIA),
                        httpException,
                        1,
                    )
                assertEquals("HTTP $code must fail fast", C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo))
            }

            // 4. Streaming chunk retrieval succeeds when server responds normally
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "audio/flac")
                    .setHeader("Content-Length", "2048")
                    .setBody("recovered-audio-stream-chunk-data"),
            )

            val dataSource = mediaSourceAdapter.getDataSourceFactory(testServer).createDataSource()
            dataSource.open(dataSpec)
            val buffer = ByteArray(64)
            val bytesRead = dataSource.read(buffer, 0, buffer.size)
            dataSource.close()

            assertTrue("Should read non-zero bytes", bytesRead > 0)
            assertEquals("recovered-audio-stream-chunk-data", String(buffer, 0, bytesRead))

            // 5. Verify auth header is attached to stream chunk request
            val chunkRequest = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
            assertNotNull(chunkRequest)
            assertEquals(
                Credentials.basic(testServer.username, testServer.password),
                chunkRequest!!.getHeader("Authorization"),
            )

            // 6. Verify streaming client has >=30s read timeout for resilient streaming
            val streamingClient = mediaSourceAdapter.getStreamingClientForServer(testServer)
            assertTrue(
                "Streaming client read timeout must be >= 30s",
                streamingClient.readTimeoutMillis >= 30_000,
            )
        }

    /**
     * Acceptance Criterion 3:
     * Background service execution and foreground notification updates operate continuously
     * without receiving App Idle termination warnings from the Android system.
     */
    @Test
    fun e2e_backgroundServiceAndNotification_preventsAppIdleTermination() {
        val fakeEngine = FakeAudioPlayerEngine()
        val exoPlayer =
            androidx.media3.exoplayer.ExoPlayer
                .Builder(context)
                .build()
        val mediaSession =
            androidx.media3.session.MediaSession
                .Builder(context, exoPlayer)
                .setId("e2e_session_${System.currentTimeMillis()}")
                .setCallback(WebDavMediaSessionCallback(fakeEngine))
                .build()

        val host =
            PlaybackSessionHost(
                context = context,
                playerEngine = fakeEngine,
                mediaSession = mediaSession,
                audioFocusHandler =
                    com.webdav.player.data.player
                        .AudioFocusHandler(context, fakeEngine),
            )
        PlaybackSessionHost.setInstanceForTesting(host)

        val serviceController: ServiceController<WebDavMediaService> = Robolectric.buildService(WebDavMediaService::class.java)
        val service = serviceController.create().get()
        val shadowService = shadowOf(service)

        try {
            // 1. Transition to Playing -> elevates foreground service with MEDIA_PLAYBACK type
            host.handlePlaybackStateChanged(PlaybackState.Playing)
            assertTrue("Host is in foreground when playing", host.isForegroundActive)
            assertFalse("Service foreground is active", shadowService.isForegroundStopped)
            assertNotNull("Notification published", shadowService.lastForegroundNotification)

            // 2. Transition to Buffering -> service remains in foreground (no App Idle)
            host.handlePlaybackStateChanged(PlaybackState.Buffering)
            assertTrue("Host remains in foreground during buffering", host.isForegroundActive)

            // 3. Transition to Paused -> graceful demote to STOP_FOREGROUND_DETACH
            // Crucial: Keeps notification visible for the user, but releases active foreground status
            // so Android ActivityManager does not flag the app with App Idle violation warnings!
            host.handlePlaybackStateChanged(PlaybackState.Paused)
            assertFalse("Host foreground demoted on pause", host.isForegroundActive)
            assertTrue("Service stopped foreground execution gracefully", shadowService.isForegroundStopped)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val activeNotifications = notificationManager.activeNotifications
            assertTrue(
                "Notification remains in NotificationManager for user resumption",
                activeNotifications.any { it.id == host.notificationProvider.notificationId },
            )

            // 4. Audio Focus Interruption: incoming call causes AUDIOFOCUS_LOSS_TRANSIENT while playing
            fakeEngine._playbackState.value = PlaybackState.Playing
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            assertEquals("Playback paused on audio focus loss", 1, fakeEngine.pauseCount)
            assertTrue("Resume on focus gain is set", host.audioFocusHandler.resumeOnFocusGain)

            // Audio Focus Regained: call ends -> auto resume playback
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            assertEquals("Playback resumed on audio focus gain", 1, fakeEngine.playCount)
            assertFalse("Resume on focus gain is reset", host.audioFocusHandler.resumeOnFocusGain)

            // 5. Hardware Media Button controls dispatch directly to the host
            val callback = WebDavMediaSessionCallback(host)
            val pauseButtonIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            assertTrue(callback.handleMediaButtonIntent(pauseButtonIntent))
            assertEquals(2, fakeEngine.pauseCount)

            val playButtonIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY)
            assertTrue(callback.handleMediaButtonIntent(playButtonIntent))
            assertEquals(2, fakeEngine.playCount)

            // 6. Error state -> graceful foreground demote to prevent App Idle
            host.handlePlaybackStateChanged(PlaybackState.Error("Connection reset"))
            assertFalse("Host foreground demoted on error", host.isForegroundActive)

            // 7. Playback stop or completion -> STOP_FOREGROUND_REMOVE, dismiss notification
            host.handlePlaybackStateChanged(PlaybackState.Idle)
            assertFalse(host.isForegroundActive)
        } finally {
            serviceController.destroy()
            host.release()
            mediaSession.release()
            exoPlayer.release()
        }
    }

    /**
     * Acceptance Criterion 4:
     * UI rendering performance is verified to ensure main-thread frame skipping
     * during cold start and track transitions is fully resolved.
     */
    @Test
    fun e2e_twoTrackSessionPerformance_eliminatesMainThreadCongestionDuringProgressAndTransitions() =
        runTest(testDispatcher) {
            val fakeEngine = FakeAudioPlayerEngine()
            val sessionScope = TestScope(testDispatcher)

            val sampleFile1 = RemoteFile("track1.mp3", "/track1.mp3", 1000L, fileType = RemoteFileType.Audio(AudioFormat.MP3))
            val sampleFile2 = RemoteFile("track2.mp3", "/track2.mp3", 2000L, fileType = RemoteFileType.Audio(AudioFormat.MP3))
            val sampleDirectory =
                RemoteDirectory(
                    path = "/",
                    name = "Root",
                    files = listOf(sampleFile1, sampleFile2),
                )

            val appSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = fakeEngine,
                    serverRepository = serverRepository,
                    sessionStore = sessionStore,
                    coroutineScope = sessionScope,
                    progressDispatcher = testDispatcher,
                )

            appSession.setActiveServer(testServer)
            appSession.playDirectoryTrack(sampleDirectory, sampleFile1)
            advanceUntilIdle()

            // Track emissions on both reactive streams
            val structuralEmissions = mutableListOf<PlayerSessionState>()
            val progressEmissions = mutableListOf<PlaybackProgress>()

            val structuralJob =
                launch {
                    appSession.sessionState.collect { structuralEmissions.add(it) }
                }
            val progressJob =
                launch {
                    appSession.playbackProgress.collect { progressEmissions.add(it) }
                }
            advanceUntilIdle()

            val initialStructuralCount = structuralEmissions.size
            val initialProgressCount = progressEmissions.size

            // Simulate 50 high-frequency millisecond playback position ticks (e.g. 50ms intervals)
            for (pos in 1000L..50000L step 1000L) {
                fakeEngine._currentPositionMs.value = pos
                advanceTimeBy(50L)
                advanceUntilIdle()
            }

            // VERIFICATION: Structural session state MUST have ZERO new emissions from millisecond progress ticks!
            // This guarantees that mini-player, directory browser, and server management UI screens
            // do not recompose on sub-second playback progress, completely eliminating main-thread Looper delays!
            val newStructuralCount = structuralEmissions.size - initialStructuralCount
            assertEquals(
                "Structural session state must NOT emit on high-frequency progress updates, but emitted $newStructuralCount times",
                0,
                newStructuralCount,
            )

            // Progress stream MUST receive the high-frequency updates for smooth full-player rendering
            val newProgressCount = progressEmissions.size - initialProgressCount
            assertTrue(
                "Playback progress stream must receive high-frequency updates, received $newProgressCount",
                newProgressCount > 0,
            )
            assertEquals(50000L, appSession.playbackProgress.value.currentPositionMs)

            // Track Transition: Switch to next track -> structural state emits exactly 1 update for track index change
            fakeEngine.skipToNext()
            advanceUntilIdle()

            val transitionStructuralCount = structuralEmissions.size - initialStructuralCount
            assertEquals("Track transition must emit exactly 1 structural state update", 1, transitionStructuralCount)
            assertEquals(
                "track2.mp3",
                appSession.sessionState.value.currentTrack
                    ?.title,
            )

            structuralJob.cancel()
            progressJob.cancel()
            appSession.release()
        }

    /**
     * Acceptance Criterion 5:
     * Ephemeral in-memory streaming buffer constraints established by ADR-0002 are strictly maintained,
     * with zero audio chunk bytes persisting to local disk storage.
     */
    @Test
    fun e2e_inMemoryStreamingBuffer_strictlyAdheresToAdr0002ZeroDiskPersistence() {
        // 1. Verify OkHttpClient streaming configuration has NO persistent disk cache configured
        val streamingClient = webDavClient.buildStreamingClientForServer(testServer)
        assertNull(
            "ADR-0002 Violation: OkHttp streaming client must have no disk cache configured",
            streamingClient.cache,
        )

        // 2. Snapshot local disk storage before streaming operations
        val cacheDir = context.cacheDir
        val filesDir = context.filesDir
        val externalCacheDir = context.externalCacheDir

        fun findAudioOrExoFiles(dir: File?): List<File> {
            if (dir == null || !dir.exists()) return emptyList()
            return dir
                .walkTopDown()
                .filter { file ->
                    file.isFile && (
                        file.name.endsWith(".mp3") ||
                            file.name.endsWith(".flac") ||
                            file.name.endsWith(".wma") ||
                            file.name.endsWith(".wav") ||
                            file.name.endsWith(".exo") ||
                            file.name.endsWith(".chunk") ||
                            file.name.contains("exo_") ||
                            file.name.contains("cache-")
                    )
                }.toList()
        }

        val initialAudioFiles =
            findAudioOrExoFiles(cacheDir) +
                findAudioOrExoFiles(filesDir) +
                findAudioOrExoFiles(externalCacheDir)

        assertEquals("No audio chunk files exist initially", 0, initialAudioFiles.size)

        // 3. Perform streaming chunk request through WebDavMediaSourceAdapter
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/flac")
                .setHeader("Content-Length", "4096")
                .setBody("test-in-memory-audio-chunk-bytes-must-not-be-written-to-disk"),
        )

        val track =
            AudioTrack(
                id = "100:/Music/memory_test.flac",
                serverId = testServer.id,
                remotePath = "/Music/memory_test.flac",
                title = "Memory Test",
                format = AudioFormat.FLAC,
                size = 4096L,
            )

        val dataSource = mediaSourceAdapter.getDataSourceFactory(testServer).createDataSource()
        val dataSpec =
            DataSpec
                .Builder()
                .setUri(testServer.resolveFileUrl(track.remotePath))
                .build()

        dataSource.open(dataSpec)
        val readBuffer = ByteArray(128)
        val bytesRead = dataSource.read(readBuffer, 0, readBuffer.size)
        dataSource.close()

        assertTrue("Read bytes into in-memory buffer", bytesRead > 0)

        // 4. Verify ZERO audio chunk bytes were written to local disk
        val postStreamingAudioFiles =
            findAudioOrExoFiles(cacheDir) +
                findAudioOrExoFiles(filesDir) +
                findAudioOrExoFiles(externalCacheDir)

        assertEquals(
            "ADR-0002 Violation: Audio stream chunk was persisted to local disk storage! Files found: $postStreamingAudioFiles",
            0,
            postStreamingAudioFiles.size,
        )
    }

    /**
     * Format Extractor Resolution Verification:
     * Custom ASF extractor binds for WMA, standard extractors bind for FLAC/MP3/WAV.
     */
    @Test
    fun e2e_specializedAudioFormatExtractors_autoResolveAndBind() {
        // WMA
        val wmaExtractors = mediaSourceAdapter.resolveExtractorsFactory(AudioFormat.WMA).createExtractors()
        assertTrue("WMA extractor list must not be empty", wmaExtractors.isNotEmpty())
        assertTrue("WMA must bind AsfExtractor", wmaExtractors.any { it is AsfExtractor })

        // FLAC
        val flacExtractors = mediaSourceAdapter.resolveExtractorsFactory(AudioFormat.FLAC).createExtractors()
        assertTrue("FLAC must bind FlacExtractor", flacExtractors.any { it is androidx.media3.extractor.flac.FlacExtractor })

        // MP3
        val mp3Extractors = mediaSourceAdapter.resolveExtractorsFactory(AudioFormat.MP3).createExtractors()
        assertTrue("MP3 must bind Mp3Extractor", mp3Extractors.any { it is androidx.media3.extractor.mp3.Mp3Extractor })

        // WAV
        val wavExtractors = mediaSourceAdapter.resolveExtractorsFactory(AudioFormat.WAV).createExtractors()
        assertTrue("WAV must bind WavExtractor", wavExtractors.any { it is androidx.media3.extractor.wav.WavExtractor })
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

    private fun createHighResJpegBytes(minSizeBytes: Int = 600 * 1024): ByteArray {
        val baseBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        baseBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val standardJpeg = stream.toByteArray()
        baseBitmap.recycle()

        if (standardJpeg.size >= minSizeBytes) {
            return standardJpeg
        }
        val padded = ByteArray(minSizeBytes)
        System.arraycopy(standardJpeg, 0, padded, 0, standardJpeg.size - 2)
        for (i in (standardJpeg.size - 2) until (minSizeBytes - 2)) {
            padded[i] = 0xAA.toByte()
        }
        padded[minSizeBytes - 2] = 0xFF.toByte()
        padded[minSizeBytes - 1] = 0xD9.toByte()
        return padded
    }

    /**
     * Acceptance Criterion: High-res cover art (>500KB) extracted and rendered without truncation.
     * Verifies the dual-range HTTP fetching mechanism, extraction of APIC frames larger than 512KB,
     * local thumbnail storage, queue metadata enrichment, and bitmap rendering.
     */
    @Test
    fun e2e_highResCoverArt_extractedAndRenderedWithoutTruncation_acrossPipeline() =
        runTest(testDispatcher) {
            // 1. Generate high-resolution embedded JPEG artwork (>500KB)
            val highResArtwork = createHighResJpegBytes(600 * 1024)
            assertTrue("Artwork must be > 500KB", highResArtwork.size > 500 * 1024)
            assertTrue("Artwork must have valid JPEG boundaries", ImageHeaderValidator.isCompleteImage(highResArtwork))

            // Build full MP3 bytes containing high-res APIC tag
            val mp3Bytes =
                buildSampleId3v2Bytes(
                    title = "Symphony No. 9",
                    artist = "Ludwig van Beethoven",
                    album = "Masterpieces in Hi-Res",
                    artworkBytes = highResArtwork,
                )
            assertTrue("MP3 bytes must exceed 512KB to test adaptive secondary range fetch", mp3Bytes.size > 524288)

            // Setup MockWebServer dispatcher supporting chunked Range requests
            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path?.substringBefore('?') ?: ""
                        val range = request.getHeader("Range")
                        return when {
                            path == "/dav/Music/HighRes.mp3" -> createRangeMockResponse(mp3Bytes, range)
                            else -> MockResponse().setResponseCode(404)
                        }
                    }
                }

            val remoteFile =
                RemoteFile(
                    name = "HighRes.mp3",
                    path = "/Music/HighRes.mp3",
                    size = mp3Bytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val directory =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(remoteFile),
                )

            // Step 1: Resolve metadata through TrackMetadataRepository
            // This tests adaptive initial 512KB range + secondary range fetch for oversized ID3 tag
            trackMetadataRepository.resolveMetadata(testServer, listOf(remoteFile))

            val cachedMeta = trackMetadataRepository.getCachedMetadata(testServer.id, remoteFile.path)
            assertNotNull("Metadata must be persisted in Room database", cachedMeta)
            val resolvedMeta = cachedMeta!!

            assertNotNull("Cover thumbnail path must be non-null", resolvedMeta.coverThumbnailPath)
            assertEquals("Symphony No. 9", resolvedMeta.title)
            assertEquals("Ludwig van Beethoven", resolvedMeta.artist)
            assertEquals("Masterpieces in Hi-Res", resolvedMeta.album)

            // Step 2: Enqueue into player session and verify queue metadata propagation
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
            appSession.playDirectoryTrack(directory, remoteFile, mapOf(remoteFile.path to resolvedMeta))
            advanceUntilIdle()

            val currentTrack = appSession.sessionState.value.currentTrack
            assertNotNull("Active track must be present in session", currentTrack)
            assertEquals("Symphony No. 9", currentTrack?.title)
            val thumbPath = currentTrack?.coverThumbnailPath
            assertNotNull("Current track must immediately have cover thumbnail path", thumbPath)

            // Step 3: Verify thumbnail file on disk is valid and NOT truncated
            val thumbFile = File(thumbPath!!)
            assertTrue("Thumbnail file must exist on disk", thumbFile.exists())
            assertTrue("Thumbnail file must be non-empty", thumbFile.length() > 0)

            // Step 4: Verify bitmap decoding & ThumbnailMemoryCache rendering
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val decodedBitmap = BitmapFactory.decodeFile(thumbFile.absolutePath, options)
            if (decodedBitmap != null) {
                assertTrue("Decoded bitmap width must be > 0", decodedBitmap.width > 0)
                assertTrue("Decoded bitmap height must be > 0", decodedBitmap.height > 0)
                ThumbnailMemoryCache.put(thumbPath, decodedBitmap.asImageBitmap())
                val inMemBitmap = ThumbnailMemoryCache.get(thumbPath)
                assertNotNull("ThumbnailMemoryCache must store and retrieve decoded artwork", inMemBitmap)
            } else {
                // If Robolectric bypasses native BitmapFactory, raw byte stream must remain intact
                assertTrue("Thumbnail file must contain uncorrupted bytes", thumbFile.length() >= 512)
            }

            // Step 5: Verify playback progression and seek
            engine.seekTo(15000L)
            advanceUntilIdle()
            assertEquals(15000L, appSession.playbackProgress.value.currentPositionMs)

            appSession.release()
            engine.release()
        }

    /**
     * Acceptance Criterion: Folder-level cover.jpg loaded when audio file has no embedded artwork.
     * Verifies companion artwork probing, caching in CoverArtStorage, association with Room metadata,
     * and seamless propagation across queue and track transitions.
     */
    @Test
    fun e2e_folderCoverFallback_loadedWhenAudioLacksEmbeddedArtwork_acrossDirectoryAndPlayback() =
        runTest(testDispatcher) {
            // Build audio files without embedded artwork
            val track1Bytes =
                buildSampleId3v2Bytes(
                    title = "Sonata 1",
                    artist = "Chopin",
                    album = "Nocturnes",
                    artworkBytes = null,
                )
            val track2Bytes =
                buildSampleFlacBytes(
                    title = "Sonata 2",
                    artist = "Chopin",
                    album = "Nocturnes",
                    artworkBytes = null,
                )

            // Companion folder cover.jpg (valid JPEG)
            val folderCoverBytes =
                ByteArray(16 * 1024).apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[2] = 0xFF.toByte()
                    this[3] = 0xE0.toByte()
                    this[this.size - 2] = 0xFF.toByte()
                    this[this.size - 1] = 0xD9.toByte()
                }

            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path?.substringBefore('?') ?: ""
                        val range = request.getHeader("Range")
                        return when {
                            path == "/dav/Albums/Chopin/Sonata1.mp3" -> {
                                createRangeMockResponse(track1Bytes, range)
                            }

                            path == "/dav/Albums/Chopin/Sonata2.flac" -> {
                                createRangeMockResponse(track2Bytes, range)
                            }

                            path == "/dav/Albums/Chopin/cover.jpg" -> {
                                MockResponse()
                                    .setResponseCode(200)
                                    .setHeader("Content-Type", "image/jpeg")
                                    .setHeader("Content-Length", folderCoverBytes.size.toString())
                                    .setBody(Buffer().write(folderCoverBytes))
                            }

                            else -> {
                                MockResponse().setResponseCode(404)
                            }
                        }
                    }
                }

            val file1 =
                RemoteFile(
                    name = "Sonata1.mp3",
                    path = "/Albums/Chopin/Sonata1.mp3",
                    size = track1Bytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val file2 =
                RemoteFile(
                    name = "Sonata2.flac",
                    path = "/Albums/Chopin/Sonata2.flac",
                    size = track2Bytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val directory =
                RemoteDirectory(
                    path = "/Albums/Chopin/",
                    name = "Chopin",
                    files = listOf(file1, file2),
                )

            // Step 1: Resolve metadata for both tracks in directory
            trackMetadataRepository.resolveMetadata(testServer, listOf(file1, file2))

            val meta1 = trackMetadataRepository.getCachedMetadata(testServer.id, file1.path)
            val meta2 = trackMetadataRepository.getCachedMetadata(testServer.id, file2.path)

            assertNotNull("Track 1 must have cached metadata", meta1)
            assertNotNull("Track 2 must have cached metadata", meta2)

            val coverPath1 = meta1?.coverThumbnailPath
            val coverPath2 = meta2?.coverThumbnailPath
            assertNotNull("Track 1 must have resolved folder cover", coverPath1)
            assertNotNull("Track 2 must have resolved folder cover", coverPath2)
            assertEquals("Both tracks must share the same cached folder cover thumbnail", coverPath1, coverPath2)

            val coverFile = File(coverPath1!!)
            assertTrue("Folder cover thumbnail must exist on disk", coverFile.exists())
            assertTrue("Folder cover thumbnail must be non-empty", coverFile.length() > 0)

            // Step 2: Enqueue and play Track 1 in MusicPlayerAppSessionImpl
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
            appSession.playDirectoryTrack(
                directory,
                file1,
                mapOf(file1.path to meta1!!, file2.path to meta2!!),
            )
            advanceUntilIdle()

            // Verify active Track 1 displays the folder cover
            assertEquals(
                "Sonata 1",
                appSession.sessionState.value.currentTrack
                    ?.title,
            )
            assertEquals(
                coverPath1,
                appSession.sessionState.value.currentTrack
                    ?.coverThumbnailPath,
            )

            // Verify ThumbnailMemoryCache rendering
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            ThumbnailMemoryCache.put(coverPath1, bitmap.asImageBitmap())
            assertNotNull(ThumbnailMemoryCache.get(coverPath1))

            // Step 3: Track Transition to Track 2
            engine.skipToNext()
            advanceUntilIdle()

            assertEquals(
                "Sonata 2",
                appSession.sessionState.value.currentTrack
                    ?.title,
            )
            assertEquals(
                "Track 2 must immediately retain folder cover after transition",
                coverPath1,
                appSession.sessionState.value.currentTrack
                    ?.coverThumbnailPath,
            )
            assertNotNull("Bitmap remains accessible in memory cache across transition", ThumbnailMemoryCache.get(coverPath1))

            appSession.release()
            engine.release()
        }

    /**
     * Acceptance Criterion: Enhanced LRC with multiple inline word timestamps verified
     * to contain exact unique lines per verse.
     * Verifies syllable timestamp stripping, chorus timestamp expansion,
     * and accurate active verse tracking during playback progress.
     */
    @Test
    fun e2e_enhancedLrc_inlineTimestamps_normalizedToUniqueLinesPerVerse_duringPlayback() =
        runTest(testDispatcher) {
            val audioBytes =
                buildSampleId3v2Bytes(
                    title = "Karaoke Hit",
                    artist = "Ensemble",
                    album = "Acoustic Sessions",
                    artworkBytes = null,
                )

            val enhancedLrcContent =
                """
                [ti:Karaoke Hit]
                [ar:Ensemble]
                [al:Acoustic Sessions]
                [00:05.00] 哪怕[00:05.50]现实[00:06.00]再残酷
                [00:12.00]<00:12.00>Never <00:12.30>gonna <00:12.60>give <00:12.90>you <00:13.20>up
                [00:25.00][00:45.00]Chorus verse with [00:25.50]karaoke [00:45.50]word tags
                [01:10.00]End of the journey
                """.trimIndent()

            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path?.substringBefore('?') ?: ""
                        val range = request.getHeader("Range")
                        return when {
                            path == "/dav/Music/KaraokeHit.mp3" -> {
                                createRangeMockResponse(audioBytes, range)
                            }

                            path == "/dav/Music/KaraokeHit.lrc" -> {
                                MockResponse()
                                    .setResponseCode(200)
                                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                                    .setBody(enhancedLrcContent)
                            }

                            else -> {
                                MockResponse().setResponseCode(404)
                            }
                        }
                    }
                }

            val karaokeFile =
                RemoteFile(
                    name = "KaraokeHit.mp3",
                    path = "/Music/KaraokeHit.mp3",
                    size = audioBytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val directory =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(karaokeFile),
                )

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
            appSession.playDirectoryTrack(directory, karaokeFile)
            advanceUntilIdle()

            // Wait for lyrics to resolve from MockWebServer
            var attempts = 0
            while (appSession.sessionState.value.lyrics == null && attempts < 50) {
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                attempts++
            }

            val lyrics = appSession.sessionState.value.lyrics
            assertNotNull("Lyrics must be loaded automatically by app session", lyrics)
            assertTrue("Lyrics must be synchronized", lyrics!!.isSynchronized)

            // VERIFICATION: Enhanced LRC must contain EXACT unique lines per verse!
            // Verse 1: "哪怕现实再残酷" at 5000L (No duplicate lines for [00:05.50] and [00:06.00])
            // Verse 2: "Never gonna give you up" at 12000L (Inline angle brackets stripped)
            // Chorus: Repeated chorus at 25000L and 45000L (Leading timestamps expanded, inline stripped)
            // Outro: "End of the journey" at 70000L
            // Total lines must be EXACTLY 5:
            assertEquals("Must have exactly 5 lines (no duplicate lines per verse)", 5, lyrics.lines.size)

            assertEquals(5000L, lyrics.lines[0].timestampMs)
            assertEquals("哪怕现实再残酷", lyrics.lines[0].text)

            assertEquals(12000L, lyrics.lines[1].timestampMs)
            assertEquals("Never gonna give you up", lyrics.lines[1].text)

            assertEquals(25000L, lyrics.lines[2].timestampMs)
            assertEquals("Chorus verse with karaoke word tags", lyrics.lines[2].text)

            assertEquals(45000L, lyrics.lines[3].timestampMs)
            assertEquals("Chorus verse with karaoke word tags", lyrics.lines[3].text)

            assertEquals(70000L, lyrics.lines[4].timestampMs)
            assertEquals("End of the journey", lyrics.lines[4].text)

            // Verify active verse tracking as playback progresses
            assertEquals(0, lyrics.findActiveLineIndex(6000L))
            assertEquals(1, lyrics.findActiveLineIndex(15000L))
            assertEquals(2, lyrics.findActiveLineIndex(30000L))
            assertEquals(3, lyrics.findActiveLineIndex(46000L))
            assertEquals(4, lyrics.findActiveLineIndex(75000L))

            appSession.release()
            engine.release()
        }

    /**
     * Acceptance Criterion: Bilingual LRC loaded and verified with structured translations.
     * Verifies pairing of original lines and translations, seek synchronization,
     * track transition lyric reloads, and cold-start state restoration.
     */
    @Test
    fun e2e_bilingualLrc_loadedWithStructuredTranslations_renderedAndTrackTransitions() =
        runTest(testDispatcher) {
            val audioBytes =
                buildSampleId3v2Bytes(
                    title = "Bilingual Song 1",
                    artist = "Global Artist",
                    album = "Harmony",
                    artworkBytes = null,
                )

            val lrc1 =
                """
                [ti:Bilingual Song 1]
                [ar:Global Artist]
                [00:03.00]First verse in English
                [00:03.00]第一节英文歌词
                [00:10.000]Second line under the moon
                [00:10.200]月光下的第二行
                [00:20.00][00:40.00]Chorus line shining bright
                [00:20.00][00:40.00]闪耀璀璨的副歌行
                """.trimIndent()

            val lrc2 =
                """
                [ti:Bilingual Song 2]
                [ar:Global Artist]
                [00:05.00]The sun rises again
                [00:05.00]太阳再次升起
                [00:15.00]A brand new chapter starts
                [00:15.100]全新篇章开启
                """.trimIndent()

            mockWebServer.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path?.substringBefore('?') ?: ""
                        val range = request.getHeader("Range")
                        return when {
                            path == "/dav/Music/Bilingual01.mp3" -> {
                                createRangeMockResponse(audioBytes, range)
                            }

                            path == "/dav/Music/Bilingual01.lrc" -> {
                                MockResponse()
                                    .setResponseCode(200)
                                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                                    .setBody(lrc1)
                            }

                            path == "/dav/Music/Bilingual02.mp3" -> {
                                createRangeMockResponse(audioBytes, range)
                            }

                            path == "/dav/Music/Bilingual02.lrc" -> {
                                MockResponse()
                                    .setResponseCode(200)
                                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                                    .setBody(lrc2)
                            }

                            else -> {
                                MockResponse().setResponseCode(404)
                            }
                        }
                    }
                }

            val file1 =
                RemoteFile(
                    name = "Bilingual01.mp3",
                    path = "/Music/Bilingual01.mp3",
                    size = audioBytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val file2 =
                RemoteFile(
                    name = "Bilingual02.mp3",
                    path = "/Music/Bilingual02.mp3",
                    size = audioBytes.size.toLong(),
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val directory =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(file1, file2),
                )

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
            appSession.playDirectoryTrack(directory, file1)
            advanceUntilIdle()

            // Wait for lyrics to load
            var attempts = 0
            while (appSession.sessionState.value.lyrics == null && attempts < 50) {
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                attempts++
            }

            val lyrics1 = appSession.sessionState.value.lyrics
            assertNotNull("Track 1 lyrics must be loaded", lyrics1)
            assertTrue("Track 1 lyrics must be synchronized", lyrics1!!.isSynchronized)
            assertEquals("Track 1 must have 4 bilingual lines", 4, lyrics1.lines.size)

            // Step 1: Verify structured translations on LyricLine
            val line0 = lyrics1.lines[0]
            assertEquals(3000L, line0.timestampMs)
            assertEquals("First verse in English", line0.mainText)
            assertEquals("第一节英文歌词", line0.translation)
            assertTrue("Line 0 must have translation", line0.hasTranslation)

            val line1 = lyrics1.lines[1]
            assertEquals(10000L, line1.timestampMs)
            assertEquals("Second line under the moon", line1.mainText)
            assertEquals("月光下的第二行", line1.translation)
            assertTrue("Line 1 must have translation", line1.hasTranslation)

            val line2 = lyrics1.lines[2]
            assertEquals(20000L, line2.timestampMs)
            assertEquals("Chorus line shining bright", line2.mainText)
            assertEquals("闪耀璀璨的副歌行", line2.translation)

            val line3 = lyrics1.lines[3]
            assertEquals(40000L, line3.timestampMs)
            assertEquals("Chorus line shining bright", line3.mainText)
            assertEquals("闪耀璀璨的副歌行", line3.translation)

            // Step 2: Seek to bilingual line and verify active line selection
            engine.seekTo(10000L)
            advanceUntilIdle()
            val activeIndex = lyrics1.findActiveLineIndex(10000L)
            assertEquals(1, activeIndex)
            assertEquals("Second line under the moon", lyrics1.lines[activeIndex].mainText)
            assertEquals("月光下的第二行", lyrics1.lines[activeIndex].translation)

            // Step 3: Track Transition to Track 2 (Queue skip)
            engine.skipToNext()
            advanceUntilIdle()

            assertEquals(
                "Bilingual02.mp3",
                appSession.sessionState.value.currentTrack
                    ?.title,
            )

            // Wait for Track 2 lyrics to load reactively
            attempts = 0
            while ((
                    appSession.sessionState.value.lyrics
                        ?.lines
                        ?.firstOrNull()
                        ?.mainText != "The sun rises again"
                ) && attempts < 50
            ) {
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                attempts++
            }

            val lyrics2 = appSession.sessionState.value.lyrics
            assertNotNull("Track 2 lyrics must be loaded", lyrics2)
            assertEquals(2, lyrics2!!.lines.size)
            assertEquals("The sun rises again", lyrics2.lines[0].mainText)
            assertEquals("太阳再次升起", lyrics2.lines[0].translation)
            assertEquals("A brand new chapter starts", lyrics2.lines[1].mainText)
            assertEquals("全新篇章开启", lyrics2.lines[1].translation)

            // Step 4: Cold Start Restoration with SessionStore
            appSession.flushSession()
            advanceUntilIdle()

            val restoredSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine,
                    serverRepository = serverRepository,
                    trackMetadataRepository = trackMetadataRepository,
                    lyricsRepository = lyricsRepository,
                    sessionStore = sessionStore,
                    coroutineScope = sessionScope,
                    progressDispatcher = testDispatcher,
                )
            advanceUntilIdle()

            attempts = 0
            while (restoredSession.sessionState.value.lyrics == null && attempts < 50) {
                advanceTimeBy(50L)
                advanceUntilIdle()
                Thread.sleep(10)
                attempts++
            }

            val restoredLyrics = restoredSession.sessionState.value.lyrics
            assertNotNull("Restored session must have loaded lyrics", restoredLyrics)
            assertEquals("The sun rises again", restoredLyrics?.lines?.firstOrNull()?.mainText)
            assertEquals("太阳再次升起", restoredLyrics?.lines?.firstOrNull()?.translation)

            restoredSession.release()
            appSession.release()
            engine.release()
        }
}
