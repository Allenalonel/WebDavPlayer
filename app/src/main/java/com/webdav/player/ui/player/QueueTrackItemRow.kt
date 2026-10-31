package com.webdav.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.TrackThumbnailLeading
import com.webdav.player.ui.theme.AppIcons

fun resolveQueueTrackSubtitle(
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
fun handleQueueDismiss(
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
fun QueueLeadingContent(
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
fun QueueTrackItemRow(
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
