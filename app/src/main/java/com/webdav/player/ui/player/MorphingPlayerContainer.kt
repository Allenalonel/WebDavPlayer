package com.webdav.player.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.common.CoverThumbnailImage
import com.webdav.player.ui.motion.MotionTokens
import com.webdav.player.ui.motion.isReduceMotionEnabled
import com.webdav.player.ui.player.components.PlayerProgressSlider
import com.webdav.player.ui.player.components.rememberArtworkBackgroundBrush
import com.webdav.player.ui.theme.AppIcons

/**
 * Default dimension tokens and transition thresholds for [MorphingPlayerContainer].
 */
object MorphingPlayerDefaults {
    val WindowCornerRadius: Dp = 36.dp
    val FullPlayerArtworkSize: Dp = 250.dp
    val FullPlayerArtworkRadius: Dp = 28.dp
    val FullPlayerPlayButtonSize: Dp = 68.dp
    val FullPlayerPlayButtonRadius: Dp = 26.dp
    val FullPlayerQueueButtonSize: Dp = 48.dp
    val CapsuleHorizontalMargin: Dp = 14.dp

    const val SecondaryControlsFadeStartProgress: Float = 0.35f
    const val MiniControlsFadeEndProgress: Float = 0.25f
}

/**
 * Pure mathematical interpolation functions for container morphing.
 *
 * Implements smooth continuous geometry transitions between the 60dp floating capsule
 * and the edge-to-edge full screen player view.
 */
object MorphingPlayerMath {
    /**
     * Interpolates container height from [MiniPlayerDefaults.CapsuleHeight] (60dp) to [screenHeight].
     */
    fun containerHeight(
        screenHeight: Dp,
        progress: Float,
    ): Dp {
        val p = progress.coerceIn(0f, 1f)
        return lerp(MiniPlayerDefaults.CapsuleHeight, screenHeight, p)
    }

    /**
     * Interpolates container horizontal margin from 14dp to 0dp edge-to-edge.
     */
    fun containerHorizontalMargin(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        return lerp(MorphingPlayerDefaults.CapsuleHorizontalMargin, 0.dp, p)
    }

    /**
     * Interpolates container bottom margin from [collapsedBottomPadding] to 0dp.
     */
    fun containerBottomMargin(
        collapsedBottomPadding: Dp,
        progress: Float,
    ): Dp {
        val p = progress.coerceIn(0f, 1f)
        return lerp(collapsedBottomPadding, 0.dp, p)
    }

    /**
     * Interpolates container corner radius from [MiniPlayerDefaults.CapsuleCornerRadius] (30dp)
     * to [MorphingPlayerDefaults.WindowCornerRadius] (36dp).
     */
    fun containerCornerRadius(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        return lerp(MiniPlayerDefaults.CapsuleCornerRadius, MorphingPlayerDefaults.WindowCornerRadius, p)
    }

    /**
     * Interpolates album artwork size from [MiniPlayerDefaults.CoverSize] (42dp)
     * to [MorphingPlayerDefaults.FullPlayerArtworkSize] (250dp).
     */
    fun artworkSize(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        return lerp(MiniPlayerDefaults.CoverSize, MorphingPlayerDefaults.FullPlayerArtworkSize, p)
    }

    /**
     * Interpolates artwork corner radius from circular (size / 2) to [MorphingPlayerDefaults.FullPlayerArtworkRadius] (28dp).
     */
    fun artworkCornerRadius(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        val currentSize = artworkSize(p)
        return lerp(currentSize / 2f, MorphingPlayerDefaults.FullPlayerArtworkRadius, p)
    }

    /**
     * Rotation of the album artwork: strictly 0° stationary at all progress values.
     * Guaranteed stationary (NO spinning or rotation).
     */
    @Suppress("UNUSED_PARAMETER")
    fun artworkRotation(progress: Float): Float = 0f

    /**
     * Interpolates play/pause button size from [MiniPlayerDefaults.PlayPauseButtonSize] (40dp)
     * to [MorphingPlayerDefaults.FullPlayerPlayButtonSize] (68dp).
     */
    fun playButtonSize(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        return lerp(MiniPlayerDefaults.PlayPauseButtonSize, MorphingPlayerDefaults.FullPlayerPlayButtonSize, p)
    }

    /**
     * Interpolates play/pause button corner radius from circular (size / 2)
     * to [MorphingPlayerDefaults.FullPlayerPlayButtonRadius] (26dp squircle).
     */
    fun playButtonCornerRadius(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        val currentSize = playButtonSize(p)
        return lerp(currentSize / 2f, MorphingPlayerDefaults.FullPlayerPlayButtonRadius, p)
    }

