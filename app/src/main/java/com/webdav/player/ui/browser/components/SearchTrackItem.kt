package com.webdav.player.ui.browser.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.AudioTrackRowDefaults
import com.webdav.player.ui.common.TrackTagPill
import com.webdav.player.ui.common.TrackThumbnailLeading
import com.webdav.player.ui.theme.AppIcons

fun resolveSearchTrackSubtitle(track: AudioTrack): String {
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
fun CueTagBadge(modifier: Modifier = Modifier) =
    TrackTagPill(
        text = "CUE",
        modifier = modifier,
    )

@Composable
fun SearchTrackItem(
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
fun SearchAudioTrackItem(
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

fun LazyListScope.searchTracksSection(
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
