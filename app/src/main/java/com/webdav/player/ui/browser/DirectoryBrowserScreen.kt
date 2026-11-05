package com.webdav.player.ui.browser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.ui.browser.components.AudioTrackItemRow
import com.webdav.player.ui.browser.components.CueAlbumCard
import com.webdav.player.ui.browser.components.DirectoryBreadcrumbStrip
import com.webdav.player.ui.browser.components.DirectoryItemRow
import com.webdav.player.ui.browser.components.EmptyFolderState
import com.webdav.player.ui.browser.components.ErrorState
import com.webdav.player.ui.browser.components.FloatingSearchPillCapsule
import com.webdav.player.ui.browser.components.NoActiveServerState
import com.webdav.player.ui.browser.components.SearchResultsContent
import com.webdav.player.ui.common.FloatingMultiSelectBottomBar
import com.webdav.player.ui.common.FluidTopAppBar
import com.webdav.player.ui.common.FluidTopAppBarDefaults
import com.webdav.player.ui.motion.MotionTokens
import com.webdav.player.ui.motion.MotionTransitions
import com.webdav.player.ui.motion.isReduceMotionEnabled
import com.webdav.player.ui.player.MiniPlayerDefaults
import com.webdav.player.ui.theme.AppIcons

/**
 * Snapshot of directory items used to preserve the outgoing screen's exact content
 * during hierarchy drilldown transitions.
 */