    /**
     * Interpolates queue button size from [MiniPlayerDefaults.QueueMusicButtonSize] (38dp)
     * to [MorphingPlayerDefaults.FullPlayerQueueButtonSize] (48dp).
     */
    fun queueButtonSize(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        return lerp(MiniPlayerDefaults.QueueMusicButtonSize, MorphingPlayerDefaults.FullPlayerQueueButtonSize, p)
    }

    /**
     * Fade-in alpha for secondary controls (slider, top bar, secondary transport buttons).
     * Returns 0.0f when [progress] <= 0.35f, ramping to 1.0f at progress = 1.0f.
     */
    fun secondaryControlsAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val start = MorphingPlayerDefaults.SecondaryControlsFadeStartProgress
        return if (p <= start) {
            0f
        } else {
            ((p - start) / (1f - start)).coerceIn(0f, 1f)
        }
    }

    /**
     * Fade-out alpha for mini player title, subtitle, and micro-bar.
     * Returns 1.0f when [progress] = 0.0f, ramping to 0.0f by progress = 0.25f.
     */
    fun miniControlsAlpha(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val end = MorphingPlayerDefaults.MiniControlsFadeEndProgress
        return if (p >= end) {
            0f
        } else {
            (1f - p / end).coerceIn(0f, 1f)
        }
    }
}

/**
 * Unified continuous morphing player container.
 *
 * Unifies [MiniPlayer] and [FullPlayerView] into a single living entity driven by [isExpanded]
 * and animated smoothly via [MotionTokens.spatialSpec].
 *
 * Fully respects zero UI mutation: when [isExpanded] is false (progress = 0f), 100% of
 * existing [MiniPlayer] layout, paddings, and actions are preserved. When [isExpanded] is true
 * (progress = 1f), 100% of existing [FullPlayerView] is preserved.
 */
