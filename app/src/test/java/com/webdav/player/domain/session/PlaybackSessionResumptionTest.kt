package com.webdav.player.domain.session

import com.webdav.player.data.cue.CueParser
import com.webdav.player.data.cue.CueTextCache
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionResumptionTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var fakeStore: FakePlaybackSessionStore
    private lateinit var fakeServerRepo: TestServerRepository
    private lateinit var sessionJob: kotlinx.coroutines.CompletableJob
    private lateinit var sessionScope: kotlinx.coroutines.CoroutineScope
    private lateinit var session: MusicPlayerAppSessionImpl

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Home NAS",
            url = "http://192.168.1.100:8080/webdav",
            port = 8080,
            pathPrefix = "/webdav",
            username = "admin",
            password = "password123",
            isDefault = true,
        )

    private val track1 =
        AudioTrack(
            id = "1:/Music/Jazz/track1.mp3",
            serverId = 1L,
            remotePath = "/Music/Jazz/track1.mp3",
            title = "Autumn Leaves",
            artist = "Bill Evans",
            album = "Portrait in Jazz",
            durationMs = 210000L,
            size = 8000000L,
            format = AudioFormat.MP3,
            coverThumbnailPath = "/covers/1.png",
        )

    private val track2 =
        AudioTrack(
            id = "1:/Music/Jazz/track2.flac",
            serverId = 1L,
            remotePath = "/Music/Jazz/track2.flac",
            title = "Blue in Green",
            artist = "Miles Davis",
            album = "Kind of Blue",
            durationMs = 330000L,
            size = 25000000L,
            format = AudioFormat.FLAC,
            coverThumbnailPath = "/covers/2.png",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeEngine = FakeAudioPlayerEngine()
        fakeStore = FakePlaybackSessionStore()
        fakeServerRepo = TestServerRepository(listOf(testServer))
        sessionJob = SupervisorJob()
        sessionScope = kotlinx.coroutines.CoroutineScope(testDispatcher + sessionJob)
    }

    @After
    fun tearDown() {
        if (::session.isInitialized) {
            session.release()
        }
        CueTextCache.clear()
        sessionJob.cancel()
        Dispatchers.resetMain()
    }

    private fun createSession(
        store: FakePlaybackSessionStore = fakeStore,
        metadataRepo: TrackMetadataRepository? = null,
        client: WebDavClient? = null,
    ): MusicPlayerAppSessionImpl =
        MusicPlayerAppSessionImpl(
            playerEngine = fakeEngine,
            serverRepository = fakeServerRepo,
            trackMetadataRepository = metadataRepo,
            sessionStore = store,
            webDavClient = client,
            coroutineScope = sessionScope,
        )

    private val darkSideTrack =
        AudioTrack(
            id = "1:/Music/PinkFloyd/dark_side.flac",
            serverId = 1L,
            remotePath = "/Music/PinkFloyd/dark_side.flac",
            title = "dark_side.flac",
            artist = "Pink Floyd",
            album = "The Dark Side of the Moon",
            durationMs = 2580000L,
            size = 350000000L,
            format = AudioFormat.FLAC,
        )

    private val cueContentSample =
        """
        REM GENRE Rock
        REM DATE 1973
        PERFORMER "Pink Floyd"
        TITLE "The Dark Side of the Moon"
        FILE "dark_side.flac" WAVE
          TRACK 01 AUDIO
            TITLE "Speak to Me"
            PERFORMER "Pink Floyd"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "Breathe (In the Air)"
            PERFORMER "Pink Floyd"
            INDEX 01 01:13:00
          TRACK 03 AUDIO
            TITLE "On the Run"
            PERFORMER "Pink Floyd"
            INDEX 01 03:56:00
          TRACK 04 AUDIO
            TITLE "Time"
            PERFORMER "Pink Floyd"
            INDEX 01 07:31:00
        """.trimIndent()

    @Test
    fun coldStart_restoresStateAndRendersMiniPlayerInPausedStateAtSavedPosition() =
        runTest(testDispatcher) {
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(track1, track2),
                    currentTrackIndex = 1,
                    positionMs = 75000L,
                    playbackMode = PlaybackMode.SINGLE_LOOP,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            val state = session.sessionState.value
            assertEquals(testServer, state.activeServer)
            assertEquals(2, state.queue.size)
            assertEquals(1, state.queue.currentIndex)
            assertEquals(track2, state.currentTrack)
            assertTrue("Restored mini-player must be in paused state", state.isPaused)
            assertEquals(75000L, session.playbackProgress.value.currentPositionMs)
            assertEquals(track2.durationMs, state.durationMs)
            assertEquals(PlaybackMode.SINGLE_LOOP, state.playbackMode)
            assertEquals("/Music/Jazz/", state.currentDirectoryPath)

            // Engine should still be idle - no network streaming has started yet
            assertTrue(fakeEngine.playbackState.value is PlaybackState.Idle)
            assertEquals(-1, fakeEngine.lastStartIndex)
        }

    @Test
    fun flushSession_beforeRestorationCompletes_doesNotOverwriteSavedPositionOrState() =
        runTest(testDispatcher) {
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(track1, track2),
                    currentTrackIndex = 1,
                    positionMs = 75000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            // Create session without advanceUntilIdle, immediately call flushSession
            session = createSession()
            session.flushSession() // Simulates onPause firing on app launch before restore completes

            // Saved session in store must NOT be overwritten with 0 or empty!
            val inStore = fakeStore.savedSession
            assertNotNull(inStore)
            assertEquals(1, inStore?.currentTrackIndex)
            assertEquals(75000L, inStore?.positionMs)
        }

    @Test
    fun tappingPlayOnRestoredMiniPlayer_seamlesslyStreamsFromSavedOffset() =
        runTest(testDispatcher) {
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(track1, track2),
                    currentTrackIndex = 1,
                    positionMs = 75000L,
                    playbackMode = PlaybackMode.SINGLE_LOOP,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            assertTrue(session.sessionState.value.isPaused)

            // User taps play on the restored mini-player
            session.togglePlayPause()
            runCurrent()

            // Engine starts streaming at the exact saved millisecond offset
            assertEquals(testServer, fakeEngine.lastServer)
            assertEquals(2, fakeEngine.lastTracks.size)
            assertEquals(1, fakeEngine.lastStartIndex)
            assertEquals(75000L, fakeEngine.lastStartPositionMs)
            assertTrue(session.sessionState.value.isPlaying)

            session.pause()
            advanceUntilIdle()
        }

    @Test
    fun seekingWhileInRestoredPausedState_updatesPositionAndStreamsFromNewOffsetOnPlay() =
        runTest(testDispatcher) {
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(track1, track2),
                    currentTrackIndex = 0,
                    positionMs = 20000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            // User scrubs progress bar to 90000ms
            session.seekTo(90000L)
            advanceUntilIdle()

            assertEquals(90000L, session.playbackProgress.value.currentPositionMs)
            assertTrue(fakeEngine.playbackState.value is PlaybackState.Idle)

            // User taps play
            session.play()
            runCurrent()

            assertEquals(0, fakeEngine.lastStartIndex)
            assertEquals(90000L, fakeEngine.lastStartPositionMs)
            assertTrue(session.sessionState.value.isPlaying)

            session.pause()
            advanceUntilIdle()
        }

    @Test
    fun flushSession_persistsCurrentDirectoryPathAndAllFields() =
        runTest(testDispatcher) {
            session = createSession()
            session.setActiveServer(testServer)
            session.setCurrentDirectoryPath("/Music/Jazz/")
            advanceUntilIdle()

            val directory =
                RemoteDirectory(
                    path = "/Music/Jazz/",
                    name = "Jazz",
                    files =
                        listOf(
                            RemoteFile(name = "track1.mp3", path = "/Music/Jazz/track1.mp3", size = 8000000L),
                            RemoteFile(name = "track2.flac", path = "/Music/Jazz/track2.flac", size = 25000000L),
                        ),
                )
            session.playDirectoryTrack(directory, directory.files[1])
            fakeEngine.seekTo(42000L)
            session.setPlaybackMode(PlaybackMode.SHUFFLE)
            runCurrent()

            session.flushSession()
            runCurrent()

            val saved = fakeStore.savedSession
            assertNotNull(saved)
            assertEquals(1L, saved?.activeServerId)
            assertEquals("/Music/Jazz/", saved?.currentDirectoryPath)
            assertEquals(2, saved?.queueTracks?.size)
            assertEquals(1, saved?.currentTrackIndex)
            assertEquals(42000L, saved?.positionMs)
            assertEquals(PlaybackMode.SHUFFLE, saved?.playbackMode)
        }

    @Test
    fun periodicFlushing_periodicallySavesStateToStore() =
        runTest(testDispatcher) {
            session =
                MusicPlayerAppSessionImpl(
                    playerEngine = fakeEngine,
                    serverRepository = fakeServerRepo,
                    sessionStore = fakeStore,
                    coroutineScope = sessionScope,
                    periodicDispatcher = testDispatcher,
                )
            session.setActiveServer(testServer)
            session.setCurrentDirectoryPath("/Music/Jazz/")
            advanceUntilIdle()

            val directory =
                RemoteDirectory(
                    path = "/Music/Jazz/",
                    name = "Jazz",
                    files =
                        listOf(
                            RemoteFile(name = "track1.mp3", path = "/Music/Jazz/track1.mp3", size = 8000000L),
                        ),
                )
            session.playDirectoryTrack(directory, directory.files[0])
            runCurrent()

            fakeEngine.seekTo(15000L)
            val initialCount = fakeStore.saveSessionCount

            // Advance 5 seconds (periodic ticker)
            advanceTimeBy(5100L)
            runCurrent()

            assertTrue(fakeStore.saveSessionCount > initialCount)
            assertEquals(15000L, fakeStore.savedSession?.positionMs)

            session.pause()
            runCurrent()
        }

    @Test
    fun skipToNext_automaticallyPersistsUpdatedTrackIndexToSessionStore() =
        runTest(testDispatcher) {
            session = createSession()
            session.setActiveServer(testServer)
            advanceUntilIdle()

            val directory =
                RemoteDirectory(
                    path = "/Music/Jazz/",
                    name = "Jazz",
                    files =
                        listOf(
                            RemoteFile(name = "track1.mp3", path = "/Music/Jazz/track1.mp3", size = 8000000L),
                            RemoteFile(name = "track2.flac", path = "/Music/Jazz/track2.flac", size = 25000000L),
                        ),
                )
            // 1. User starts playing track 1
            session.playDirectoryTrack(directory, directory.files[0])
            runCurrent()
            assertEquals(0, session.sessionState.value.queue.currentIndex)
            assertEquals(0, fakeStore.savedSession?.currentTrackIndex)

            // 2. User taps "next track"
            session.skipToNext()
            runCurrent()

            // 3. The state in memory is track 2
            assertEquals(1, session.sessionState.value.queue.currentIndex)

            // 4. Session store must automatically record track 2 without waiting for periodic timer or manual flush
            assertEquals(1, fakeStore.savedSession?.currentTrackIndex)
        }

    @Test
    fun skipToPrevious_automaticallyPersistsUpdatedTrackIndexToSessionStore() =
        runTest(testDispatcher) {
            session = createSession()
            session.setActiveServer(testServer)
            advanceUntilIdle()

            val directory =
                RemoteDirectory(
                    path = "/Music/Jazz/",
                    name = "Jazz",
                    files =
                        listOf(
                            RemoteFile(name = "track1.mp3", path = "/Music/Jazz/track1.mp3", size = 8000000L),
                            RemoteFile(name = "track2.flac", path = "/Music/Jazz/track2.flac", size = 25000000L),
                        ),
                )
            // Start playing track 2 (index 1)
            session.playDirectoryTrack(directory, directory.files[1])
            runCurrent()
            assertEquals(1, fakeStore.savedSession?.currentTrackIndex)

            // User taps "previous track"
            session.skipToPrevious()
            runCurrent()

            assertEquals(0, session.sessionState.value.queue.currentIndex)
            assertEquals(0, fakeStore.savedSession?.currentTrackIndex)
        }

    @Test
    fun playQueueIndex_automaticallyPersistsUpdatedTrackIndexToSessionStore() =
        runTest(testDispatcher) {
            session = createSession()
            session.setActiveServer(testServer)
            advanceUntilIdle()

            val directory =
                RemoteDirectory(
                    path = "/Music/Jazz/",
                    name = "Jazz",
                    files =
                        listOf(
                            RemoteFile(name = "track1.mp3", path = "/Music/Jazz/track1.mp3", size = 8000000L),
                            RemoteFile(name = "track2.flac", path = "/Music/Jazz/track2.flac", size = 25000000L),
                            RemoteFile(name = "track3.mp3", path = "/Music/Jazz/track3.mp3", size = 12000000L),
                        ),
                )
            session.playDirectoryTrack(directory, directory.files[0])
            runCurrent()
            assertEquals(0, fakeStore.savedSession?.currentTrackIndex)

            // Jump directly to track 3 (index 2) via playlist
            session.playQueueIndex(2)
            runCurrent()

            assertEquals(2, session.sessionState.value.queue.currentIndex)
            assertEquals(2, fakeStore.savedSession?.currentTrackIndex)
        }

    @Test
    fun seekTo_immediatelyPersistsUpdatedPositionToStore() =
        runTest(testDispatcher) {
            session = createSession()
            session.setActiveServer(testServer)
            advanceUntilIdle()

            val directory =
                RemoteDirectory(
                    path = "/Music/Jazz/",
                    name = "Jazz",
                    files =
                        listOf(
                            RemoteFile(name = "track1.mp3", path = "/Music/Jazz/track1.mp3", size = 8000000L),
                            RemoteFile(name = "track2.flac", path = "/Music/Jazz/track2.flac", size = 25000000L),
                        ),
                )
            session.playDirectoryTrack(directory, directory.files[0])
            runCurrent()
            assertEquals(0L, fakeStore.savedSession?.positionMs)

            // User scrubs slider to 871673L
            session.seekTo(871673L)
            runCurrent()

            assertEquals(871673L, session.playbackProgress.value.currentPositionMs)
            assertEquals(871673L, fakeStore.savedSession?.positionMs)
        }

    @Test
    fun coldStart_whenSavedServerDoesNotExist_handlesGracefullyWithoutCrashing() =
        runTest(testDispatcher) {
            val nonExistentSession =
                PlaybackSessionData(
                    activeServerId = 9999L,
                    currentDirectoryPath = "/Unknown/",
                    queueTracks = listOf(track1),
                    currentTrackIndex = 0,
                    positionMs = 10000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = nonExistentSession

            // Repo without any matching server
            val emptyServerRepo = TestServerRepository(emptyList())
            session =
                MusicPlayerAppSessionImpl(
                    playerEngine = fakeEngine,
                    serverRepository = emptyServerRepo,
                    sessionStore = fakeStore,
                    coroutineScope = sessionScope,
                )
            advanceUntilIdle()

            val state = session.sessionState.value
            assertNull(state.activeServer)
            assertTrue(state.queue.isEmpty)
            assertTrue(state.isIdle)
            assertNull(state.errorMessage)
        }

    @Test
    fun coldStart_whenStoreIsEmpty_leavesSessionClean() =
        runTest(testDispatcher) {
            fakeStore.savedSession = null

            session = createSession()
            advanceUntilIdle()

            val state = session.sessionState.value
            assertNotNull(state.activeServer) // From fakeServerRepo default server
            assertTrue(state.queue.isEmpty)
            assertTrue(state.isIdle)
            assertEquals(0L, session.playbackProgress.value.currentPositionMs)
        }

    @Test
    fun coldStart_whenRemoteFileIsNoLongerAccessible_handlesErrorGracefully() =
        runTest(testDispatcher) {
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(track1),
                    currentTrackIndex = 0,
                    positionMs = 10000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            // User taps play
            session.play()
            runCurrent()

            // Remote server returns 404 / error
            fakeEngine._playbackState.value = PlaybackState.Error("HTTP 404: File not found")
            advanceUntilIdle()

            val state = session.sessionState.value
            assertTrue(state.playbackState is PlaybackState.Error)
            assertEquals("HTTP 404: File not found", state.errorMessage)
            // Ensure no crash occurred and state still holds the track metadata
            assertEquals(track1, state.currentTrack)
        }

    @Test
    fun coldStart_whenArtworkFileMissingFromDisk_safelyStripsDeadArtworkUriAndPresentsCleanFallback() =
        runTest(testDispatcher) {
            val fakeMetadataRepo = TestTrackMetadataRepository()

            val deadCoverPath = "/cache/covers/deleted_art.jpg"
            val trackWithDeadArt = track1.copy(coverThumbnailPath = deadCoverPath)

            // When repository is queried for cached metadata, it returns sanitized metadata (coverThumbnailPath = null)
            fakeMetadataRepo.cachedMetadataMap[track1.remotePath] =
                TrackMetadata(
                    serverId = 1L,
                    remotePath = track1.remotePath,
                    title = track1.title,
                    coverThumbnailPath = null,
                )

            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(trackWithDeadArt),
                    currentTrackIndex = 0,
                    positionMs = 15000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            session =
                createSession(
                    store = fakeStore,
                    metadataRepo = fakeMetadataRepo,
                )
            advanceUntilIdle()

            val state = session.sessionState.value
            // Physical file does not exist on disk -> artwork URI must be stripped to null
            assertNull("Dead artwork path must be stripped to null during session restore", state.currentTrack?.coverThumbnailPath)

            // Starting playback must pass sanitized track with null artwork to player engine
            session.togglePlayPause()
            runCurrent()

            assertEquals(1, fakeEngine.lastTracks.size)
            assertNull(
                "PlayerEngine must receive null artwork URI avoiding FileNotFoundException crashes",
                fakeEngine.lastTracks[0].coverThumbnailPath,
            )
        }

    @Test
    fun coldStart_whenArtworkFileMissingFromDisk_triggersBackgroundSelfHealingAndEnrichesTrackNonDisruptively() =
        runTest(testDispatcher) {
            val fakeMetadataRepo = TestTrackMetadataRepository()

            val deadCoverPath = "/cache/covers/deleted_1.jpg"
            val healedCoverPath = "/cache/covers/healed_1.jpg"
            val trackWithDeadArt = track1.copy(coverThumbnailPath = deadCoverPath)

            fakeMetadataRepo.cachedMetadataMap[track1.remotePath] =
                TrackMetadata(
                    serverId = 1L,
                    remotePath = track1.remotePath,
                    title = "Autumn Leaves",
                    artist = "Bill Evans",
                    coverThumbnailPath = null,
                )

            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(trackWithDeadArt),
                    currentTrackIndex = 0,
                    positionMs = 20000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            session =
                createSession(
                    store = fakeStore,
                    metadataRepo = fakeMetadataRepo,
                )
            advanceUntilIdle()

            // Session is restored in paused state, dead artwork stripped
            val restoredState = session.sessionState.value
            assertTrue("Restored session must be in paused state", restoredState.isPaused)
            assertNull("Dead artwork path must be stripped to null", restoredState.currentTrack?.coverThumbnailPath)

            // Start playback on restored session while background self-healing enriches metadata
            session.togglePlayPause()
            runCurrent()
            assertTrue("Playback must start immediately", session.sessionState.value.isPlaying)

            // Flow emits healed metadata
            fakeMetadataRepo.emit(
                listOf(
                    TrackMetadata(
                        serverId = 1L,
                        remotePath = track1.remotePath,
                        title = "Autumn Leaves",
                        artist = "Bill Evans",
                        coverThumbnailPath = healedCoverPath,
                    ),
                ),
            )
            advanceUntilIdle()

            // Queue and active track must be enriched non-disruptively
            val enrichedTrack = session.sessionState.value.currentTrack
            assertNotNull(enrichedTrack)
            assertEquals(healedCoverPath, enrichedTrack?.coverThumbnailPath)

            // Player engine received track update non-disruptively
            assertTrue(fakeEngine.updateTrackCalls >= 1)
            assertEquals(healedCoverPath, fakeEngine.lastTracks[0].coverThumbnailPath)
            assertTrue("Playback must not be interrupted during enrichment", session.sessionState.value.isPlaying)
        }

    @Test
    fun coldStart_whenMultipleTracksHaveMissingArtwork_enrichesAllQueueTracksViaFlow() =
        runTest(testDispatcher) {
            val fakeMetadataRepo = TestTrackMetadataRepository()

            val deadCover1 = "/cache/covers/dead1.jpg"
            val deadCover2 = "/cache/covers/dead2.jpg"
            val healedCover1 = "/cache/covers/healed1.jpg"
            val healedCover2 = "/cache/covers/healed2.jpg"

            val t1 = track1.copy(coverThumbnailPath = deadCover1)
            val t2 = track2.copy(coverThumbnailPath = deadCover2)

            fakeMetadataRepo.cachedMetadataMap[t1.remotePath] =
                TrackMetadata(
                    serverId = 1L,
                    remotePath = t1.remotePath,
                    title = t1.title,
                    coverThumbnailPath = null,
                )
            fakeMetadataRepo.cachedMetadataMap[t2.remotePath] =
                TrackMetadata(
                    serverId = 1L,
                    remotePath = t2.remotePath,
                    title = t2.title,
                    coverThumbnailPath = null,
                )

            // Track 2 is active track (index 1)
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(t1, t2),
                    currentTrackIndex = 1,
                    positionMs = 30000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            session =
                createSession(
                    store = fakeStore,
                    metadataRepo = fakeMetadataRepo,
                )

            // Complete restoration
            advanceUntilIdle()

            // Initial queue has dead artwork stripped
            val initialQueue = session.sessionState.value.queue
            assertNull(initialQueue.tracks[0].coverThumbnailPath)
            assertNull(initialQueue.tracks[1].coverThumbnailPath)

            // Emit healed metadata from repository flow
            fakeMetadataRepo.emit(
                listOf(
                    TrackMetadata(
                        serverId = 1L,
                        remotePath = t1.remotePath,
                        title = t1.title,
                        coverThumbnailPath = healedCover1,
                    ),
                    TrackMetadata(
                        serverId = 1L,
                        remotePath = t2.remotePath,
                        title = t2.title,
                        coverThumbnailPath = healedCover2,
                    ),
                ),
            )
            advanceUntilIdle()

            // Verify both tracks are enriched
            val healedQueue = session.sessionState.value.queue
            assertEquals(healedCover1, healedQueue.tracks[0].coverThumbnailPath)
            assertEquals(healedCover2, healedQueue.tracks[1].coverThumbnailPath)
        }

    @Test
    fun coldStart_whenArtworkFilePhysicallyExistsOnDisk_retainsArtworkUriWithoutTriggeringSelfHealing() =
        runTest(testDispatcher) {
            val fakeMetadataRepo = TestTrackMetadataRepository()

            val validPath = "/cache/covers/valid_art.jpg"
            val trackWithValidArt = track1.copy(coverThumbnailPath = validPath)

            fakeMetadataRepo.cachedMetadataMap[track1.remotePath] =
                TrackMetadata(
                    serverId = 1L,
                    remotePath = track1.remotePath,
                    title = track1.title,
                    coverThumbnailPath = validPath,
                )

            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/Jazz/",
                    queueTracks = listOf(trackWithValidArt),
                    currentTrackIndex = 0,
                    positionMs = 10000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                )
            fakeStore.savedSession = savedSession

            session =
                createSession(
                    store = fakeStore,
                    metadataRepo = fakeMetadataRepo,
                )
            advanceUntilIdle()

            val state = session.sessionState.value
            assertEquals("Existing artwork file on disk must be retained", validPath, state.currentTrack?.coverThumbnailPath)
        }

    private class TestTrackMetadataRepository : TrackMetadataRepository {
        val metadataFlow = MutableStateFlow<List<TrackMetadata>>(emptyList())
        val cachedMetadataMap = mutableMapOf<String, TrackMetadata>()
        val resolveSingleTrackCalls = mutableListOf<RemoteFile>()
        var singleTrackResolver: (suspend (WebDavServer, RemoteFile) -> TrackMetadata)? = null

        fun emit(list: List<TrackMetadata>) {
            metadataFlow.value = list
        }

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> = metadataFlow.asStateFlow()

        override fun getMetadataForPathsFlow(
            serverId: Long,
            remotePaths: List<String>,
        ): Flow<List<TrackMetadata>> = metadataFlow.map { list -> list.filter { it.remotePath in remotePaths } }

        override fun getMetadataFlow(
            serverId: Long,
            remotePath: String,
        ): Flow<TrackMetadata?> = metadataFlow.map { list -> list.firstOrNull { it.remotePath == remotePath } }

        override suspend fun getCachedMetadata(
            serverId: Long,
            remotePath: String,
        ): TrackMetadata? = cachedMetadataMap[remotePath] ?: metadataFlow.value.firstOrNull { it.remotePath == remotePath }

        override suspend fun resolveMetadata(
            server: WebDavServer,
            files: List<RemoteFile>,
            forceRefresh: Boolean,
        ) {}

        override suspend fun resolveSingleTrackMetadata(
            server: WebDavServer,
            file: RemoteFile,
        ): TrackMetadata {
            resolveSingleTrackCalls.add(file)
            return singleTrackResolver?.invoke(server, file)
                ?: TrackMetadata(
                    serverId = server.id,
                    remotePath = file.path,
                    title = file.name,
                )
        }
    }

    private class TestServerRepository(
        servers: List<WebDavServer>,
    ) : ServerRepository {
        private val serversFlow = MutableStateFlow(servers)

        override fun getAllServers(): Flow<List<WebDavServer>> = serversFlow

        override fun getActiveServer(): Flow<WebDavServer?> =
            serversFlow.map { list ->
                list.firstOrNull { it.isDefault }
            }

        override suspend fun getServerById(id: Long): WebDavServer? = serversFlow.value.firstOrNull { it.id == id }

        override suspend fun saveServer(server: WebDavServer): Long {
            serversFlow.value = serversFlow.value + server
            return server.id
        }

        override suspend fun deleteServer(id: Long) {
            serversFlow.value = serversFlow.value.filter { it.id != id }
        }

        override suspend fun setActiveServer(id: Long) {
            serversFlow.value =
                serversFlow.value.map {
                    it.copy(isDefault = it.id == id)
                }
        }
    }

    private class TestWebDavClient(
        val textResponses: MutableMap<String, String?> = mutableMapOf(),
    ) : WebDavClient {
        override suspend fun testConnection(server: WebDavServer): com.webdav.player.data.remote.ConnectionResult =
            com.webdav.player.data.remote.ConnectionResult.Success

        override suspend fun listDirectory(server: WebDavServer, path: String) =
            com.webdav.player.domain.model.ListDirectoryResult.Success(
                RemoteDirectory(path = path, name = path.substringAfterLast('/'), files = emptyList()),
            )

        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long,
        ): ByteArray? = null

        override suspend fun fetchText(server: WebDavServer, remotePath: String): String? =
            textResponses[remotePath]
    }

    @Test
    fun coldStart_withVirtualTrackSession_restoresVirtualQueueAndRelativeProgressInPausedState() =
        runTest(testDispatcher) {
            CueTextCache.put(1L, "/Music/PinkFloyd/dark_side.cue", cueContentSample)
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/PinkFloyd/",
                    queueTracks = listOf(darkSideTrack),
                    currentTrackIndex = 0,
                    positionMs = 98000L, // Track 2 starts at 73000ms + 25000ms relative = 98000ms
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cuePath = "/Music/PinkFloyd/dark_side.cue",
                    virtualTrackNumber = 2,
                    virtualPositionMs = 25000L,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            val state = session.sessionState.value
            assertEquals(testServer, state.activeServer)
            assertEquals(4, state.queue.size)
            assertEquals(1, state.queue.currentIndex)
            assertEquals("Breathe (In the Air)", state.currentTrack?.title)
            assertEquals("Pink Floyd", state.currentTrack?.artist)
            assertEquals(163000L, state.currentTrack?.durationMs)
            assertTrue("Restored virtual track mini-player must be in paused state", state.isPaused)
            assertEquals(163000L, state.durationMs)
            assertEquals("/Music/PinkFloyd/", state.currentDirectoryPath)

            // Progress should be virtual relative progress (25000ms), NOT global file position (98000ms)
            assertEquals(25000L, session.playbackProgress.value.currentPositionMs)
            assertEquals(163000L, session.playbackProgress.value.durationMs)

            // Physical engine should still be idle before user taps play
            assertTrue(fakeEngine.playbackState.value is PlaybackState.Idle)
            assertEquals(-1, fakeEngine.lastStartIndex)
            // MediaSession was primed with virtual track metadata non-disruptively
            assertTrue(fakeEngine.updateTrackCalls >= 1)
        }

    @Test
    fun tappingPlayOnRestoredVirtualTrackMiniPlayer_streamsUnderlyingPhysicalAudioFromCalculatedAbsoluteOffset() =
        runTest(testDispatcher) {
            CueTextCache.put(1L, "/Music/PinkFloyd/dark_side.cue", cueContentSample)
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/PinkFloyd/",
                    queueTracks = listOf(darkSideTrack),
                    currentTrackIndex = 0,
                    positionMs = 98000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cuePath = "/Music/PinkFloyd/dark_side.cue",
                    virtualTrackNumber = 2,
                    virtualPositionMs = 25000L,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            assertTrue(session.sessionState.value.isPaused)

            // User taps play on the restored mini-player
            session.togglePlayPause()
            runCurrent()

            // Underlying physical audio stream starts at absolute ms (73000 + 25000 = 98000ms)
            assertEquals(testServer, fakeEngine.lastServer)
            assertEquals(1, fakeEngine.lastTracks.size)
            assertEquals("/Music/PinkFloyd/dark_side.flac", fakeEngine.lastTracks[0].remotePath)
            assertEquals(0, fakeEngine.lastStartIndex)
            assertEquals(98000L, fakeEngine.lastStartPositionMs)
            assertTrue(session.sessionState.value.isPlaying)
            assertEquals("Breathe (In the Air)", session.sessionState.value.currentTrack?.title)

            session.pause()
            advanceUntilIdle()
        }

    @Test
    fun coldStart_withVirtualTrackSession_whenCueFileDeletedOrMissing_fallsBackSafelyToOrdinaryBigAudioPlayback() =
        runTest(testDispatcher) {
            val fakeClient = TestWebDavClient() // textResponses empty -> returns null for any fetchText
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/PinkFloyd/",
                    queueTracks = listOf(darkSideTrack),
                    currentTrackIndex = 0,
                    positionMs = 98000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cuePath = "/Music/PinkFloyd/deleted_dark_side.cue",
                    virtualTrackNumber = 2,
                    virtualPositionMs = 25000L,
                )
            fakeStore.savedSession = savedSession

            session = createSession(client = fakeClient)
            advanceUntilIdle()

            val state = session.sessionState.value
            // Graceful fallback to ordinary big audio playback without crashing
            assertEquals(1, state.queue.size)
            assertEquals(0, state.queue.currentIndex)
            assertEquals(darkSideTrack.remotePath, state.currentTrack?.remotePath)
            assertEquals(darkSideTrack.durationMs, state.durationMs)
            assertTrue(state.isPaused)
            assertNull(state.errorMessage)

            // Absolute position preserved for ordinary playback
            assertEquals(98000L, session.playbackProgress.value.currentPositionMs)
            assertEquals(darkSideTrack.durationMs, session.playbackProgress.value.durationMs)

            // Tapping play plays parentTrack at absolute position
            session.togglePlayPause()
            runCurrent()

            assertEquals(1, fakeEngine.lastTracks.size)
            assertEquals("/Music/PinkFloyd/dark_side.flac", fakeEngine.lastTracks[0].remotePath)
            assertEquals(98000L, fakeEngine.lastStartPositionMs)
            assertTrue(session.sessionState.value.isPlaying)
        }

    @Test
    fun coldStart_withVirtualTrackSession_whenCueMalformed_fallsBackSafelyToOrdinaryBigAudioPlayback() =
        runTest(testDispatcher) {
            val fakeClient = TestWebDavClient(
                mutableMapOf("/Music/PinkFloyd/corrupted.cue" to "THIS IS JUST BINARY GARBAGE WITH NO TRACKS")
            )
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/PinkFloyd/",
                    queueTracks = listOf(darkSideTrack),
                    currentTrackIndex = 0,
                    positionMs = 98000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cuePath = "/Music/PinkFloyd/corrupted.cue",
                    virtualTrackNumber = 2,
                    virtualPositionMs = 25000L,
                )
            fakeStore.savedSession = savedSession

            session = createSession(client = fakeClient)
            advanceUntilIdle()

            val state = session.sessionState.value
            assertEquals(1, state.queue.size)
            assertEquals(0, state.queue.currentIndex)
            assertEquals(darkSideTrack.remotePath, state.currentTrack?.remotePath)
            assertEquals(darkSideTrack.durationMs, state.durationMs)
            assertTrue(state.isPaused)
            assertNull(state.errorMessage)
        }

    @Test
    fun seekingWhileInRestoredVirtualTrackPausedState_updatesRelativePositionAndStreamsCorrectAbsoluteOffsetOnPlay() =
        runTest(testDispatcher) {
            CueTextCache.put(1L, "/Music/PinkFloyd/dark_side.cue", cueContentSample)
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/PinkFloyd/",
                    queueTracks = listOf(darkSideTrack),
                    currentTrackIndex = 0,
                    positionMs = 98000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cuePath = "/Music/PinkFloyd/dark_side.cue",
                    virtualTrackNumber = 2,
                    virtualPositionMs = 25000L,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            // User scrubs relative slider to 50000ms (50s into Track 2)
            session.seekTo(50000L)
            advanceUntilIdle()

            assertEquals(50000L, session.playbackProgress.value.currentPositionMs)
            assertTrue(fakeEngine.playbackState.value is PlaybackState.Idle)

            // User taps play
            session.play()
            runCurrent()

            // Stream starts at 73000 + 50000 = 123000ms
            assertEquals(123000L, fakeEngine.lastStartPositionMs)
            assertTrue(session.sessionState.value.isPlaying)
        }

    @Test
    fun flushSession_whilePlayingVirtualTrack_persistsCuePathVirtualTrackNumberAndRelativeOffset() =
        runTest(testDispatcher) {
            session = createSession()
            session.setActiveServer(testServer)
            advanceUntilIdle()

            val parsedTracks = CueParser.parse(
                content = cueContentSample,
                parentAudioPath = darkSideTrack.remotePath,
                totalDurationMs = darkSideTrack.durationMs,
            )

            // Start playing Track 2 (index 1: starts at 73000L)
            session.playVirtualTracks(
                parentTrack = darkSideTrack,
                virtualTracks = parsedTracks,
                startIndex = 1,
                cuePath = "/Music/PinkFloyd/dark_side.cue",
            )
            runCurrent()

            // Engine reports physical playback position: 95000ms (22000ms into Track 2)
            fakeEngine._currentPositionMs.value = 95000L
            session.setPlaybackMode(PlaybackMode.SHUFFLE)
            runCurrent()

            session.flushSession()
            runCurrent()

            val saved = fakeStore.savedSession
            assertNotNull(saved)
            assertEquals(1L, saved?.activeServerId)
            assertEquals("/Music/PinkFloyd/dark_side.cue", saved?.cuePath)
            assertEquals(2, saved?.virtualTrackNumber)
            assertEquals(22000L, saved?.virtualPositionMs)
            assertEquals(95000L, saved?.positionMs)
            assertEquals(1, saved?.queueTracks?.size)
            assertEquals("/Music/PinkFloyd/dark_side.flac", saved?.queueTracks?.get(0)?.remotePath)
            assertEquals(PlaybackMode.SHUFFLE, saved?.playbackMode)
        }

    @Test
    fun skipControlsOnRestoredVirtualTrackSession_translatesRelativeToGlobalStream() =
        runTest(testDispatcher) {
            CueTextCache.put(1L, "/Music/PinkFloyd/dark_side.cue", cueContentSample)
            val savedSession =
                PlaybackSessionData(
                    activeServerId = 1L,
                    currentDirectoryPath = "/Music/PinkFloyd/",
                    queueTracks = listOf(darkSideTrack),
                    currentTrackIndex = 0,
                    positionMs = 98000L,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    cuePath = "/Music/PinkFloyd/dark_side.cue",
                    virtualTrackNumber = 2,
                    virtualPositionMs = 25000L,
                )
            fakeStore.savedSession = savedSession

            session = createSession()
            advanceUntilIdle()

            // Skip to next virtual track (Track 3: On the Run, starts at 236000ms)
            session.skipToNext()
            runCurrent()

            assertEquals(2, session.sessionState.value.queue.currentIndex)
            assertEquals("On the Run", session.sessionState.value.currentTrack?.title)
            assertEquals(0L, session.playbackProgress.value.currentPositionMs)

            // User taps play
            session.togglePlayPause()
            runCurrent()

            assertEquals(236000L, fakeEngine.lastStartPositionMs)
            assertTrue(session.sessionState.value.isPlaying)
        }
}
