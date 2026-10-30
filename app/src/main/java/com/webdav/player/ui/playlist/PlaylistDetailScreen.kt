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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
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
import com.webdav.player.ui.common.CoverThumbnailImage
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
            // Header Area (方案 B-1: 纯大字标题 + 药丸动作栏，无大方块海报)
            PlaylistHeaderArea(
                playlist = uiState.playlist,
                trackCount = uiState.tracks.size,
                onPlayAll = onPlayAll,
                onShufflePlay = { viewModel.playShuffle() },
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
    onShufflePlay: () -> Unit = {},
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

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = playlist?.name ?: "",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "共 $trackCount 首歌曲 · 创建于 $createdFormatted",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onPlayAll,
                enabled = trackCount > 0,
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
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
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            FilledTonalButton(
                onClick = onShufflePlay,
                enabled = trackCount > 0,
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "随机播放",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
        targetValue = if (isDragging) 8.dp else 1.dp,
        label = "drag_elevation",
    )

    val durationText =
        remember(track.durationMs) {
            com.webdav.player.domain.model.PlaybackProgress.formatMs(track.durationMs)
        }

    val subtitle =
        remember(track.artist, track.album) {
            val parts = mutableListOf<String>()
            if (!track.artist.isNullOrBlank()) parts.add(track.artist)
            if (!track.album.isNullOrBlank()) parts.add(track.album)
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
                        .clip(RoundedCornerShape(16.dp))
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
        ElevatedCard(
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
                    }
                    .clickable(onClick = onClick)
                    .alpha(if (track.isInactive) 0.38f else 1f),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor =
                        if (isDragging) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 拖拽手把
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 48×48dp 方形圆角专辑封面海报
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverThumbnailImage(
                        thumbnailPath = track.coverThumbnailPath,
                        contentDescription = "封面",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color =
                                if (track.isVirtualTrack) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
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
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 歌曲信息
                Column(modifier = Modifier.weight(1f)) {
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
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (track.durationMs > 0L) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }

                // 三点操作菜单
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
            }
        }
    }
}
