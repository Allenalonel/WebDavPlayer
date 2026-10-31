package com.webdav.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.webdav.player.domain.session.MusicPlayerAppSession

/**
 * Cohesive playback intent contract aggregating user controls from transport buttons,
 * scrubbers, and queue sheets.
 */
interface PlaybackActions {
    fun togglePlayPause()

    fun seekTo(positionMs: Long)

    fun skipToNext()

    fun skipToPrevious()

    fun cyclePlaybackMode()

    fun playQueueIndex(index: Int)

    fun removeQueueTrack(index: Int)

    fun retryPlayback()

    companion object {
        val EMPTY: PlaybackActions =
            object : PlaybackActions {
                override fun togglePlayPause() {}

                override fun seekTo(positionMs: Long) {}

                override fun skipToNext() {}

                override fun skipToPrevious() {}

                override fun cyclePlaybackMode() {}

                override fun playQueueIndex(index: Int) {}

                override fun removeQueueTrack(index: Int) {}

                override fun retryPlayback() {}
            }
    }
}

/**
 * Remembers a [PlaybackActions] implementation delegating to the active [MusicPlayerAppSession].
 */
@Composable
fun rememberPlaybackActions(session: MusicPlayerAppSession?): PlaybackActions =
    remember(session) {
        if (session == null) {
            PlaybackActions.EMPTY
        } else {
            object : PlaybackActions {
                override fun togglePlayPause() = session.togglePlayPause()

                override fun seekTo(positionMs: Long) = session.seekTo(positionMs)

                override fun skipToNext() = session.skipToNext()

                override fun skipToPrevious() = session.skipToPrevious()

                override fun cyclePlaybackMode() = session.cyclePlaybackMode()

                override fun playQueueIndex(index: Int) = session.playQueueIndex(index)

                override fun removeQueueTrack(index: Int) = session.removeQueueTrack(index)

                override fun retryPlayback() = session.retryPlayback()
            }
        }
    }
