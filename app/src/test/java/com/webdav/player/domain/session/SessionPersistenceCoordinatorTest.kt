package com.webdav.player.domain.session

import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPersistenceCoordinatorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher + SupervisorJob())

    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var fakeStore: FakePlaybackSessionStore
    private lateinit var virtualTimelineEngine: VirtualTimelineEngine
    private val sessionStateFlow = MutableStateFlow(PlayerSessionState())
    private val playbackProgressFlow = MutableStateFlow(PlaybackProgress.ZERO)

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "TestServer",
            url = "https://webdav.example.com",
            username = "user",
            password = "pwd",
        )

    private val testTrack =
        AudioTrack(
            id = "1:/music/song.mp3",
            serverId = 1L,
            remotePath = "/music/song.mp3",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 200_000L,
            size = 5_000_000L,
            format = AudioFormat.MP3,
        )

    private class TestServerRepo(
        private val servers: List<WebDavServer> = emptyList(),
    ) : ServerRepository {
        override fun getAllServers(): Flow<List<WebDavServer>> = flowOf(servers)

        override fun getActiveServer(): Flow<WebDavServer?> = flowOf(servers.firstOrNull())

        override suspend fun getServerById(id: Long): WebDavServer? = servers.firstOrNull { it.id == id }

        override suspend fun saveServer(server: WebDavServer): Long = server.id

        override suspend fun deleteServer(id: Long) {}

        override suspend fun setActiveServer(id: Long) {}
    }

    private class TestMetadataRepo : TrackMetadataRepository {
        val cached = mutableMapOf<Pair<Long, String>, TrackMetadata>()

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> = emptyFlow()

        override fun getMetadataForPathsFlow(
            serverId: Long,
            remotePaths: List<String>,
        ): Flow<List<TrackMetadata>> = emptyFlow()

        override fun getMetadataFlow(
            serverId: Long,
            remotePath: String,
        ): Flow<TrackMetadata?> = emptyFlow()

        override suspend fun getCachedMetadata(
            serverId: Long,
            remotePath: String,
        ): TrackMetadata? = cached[serverId to remotePath]

        override suspend fun resolveMetadata(
            server: WebDavServer,
            files: List<RemoteFile>,
            forceRefresh: Boolean,
        ) {}

        override suspend fun resolveSingleTrackMetadata(
            server: WebDavServer,
            file: RemoteFile,
        ): TrackMetadata = cached[server.id to file.path] ?: TrackMetadata(serverId = server.id, remotePath = file.path, title = "Resolved")
    }

    private class TestCueSheetResolver : CueSheetResolver {
        val virtualTracksMap = mutableMapOf<String, List<VirtualTrack>>()

        override suspend fun resolveDirectoryCueAlbums(
            server: WebDavServer,
            directory: RemoteDirectory,
            parentDurationsByPath: Map<String, Long>,
        ): List<CueAlbumItem> = emptyList()

        override suspend fun resolveVirtualTracks(
            server: WebDavServer,
            cuePath: String,
            parentAudioPath: String,
            totalDurationMs: Long?,
        ): List<VirtualTrack> = virtualTracksMap[cuePath] ?: emptyList()

        override fun clearCache() {}
    }

    @Before
    fun setUp() {
        fakeEngine = FakeAudioPlayerEngine()
        fakeStore = FakePlaybackSessionStore()
        virtualTimelineEngine = VirtualTimelineEngine()
        sessionStateFlow.value = PlayerSessionState()
        playbackProgressFlow.value = PlaybackProgress.ZERO
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private fun createCoordinator(
        serverRepo: ServerRepository? = TestServerRepo(listOf(testServer)),
        metadataRepo: TrackMetadataRepository? = null,
        cueResolver: CueSheetResolver? = null,
        activeParentAudioTrack: () -> AudioTrack? = { null },
        activeCuePath: () -> String? = { null },
        onCueRestored: (AudioTrack, String) -> Unit = { _, _ -> },
        onOrdinaryRestored: () -> Unit = {},
    ): SessionPersistenceCoordinator =
        SessionPersistenceCoordinator(
            sessionStore = fakeStore,
            serverRepository = serverRepo,
            trackMetadataRepository = metadataRepo,
            cueSheetResolver = cueResolver,
            playerEngine = fakeEngine,
            virtualTimelineEngine = virtualTimelineEngine,
            sessionStateFlow = sessionStateFlow,
            playbackProgressFlow = playbackProgressFlow,
            coroutineScope = testScope,
            periodicDispatcher = testDispatcher,
            getActiveParentAudioTrack = activeParentAudioTrack,
            getActiveCuePath = activeCuePath,
            onCueSessionRestored = onCueRestored,
            onOrdinarySessionRestored = onOrdinaryRestored,
        )

    @Test
    fun restoreOrdinarySession_restoresQueueAndProgressAccurately() =
        testScope.runTest {
            val savedSession =
                PlaybackSessionData(
                    activeServerId = testServer.id,
                    currentDirectoryPath = "/Rock/",
                    queueTracks = listOf(testTrack),
                    currentTrackIndex = 0,
                    positionMs = 45_000L,
                    playbackMode = PlaybackMode.SHUFFLE,
                    cleanExit = false,
                )
            fakeStore.savedSession = savedSession

            val coordinator = createCoordinator()
            coordinator.restoreSession()

            val state = sessionStateFlow.value
            assertEquals(testServer, state.activeServer)
            assertEquals(1, state.queue.size)
            assertEquals(testTrack, state.currentTrack)
            assertEquals(PlaybackState.Paused, state.playbackState)
            assertEquals(PlaybackMode.SHUFFLE, state.playbackMode)
            assertEquals("/Rock/", state.currentDirectoryPath)
            assertTrue(state.wasKilledBySystem)

            val progress = playbackProgressFlow.value
            assertEquals(45_000L, progress.currentPositionMs)
            assertEquals(testTrack.durationMs, progress.durationMs)
            assertTrue(coordinator.isRestored.value)
        }

    @Test
    fun restoreOrdinarySession_enrichesWithCachedMetadata() =
        testScope.runTest {
            val metadataRepo = TestMetadataRepo()
            metadataRepo.cached[testServer.id to testTrack.remotePath] =
                TrackMetadata(
                    serverId = testServer.id,
                    remotePath = testTrack.remotePath,
                    title = "Enriched Title",
                    artist = "Enriched Artist",
                    album = "Enriched Album",
                )

            val savedSession =
                PlaybackSessionData(
                    activeServerId = testServer.id,
                    currentDirectoryPath = "/",
                    queueTracks = listOf(testTrack),
                    currentTrackIndex = 0,
                    positionMs = 10_000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cleanExit = true,
                )
            fakeStore.savedSession = savedSession

            val coordinator = createCoordinator(metadataRepo = metadataRepo)
            coordinator.restoreSession()

            val current = sessionStateFlow.value.currentTrack
            assertNotNull(current)
            assertEquals("Enriched Title", current?.title)
            assertEquals("Enriched Artist", current?.artist)
            assertEquals("Enriched Album", current?.album)
            assertFalse(sessionStateFlow.value.wasKilledBySystem)
        }

    @Test
    fun restoreSession_ignoresWhenServerNoLongerExists() =
        testScope.runTest {
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 999L,
                    currentDirectoryPath = "/",
                    queueTracks = listOf(testTrack),
                    currentTrackIndex = 0,
                    positionMs = 10_000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            val coordinator = createCoordinator(serverRepo = TestServerRepo(emptyList()))
            coordinator.restoreSession()

            assertNull(sessionStateFlow.value.activeServer)
            assertTrue(sessionStateFlow.value.queue.isEmpty)
            assertTrue(coordinator.isRestored.value)
        }

    @Test
    fun restoreVirtualTrackSession_reconstructsVirtualTimeline() =
        testScope.runTest {
            val cueResolver = TestCueSheetResolver()
            val vt1 = VirtualTrack(1, "Virtual Intro", "Virtual Artist", 0L, 60_000L, "/album/disc.flac")
            val vt2 = VirtualTrack(2, "Virtual Main", "Virtual Artist", 60_000L, 180_000L, "/album/disc.flac")
            cueResolver.virtualTracksMap["/album/disc.cue"] = listOf(vt1, vt2)

            val parentTrack =
                AudioTrack(
                    id = "1:/album/disc.flac",
                    serverId = testServer.id,
                    remotePath = "/album/disc.flac",
                    title = "disc.flac",
                    durationMs = 180_000L,
                    format = AudioFormat.FLAC,
                )

            val savedSession =
                PlaybackSessionData(
                    activeServerId = testServer.id,
                    currentDirectoryPath = "/album/",
                    queueTracks = listOf(parentTrack),
                    currentTrackIndex = 1,
                    positionMs = 75_000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cuePath = "/album/disc.cue",
                    virtualTrackNumber = 2,
                    virtualPositionMs = 15_000L,
                    cleanExit = false,
                )
            fakeStore.savedSession = savedSession

            var restoredParent: AudioTrack? = null
            var restoredCue: String? = null
            val coordinator =
                createCoordinator(
                    cueResolver = cueResolver,
                    onCueRestored = { parent, cue ->
                        restoredParent = parent
                        restoredCue = cue
                    },
                )

            coordinator.restoreSession()

            assertTrue(virtualTimelineEngine.isActive)
            assertEquals(vt2, virtualTimelineEngine.activeTrack)
            assertEquals(parentTrack.id, restoredParent?.id)
            assertEquals("/album/disc.cue", restoredCue)

            val state = sessionStateFlow.value
            assertEquals(2, state.queue.size)
            assertEquals(1, state.queue.currentIndex)
            assertEquals("Virtual Main", state.currentTrack?.title)
            assertEquals(120_000L, state.durationMs)
            assertTrue(state.wasKilledBySystem)

            val progress = playbackProgressFlow.value
            assertEquals(15_000L, progress.currentPositionMs)
            assertEquals(120_000L, progress.durationMs)
        }

    @Test
    fun flushSession_savesActiveStateIntoSessionStore() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.markRestored()

            sessionStateFlow.value =
                PlayerSessionState(
                    activeServer = testServer,
                    currentDirectoryPath = "/Classical/",
                    queue = PlaybackQueue(listOf(testTrack), currentIndex = 0),
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            playbackProgressFlow.value = PlaybackProgress(currentPositionMs = 30_000L, durationMs = 200_000L)

            coordinator.flushSession()

            val saved = fakeStore.savedSession
            assertNotNull(saved)
            assertEquals(testServer.id, saved?.activeServerId)
            assertEquals("/Classical/", saved?.currentDirectoryPath)
            assertEquals(1, saved?.queueTracks?.size)
            assertEquals(30_000L, saved?.positionMs)
            assertEquals(PlaybackMode.LIST_LOOP, saved?.playbackMode)
            assertTrue(saved?.cleanExit == true)
        }

    @Test
    fun periodicFlush_savesPositionWhenPlaying() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.markRestored()

            sessionStateFlow.value =
                PlayerSessionState(
                    activeServer = testServer,
                    queue = PlaybackQueue(listOf(testTrack), currentIndex = 0),
                    playbackState = PlaybackState.Playing,
                )
            fakeEngine._playbackState.value = PlaybackState.Playing
            fakeEngine._currentPositionMs.value = 12_345L

            coordinator.startPeriodicFlush()
            coordinator.flushSession()
            advanceTimeBy(1500L)

            val saved = fakeStore.savedSession
            assertNotNull(saved)
            assertEquals(12_345L, saved?.positionMs)

            coordinator.stopPeriodicFlush()
        }

    @Test
    fun directoryMemory_tracksAndRestoresDirectoryPerServer() =
        testScope.runTest {
            val coordinator = createCoordinator()
            coordinator.markRestored()

            sessionStateFlow.value = PlayerSessionState(activeServer = testServer)
            coordinator.setCurrentDirectoryPath("/Jazz/Miles/")

            assertEquals("/Jazz/Miles/", coordinator.getLastDirectoryForServer(testServer.id))
            assertEquals("/", coordinator.getLastDirectoryForServer(999L))
        }
}
