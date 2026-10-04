package com.webdav.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.common.CoverThumbnailImage
import com.webdav.player.ui.theme.AppIcons

object MiniPlayerDefaults {
    val CapsuleCornerRadius: Dp = 20.dp
    val CapsuleShape = RoundedCornerShape(CapsuleCornerRadius)
    val ProgressMicroBarShape =
        RoundedCornerShape(bottomStart = CapsuleCornerRadius, bottomEnd = CapsuleCornerRadius)
    val PlayPauseButtonSize: Dp = 42.dp
    val SkipNextButtonSize: Dp = 38.dp
    val ButtonSpacing: Dp = 8.dp
    val MicroBarHeight: Dp = 2.dp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    sessionState: PlayerSessionState,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipToNext: () -> Unit = {},
    onMiniPlayerClick: () -> Unit = {},
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
) {
    val currentTrack = sessionState.currentTrack ?: return

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MiniPlayerDefaults.CapsuleShape)
                .pointerInput(Unit) {
                    var upwardDrag = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            upwardDrag += dragAmount
                            if (upwardDrag < -20f) {
                                change.consume()
                                onMiniPlayerClick()
                                upwardDrag = 0f
                            }
                        },
                        onDragEnd = { upwardDrag = 0f },
                        onDragCancel = { upwardDrag = 0f },
                    )
                }.clickable { onMiniPlayerClick() },
        shape = MiniPlayerDefaults.CapsuleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Album Art / Audio Icon Box
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverThumbnailImage(
                        thumbnailPath = currentTrack.coverThumbnailPath,
                        contentDescription = "封面",
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Audiotrack,
                            contentDescription = "音轨",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and State Column
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = currentTrack.title,
                        modifier = Modifier.basicMarquee(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    PlaybackStateIndicator(
                        sessionState = sessionState,
                        playbackProgress = playbackProgress,
                    )
                }

                Spacer(modifier = Modifier.width(MiniPlayerDefaults.ButtonSpacing))

                // Play / Pause / Buffering Toggle Button
                PlayPauseToggleButton(
                    sessionState = sessionState,
                    onTogglePlayPause = onTogglePlayPause,
                )

                Spacer(modifier = Modifier.width(MiniPlayerDefaults.ButtonSpacing))

                // Skip Next Button
                IconButton(
                    onClick = onSkipToNext,
                    modifier =
                        Modifier
                            .size(MiniPlayerDefaults.SkipNextButtonSize)
                            .clip(CircleShape),
                ) {
                    Icon(
                        imageVector = AppIcons.SkipNext,
                        contentDescription = "下一首",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { playbackProgress.progressFraction },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerDefaults.MicroBarHeight)
                        .clip(MiniPlayerDefaults.ProgressMicroBarShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
fun DockedMiniPlayer(
    sessionState: PlayerSessionState,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipToNext: () -> Unit = {},
    onMiniPlayerClick: () -> Unit = {},
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
) {
    MiniPlayer(
        sessionState = sessionState,
        onTogglePlayPause = onTogglePlayPause,
        modifier = modifier,
        onSkipToNext = onSkipToNext,
        onMiniPlayerClick = onMiniPlayerClick,
        playbackProgress = playbackProgress,
    )
}

@Composable
private fun PlaybackStateIndicator(
    sessionState: PlayerSessionState,
    modifier: Modifier = Modifier,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            sessionState.isBuffering -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "正在缓冲...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            sessionState.isPlaying -> {
                val text =
                    if (playbackProgress.durationMs > 0L) {
                        "正在播放 · ${playbackProgress.formattedCurrentPosition} / ${playbackProgress.formattedDuration}"
                    } else {
                        "正在播放"
                    }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            sessionState.isPaused -> {
                val text =
                    if (playbackProgress.durationMs > 0L) {
                        "已暂停 · ${playbackProgress.formattedCurrentPosition} / ${playbackProgress.formattedDuration}"
                    } else {
                        "已暂停"
                    }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            sessionState.playbackState is PlaybackState.Error -> {
                Text(
                    text = sessionState.errorMessage ?: "播放出错",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            else -> {
                Text(
                    text = formatMiniPlayerSubtitle(sessionState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayPauseToggleButton(
    sessionState: PlayerSessionState,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onTogglePlayPause,
        modifier = modifier.size(MiniPlayerDefaults.PlayPauseButtonSize),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        AnimatedContent(
            targetState = sessionState.playbackState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "PlayPauseTransition",
        ) { state ->
            when (state) {
                is PlaybackState.Buffering -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                is PlaybackState.Playing -> {
                    Icon(
                        imageVector = AppIcons.Pause,
                        contentDescription = "暂停",
                        modifier = Modifier.size(22.dp),
                    )
                }

                else -> {
                    Icon(
                        imageVector = AppIcons.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

fun formatMiniPlayerSubtitle(sessionState: PlayerSessionState): String {
    val track = sessionState.currentTrack ?: return ""
    val format = currentTrackFormatLabel(sessionState)
    val artist = track.artist?.trim()?.takeIf { it.isNotBlank() }
    return when {
        artist != null && format.isNotBlank() -> "$artist · $format"
        artist != null -> artist
        format.isNotBlank() -> format
        sessionState.isPlaying -> "正在播放"
        else -> "已暂停"
    }
}

private fun currentTrackFormatLabel(sessionState: PlayerSessionState): String {
    val track = sessionState.currentTrack ?: return ""
    return track.format.extension.uppercase()
}
