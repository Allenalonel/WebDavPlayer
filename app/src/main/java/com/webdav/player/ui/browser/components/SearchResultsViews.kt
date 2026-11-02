package com.webdav.player.ui.browser.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.LibrarySearchResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.AudioTrackRowDefaults
import com.webdav.player.ui.common.TrackTagPill
import com.webdav.player.ui.common.TrackThumbnailLeading
import com.webdav.player.ui.theme.AppIcons

/**
 * Material 3 Native Expressive Search Content View.
 * Implements clean MD3 ListItems, single-action row play next, and vertical history list.
 */
@Composable
fun SearchResultsContent(
    query: String,
    isSearching: Boolean,
    searchResult: LibrarySearchResult,
    searchHistory: List<String>,
    onSelectHistoryQuery: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onDirectoryClick: (RemoteDirectory) -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cleanQuery = query.trim()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            cleanQuery.isEmpty() -> {
                SearchHistorySection(
                    history = searchHistory,
                    onSelectQuery = onSelectHistoryQuery,
                    onClearHistory = onClearHistory,
                    onRemoveHistoryItem = onRemoveHistoryItem,
                )
            }

            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在检索曲库快照...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            searchResult.isEmpty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "未找到匹配的目录或歌曲",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "仅检索已缓存的目录快照，尝试输入其他关键词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                SearchResultsList(
                    searchResult = searchResult,
                    onDirectoryClick = onDirectoryClick,
                    onTrackClick = onTrackClick,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
        }
    }
}

@Composable
internal fun SearchResultsList(
    searchResult: LibrarySearchResult,
    onDirectoryClick: (RemoteDirectory) -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        searchDirectoriesSection(
            directories = searchResult.matchedDirectories,
            onDirectoryClick = onDirectoryClick,
        )
        searchTracksSection(
            tracks = searchResult.matchedTracks,
            onTrackClick = onTrackClick,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}

@Composable
internal fun SearchHistorySection(
    history: List<String>,
    onSelectQuery: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = AppIcons.History,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "输入歌曲、歌手、专辑或目录名进行搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "header_recent") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "最近搜索",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(
                            text = "全部清空",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            items(history, key = { "history_$it" }) { query ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = query,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = AppIcons.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { onRemoveHistoryItem(query) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "删除记录",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    modifier = Modifier.clickable { onSelectQuery(query) },
                )
            }
        }
    }
}

@Composable
internal fun SearchSectionHeader(
    title: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = containerColor,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun SearchDirectoryItem(
    directory: RemoteDirectory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = directory.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = directory.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

internal fun LazyListScope.searchDirectoriesSection(
    directories: List<RemoteDirectory>,
    onDirectoryClick: (RemoteDirectory) -> Unit,
) {
    if (directories.isEmpty()) return
    item(key = "header_directories") {
        SearchSectionHeader(
            title = "文件夹目录 (${directories.size})",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    items(directories, key = { "dir_${it.path}" }) { dir ->
        SearchDirectoryItem(directory = dir, onClick = { onDirectoryClick(dir) })
    }
}

internal fun resolveSearchTrackSubtitle(track: AudioTrack): String {
    val artist = track.artist?.ifBlank { "未知艺术家" } ?: "未知艺术家"
    val extra =
        if (track.isVirtualTrack) {
            track.fileName.ifEmpty { track.remotePath.substringAfterLast('/') }.takeIf { it.isNotBlank() }
        } else {
            null
        }
    return AudioTrackRowDefaults.formatSubtitle(
        artist = artist,
        album = track.album,
        extra = extra,
    ) ?: artist
}

@Composable
internal fun CueTagBadge(modifier: Modifier = Modifier) =
    TrackTagPill(
        text = "CUE",
        modifier = modifier,
    )

@Composable
internal fun SearchTrackItem(
    track: AudioTrack,
    onClick: () -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    AudioTrackRow(
        title = track.title,
        subtitle = resolveSearchTrackSubtitle(track),
        modifier = modifier,
        coverThumbnailPath = track.coverThumbnailPath,
        audioQualityBadge = track.qualityBadge,
        leadingContent = {
            TrackThumbnailLeading(
                coverThumbnailPath = track.coverThumbnailPath,
                fallbackIcon = if (track.isVirtualTrack) AppIcons.QueueMusic else AppIcons.Audiotrack,
            )
        },
        tagContent =
            if (track.isVirtualTrack) {
                { CueTagBadge() }
            } else {
                null
            },
        onClick = onClick,
        actionsMenu = { dismiss ->
            DropdownMenuItem(
                text = { Text("添加到歌单") },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    dismiss()
                    onAddToPlaylist(track)
                },
            )
        },
    )
}

@Composable
internal fun SearchAudioTrackItem(
    track: AudioTrack,
    onClick: () -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier,
) = SearchTrackItem(
    track = track,
    onClick = onClick,
    onAddToPlaylist = onAddToPlaylist,
    modifier = modifier,
)

internal fun LazyListScope.searchTracksSection(
    tracks: List<AudioTrack>,
    onTrackClick: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    item(key = "header_tracks") {
        SearchSectionHeader(
            title = "单曲与分轨 (${tracks.size})",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    items(tracks, key = { "track_${it.id}" }) { track ->
        SearchTrackItem(
            track = track,
            onClick = { onTrackClick(track) },
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
