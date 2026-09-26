package com.webdav.player.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.ui.common.CoverThumbnailImage
import com.webdav.player.ui.player.PlayerTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowserScreen(
    viewModel: DirectoryBrowserViewModel,
    onNavigateToServerManagement: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTab: Boolean = true,
    onFileClicked: (RemoteFile) -> Unit = { viewModel.onAudioTrackClicked(it) },
    onOpenFullPlayer: () -> Unit = {}
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
                        val titleText = if (uiState.currentPath == "/") {
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
                        Text(
                            text = uiState.currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.canNavigateUp) {
                        IconButton(onClick = { viewModel.onNavigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回上级"
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateToServerManagement) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = "服务器列表"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onRefresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新目录"
                        )
                    }
                    IconButton(onClick = onNavigateToServerManagement) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = "管理服务器"
                        )
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
            // Breadcrumb navigation bar (MD3 AssistChip Strip)
            DirectoryBreadcrumbStrip(
                breadcrumbs = uiState.breadcrumbs,
                currentPath = uiState.currentPath,
                onBreadcrumbClicked = { viewModel.onBreadcrumbClicked(it) }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Content Area with Pull-to-Refresh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                when {
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

                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
fun DirectoryBreadcrumbStrip(
    breadcrumbs: List<Breadcrumb>,
    currentPath: String,
    onBreadcrumbClicked: (Breadcrumb) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(breadcrumbs, breadcrumbs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        breadcrumbs.forEachIndexed { index, breadcrumb ->
            val isTail = index == breadcrumbs.lastIndex ||
                    breadcrumb.path == currentPath ||
                    breadcrumb.path.trimEnd('/') == currentPath.trimEnd('/')

            AssistChip(
                onClick = { onBreadcrumbClicked(breadcrumb) },
                label = {
                    Text(
                        text = breadcrumb.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isTail) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    if (index == 0) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "根目录",
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "目录",
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                },
                colors = if (isTail) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        leadingIconContentColor = MaterialTheme.colorScheme.outline
                    )
                },
                border = if (isTail) {
                    AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                } else {
                    AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                },
                shape = RoundedCornerShape(8.dp)
            )

            if (index < breadcrumbs.lastIndex) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

/**
 * Backward compatibility alias for DirectoryBreadcrumbStrip
 */
@Composable
fun BreadcrumbBar(
    breadcrumbs: List<Breadcrumb>,
    currentPath: String,
    onBreadcrumbClicked: (Breadcrumb) -> Unit,
    modifier: Modifier = Modifier
) {
    DirectoryBreadcrumbStrip(
        breadcrumbs = breadcrumbs,
        currentPath = currentPath,
        onBreadcrumbClicked = onBreadcrumbClicked,
        modifier = modifier
    )
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
            FileItemRow(
                file = file,
                metadata = metadata,
                onClick = { onFileClicked(file) },
                onPlayNext = { onPlayNext(file) }
            )
        }
    }
}

@Composable
fun DirectoryItemRow(
    directory: RemoteDirectory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "文件夹",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directory.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val hint = when {
                    directory.audioFiles.isNotEmpty() -> "${directory.audioFiles.size} 首歌曲"
                    (directory.subDirectories.size + directory.files.size) > 0 -> "${directory.subDirectories.size + directory.files.size} 项内容"
                    else -> "文件夹"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "进入目录",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun FileItemRow(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val isAudio = file.isAudio
    val isLyrics = file.isLyrics
    val isOther = !isAudio && !isLyrics

    val displayTitle = metadata?.displayTitle(file.name) ?: file.name
    val displayArtist = metadata?.displayArtist()
    val displayAlbum = metadata?.album?.takeIf { it.isNotBlank() }
    val durationStr = if ((metadata?.durationMs ?: 0L) > 0L) {
        PlayerTimeFormatter.formatMs(metadata?.durationMs ?: 0L)
    } else null

    val badgeInfo = remember(file, metadata) {
        AudioQualityBadgeHelper.getBadge(file, metadata)
    }

    if (showInfoDialog) {
        FileInfoDialog(
            file = file,
            metadata = metadata,
            badgeInfo = badgeInfo,
            onDismiss = { showInfoDialog = false },
            onPlay = {
                showInfoDialog = false
                onClick()
            }
        )
    }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isAudio) { onClick() }
            .alpha(if (isOther) 0.5f else 1f),
        leadingContent = {
            when {
                isAudio -> {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CoverThumbnailImage(
                            thumbnailPath = metadata?.coverThumbnailPath,
                            contentDescription = "封面",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Audiotrack,
                                        contentDescription = "音频",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                isLyrics -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = "歌词",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = "其他文件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        headlineContent = {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isAudio) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (displayArtist != null) {
                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (displayAlbum != null) {
                        Text(
                            text = " · $displayAlbum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (durationStr != null) {
                        Text(
                            text = " · $durationStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val subtitleParts = mutableListOf<String>()
                    if (durationStr != null) subtitleParts.add(durationStr)
                    if (file.size > 0) subtitleParts.add(formatFileSize(file.size))
                    if (file.lastModified != null && subtitleParts.isEmpty()) subtitleParts.add(file.lastModified)
                    Text(
                        text = subtitleParts.joinToString(" · ").ifEmpty { "音频文件" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Quality Badge Pill
                if (badgeInfo != null) {
                    AudioQualityBadgePill(badge = badgeInfo)
                }

                // Secondary Action Menu ("...")
                if (isAudio) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "更多操作",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("下一首播放") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onPlayNext()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("查看详细信息") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showInfoDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
fun AudioQualityBadgePill(
    badge: AudioQualityBadge,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (badge.qualityLevel) {
        AudioQualityLevel.LOSSLESS -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }
        AudioQualityLevel.HIGH_QUALITY -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        AudioQualityLevel.STANDARD -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
        AudioQualityLevel.COMPRESSED -> {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = badge.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
        )
    }
}

@Composable
fun FileInfoDialog(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    badgeInfo: AudioQualityBadge? = null,
    onDismiss: () -> Unit,
    onPlay: () -> Unit
) {
    val displayTitle = metadata?.displayTitle(file.name) ?: file.name
    val qualitySummary = remember(file, metadata) {
        AudioQualityBadgeHelper.formatQualitySummary(file, metadata)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "音频详情",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow(label = "歌曲标题", value = displayTitle)
                if (!metadata?.artist.isNullOrBlank()) {
                    InfoRow(label = "艺术家", value = metadata!!.artist!!)
                }
                if (!metadata?.album.isNullOrBlank()) {
                    InfoRow(label = "专辑名称", value = metadata!!.album!!)
                }
                InfoRow(label = "音频格式", value = qualitySummary)
                if (badgeInfo?.estimatedBitrateKbps != null) {
                    InfoRow(label = "预估码率", value = "${badgeInfo.estimatedBitrateKbps} kbps")
                }
                if ((metadata?.durationMs ?: 0L) > 0L) {
                    InfoRow(label = "曲目时长", value = PlayerTimeFormatter.formatMs(metadata!!.durationMs))
                }
                if (file.size > 0L) {
                    InfoRow(label = "文件大小", value = formatFileSize(file.size))
                }
                InfoRow(label = "文件名称", value = file.name)
                InfoRow(label = "远程路径", value = file.path)
            }
        },
        confirmButton = {
            if (file.isAudio) {
                Button(onClick = onPlay) {
                    Text("立即播放")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Backward compatibility alias for AudioBadge
 */
@Composable
fun AudioBadge(format: AudioFormat, modifier: Modifier = Modifier) {
    val badge = AudioQualityBadge(
        label = format.extension.uppercase(),
        format = format,
        isLossless = format == AudioFormat.FLAC || format == AudioFormat.WAV,
        qualityLevel = when (format) {
            AudioFormat.FLAC, AudioFormat.WAV -> AudioQualityLevel.LOSSLESS
            AudioFormat.MP3 -> AudioQualityLevel.HIGH_QUALITY
            AudioFormat.WMA -> AudioQualityLevel.COMPRESSED
            else -> AudioQualityLevel.STANDARD
        }
    )
    AudioQualityBadgePill(badge = badge, modifier = modifier)
}

@Composable
fun LyricBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "LRC",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun OtherBadge(extension: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = extension.take(4),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun EmptyFolderState(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "当前目录为空",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "此远程目录下没有找到子目录或文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("重新刷新")
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
    canGoBack: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "无法访问该目录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canGoBack) {
                OutlinedButton(onClick = onGoBack) {
                    Text("返回上级")
                }
            }
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
fun NoActiveServerState(
    onNavigateToServerManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Cloud,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "未选定活跃 WebDAV 服务器",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "请前往服务器管理页面选择或添加一个服务器",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToServerManagement) {
            Text("前往服务器管理")
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val unitIndex = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, unitIndex.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
