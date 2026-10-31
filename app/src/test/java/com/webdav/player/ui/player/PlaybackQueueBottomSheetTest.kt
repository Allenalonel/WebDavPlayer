package com.webdav.player.ui.player

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun queueTrackItemRow_composableExistsOnClasspath() {
        val clazz =
            runCatching {
                Class.forName("com.webdav.player.ui.player.QueueTrackItemRowKt")
            }.getOrElse {
                Class.forName("com.webdav.player.ui.player.PlaybackQueueBottomSheetKt")
            }
        val methodNames = clazz.methods.map { it.name }
        assertTrue(
            "QueueTrackItemRow must exist on classpath",
            methodNames.any { it.startsWith("QueueTrackItemRow") },
        )
    }

    @Test
    fun playbackQueueBottomSheet_lineCountWithinBudget() {
        val sheetFile = File("src/main/java/com/webdav/player/ui/player/PlaybackQueueBottomSheet.kt")
        assertTrue("PlaybackQueueBottomSheet.kt must exist", sheetFile.exists())
        val sheetLines = sheetFile.readLines().size
        assertTrue(
            "PlaybackQueueBottomSheet.kt line count must be <= 190 lines, but was $sheetLines",
            sheetLines <= 190,
        )

        val collaboratorFile = File("src/main/java/com/webdav/player/ui/player/QueueTrackItemRow.kt")
        if (collaboratorFile.exists()) {
            val collabLines = collaboratorFile.readLines().size
            assertTrue(
                "QueueTrackItemRow.kt line count must be strictly < 600 lines, but was $collabLines",
                collabLines < 600,
            )
        }
    }
}
