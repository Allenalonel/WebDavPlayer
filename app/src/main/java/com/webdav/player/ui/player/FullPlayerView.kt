package com.webdav.player.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.player.components.FullPlayerRetryBanner
import com.webdav.player.ui.player.components.PlayerTransportControls
import com.webdav.player.ui.player.components.PlayerVinylArtwork
import com.webdav.player.ui.player.components.isFullPlayerRetryBannerVisible
import com.webdav.player.ui.player.components.rememberArtworkBackgroundBrush
import kotlinx.coroutines.launch

/**
 * Expanded full player view displaying artwork, playback controls, and lyrics.
 */
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
    onRetryPlayback: () -> Unit = onTogglePlayPause,
    batteryWhitelistManager: BatteryWhitelistManager = remember { DefaultBatteryWhitelistManager() },
    onDismissBatteryBanner: () -> Unit = {},
    onConfirmUnsupportedRangeReplay: () -> Unit = {},
    onDismissUnsupportedRange: () -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
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
    val backgroundBrush = rememberArtworkBackgroundBrush(currentTrack?.coverThumbnailPath)

    // Interactive scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbedPositionMs by remember { mutableLongStateOf(0L) }

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
                        ).then(downwardSwipeModifier),
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

            BatteryWhitelistBanner(
                wasKilledBySystem = sessionState.wasKilledBySystem,
                whitelistManager = batteryWhitelistManager,
                onDismiss = onDismissBatteryBanner,
            )

            if (isFullPlayerRetryBannerVisible(sessionState)) {
                FullPlayerRetryBanner(
                    onRetryPlayback = onRetryPlayback,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                        PlayerVinylArtwork(
                            currentTrack = currentTrack,
                            sessionState = sessionState,
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

            // Transport Controls: Progress Slider, Elapsed/Remaining Time, and Expressive Control Buttons
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
                onOpenQueue = { showQueueSheet = true },
            )
        }
    }

    // Playback Queue Bottom Sheet
    if (showQueueSheet) {
        PlaybackQueueBottomSheet(
            queue = sessionState.queue,
            playbackMode = sessionState.playbackMode,
            playbackProgress = playbackProgress,
            isPlaying = sessionState.isPlaying,
            onTrackClick = { index ->
                onPlayQueueIndex(index)
            },
            onRemoveTrack = { index ->
                onRemoveQueueTrack(index)
            },
            onCyclePlaybackMode = onCyclePlaybackMode,
            onDismissRequest = { showQueueSheet = false },
            onAddToPlaylist = onAddToPlaylist,
        )
    }

    if (sessionState.isUnsupportedRange) {
        AlertDialog(
            onDismissRequest = onDismissUnsupportedRange,
            text = {
                Text(text = "当前服务器不支持断点续传，是否从头重新播放此曲？")
            },
            confirmButton = {
                TextButton(onClick = onConfirmUnsupportedRangeReplay) {
                    Text("重新播放")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUnsupportedRange) {
                    Text("取消")
                }
            },
        )
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

fun formatTrackArtistAndFormat(track: AudioTrack?): String {
    if (track == null) return "未知艺术家"
    val artist = track.artist?.trim()?.takeIf { it.isNotBlank() } ?: "未知艺术家"
    val format = track.format.extension.uppercase()
    return if (format.isNotBlank()) "$artist · $format" else artist
}

/**
 * Forwarding alias for [isFullPlayerRetryBannerVisible] preserving existing API compatibility.
 */
internal fun isFullPlayerRetryBannerVisible(sessionState: PlayerSessionState): Boolean =
    com.webdav.player.ui.player.components
        .isFullPlayerRetryBannerVisible(sessionState)

/**
 * Forwarding alias for [FullPlayerRetryBanner] preserving existing API compatibility.
 */
@Composable
internal fun FullPlayerRetryBanner(
    onRetryPlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.webdav.player.ui.player.components.FullPlayerRetryBanner(
        onRetryPlayback = onRetryPlayback,
        modifier = modifier,
    )
}
