package com.webdav.player.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
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
                            val icon =
                                when (playbackMode) {
                                    PlaybackMode.LIST_LOOP -> AppIcons.Repeat
                                    PlaybackMode.SINGLE_LOOP -> AppIcons.RepeatOne
                                    PlaybackMode.SHUFFLE -> AppIcons.Shuffle
                                }
                            Icon(
                                imageVector = icon,
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

                // Auto-scroll to active track on open
                LaunchedEffect(queue.currentIndex) {
                    if (queue.currentIndex in queue.tracks.indices) {
                        listState.animateScrollToItem(queue.currentIndex.coerceAtLeast(0))
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
                        key = { index, track -> "track_${track.id}_$index" },
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
                        )
                    }
                }
            }
        }
    }
}
