package com.webdav.player

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import com.webdav.player.data.lyrics.LrcParser
import com.webdav.player.data.service.WebDavMediaSessionCallback
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.session.FakePlaybackSessionStore
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl
import com.webdav.player.ui.browser.AudioQualityBadgeHelper
import com.webdav.player.ui.browser.AudioQualityLevel
import com.webdav.player.ui.browser.BreadcrumbNavigationHelper
import com.webdav.player.ui.navigation.AppDestination
import com.webdav.player.ui.navigation.MainNavigationCoordinator
import com.webdav.player.ui.player.ArtworkColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EndToEndMusicPlayerPipelineTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var fakeStore: FakePlaybackSessionStore
    private lateinit var fakeServerRepo: TestServerRepository
    private lateinit var sessionJob: kotlinx.coroutines.CompletableJob
    private lateinit var sessionScope: kotlinx.coroutines.CoroutineScope
    private lateinit var session: MusicPlayerAppSessionImpl
    private lateinit var mediaSessionCallback: WebDavMediaSessionCallback

    private val sampleServer =
        WebDavServer(
            id = 1L,
            name = "Synology NAS",
            url = "https://nas.local:5006/music",
            port = 5006,
            pathPrefix = "/music",
            username = "audiophile",
            password = "secret",
            allowSelfSigned = true,
            isDefault = true,
        )

    private val flacFile =
        RemoteFile(
            name = "01-Symphony.flac",
            path = "/music/Lossless/Album/01-Symphony.flac",
            size = 35000000L,
            lastModified = "2026-09-26",
            fileType = RemoteFileType.Audio(AudioFormat.FLAC),
        )

    private val mp3File =
        RemoteFile(
            name = "02-Allegro [320k].mp3",
            path = "/music/Lossless/Album/02-Allegro [320k].mp3",
            size = 12400000L,
            lastModified = "2026-09-26",
            fileType = RemoteFileType.Audio(AudioFormat.MP3),
        )

    private val sampleDirectory =
        RemoteDirectory(
            path = "/music/Lossless/Album/",
            name = "Album",
            subDirectories = emptyList(),
            files = listOf(flacFile, mp3File),
        )

    private class TestServerRepository(
        initialServers: List<WebDavServer> = emptyList(),
    ) : ServerRepository {
        private val serversFlow = MutableStateFlow(initialServers)
        private val activeServerFlow = MutableStateFlow(initialServers.firstOrNull { it.isDefault } ?: initialServers.firstOrNull())

        override fun getAllServers(): Flow<List<WebDavServer>> = serversFlow

        override fun getActiveServer(): Flow<WebDavServer?> = activeServerFlow

        override suspend fun getServerById(id: Long): WebDavServer? = serversFlow.value.find { it.id == id }

        override suspend fun saveServer(server: WebDavServer): Long = server.id

        override suspend fun deleteServer(id: Long) {
            serversFlow.value = serversFlow.value.filter { it.id != id }
        }

        override suspend fun setActiveServer(id: Long) {
            activeServerFlow.value = serversFlow.value.find { it.id == id }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeEngine = FakeAudioPlayerEngine()
        fakeStore = FakePlaybackSessionStore()
        fakeServerRepo = TestServerRepository(listOf(sampleServer))
        sessionJob = SupervisorJob()
        sessionScope = kotlinx.coroutines.CoroutineScope(testDispatcher + sessionJob)

        session =
            MusicPlayerAppSessionImpl(
                playerEngine = fakeEngine,
                serverRepository = fakeServerRepo,
                sessionStore = fakeStore,
                coroutineScope = sessionScope,
            )

        mediaSessionCallback = WebDavMediaSessionCallback(fakeEngine)
    }

    @After
    fun tearDown() {
        session.release()
        sessionJob.cancel()
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

    @Test
    fun e2e_audioStreamingAndPlaybackStatePropagation() =
        runTest(testDispatcher) {
            // Set active server
            session.setActiveServer(sampleServer)
            advanceUntilIdle()

            // 1. User starts playback of FLAC track from directory
            session.playDirectoryTrack(sampleDirectory, flacFile)
            advanceUntilIdle()

            // Verify Engine received correct parameters
            assertEquals(sampleServer, fakeEngine.lastServer)
            assertEquals(2, fakeEngine.lastTracks.size)
            assertEquals(0, fakeEngine.lastStartIndex)

            // Verify PlayerSessionState in AppSession
            var state = session.sessionState.value
            assertTrue("Session has track", state.hasTrack)
            assertEquals("01-Symphony.flac", state.currentTrack?.title)
            assertEquals(PlaybackState.Playing, state.playbackState)
            assertEquals(2, state.queue.size)
            assertEquals(0, state.queue.currentIndex)

            // 2. Audio engine updates duration and position
            fakeEngine._durationMs.value = 420000L
            fakeEngine._currentPositionMs.value = 15000L
            advanceUntilIdle()

            state = session.sessionState.value
            assertEquals(420000L, state.durationMs)
            assertEquals(15000L, session.playbackProgress.value.currentPositionMs)
        }

    @Test
    fun e2e_backgroundPlaybackAndMediaSessionLockScreenControls() =
        runTest(testDispatcher) {
            session.setActiveServer(sampleServer)
            session.playDirectoryTrack(sampleDirectory, flacFile)
            advanceUntilIdle()

            // Verify currently Playing
            assertTrue(session.sessionState.value.isPlaying)

            // 1. Lock screen Pause button pressed
            val pauseIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            val handledPause = mediaSessionCallback.handleMediaButtonIntent(pauseIntent)
            assertTrue("Pause media button handled", handledPause)
            advanceUntilIdle()

            assertEquals(1, fakeEngine.pauseCount)
            assertEquals(PlaybackState.Paused, session.sessionState.value.playbackState)

            // 2. Lock screen Play button pressed
            val playIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY)
            val handledPlay = mediaSessionCallback.handleMediaButtonIntent(playIntent)
            assertTrue("Play media button handled", handledPlay)
            advanceUntilIdle()

            assertEquals(1, fakeEngine.playCount)
            assertEquals(PlaybackState.Playing, session.sessionState.value.playbackState)

            // 3. Lock screen Skip Next pressed
            val nextIntent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT)
            val handledNext = mediaSessionCallback.handleMediaButtonIntent(nextIntent)
            assertTrue("Next media button handled", handledNext)
            advanceUntilIdle()

            assertEquals(1, fakeEngine.currentTrackIndex.value)
            assertEquals(
                mp3File.path,
                session.sessionState.value.currentTrack
                    ?.remotePath,
            )

            // 4. SeekTo triggered
            fakeEngine.seekTo(120000L)
            advanceUntilIdle()

            assertEquals(120000L, fakeEngine.seekToPosition)
            assertEquals(120000L, session.playbackProgress.value.currentPositionMs)
        }

    @Test
    fun e2e_sessionPersistenceAndColdResumptionAcrossProcessLifecycle() =
        runTest(testDispatcher) {
            session.setActiveServer(sampleServer)
            session.playDirectoryTrack(sampleDirectory, mp3File)
            advanceUntilIdle()

            fakeEngine._currentPositionMs.value = 45000L
            session.setPlaybackMode(PlaybackMode.SINGLE_LOOP)
            advanceUntilIdle()

            // Wait for debounce auto-save
            advanceTimeBy(3000L)
            advanceUntilIdle()

            // Verify DataStore mock received the persisted session
            val savedData = fakeStore.savedSession
            assertNotNull("Session data was saved to store", savedData)
            val data = savedData!!
            assertEquals(sampleServer.id, data.activeServerId)
            assertEquals(mp3File.path, data.queueTracks[data.currentTrackIndex].remotePath)
            assertEquals(45000L, data.positionMs)
            assertEquals(PlaybackMode.SINGLE_LOOP, data.playbackMode)
            assertEquals(2, data.queueTracks.size)

            // 2. Simulate process termination and recreation of AppSession
            session.release()
            sessionJob.cancel()

            val freshEngine = FakeAudioPlayerEngine()
            val freshJob = SupervisorJob()
            val freshScope = kotlinx.coroutines.CoroutineScope(testDispatcher + freshJob)

            val resumedSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = freshEngine,
                    serverRepository = fakeServerRepo,
                    sessionStore = fakeStore,
                    coroutineScope = freshScope,
                )

            // Advance to trigger auto-resumption in background
            advanceTimeBy(500L)
            advanceUntilIdle()

            val restoredState = resumedSession.sessionState.value
            assertTrue("Restored session has track", restoredState.hasTrack)
            assertEquals("02-Allegro [320k].mp3", restoredState.currentTrack?.title)
            assertEquals(45000L, resumedSession.playbackProgress.value.currentPositionMs)
            assertEquals(PlaybackMode.SINGLE_LOOP, restoredState.playbackMode)
            assertEquals(PlaybackState.Paused, restoredState.playbackState)
            assertEquals(2, restoredState.queue.size)
            assertEquals(1, restoredState.queue.currentIndex)

            resumedSession.release()
            freshJob.cancel()
        }

    @Test
    fun e2e_navigationCoordinatorAndMiniPlayerExpansion() {
        val coordinator = MainNavigationCoordinator()

        // 1. Initial destination is SERVER_LIST
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
        assertFalse("Full player is collapsed", coordinator.uiState.value.isFullPlayerExpanded)

        // 2. Selecting a server switches destination to DIRECTORY_BROWSER
        coordinator.onServerCardClicked(sampleServer)
        assertEquals(AppDestination.DIRECTORY_BROWSER, coordinator.uiState.value.currentDestination)

        // 3. Audio session gets active track -> Mini-player pill becomes visible
        val track = AudioTrack.fromRemoteFile(sampleServer, flacFile)!!
        val activeState =
            PlayerSessionState(
                activeServer = sampleServer,
                queue =
                    com.webdav.player.domain.model
                        .PlaybackQueue(tracks = listOf(track), currentIndex = 0),
                playbackState = PlaybackState.Playing,
            )
        coordinator.onSessionStateChanged(activeState)
        assertTrue("Mini-player is visible", coordinator.uiState.value.isMiniPlayerVisible)

        // 4. User taps floating mini-player pill -> Expands to full player sheet
        coordinator.expandFullPlayer()
        assertTrue("Full player is expanded", coordinator.uiState.value.isFullPlayerExpanded)

        // 5. User dismisses full player sheet via downward gesture or arrow
        coordinator.collapseFullPlayer()
        assertFalse("Full player is collapsed back to mini-player", coordinator.uiState.value.isFullPlayerExpanded)

        // 6. Switching tabs preserves mini-player availability
        coordinator.selectDestination(AppDestination.SERVER_LIST)
        assertEquals(AppDestination.SERVER_LIST, coordinator.uiState.value.currentDestination)
        assertTrue("Mini-player remains visible on server list tab", coordinator.uiState.value.isMiniPlayerVisible)
    }

    @Test
    fun e2e_breadcrumbNavigationHierarchy() {
        val fullPath = "/Music/Lossless/Symphonies/Beethoven"
        val breadcrumbs = BreadcrumbNavigationHelper.buildBreadcrumbs(sampleServer, fullPath)

        assertEquals(5, breadcrumbs.size)
        assertEquals("Synology NAS", breadcrumbs[0].name)
        assertEquals("/", breadcrumbs[0].path)

        assertEquals("Music", breadcrumbs[1].name)
        assertEquals("/Music/", breadcrumbs[1].path)

        assertEquals("Beethoven", breadcrumbs[4].name)
        assertEquals("/Music/Lossless/Symphonies/Beethoven/", breadcrumbs[4].path)

        // Ancestor jump to /Music
        val targetBreadcrumb = breadcrumbs[1]
        val isAncestor = BreadcrumbNavigationHelper.isAncestor(targetBreadcrumb.path, fullPath)
        assertTrue("Target is ancestor of current path", isAncestor)
    }

    @Test
    fun e2e_audioQualityBadgesAndColorExtractionFallback() {
        val flacBadge = AudioQualityBadgeHelper.getBadge(flacFile)
        assertNotNull(flacBadge)
        assertEquals("FLAC", flacBadge!!.label)
        assertTrue(flacBadge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, flacBadge.qualityLevel)

        val mp3Badge = AudioQualityBadgeHelper.getBadge(mp3File)
        assertNotNull(mp3Badge)
        assertEquals("MP3 320k", mp3Badge!!.label)
        assertEquals(AudioQualityLevel.HIGH_QUALITY, mp3Badge.qualityLevel)

        // Fallback color extraction when no thumbnail exists
        val fallback =
            ArtworkColorExtractor.createFallbackColors(
                defaultSurfaceColor = Color(0xFF1E1F25),
                defaultBackgroundColor = Color(0xFF121318),
            )
        assertNotNull(fallback)
        assertTrue("Is fallback color scheme", fallback.isFallback)
        assertEquals(Color(0xFF1E1F25), fallback.dominantColor)
    }

    @Test
    fun e2e_lyricSyncAndTapToSeekInteraction() {
        val sampleLrc =
            """
            [00:00.00]Beethoven Symphony Intro
            [00:15.50]First Theme
            [00:45.00]Second Theme
            [01:20.00]Development Section
            """.trimIndent()

        val lyrics = LrcParser.parse(sampleLrc)
        assertTrue(lyrics.isSynchronized)
        assertEquals(4, lyrics.lines.size)

        // At position 20s (20,000ms), active line should be "First Theme" (index 1)
        val activeIndex = lyrics.findActiveLineIndex(20000L)
        assertEquals(1, activeIndex)
        assertEquals("First Theme", lyrics.lines[activeIndex].text)

        // Tap to seek: clicking line 3 ("Development Section") seeks to 80,000ms
        val targetLine = lyrics.lines[3]
        fakeEngine.seekTo(targetLine.timestampMs)
        assertEquals(80000L, fakeEngine.seekToPosition)
    }
}
