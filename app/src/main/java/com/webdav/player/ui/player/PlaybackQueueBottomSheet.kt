package com.webdav.player.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.TrackThumbnailLeading
import com.webdav.player.ui.motion.MotionTokens
import com.webdav.player.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoistedPlaybackQueueSheet(
    sessionState: PlayerSessionState,
    playbackProgress: PlaybackProgress,
    playbackActions: PlaybackActions,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (AudioTrack) -> Unit = {},
) {
    PlaybackQueueBottomSheet(
        queue = sessionState.queue,
        playbackMode = sessionState.playbackMode,
        playbackProgress = playbackProgress,
        isPlaying = sessionState.isPlaying,
        onTrackClick = { index -> playbackActions.playQueueIndex(index) },
        onRemoveTrack = { index -> playbackActions.removeQueueTrack(index) },
        onCyclePlaybackMode = { playbackActions.cyclePlaybackMode() },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onAddToPlaylist = onAddToPlaylist,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaybackQueueBottomSheet(
    queue: PlaybackQueue,
    playbackMode: PlaybackMode,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
    isPlaying: Boolean = false,
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "播放队列",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${queue.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Playback Mode Chip
                    AssistChip(
                        onClick = onCyclePlaybackMode,
                        label = {
                            Text(
                                text = playbackMode.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = resolvePlaybackModeIcon(playbackMode),
                                contentDescription = playbackMode.label,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors =
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDismissRequest) {
                        Icon(imageVector = AppIcons.Close, contentDescription = "关闭队列")
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Queue List
            if (queue.isEmpty) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "播放队列为空",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val listState = rememberLazyListState()

                val hasScrolledOnOpen = remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!hasScrolledOnOpen.value && queue.currentIndex in queue.tracks.indices) {
                        hasScrolledOnOpen.value = true
                        listState.scrollToItem(queue.currentIndex.coerceAtLeast(0))
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 450.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(
                        items = queue.tracks,
                        key = { _, track -> track.id },
                    ) { index, track ->
                        val isActive = index == queue.currentIndex

                        QueueTrackItemRow(
                            track = track,
                            index = index,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            playbackProgress = if (isActive) playbackProgress else PlaybackProgress.ZERO,
                            onClick = { onTrackClick(index) },
                            onRemove = { onRemoveTrack(index) },
                            onAddToPlaylist = onAddToPlaylist,
                            modifier = Modifier.animateItemPlacement(MotionTokens.spatialSpring()),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stable key contract for playback queue items. Uses track ID independent of list index
 * so Compose can track items across reordering, shuffle transitions, and dismiss animations.
 */
internal fun resolveQueueItemKey(track: AudioTrack): String = track.id

/**
 * Resolves the appropriate icon for the given playback mode.
 */
internal fun resolvePlaybackModeIcon(playbackMode: PlaybackMode) =
    when (playbackMode) {
        PlaybackMode.LIST_LOOP -> AppIcons.Repeat
        PlaybackMode.SINGLE_LOOP -> AppIcons.RepeatOne
        PlaybackMode.SHUFFLE -> AppIcons.Shuffle
    }

/**
 * Cycles to the next playback mode in the standard sequence:
 * LIST_LOOP -> SINGLE_LOOP -> SHUFFLE -> LIST_LOOP.
 */
internal fun cyclePlaybackMode(current: PlaybackMode): PlaybackMode = current.next()

internal fun resolveQueueTrackSubtitle(
    track: AudioTrack,
    isActive: Boolean,
    playbackProgress: PlaybackProgress,
): String {
    val artistText = track.artist?.ifBlank { "未知艺术家" } ?: "未知艺术家"
    return if (isActive && playbackProgress.durationMs > 0L) {
        "$artistText · ${playbackProgress.formattedCurrentPosition} / ${playbackProgress.formattedDuration}"
    } else {
        artistText
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun handleQueueDismiss(
    value: SwipeToDismissBoxValue,
    onRemove: () -> Unit,
): Boolean =
    if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
        onRemove()
        true
    } else {
        false
    }

@Composable
internal fun QueueLeadingContent(
    index: Int,
    coverThumbnailPath: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 20.dp),
            textAlign = TextAlign.Center,
        )
        TrackThumbnailLeading(
            coverThumbnailPath = coverThumbnailPath,
            isActive = isActive,
            isPlaying = isPlaying,
            size = 36.dp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueTrackItemRow(
    track: AudioTrack,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    modifier: Modifier = Modifier,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { handleQueueDismiss(it, onRemove) },
        )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = AppIcons.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            AudioTrackRow(
                title = track.title,
                subtitle = resolveQueueTrackSubtitle(track, isActive, playbackProgress),
                audioQualityBadge = track.qualityBadge,
                isActive = isActive,
                isPlaying = isPlaying,
                onClick = onClick,
                leadingContent = {
                    QueueLeadingContent(
                        index = index,
                        coverThumbnailPath = track.coverThumbnailPath,
                        isActive = isActive,
                        isPlaying = isPlaying,
                    )
                },
                actionsMenu = { dismiss ->
                    DropdownMenuItem(
                        text = { Text("添加到歌单") },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            dismiss()
                            onAddToPlaylist(track)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("从队列移除") },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            dismiss()
                            onRemove()
                        },
                    )
                },
            )
        }
    }
}
