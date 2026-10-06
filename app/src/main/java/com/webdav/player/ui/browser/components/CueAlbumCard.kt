package com.webdav.player.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.ui.common.CoverThumbnailImage
import com.webdav.player.ui.theme.AppIcons
import java.util.Locale

@Composable
fun CueAlbumCard(
    album: CueAlbumItem,
    metadata: TrackMetadata? = null,
    isAlbumActive: Boolean = false,
    activeTrackId: String? = null,
    isPlaying: Boolean = false,
    onPlayAlbum: () -> Unit,
    onVirtualTrackClick: (Int) -> Unit,
    onToggleExpand: () -> Unit,
    onPlayAsWholeAudio: () -> Unit = onPlayAlbum,
    modifier: Modifier = Modifier,
) {
    val albumWaveState =
        remember(isAlbumActive, isPlaying) {
            EqualizerStateHelper.resolveEqualizerState(
                isActive = isAlbumActive,
                isPlaying = isPlaying,
            )
        }

    val displayTitle =
        remember(album.audioFile?.name, album.cueFile.name, metadata?.title, metadata?.album) {
            metadata?.album?.takeIf { it.isNotBlank() }
                ?: metadata?.title?.takeIf { it.isNotBlank() }
                ?: album.albumTitle
        }

    val displayArtist =
        remember(metadata?.artist, album.tracks) {
            metadata?.artist?.takeIf { it.isNotBlank() }
                ?: album.tracks.firstOrNull { !it.performer.isNullOrBlank() }?.performer
                ?: "CUE 整轨专辑"
        }

    val badgeInfo =
        remember(album.audioFile, metadata) {
            album.audioFile?.resolveBadge(metadata)
        }

    val rotationAngle by animateFloatAsState(
        targetValue = if (album.isExpanded) 90f else 0f,
        label = "cue_album_expand_rotation",
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isAlbumActive) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Album Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover Art / Equalizer Indicator
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverThumbnailImage(
                        thumbnailPath = metadata?.coverThumbnailPath,
                        contentDescription = "专辑封面",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.QueueMusic,
                                    contentDescription = "CUE 专辑",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }

                    if (albumWaveState != EqualizerWaveState.IDLE) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            EqualizerTrackIndicator(waveState = albumWaveState)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Artist, Specs
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isAlbumActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isAlbumActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val trackCountText =
                            if (album.isLoading) {
                                "正在解析分轨..."
                            } else if (album.tracks.isNotEmpty()) {
                                "CUE 分轨 · ${album.tracks.size} 首"
                            } else {
                                "CUE 索引"
                            }
                        Text(
                            text = trackCountText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (badgeInfo != null) {
                            AudioQualityBadgePill(badge = badgeInfo)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Trailing Buttons: Play Album & Expand Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (album.tracks.isNotEmpty()) {
                                onPlayAlbum()
                            } else if (album.audioFile != null) {
                                onPlayAsWholeAudio()
                            }
                        },
                        enabled = album.tracks.isNotEmpty() || album.audioFile != null,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.PlayArrow,
                            contentDescription = "播放整张专辑",
                            tint =
                                if (album.tracks.isNotEmpty() || album.audioFile != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = if (album.isExpanded) "折叠分轨" else "展开分轨",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .rotate(rotationAngle),
                        )
                    }
                }
            }

            // Loading state
            if (album.isLoading) {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            // Error banner
            if (album.errorMessage != null && !album.isLoading) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = album.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    if (album.audioFile != null) {
                        TextButton(onClick = onPlayAsWholeAudio) {
                            Text("作为整轨播放", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Expandable Virtual Tracks List
            AnimatedVisibility(visible = album.isExpanded && album.tracks.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    album.tracks.forEachIndexed { index, track ->
                        val isTrackActive =
                            album.isVirtualTrackActive(
                                activeTrackPath = if (isAlbumActive) album.audioFile?.path else null,
                                activeTrackId = activeTrackId,
                                track = track,
                            )

                        VirtualTrackItemRow(
                            track = track,
                            index = index,
                            isActive = isTrackActive,
                            isPlaying = isPlaying,
                            onClick = { onVirtualTrackClick(index) },
                        )

                        if (index < album.tracks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp, end = 12.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VirtualTrackItemRow(
    track: VirtualTrack,
    index: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackWaveState =
        remember(isActive, isPlaying) {
            EqualizerStateHelper.resolveEqualizerState(
                isActive = isActive,
                isPlaying = isPlaying,
            )
        }

    val durationText =
        remember(track.durationMs) {
            if (track.durationMs > 0L) {
                PlaybackProgress.formatMs(track.durationMs)
            } else {
                null
            }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .background(
                    if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        Color.Transparent
                    },
                ).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Track number (01, 02...)
        val trackNum = if (track.trackNumber > 0) track.trackNumber else index + 1
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = String.format(Locale.US, "%02d", trackNum),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and Performer
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isActive) Modifier.basicMarquee() else Modifier,
            )

            if (!track.performer.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = track.performer,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Dynamic Equalizer Indicator or Duration
        if (trackWaveState != EqualizerWaveState.IDLE) {
            EqualizerTrackIndicator(
                waveState = trackWaveState,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else if (durationText != null) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
