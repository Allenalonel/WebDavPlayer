package com.webdav.player.ui.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.ui.theme.AppIcons
import java.util.Locale

@Composable
fun FileInfoDialog(
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

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val unitIndex = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, unitIndex.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
