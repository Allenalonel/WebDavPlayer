package com.webdav.player.domain.session

import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.QueueOrigin
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.PlaybackSessionStore
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackTimelineCoordinatorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val coordinatorJob = SupervisorJob()
    private val coordinatorScope = CoroutineScope(testDispatcher + coordinatorJob)

    private val fakeEngine = FakeAudioPlayerEngine()
    private val sessionStateFlow = MutableStateFlow(PlayerSessionState())
    private val playbackProgressFlow = MutableStateFlow(PlaybackProgress.ZERO)
    private val virtualTimelineEngine = VirtualTimelineEngine()
    private val isPrerollAppended = AtomicBoolean(false)

    private val server1 =
        WebDavServer(
            id = 1L,
            name = "Server 1",
            url = "http://server1.test/webdav",
            username = "user1",
            password = "pass1",
        )
    private val server2 =
        WebDavServer(
            id = 2L,
            name = "Server 2",
            url = "http://server2.test/webdav",
            username = "user2",
            password = "pass2",
        )

    private val serverMap = mutableMapOf<Long, WebDavServer>(1L to server1, 2L to server2)

    private val testServerRepo =
        object : ServerRepository {
            override fun getAllServers(): Flow<List<WebDavServer>> = flowOf(serverMap.values.toList())

            override fun getActiveServer(): Flow<WebDavServer?> = flowOf(serverMap[1L])

            override suspend fun getServerById(id: Long): WebDavServer? = serverMap[id]

            override suspend fun saveServer(server: WebDavServer): Long = server.id

            override suspend fun deleteServer(id: Long) {}

            override suspend fun setActiveServer(id: Long) {}
        }

    private lateinit var queueManager: PlaybackQueueManager
    private lateinit var faultTolerance: PlaybackFaultTolerance
    private lateinit var persistenceCoordinator: SessionPersistenceCoordinator
    private lateinit var coordinator: PlaybackTimelineCoordinator

    private val track1 =
        AudioTrack(
            id = "track-1",
            serverId = 1L,
            remotePath = "/music/song1.mp3",
            title = "Song 1",
            durationMs = 180_000L,
            format = AudioFormat.MP3,
            size = 4_000_000L,
        )

    private val track2 =
        AudioTrack(
            id = "track-2",
            serverId = 2L,
            remotePath = "/music/song2.mp3",
            title = "Song 2",
            durationMs = 210_000L,
            format = AudioFormat.MP3,
            size = 5_000_000L,
        )

    private val track3 =
        AudioTrack(
            id = "track-3",
            serverId = 2L,
            remotePath = "/music/song3.mp3",
            title = "Song 3",
            durationMs = 240_000L,
            format = AudioFormat.MP3,
            size = 6_000_000L,
        )

    @Before
    fun setUp() {
        queueManager = PlaybackQueueManager(sessionStateFlow, testServerRepo, null) {}
        faultTolerance = PlaybackFaultTolerance(sessionStateFlow, fakeEngine, null, queueManager, coordinatorScope)
        persistenceCoordinator =
            SessionPersistenceCoordinator(
                sessionStore = null,
                serverRepository = testServerRepo,
                trackMetadataRepository = null,
                cueSheetResolver = null,
                playerEngine = fakeEngine,
                virtualTimelineEngine = virtualTimelineEngine,
                sessionStateFlow = sessionStateFlow,
                playbackProgressFlow = playbackProgressFlow,
                coroutineScope = coordinatorScope,
                periodicDispatcher = testDispatcher,
                getActiveParentAudioTrack = { null },
                getActiveCuePath = { null },
                onCueSessionRestored = { _, _ -> },
                onOrdinarySessionRestored = {},
            )

        coordinator =
            PlaybackTimelineCoordinator(
                playerEngine = fakeEngine,
                serverRepository = testServerRepo,
                sessionStateFlow = sessionStateFlow,
                playbackProgressFlow = playbackProgressFlow,
                virtualTimelineEngine = virtualTimelineEngine,
                faultTolerance = faultTolerance,
                queueManager = queueManager,
                persistenceCoordinator = persistenceCoordinator,
                coroutineScope = coordinatorScope,
                progressDispatcher = testDispatcher,
                isPrerollAppended = isPrerollAppended,
                getHasStreamLoaded = { true },
                clearPrerollIfActive = { isPrerollAppended.set(false) },
                onRestoreSession = {},
                onSetActiveServer = {},
                onSkipToNext = {},
                onTransitionToQueueIndex = { _, _ -> },
            )
        coordinator.startObservations()
    }

    @After
    fun tearDown() {
        coordinator.cancelObservations()
        coordinatorJob.cancel()
    }

    @Test
    fun crossServerTrackTransition_atomicallyUpdatesSessionAndEngineActiveServer() =
        runTest(testDispatcher) {
            val crossServerQueue =
                PlaybackQueue(
                    tracks = listOf(track1, track2),
                    currentIndex = 0,
                    origin = QueueOrigin.PLAYLIST,
                )
            sessionStateFlow.value =
                PlayerSessionState(
                    activeServer = server1,
                    queue = crossServerQueue,
                    playbackState = PlaybackState.Playing,
                    durationMs = track1.durationMs,
                )
            fakeEngine.lastServer = server1
            isPrerollAppended.set(true)

            // Trigger natural track transition from track1 (server1) to track2 (server2)
            fakeEngine.simulateTrackTransition()
            advanceUntilIdle()

            assertFalse("isPrerollAppended must reset on transition", isPrerollAppended.get())
            val state = sessionStateFlow.value
            assertEquals("Current queue index must advance to 1", 1, state.queue.currentIndex)
            assertEquals("Active server in sessionState must update to server2", server2, state.activeServer)
            assertEquals("Engine active server must update to server2", server2, fakeEngine.lastServer)
            assertTrue("Engine setActiveServer must be called", fakeEngine.activeServerCalls > 0)
            assertEquals("Progress current position must be reset to 0L", 0L, playbackProgressFlow.value.currentPositionMs)
            assertEquals("Progress duration must match new track", track2.durationMs, playbackProgressFlow.value.durationMs)
        }

    @Test
    fun sameServerTrackTransition_retainsActiveServerWithoutRedundantEngineServerCalls() =
        runTest(testDispatcher) {
            val sameServerQueue =
                PlaybackQueue(
                    tracks = listOf(track2, track3),
                    currentIndex = 0,
                    origin = QueueOrigin.PLAYLIST,
                )
            sessionStateFlow.value =
                PlayerSessionState(
                    activeServer = server2,
                    queue = sameServerQueue,
                    playbackState = PlaybackState.Playing,
                    durationMs = track2.durationMs,
                )
            fakeEngine.lastServer = server2
            val initialEngineServerCalls = fakeEngine.activeServerCalls

            fakeEngine.simulateTrackTransition()
            advanceUntilIdle()

            val state = sessionStateFlow.value
            assertEquals(1, state.queue.currentIndex)
            assertEquals(server2, state.activeServer)
            assertEquals(server2, fakeEngine.lastServer)
            assertEquals(
                "Engine setActiveServer should not be invoked when server does not change",
                initialEngineServerCalls,
                fakeEngine.activeServerCalls,
            )
        }

    @Test
    fun crossServerTransition_whenRepositoryFailsToResolve_maintainsPriorServerContext() =
        runTest(testDispatcher) {
            val unknownServerTrack = track2.copy(serverId = 999L)
            val queue =
                PlaybackQueue(
                    tracks = listOf(track1, unknownServerTrack),
                    currentIndex = 0,
                    origin = QueueOrigin.PLAYLIST,
                )
            sessionStateFlow.value =
                PlayerSessionState(
                    activeServer = server1,
                    queue = queue,
                    playbackState = PlaybackState.Playing,
                    durationMs = track1.durationMs,
                )
            fakeEngine.lastServer = server1
            val initialEngineServerCalls = fakeEngine.activeServerCalls

            fakeEngine.simulateTrackTransition()
            advanceUntilIdle()

            val state = sessionStateFlow.value
            assertEquals(1, state.queue.currentIndex)
            assertEquals("Active server should remain server1 if server 999 not found", server1, state.activeServer)
            assertEquals(server1, fakeEngine.lastServer)
            assertEquals(initialEngineServerCalls, fakeEngine.activeServerCalls)
        }
}
