package com.webdav.player.ui.playlist

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webdav.player.domain.model.Playlist
import com.webdav.player.ui.common.FluidTopAppBar
import com.webdav.player.ui.player.MiniPlayerDefaults
import com.webdav.player.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    viewModel: PlaylistListViewModel,
    onPlaylistClick: (Playlist) -> Unit = {},
    modifier: Modifier = Modifier,
    fabBottomPadding: Dp = 80.dp,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FluidTopAppBar(
                title = "歌单",
                subtitle = "共 ${uiState.playlists.size} 个云端歌单",
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = fabBottomPadding),
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = "新建歌单",
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.playlists.isEmpty() -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .padding(bottom = MiniPlayerDefaults.PenetrativeContentBottomPadding),
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
                                text = "暂无歌单",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右下角按钮创建歌单，或在曲库中将喜欢的音乐添加到歌单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = MiniPlayerDefaults.PenetrativeContentBottomPadding,
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            PlaylistItemRow(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                onRename = { viewModel.openRenameDialog(playlist) },
                                onDelete = { viewModel.openDeleteDialog(playlist) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (uiState.showCreateDialog) {
        PlaylistNameInputDialog(
            title = "新建歌单",
            initialName = "",
            confirmLabel = "创建",
            errorMessage = uiState.errorMessage,
            onDismiss = {
                viewModel.closeCreateDialog()
            },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
            },
            onClearError = { viewModel.clearError() },
        )
    }

    // Rename Playlist Dialog
    if (uiState.playlistToRename != null) {
        val playlist = uiState.playlistToRename!!
        PlaylistNameInputDialog(
            title = "重命名歌单",
            initialName = playlist.name,
            confirmLabel = "保存",
            errorMessage = uiState.errorMessage,
            onDismiss = {
                viewModel.closeRenameDialog()
            },
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlist.id, newName)
            },
            onClearError = { viewModel.clearError() },
        )
    }

    // Delete Playlist Dialog
    if (uiState.playlistToDelete != null) {
        val playlist = uiState.playlistToDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteDialog() },
            title = { Text(text = "删除歌单") },
            text = {
                Text(text = "确定要删除歌单 “${playlist.name}” 吗？此操作不会删除远程服务器上的音乐文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeletePlaylist(playlist.id) },
                ) {
                    Text(text = "删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDeleteDialog() }) {
                    Text(text = "取消")
                }
            },
        )
    }
}

@Composable
fun PlaylistItemRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    val dateFormatted =
        remember(playlist.updatedAtMs) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(playlist.updatedAtMs))
        }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${playlist.trackCount} 首",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "更新于 $dateFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
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
                            text = { Text("重命名") },
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除歌单", color = MaterialTheme.colorScheme.error) },
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
                                onDelete()
                            },
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
fun PlaylistNameInputDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onClearError()
                    },
                    label = { Text("歌单名称") },
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}
