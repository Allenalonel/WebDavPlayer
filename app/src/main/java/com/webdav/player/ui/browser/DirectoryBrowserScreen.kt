package com.webdav.player.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.ui.browser.components.AudioTrackItemRow
import com.webdav.player.ui.browser.components.DirectoryBreadcrumbStrip
import com.webdav.player.ui.browser.components.DirectoryItemRow
import com.webdav.player.ui.browser.components.EmptyFolderState
import com.webdav.player.ui.browser.components.ErrorState
import com.webdav.player.ui.browser.components.NoActiveServerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowserScreen(
    viewModel: DirectoryBrowserViewModel,
    onNavigateToServerManagement: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTab: Boolean = true,
    onFileClicked: (RemoteFile) -> Unit = { viewModel.onAudioTrackClicked(it) },
    @Suppress("UNUSED_PARAMETER") onOpenFullPlayer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = isCurrentTab) {
        val handled = viewModel.onNavigateUp()
        if (!handled) {
            onNavigateToServerManagement()
        }
    }

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.onRefresh()
        }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) {
            pullRefreshState.endRefresh()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val titleText = if (uiState.isInitializing) {
                            "媒体库"
                        } else if (uiState.currentPath == "/") {
                            uiState.activeServer?.name ?: "远程目录"
                        } else {
                            uiState.currentDirectory?.name
                                ?: uiState.currentPath.trimEnd('/').substringAfterLast('/')
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!uiState.isInitializing) {
                            Text(
                                text = uiState.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.canNavigateUp && !uiState.isInitializing) {
                        IconButton(onClick = { viewModel.onNavigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回上级"
                            )
                        }
                    }
                },
                actions = {
                    if (!uiState.isInitializing) {
                        IconButton(onClick = { viewModel.onRefresh() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "刷新目录"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isInitializing && uiState.activeServer != null) {
                // Breadcrumb navigation bar (MD3 AssistChip Strip)
                DirectoryBreadcrumbStrip(
                    breadcrumbs = uiState.breadcrumbs,
                    currentPath = uiState.currentPath,
                    onBreadcrumbClicked = { viewModel.onBreadcrumbClicked(it) }
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Content Area with Pull-to-Refresh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
                    .clipToBounds()
            ) {
                when {
                    uiState.isInitializing -> {
                        // Cold-start initialization in progress: wait quietly without flashing NoActiveServerState
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
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
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "正在加载远程目录...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    uiState.errorMessage != null -> {
                        ErrorState(
                            message = uiState.errorMessage ?: "加载失败",
                            onRetry = { viewModel.onRetry() },
                            onGoBack = { viewModel.onNavigateUp() },
                            canGoBack = uiState.canNavigateUp
                        )
                    }

                    uiState.isEmpty -> {
                        EmptyFolderState(onRefresh = { viewModel.onRefresh() })
                    }

                    else -> {
                        DirectoryContentList(
                            directories = uiState.subDirectories,
                            files = uiState.files,
                            metadataMap = uiState.metadataMap,
                            onDirectoryClicked = { viewModel.onDirectoryClicked(it) },
                            onFileClicked = onFileClicked,
                            onPlayNext = { viewModel.playNext(it) }
                        )
                    }
                }

                if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) {
                    PullToRefreshContainer(
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
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
    onDirectoryClicked: (RemoteDirectory) -> Unit,
    onFileClicked: (RemoteFile) -> Unit,
    onPlayNext: (RemoteFile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
    ) {
        // Subdirectories first (Elevated MD3 Cards)
        items(directories, key = { "dir_${it.path}" }) { dir ->
            DirectoryItemRow(
                directory = dir,
                onClick = { onDirectoryClicked(dir) }
            )
        }

        // Space between folders and files if both exist
        if (directories.isNotEmpty() && files.isNotEmpty()) {
            item(key = "divider_folder_files") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Files next (MD3 ListItems)
        items(files, key = { "file_${it.path}" }) { file ->
            val metadata = metadataMap[file.path]
            AudioTrackItemRow(
                file = file,
                metadata = metadata,
                onClick = { onFileClicked(file) },
                onPlayNext = { onPlayNext(file) }
            )
        }
    }
}
