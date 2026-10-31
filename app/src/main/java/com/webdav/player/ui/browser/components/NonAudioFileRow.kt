@file:JvmName("AudioTrackItemRowKt")
@file:JvmMultifileClass

package com.webdav.player.ui.browser.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.ui.common.AudioQualityBadgePill
import com.webdav.player.ui.common.AudioTrackRowDefaults
import com.webdav.player.ui.theme.AppIcons

@Composable
internal fun NonAudioFileRow(
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
    ListItem(
        modifier = modifier.fillMaxWidth().alpha(if (file.isLyrics || file.isCue) 1f else 0.5f),
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
    HorizontalDivider(
        modifier =
            Modifier.padding(
                start = AudioTrackRowDefaults.DividerInsetStart,
                end = AudioTrackRowDefaults.DividerInsetEnd,
            ),
        thickness = AudioTrackRowDefaults.DividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}
