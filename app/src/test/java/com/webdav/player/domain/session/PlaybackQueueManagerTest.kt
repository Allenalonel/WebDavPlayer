package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackQueueManagerTest {
    private lateinit var sessionStateFlow: MutableStateFlow<PlayerSessionState>
    private lateinit var queueManager: PlaybackQueueManager
    private var flushRequested = false

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Server1",
            url = "http://example.com",
            username = "u",
            password = "p",
        )

    private fun createTrack(
        id: String,
        durationMs: Long = 180_000L,
    ): AudioTrack =
        AudioTrack(
            id = id,
            serverId = 1L,
            remotePath = "/$id.mp3",
            title = "Track $id",
            durationMs = durationMs,
            format = AudioFormat.MP3,
        )

    private val t1 = createTrack("1")
    private val t2 = createTrack("2")
    private val t3 = createTrack("3")

    @Before
    fun setUp() {
        flushRequested = false
        sessionStateFlow =
            MutableStateFlow(
                PlayerSessionState(
                    activeServer = testServer,
                    queue = PlaybackQueue(listOf(t1, t2, t3), currentIndex = 0),
                    playbackMode = PlaybackMode.LIST_LOOP,
                ),
            )
        queueManager =
            PlaybackQueueManager(
                sessionStateFlow = sessionStateFlow,
                onFlushRequested = { flushRequested = true },
            )
    }

    @Test
    fun calculateNextAndPrevious_listLoop_cyclesThroughAllIndices() {
        val q = PlaybackQueue(listOf(t1, t2, t3), currentIndex = 0)
        assertEquals(1, queueManager.calculateNextQueueIndex(q, PlaybackMode.LIST_LOOP))

        val qEnd = PlaybackQueue(listOf(t1, t2, t3), currentIndex = 2)
        assertEquals(0, queueManager.calculateNextQueueIndex(qEnd, PlaybackMode.LIST_LOOP))

        assertEquals(2, queueManager.calculatePreviousQueueIndex(q, PlaybackMode.LIST_LOOP))
        assertEquals(1, queueManager.calculatePreviousQueueIndex(qEnd, PlaybackMode.LIST_LOOP))
    }

    @Test
    fun calculateNextAndPrevious_singleLoop_staysOnCurrentTrackWhenNoFailures() {
        val q = PlaybackQueue(listOf(t1, t2, t3), currentIndex = 1)
        assertEquals(1, queueManager.calculateNextQueueIndex(q, PlaybackMode.SINGLE_LOOP))
        assertEquals(1, queueManager.calculatePreviousQueueIndex(q, PlaybackMode.SINGLE_LOOP))
    }

    @Test
    fun calculateNext_singleLoop_advancesWhenTracksHaveFailed() {
        val q = PlaybackQueue(listOf(t1, t2, t3), currentIndex = 1)
        queueManager.recordFailedTrack(t2.id, q.size)

        // SINGLE_LOOP should advance to avoid dead-looping on a broken track
        val nextIdx = queueManager.calculateNextQueueIndex(q, PlaybackMode.SINGLE_LOOP)
        assertEquals(2, nextIdx)
    }

    @Test
    fun recordFailedTrack_exhaustsCycleWhenAllTracksFail() {
        val q = PlaybackQueue(listOf(t1, t2), currentIndex = 0)
        val shouldSkip1 = queueManager.recordFailedTrack(t1.id, q.size)
        assertTrue(shouldSkip1)
        assertTrue(queueManager.hasFailedTracks())

        val shouldSkip2 = queueManager.recordFailedTrack(t2.id, q.size)
        assertFalse(shouldSkip2) // Entire queue failed, should abort skipping!
        assertFalse(queueManager.hasFailedTracks())
    }

    @Test
    fun calculateNext_shuffle_obeysShuffleOrder() {
        val q = PlaybackQueue(listOf(t1, t2, t3), currentIndex = 0, shuffleOrder = listOf(0, 2, 1))
        val next = queueManager.calculateNextQueueIndex(q, PlaybackMode.SHUFFLE)
        assertEquals(2, next)
    }

    @Test
    fun modeManagement_cycleAndSet_generatesShuffleOrderLazily() {
        queueManager.setPlaybackMode(PlaybackMode.SHUFFLE)
        assertTrue(flushRequested)
        assertEquals(PlaybackMode.SHUFFLE, sessionStateFlow.value.playbackMode)
        assertEquals(3, sessionStateFlow.value.queue.shuffleOrder.size)

        flushRequested = false
        queueManager.cyclePlaybackMode() // SHUFFLE -> LIST_LOOP
        assertEquals(PlaybackMode.LIST_LOOP, sessionStateFlow.value.playbackMode)
        assertTrue(flushRequested)
    }

    @Test
    fun playNext_insertsTrackImmediatelyAfterCurrent() {
        val insert = createTrack("inserted")
        var playedTrack: AudioTrack? = null

        queueManager.playNext(insert, onPlayTrack = { playedTrack = it })

        val q = sessionStateFlow.value.queue
        assertEquals(4, q.size)
        assertEquals(t1, q.tracks[0])
        assertEquals(insert, q.tracks[1])
        assertEquals(t2, q.tracks[2])
        assertEquals(0, q.currentIndex)
        assertTrue(flushRequested)
        assertEquals(null, playedTrack)
    }

    @Test
    fun playNext_whenQueueEmpty_delegatesToPlayTrack() {
        sessionStateFlow.value = sessionStateFlow.value.copy(queue = PlaybackQueue.EMPTY)
        val insert = createTrack("first")
        var playedTrack: AudioTrack? = null

        queueManager.playNext(insert, onPlayTrack = { playedTrack = it })

        assertEquals(insert, playedTrack)
    }

    @Test
    fun playNextAndAdvance_insertsAndTriggersTransition() {
        val insert = createTrack("advance")
        var transitionedIndex: Int? = null

        queueManager.playNextAndAdvance(
            track = insert,
            hasServerOrRepo = true,
            onPlayTrack = {},
            onTransitionToIndex = { transitionedIndex = it },
        )

        val q = sessionStateFlow.value.queue
        assertEquals(4, q.size)
        assertEquals(insert, q.tracks[1])
        assertEquals(1, transitionedIndex)
    }

    @Test
    fun removeQueueTrack_removesNonCurrentTrackCleanly() {
        queueManager.removeQueueTrack(
            index = 2,
            onQueueCleared = {},
            onCurrentTrackChanged = {},
        )

        val q = sessionStateFlow.value.queue
        assertEquals(2, q.size)
        assertEquals(listOf(t1, t2), q.tracks)
        assertEquals(0, q.currentIndex)
        assertTrue(flushRequested)
    }

    @Test
    fun removeQueueTrack_removesCurrentTrackAndSwitchesToNext() {
        var changedTrack: AudioTrack? = null
        queueManager.removeQueueTrack(
            index = 0,
            onQueueCleared = {},
            onCurrentTrackChanged = { changedTrack = it },
        )

        val q = sessionStateFlow.value.queue
        assertEquals(2, q.size)
        assertEquals(listOf(t2, t3), q.tracks)
        assertEquals(t2, changedTrack)
        assertEquals(t2.durationMs, sessionStateFlow.value.durationMs)
        assertTrue(flushRequested)
    }

    @Test
    fun removeQueueTrack_clearsQueueWhenLastTrackRemoved() {
        sessionStateFlow.value =
            sessionStateFlow.value.copy(queue = PlaybackQueue(listOf(t1), currentIndex = 0))

        var queueClearedCalled = false
        queueManager.removeQueueTrack(
            index = 0,
            onQueueCleared = { queueClearedCalled = true },
            onCurrentTrackChanged = {},
        )

        assertTrue(sessionStateFlow.value.queue.isEmpty)
        assertEquals(PlaybackState.Idle, sessionStateFlow.value.playbackState)
        assertTrue(queueClearedCalled)
        assertTrue(flushRequested)
    }

    @Test
    fun shufflePermutation_remainsStableAcrossQueueProgressionAndSettingMode() {
        queueManager.setPlaybackMode(PlaybackMode.SHUFFLE)
        val initialOrder = sessionStateFlow.value.queue.shuffleOrder
        assertEquals(3, initialOrder.size)

        // Advancing next index under shuffle maintains the permutation order
        val nextIdx = queueManager.calculateNextQueueIndex(sessionStateFlow.value.queue, PlaybackMode.SHUFFLE)
        val targetOrderIndex = (initialOrder.indexOf(sessionStateFlow.value.queue.currentIndex) + 1) % initialOrder.size
        assertEquals(initialOrder[targetOrderIndex], nextIdx)

        // Setting same mode does not regenerate/reshuffle the order
        queueManager.setPlaybackMode(PlaybackMode.SHUFFLE)
        assertEquals(initialOrder, sessionStateFlow.value.queue.shuffleOrder)
    }

    @Test
    fun playNext_whenQueueEmptyAndActiveServerNull_delegatesToPlayTrackIfServerRepoPresent() {
        sessionStateFlow.value = sessionStateFlow.value.copy(activeServer = null, queue = PlaybackQueue.EMPTY)
        val insert = createTrack("fallback")
        var playedTrack: AudioTrack? = null

        queueManager.playNext(insert, hasServerOrRepo = true, onPlayTrack = { playedTrack = it })

        assertEquals(insert, playedTrack)
    }
}
