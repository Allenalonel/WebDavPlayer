package com.webdav.player.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val onDragEnd: () -> Unit,
) {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun onDragStart(index: Int) {
        draggedIndex = index
        dragOffset = 0f
    }

    fun onDrag(dragAmount: Float) {
        val currentIndex = draggedIndex ?: return
        dragOffset += dragAmount

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentItemInfo = visibleItems.firstOrNull { it.index == currentIndex } ?: return
        val currentMidY = currentItemInfo.offset + dragOffset + currentItemInfo.size / 2f

        for (item in visibleItems) {
            if (item.index != currentIndex) {
                val itemMidY = item.offset + item.size / 2f
                if (dragOffset > 0 && currentMidY > itemMidY && item.index > currentIndex) {
                    onMove(currentIndex, item.index)
                    dragOffset -= item.size
                    draggedIndex = item.index
                    break
                } else if (dragOffset < 0 && currentMidY < itemMidY && item.index < currentIndex) {
                    onMove(currentIndex, item.index)
                    dragOffset += item.size
                    draggedIndex = item.index
                    break
                }
            }
        }
    }

    fun onDragStopped() {
        val wasDragging = draggedIndex != null
        draggedIndex = null
        dragOffset = 0f
        if (wasDragging) {
            onDragEnd()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    onBackClick: () -> Unit,
    onTrackClick: (PlaylistTrackEntry, Int) -> Unit = { track, index -> viewModel.onTrackClick(track, index) },
    onPlayAll: () -> Unit = { viewModel.playAll() },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(onBack = onBackClick)

    val listState = rememberLazyListState()
    val dragDropState =
        remember(listState) {
            DragDropState(
                lazyListState = listState,
                onMove = { from, to -> viewModel.moveTrack(from, to) },
                onDragEnd = { viewModel.onDragEnd() },
            )
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.playlist?.name ?: "歌单详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = AppIcons.Sort,
                                contentDescription = "排序",
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加时间（最新优先）") },
                                trailingIcon = {
                                    if (uiState.activeSortOption == PlaylistSortOption.DATE_ADDED_DESC) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.sortTracks(PlaylistSortOption.DATE_ADDED_DESC)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("添加时间（最早优先）") },
                                trailingIcon = {
                                    if (uiState.activeSortOption == PlaylistSortOption.DATE_ADDED_ASC) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.sortTracks(PlaylistSortOption.DATE_ADDED_ASC)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("歌曲标题（A-Z）") },
                                trailingIcon = {
                                    if (uiState.activeSortOption == PlaylistSortOption.TITLE_ASC) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.sortTracks(PlaylistSortOption.TITLE_ASC)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("艺术家（A-Z）") },
                                trailingIcon = {
                                    if (uiState.activeSortOption == PlaylistSortOption.ARTIST_ASC) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.sortTracks(PlaylistSortOption.ARTIST_ASC)
                                },
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = AppIcons.MoreVert,
                                contentDescription = "更多操作",
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("清理失效歌曲") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.cleanInactiveTracks()
                                },
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // Header Area
            PlaylistHeaderArea(
                playlist = uiState.playlist,
                trackCount = uiState.tracks.size,
                onPlayAll = onPlayAll,
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            when {
                uiState.isLoading && uiState.tracks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.tracks.isEmpty() -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = AppIcons.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "歌单内暂无歌曲",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "可以在曲库或搜索结果中将喜欢的音乐加入歌单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.tracks,
                            key = { _, item -> item.id },
                        ) { index, track ->
                            PlaylistTrackRow(
                                track = track,
                                index = index,
                                dragDropState = dragDropState,
                                onClick = { onTrackClick(track, index) },
                                onRemove = { viewModel.removeTrack(track.id) },
                                modifier = Modifier.animateItemPlacement(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistHeaderArea(
    playlist: Playlist?,
    trackCount: Int,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val createdFormatted =
        remember(playlist?.createdAtMs) {
            if (playlist != null && playlist.createdAtMs > 0) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.format(Date(playlist.createdAtMs))
            } else {
                "--"
            }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AppIcons.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist?.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "共 $trackCount 首 · 创建于 $createdFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onPlayAll,
            enabled = trackCount > 0,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = AppIcons.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "播放全部",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTrackRow(
    track: PlaylistTrackEntry,
    index: Int,
    dragDropState: DragDropState,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val isDragging = dragDropState.draggedIndex == index
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "drag_elevation",
    )

    val durationText =
        remember(track.durationMs) {
            formatDuration(track.durationMs)
        }

    val subtitle =
        remember(track.artist, track.album, durationText) {
            val parts = mutableListOf<String>()
            if (!track.artist.isNullOrBlank()) parts.add(track.artist)
            if (!track.album.isNullOrBlank()) parts.add(track.album)
            parts.add(durationText)
            parts.joinToString(" · ")
        }

    val dismissState =
        key(track.id) {
            rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                        onRemove()
                        true
                    } else {
                        false
                    }
                },
            )
        }

    SwipeToDismissBox(
        state = dismissState,
        modifier =
            modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 2f else 1f)
                .graphicsLayer {
                    translationY = if (isDragging) dragDropState.dragOffset else 0f
                    shadowElevation = elevation.toPx()
                },
        backgroundContent = {
            val color = MaterialTheme.colorScheme.errorContainer
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                contentAlignment =
                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
            ) {
                Icon(
                    imageVector = AppIcons.DeleteOutline,
                    contentDescription = "从歌单移除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pointerInput(dragDropState, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragDropState.onDragStart(index)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDropState.onDrag(dragAmount.y)
                            },
                            onDragEnd = { dragDropState.onDragStopped() },
                            onDragCancel = { dragDropState.onDragStopped() },
                        )
                    },
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
                            // Drag Handle
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .pointerInput(dragDropState, index) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    dragDropState.onDragStart(index)
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Track index
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = AppIcons.MoreVert,
                                    contentDescription = "更多操作",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("从歌单移除", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = AppIcons.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onRemove()
                                    },
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp).align(Alignment.BottomCenter),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
