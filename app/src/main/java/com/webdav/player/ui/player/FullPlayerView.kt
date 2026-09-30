package com.webdav.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.common.CoverThumbnailImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerView(
    sessionState: PlayerSessionState,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 2-page Horizontal Pager: Page 0 = Artwork & Info, Page 1 = Synchronized Lyrics
    val pagerState =
        rememberPagerState(
            initialPage = 0,
            pageCount = { 2 },
        )

    // Intercept back button to close sheet or collapse to mini-player
    BackHandler {
        if (showQueueSheet) {
            showQueueSheet = false
        } else {
            onCollapse()
        }
    }

    val currentTrack = sessionState.currentTrack

    // Dynamic atmospheric gradient sampled from cover art with MD3 fallback
    val artworkColors by rememberArtworkColors(
        thumbnailPath = currentTrack?.coverThumbnailPath,
        defaultSurfaceColor = MaterialTheme.colorScheme.surfaceContainer,
        defaultBackgroundColor = MaterialTheme.colorScheme.surface,
    )

    val animatedTopColor by animateColorAsState(
        targetValue = artworkColors.backgroundTopColor,
        animationSpec = tween(durationMillis = 400),
        label = "FullPlayerBackgroundTop",
    )
    val animatedBottomColor by animateColorAsState(
        targetValue = artworkColors.backgroundBottomColor,
        animationSpec = tween(durationMillis = 400),
        label = "FullPlayerBackgroundBottom",
    )
    val backgroundBrush =
        remember(animatedTopColor, animatedBottomColor) {
            Brush.verticalGradient(
                colors = listOf(animatedTopColor, animatedBottomColor),
            )
        }

    // Interactive scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbedPositionMs by remember { mutableLongStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(false) }

    val displayPositionMs =
        if (isScrubbing) {
            scrubbedPositionMs
        } else {
            playbackProgress.currentPositionMs
        }

    // Downward swipe modifier to collapse player sheet smoothly
    val downwardSwipeModifier =
        Modifier.pointerInput(Unit) {
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

    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundBrush),
        color = Color.Transparent,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Minimalist Centered Drag Handle Zone: width 36dp, height 4dp, rounded corners 2dp
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapse,
                        )
                        .then(downwardSwipeModifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }

            // Upper Main Area: Horizontal Pager between Artwork and Lyrics
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        // Page 0: Cover Artwork & Metadata view
                        ArtworkPage(
                            currentTrack = currentTrack,
                            onArtworkClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            modifier = downwardSwipeModifier,
                        )
                    }

                    1 -> {
                        // Page 1: Synchronized Lyrics view with Tap-to-Seek
                        LyricsPage(
                            sessionState = sessionState,
                            playbackProgress = playbackProgress,
                            currentPositionMs = displayPositionMs,
                            onSeekTo = onSeek,
                            onReturnToArtwork = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                        )
                    }
                }
            }

            // Page Indicator Dots (Pill indicators)
            Row(
                modifier =
                    Modifier
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(2) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier =
                            Modifier
                                .size(
                                    width = if (isSelected) 18.dp else 6.dp,
                                    height = 6.dp,
                                ).clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    },
                                ).clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dual-Layer Buffered Progress Slider & Real-time Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                val maxDuration =
                    if (playbackProgress.durationMs > 0L) {
                        playbackProgress.durationMs
                    } else {
                        sessionState.durationMs.coerceAtLeast(0L)
                    }
                val sliderValue =
                    if (maxDuration > 0L) {
                        displayPositionMs.coerceIn(0L, maxDuration).toFloat()
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
                            isScrubbing = true
                            scrubbedPositionMs = newPos.toLong()
                        },
                        onValueChangeFinished = {
                            onSeek(scrubbedPositionMs)
                            isScrubbing = false
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
                            if (isScrubbing) {
                                PlaybackProgress.formatMs(displayPositionMs)
                            } else {
                                playbackProgress.formattedCurrentPosition
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            if (showRemainingTime && maxDuration > displayPositionMs) {
                                "-${PlaybackProgress.formatMs(maxDuration - displayPositionMs)}"
                            } else if (!isScrubbing) {
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

            Spacer(modifier = Modifier.height(10.dp))

            // Expressive Controls Bar: Mode, Prev, Play/Pause/Buffering, Next, Queue
            Row(
                modifier =
                    Modifier
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
                        imageVector = Icons.Filled.SkipPrevious,
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
                    onClick = onTogglePlayPause,
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }

                            is PlaybackState.Playing -> {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "暂停",
                                    modifier = Modifier.size(38.dp),
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
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
                        imageVector = Icons.Filled.SkipNext,
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
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Playback Queue Bottom Sheet
    if (showQueueSheet) {
        PlaybackQueueBottomSheet(
            queue = sessionState.queue,
            playbackMode = sessionState.playbackMode,
            playbackProgress = playbackProgress,
            onTrackClick = { index ->
                onPlayQueueIndex(index)
            },
            onRemoveTrack = { index ->
                onRemoveQueueTrack(index)
            },
            onCyclePlaybackMode = onCyclePlaybackMode,
            onDismissRequest = { showQueueSheet = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtworkPage(
    currentTrack: AudioTrack?,
    onArtworkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large Album Artwork Card with Soft Shadow
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f)
                    .shadow(elevation = 20.dp, shape = MaterialTheme.shapes.extraLarge)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onArtworkClick() },
            contentAlignment = Alignment.Center,
        ) {
            CoverThumbnailImage(
                thumbnailPath = currentTrack?.coverThumbnailPath,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Audiotrack,
                    contentDescription = "专辑封面",
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track Title with basicMarquee for long titles
        Text(
            text = currentTrack?.title ?: "无正在播放曲目",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Artist Name
        Text(
            text = currentTrack?.artist?.trim()?.takeIf { it.isNotBlank() } ?: "未知艺术家",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Audiophile Specification Capsule
        val specs = currentTrack?.audiophileSpecsModel
        if (specs != null && specs.formatted.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricsPage(
    sessionState: PlayerSessionState,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
    currentPositionMs: Long = 0L,
    onSeekTo: (Long) -> Unit,
    onReturnToArtwork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrack = sessionState.currentTrack

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Song Header in lyrics view
        Text(
            text = currentTrack?.title ?: "无正在播放曲目",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = currentTrack?.artist?.ifBlank { "未知艺术家" } ?: "未知艺术家",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Synchronized, smooth-scrolling Lyrics View with Tap-to-Seek
        LyricsView(
            lyrics = sessionState.lyrics,
            isLoading = sessionState.isLoadingLyrics,
            playbackProgress = playbackProgress,
            currentPositionMs = currentPositionMs,
            onSeekTo = onSeekTo,
            onToggleCover = onReturnToArtwork,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaybackModeButton(
    mode: PlaybackMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (mode) {
            PlaybackMode.LIST_LOOP -> Icons.Filled.Repeat
            PlaybackMode.SINGLE_LOOP -> Icons.Filled.RepeatOne
            PlaybackMode.SHUFFLE -> Icons.Filled.Shuffle
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

fun formatTrackArtistAndFormat(track: AudioTrack?): String {
    if (track == null) return "未知艺术家"
    val artist = track.artist?.trim()?.takeIf { it.isNotBlank() } ?: "未知艺术家"
    val format = track.format.extension.uppercase()
    return if (format.isNotBlank()) "$artist · $format" else artist
}
