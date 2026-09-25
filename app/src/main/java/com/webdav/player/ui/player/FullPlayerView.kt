package com.webdav.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.common.CoverThumbnailImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerView(
    sessionState: PlayerSessionState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showQueueSheet by remember { mutableStateOf(false) }

    // Intercept back button to close full player
    BackHandler {
        if (showQueueSheet) {
            showQueueSheet = false
        } else {
            onCollapse()
        }
    }

    val currentTrack = sessionState.currentTrack

    // Interactive scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbedPositionMs by remember { mutableLongStateOf(0L) }

    val displayPositionMs = if (isScrubbing) {
        scrubbedPositionMs
    } else {
        sessionState.currentPositionMs
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "正在播放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (currentTrack?.album != null && currentTrack.album.isNotBlank()) {
                            Text(
                                text = currentTrack.album,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "收起播放器",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "查看播放队列"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Album Artwork Area
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                CoverThumbnailImage(
                    thumbnailPath = currentTrack?.coverThumbnailPath,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Audiotrack,
                        contentDescription = "专辑封面",
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track Title and Artist
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTrack?.title ?: "无正在播放曲目",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = currentTrack?.artist?.ifBlank { "未知艺术家" } ?: "未知艺术家",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrubbing Seek Slider & Time Indicators
            Column(modifier = Modifier.fillMaxWidth()) {
                val maxDuration = sessionState.durationMs.coerceAtLeast(0L)
                val sliderValue = if (maxDuration > 0L) {
                    displayPositionMs.coerceIn(0L, maxDuration).toFloat()
                } else {
                    0f
                }

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
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = PlayerTimeFormatter.formatMs(displayPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = PlayerTimeFormatter.formatMs(maxDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback Controls Row (Mode, Prev, Play/Pause, Next, Queue)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Playback Mode Button
                PlaybackModeButton(
                    mode = sessionState.playbackMode,
                    onClick = onCyclePlaybackMode
                )

                // Previous Track Button
                IconButton(
                    onClick = onSkipToPrevious,
                    enabled = sessionState.queue.isNotEmpty,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(36.dp),
                        tint = if (sessionState.queue.isNotEmpty) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                }

                // Play / Pause / Buffering Toggle Button
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    AnimatedContent(
                        targetState = sessionState.playbackState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "FullPlayerPlayPause"
                    ) { state ->
                        when (state) {
                            is PlaybackState.Buffering -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            is PlaybackState.Playing -> {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "暂停",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "播放",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }

                // Next Track Button
                IconButton(
                    onClick = onSkipToNext,
                    enabled = sessionState.queue.isNotEmpty,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(36.dp),
                        tint = if (sessionState.queue.isNotEmpty) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                }

                // Queue Button
                IconButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            onTrackClick = { index ->
                onPlayQueueIndex(index)
            },
            onRemoveTrack = { index ->
                onRemoveQueueTrack(index)
            },
            onCyclePlaybackMode = onCyclePlaybackMode,
            onDismissRequest = { showQueueSheet = false }
        )
    }
}

@Composable
private fun PlaybackModeButton(
    mode: PlaybackMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (mode) {
        PlaybackMode.LIST_LOOP -> Icons.Filled.Repeat
        PlaybackMode.SINGLE_LOOP -> Icons.Filled.RepeatOne
        PlaybackMode.SHUFFLE -> Icons.Filled.Shuffle
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mode.label,
            modifier = Modifier.size(26.dp),
            tint = when (mode) {
                PlaybackMode.LIST_LOOP -> MaterialTheme.colorScheme.onSurfaceVariant
                PlaybackMode.SINGLE_LOOP, PlaybackMode.SHUFFLE -> MaterialTheme.colorScheme.primary
            }
        )
    }
}
