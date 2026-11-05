package com.webdav.player.ui.player

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.ui.theme.AppIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class PlaybackQueueBottomSheetTest {
    private fun createTrack(
        id: String = "track_1",
        title: String = "Time",
        artist: String? = "Pink Floyd",
    ): AudioTrack =
        AudioTrack(
            id = id,
            serverId = 1L,
            remotePath = "/Music/Time.flac",
            title = title,
            artist = artist,
            format = AudioFormat.FLAC,
        )

    @Test
    fun resolveQueueTrackSubtitle_inactiveTrack_returnsArtist() {
        val track = createTrack(artist = "Pink Floyd")
        val result =
            resolveQueueTrackSubtitle(
                track = track,
                isActive = false,
                playbackProgress = PlaybackProgress.ZERO,
            )
        assertEquals("Pink Floyd", result)
    }

    @Test
    fun resolveQueueTrackSubtitle_inactiveTrackWithNullArtist_returnsFallback() {
        val track = createTrack(artist = null)
        val result =
            resolveQueueTrackSubtitle(
                track = track,
                isActive = false,
                playbackProgress = PlaybackProgress.ZERO,
            )
        assertEquals("未知艺术家", result)
    }

    @Test
    fun resolveQueueTrackSubtitle_activeTrackWithProgress_formatsPositionAndDuration() {
        val track = createTrack(artist = "Pink Floyd")
        val progress =
            PlaybackProgress(
                currentPositionMs = 65_000L,
                durationMs = 180_000L,
                bufferedPositionMs = 100_000L,
            )
        val result =
            resolveQueueTrackSubtitle(
                track = track,
                isActive = true,
                playbackProgress = progress,
            )
        assertEquals("Pink Floyd · 01:05 / 03:00", result)
    }

    @Test
    fun resolveQueueTrackSubtitle_activeTrackWithZeroDuration_returnsArtistOnly() {
        val track = createTrack(artist = "Pink Floyd")
        val progress =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
            )
        val result =
            resolveQueueTrackSubtitle(
                track = track,
                isActive = true,
                playbackProgress = progress,
            )
        assertEquals("Pink Floyd", result)
    }

    @Test
    fun handleQueueDismiss_swipeActions_triggerRemoveAndConfirm() {
        var removed = false
        val startToEndResult =
            handleQueueDismiss(
                value = SwipeToDismissBoxValue.StartToEnd,
                onRemove = { removed = true },
            )
        assertTrue("StartToEnd must confirm dismiss", startToEndResult)
        assertTrue("StartToEnd must call onRemove", removed)

        removed = false
        val endToStartResult =
            handleQueueDismiss(
                value = SwipeToDismissBoxValue.EndToStart,
                onRemove = { removed = true },
            )
        assertTrue("EndToStart must confirm dismiss", endToStartResult)
        assertTrue("EndToStart must call onRemove", removed)

        removed = false
        val settledResult =
            handleQueueDismiss(
                value = SwipeToDismissBoxValue.Settled,
                onRemove = { removed = true },
            )
        assertFalse("Settled must not confirm dismiss", settledResult)
        assertFalse("Settled must not call onRemove", removed)
    }

    @Test
    fun cyclePlaybackMode_cyclesThroughAllModesInCanonicalOrder() {
        val mode0 = PlaybackMode.LIST_LOOP
        val mode1 = cyclePlaybackMode(mode0)
        assertEquals(PlaybackMode.SINGLE_LOOP, mode1)

        val mode2 = cyclePlaybackMode(mode1)
        assertEquals(PlaybackMode.SHUFFLE, mode2)

        val mode3 = cyclePlaybackMode(mode2)
        assertEquals(PlaybackMode.LIST_LOOP, mode3)
    }

    @Test
    fun playbackMode_labelsAndIcons_matchSpecification() {
        assertEquals("列表循环", PlaybackMode.LIST_LOOP.label)
        assertSame(AppIcons.Repeat, resolvePlaybackModeIcon(PlaybackMode.LIST_LOOP))

        assertEquals("单曲循环", PlaybackMode.SINGLE_LOOP.label)
        assertSame(AppIcons.RepeatOne, resolvePlaybackModeIcon(PlaybackMode.SINGLE_LOOP))

        assertEquals("随机播放", PlaybackMode.SHUFFLE.label)
        assertSame(AppIcons.Shuffle, resolvePlaybackModeIcon(PlaybackMode.SHUFFLE))
    }

    @Test
    fun resolveQueueItemKey_returnsTrackId_independentOfListIndex() {
        val trackA = createTrack(id = "track_alpha", title = "Track Alpha")
        val trackB = createTrack(id = "track_beta", title = "Track Beta")

        // Key depends strictly on track identity, not index position
        assertEquals("track_alpha", resolveQueueItemKey(trackA))
        assertEquals("track_beta", resolveQueueItemKey(trackB))
        assertNotEquals(resolveQueueItemKey(trackA), resolveQueueItemKey(trackB))
    }

    @Test
    fun queue_shuffleTransition_pinsPlayingTrackAtIndex0_andPreservesStableKeys() {
        val tracks = (1..5).map { createTrack(id = "track_$it", title = "Track $it") }
        // Currently playing track is index 2 ("track_3")
        val originalQueue = PlaybackQueue(tracks = tracks, currentIndex = 2)

        // Switch to SHUFFLE mode
        val shuffledQueue = originalQueue.toShuffled()

        // 1. Current track remains pinned at index 0
        assertEquals(0, shuffledQueue.currentIndex)
        assertEquals("track_3", shuffledQueue.currentTrack?.id)
        assertEquals("track_3", shuffledQueue.tracks[0].id)
        assertEquals(5, shuffledQueue.size)

        // 2. Stable keys are preserved across all slots
        val originalKeySet = originalQueue.tracks.map { resolveQueueItemKey(it) }.toSet()
        val shuffledKeySet = shuffledQueue.tracks.map { resolveQueueItemKey(it) }.toSet()
        assertEquals(originalKeySet, shuffledKeySet)

        // 3. Switch back to unshuffled (LIST_LOOP) restores original sequence and index
        val unshuffledQueue = shuffledQueue.toUnshuffled()
        assertEquals(2, unshuffledQueue.currentIndex)
        assertEquals("track_3", unshuffledQueue.currentTrack?.id)
        assertEquals(tracks.map { it.id }, unshuffledQueue.tracks.map { it.id })
    }

    @Test
    fun queue_removeTrack_preservesStableKeysForRemainingItems() {
        val tracks = (1..4).map { createTrack(id = "track_$it", title = "Track $it") }
        val queue = PlaybackQueue(tracks = tracks, currentIndex = 0)

        // Remove track at index 1 ("track_2")
        val updatedQueue = queue.removeTrackAt(1)

        assertEquals(3, updatedQueue.size)
        val remainingKeys = updatedQueue.tracks.map { resolveQueueItemKey(it) }
        assertEquals(listOf("track_1", "track_3", "track_4"), remainingKeys)
    }
}
