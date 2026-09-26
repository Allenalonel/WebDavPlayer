package com.webdav.player.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {

    private fun createTrack(id: String, title: String): AudioTrack {
        return AudioTrack(
            id = id,
            serverId = 1L,
            remotePath = "/$id",
            title = title,
            format = AudioFormat.MP3
        )
    }

    @Test
    fun playbackMode_cycleTransitionsCorrectly() {
        assertEquals(PlaybackMode.SINGLE_LOOP, PlaybackMode.LIST_LOOP.next())
        assertEquals(PlaybackMode.SHUFFLE, PlaybackMode.SINGLE_LOOP.next())
        assertEquals(PlaybackMode.LIST_LOOP, PlaybackMode.SHUFFLE.next())
    }

    @Test
    fun emptyQueue_behavior() {
        val queue = PlaybackQueue.EMPTY
        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)
        assertNull(queue.currentTrack)
        assertFalse(queue.hasNext)
        assertFalse(queue.hasPrevious)
        assertNull(queue.getNextIndex(PlaybackMode.LIST_LOOP))
        assertNull(queue.getPreviousIndex(PlaybackMode.LIST_LOOP))
    }

    @Test
    fun playTrackAt_updatesCurrentIndex() {
        val t1 = createTrack("1", "Song 1")
        val t2 = createTrack("2", "Song 2")
        val queue = PlaybackQueue(tracks = listOf(t1, t2), currentIndex = 0)

        assertEquals(t1, queue.currentTrack)
        val updated = queue.playTrackAt(1)
        assertEquals(1, updated.currentIndex)
        assertEquals(t2, updated.currentTrack)

        // Invalid index does not change queue
        val invalid = queue.playTrackAt(5)
        assertEquals(0, invalid.currentIndex)
    }

    @Test
    fun removeTrackAt_whenIndexBeforeCurrent_decrementsCurrentIndex() {
        val t1 = createTrack("1", "Song 1")
        val t2 = createTrack("2", "Song 2")
        val t3 = createTrack("3", "Song 3")
        val queue = PlaybackQueue(tracks = listOf(t1, t2, t3), currentIndex = 1) // current is Song 2

        val updated = queue.removeTrackAt(0) // remove Song 1
        assertEquals(2, updated.size)
        assertEquals(listOf(t2, t3), updated.tracks)
        assertEquals(0, updated.currentIndex)
        assertEquals(t2, updated.currentTrack) // Still Song 2
    }

    @Test
    fun removeTrackAt_whenIndexAfterCurrent_preservesCurrentIndex() {
        val t1 = createTrack("1", "Song 1")
        val t2 = createTrack("2", "Song 2")
        val t3 = createTrack("3", "Song 3")
        val queue = PlaybackQueue(tracks = listOf(t1, t2, t3), currentIndex = 1) // current is Song 2

        val updated = queue.removeTrackAt(2) // remove Song 3
        assertEquals(2, updated.size)
        assertEquals(listOf(t1, t2), updated.tracks)
        assertEquals(1, updated.currentIndex)
        assertEquals(t2, updated.currentTrack) // Still Song 2
    }

    @Test
    fun removeTrackAt_whenIndexIsCurrentAndHasNext_pointsToNextTrack() {
        val t1 = createTrack("1", "Song 1")
        val t2 = createTrack("2", "Song 2")
        val t3 = createTrack("3", "Song 3")
        val queue = PlaybackQueue(tracks = listOf(t1, t2, t3), currentIndex = 1) // current is Song 2

        val updated = queue.removeTrackAt(1) // remove current (Song 2)
        assertEquals(2, updated.size)
        assertEquals(listOf(t1, t3), updated.tracks)
        assertEquals(1, updated.currentIndex)
        assertEquals(t3, updated.currentTrack) // Points to Song 3 (was at index 2)
    }

    @Test
    fun removeTrackAt_whenIndexIsCurrentAndIsLast_pointsToNewLastTrack() {
        val t1 = createTrack("1", "Song 1")
        val t2 = createTrack("2", "Song 2")
        val queue = PlaybackQueue(tracks = listOf(t1, t2), currentIndex = 1) // current is Song 2

        val updated = queue.removeTrackAt(1) // remove Song 2
        assertEquals(1, updated.size)
        assertEquals(listOf(t1), updated.tracks)
        assertEquals(0, updated.currentIndex)
        assertEquals(t1, updated.currentTrack)
    }

    @Test
    fun removeTrackAt_whenOnlyTrackRemoved_becomesEmpty() {
        val t1 = createTrack("1", "Song 1")
        val queue = PlaybackQueue(tracks = listOf(t1), currentIndex = 0)

        val updated = queue.removeTrackAt(0)
        assertTrue(updated.isEmpty)
        assertEquals(-1, updated.currentIndex)
        assertNull(updated.currentTrack)
    }

    @Test
    fun removeTrackAt_invalidIndex_returnsOriginalQueue() {
        val t1 = createTrack("1", "Song 1")
        val queue = PlaybackQueue(tracks = listOf(t1), currentIndex = 0)

        val updated = queue.removeTrackAt(99)
        assertEquals(queue, updated)
    }

    @Test
    fun navigationIndices_listLoop() {
        val tracks = (1..3).map { createTrack("$it", "Song $it") }
        val queue = PlaybackQueue(tracks = tracks, currentIndex = 0)

        // 0 -> 1 -> 2 -> 0 (loop)
        assertEquals(1, queue.getNextIndex(PlaybackMode.LIST_LOOP))
        assertEquals(2, queue.getPreviousIndex(PlaybackMode.LIST_LOOP))

        val atLast = queue.copy(currentIndex = 2)
        assertEquals(0, atLast.getNextIndex(PlaybackMode.LIST_LOOP))
        assertEquals(1, atLast.getPreviousIndex(PlaybackMode.LIST_LOOP))
    }

    @Test
    fun navigationIndices_singleLoop() {
        val tracks = (1..3).map { createTrack("$it", "Song $it") }
        val queue = PlaybackQueue(tracks = tracks, currentIndex = 1)

        // Single loop keeps the same track
        assertEquals(1, queue.getNextIndex(PlaybackMode.SINGLE_LOOP))
        assertEquals(1, queue.getPreviousIndex(PlaybackMode.SINGLE_LOOP))
    }

    @Test
    fun navigationIndices_shuffleWithExplicitOrder() {
        val tracks = (0..3).map { createTrack("$it", "Song $it") }
        val shuffleOrder = listOf(2, 0, 3, 1)

        val queueAt2 = PlaybackQueue(tracks = tracks, currentIndex = 2)
        assertEquals(0, queueAt2.getNextIndex(PlaybackMode.SHUFFLE, shuffleOrder))
        assertEquals(1, queueAt2.getPreviousIndex(PlaybackMode.SHUFFLE, shuffleOrder))

        val queueAt1 = PlaybackQueue(tracks = tracks, currentIndex = 1) // last in shuffle order
        assertEquals(2, queueAt1.getNextIndex(PlaybackMode.SHUFFLE, shuffleOrder)) // wraps to first
        assertEquals(3, queueAt1.getPreviousIndex(PlaybackMode.SHUFFLE, shuffleOrder))
    }

    @Test
    fun insertNext_intoEmptyQueue_createsSingleTrackQueue() {
        val t1 = createTrack("1", "Song 1")
        val queue = PlaybackQueue.EMPTY.insertNext(t1)

        assertEquals(1, queue.size)
        assertEquals(0, queue.currentIndex)
        assertEquals(t1, queue.currentTrack)
    }

    @Test
    fun insertNext_whenPlayingFirstTrack_insertsAtSecondPosition() {
        val t1 = createTrack("1", "Song 1")
        val t2 = createTrack("2", "Song 2")
        val tNew = createTrack("99", "New Next Song")

        val queue = PlaybackQueue(tracks = listOf(t1, t2), currentIndex = 0)
        val updated = queue.insertNext(tNew)

        assertEquals(3, updated.size)
        assertEquals(0, updated.currentIndex)
        assertEquals(t1, updated.currentTrack)
        assertEquals(listOf(t1, tNew, t2), updated.tracks)
    }

    @Test
    fun insertNext_whenPlayingLastTrack_insertsAtEnd() {
        val t1 = createTrack("1", "Song 1")
        val t2 = createTrack("2", "Song 2")
        val tNew = createTrack("99", "New Next Song")

        val queue = PlaybackQueue(tracks = listOf(t1, t2), currentIndex = 1)
        val updated = queue.insertNext(tNew)

        assertEquals(3, updated.size)
        assertEquals(1, updated.currentIndex)
        assertEquals(t2, updated.currentTrack)
        assertEquals(listOf(t1, t2, tNew), updated.tracks)
    }
}