private data class CachedDirectoryContent(
    val directories: List<RemoteDirectory>,
    val files: List<RemoteFile>,
    val metadataMap: Map<String, TrackMetadata>,
    val cueAlbums: List<CueAlbumItem>,
    val isEmpty: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowserScreen(
    viewModel: DirectoryBrowserViewModel,
    onNavigateToServerManagement: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTab: Boolean = true,
    onFileClicked: (RemoteFile) -> Unit = { viewModel.onAudioTrackClicked(it) },
    @Suppress("UNUSED_PARAMETER") onOpenFullPlayer: () -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onAddTracksToPlaylist: (List<AudioTrack>) -> Unit = { tracks -> tracks.firstOrNull()?.let(onAddToPlaylist) },
    bottomBarPadding: Dp = 88.dp,
    onSelectionModeChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isReduceMotion = isReduceMotionEnabled()

    val (selectedFilePaths, setSelectedFilePaths) = remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedFilePaths.isNotEmpty()

    val (selectedSearchTrackIds, setSelectedSearchTrackIds) = remember { mutableStateOf(setOf<String>()) }
    val isSearchSelectionMode = selectedSearchTrackIds.isNotEmpty()

    val isSelectionActive = isSelectionMode || isSearchSelectionMode
    LaunchedEffect(isSelectionActive) {
        onSelectionModeChanged(isSelectionActive)
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearchSelectionMode) {
        if (isSearchSelectionMode) {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(uiState.currentPath) {
        setSelectedFilePaths(emptySet())
    }

    LaunchedEffect(uiState.isSearchActive) {
        setSelectedSearchTrackIds(emptySet())
    }

    BackHandler(enabled = isCurrentTab && isSearchSelectionMode) {
        setSelectedSearchTrackIds(emptySet())
    }

    BackHandler(enabled = isCurrentTab && !isSearchSelectionMode && uiState.isSearchActive) {
        viewModel.onSearchActiveChanged(false)
    }

    BackHandler(enabled = isCurrentTab && isSelectionMode && !uiState.isSearchActive) {
        setSelectedFilePaths(emptySet())
    }

    BackHandler(enabled = isCurrentTab && !isSelectionMode && !uiState.isSearchActive) {
        val handled = viewModel.onNavigateUp()
        if (!handled) {
            onNavigateToServerManagement()
        }
    }

    val context = LocalContext.current
    val isUserInitiatedRefresh = remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            isUserInitiatedRefresh.value = true
            viewModel.onRefresh()
        }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) {
            pullRefreshState.endRefresh()
            if (isUserInitiatedRefresh.value) {
                isUserInitiatedRefresh.value = false
                val error = uiState.errorMessage
                if (error != null) {
                    Toast.makeText(context, "刷新失败: $error", Toast.LENGTH_SHORT).show()
                } else {
                    val count = (uiState.currentDirectory?.subDirectories?.size ?: 0) + (uiState.currentDirectory?.files?.size ?: 0)
                    Toast.makeText(context, "刷新完成 (共 $count 项)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Flag indicating if upcoming path navigation is a focal landing from search results
    val isSearchLanding = remember { mutableStateOf(false) }

    // Directory content cache to guarantee solid double-layer transitions without flashing
    val directoryContentCache = remember { mutableMapOf<String, CachedDirectoryContent>() }

    LaunchedEffect(uiState.currentPath, uiState.subDirectories, uiState.files, uiState.metadataMap, uiState.cueAlbums) {
        if (uiState.currentDirectory != null && uiState.currentDirectory?.path == uiState.currentPath) {
            directoryContentCache[uiState.currentPath] =
                CachedDirectoryContent(
                    directories = uiState.subDirectories,
                    files = uiState.files,
                    metadataMap = uiState.metadataMap,
                    cueAlbums = uiState.cueAlbums,
                    isEmpty = uiState.isEmpty,
                )
        }
    }

    // Search capsule morphing progress: drives smooth top bar title fade-out and pill growth
    val searchExpansionProgress by animateFloatAsState(
        targetValue = if (uiState.isSearchActive) 1f else 0f,
        animationSpec = MotionTokens.spatialSpec(isReduceMotion),
        label = "search_expansion_progress",
    )
    val topBarTitleAlpha = (1f - searchExpansionProgress).coerceIn(0f, 1f)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(uiState.currentPath) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    val coordinatedRefreshConnection =
        remember(pullRefreshState, scrollBehavior) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y > 0f && scrollBehavior.state.collapsedFraction > 0f) {
                        return Offset.Zero
                    }
                    return pullRefreshState.nestedScrollConnection.onPreScroll(available, source)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y > 0f && scrollBehavior.state.collapsedFraction > 0f) {
                        return Offset.Zero
                    }
                    return pullRefreshState.nestedScrollConnection.onPostScroll(consumed, available, source)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (available.y > 0f && scrollBehavior.state.collapsedFraction > 0f) {
                        return Velocity.Zero
                    }
                    return pullRefreshState.nestedScrollConnection.onPreFling(available)
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (available.y > 0f && scrollBehavior.state.collapsedFraction > 0f) {
                        return Velocity.Zero
                    }
                    return pullRefreshState.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        // --- Layer 1: Base Directory Browser Scaffold ---
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "已选 ${selectedFilePaths.size} 项",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = uiState.headerTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { setSelectedFilePaths(emptySet()) }) {
                                Icon(
                                    imageVector = AppIcons.Close,
                                    contentDescription = "退出多选",
                                )
                            }
                        },
                        actions = {
                            val selectableFiles = uiState.files.filter { it.isAudio }
                            val isAllSelected = selectableFiles.isNotEmpty() && selectedFilePaths.size == selectableFiles.size
                            TextButton(
                                onClick = {
                                    setSelectedFilePaths(
                                        if (isAllSelected) {
                                            emptySet()
                                        } else {
                                            selectableFiles.map { it.path }.toSet()
                                        },
                                    )
                                },
                            ) {
                                Text(
                                    text = if (isAllSelected) "取消全选" else "全选",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                    )
                } else {
                    FluidTopAppBar(
                        title = uiState.headerTitle,
                        subtitle = uiState.activeServer?.name?.let { "当前活跃节点: $it" } ?: "未连接服务器",
                        navigationIcon = null,
                        actions = {
                            if (!uiState.isInitializing && uiState.activeServer != null) {
                                FilledTonalIconButton(
                                    onClick = { viewModel.onSearchActiveChanged(true) },
                                    modifier =
                                        Modifier
                                            .size(38.dp)
                                            .alpha(if (searchExpansionProgress > 0f) 0f else 1f),
                                    shape = CircleShape,
                                    colors =
                                        IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Search,
                                        contentDescription = "搜索曲库",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        expandedHeight = FluidTopAppBarDefaults.ExpandedHeight,
                        modifier = Modifier.alpha(topBarTitleAlpha),
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                if (!uiState.isInitializing && uiState.activeServer != null) {
                    // Breadcrumb navigation bar (MD3 AssistChip Strip with dynamic tail growth)
                    DirectoryBreadcrumbStrip(
                        breadcrumbs = uiState.breadcrumbs,
                        currentPath = uiState.currentPath,
                        onBreadcrumbClicked = { viewModel.onBreadcrumbClicked(it) },
                    )
                }

                // Content Area with Hierarchy Drilldown AnimatedContent
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(coordinatedRefreshConnection)
                            .clipToBounds(),
                ) {
                    when {
                        uiState.isInitializing -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        uiState.activeServer == null -> {
                            NoActiveServerState(onNavigateToServerManagement = onNavigateToServerManagement)
                        }

                        else -> {
                            AnimatedContent(
                                targetState = uiState.currentPath,
                                transitionSpec = {
                                    val landing = isSearchLanding.value
                                    val direction =
                                        MotionTransitions.resolveHierarchyDirection(
                                            initialPath = initialState,
                                            targetPath = targetState,
                                            isFocalLanding = landing,
                                        )
                                    if (landing) {
                                        isSearchLanding.value = false
                                    }
                                    MotionTransitions.hierarchyContentTransform(direction, isReduceMotion)
                                },
                                label = "hierarchy_drilldown_transition",
                                modifier = Modifier.fillMaxSize(),
                            ) { targetPath ->
                                Box(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                ) {
                                    if (targetPath == uiState.currentPath) {
                                    // Live content for current path
                                    when {
                                        uiState.isLoading && uiState.currentDirectory == null -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    CircularProgressIndicator()
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                                                        text = "正在加载远程目录...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }

                                        uiState.errorMessage != null -> {
                                            ErrorState(
                                                message = uiState.errorMessage ?: "加载失败",
                                                onRetry = { viewModel.onRetry() },
                                                onGoBack = { viewModel.onNavigateUp() },
                                                canGoBack = uiState.canNavigateUp,
                                            )
                                        }

                                        uiState.isEmpty -> {
                                            EmptyFolderState(onRefresh = {
                                                isUserInitiatedRefresh.value = true
                                                Toast.makeText(context, "正在刷新目录...", Toast.LENGTH_SHORT).show()
                                                viewModel.onRefresh()
                                            })
                                        }

                                        else -> {
                                            DirectoryContentList(
                                                directories = uiState.subDirectories,
                                                files = uiState.files,
                                                metadataMap = uiState.metadataMap,
                                                activeTrackPath = uiState.activeTrackPath,
                                                activeTrackId = uiState.activeTrackId,
                                                cueAlbums = uiState.cueAlbums,
                                                isPlaying = uiState.isPlaying,
                                                activeServer = uiState.activeServer,
                                                onDirectoryClicked = { viewModel.onDirectoryClicked(it) },
                                                onFileClicked = { file ->
                                                    if (isSelectionMode && file.isAudio) {
                                                        setSelectedFilePaths(
                                                            if (file.path in selectedFilePaths) {
                                                                selectedFilePaths - file.path
                                                            } else {
                                                                selectedFilePaths + file.path
                                                            },
                                                        )
                                                    } else {
                                                        onFileClicked(file)
                                                    }
                                                },
                                                onPlayNext = { viewModel.playNext(it) },
                                                onAddToPlaylist = onAddToPlaylist,
                                                onVirtualTrackClick = {
                                                    album,
                                                    trackIndex,
                                                    ->
                                                    viewModel.playVirtualTrack(album, trackIndex)
                                                },
                                                onPlayCueAlbum = { album -> viewModel.playCueAlbum(album) },
                                                onToggleCueAlbumExpanded = { cuePath -> viewModel.toggleCueAlbumExpanded(cuePath) },
                                                isSelectionMode = isSelectionMode,
                                                selectedFilePaths = selectedFilePaths,
                                                onFileLongClicked = { file ->
                                                    if (file.isAudio && !isSelectionMode) {
                                                        setSelectedFilePaths(setOf(file.path))
                                                    }
                                                },
                                            )
                                        }
                                    }
                                } else {
                                    // Outgoing content during transition: rendered from cache to eliminate flashing
                                    val cached = directoryContentCache[targetPath]
                                    if (cached != null) {
                                        DirectoryContentList(
                                            directories = cached.directories,
                                            files = cached.files,
                                            metadataMap = cached.metadataMap,
                                            activeTrackPath = uiState.activeTrackPath,
                                            activeTrackId = uiState.activeTrackId,
                                            cueAlbums = cached.cueAlbums,
                                            isPlaying = uiState.isPlaying,
                                            activeServer = uiState.activeServer,
                                            onDirectoryClicked = { viewModel.onDirectoryClicked(it) },
                                            onFileClicked = { onFileClicked(it) },
                                            onPlayNext = { viewModel.playNext(it) },
                                            onAddToPlaylist = onAddToPlaylist,
                                            onVirtualTrackClick = { album, trackIndex -> viewModel.playVirtualTrack(album, trackIndex) },
                                            onPlayCueAlbum = { album -> viewModel.playCueAlbum(album) },
                                            onToggleCueAlbumExpanded = { cuePath -> viewModel.toggleCueAlbumExpanded(cuePath) },
                                            isSelectionMode = isSelectionMode,
                                            selectedFilePaths = selectedFilePaths,
                                            onFileLongClicked = null,
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                            }
                        }
                    }

                    FloatingMultiSelectBottomBar(
                        selectedCount = selectedFilePaths.size,
                        onPlayNext = {
                            val server = uiState.activeServer ?: return@FloatingMultiSelectBottomBar
                            val tracks =
                                selectedFilePaths.mapNotNull { path ->
                                    val file = uiState.files.firstOrNull { it.path == path }
                                    if (file != null) AudioTrack.fromRemoteFile(server, file, uiState.metadataMap[path]) else null
                                }
                            tracks.reversed().forEach { track ->
                                viewModel.musicPlayerAppSession?.playNext(track)
                            }
                            Toast.makeText(context, "已将 ${tracks.size} 首歌曲添加至下一首播放", Toast.LENGTH_SHORT).show()
                            setSelectedFilePaths(emptySet())
                        },
                        onAddToPlaylist = {
                            val server = uiState.activeServer ?: return@FloatingMultiSelectBottomBar
                            val tracks =
                                selectedFilePaths.mapNotNull { path ->
                                    val file = uiState.files.firstOrNull { it.path == path }
                                    if (file != null) AudioTrack.fromRemoteFile(server, file, uiState.metadataMap[path]) else null
                                }
                            if (tracks.isNotEmpty()) {
                                onAddTracksToPlaylist(tracks)
                                setSelectedFilePaths(emptySet())
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBarPadding),
                    )

                    if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) {
                        PullToRefreshContainer(
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }

        // --- Layer 2: Search View Overlay (Slides down from underneath search pill) ---
        AnimatedVisibility(
            visible = uiState.isSearchActive,
            enter = MotionTransitions.searchOverlayEnter(isReduceMotion),
            exit = MotionTransitions.searchOverlayExit(isReduceMotion),
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 56.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
            ) {
                SearchResultsContent(
                    query = uiState.searchQuery,
                    isSearching = uiState.isSearching,
                    searchResult = uiState.searchResult,
                    searchHistory = uiState.searchHistory,
                    onSelectHistoryQuery = {
                        viewModel.onSearchQueryChanged(it)
                        viewModel.onSearchSubmitted(it)
                    },
                    onClearHistory = { viewModel.onClearSearchHistory() },
                    onRemoveHistoryItem = { viewModel.onRemoveSearchHistoryItem(it) },
                    onDirectoryClick = { dir ->
                        isSearchLanding.value = true
                        viewModel.onSearchResultDirectoryClicked(dir)
                    },
                    onTrackClick = { track ->
                        if (isSearchSelectionMode) {
                            setSelectedSearchTrackIds(
                                if (track.id in selectedSearchTrackIds) {
                                    selectedSearchTrackIds - track.id
                                } else {
                                    selectedSearchTrackIds + track.id
                                },
                            )
                        } else {
                            viewModel.onSearchResultTrackClicked(track)
                            viewModel.onSearchActiveChanged(false)
                        }
                    },
                    onAddToPlaylist = onAddToPlaylist,
                    isSelectionMode = isSearchSelectionMode,
                    selectedTrackIds = selectedSearchTrackIds,
                    onTrackLongClick = { track ->
                        if (!isSearchSelectionMode) {
                            setSelectedSearchTrackIds(setOf(track.id))
                        }
                    },
                )

                FloatingMultiSelectBottomBar(
                    selectedCount = selectedSearchTrackIds.size,
                    onPlayNext = {
                        val tracks = uiState.searchResult.matchedTracks.filter { it.id in selectedSearchTrackIds }
                        tracks.reversed().forEach { track ->
                            viewModel.musicPlayerAppSession?.playNext(track)
                        }
                        Toast.makeText(context, "已将 ${tracks.size} 首歌曲添加至下一首播放", Toast.LENGTH_SHORT).show()
                        setSelectedSearchTrackIds(emptySet())
                    },
                    onAddToPlaylist = {
                        val tracks = uiState.searchResult.matchedTracks.filter { it.id in selectedSearchTrackIds }
                        if (tracks.isNotEmpty()) {
                            onAddTracksToPlaylist(tracks)
                            setSelectedSearchTrackIds(emptySet())
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBarPadding),
                )
            }
        }

        // --- Layer 3: Floating Search Pill Capsule (Morphs between 38dp circle and 26dp rounded pill) ---
        FloatingSearchPillCapsule(
            isExpanded = uiState.isSearchActive,
            onExpand = { viewModel.onSearchActiveChanged(true) },
            onBackClick = { viewModel.onSearchActiveChanged(false) },
            searchQuery = uiState.searchQuery,
            onQueryChange = { viewModel.onSearchQueryChanged(it) },
            onClearQuery = { viewModel.onClearSearchQuery() },
            isSearchSelectionMode = isSearchSelectionMode,
            selectedCount = selectedSearchTrackIds.size,
            isAllSelected =
                uiState.searchResult.matchedTracks.isNotEmpty() && selectedSearchTrackIds.size == uiState.searchResult.matchedTracks.size,
            onClearSelection = { setSelectedSearchTrackIds(emptySet()) },
            onToggleSelectAll = {
                val selectableTracks = uiState.searchResult.matchedTracks
                val isAllSelected = selectableTracks.isNotEmpty() && selectedSearchTrackIds.size == selectableTracks.size
                setSelectedSearchTrackIds(
                    if (isAllSelected) {
                        emptySet()
                    } else {
                        selectableTracks.map { it.id }.toSet()
                    },
                )
            },
        )
    }
}

@Composable
fun DirectoryContentList(
    directories: List<RemoteDirectory>,
    files: List<RemoteFile>,
    metadataMap: Map<String, TrackMetadata> = emptyMap(),
    activeTrackPath: String? = null,
    activeTrackId: String? = null,
    cueAlbums: List<CueAlbumItem> = emptyList(),
    isPlaying: Boolean = false,
    activeServer: WebDavServer? = null,
    onDirectoryClicked: (RemoteDirectory) -> Unit,
    onFileClicked: (RemoteFile) -> Unit,
    onPlayNext: (RemoteFile) -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onVirtualTrackClick: (CueAlbumItem, Int) -> Unit = { _, _ -> },
    onPlayCueAlbum: (CueAlbumItem) -> Unit = {},
    onToggleCueAlbumExpanded: (String) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedFilePaths: Set<String> = emptySet(),
    onFileLongClicked: ((RemoteFile) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cueAudioPaths = remember(cueAlbums) { cueAlbums.mapNotNull { it.audioFile?.path }.toSet() }
    val cueFilePaths = remember(cueAlbums) { cueAlbums.map { it.cueFile.path }.toSet() }
    val standaloneFiles =
        remember(files, cueAudioPaths, cueFilePaths) {
            files.filter { it.path !in cueAudioPaths && it.path !in cueFilePaths }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = MiniPlayerDefaults.PenetrativeContentBottomPadding),
    ) {
        // Subdirectories first (Elevated MD3 Cards)
        items(directories, key = { "dir_${it.path}" }) { dir ->
            DirectoryItemRow(
                directory = dir,
                onClick = { if (!isSelectionMode) onDirectoryClicked(dir) },
            )
        }

        // Space between folders and files if both exist
        if (directories.isNotEmpty() && (cueAlbums.isNotEmpty() || standaloneFiles.isNotEmpty())) {
            item(key = "divider_folder_files") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // CUE Albums next (MD3 CueAlbumCard with collapse/expand)
        items(cueAlbums, key = { "cue_album_${it.cueFile.path}" }) { album ->
            val metadata = album.audioFile?.let { metadataMap[it.path] }
            val isAlbumActive = album.isAlbumActive(activeTrackPath)
            CueAlbumCard(
                album = album,
                metadata = metadata,
                isAlbumActive = isAlbumActive,
                activeTrackId = activeTrackId,
                isPlaying = isPlaying,
                onPlayAlbum = { if (!isSelectionMode) onPlayCueAlbum(album) },
                onVirtualTrackClick = { trackIndex -> if (!isSelectionMode) onVirtualTrackClick(album, trackIndex) },
                onToggleExpand = { if (!isSelectionMode) onToggleCueAlbumExpanded(album.cueFile.path) },
                onPlayAsWholeAudio = { album.audioFile?.let { if (!isSelectionMode) onFileClicked(it) } },
            )
        }

        // Standalone audio and other files next (MD3 ListItems)
        items(standaloneFiles, key = { "file_${it.path}" }) { file ->
            val metadata = metadataMap[file.path]
            val isActive = file.isAudio && file.path == activeTrackPath
            AudioTrackItemRow(
                file = file,
                metadata = metadata,
                isActive = isActive,
                isPlaying = isPlaying,
                isSelectionMode = isSelectionMode,
                isSelected = file.path in selectedFilePaths,
                onClick = { onFileClicked(file) },
                onLongClick = onFileLongClicked?.let { { it(file) } },
                onPlayNext = { onPlayNext(file) },
                onAddToPlaylist = {
                    if (activeServer != null) {
                        AudioTrack.fromRemoteFile(activeServer, file, metadata)?.let { track ->
                            onAddToPlaylist(track)
                        }
                    }
                },
            )
        }
    }
}
