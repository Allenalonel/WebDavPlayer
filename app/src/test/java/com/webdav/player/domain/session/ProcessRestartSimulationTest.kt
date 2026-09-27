package com.webdav.player.domain.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.repository.DataStorePlaybackSessionStore
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProcessRestartSimulationTest {
    private val testDispatcher = StandardTestDispatcher()
    private var dataStoreScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionStore: DataStorePlaybackSessionStore

    private val testServer =
        WebDavServer(
            id = 100L,
            name = "Synology NAS",
            url = "https://synology.local:5001",
            port = 5001,
            pathPrefix = "/",
            username = "musicuser",
            password = "secretpassword",
            isDefault = true,
        )

    private val track1 =
        AudioTrack(
            id = "100:/Music/Rock/01-Intro.mp3",
            serverId = 100L,
            remotePath = "/Music/Rock/01-Intro.mp3",
            title = "Intro",
            artist = "Rock Band",
            album = "Greatest Hits",
            durationMs = 120000L,
            size = 3000000L,
            format = AudioFormat.MP3,
        )

    private val track2 =
        AudioTrack(
            id = "100:/Music/Rock/02-Solo.flac",
            serverId = 100L,
            remotePath = "/Music/Rock/02-Solo.flac",
            title = "Solo",
            artist = "Rock Band",
            album = "Greatest Hits",
            durationMs = 260000L,
            size = 18000000L,
            format = AudioFormat.FLAC,
        )

    private val track3 =
        AudioTrack(
            id = "100:/Music/Rock/03-Outro.wav",
            serverId = 100L,
            remotePath = "/Music/Rock/03-Outro.wav",
            title = "Outro",
            artist = "Rock Band",
            album = "Greatest Hits",
            durationMs = 150000L,
            size = 22000000L,
            format = AudioFormat.WAV,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        dataStoreScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStoreFile = File(context.filesDir, "datastore/process_test_${UUID.randomUUID()}.preferences_pb")

        dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { dataStoreFile },
            )
        sessionStore = DataStorePlaybackSessionStore(dataStore, { dataStoreFile })
    }

    @After
    fun tearDown() {
        dataStoreScope.coroutineContext[Job]?.cancel()
        if (dataStoreFile.exists()) {
            dataStoreFile.delete()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun simulatedProcessRestart_persistsStateAndResumesPlaybackSeamlessly() =
        runTest(testDispatcher) {
            val serverRepo = InMemoryServerRepository(mutableListOf(testServer))

            // === PROCESS 1 ===
            val process1Scope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob())
            val engine1 = FakeAudioPlayerEngine()
            val session1 =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine1,
                    serverRepository = serverRepo,
                    sessionStore = sessionStore,
                    coroutineScope = process1Scope,
                )
            session1.setActiveServer(testServer)

            val rockDir =
                RemoteDirectory(
                    path = "/Music/Rock/",
                    name = "Rock",
                    files =
                        listOf(
                            RemoteFile(name = "01-Intro.mp3", path = "/Music/Rock/01-Intro.mp3", size = 3000000L),
                            RemoteFile(name = "02-Solo.flac", path = "/Music/Rock/02-Solo.flac", size = 18000000L),
                            RemoteFile(name = "03-Outro.wav", path = "/Music/Rock/03-Outro.wav", size = 22000000L),
                        ),
                )

            // User plays track 2 (index 1) in the directory
            session1.playDirectoryTrack(rockDir, rockDir.files[1])
            runCurrent()

            // User seeks to 48000ms and switches playback mode to SINGLE_LOOP
            session1.seekTo(48000L)
            session1.setPlaybackMode(PlaybackMode.SINGLE_LOOP)
            runCurrent()

            // Flush on app exit / termination
            try {
                session1.flushSession()
            } catch (e: Throwable) {
                println("FLUSH ERROR: $e")
                e.printStackTrace()
            }
            testDispatcher.scheduler.advanceUntilIdle()

            val savedInStore = sessionStore.getSavedSession()
            assertNotNull(savedInStore)

            // Simulate process death: release and destroy Process 1
            session1.release()
            process1Scope.coroutineContext[Job]?.cancel()
            testDispatcher.scheduler.advanceUntilIdle()

            // === PROCESS 2 (COLD START) ===
            val process2Scope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob())
            val engine2 = FakeAudioPlayerEngine()
            val session2 =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine2,
                    serverRepository = serverRepo,
                    sessionStore = sessionStore,
                    coroutineScope = process2Scope,
                )

            // Advance to let cold-start restoration complete
            advanceUntilIdle()

            // Verify pre-populated mini-player state
            val restoredState = session2.sessionState.value
            assertNotNull(restoredState.activeServer)
            assertEquals(100L, restoredState.activeServer?.id)
            assertEquals(3, restoredState.queue.size)
            assertEquals(1, restoredState.queue.currentIndex)
            assertEquals("02-Solo.flac", restoredState.currentTrack?.title)
            assertTrue("Restored mini-player must be in paused state", restoredState.isPaused)
            assertEquals(48000L, session2.playbackProgress.value.currentPositionMs)
            assertEquals(PlaybackMode.SINGLE_LOOP, restoredState.playbackMode)
            assertEquals("/Music/Rock/", restoredState.currentDirectoryPath)

            // Engine must be idle before user taps play
            assertTrue(engine2.playbackState.value is PlaybackState.Idle)
            assertEquals(-1, engine2.lastStartIndex)

            // User single-taps play on the restored mini-player
            session2.togglePlayPause()
            runCurrent()

            // Engine streams from the exact 48000ms offset
            assertEquals(testServer, engine2.lastServer)
            assertEquals(3, engine2.lastTracks.size)
            assertEquals(1, engine2.lastStartIndex)
            assertEquals(48000L, engine2.lastStartPositionMs)
            assertTrue(session2.sessionState.value.isPlaying)

            session2.pause()
            runCurrent()
            session2.release()
            process2Scope.coroutineContext[Job]?.cancel()
        }

    @Test
    fun coldStart_whenServerWasDeletedWhileAppWasClosed_cleansUpGracefully() =
        runTest(testDispatcher) {
            val serverRepo = InMemoryServerRepository(mutableListOf(testServer))

            // Save a session with testServer (id 100)
            sessionStore.saveSession(
                com.webdav.player.domain.model.PlaybackSessionData(
                    activeServerId = 100L,
                    currentDirectoryPath = "/Music/",
                    queueTracks = listOf(track1),
                    currentTrackIndex = 0,
                    positionMs = 15000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                ),
            )

            // Server is deleted while app is closed
            serverRepo.deleteServer(100L)

            // Cold start in Process 2
            val processScope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob())
            val engine = FakeAudioPlayerEngine()
            val session =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine,
                    serverRepository = serverRepo,
                    sessionStore = sessionStore,
                    coroutineScope = processScope,
                )

            advanceUntilIdle()

            val state = session.sessionState.value
            assertNull(state.activeServer)
            assertTrue(state.queue.isEmpty)
            assertTrue(state.isIdle)
            assertNull(state.errorMessage)

            session.release()
            processScope.coroutineContext[Job]?.cancel()
        }

    @Test
    fun coldStart_whenRemoteFileIsUnavailable_displaysErrorGracefully() =
        runTest(testDispatcher) {
            val serverRepo = InMemoryServerRepository(mutableListOf(testServer))

            sessionStore.saveSession(
                com.webdav.player.domain.model.PlaybackSessionData(
                    activeServerId = 100L,
                    currentDirectoryPath = "/Music/",
                    queueTracks = listOf(track1),
                    currentTrackIndex = 0,
                    positionMs = 15000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                ),
            )

            val processScope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob())
            val engine = FakeAudioPlayerEngine()
            val session =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine,
                    serverRepository = serverRepo,
                    sessionStore = sessionStore,
                    coroutineScope = processScope,
                )

            advanceUntilIdle()

            // Mini player restored
            assertEquals(track1, session.sessionState.value.currentTrack)

            // User taps play, but network fails
            session.play()
            runCurrent()

            engine._playbackState.value = PlaybackState.Error("HTTP 404: Remote file not found on WebDAV")
            runCurrent()

            val state = session.sessionState.value
            assertTrue(state.playbackState is PlaybackState.Error)
            assertEquals("HTTP 404: Remote file not found on WebDAV", state.errorMessage)
            assertFalse(state.isPlaying)

            session.release()
            processScope.coroutineContext[Job]?.cancel()
        }

    @Test
    fun skipToNext_followedByProcessRestart_resumesAtNextTrack() =
        runTest(testDispatcher) {
            val serverRepo = InMemoryServerRepository(mutableListOf(testServer))

            // === PROCESS 1 ===
            val process1Scope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob())
            val engine1 = FakeAudioPlayerEngine()
            val session1 =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine1,
                    serverRepository = serverRepo,
                    sessionStore = sessionStore,
                    coroutineScope = process1Scope,
                )
            session1.setActiveServer(testServer)

            val rockDir =
                RemoteDirectory(
                    path = "/Music/Rock/",
                    name = "Rock",
                    files =
                        listOf(
                            RemoteFile(name = "01-Intro.mp3", path = "/Music/Rock/01-Intro.mp3", size = 3000000L),
                            RemoteFile(name = "02-Solo.flac", path = "/Music/Rock/02-Solo.flac", size = 18000000L),
                            RemoteFile(name = "03-Outro.wav", path = "/Music/Rock/03-Outro.wav", size = 22000000L),
                        ),
                )

            // 1. User starts playing track 1 ("01-Intro.mp3", index 0)
            session1.playDirectoryTrack(rockDir, rockDir.files[0])
            runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(0, session1.sessionState.value.queue.currentIndex)

            // 2. User taps "Next" on the player UI to play track 2 ("02-Solo.flac", index 1)
            session1.skipToNext()
            runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, session1.sessionState.value.queue.currentIndex)

            // Wait briefly for background DataStore IO to finish writing to disk
            var savedSession: com.webdav.player.domain.model.PlaybackSessionData? = null
            for (i in 0 until 50) {
                testDispatcher.scheduler.advanceUntilIdle()
                runCurrent()
                savedSession = sessionStore.getSavedSession()
                if (savedSession != null && savedSession.currentTrackIndex == 1) break
                Thread.sleep(50)
            }
            assertNotNull(savedSession)
            assertEquals(1, savedSession?.currentTrackIndex)

            // 3. User exits app immediately (process terminates)
            session1.release()
            process1Scope.coroutineContext[Job]?.cancel()
            testDispatcher.scheduler.advanceUntilIdle()

            // === PROCESS 2 (COLD START RESTART) ===
            val process2Scope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob())
            val engine2 = FakeAudioPlayerEngine()
            val session2 =
                MusicPlayerAppSessionImpl(
                    playerEngine = engine2,
                    serverRepository = serverRepo,
                    sessionStore = sessionStore,
                    coroutineScope = process2Scope,
                )

            advanceUntilIdle()

            // 4. Verify cold start restored session remembers track 2 ("02-Solo.flac", index 1), NOT track 1!
            val restoredState = session2.sessionState.value
            assertEquals(3, restoredState.queue.size)
            assertEquals(1, restoredState.queue.currentIndex)
            assertEquals("02-Solo.flac", restoredState.currentTrack?.title)

            session2.release()
            process2Scope.coroutineContext[Job]?.cancel()
        }

    private class InMemoryServerRepository(
        private val servers: MutableList<WebDavServer>,
    ) : ServerRepository {
        private val flow = MutableStateFlow<List<WebDavServer>>(servers.toList())

        override fun getAllServers(): Flow<List<WebDavServer>> = flow

        override fun getActiveServer(): Flow<WebDavServer?> =
            flow.map { list ->
                list.firstOrNull { it.isDefault }
            }

        override suspend fun getServerById(id: Long): WebDavServer? = flow.value.firstOrNull { it.id == id }

        override suspend fun saveServer(server: WebDavServer): Long {
            servers.add(server)
            flow.value = servers.toList()
            return server.id
        }

        override suspend fun deleteServer(id: Long) {
            servers.removeAll { it.id == id }
            flow.value = servers.toList()
        }

        override suspend fun setActiveServer(id: Long) {
            servers.replaceAll { it.copy(isDefault = it.id == id) }
            flow.value = servers.toList()
        }
    }
}
