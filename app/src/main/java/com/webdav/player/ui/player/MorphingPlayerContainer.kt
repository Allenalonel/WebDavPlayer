package com.webdav.player.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.motion.isReduceMotionEnabled
import com.webdav.player.ui.player.components.rememberArtworkBackgroundBrush

/**
 * Default dimension tokens and transition thresholds for [MorphingPlayerContainer].
 */
object MorphingPlayerDefaults {
    val WindowCornerRadius: Dp = 36.dp
    val FullPlayerArtworkSize: Dp = 280.dp
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
     * to [MorphingPlayerDefaults.FullPlayerArtworkSize] (280dp).
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
 * and animated smoothly via Material 3 Expressive easing curves.
 *
 * Fully respects zero UI mutation: when [isExpanded] is false (progress = 0f), 100% of
 * existing [MiniPlayer] layout, paddings, and actions are preserved. When [isExpanded] is true
 * (progress = 1f), 100% of existing [FullPlayerView] is preserved.
 *
 * Single living view architecture: [FullPlayerView] is the authentic full-screen view throughout
 * expansion without any fake clones or end-state swaps, eliminating layout shift and component jitter.
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

    val morphingSpec =
        remember(isReduceMotion) {
            if (isReduceMotion) {
                snap()
            } else {
                tween<Float>(
                    durationMillis = 480,
                    easing = CubicBezierEasing(0.18f, 0.94f, 0.28f, 1.05f),
                )
            }
        }

    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = morphingSpec,
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
                val density = LocalDensity.current

                // 1. Single Living FullPlayerView (Active as container expands past 0f)
                if (expansionProgress > 0.001f) {
                    val fullAlpha = ((expansionProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    val fullTranslationY = with(density) { 40.dp.toPx() * (1f - expansionProgress) }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = fullAlpha
                                    translationY = fullTranslationY
                                },
                    ) {
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
                }

                // 2. MiniPlayer (Active when collapsed, smoothly fades out as container expands)
                if (expansionProgress < 0.40f) {
                    val miniAlpha = if (expansionProgress == 0f) 1f else ((0.40f - expansionProgress) / 0.40f).coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .graphicsLayer {
                                    alpha = miniAlpha
                                },
                    ) {
                        MiniPlayer(
                            sessionState = sessionState,
                            playbackActions = playbackActions,
                            playbackProgress = playbackProgress,
                            onMiniPlayerClick = onExpand,
                            onOpenQueue = onOpenQueue,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
