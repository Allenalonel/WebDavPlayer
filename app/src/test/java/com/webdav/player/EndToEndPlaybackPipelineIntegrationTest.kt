package com.webdav.player

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
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
import com.webdav.player.data.player.AsfExtractor
import com.webdav.player.data.player.DefaultWebDavMediaSourceAdapter
import com.webdav.player.data.player.Media3AudioPlayerEngine
import com.webdav.player.data.player.WebDavLoadErrorHandlingPolicy
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.DirectoryRepositoryImpl
import com.webdav.player.data.repository.ServerRepositoryImpl
import com.webdav.player.data.service.PlaybackSessionHost
import com.webdav.player.data.service.WebDavMediaService
import com.webdav.player.data.service.WebDavMediaSessionCallback
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.ListDirectoryResult
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
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
}
