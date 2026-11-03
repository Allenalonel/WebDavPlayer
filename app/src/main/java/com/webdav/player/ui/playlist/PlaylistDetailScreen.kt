package com.webdav.player.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.ui.common.FluidTopAppBar
import com.webdav.player.ui.player.MiniPlayerDefaults
import com.webdav.player.ui.playlist.components.PlaylistHeaderArea
import com.webdav.player.ui.playlist.components.PlaylistTrackDetailDialog
import com.webdav.player.ui.playlist.components.PlaylistTrackRow
import com.webdav.player.ui.theme.AppIcons

internal class DragDropState(
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
                val movingDown = dragOffset > 0 && currentMidY > itemMidY && item.index > currentIndex
                val movingUp = dragOffset < 0 && currentMidY < itemMidY && item.index < currentIndex
                if (movingDown || movingUp) {
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
    isCurrentTab: Boolean = true,
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

    LaunchedEffect(isCurrentTab) {
        if (!isCurrentTab && uiState.isCustomReorderMode) {
            viewModel.saveCustomReorder()
        }
    }

    BackHandler(enabled = isCurrentTab && uiState.isCustomReorderMode) {
        viewModel.saveCustomReorder()
    }
    BackHandler(enabled = isCurrentTab && !uiState.isCustomReorderMode, onBack = onBackClick)

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
            FluidTopAppBar(
                title = uiState.playlist?.name ?: "歌单",
                subtitle = "${uiState.tracks.size} 首歌曲",
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
                                val sortOptions =
                                    listOf(
                                        PlaylistSortOption.CUSTOM to "自定义排序",
                                        PlaylistSortOption.DATE_ADDED_DESC to "添加时间（最新优先）",
                                        PlaylistSortOption.DATE_ADDED_ASC to "添加时间（最早优先）",
                                        PlaylistSortOption.TITLE_ASC to "歌曲标题（A-Z）",
                                        PlaylistSortOption.ARTIST_ASC to "艺术家（A-Z）",
                                    )
                                sortOptions.forEach { (option, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        trailingIcon = {
                                            if (uiState.activeSortOption == option) {
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
                                            if (option == PlaylistSortOption.CUSTOM) {
                                                viewModel.enterCustomReorderMode()
                                            } else {
                                                viewModel.sortTracks(option)
                                            }
                                        },
                                    )
                                }
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
                scrollBehavior = null,
                expandedHeight = 0.dp,
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
                        contentPadding =
                            PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 4.dp,
                                bottom = MiniPlayerDefaults.PenetrativeContentBottomPadding,
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                isActive = uiState.isTrackActive(track),
                                isPlaying = uiState.isPlaying,
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
