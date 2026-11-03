package com.webdav.player.ui.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.AudiophileSpecs
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.player.FullPlayerDefaults
import com.webdav.player.ui.theme.AppIcons

/**
 * Audiophile format specification badge capsule (e.g. "96kHz · 24bit · FLAC" or "Hi-Res").
 */
@Composable
internal fun AudiophileSpecsBadge(
    specs: AudiophileSpecs?,
    modifier: Modifier = Modifier,
) {
    if (specs == null || specs.formatted.isBlank()) return
    val isHiRes = specs.isHiRes
    Surface(
        shape = RoundedCornerShape(12.dp),
        color =
            if (isHiRes) {
                Color(0x26FFB703) // Subtle gold tint for Hi-Res
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (isHiRes) {
                        Color(0x4DFFB703) // Subtle gold border for Hi-Res
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    },
            ),
        modifier = modifier,
    ) {
        Text(
            text = specs.formatted,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
            color =
                if (isHiRes) {
                    Color(0xFFFFB703)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Playback mode button cycling through LIST_LOOP, SINGLE_LOOP, and SHUFFLE.
 */
@Composable
internal fun PlaybackModeButton(
    mode: PlaybackMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (mode) {
            PlaybackMode.LIST_LOOP -> AppIcons.Repeat
            PlaybackMode.SINGLE_LOOP -> AppIcons.RepeatOne
            PlaybackMode.SHUFFLE -> AppIcons.Shuffle
        }

    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mode.label,
            modifier = Modifier.size(26.dp),
            tint =
                when (mode) {
                    PlaybackMode.LIST_LOOP -> MaterialTheme.colorScheme.onSurfaceVariant
                    PlaybackMode.SINGLE_LOOP, PlaybackMode.SHUFFLE -> MaterialTheme.colorScheme.primary
                },
        )
    }
}

/**
 * Expressive transport playback control buttons: mode, previous, play/pause/buffer, next, and queue.
 */
@Composable
internal fun PlayerPlaybackControls(
    sessionState: PlayerSessionState,
    onTogglePlayPause: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Playback Mode Button
        PlaybackModeButton(
            mode = sessionState.playbackMode,
            onClick = onCyclePlaybackMode,
        )

        // Previous Track Button: FilledTonal Pill Button
        FilledTonalIconButton(
            onClick = onSkipToPrevious,
            enabled = sessionState.queue.isNotEmpty,
            modifier = Modifier.size(FullPlayerDefaults.SkipButtonSize),
            shape = FullPlayerDefaults.SkipButtonShape,
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.outlineVariant,
                ),
        ) {
            Icon(
                imageVector = AppIcons.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(24.dp),
            )
        }

        // Expressive Prominent Play / Pause / Buffering Toggle Button: 68dp Squircle (26dp rounded corner)
        FilledIconButton(
            onClick = {
                if (sessionState.isSuspendedRetry) onRetryPlayback() else onTogglePlayPause()
            },
            modifier =
                Modifier
                    .size(FullPlayerDefaults.PlayButtonSize)
                    .shadow(
                        elevation = 8.dp,
                        shape = FullPlayerDefaults.PlayButtonSquircleShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ),
            shape = FullPlayerDefaults.PlayButtonSquircleShape,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            AnimatedContent(
                targetState = sessionState.playbackState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "FullPlayerPlayPause",
            ) { state ->
                when (state) {
                    is PlaybackState.Buffering -> {
                        if (sessionState.isSuspendedRetry) {
                            Icon(
                                imageVector = AppIcons.Refresh,
                                contentDescription = "重试",
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    is PlaybackState.Playing -> {
                        Icon(
                            imageVector = AppIcons.Pause,
                            contentDescription = "暂停",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = AppIcons.PlayArrow,
                            contentDescription = "播放",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        // Next Track Button: FilledTonal Pill Button
        FilledTonalIconButton(
            onClick = onSkipToNext,
            enabled = sessionState.queue.isNotEmpty,
            modifier = Modifier.size(FullPlayerDefaults.SkipButtonSize),
            shape = FullPlayerDefaults.SkipButtonShape,
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.outlineVariant,
                ),
        ) {
            Icon(
                imageVector = AppIcons.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(24.dp),
            )
        }

        // Queue Sheet Button
        IconButton(
            onClick = onOpenQueue,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = AppIcons.QueueMusic,
                contentDescription = "播放队列",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Transport controls aggregating the progress slider and playback control actions.
 */
@Composable
internal fun PlayerTransportControls(
    sessionState: PlayerSessionState,
    playbackProgress: PlaybackProgress,
    isScrubbing: Boolean,
    displayPositionMs: Long,
    onScrubbingChange: (Boolean, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PlayerProgressSlider(
            playbackProgress = playbackProgress,
            sessionDurationMs = sessionState.durationMs,
            displayPositionMs = displayPositionMs,
            isScrubbing = isScrubbing,
            onScrubbingChange = onScrubbingChange,
            onSeek = onSeek,
        )

        Spacer(modifier = Modifier.height(10.dp))

        PlayerPlaybackControls(
            sessionState = sessionState,
            onTogglePlayPause = onTogglePlayPause,
            onRetryPlayback = onRetryPlayback,
            onSkipToNext = onSkipToNext,
            onSkipToPrevious = onSkipToPrevious,
            onCyclePlaybackMode = onCyclePlaybackMode,
            onOpenQueue = onOpenQueue,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerTransportControlsPreview() {
    val sampleTrack =
        AudioTrack(
            id = "1:/track.flac",
            serverId = 1L,
            remotePath = "/track.flac",
            title = "Preview Song",
            artist = "Preview Artist",
            durationMs = 180000L,
            format = AudioFormat.FLAC,
        )
    MaterialTheme {
        PlayerTransportControls(
            sessionState =
                PlayerSessionState(
                    queue = PlaybackQueue(tracks = listOf(sampleTrack), currentIndex = 0),
                    playbackState = PlaybackState.Playing,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    durationMs = 180000L,
                ),
            playbackProgress = PlaybackProgress(currentPositionMs = 60000L, durationMs = 180000L),
            isScrubbing = false,
            displayPositionMs = 60000L,
            onScrubbingChange = { _, _ -> },
            onSeek = {},
            onTogglePlayPause = {},
            onRetryPlayback = {},
            onSkipToNext = {},
            onSkipToPrevious = {},
            onCyclePlaybackMode = {},
            onOpenQueue = {},
        )
    }
}
