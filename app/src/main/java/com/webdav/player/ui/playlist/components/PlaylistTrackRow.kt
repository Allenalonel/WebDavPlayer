package com.webdav.player.ui.playlist.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.ui.common.CoverThumbnailImage
import com.webdav.player.ui.playlist.DragDropState
import com.webdav.player.ui.theme.AppIcons
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistTrackRow(
    track: PlaylistTrackEntry,
    index: Int,
    dragDropState: DragDropState,
    showDragHandle: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onShowDetails: (PlaylistTrackEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val isDragging = dragDropState.draggedIndex == index
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 0.dp,
        label = "drag_elevation",
    )

    val durationText =
        remember(track.durationMs) {
            PlaybackProgress.formatMs(track.durationMs)
        }

    val subtitle =
        remember(track.artist, track.album) {
            val parts = mutableListOf<String>()
            if (!track.artist.isNullOrBlank()) parts.add(track.artist)
            if (!track.album.isNullOrBlank()) parts.add(track.album)
            parts.joinToString(" · ")
        }

    val itemWidthPx = remember { mutableFloatStateOf(0f) }
    val isPastThreshold = remember { mutableStateOf(false) }

    // 细化滑动操作：右滑删除，左滑下一首播放（严格以50%最大宽度为触发阈值，滑回则取消）
    val dismissState =
        key(track.id) {
            rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (!isPastThreshold.value) {
                        false
                    } else {
                        when (value) {
                            SwipeToDismissBoxValue.StartToEnd -> {
                                // 右滑：从歌单中删除
                                onRemove()
                                true
                            }

                            SwipeToDismissBoxValue.EndToStart -> {
                                // 左滑：添加到下一首播放，弹性回位
                                onPlayNext()
                                false
                            }

                            else -> {
                                false
                            }
                        }
                    }
                },
                positionalThreshold = { totalDistance -> totalDistance * 0.5f },
            )
        }

    // 监听实时位移：严格到达 50% 宽度触发震动，滑回小于 50% 则重置
    LaunchedEffect(dismissState) {
        snapshotFlow {
            val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
            val width = itemWidthPx.floatValue
            if (width > 0f) {
                kotlin.math.abs(offset) >= (width * 0.5f)
            } else {
                false
            }
        }.distinctUntilChanged().collect { past ->
            isPastThreshold.value = past
            if (past) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val currentIdx by rememberUpdatedState(index)

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !showDragHandle,
        enableDismissFromEndToStart = !showDragHandle,
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { itemWidthPx.floatValue = it.width.toFloat() }
                .zIndex(if (isDragging) 2f else 1f)
                .graphicsLayer {
                    translationY = if (isDragging) dragDropState.dragOffset else 0f
                    shadowElevation = elevation.toPx()
                },
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isDelete = direction == SwipeToDismissBoxValue.StartToEnd
            val isPlayNext = direction == SwipeToDismissBoxValue.EndToStart

            val alpha = if (isPastThreshold.value) 1f else 0.45f
            val bgColor =
                when {
                    isDelete -> MaterialTheme.colorScheme.errorContainer.copy(alpha = alpha)
                    isPlayNext -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
                    else -> Color.Transparent
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(bgColor)
                        .padding(horizontal = 20.dp),
                contentAlignment = if (isDelete) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (isDelete) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.DeleteOutline,
                            contentDescription = "从歌单删除",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "从歌单中删除",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                } else if (isPlayNext) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "下一首播放",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Icon(
                            imageVector = AppIcons.QueueMusic,
                            contentDescription = "下一首播放",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color =
                if (isDragging) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surface
                },
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .alpha(if (track.isInactive) 0.38f else 1f),
                    leadingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (showDragHandle) {
                                // 拖拽手把（仅在自定义排序模式下显示）
                                Box(
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .pointerInput(dragDropState) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        dragDropState.onDragStart(currentIdx)
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragDropState.onDrag(dragAmount)
                                                    },
                                                    onDragEnd = { dragDropState.onDragStopped() },
                                                    onDragCancel = { dragDropState.onDragStopped() },
                                                )
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = AppIcons.DragHandle,
                                        contentDescription = "拖拽排序",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            // 序号
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(20.dp),
                                textAlign = TextAlign.Center,
                            )

                            // 44×44dp 歌曲专辑海报封面
                            Box(
                                modifier =
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CoverThumbnailImage(
                                    thumbnailPath = track.coverThumbnailPath,
                                    contentDescription = "封面",
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color =
                                            if (track.isVirtualTrack) {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (track.isVirtualTrack) AppIcons.QueueMusic else AppIcons.Audiotrack,
                                                contentDescription = null,
                                                tint =
                                                    if (track.isVirtualTrack) {
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                    },
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (track.isVirtualTrack) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        text = "分轨",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (track.durationMs > 0L) {
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = AppIcons.MoreVert,
                                        contentDescription = "更多操作",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("详细信息") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Info,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onShowDetails(track)
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                HorizontalDivider(
                    modifier =
                        Modifier
                            .padding(start = if (showDragHandle) 136.dp else 100.dp, end = 16.dp)
                            .align(Alignment.BottomCenter),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaylistTrackRowPreview() {
    MaterialTheme {
        val listState = rememberLazyListState()
        val dragDropState = remember(listState) { DragDropState(listState, { _, _ -> }, {}) }
        PlaylistTrackRow(
            track =
                PlaylistTrackEntry(
                    id = 1L,
                    playlistId = 1L,
                    serverId = 1L,
                    remotePath = "/Music/Artist/Track 01.flac",
                    sortOrder = 0,
                    title = "Sample Track",
                    artist = "Sample Artist",
                    album = "Sample Album",
                    durationMs = 215000L,
                    size = 14200000L,
                    format = AudioFormat.FLAC,
                ),
            index = 0,
            dragDropState = dragDropState,
            onClick = {},
            onRemove = {},
            onPlayNext = {},
            onShowDetails = {},
        )
    }
}
