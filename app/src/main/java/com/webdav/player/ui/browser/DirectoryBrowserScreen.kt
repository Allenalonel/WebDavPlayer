package com.webdav.player.ui.browser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.webdav.player.ui.browser.components.NoActiveServerState
import com.webdav.player.ui.browser.components.SearchResultsContent
import com.webdav.player.ui.common.FloatingMultiSelectBottomBar
import com.webdav.player.ui.theme.AppIcons

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val (selectedFilePaths, setSelectedFilePaths) = remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedFilePaths.isNotEmpty()

    val (selectedSearchTrackIds, setSelectedSearchTrackIds) = remember { mutableStateOf(setOf<String>()) }
    val isSearchSelectionMode = selectedSearchTrackIds.isNotEmpty()

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

    if (uiState.isSearchActive) {
        // 方案 2：全通透极简沉浸条（完全去除生硬的分割横线）
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSearchSelectionMode) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { setSelectedSearchTrackIds(emptySet()) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "退出多选",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "已选 ${selectedSearchTrackIds.size} 项",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            val selectableTracks = uiState.searchResult.matchedTracks
                            val isAllSelected = selectableTracks.isNotEmpty() && selectedSearchTrackIds.size == selectableTracks.size
                            TextButton(
                                onClick = {
                                    setSelectedSearchTrackIds(
                                        if (isAllSelected) {
                                            emptySet()
                                        } else {
                                            selectableTracks.map { it.id }.toSet()
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
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = AppIcons.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                singleLine = true,
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester),
                                decorationBox = { innerTextField ->
                                    if (uiState.searchQuery.isEmpty()) {
                                        Text(
                                            text = "搜索歌曲、歌手、专辑或文件夹...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                },
                            )
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onClearSearchQuery() },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "清空输入",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = { viewModel.onSearchActiveChanged(false) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // 下方搜索内容区域，无任何生硬分割横线
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                    onDirectoryClick = { viewModel.onSearchResultDirectoryClicked(it) },
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
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
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
                    TopAppBar(
                        title = {
                            val titleText = uiState.headerTitle
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        actions = {
                            if (!uiState.isInitializing) {
                                IconButton(onClick = {
                                    isUserInitiatedRefresh.value = true
                                    Toast.makeText(context, "正在刷新目录...", Toast.LENGTH_SHORT).show()
                                    viewModel.onRefresh()
                                }) {
                                    Icon(
                                        imageVector = AppIcons.Refresh,
                                        contentDescription = "刷新目录",
                                    )
                                }
                            }
                            if (!uiState.isInitializing && uiState.activeServer != null) {
                                IconButton(onClick = { viewModel.onSearchActiveChanged(true) }) {
                                    Icon(
                                        imageVector = AppIcons.Search,
                                        contentDescription = "搜索曲库",
                                    )
                                }
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
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
                    // Breadcrumb navigation bar (MD3 AssistChip Strip)
                    DirectoryBreadcrumbStrip(
                        breadcrumbs = uiState.breadcrumbs,
                        currentPath = uiState.currentPath,
                        onBreadcrumbClicked = { viewModel.onBreadcrumbClicked(it) },
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Content Area with Pull-to-Refresh
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(pullRefreshState.nestedScrollConnection)
                            .clipToBounds(),
                ) {
                    when {
                        uiState.isInitializing -> {
                            // Cold-start initialization in progress: wait quietly without flashing NoActiveServerState
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Clean background during the brief cold-start initialization
                            }
                        }

                        uiState.activeServer == null -> {
                            NoActiveServerState(onNavigateToServerManagement = onNavigateToServerManagement)
                        }

                        uiState.isLoading -> {
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
                                onVirtualTrackClick = { album, trackIndex -> viewModel.playVirtualTrack(album, trackIndex) },
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
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
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
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
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
