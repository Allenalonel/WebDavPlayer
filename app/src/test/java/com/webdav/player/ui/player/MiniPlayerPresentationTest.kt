package com.webdav.player.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerPresentationTest {

    private fun createTrack(
        title: String = "Song A",
        artist: String? = "Artist A",
        format: AudioFormat = AudioFormat.FLAC
    ) = AudioTrack(
        id = "1:/song.flac",
        serverId = 1L,
        remotePath = "/song.flac",
        title = title,
        artist = artist,
        album = "Album A",
        format = format
    )

    private fun sessionWithTrack(track: AudioTrack?): PlayerSessionState {
        return if (track == null) {
            PlayerSessionState()
        } else {
            PlayerSessionState(queue = PlaybackQueue(listOf(track), 0))
        }
    }

    @Test
    fun formatSubtitle_withArtistAndFormat_returnsCombinedString() {
        val session = sessionWithTrack(createTrack(artist = "Pink Floyd", format = AudioFormat.FLAC))
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("Pink Floyd · FLAC", subtitle)
    }

    @Test
    fun formatSubtitle_withArtistOnly_whenFormatIsUnavailable() {
        val track = AudioTrack(
            id = "1:/song.mp3",
            serverId = 1L,
            remotePath = "/song.mp3",
            title = "Acoustic",
            artist = "Eric Clapton",
            format = AudioFormat.MP3
        )
        val session = sessionWithTrack(track)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("Eric Clapton · MP3", subtitle)
    }

    @Test
    fun formatSubtitle_withNoArtist_returnsFormatOnly() {
        val track = AudioTrack(
            id = "1:/track01.wav",
            serverId = 1L,
            remotePath = "/track01.wav",
            title = "Track 01",
            artist = null,
            format = AudioFormat.WAV
        )
        val session = sessionWithTrack(track)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("WAV", subtitle)
    }

    @Test
    fun formatSubtitle_nullTrack_returnsEmpty() {
        val session = sessionWithTrack(null)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("", subtitle)
    }

    @Test
    fun formatSubtitle_blankArtist_returnsFormatOnly() {
        val track = AudioTrack(
            id = "1:/track04.wma",
            serverId = 1L,
            remotePath = "/track04.wma",
            title = "Track 04",
            artist = "   ",
            format = AudioFormat.WMA
        )
        val session = sessionWithTrack(track)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("WMA", subtitle)
    }

    @Test
    fun miniPlayerGeometry_capsuleAndMicroBarDimensions_matchSpecification() {
        assertEquals(20.dp, MiniPlayerDefaults.CapsuleCornerRadius)
        assertEquals(2.dp, MiniPlayerDefaults.MicroBarHeight)
        assertEquals(RoundedCornerShape(20.dp), MiniPlayerDefaults.CapsuleShape)
    }

    @Test
    fun miniPlayerTouchClearance_playPauseAndSkipNextTargets_doNotOverlap() {
        assertEquals(42.dp, MiniPlayerDefaults.PlayPauseButtonSize)
        assertEquals(38.dp, MiniPlayerDefaults.SkipNextButtonSize)
        assertEquals(8.dp, MiniPlayerDefaults.ButtonSpacing)

        val centerDistance = (MiniPlayerDefaults.PlayPauseButtonSize / 2) +
            MiniPlayerDefaults.ButtonSpacing +
            (MiniPlayerDefaults.SkipNextButtonSize / 2)
        // Material Design 3 minimum interactive component dimension is 48.dp
        val md3MinimumInteractiveSize = 48.dp

        assertTrue(
            "Center-to-center distance ($centerDistance) must be at least $md3MinimumInteractiveSize to avoid touch overlap",
            centerDistance >= md3MinimumInteractiveSize
        )
        assertEquals(48.dp, centerDistance)
    }

    @Test
    fun miniPlayerMicroBarShape_bottomCornersAreClippedToCapsuleRadius() {
        val density = Density(density = 2f, fontScale = 1f)
        val size = Size(width = 360f, height = 4f)
        val expectedCornerPx = with(density) { MiniPlayerDefaults.CapsuleCornerRadius.toPx() }

        // Top corners must be flush (0px), bottom corners must match capsule radius
        assertEquals(0f, MiniPlayerDefaults.ProgressMicroBarShape.topStart.toPx(size, density), 0.01f)
        assertEquals(0f, MiniPlayerDefaults.ProgressMicroBarShape.topEnd.toPx(size, density), 0.01f)
        assertEquals(expectedCornerPx, MiniPlayerDefaults.ProgressMicroBarShape.bottomStart.toPx(size, density), 0.01f)
        assertEquals(expectedCornerPx, MiniPlayerDefaults.ProgressMicroBarShape.bottomEnd.toPx(size, density), 0.01f)
    }
}
