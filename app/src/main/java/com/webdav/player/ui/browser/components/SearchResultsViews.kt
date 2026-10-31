package com.webdav.player.ui.browser.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.LibrarySearchResult
import com.webdav.player.domain.model.RemoteDirectory
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
fun SearchResultsList(
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
