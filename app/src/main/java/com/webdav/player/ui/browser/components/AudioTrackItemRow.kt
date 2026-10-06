package com.webdav.player.ui.browser.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.ui.common.CoverThumbnailImage
import com.webdav.player.ui.theme.AppIcons

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioTrackItemRow(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val isAudio = file.isAudio
    val isLyrics = file.isLyrics
    val isCue = file.isCue
    val isOther = !isAudio && !isLyrics && !isCue

    val displayTitle =
        remember(file.name, metadata?.title) {
            metadata?.displayTitle(file.name) ?: file.name
        }
    val displayArtist =
        remember(metadata?.artist) {
            metadata?.displayArtist()
        }
    val displayAlbum =
        remember(metadata?.album) {
            metadata?.album?.takeIf { it.isNotBlank() }
        }
    val durationStr =
        remember(metadata?.durationMs) {
            if ((metadata?.durationMs ?: 0L) > 0L) {
                PlaybackProgress.formatMs(metadata?.durationMs ?: 0L)
            } else {
                null
            }
        }
    val fallbackSubtitle =
        remember(displayArtist, durationStr, file.size, file.lastModified) {
            if (displayArtist == null) {
                val subtitleParts = mutableListOf<String>()
                if (durationStr != null) subtitleParts.add(durationStr)
                if (file.size > 0) subtitleParts.add(formatFileSize(file.size))
                if (file.lastModified != null && subtitleParts.isEmpty()) subtitleParts.add(file.lastModified)
                subtitleParts.joinToString(" · ").ifEmpty { "音频文件" }
            } else {
                null
            }
        }

    val badgeInfo =
        remember(file, metadata) {
            file.resolveBadge(metadata)
        }

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

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = isAudio) { onClick() }
                .alpha(if (isOther) 0.5f else 1f),
        leadingContent = {
            when {
                isAudio -> {
                    Box(
                        modifier =
                            Modifier
                                .size(46.dp)
                                .clip(MaterialTheme.shapes.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        CoverThumbnailImage(
                            thumbnailPath = metadata?.coverThumbnailPath,
                            contentDescription = "封面",
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = AppIcons.Audiotrack,
                                        contentDescription = "音频",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        val waveState =
                            EqualizerStateHelper.resolveEqualizerState(
                                isActive = isActive,
                                isPlaying = isPlaying,
                            )
                        if (waveState != EqualizerWaveState.IDLE) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                EqualizerTrackIndicator(waveState = waveState)
                            }
                        }
                    }
                }

                isLyrics -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.Description,
                                contentDescription = "歌词",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                isCue -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.QueueMusic,
                                contentDescription = "CUE 分轨",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                else -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.InsertDriveFile,
                                contentDescription = "其他文件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        },
        headlineContent = {
            Text(
                text = displayTitle,
                modifier = if (isAudio) Modifier.basicMarquee() else Modifier,
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight =
                    if (isActive) {
                        FontWeight.Bold
                    } else if (isAudio) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                maxLines = 1,
                overflow = if (isAudio) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (displayArtist != null) {
                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (displayAlbum != null) {
                        Text(
                            text = " · $displayAlbum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (durationStr != null) {
                        Text(
                            text = " · $durationStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = fallbackSubtitle ?: "音频文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Quality Badge Pill
                if (badgeInfo != null) {
                    AudioQualityBadgePill(badge = badgeInfo)
                }

                // Secondary Action Menu ("...")
                if (isAudio) {
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
                                text = { Text("下一首播放") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onPlayNext()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("查看详细信息") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showInfoDialog = true
                                },
                            )
                        }
                    }
                }
            }
        },
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (isActive) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    },
            ),
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

/**
 * Backward compatibility alias for AudioTrackItemRow
 */
@Composable
fun FileItemRow(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AudioTrackItemRow(
        file = file,
        metadata = metadata,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onPlayNext = onPlayNext,
        modifier = modifier,
    )
}

@Composable
fun EqualizerTrackIndicator(
    waveState: EqualizerWaveState,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (waveState == EqualizerWaveState.IDLE) return
    val isPlaying = waveState == EqualizerWaveState.PLAYING
    val transition = rememberInfiniteTransition(label = "equalizer_bars")

    val bar1Height by transition.animateFloat(
        initialValue = EqualizerStateHelper.MinAnimatedBarHeight.value,
        targetValue = 18f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "bar1_height",
    )

    val bar2Height by transition.animateFloat(
        initialValue = 18f,
        targetValue = 5f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 580, delayMillis = 100, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "bar2_height",
    )

    val bar3Height by transition.animateFloat(
        initialValue = 6f,
        targetValue = EqualizerStateHelper.MaxAnimatedBarHeight.value,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 500, delayMillis = 60, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "bar3_height",
    )

    val (h1, h2, h3) =
        if (isPlaying) {
            Triple(bar1Height.dp, bar2Height.dp, bar3Height.dp)
        } else {
            Triple(
                EqualizerStateHelper.FrozenBar1Height,
                EqualizerStateHelper.FrozenBar2Height,
                EqualizerStateHelper.FrozenBar3Height,
            )
        }

    Row(
        modifier =
            modifier
                .height(EqualizerStateHelper.MaxAnimatedBarHeight)
                .semantics {
                    contentDescription = if (isPlaying) "正在播放音轨" else "已暂停音轨"
                },
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        EqualizerBar(height = h1, color = barColor)
        EqualizerBar(height = h2, color = barColor)
        EqualizerBar(height = h3, color = barColor)
    }
}

@Composable
fun EqualizerTrackIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    EqualizerTrackIndicator(
        waveState = if (isPlaying) EqualizerWaveState.PLAYING else EqualizerWaveState.PAUSED,
        modifier = modifier,
        barColor = barColor,
    )
}

@Composable
private fun EqualizerBar(
    height: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(3.dp)
                .height(height)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color),
    )
}

@Composable
fun AudioQualityBadgePill(
    badge: AudioQualityBadge,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) =
        when (badge.qualityLevel) {
            AudioQualityLevel.LOSSLESS -> {
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            }

            AudioQualityLevel.HIGH_QUALITY -> {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }

            AudioQualityLevel.STANDARD -> {
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            }

            AudioQualityLevel.COMPRESSED -> {
                MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = badge.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
        )
    }
}

/**
 * Backward compatibility alias for AudioBadge
 */
@Composable
fun AudioBadge(
    format: AudioFormat,
    modifier: Modifier = Modifier,
) {
    val badge =
        AudioQualityBadge(
            label = format.extension.uppercase(),
            format = format,
            isLossless = format == AudioFormat.FLAC || format == AudioFormat.WAV,
            qualityLevel =
                when (format) {
                    AudioFormat.FLAC, AudioFormat.WAV -> AudioQualityLevel.LOSSLESS
                    AudioFormat.MP3 -> AudioQualityLevel.HIGH_QUALITY
                    AudioFormat.WMA -> AudioQualityLevel.COMPRESSED
                    else -> AudioQualityLevel.STANDARD
                },
        )
    AudioQualityBadgePill(badge = badge, modifier = modifier)
}

@Composable
fun LyricBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = "LRC",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun OtherBadge(
    extension: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = extension.take(4),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
