@file:JvmName("AudioTrackItemRowKt")
@file:JvmMultifileClass

package com.webdav.player.ui.browser.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.AudioTrackRowDefaults
import com.webdav.player.ui.theme.AppIcons

@Composable
fun AudioTrackItemRow(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    val badgeInfo = remember(file, metadata) { file.resolveBadge(metadata) }

    if (showInfoDialog) {
        FileInfoDialog(
            file = file,
            metadata = metadata,
            badgeInfo = badgeInfo,
            onDismiss = { showInfoDialog = false },
            onPlay = {
                showInfoDialog = false
                onClick()
            },
        )
    }

    if (file.isAudio) {
        val displayTitle = remember(file.name, metadata?.title) { metadata?.displayTitle(file.name) ?: file.name }
        val subtitle = remember(file, metadata) { resolveAudioSubtitle(file, metadata) }
        val durationStr =
            remember(metadata?.durationMs) {
                val d = metadata?.durationMs ?: 0L
                if (d > 0L) PlaybackProgress.formatMs(d) else null
            }
        AudioTrackRow(
            title = displayTitle,
            subtitle = subtitle,
            modifier = modifier,
            durationText = durationStr,
            coverThumbnailPath = metadata?.coverThumbnailPath,
            audioQualityBadge = badgeInfo,
            isActive = isActive,
            isPlaying = isPlaying,
            onClick = onClick,
            showDivider = true,
            actionsMenu = { dismiss ->
                DropdownMenuItem(
                    text = { Text("下一首播放") },
                    leadingIcon = {
                        Icon(AppIcons.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        dismiss()
                        onPlayNext()
                    },
                )
                DropdownMenuItem(
                    text = { Text("添加到歌单") },
                    leadingIcon = {
                        Icon(AppIcons.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        dismiss()
                        onAddToPlaylist()
                    },
                )
                DropdownMenuItem(
                    text = { Text("查看详细信息") },
                    leadingIcon = {
                        Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        dismiss()
                        showInfoDialog = true
                    },
                )
            },
        )
    } else {
        NonAudioFileRow(file = file, badgeInfo = badgeInfo, modifier = modifier)
    }
}

internal fun resolveAudioSubtitle(
    file: RemoteFile,
    metadata: TrackMetadata?,
): String {
    val artist = metadata?.displayArtist()
    val album = metadata?.album?.takeIf { it.isNotBlank() }
    val formatted = AudioTrackRowDefaults.formatSubtitle(artist = artist, album = album)
    if (!formatted.isNullOrBlank()) return formatted
    if (file.size > 0) return formatFileSize(file.size)
    return file.lastModified?.takeIf { it.isNotBlank() } ?: "音频文件"
}
