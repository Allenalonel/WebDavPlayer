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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.player.components.FullPlayerRetryBanner
import com.webdav.player.ui.player.components.PlayerTransportControls
import com.webdav.player.ui.player.components.PlayerVinylArtwork
import com.webdav.player.ui.player.components.isFullPlayerRetryBannerVisible
import com.webdav.player.ui.player.components.rememberArtworkBackgroundBrush
import com.webdav.player.ui.theme.AppIcons
import kotlinx.coroutines.launch

object FullPlayerDefaults {
    val TabletWideBreakpoint: Dp = 660.dp
    val ArtworkSquircleRadius: Dp = 28.dp
    val ArtworkSquircleShape = RoundedCornerShape(ArtworkSquircleRadius)

    val PlayButtonSize: Dp = 68.dp
    val PlayButtonSquircleRadius: Dp = 26.dp
    val PlayButtonSquircleShape = RoundedCornerShape(PlayButtonSquircleRadius)

    val SkipButtonSize: Dp = 48.dp
    val SkipButtonShape = RoundedCornerShape(16.dp)

    fun isDualPaneLayout(widthDp: Dp): Boolean = widthDp >= TabletWideBreakpoint
}

/**
 * Expanded full player view displaying artwork, playback controls, and lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerView(
    sessionState: PlayerSessionState,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
    playbackActions: PlaybackActions = PlaybackActions.EMPTY,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
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
            // Top lightweight bar: collapse handle / chevron, "正在播放自 WebDAV", album/source, and options
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
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
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCollapse,
                            ).then(downwardSwipeModifier),
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
                    val trackTitle = currentTrack?.title ?: "WebDAV 曲库"
                    val artist = currentTrack?.artist?.takeIf { it.isNotBlank() }
                    val displaySubtitle = if (artist != null) "$trackTitle · $artist" else trackTitle
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = { currentTrack?.let(onAddToPlaylist) },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "添加到歌单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            BatteryWhitelistBanner(
                wasKilledBySystem = sessionState.wasKilledBySystem,
                whitelistManager = batteryWhitelistManager,
                onDismiss = onDismissBatteryBanner,
            )

            if (isFullPlayerRetryBannerVisible(sessionState)) {
                FullPlayerRetryBanner(
                    onRetryPlayback = { playbackActions.retryPlayback() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Upper Main Area: Responsive Dual-Pane (660dp+) or Horizontal Pager
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                val isWideScreen = FullPlayerDefaults.isDualPaneLayout(maxWidth)
                val transportControls = @Composable { modifier: Modifier ->
                    PlayerTransportControls(
                        sessionState = sessionState,
                        playbackProgress = playbackProgress,
                        isScrubbing = isScrubbing,
                        displayPositionMs = displayPositionMs,
                        onScrubbingChange = { scrubbing, pos ->
                            isScrubbing = scrubbing
                            scrubbedPositionMs = pos
                        },
                        onSeek = { playbackActions.seekTo(it) },
                        onTogglePlayPause = { playbackActions.togglePlayPause() },
                        onRetryPlayback = { playbackActions.retryPlayback() },
                        onSkipToNext = { playbackActions.skipToNext() },
                        onSkipToPrevious = { playbackActions.skipToPrevious() },
                        onCyclePlaybackMode = { playbackActions.cyclePlaybackMode() },
                        onOpenQueue = { showQueueSheet = true },
                        modifier = modifier,
                    )
                }

                if (isWideScreen) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Pane: Square Artwork & Transport Controls
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                PlayerVinylArtwork(
                                    currentTrack = currentTrack,
                                    sessionState = sessionState,
                                    onArtworkClick = {},
                                    modifier = downwardSwipeModifier,
                                )
                            }

                            transportControls(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Right Pane: Immersive Synchronized Lyrics
                        Box(
                            modifier =
                                Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LyricsPage(
                                sessionState = sessionState,
                                playbackProgress = playbackProgress,
                                currentPositionMs = displayPositionMs,
                                onSeekTo = { playbackActions.seekTo(it) },
                                onReturnToArtwork = {},
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
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
                                        onSeekTo = { playbackActions.seekTo(it) },
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
                            modifier = Modifier.padding(vertical = 8.dp),
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
                        transportControls(Modifier)
                    }
                }
            }
        }
    }

    // Playback Queue Bottom Sheet
    if (showQueueSheet) {
        HoistedPlaybackQueueSheet(
            sessionState = sessionState,
            playbackProgress = playbackProgress,
            playbackActions = playbackActions,
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
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // M3 Expressive Synchronized, smooth-scrolling Lyrics View with Tap-to-Seek
        LyricsView(
            lyrics = sessionState.lyrics,
            isLoading = sessionState.isLoadingLyrics,
            playbackProgress = playbackProgress,
            currentPositionMs = currentPositionMs,
            onSeekTo = onSeekTo,
            onToggleCover = onReturnToArtwork,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

fun formatTrackArtistAndFormat(track: AudioTrack?): String {
    if (track == null) return "未知艺术家"
    val artist = track.artist?.trim()?.takeIf { it.isNotBlank() } ?: "未知艺术家"
    val format = track.format.extension.uppercase()
    return if (format.isNotBlank()) "$artist · $format" else artist
}
