package com.webdav.player.ui.player

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
    val CapsuleHeight: Dp = 60.dp
    val CapsuleCornerRadius: Dp = 30.dp
    val CapsuleShape = RoundedCornerShape(CapsuleCornerRadius)
    val MicroBarHeight: Dp = 2.dp
    val ProgressMicroBarShape = RoundedCornerShape(MicroBarHeight)
    val CoverSize: Dp = 42.dp
    val ButtonSize: Dp = 38.dp
    val PlayPauseButtonSize: Dp = 40.dp
    val QueueMusicButtonSize: Dp = 38.dp
    val ButtonSpacing: Dp = 8.dp
    val MicroBarHorizontalPadding: Dp = 16.dp
    val MicroBarBottomPadding: Dp = 3.dp
    val SwipeSkipThreshold: Dp = 40.dp
    val VerticalDragThreshold: Float = -20f

    // Elevated solid MD3 surface styling constants
    val SurfaceBorderWidth: Dp = 1.dp
    const val SurfaceBorderAlpha: Float = 0.25f

    // Penetrative scroll content bottom padding contract
    val PenetrativeContentBottomPadding: Dp = 150.dp
}

enum class MiniPlayerGestureAction {
    NONE,
    SKIP_TO_NEXT,
    SKIP_TO_PREVIOUS,
    EXPAND_FULL_PLAYER,
}

fun resolveMiniPlayerGesture(
    dragX: Float,
    dragY: Float,
    swipeThresholdPx: Float,
    verticalDragThresholdPx: Float = MiniPlayerDefaults.VerticalDragThreshold,
): MiniPlayerGestureAction {
    val absX = kotlin.math.abs(dragX)
    val absY = kotlin.math.abs(dragY)
    return when {
        dragY < verticalDragThresholdPx && absY > absX -> MiniPlayerGestureAction.EXPAND_FULL_PLAYER
        dragX < -swipeThresholdPx && absX > absY -> MiniPlayerGestureAction.SKIP_TO_NEXT
        dragX > swipeThresholdPx && absX > absY -> MiniPlayerGestureAction.SKIP_TO_PREVIOUS
        else -> MiniPlayerGestureAction.NONE
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    sessionState: PlayerSessionState,
    playbackActions: PlaybackActions,
    modifier: Modifier = Modifier,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
    onMiniPlayerClick: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
) {
    val currentTrack = sessionState.currentTrack ?: return
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val swipeThresholdPx = with(density) { MiniPlayerDefaults.SwipeSkipThreshold.toPx() }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val borderStroke =
        BorderStroke(
            MiniPlayerDefaults.SurfaceBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MiniPlayerDefaults.SurfaceBorderAlpha),
        )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MiniPlayerDefaults.CapsuleHeight)
                .clip(MiniPlayerDefaults.CapsuleShape)
                .pointerInput(playbackActions, onMiniPlayerClick, swipeThresholdPx) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var actionTriggered = false

                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                            actionTriggered = false
                        },
                        onDragEnd = {
                            totalDragX = 0f
                            totalDragY = 0f
                            actionTriggered = false
                        },
                        onDragCancel = {
                            totalDragX = 0f
                            totalDragY = 0f
                            actionTriggered = false
                        },
                        onDrag = { change, dragAmount ->
                            if (actionTriggered) return@detectDragGestures

                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            when (resolveMiniPlayerGesture(totalDragX, totalDragY, swipeThresholdPx)) {
                                MiniPlayerGestureAction.EXPAND_FULL_PLAYER -> {
                                    actionTriggered = true
                                    change.consume()
                                    onMiniPlayerClick()
                                }

                                MiniPlayerGestureAction.SKIP_TO_NEXT -> {
                                    actionTriggered = true
                                    change.consume()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    playbackActions.skipToNext()
                                }

                                MiniPlayerGestureAction.SKIP_TO_PREVIOUS -> {
                                    actionTriggered = true
                                    change.consume()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    playbackActions.skipToPrevious()
                                }

                                MiniPlayerGestureAction.NONE -> {
                                    // Keep accumulating drag
                                }
                            }
                        },
                    )
                }.clickable { onMiniPlayerClick() },
        shape = MiniPlayerDefaults.CapsuleShape,
        color = surfaceColor,
        border = borderStroke,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isMiniPlayerRetryBannerVisible(sessionState)) {
                MiniPlayerRetryBanner(
                    onRetryPlayback = { playbackActions.retryPlayback() },
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Circular Album Art Disc (CircleShape matching capsule geometry)
                Box(
                    modifier =
                        Modifier
                            .size(MiniPlayerDefaults.CoverSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverThumbnailImage(
                        thumbnailPath = currentTrack.coverThumbnailPath,
                        contentDescription = "封面",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    ) {
                        Icon(
                            imageVector = AppIcons.Audiotrack,
                            contentDescription = "音轨",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
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
                    onTogglePlayPause = {
                        if (sessionState.isSuspendedRetry) {
                            playbackActions.retryPlayback()
                        } else {
                            playbackActions.togglePlayPause()
                        }
                    },
                )

                Spacer(modifier = Modifier.width(MiniPlayerDefaults.ButtonSpacing))

                // Queue Music Button
                FilledTonalIconButton(
                    onClick = onOpenQueue,
                    modifier =
                        Modifier
                            .size(MiniPlayerDefaults.QueueMusicButtonSize),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        imageVector = AppIcons.QueueMusic,
                        contentDescription = "播放队列",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Inset micro progress track (MD3 Elevated Solid Island)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MiniPlayerDefaults.MicroBarHorizontalPadding)
                        .padding(bottom = MiniPlayerDefaults.MicroBarBottomPadding),
            ) {
                LinearProgressIndicator(
                    progress = { playbackProgress.progressFraction },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(MiniPlayerDefaults.MicroBarHeight)
                            .clip(MiniPlayerDefaults.ProgressMicroBarShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
            }
        }
    }
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
                    color = if (sessionState.isWeakNetwork) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(10.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                val bufferText =
                    when {
                        sessionState.isSuspendedRetry -> PlayerSessionState.RETRY_BANNER_MESSAGE
                        sessionState.isWeakNetwork -> PlayerSessionState.WEAK_NETWORK_MESSAGE
                        else -> "正在缓冲..."
                    }
                Text(
                    text = bufferText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sessionState.isWeakNetwork) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
    FilledIconButton(
        onClick = onTogglePlayPause,
        modifier = modifier.size(MiniPlayerDefaults.PlayPauseButtonSize),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        AnimatedContent(
            targetState = sessionState.playbackState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "PlayPauseTransition",
        ) { state ->
            when (state) {
                is PlaybackState.Buffering -> {
                    if (sessionState.isSuspendedRetry) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "重试",
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
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

fun isMiniPlayerRetryBannerVisible(sessionState: PlayerSessionState): Boolean = sessionState.isSuspendedRetry

@Composable
fun MiniPlayerRetryBanner(
    onRetryPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onRetryPlayback,
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("MiniPlayerRetryBanner"),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = AppIcons.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = PlayerSessionState.RETRY_BANNER_MESSAGE,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(
                onClick = onRetryPlayback,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.testTag("MiniPlayerRetryButton"),
            ) {
                Text(
                    text = PlayerSessionState.RETRY_BUTTON_LABEL,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

fun formatMiniPlayerSubtitle(sessionState: PlayerSessionState): String {
    if (sessionState.isSuspendedRetry) {
        return PlayerSessionState.RETRY_BANNER_MESSAGE
    }
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
