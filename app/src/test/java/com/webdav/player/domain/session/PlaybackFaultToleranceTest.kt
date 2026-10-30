package com.webdav.player.domain.session

import com.webdav.player.data.player.UnsupportedRangeReconnectionException
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackFaultToleranceTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher + SupervisorJob())

    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var sessionStateFlow: MutableStateFlow<PlayerSessionState>
    private lateinit var queueManager: PlaybackQueueManager
    private lateinit var faultTolerance: PlaybackFaultTolerance
    private lateinit var fakePlaylistRepo: FakePlaylistRepo

    private val t1 =
        AudioTrack(
            id = "1:/song1.mp3",
            serverId = 1L,
            remotePath = "/song1.mp3",
            title = "Song 1",
            durationMs = 200_000L,
            format = AudioFormat.MP3,
        )

    private val t2 =
        AudioTrack(
            id = "1:/song2.mp3",
            serverId = 1L,
            remotePath = "/song2.mp3",
            title = "Song 2",
            durationMs = 200_000L,
            format = AudioFormat.MP3,
        )

    private class FakePlaylistRepo : PlaylistRepository {
        val markedInactive = mutableListOf<Triple<Long, String, InactiveReason>>()

        override fun getAllPlaylistsFlow(): Flow<List<Playlist>> = emptyFlow()

        override suspend fun getAllPlaylists(): List<Playlist> = emptyList()

        override fun getPlaylistFlow(playlistId: Long): Flow<Playlist?> = flowOf(null)

        override suspend fun getPlaylist(playlistId: Long): Playlist? = null

        override suspend fun createPlaylist(name: String): Long = 1L

        override suspend fun renamePlaylist(
            playlistId: Long,
            newName: String,
        ) {}

        override suspend fun deletePlaylist(playlistId: Long) {}

        override suspend fun isPlaylistNameExists(
            name: String,
            excludePlaylistId: Long?,
        ): Boolean = false

        override fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<PlaylistTrackEntry>> = emptyFlow()

        override suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntry> = emptyList()

        override suspend fun addTrackToPlaylist(
            playlistId: Long,
            track: AudioTrack,
            cuePath: String?,
        ): Long = 1L

        override suspend fun addTracksToPlaylist(
            playlistId: Long,
            tracks: List<AudioTrack>,
        ): List<Long> = emptyList()

        override suspend fun removeTrackFromPlaylist(trackEntryId: Long) {}

        override suspend fun removeTracksFromPlaylist(
            playlistId: Long,
            trackEntryIds: List<Long>,
        ) {}

        override suspend fun updateTrackOrders(
            playlistId: Long,
            orderedTrackIds: List<Long>,
        ) {}

        override fun markTrackInactive(
            serverId: Long,
            remotePath: String,
            reason: InactiveReason,
        ) {
            markedInactive.add(Triple(serverId, remotePath, reason))
        }
    }

    @Before
    fun setUp() {
        fakeEngine = FakeAudioPlayerEngine()
        fakePlaylistRepo = FakePlaylistRepo()
        sessionStateFlow =
            MutableStateFlow(
                PlayerSessionState(
                    queue = PlaybackQueue(listOf(t1, t2), currentIndex = 0),
                    playbackMode = PlaybackMode.LIST_LOOP,
                ),
            )
        queueManager = PlaybackQueueManager(sessionStateFlow)
        faultTolerance =
            PlaybackFaultTolerance(
                sessionStateFlow = sessionStateFlow,
                playerEngine = fakeEngine,
                playlistRepository = fakePlaylistRepo,
                queueManager = queueManager,
                coroutineScope = testScope,
            )
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun bufferingWatchdog_triggersWeakNetworkAt15sAndPausesAt30s() =
        testScope.runTest {
            faultTolerance.onBufferingStarted()

            // 14 seconds: nothing triggered yet
            advanceTimeBy(14_000L)
            assertFalse(sessionStateFlow.value.isWeakNetwork)
            assertFalse(sessionStateFlow.value.canManualRetry)

            // 15 seconds: weak network flag set, reconnecting
            advanceTimeBy(1_500L)
            assertTrue(sessionStateFlow.value.isWeakNetwork)
            assertTrue(sessionStateFlow.value.isReconnecting)
            assertFalse(sessionStateFlow.value.canManualRetry)

            // 30 seconds total: engine paused, manual retry enabled
            advanceTimeBy(15_000L)
            assertTrue(sessionStateFlow.value.isWeakNetwork)
            assertTrue(sessionStateFlow.value.canManualRetry)
            assertEquals(PlaybackState.Paused, sessionStateFlow.value.playbackState)
            assertEquals(1, fakeEngine.pauseCount)
        }

    @Test
    fun onPlayingStarted_resetsWatchdogAndWeakNetworkFlags() =
        testScope.runTest {
            faultTolerance.onBufferingStarted()
            advanceTimeBy(16_000L)
            assertTrue(sessionStateFlow.value.isWeakNetwork)

            faultTolerance.onPlayingStarted()
            assertFalse(sessionStateFlow.value.isWeakNetwork)
            assertFalse(sessionStateFlow.value.canManualRetry)
            assertFalse(sessionStateFlow.value.isReconnecting)
        }

    @Test
    fun errorClassification_accuratelyCategorizesExceptions() {
        assertTrue(FileNotFoundException("File missing 404").is404Exception())
        assertTrue(faultTolerance.is404Error(PlaybackState.Error("Resource 404 Not Found")))

        assertTrue(faultTolerance.isAuthException(RuntimeException("HTTP 401 Unauthorized")))
        assertTrue(faultTolerance.isAuthError(PlaybackState.Error("403 Forbidden")))

        assertTrue(faultTolerance.isServerUnreachableException(ConnectException("Connection refused")))
        assertTrue(faultTolerance.isServerUnreachableError(PlaybackState.Error("ConnectException: Failed to connect")))

        val rangeEx = UnsupportedRangeReconnectionException("Range not supported")
        assertTrue(faultTolerance.isUnsupportedRangeError(PlaybackState.Error("UnsupportedRangeReconnectionException", cause = rangeEx)))
    }

    @Test
    fun handle404Failure_marksInactiveAndSkipsWhenQueueAvailable() {
        var skipped = false
        faultTolerance.handle404Failure(t1, "Missing file") { skipped = true }

        assertTrue(skipped)
        assertEquals(1, fakePlaylistRepo.markedInactive.size)
        assertEquals(InactiveReason.FILE_NOT_FOUND, fakePlaylistRepo.markedInactive[0].third)
    }

    @Test
    fun handle404Failure_setsErrorStateWhenQueueExhausted() {
        // Mark both tracks failed
        queueManager.recordFailedTrack(t1.id, 2)
        var skipped = false
        faultTolerance.handle404Failure(t2, "File deleted") { skipped = true }

        assertFalse(skipped)
        assertTrue(sessionStateFlow.value.playbackState is PlaybackState.Error)
        assertEquals("File deleted", sessionStateFlow.value.errorMessage)
    }

    @Test
    fun unsupportedRangeDialogue_dismissAndConfirm() {
        sessionStateFlow.update { it.copy(isUnsupportedRange = true) }

        faultTolerance.dismissUnsupportedRange()
        assertFalse(sessionStateFlow.value.isUnsupportedRange)

        sessionStateFlow.update { it.copy(isUnsupportedRange = true, errorMessage = "Range error") }
        var replayCalled = false
        faultTolerance.confirmUnsupportedRangeReplay { replayCalled = true }

        assertFalse(sessionStateFlow.value.isUnsupportedRange)
        assertEquals(null, sessionStateFlow.value.errorMessage)
        assertTrue(replayCalled)
    }

    @Test
    fun prematureEofArbitration_differentiatesGenuineCompletionFromPrematureCutoff() {
        // Genuine completion by physical tolerance: within 5000ms
        assertFalse(faultTolerance.isPrematureEof(currentPos = 196_000L, duration = 200_000L))

        // Genuine completion by 95% progress threshold
        assertFalse(faultTolerance.isPrematureEof(currentPos = 191_000L, duration = 200_000L))

        // Premature EOF: cut off at 50%
        assertTrue(faultTolerance.isPrematureEof(currentPos = 100_000L, duration = 200_000L))
    }
}
