package com.webdav.player.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {
    private fun createTrack(
        id: String,
        title: String,
    ): AudioTrack =
        AudioTrack(
            id = id,
            serverId = 1L,
            remotePath = "/$id",
            title = title,
            format = AudioFormat.MP3,
        )

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

    @Test
    fun folderRingPlayback_strictlyConfinedWithinCurrentFolder() {
        val folderTracks = (1..5).map { createTrack("f_$it", "Folder Track $it") }
        val queue = PlaybackQueue(tracks = folderTracks, currentIndex = 0)

        // Forward traversal: 0 -> 1 -> 2 -> 3 -> 4 -> 0 (loop back)
        var current = queue
        for (expectedIndex in listOf(1, 2, 3, 4, 0, 1)) {
            val nextIndex = current.getNextIndex(PlaybackMode.LIST_LOOP)!!
            assertEquals(expectedIndex, nextIndex)
            current = current.copy(currentIndex = nextIndex)
        }

        // Backward traversal from 0 wraps back to the end of the folder (index 4)
        val atStart = queue.copy(currentIndex = 0)
        assertEquals(4, atStart.getPreviousIndex(PlaybackMode.LIST_LOOP))

        // Single track folder strictly returns 0 and does not jump out
        val singleTrackQueue = PlaybackQueue(tracks = listOf(createTrack("solo", "Solo")), currentIndex = 0)
        assertEquals(0, singleTrackQueue.getNextIndex(PlaybackMode.LIST_LOOP))
        assertEquals(0, singleTrackQueue.getPreviousIndex(PlaybackMode.LIST_LOOP))
    }

    @Test
    fun virtualTracks_queueMapping_maintainsVirtualTrackIdsAndTitles() {
        val parentTrack =
            createTrack("parent_album", "Parent Album").copy(
                remotePath = "/Music/album.flac",
                format = AudioFormat.FLAC,
            )
        val virtualTracks =
            listOf(
                VirtualTrack(
                    trackNumber = 1,
                    title = "Virtual Track 1",
                    performer = "Performer A",
                    startTimeMs = 0L,
                    endTimeMs = 180000L,
                    parentAudioPath = "/Music/album.flac",
                ),
                VirtualTrack(
                    trackNumber = 2,
                    title = "Virtual Track 2",
                    performer = "Performer B",
                    startTimeMs = 180000L,
                    endTimeMs = 360000L,
                    parentAudioPath = "/Music/album.flac",
                ),
            )

        val queueTracks =
            virtualTracks.map { vt ->
                AudioTrack(
                    id = "${parentTrack.id}#cue_${vt.trackNumber}",
                    serverId = parentTrack.serverId,
                    remotePath = parentTrack.remotePath,
                    title = vt.title,
                    artist = vt.performer ?: parentTrack.artist,
                    durationMs = vt.durationMs,
                    format = parentTrack.format,
                )
            }
        val queue = PlaybackQueue(tracks = queueTracks, currentIndex = 0)

        assertEquals(2, queue.size)
        assertEquals("parent_album#cue_1", queue.currentTrack?.id)
        assertEquals("Virtual Track 1", queue.currentTrack?.title)
        assertEquals("Performer A", queue.currentTrack?.artist)
        assertEquals(180000L, queue.currentTrack?.durationMs)

        val switched = queue.playTrackAt(1)
        assertEquals(1, switched.currentIndex)
        assertEquals("parent_album#cue_2", switched.currentTrack?.id)
        assertEquals("Virtual Track 2", switched.currentTrack?.title)

        // Folder Ring looping over virtual tracks
        assertEquals(0, switched.getNextIndex(PlaybackMode.LIST_LOOP))
    }

    @Test
    fun fromTracks_withVirtualTracksMap_inlinesVirtualTracksAndAdjustsIndices() {
        val t1 = createTrack("track_1", "Intro").copy(remotePath = "/music/01.mp3")
        val tAlbum = createTrack("parent_album", "Full Album").copy(
            remotePath = "/music/album.flac",
            format = AudioFormat.FLAC,
        )
        val t3 = createTrack("track_3", "Outro").copy(remotePath = "/music/03.mp3")

        val vt1 = VirtualTrack(
            trackNumber = 1,
            title = "Overture",
            performer = "Orchestra",
            startTimeMs = 0L,
            endTimeMs = 120000L,
            parentAudioPath = "/music/album.flac",
        )
        val vt2 = VirtualTrack(
            trackNumber = 2,
            title = "Movement 1",
            performer = "Orchestra",
            startTimeMs = 120000L,
            endTimeMs = 300000L,
            parentAudioPath = "/music/album.flac",
        )
        val vt3 = VirtualTrack(
            trackNumber = 3,
            title = "Movement 2",
            performer = "Orchestra",
            startTimeMs = 300000L,
            endTimeMs = 500000L,
            parentAudioPath = "/music/album.flac",
        )

        val virtualMap = mapOf("/music/album.flac" to listOf(vt1, vt2, vt3))
        val rawTracks = listOf(t1, tAlbum, t3)

        // Case 1: Clicked track before album (index 0)
        val queueAt0 = PlaybackQueue.fromTracks(rawTracks, virtualMap, selectedIndex = 0)
        assertEquals(5, queueAt0.size)
        assertEquals(0, queueAt0.currentIndex)
        assertEquals("Intro", queueAt0.currentTrack?.title)
        assertFalse(queueAt0.currentTrack?.isVirtualTrack ?: true)

        // Case 2: Clicked album (index 1) -> inlined and points to first virtual track (index 1)
        val queueAt1 = PlaybackQueue.fromTracks(rawTracks, virtualMap, selectedIndex = 1)
        assertEquals(5, queueAt1.size)
        assertEquals(1, queueAt1.currentIndex)
        assertEquals("Overture", queueAt1.currentTrack?.title)
        assertTrue(queueAt1.currentTrack?.isVirtualTrack ?: false)
        assertEquals("parent_album#cue_1", queueAt1.currentTrack?.id)

        // Case 3: Clicked track after album (index 2) -> shifts by +2 to index 4
        val queueAt2 = PlaybackQueue.fromTracks(rawTracks, virtualMap, selectedIndex = 2)
        assertEquals(5, queueAt2.size)
        assertEquals(4, queueAt2.currentIndex)
        assertEquals("Outro", queueAt2.currentTrack?.title)
        assertFalse(queueAt2.currentTrack?.isVirtualTrack ?: true)
    }

    @Test
    fun inlineVirtualTracks_replacesTargetTrackInPlaceAndPreservesIntegrity() {
        val t1 = createTrack("1", "Song 1").copy(remotePath = "/music/1.mp3")
        val tAlbum = createTrack("album", "Album").copy(remotePath = "/music/album.flac")
        val t3 = createTrack("3", "Song 3").copy(remotePath = "/music/3.mp3")

        val vt1 = VirtualTrack(
            trackNumber = 1,
            title = "V1",
            startTimeMs = 0L,
            endTimeMs = 60000L,
            parentAudioPath = "/music/album.flac",
        )
        val vt2 = VirtualTrack(
            trackNumber = 2,
            title = "V2",
            startTimeMs = 60000L,
            endTimeMs = 120000L,
            parentAudioPath = "/music/album.flac",
        )
        val vts = listOf(vt1, vt2)

        val queueWithCurrentAtAlbum = PlaybackQueue(tracks = listOf(t1, tAlbum, t3), currentIndex = 1)
        val inlinedCurrent = queueWithCurrentAtAlbum.inlineVirtualTracks("/music/album.flac", vts)
        assertEquals(4, inlinedCurrent.size)
        assertEquals(1, inlinedCurrent.currentIndex)
        assertEquals("V1", inlinedCurrent.currentTrack?.title)

        val queueWithCurrentAfterAlbum = PlaybackQueue(tracks = listOf(t1, tAlbum, t3), currentIndex = 2)
        val inlinedAfter = queueWithCurrentAfterAlbum.inlineVirtualTracks("/music/album.flac", vts)
        assertEquals(4, inlinedAfter.size)
        assertEquals(3, inlinedAfter.currentIndex)
        assertEquals("Song 3", inlinedAfter.currentTrack?.title)

        val queueWithCurrentBeforeAlbum = PlaybackQueue(tracks = listOf(t1, tAlbum, t3), currentIndex = 0)
        val inlinedBefore = queueWithCurrentBeforeAlbum.inlineVirtualTracks("/music/album.flac", vts)
        assertEquals(4, inlinedBefore.size)
        assertEquals(0, inlinedBefore.currentIndex)
        assertEquals("Song 1", inlinedBefore.currentTrack?.title)

        // Non-matching path or empty virtual list returns identical queue
        assertEquals(queueWithCurrentAtAlbum, queueWithCurrentAtAlbum.inlineVirtualTracks("/music/unknown.flac", vts))
        assertEquals(queueWithCurrentAtAlbum, queueWithCurrentAtAlbum.inlineVirtualTracks("/music/album.flac", emptyList()))
    }

    @Test
    fun toAudioTrack_producesValidVirtualTrackAttributes() {
        val parent = createTrack("parent_id", "Parent Album").copy(
            remotePath = "/remote/album.flac",
            artist = "Original Artist",
            album = "Master Album",
            format = AudioFormat.FLAC,
            coverThumbnailPath = "/covers/art.jpg",
        )
        val vt = VirtualTrack(
            trackNumber = 4,
            title = "Specific Movement",
            performer = "Guest Soloist",
            startTimeMs = 100000L,
            endTimeMs = 250000L,
            parentAudioPath = "/remote/album.flac",
        )

        val virtualAudioTrack = vt.toAudioTrack(parent)
        assertEquals("parent_id#cue_4", virtualAudioTrack.id)
        assertEquals("/remote/album.flac", virtualAudioTrack.remotePath)
        assertEquals("Specific Movement", virtualAudioTrack.title)
        assertEquals("Guest Soloist", virtualAudioTrack.artist)
        assertEquals("Master Album", virtualAudioTrack.album)
        assertEquals(150000L, virtualAudioTrack.durationMs)
        assertEquals("/covers/art.jpg", virtualAudioTrack.coverThumbnailPath)
        assertTrue(virtualAudioTrack.isVirtualTrack)
    }
}
