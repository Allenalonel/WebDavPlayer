package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlayerAppSessionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var sessionJob: kotlinx.coroutines.CompletableJob
    private lateinit var sessionScope: kotlinx.coroutines.CoroutineScope
    private lateinit var session: MusicPlayerAppSessionImpl

    private val testServer = WebDavServer(
        id = 1L,
        name = "Home NAS",
        url = "http://192.168.1.100:8080/webdav",
        port = 8080,
        pathPrefix = "/webdav",
        username = "admin",
        password = "password123"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeEngine = FakeAudioPlayerEngine()
        sessionJob = SupervisorJob()
        sessionScope = kotlinx.coroutines.CoroutineScope(testDispatcher + sessionJob)
        session = MusicPlayerAppSessionImpl(
            playerEngine = fakeEngine,
            serverRepository = null,
            coroutineScope = sessionScope
        )
    }

    @After
    fun tearDown() {
        session.release()
        sessionJob.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isEmptyAndIdle() = runTest(testDispatcher) {
        val state = session.sessionState.value
        assertNull(state.activeServer)
        assertTrue(state.queue.isEmpty)
        assertTrue(state.isIdle)
        assertFalse(state.isPlaying)
        assertNull(state.currentTrack)
        assertEquals(PlaybackMode.LIST_LOOP, state.playbackMode)
    }

    @Test
    fun setActiveServer_updatesSessionState() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        advanceUntilIdle()

        assertEquals(testServer, session.sessionState.value.activeServer)
    }

    @Test
    fun playDirectoryTrack_populatesQueueWithOnlyAudioFilesAndStartsPlayback() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        advanceUntilIdle()

        val track1 = RemoteFile(name = "01 - Intro.mp3", path = "/Music/Album/01 - Intro.mp3", size = 1000)
        val lrc = RemoteFile(name = "01 - Intro.lrc", path = "/Music/Album/01 - Intro.lrc", size = 200)
        val track2 = RemoteFile(name = "02 - Main Song.flac", path = "/Music/Album/02 - Main Song.flac", size = 5000)
        val cover = RemoteFile(name = "cover.jpg", path = "/Music/Album/cover.jpg", size = 400)
        val track3 = RemoteFile(name = "03 - Outro.wav", path = "/Music/Album/03 - Outro.wav", size = 8000)

        val directory = RemoteDirectory(
            path = "/Music/Album/",
            name = "Album",
            files = listOf(track1, lrc, track2, cover, track3)
        )

        // User clicks track 2
        session.playDirectoryTrack(directory, track2)
        advanceUntilIdle()

        val state = session.sessionState.value
        assertEquals(3, state.queue.size)
        assertEquals(1, state.queue.currentIndex)
        assertEquals("02 - Main Song.flac", state.currentTrack?.title)
        assertEquals(AudioFormat.FLAC, state.currentTrack?.format)

        // Verify fake player received playTracks
        assertEquals(testServer, fakeEngine.lastServer)
        assertEquals(3, fakeEngine.lastTracks.size)
        assertEquals(1, fakeEngine.lastStartIndex)
        assertTrue(state.isPlaying)
    }

    @Test
    fun playDirectoryTrack_withWmaFile_populatesQueueWithWmaAudioTrackAndMimeType() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        advanceUntilIdle()

        val wmaTrack = RemoteFile(name = "01 - Classic.wma", path = "/Music/01 - Classic.wma", size = 1500)
        val mp3Track = RemoteFile(name = "02 - Pop.mp3", path = "/Music/02 - Pop.mp3", size = 2500)
        val directory = RemoteDirectory(
            path = "/Music/",
            name = "Music",
            files = listOf(wmaTrack, mp3Track)
        )

        session.playDirectoryTrack(directory, wmaTrack)
        advanceUntilIdle()

        val state = session.sessionState.value
        assertEquals(2, state.queue.size)
        assertEquals(0, state.queue.currentIndex)
        assertEquals(AudioFormat.WMA, state.currentTrack?.format)
        assertEquals("audio/x-ms-wma", state.currentTrack?.format?.mimeType)

        assertEquals(testServer, fakeEngine.lastServer)
        assertEquals(2, fakeEngine.lastTracks.size)
        assertEquals(AudioFormat.WMA, fakeEngine.lastTracks[0].format)
    }

    @Test
    fun togglePlayPause_pausesWhenPlaying_andResumesWhenPaused() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        advanceUntilIdle()

        val track = RemoteFile(name = "test.mp3", path = "/test.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track))
        session.playDirectoryTrack(directory, track)
        advanceUntilIdle()

        assertTrue(session.sessionState.value.isPlaying)

        // Toggle to pause
        session.togglePlayPause()
        advanceUntilIdle()
        assertEquals(1, fakeEngine.pauseCount)
        assertTrue(session.sessionState.value.isPaused)

        // Toggle to resume
        session.togglePlayPause()
        advanceUntilIdle()
        assertEquals(1, fakeEngine.playCount)
        assertTrue(session.sessionState.value.isPlaying)
    }

    @Test
    fun engineTrackIndexChange_updatesQueueCurrentIndex() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val track2 = RemoteFile(name = "02.mp3", path = "/02.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1, track2))
        session.playDirectoryTrack(directory, track1)
        advanceUntilIdle()

        assertEquals(0, session.sessionState.value.queue.currentIndex)

        // Engine transitions to next track
        fakeEngine._currentTrackIndex.value = 1
        advanceUntilIdle()

        assertEquals(1, session.sessionState.value.queue.currentIndex)
        assertEquals("02.mp3", session.sessionState.value.currentTrack?.title)
    }

    @Test
    fun engineError_updatesErrorMessageAndState() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track))
        session.playDirectoryTrack(directory, track)
        advanceUntilIdle()

        fakeEngine._playbackState.value = PlaybackState.Error("Network timeout")
        advanceUntilIdle()

        val state = session.sessionState.value
        assertTrue(state.playbackState is PlaybackState.Error)
        assertEquals("Network timeout", state.errorMessage)
    }

    @Test
    fun switchingActiveServer_resetsPlaybackQueueAndStopsPlayback() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track))
        session.playDirectoryTrack(directory, track)
        advanceUntilIdle()

        assertTrue(session.sessionState.value.hasTrack)

        val otherServer = WebDavServer(id = 2L, name = "Other", url = "http://example.com")
        session.setActiveServer(otherServer)
        advanceUntilIdle()

        val state = session.sessionState.value
        assertEquals(otherServer, state.activeServer)
        assertTrue(state.queue.isEmpty)
        assertTrue(state.isIdle)
        assertEquals(1, fakeEngine.stopCount)
    }

    @Test
    fun playbackControls_dispatchToPlayerEngine() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val track2 = RemoteFile(name = "02.mp3", path = "/02.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1, track2))
        session.playDirectoryTrack(directory, track1)
        advanceUntilIdle()

        session.pause()
        advanceUntilIdle()
        assertEquals(1, fakeEngine.pauseCount)

        session.play()
        advanceUntilIdle()
        assertEquals(1, fakeEngine.playCount)

        session.seekTo(5000L)
        advanceUntilIdle()
        assertEquals(5000L, fakeEngine.seekToPosition)
        assertEquals(5000L, session.sessionState.value.currentPositionMs)

        session.skipToNext()
        advanceUntilIdle()
        assertEquals(1, fakeEngine._currentTrackIndex.value)

        session.skipToPrevious()
        advanceUntilIdle()
        assertEquals(0, fakeEngine._currentTrackIndex.value)

        session.playQueueIndex(1)
        advanceUntilIdle()
        assertEquals(1, fakeEngine._currentTrackIndex.value)

        session.stop()
        advanceUntilIdle()
        assertEquals(1, fakeEngine.stopCount)
        assertTrue(session.sessionState.value.isIdle)
    }

    @Test
    fun cyclePlaybackMode_cyclesThroughAllModesAndNotifiesEngine() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        advanceUntilIdle()

        assertEquals(PlaybackMode.LIST_LOOP, session.sessionState.value.playbackMode)
        assertEquals(PlaybackMode.LIST_LOOP, fakeEngine._playbackMode.value)

        // LIST_LOOP -> SINGLE_LOOP
        session.cyclePlaybackMode()
        advanceUntilIdle()
        assertEquals(PlaybackMode.SINGLE_LOOP, session.sessionState.value.playbackMode)
        assertEquals(PlaybackMode.SINGLE_LOOP, fakeEngine._playbackMode.value)

        // SINGLE_LOOP -> SHUFFLE
        session.cyclePlaybackMode()
        advanceUntilIdle()
        assertEquals(PlaybackMode.SHUFFLE, session.sessionState.value.playbackMode)
        assertEquals(PlaybackMode.SHUFFLE, fakeEngine._playbackMode.value)

        // SHUFFLE -> LIST_LOOP
        session.cyclePlaybackMode()
        advanceUntilIdle()
        assertEquals(PlaybackMode.LIST_LOOP, session.sessionState.value.playbackMode)
        assertEquals(PlaybackMode.LIST_LOOP, fakeEngine._playbackMode.value)
    }

    @Test
    fun removeQueueTrack_removesNonActiveTrackAndKeepsCurrentTrack() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val track2 = RemoteFile(name = "02.mp3", path = "/02.mp3")
        val track3 = RemoteFile(name = "03.mp3", path = "/03.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1, track2, track3))
        session.playDirectoryTrack(directory, track2) // Playing track 2 (index 1)
        advanceUntilIdle()

        assertEquals(1, session.sessionState.value.queue.currentIndex)
        assertEquals("02.mp3", session.sessionState.value.currentTrack?.title)

        // Remove track 0 (track 1)
        session.removeQueueTrack(0)
        advanceUntilIdle()

        val state = session.sessionState.value
        assertEquals(2, state.queue.size)
        assertEquals(0, state.queue.currentIndex)
        assertEquals("02.mp3", state.currentTrack?.title)
    }

    @Test
    fun removeQueueTrack_removesActiveTrack_andAdvancesToNext() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val track2 = RemoteFile(name = "02.mp3", path = "/02.mp3")
        val track3 = RemoteFile(name = "03.mp3", path = "/03.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1, track2, track3))
        session.playDirectoryTrack(directory, track2) // Playing track 2 (index 1)
        advanceUntilIdle()

        // Remove current track (index 1)
        session.removeQueueTrack(1)
        advanceUntilIdle()

        val state = session.sessionState.value
        assertEquals(2, state.queue.size)
        assertEquals(1, state.queue.currentIndex)
        assertEquals("03.mp3", state.currentTrack?.title)
    }

    @Test
    fun removeQueueTrack_removesOnlyTrack_stopsAndClearsQueue() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1))
        session.playDirectoryTrack(directory, track1)
        advanceUntilIdle()

        assertEquals(1, session.sessionState.value.queue.size)

        // Remove only track
        session.removeQueueTrack(0)
        advanceUntilIdle()

        val state = session.sessionState.value
        assertTrue(state.queue.isEmpty)
        assertTrue(state.isIdle)
        assertEquals(1, fakeEngine.stopCount)
    }

    @Test
    fun playbackModeTransitions_listLoopWraparound() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val track2 = RemoteFile(name = "02.mp3", path = "/02.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1, track2))
        session.playDirectoryTrack(directory, track2) // index 1 (last track)
        advanceUntilIdle()

        session.setPlaybackMode(PlaybackMode.LIST_LOOP)
        advanceUntilIdle()

        // Next at last track wraps to 0
        session.skipToNext()
        advanceUntilIdle()
        assertEquals(0, fakeEngine._currentTrackIndex.value)
        assertEquals(0, session.sessionState.value.queue.currentIndex)

        // Simulate track completion wraps to 1 then to 0
        fakeEngine.simulateTrackCompletion()
        advanceUntilIdle()
        assertEquals(1, session.sessionState.value.queue.currentIndex)

        fakeEngine.simulateTrackCompletion()
        advanceUntilIdle()
        assertEquals(0, session.sessionState.value.queue.currentIndex)
    }

    @Test
    fun playbackModeTransitions_singleLoopReplaysCurrentTrack() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val track2 = RemoteFile(name = "02.mp3", path = "/02.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1, track2))
        session.playDirectoryTrack(directory, track1) // index 0
        advanceUntilIdle()

        session.setPlaybackMode(PlaybackMode.SINGLE_LOOP)
        fakeEngine.seekTo(45000L)
        advanceUntilIdle()

        assertEquals(45000L, fakeEngine._currentPositionMs.value)
        assertEquals(0, fakeEngine._currentTrackIndex.value)

        // Track completes -> replays same track from 0L
        fakeEngine.simulateTrackCompletion()
        advanceUntilIdle()

        assertEquals(0, fakeEngine._currentTrackIndex.value)
        assertEquals(0L, fakeEngine._currentPositionMs.value)
        assertEquals(0, session.sessionState.value.queue.currentIndex)
    }

    @Test
    fun playbackModeTransitions_shuffleFollowsPermutation() = runTest(testDispatcher) {
        session.setActiveServer(testServer)
        val track1 = RemoteFile(name = "01.mp3", path = "/01.mp3")
        val track2 = RemoteFile(name = "02.mp3", path = "/02.mp3")
        val track3 = RemoteFile(name = "03.mp3", path = "/03.mp3")
        val directory = RemoteDirectory(path = "/", name = "root", files = listOf(track1, track2, track3))
        session.playDirectoryTrack(directory, track1) // index 0
        advanceUntilIdle()

        session.setPlaybackMode(PlaybackMode.SHUFFLE)
        fakeEngine.shufflePermutation = listOf(0, 2, 1)
        advanceUntilIdle()

        // Track completion moves 0 -> 2
        fakeEngine.simulateTrackCompletion()
        advanceUntilIdle()
        assertEquals(2, fakeEngine._currentTrackIndex.value)
        assertEquals(2, session.sessionState.value.queue.currentIndex)

        // Next completion moves 2 -> 1
        fakeEngine.simulateTrackCompletion()
        advanceUntilIdle()
        assertEquals(1, fakeEngine._currentTrackIndex.value)
        assertEquals(1, session.sessionState.value.queue.currentIndex)

        // Next completion wraps back to 0
        fakeEngine.simulateTrackCompletion()
        advanceUntilIdle()
        assertEquals(0, fakeEngine._currentTrackIndex.value)
        assertEquals(0, session.sessionState.value.queue.currentIndex)
    }

    @Test
    fun metadataResolution_enrichesPlaybackQueueTracksReactively() = runTest(testDispatcher) {
        val fakeMetadataRepo = FakeTrackMetadataRepo()
        val sessionWithRepo = MusicPlayerAppSessionImpl(
            playerEngine = fakeEngine,
            serverRepository = null,
            trackMetadataRepository = fakeMetadataRepo,
            coroutineScope = sessionScope
        )

        sessionWithRepo.setActiveServer(testServer)
        val file1 = RemoteFile(name = "song1.mp3", path = "/music/song1.mp3")
        val file2 = RemoteFile(name = "song2.flac", path = "/music/song2.flac")
        val dir = RemoteDirectory(path = "/music/", name = "music", files = listOf(file1, file2))

        sessionWithRepo.playDirectoryTrack(dir, file1)
        advanceUntilIdle()

        // Initially tracks have file names
        val initialTracks = sessionWithRepo.sessionState.value.queue.tracks
        assertEquals(2, initialTracks.size)
        assertEquals("song1.mp3", initialTracks[0].title)
        assertNull(initialTracks[0].artist)

        // Metadata arrives
        fakeMetadataRepo.emit(
            listOf(
                TrackMetadata(
                    serverId = testServer.id,
                    remotePath = "/music/song1.mp3",
                    title = "Rich Song One",
                    artist = "Awesome Artist",
                    album = "Great Album",
                    trackNumber = 1,
                    durationMs = 180000L,
                    coverThumbnailPath = "/cache/cover1.jpg"
                )
            )
        )
        advanceUntilIdle()

        val enrichedTracks = sessionWithRepo.sessionState.value.queue.tracks
        assertEquals("Rich Song One", enrichedTracks[0].title)
        assertEquals("Awesome Artist", enrichedTracks[0].artist)
        assertEquals("Great Album", enrichedTracks[0].album)
        assertEquals(180000L, enrichedTracks[0].durationMs)
        assertEquals("/cache/cover1.jpg", enrichedTracks[0].coverThumbnailPath)

        sessionWithRepo.release()
    }

    @Test
    fun metadataResolution_onlyUpdatesTracksWhoseMetadataActuallyChanged() = runTest(testDispatcher) {
        val fakeMetadataRepo = FakeTrackMetadataRepo()
        val sessionWithRepo = MusicPlayerAppSessionImpl(
            playerEngine = fakeEngine,
            serverRepository = null,
            trackMetadataRepository = fakeMetadataRepo,
            coroutineScope = sessionScope
        )

        sessionWithRepo.setActiveServer(testServer)
        val file1 = RemoteFile(name = "song1.mp3", path = "/music/song1.mp3")
        val file2 = RemoteFile(name = "song2.flac", path = "/music/song2.flac")
        val file3 = RemoteFile(name = "song3.wav", path = "/music/song3.wav")
        val dir = RemoteDirectory(path = "/music/", name = "music", files = listOf(file1, file2, file3))

        sessionWithRepo.playDirectoryTrack(dir, file1)
        advanceUntilIdle()

        fakeEngine.updateTrackCalls = 0
        fakeEngine.updatedTrackIndices.clear()

        // Metadata arrives for ONLY song1
        fakeMetadataRepo.emit(
            listOf(
                TrackMetadata(
                    serverId = testServer.id,
                    remotePath = "/music/song1.mp3",
                    title = "Rich Song One",
                    artist = "Awesome Artist",
                    album = "Great Album"
                )
            )
        )
        advanceUntilIdle()

        // Should ONLY update song1 (index 0)
        assertEquals(1, fakeEngine.updateTrackCalls)
        assertEquals(listOf(0), fakeEngine.updatedTrackIndices)

        // Now metadata for song2 arrives: Room emits [song1, song2]
        fakeMetadataRepo.emit(
            listOf(
                TrackMetadata(
                    serverId = testServer.id,
                    remotePath = "/music/song1.mp3",
                    title = "Rich Song One",
                    artist = "Awesome Artist",
                    album = "Great Album"
                ),
                TrackMetadata(
                    serverId = testServer.id,
                    remotePath = "/music/song2.flac",
                    title = "Rich Song Two",
                    artist = "Awesome Artist",
                    album = "Great Album"
                )
            )
        )
        advanceUntilIdle()

        // song1 did NOT change! Only song2 changed! Total calls should be 2 (song1 once, song2 once).
        assertEquals(2, fakeEngine.updateTrackCalls)
        assertEquals(listOf(0, 1), fakeEngine.updatedTrackIndices)

        sessionWithRepo.release()
    }

    @Test
    fun sessionState_lyricsLoadedAutomatically_whenTrackStartsPlaying() = runTest(testDispatcher) {
        val fakeLyricsRepo = FakeLyricsRepo()
        val expectedLyrics = com.webdav.player.domain.model.Lyrics(
            lines = listOf(
                com.webdav.player.domain.model.LyricLine(1000L, "Hello world")
            ),
            isSynchronized = true
        )
        fakeLyricsRepo.lyricsMap["/music/song1.mp3"] = expectedLyrics

        val sessionWithLyrics = MusicPlayerAppSessionImpl(
            playerEngine = fakeEngine,
            serverRepository = null,
            trackMetadataRepository = null,
            lyricsRepository = fakeLyricsRepo,
            coroutineScope = sessionScope
        )

        sessionWithLyrics.setActiveServer(testServer)
        val track = AudioTrack(
            id = "1:/music/song1.mp3",
            serverId = testServer.id,
            remotePath = "/music/song1.mp3",
            title = "song1.mp3",
            format = AudioFormat.MP3
        )

        sessionWithLyrics.playTrack(track)
        advanceUntilIdle()

        val state = sessionWithLyrics.sessionState.value
        assertNotNull(state.lyrics)
        assertEquals(1, state.lyrics?.lines?.size)
        assertEquals("Hello world", state.lyrics?.lines?.get(0)?.text)
        assertFalse(state.isLoadingLyrics)

        sessionWithLyrics.release()
    }

    private class FakeLyricsRepo : com.webdav.player.domain.repository.LyricsRepository {
        val lyricsMap = mutableMapOf<String, com.webdav.player.domain.model.Lyrics>()

        override suspend fun resolveLyrics(
            server: WebDavServer,
            track: AudioTrack
        ): com.webdav.player.domain.model.Lyrics {
            return lyricsMap[track.remotePath] ?: com.webdav.player.domain.model.Lyrics.EMPTY
        }
    }

    private class FakeTrackMetadataRepo : TrackMetadataRepository {
        val flow = MutableStateFlow<List<TrackMetadata>>(emptyList())

        fun emit(list: List<TrackMetadata>) {
            flow.value = list
        }

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> = flow.asStateFlow()
        override fun getMetadataForPathsFlow(serverId: Long, remotePaths: List<String>): Flow<List<TrackMetadata>> =
            flow.map { list -> list.filter { it.remotePath in remotePaths } }
        override fun getMetadataFlow(serverId: Long, remotePath: String): Flow<TrackMetadata?> =
            flow.map { list -> list.firstOrNull { it.remotePath == remotePath } }
        override suspend fun getCachedMetadata(serverId: Long, remotePath: String): TrackMetadata? =
            flow.value.firstOrNull { it.remotePath == remotePath }
        override suspend fun resolveMetadata(server: WebDavServer, files: List<RemoteFile>) {}
        override suspend fun resolveSingleTrackMetadata(server: WebDavServer, file: RemoteFile): TrackMetadata =
            TrackMetadata(serverId = server.id, remotePath = file.path, title = file.name)
    }
}
