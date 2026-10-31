package com.webdav.player.ui.playlist.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.AudioTrackRowDefaults
import com.webdav.player.ui.playlist.DragDropState
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
    val isDragging = dragDropState.draggedIndex == index
    val elevation by animateDpAsState(
        targetValue = if (isDragging) PlaylistTrackRowDefaults.DragElevation else PlaylistTrackRowDefaults.IdleElevation,
        label = "drag_elevation",
    )

    val durationText =
        remember(track.durationMs) {
            if (track.durationMs > 0L) PlaybackProgress.formatMs(track.durationMs) else null
        }
    val subtitle =
        remember(track.artist, track.album) {
            AudioTrackRowDefaults.formatSubtitle(track.artist, track.album)
        }

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
                    durationText = durationText,
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
