package com.webdav.player.ui.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.ui.common.AudioQualityBadgePill
import com.webdav.player.ui.common.AudioTrackRow
import com.webdav.player.ui.common.AudioTrackRowDefaults
import com.webdav.player.ui.theme.AppIcons
import java.util.Locale

@Composable
fun AudioTrackItemRow(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
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
            remember(metadata?.durationMs, file.size) {
                val d = metadata?.durationMs ?: 0L
                if (d > 0L) {
                    PlaybackProgress.formatMs(d)
                } else if (file.size > 0L) {
                    val bitrateKbps = badgeInfo?.estimatedBitrateKbps ?: 320
                    val estMs = (file.size * 8000L) / (bitrateKbps * 1000L)
                    if (estMs > 0L) PlaybackProgress.formatMs(estMs) else null
                } else {
                    null
                }
            }
        val surfaceColor =
            if (isActive) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
        ) {
            AudioTrackRow(
                title = displayTitle,
                subtitle = subtitle,
                durationText = durationStr,
                coverThumbnailPath = metadata?.coverThumbnailPath,
                audioQualityBadge = badgeInfo,
                isActive = isActive,
                isPlaying = isPlaying,
                containerColor = Color.Transparent,
                onClick = onClick,
                onLongClick = onLongClick,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
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
        }
    } else {
        NonAudioFileRow(file = file, badgeInfo = badgeInfo, modifier = modifier)
    }
}

@Composable
private fun NonAudioFileRow(
    file: RemoteFile,
    badgeInfo: AudioQualityBadge?,
    modifier: Modifier = Modifier,
) {
    val (icon, label) =
        when {
            file.isLyrics -> AppIcons.Description to "歌词"
            file.isCue -> AppIcons.QueueMusic to "CUE 分轨"
            else -> AppIcons.InsertDriveFile to "其他文件"
        }
    val (containerColor, contentColor) =
        if (file.isLyrics || file.isCue) {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth().alpha(if (file.isLyrics || file.isCue) 1f else 0.5f),
            leadingContent = {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = containerColor,
                    modifier = Modifier.size(AudioTrackRowDefaults.ThumbnailSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(AudioTrackRowDefaults.LeadingIconSize),
                        )
                    }
                }
            },
            headlineContent = {
                Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                val sub = if (file.size > 0) formatFileSize(file.size) else file.lastModified
                if (!sub.isNullOrBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = { badgeInfo?.let { AudioQualityBadgePill(it) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun FileInfoDialog(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    badgeInfo: AudioQualityBadge? = null,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
) {
    val displayTitle = metadata?.displayTitle(file.name) ?: file.name
    val qualitySummary =
        remember(file, metadata) {
            file.formatQualitySummary(metadata)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "音频详情",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    InfoRow(label = "曲目时长", value = PlaybackProgress.formatMs(metadata!!.durationMs))
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
        },
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
    return "未知"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val unitIndex = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, unitIndex.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
