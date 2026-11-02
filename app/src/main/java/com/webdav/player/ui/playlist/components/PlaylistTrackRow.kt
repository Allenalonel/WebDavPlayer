package com.webdav.player.ui.playlist.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.AudioTrackRowDefaults
import com.webdav.player.ui.common.TrackTagPill
import com.webdav.player.ui.common.TrackThumbnailLeading
import com.webdav.player.ui.playlist.DragDropState
import com.webdav.player.ui.theme.AppIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

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
    val isDragging = dragDropState.draggedIndex == index
    val elevation by animateDpAsState(
        targetValue = if (isDragging) PlaylistTrackRowDefaults.DragElevation else PlaylistTrackRowDefaults.IdleElevation,
        label = "drag_elevation",
    )

    val subtitle =
        remember(track.artist, track.album) {
            AudioTrackRowDefaults.formatSubtitle(track.artist, track.album)
        }
    val qualityBadge = remember(track) { track.toAudioTrack().qualityBadge }

    val itemWidthPx = remember { mutableFloatStateOf(0f) }
    val isPastThreshold = remember { mutableStateOf(false) }

    val dismissState =
        key(track.id) {
            rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    PlaylistTrackSwipeHelper.handleDismiss(
                        value = value,
                        isPastThreshold = isPastThreshold.value,
                        onRemove = onRemove,
                        onPlayNext = onPlayNext,
                    )
                },
                positionalThreshold = { totalDistance ->
                    PlaylistTrackSwipeHelper.positionalThreshold(totalDistance)
                },
            )
        }

    LaunchedEffect(dismissState) {
        snapshotFlow {
            val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
            PlaylistTrackSwipeHelper.isPastThreshold(offset, itemWidthPx.floatValue)
        }.distinctUntilChanged().collect { past ->
            isPastThreshold.value = past
            if (past) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

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
            PlaylistSwipeBackground(
                dismissDirection = dismissState.dismissDirection,
                isPastThreshold = isPastThreshold.value,
            )
        },
    ) {
        val surfaceColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
        Surface(modifier = Modifier.fillMaxWidth(), color = surfaceColor) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AudioTrackRow(
                    title = track.title,
                    subtitle = subtitle,
                    modifier = Modifier.alpha(if (track.isInactive) PlaylistTrackRowDefaults.InactiveAlpha else 1f),
                    durationText = null,
                    audioQualityBadge = qualityBadge,
                    onClick = onClick,
                    leadingContent = {
                        PlaylistTrackLeadingContent(
                            index = index,
                            track = track,
                            showDragHandle = showDragHandle,
                            dragDropState = dragDropState,
                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        )
                    },
                    tagContent =
                        if (track.isVirtualTrack) {
                            { PlaylistVirtualTrackTag() }
                        } else {
                            null
                        },
                    actionsMenu = { dismiss ->
                        PlaylistTrackActionsMenu(
                            track = track,
                            onPlayNext = onPlayNext,
                            onRemove = onRemove,
                            onShowDetails = onShowDetails,
                            dismiss = dismiss,
                        )
                    },
                )
                HorizontalDivider(
                    modifier =
                        Modifier.padding(
                            start = PlaylistTrackRowDefaults.dividerStartInset(showDragHandle),
                            end = AudioTrackRowDefaults.DividerInsetEnd,
                        ),
                    thickness = PlaylistTrackRowDefaults.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = PlaylistTrackRowDefaults.DividerAlpha),
                )
            }
        }
    }
}

internal object PlaylistTrackRowDefaults {
    val DragElevation: Dp = 6.dp
    val IdleElevation: Dp = 0.dp
    val ThumbnailSize: Dp = 44.dp
    val DragHandleBoxSize: Dp = 36.dp
    val DragHandleIconSize: Dp = 20.dp
    val MenuIconSize: Dp = 20.dp
    val DividerThickness: Dp = 0.5.dp
    const val InactiveAlpha: Float = 0.38f
    const val DividerAlpha: Float = 0.25f

    fun dividerStartInset(showDragHandle: Boolean): Dp = if (showDragHandle) 136.dp else 100.dp
}

internal object PlaylistTrackSwipeHelper {
    fun positionalThreshold(totalDistance: Float): Float = totalDistance * 0.5f

    fun isPastThreshold(
        offset: Float,
        width: Float,
    ): Boolean = width > 0f && abs(offset) >= (width * 0.5f)

    @OptIn(ExperimentalMaterial3Api::class)
    fun handleDismiss(
        value: SwipeToDismissBoxValue,
        isPastThreshold: Boolean,
        onRemove: () -> Unit,
        onPlayNext: () -> Unit,
    ): Boolean {
        if (!isPastThreshold) return false
        return when (value) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onRemove()
                true
            }

            SwipeToDismissBoxValue.EndToStart -> {
                onPlayNext()
                false
            }

            else -> {
                false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistSwipeBackground(
    dismissDirection: SwipeToDismissBoxValue?,
    isPastThreshold: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDelete = dismissDirection == SwipeToDismissBoxValue.StartToEnd
    val isPlayNext = dismissDirection == SwipeToDismissBoxValue.EndToStart

    val alpha = if (isPastThreshold) 1f else 0.45f
    val bgColor =
        when {
            isDelete -> MaterialTheme.colorScheme.errorContainer.copy(alpha = alpha)
            isPlayNext -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
            else -> Color.Transparent
        }

    Box(
        modifier =
            modifier
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
}

@Composable
internal fun PlaylistTrackLeadingContent(
    index: Int,
    track: PlaylistTrackEntry,
    showDragHandle: Boolean,
    dragDropState: DragDropState,
    onDragStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIdx by rememberUpdatedState(index)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showDragHandle) {
            Box(
                modifier =
                    Modifier
                        .size(PlaylistTrackRowDefaults.DragHandleBoxSize)
                        .pointerInput(dragDropState) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    onDragStart()
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
                    modifier = Modifier.size(PlaylistTrackRowDefaults.DragHandleIconSize),
                )
            }
        }

        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center,
        )

        TrackThumbnailLeading(
            coverThumbnailPath = track.coverThumbnailPath,
            size = PlaylistTrackRowDefaults.ThumbnailSize,
            fallbackIcon = if (track.isVirtualTrack) AppIcons.QueueMusic else AppIcons.Audiotrack,
        )
    }
}

@Composable
internal fun PlaylistTrackActionsMenu(
    track: PlaylistTrackEntry,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    onShowDetails: (PlaylistTrackEntry) -> Unit,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("下一首播放") },
        leadingIcon = {
            Icon(AppIcons.QueueMusic, contentDescription = null, modifier = Modifier.size(PlaylistTrackRowDefaults.MenuIconSize))
        },
        onClick = {
            dismiss()
            onPlayNext()
        },
    )
    DropdownMenuItem(
        text = { Text("从歌单删除") },
        leadingIcon = {
            Icon(AppIcons.DeleteOutline, contentDescription = null, modifier = Modifier.size(PlaylistTrackRowDefaults.MenuIconSize))
        },
        onClick = {
            dismiss()
            onRemove()
        },
    )
    DropdownMenuItem(
        text = { Text("详细信息") },
        leadingIcon = {
            Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(PlaylistTrackRowDefaults.MenuIconSize))
        },
        onClick = {
            dismiss()
            onShowDetails(track)
        },
    )
}

@Composable
internal fun PlaylistVirtualTrackTag(modifier: Modifier = Modifier) =
    TrackTagPill(
        text = "分轨",
        modifier = modifier,
    )

@Preview(showBackground = true)
@Composable
internal fun PlaylistTrackRowPreview() {
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
