package com.webdav.player.ui.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Dual-layer buffered progress slider with elapsed and remaining timestamps.
 */
@Composable
internal fun PlayerProgressSlider(
    playbackProgress: PlaybackProgress,
    sessionDurationMs: Long,
    displayPositionMs: Long,
    isScrubbing: Boolean,
    onScrubbingChange: (Boolean, Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemainingTime by remember { mutableStateOf(false) }
    var localDragPosition by remember { mutableStateOf<Float?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        val maxDuration =
            if (playbackProgress.durationMs > 0L) {
                playbackProgress.durationMs
            } else {
                sessionDurationMs.coerceAtLeast(0L)
            }
        val currentPosition = localDragPosition?.toLong() ?: displayPositionMs
        val sliderValue =
            if (maxDuration > 0L) {
                currentPosition.coerceIn(0L, maxDuration).toFloat()
            } else {
                0f
            }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Layer 1 (bottom): Translucent secondary track/indicator showing WebDAV remote buffer progress
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            ) {
                val bufferedFraction = playbackProgress.bufferedFraction.coerceIn(0f, 1f)
                if (bufferedFraction > 0f) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(fraction = bufferedFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
                    )
                }
            }

            // Layer 2 (top): Interactive M3 Slider with thumb showing elapsed playback progress
            Slider(
                value = sliderValue,
                onValueChange = { newPos ->
                    localDragPosition = newPos
                    onScrubbingChange(true, newPos.toLong())
                },
                onValueChangeFinished = {
                    val finalSeekMs = (localDragPosition?.toLong() ?: displayPositionMs).coerceIn(0L, maxDuration)
                    localDragPosition = null
                    onSeek(finalSeekMs)
                    onScrubbingChange(false, finalSeekMs)
                },
                valueRange = 0f..(if (maxDuration > 0L) maxDuration.toFloat() else 1f),
                enabled = maxDuration > 0L,
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.Transparent,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    if (isScrubbing || localDragPosition != null) {
                        PlaybackProgress.formatMs(currentPosition)
                    } else {
                        playbackProgress.formattedCurrentPosition
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    if (showRemainingTime && maxDuration > currentPosition) {
                        "-${PlaybackProgress.formatMs(maxDuration - currentPosition)}"
                    } else if (!isScrubbing && localDragPosition == null) {
                        playbackProgress.formattedDuration
                    } else {
                        PlaybackProgress.formatMs(maxDuration)
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.clickable {
                        showRemainingTime = !showRemainingTime
                    },
            )
        }
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

        // Previous Track Button
        IconButton(
            onClick = onSkipToPrevious,
            enabled = sessionState.queue.isNotEmpty,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = AppIcons.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(36.dp),
                tint =
                    if (sessionState.queue.isNotEmpty) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
            )
        }

        // Expressive Prominent Play / Pause / Buffering Toggle Button
        FilledIconButton(
            onClick = {
                if (sessionState.isSuspendedRetry) onRetryPlayback() else onTogglePlayPause()
            },
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
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
                                modifier = Modifier.size(38.dp),
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
                            modifier = Modifier.size(38.dp),
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = AppIcons.PlayArrow,
                            contentDescription = "播放",
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
        }

        // Next Track Button
        IconButton(
            onClick = onSkipToNext,
            enabled = sessionState.queue.isNotEmpty,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = AppIcons.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(36.dp),
                tint =
                    if (sessionState.queue.isNotEmpty) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
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

/**
 * Standalone overload of [PlayerTransportControls] managing scrubbing state internally.
 */
@Composable
internal fun PlayerTransportControls(
    sessionState: PlayerSessionState,
    playbackProgress: PlaybackProgress,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbedPositionMs by remember { mutableLongStateOf(0L) }
    val displayPositionMs = if (isScrubbing) scrubbedPositionMs else playbackProgress.currentPositionMs

    PlayerTransportControls(
        sessionState = sessionState,
        playbackProgress = playbackProgress,
        isScrubbing = isScrubbing,
        displayPositionMs = displayPositionMs,
        onScrubbingChange = { scrubbing, pos ->
            isScrubbing = scrubbing
            scrubbedPositionMs = pos
        },
        onSeek = onSeek,
        onTogglePlayPause = onTogglePlayPause,
        onRetryPlayback = onRetryPlayback,
        onSkipToNext = onSkipToNext,
        onSkipToPrevious = onSkipToPrevious,
        onCyclePlaybackMode = onCyclePlaybackMode,
        onOpenQueue = onOpenQueue,
        modifier = modifier,
    )
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
