package com.webdav.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.ui.theme.WebDavPlayerTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioTrackRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    durationText: String? = null,
    coverThumbnailPath: String? = null,
    audioQualityBadge: AudioQualityBadge? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    actionsMenu: (@Composable (() -> Unit) -> Unit)? = null,
    tagContent: (@Composable () -> Unit)? = null,
    containerColor: Color? = null,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            leadingContent = {
                if (leadingContent != null) {
                    leadingContent()
                } else {
                    TrackThumbnailLeading(
                        coverThumbnailPath = coverThumbnailPath,
                        isActive = isActive,
                        isPlaying = isPlaying,
                    )
                }
            },
            headlineContent = {
                val marqueeMod = if (isActive) Modifier.basicMarquee() else Modifier
                val titleColor = AudioTrackRowDefaults.resolveTitleColor(isActive, MaterialTheme.colorScheme)
                val titleWeight = AudioTrackRowDefaults.resolveTitleFontWeight(isActive)
                val overflow = if (isActive) TextOverflow.Clip else TextOverflow.Ellipsis
                if (tagContent != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            modifier = marqueeMod.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            fontWeight = titleWeight,
                            maxLines = 1,
                            overflow = overflow,
                        )
                        Spacer(modifier = Modifier.width(AudioTrackRowDefaults.TagSpacing))
                        tagContent()
                    }
                } else {
                    Text(
                        text = title,
                        modifier = marqueeMod,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        fontWeight = titleWeight,
                        maxLines = 1,
                        overflow = overflow,
                    )
                }
            },
            supportingContent = {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            },
            trailingContent = {
                if (trailingContent != null) {
                    trailingContent()
                } else {
                    AudioTrackTrailing(
                        durationText = durationText,
                        audioQualityBadge = audioQualityBadge,
                        actionsMenu = actionsMenu,
                    )
                }
            },
            colors =
                ListItemDefaults.colors(
                    containerColor =
                        containerColor
                            ?: AudioTrackRowDefaults.resolveContainerColor(isActive, MaterialTheme.colorScheme),
                ),
        )
        if (showDivider) {
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
    }
}

private val PreviewFlacBadge =
    AudioQualityBadge("FLAC", AudioFormat.FLAC, isLossless = true, qualityLevel = AudioQualityLevel.LOSSLESS)
private val PreviewHiResBadge =
    AudioQualityBadge("Hi-Res", AudioFormat.FLAC, isLossless = true, qualityLevel = AudioQualityLevel.LOSSLESS)

@Preview(name = "Idle Track", showBackground = true)
@Composable
fun AudioTrackRowIdlePreview() =
    WebDavPlayerTheme {
        AudioTrackRow(
            title = "Comfortably Numb",
            subtitle = "Pink Floyd · The Wall",
            durationText = "06:22",
            audioQualityBadge = PreviewFlacBadge,
        )
    }

@Preview(name = "Active Playing Track", showBackground = true)
@Composable
fun AudioTrackRowActivePlayingPreview() =
    WebDavPlayerTheme {
        AudioTrackRow(
            title = "Time (2023 Remastered Edition)",
            subtitle = "Pink Floyd · The Dark Side of the Moon",
            durationText = "07:05",
            audioQualityBadge = PreviewHiResBadge,
            isActive = true,
            isPlaying = true,
        )
    }

@Preview(name = "Active Paused Track", showBackground = true)
@Composable
fun AudioTrackRowActivePausedPreview() =
    WebDavPlayerTheme {
        AudioTrackRow(
            title = "Money",
            subtitle = "Pink Floyd · The Dark Side of the Moon",
            durationText = "06:22",
            audioQualityBadge = PreviewFlacBadge,
            isActive = true,
            isPlaying = false,
        )
    }

@Preview(name = "Without Cover Track", showBackground = true)
@Composable
fun AudioTrackRowWithoutCoverPreview() =
    WebDavPlayerTheme {
        AudioTrackRow(
            title = "Acoustic Session",
            subtitle = "Unknown Artist",
            durationText = "03:45",
            coverThumbnailPath = null,
        )
    }
