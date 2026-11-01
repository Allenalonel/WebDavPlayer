package com.webdav.player.ui.playlist

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.ui.unit.dp
import com.webdav.player.ui.playlist.components.PlaylistTrackRowDefaults
import com.webdav.player.ui.playlist.components.PlaylistTrackSwipeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class PlaylistTrackRowTest {
    @Test
    fun positionalThreshold_isStrictlyFiftyPercentOfTotalDistance() {
        assertEquals(100f, PlaylistTrackSwipeHelper.positionalThreshold(200f), 0.001f)
        assertEquals(250f, PlaylistTrackSwipeHelper.positionalThreshold(500f), 0.001f)
        assertEquals(0f, PlaylistTrackSwipeHelper.positionalThreshold(0f), 0.001f)
    }

    @Test
    fun isPastThreshold_rightSwipePositiveOffset() {
        val width = 400f
        assertFalse(PlaylistTrackSwipeHelper.isPastThreshold(offset = 0f, width = width))
        assertFalse(PlaylistTrackSwipeHelper.isPastThreshold(offset = 199.9f, width = width))
        assertTrue(PlaylistTrackSwipeHelper.isPastThreshold(offset = 200f, width = width))
        assertTrue(PlaylistTrackSwipeHelper.isPastThreshold(offset = 250f, width = width))
    }

    @Test
    fun isPastThreshold_leftSwipeNegativeOffset() {
        val width = 400f
        assertFalse(PlaylistTrackSwipeHelper.isPastThreshold(offset = -199.9f, width = width))
        assertTrue(PlaylistTrackSwipeHelper.isPastThreshold(offset = -200f, width = width))
        assertTrue(PlaylistTrackSwipeHelper.isPastThreshold(offset = -300f, width = width))
    }

    @Test
    fun isPastThreshold_zeroOrNegativeWidth_returnsFalse() {
        assertFalse(PlaylistTrackSwipeHelper.isPastThreshold(offset = 100f, width = 0f))
        assertFalse(PlaylistTrackSwipeHelper.isPastThreshold(offset = 100f, width = -50f))
    }

    @Test
    fun handleDismiss_whenPastThreshold_startToEndDeletesAndDismisses() {
        var removeCalled = false
        var playNextCalled = false

        val dismissed =
            PlaylistTrackSwipeHelper.handleDismiss(
                value = SwipeToDismissBoxValue.StartToEnd,
                isPastThreshold = true,
                onRemove = { removeCalled = true },
                onPlayNext = { playNextCalled = true },
            )

        assertTrue("Should return true to confirm dismiss for deletion", dismissed)
        assertTrue("onRemove should be invoked", removeCalled)
        assertFalse("onPlayNext should not be invoked", playNextCalled)
    }

    @Test
    fun handleDismiss_whenPastThreshold_endToStartPlaysNextAndBouncesBack() {
        var removeCalled = false
        var playNextCalled = false

        val dismissed =
            PlaylistTrackSwipeHelper.handleDismiss(
                value = SwipeToDismissBoxValue.EndToStart,
                isPastThreshold = true,
                onRemove = { removeCalled = true },
                onPlayNext = { playNextCalled = true },
            )

        assertFalse("Should return false so left-swipe elastically bounces back", dismissed)
        assertFalse("onRemove should not be invoked", removeCalled)
        assertTrue("onPlayNext should be invoked", playNextCalled)
    }

    @Test
    fun handleDismiss_whenUnderThreshold_doesNotTriggerActionsAndCancels() {
        var removeCalled = false
        var playNextCalled = false

        val startToEndResult =
            PlaylistTrackSwipeHelper.handleDismiss(
                value = SwipeToDismissBoxValue.StartToEnd,
                isPastThreshold = false,
                onRemove = { removeCalled = true },
                onPlayNext = { playNextCalled = true },
            )
        assertFalse(startToEndResult)
        assertFalse(removeCalled)
        assertFalse(playNextCalled)

        val endToStartResult =
            PlaylistTrackSwipeHelper.handleDismiss(
                value = SwipeToDismissBoxValue.EndToStart,
                isPastThreshold = false,
                onRemove = { removeCalled = true },
                onPlayNext = { playNextCalled = true },
            )
        assertFalse(endToStartResult)
        assertFalse(removeCalled)
        assertFalse(playNextCalled)
    }

    @Test
    fun handleDismiss_settledValue_returnsFalseWithoutAction() {
        var removeCalled = false
        var playNextCalled = false

        val result =
            PlaylistTrackSwipeHelper.handleDismiss(
                value = SwipeToDismissBoxValue.Settled,
                isPastThreshold = true,
                onRemove = { removeCalled = true },
                onPlayNext = { playNextCalled = true },
            )
        assertFalse(result)
        assertFalse(removeCalled)
        assertFalse(playNextCalled)
    }

    @Test
    fun dragElevation_matchesSpecification() {
        assertEquals(6.dp, PlaylistTrackRowDefaults.DragElevation)
        assertEquals(0.dp, PlaylistTrackRowDefaults.IdleElevation)
    }
}
