package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
}