@Composable
fun MorphingPlayerContainer(
    sessionState: PlayerSessionState,
    playbackProgress: PlaybackProgress,
    playbackActions: PlaybackActions,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    collapsedBottomPadding: Dp,
    modifier: Modifier = Modifier,
    onOpenQueue: () -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onDismissBatteryBanner: () -> Unit = {},
    onConfirmUnsupportedRangeReplay: () -> Unit = {},
    onDismissUnsupportedRange: () -> Unit = {},
) {
    val currentTrack = sessionState.currentTrack ?: return
    val isReduceMotion = isReduceMotionEnabled()

    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = MotionTokens.spatialSpec(isReduceMotion),
        label = "PlayerMorphingProgress",
    )

    // Downward swipe gesture to collapse player sheet smoothly
    val downwardSwipeModifier =
        Modifier.pointerInput(isExpanded) {
            if (!isExpanded) return@pointerInput
            var downwardDrag = 0f
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    downwardDrag += dragAmount
                    if (downwardDrag > 24f) {
                        change.consume()
                        onCollapse()
                        downwardDrag = 0f
                    }
                },
                onDragEnd = { downwardDrag = 0f },
                onDragCancel = { downwardDrag = 0f },
            )
        }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth

        val containerHeight = MorphingPlayerMath.containerHeight(screenHeight, expansionProgress)
        val horizontalMargin = MorphingPlayerMath.containerHorizontalMargin(expansionProgress)
        val bottomMargin = MorphingPlayerMath.containerBottomMargin(collapsedBottomPadding, expansionProgress)
        val cornerRadius = MorphingPlayerMath.containerCornerRadius(expansionProgress)

        val containerShape =
            remember(cornerRadius, expansionProgress) {
                if (expansionProgress >= 0.99f) {
                    RoundedCornerShape(
                        topStart = cornerRadius,
                        topEnd = cornerRadius,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    )
                } else {
                    RoundedCornerShape(cornerRadius)
                }
            }

        val backgroundBrush = rememberArtworkBackgroundBrush(currentTrack.coverThumbnailPath)

        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomMargin)
                    .padding(horizontal = horizontalMargin)
                    .fillMaxWidth()
                    .height(containerHeight)
                    .clip(containerShape)
                    .then(downwardSwipeModifier),
            shape = containerShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = lerp(6.dp, 0.dp, expansionProgress),
            shadowElevation = lerp(10.dp, 0.dp, expansionProgress),
            border =
                if (expansionProgress < 0.95f) {
                    BorderStroke(
                        MiniPlayerDefaults.SurfaceBorderWidth,
                        MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = MiniPlayerDefaults.SurfaceBorderAlpha * (1f - expansionProgress),
                        ),
                    )
                } else {
                    null
                },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (expansionProgress > 0.05f) {
                                backgroundBrush
                            } else {
                                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                        ),
            ) {
                when {
                    expansionProgress == 0f -> {
                        // Preserved 100% static MiniPlayer layout and interactions
                        MiniPlayer(
                            sessionState = sessionState,
                            playbackActions = playbackActions,
                            playbackProgress = playbackProgress,
                            onMiniPlayerClick = onExpand,
                            onOpenQueue = onOpenQueue,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    expansionProgress == 1f -> {
                        // Preserved 100% static FullPlayerView layout and interactions
                        FullPlayerView(
                            sessionState = sessionState,
                            playbackProgress = playbackProgress,
                            playbackActions = playbackActions,
                            onCollapse = onCollapse,
                            onDismissBatteryBanner = onDismissBatteryBanner,
                            onConfirmUnsupportedRangeReplay = onConfirmUnsupportedRangeReplay,
                            onDismissUnsupportedRange = onDismissUnsupportedRange,
                            onAddToPlaylist = onAddToPlaylist,
                        )
                    }

                    else -> {
                        // Seamless continuous morphing layer
                        MorphingTransitionContent(
                            sessionState = sessionState,
                            currentTrack = currentTrack,
                            playbackProgress = playbackProgress,
                            playbackActions = playbackActions,
                            expansionProgress = expansionProgress,
                            containerWidth = screenWidth - horizontalMargin * 2,
                            containerHeight = containerHeight,
                            onCollapse = onCollapse,
                            onOpenQueue = onOpenQueue,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Continuous geometry interpolation content rendered exclusively during transition phases
 * (0f < [expansionProgress] < 1f).
 */
@Composable
private fun MorphingTransitionContent(
    sessionState: PlayerSessionState,
    currentTrack: AudioTrack,
    playbackProgress: PlaybackProgress,
    playbackActions: PlaybackActions,
    expansionProgress: Float,
    containerWidth: Dp,
    containerHeight: Dp,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val miniAlpha = MorphingPlayerMath.miniControlsAlpha(expansionProgress)
    val secondaryAlpha = MorphingPlayerMath.secondaryControlsAlpha(expansionProgress)

    // Artwork geometry interpolation
    val artworkSize = MorphingPlayerMath.artworkSize(expansionProgress)
    val artworkRadius = MorphingPlayerMath.artworkCornerRadius(expansionProgress)
    val artworkRotation = MorphingPlayerMath.artworkRotation(expansionProgress)

    val miniArtX = 10.dp
    val miniArtY = (MiniPlayerDefaults.CapsuleHeight - MiniPlayerDefaults.CoverSize) / 2
    val fullArtX = (containerWidth - artworkSize) / 2
    val fullArtY = ((containerHeight * 0.45f) - (artworkSize / 2)).coerceAtLeast(70.dp)

    val artX = lerp(miniArtX, fullArtX, expansionProgress)
    val artY = lerp(miniArtY, fullArtY, expansionProgress)

    // Dual buttons geometry interpolation
    val playButtonSize = MorphingPlayerMath.playButtonSize(expansionProgress)
    val playButtonRadius = MorphingPlayerMath.playButtonCornerRadius(expansionProgress)

    val miniPlayX = containerWidth - 12.dp - 38.dp - 8.dp - 40.dp
    val miniPlayY = (MiniPlayerDefaults.CapsuleHeight - 40.dp) / 2
    val fullControlsY = (containerHeight - 56.dp - playButtonSize).coerceAtLeast(MiniPlayerDefaults.CapsuleHeight)
    val fullPlayX = (containerWidth - playButtonSize) / 2

    val playX = lerp(miniPlayX, fullPlayX, expansionProgress)
    val playY = lerp(miniPlayY, fullControlsY, expansionProgress)

    val queueButtonSize = MorphingPlayerMath.queueButtonSize(expansionProgress)
    val miniQueueX = containerWidth - 12.dp - 38.dp
    val miniQueueY = (MiniPlayerDefaults.CapsuleHeight - 38.dp) / 2
    val fullQueueX = containerWidth - 28.dp - queueButtonSize
    val fullQueueY = fullControlsY + (playButtonSize - queueButtonSize) / 2

    val queueX = lerp(miniQueueX, fullQueueX, expansionProgress)
    val queueY = lerp(miniQueueY, fullQueueY, expansionProgress)

    Box(modifier = Modifier.fillMaxSize()) {
        // --- MiniPlayer Title & State (Fades out by progress = 0.25f) ---
        if (miniAlpha > 0f) {
            Column(
                modifier =
                    Modifier
                        .offset(x = 64.dp, y = 10.dp)
                        .width((containerWidth - 64.dp - 108.dp).coerceAtLeast(0.dp))
                        .height(40.dp)
                        .graphicsLayer { alpha = miniAlpha },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatMiniPlayerSubtitle(sessionState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // --- Secondary Controls: Top Bar (Fades in past progress = 0.35f) ---
        if (secondaryAlpha > 0f) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp)
                        .graphicsLayer { alpha = secondaryAlpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = AppIcons.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val serverName = sessionState.activeServer?.name?.takeIf { it.isNotBlank() } ?: "WebDAV"
                    Text(
                        text = "正在播放自 $serverName",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.size(44.dp))
            }

            // --- Secondary Controls: Progress Slider & Controls (Fades in past progress = 0.35f) ---
            PlayerProgressSlider(
                playbackProgress = playbackProgress,
                sessionDurationMs = playbackProgress.durationMs,
                displayPositionMs = playbackProgress.currentPositionMs,
                isScrubbing = false,
                onScrubbingChange = { _, _ -> },
                onSeek = { playbackActions.seekTo(it) },
                modifier =
                    Modifier
                        .offset(y = (fullControlsY - 60.dp).coerceAtLeast(0.dp))
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = secondaryAlpha },
            )

            // Secondary Skip and Mode buttons
            val skipButtonSize = 48.dp
            val skipY = fullControlsY + (playButtonSize - skipButtonSize) / 2

            IconButton(
                onClick = { playbackActions.cyclePlaybackMode() },
                modifier =
                    Modifier
                        .offset(x = 24.dp, y = skipY)
                        .size(skipButtonSize)
                        .graphicsLayer { alpha = secondaryAlpha },
            ) {
                Icon(
                    imageVector =
                        when (sessionState.playbackMode) {
                            PlaybackMode.LIST_LOOP -> AppIcons.Repeat
                            PlaybackMode.SINGLE_LOOP -> AppIcons.RepeatOne
                            PlaybackMode.SHUFFLE -> AppIcons.Shuffle
                        },
                    contentDescription = "播放模式",
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalIconButton(
                onClick = { playbackActions.skipToPrevious() },
                modifier =
                    Modifier
                        .offset(x = (containerWidth / 2) - 34.dp - 14.dp - skipButtonSize, y = skipY)
                        .size(skipButtonSize)
                        .graphicsLayer { alpha = secondaryAlpha },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = AppIcons.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(24.dp),
                )
            }

            FilledTonalIconButton(
                onClick = { playbackActions.skipToNext() },
                modifier =
                    Modifier
                        .offset(x = (containerWidth / 2) + 34.dp + 14.dp, y = skipY)
                        .size(skipButtonSize)
                        .graphicsLayer { alpha = secondaryAlpha },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = AppIcons.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // --- Continuous Shared Album Artwork (Stationary at 0°) ---
        Box(
            modifier =
                Modifier
                    .offset(x = artX, y = artY)
                    .size(artworkSize)
                    .graphicsLayer {
                        rotationZ = artworkRotation
                        clip = true
                        shape = RoundedCornerShape(artworkRadius)
                    }.clip(RoundedCornerShape(artworkRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            CoverThumbnailImage(
                thumbnailPath = currentTrack.coverThumbnailPath,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = AppIcons.Audiotrack,
                    contentDescription = "音轨",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(lerp(22.dp, 64.dp, expansionProgress)),
                )
            }
        }

        // --- Continuous Primary Play / Pause Button ---
        FilledIconButton(
            onClick = { playbackActions.togglePlayPause() },
            modifier =
                Modifier
                    .offset(x = playX, y = playY)
                    .size(playButtonSize)
                    .graphicsLayer {
                        shape = RoundedCornerShape(playButtonRadius)
                        clip = true
                    },
            shape = RoundedCornerShape(playButtonRadius),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            val iconSize = lerp(22.dp, 36.dp, expansionProgress)
            if (sessionState.isBuffering) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(iconSize * 0.75f),
                )
            } else if (sessionState.isPlaying) {
                Icon(
                    imageVector = AppIcons.Pause,
                    contentDescription = "暂停",
                    modifier = Modifier.size(iconSize),
                )
            } else {
                Icon(
                    imageVector = AppIcons.PlayArrow,
                    contentDescription = "播放",
                    modifier = Modifier.size(iconSize),
                )
            }
        }

        // --- Continuous Secondary Queue Button ---
        FilledTonalIconButton(
            onClick = onOpenQueue,
            modifier =
                Modifier
                    .offset(x = queueX, y = queueY)
                    .size(queueButtonSize),
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            val iconSize = lerp(20.dp, 26.dp, expansionProgress)
            Icon(
                imageVector = AppIcons.QueueMusic,
                contentDescription = "播放队列",
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
