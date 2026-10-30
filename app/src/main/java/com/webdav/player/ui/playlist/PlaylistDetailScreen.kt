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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webdav.player.domain.model.PlaybackProgress
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
                    dragOffset -= (item.offset - currentItemInfo.offset)
                    draggedIndex = item.index
                    break
                } else if (dragOffset < 0 && currentMidY < itemMidY && item.index < currentIndex) {
                    onMove(currentIndex, item.index)
                    dragOffset -= (item.offset - currentItemInfo.offset)
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
    var trackForDetails by remember { mutableStateOf<PlaylistTrackEntry?>(null) }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { message ->
            android.widget.Toast
                .makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    BackHandler(enabled = uiState.isCustomReorderMode) {
        viewModel.saveCustomReorder()
    }
    BackHandler(enabled = !uiState.isCustomReorderMode, onBack = onBackClick)

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
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (uiState.isCustomReorderMode) {
                        TextButton(
                            onClick = { viewModel.saveCustomReorder() },
                        ) {
                            Text(
                                text = "保存",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
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
                                    text = { Text("自定义排序") },
                                    trailingIcon = {
                                        if (uiState.activeSortOption == PlaylistSortOption.CUSTOM) {
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
                                        viewModel.enterCustomReorderMode()
                                    },
                                )
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
            // Header Area (大字标题 + 药丸动作栏，无大方块海报)
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
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.tracks,
                            key = { _, item -> item.id },
                        ) { index, track ->
                            val isDragging = dragDropState.draggedIndex == index
                            PlaylistTrackRow(
                                track = track,
                                index = index,
                                dragDropState = dragDropState,
                                showDragHandle = uiState.isCustomReorderMode,
                                onClick = { onTrackClick(track, index) },
                                onRemove = { viewModel.removeTrack(track.id) },
                                onPlayNext = {
                                    viewModel.playNext(track)
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            "已将 “${track.title}” 添加到下一首播放",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                },
                                onShowDetails = { trackForDetails = it },
                                modifier =
                                    if (isDragging) {
                                        Modifier.zIndex(3f)
                                    } else {
                                        Modifier.animateItemPlacement()
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    if (trackForDetails != null) {
        PlaylistTrackDetailDialog(
            track = trackForDetails!!,
            onDismiss = { trackForDetails = null },
            onPlay = {
                viewModel.playTrack(trackForDetails!!)
                trackForDetails = null
            },
        )
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

    // 细化滑动操作：右滑删除，左滑下一首播放（增大滑动触发阈值至 65%，避免误触）
    val dismissState =
        key(track.id) {
            rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
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
                },
                positionalThreshold = { totalDistance -> totalDistance * 0.65f },
            )
        }

    // 到达临界阈值位置触发震动反馈
    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                .zIndex(if (isDragging) 2f else 1f)
                .graphicsLayer {
                    translationY = if (isDragging) dragDropState.dragOffset else 0f
                    shadowElevation = elevation.toPx()
                },
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isDelete = direction == SwipeToDismissBoxValue.StartToEnd
            val isPlayNext = direction == SwipeToDismissBoxValue.EndToStart

            val bgColor =
                when {
                    isDelete -> MaterialTheme.colorScheme.errorContainer
                    isPlayNext -> MaterialTheme.colorScheme.primaryContainer
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

@Composable
fun PlaylistTrackDetailDialog(
    track: PlaylistTrackEntry,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
) {
    val directoryPath =
        remember(track.remotePath) {
            val slashIdx = track.remotePath.lastIndexOf('/')
            if (slashIdx > 0) track.remotePath.substring(0, slashIdx + 1) else "/"
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "音频详细信息",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaylistInfoRow(label = "歌曲标题", value = track.title)
                if (!track.artist.isNullOrBlank()) {
                    PlaylistInfoRow(label = "艺术家", value = track.artist)
                }
                if (!track.album.isNullOrBlank()) {
                    PlaylistInfoRow(label = "专辑名称", value = track.album)
                }
                PlaylistInfoRow(label = "音频格式", value = track.format.name)
                if (track.durationMs > 0L) {
                    PlaylistInfoRow(label = "曲目时长", value = PlaybackProgress.formatMs(track.durationMs))
                }
                if (track.size > 0L) {
                    PlaylistInfoRow(label = "文件大小", value = formatTrackFileSize(track.size))
                }
                PlaylistInfoRow(label = "来源目录", value = directoryPath)
                PlaylistInfoRow(label = "远程完整路径", value = track.remotePath)
                PlaylistInfoRow(label = "所属服务器 ID", value = "${track.serverId}")
                if (track.isVirtualTrack) {
                    PlaylistInfoRow(label = "CUE 分轨标识", value = track.virtualTrackId ?: "分轨")
                    if (!track.cuePath.isNullOrBlank()) {
                        PlaylistInfoRow(label = "CUE 文件路径", value = track.cuePath)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onPlay) {
                Text("立即播放")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun PlaylistInfoRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatTrackFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
