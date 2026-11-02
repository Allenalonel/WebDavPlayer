package com.webdav.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.ui.theme.AppIcons
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
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    actionsMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    tagContent: (@Composable () -> Unit)? = null,
    containerColor: Color? = null,
    showDivider: Boolean = false,
) {
    val clickModifier =
        if (onLongClick != null) {
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
        } else {
            Modifier.clickable(onClick = onClick)
        }

    val resolvedContainerColor =
        when {
            isSelectionMode && isSelected -> MaterialTheme.colorScheme.surfaceContainerHighest
            containerColor != null -> containerColor
            else -> AudioTrackRowDefaults.resolveContainerColor(isActive, MaterialTheme.colorScheme)
        }

    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.fillMaxWidth().then(clickModifier),
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (leadingContent != null) {
                        leadingContent()
                    } else {
                        TrackThumbnailLeading(
                            coverThumbnailPath = coverThumbnailPath,
                            isActive = isActive,
                            isPlaying = isPlaying,
                        )
                    }
                }
            },
            headlineContent = {
                val marqueeMod = if (isActive) Modifier.basicMarquee() else Modifier
                val titleColor = AudioTrackRowDefaults.resolveTitleColor(isActive, MaterialTheme.colorScheme)
                val titleWeight = AudioTrackRowDefaults.resolveTitleFontWeight(isActive)
                val overflow = if (isActive) TextOverflow.Clip else TextOverflow.Ellipsis
                val renderTitle: @Composable (Modifier) -> Unit = { mod ->
                    Text(
                        text = title,
                        modifier = mod,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        fontWeight = titleWeight,
                        maxLines = 1,
                        overflow = overflow,
                    )
                }
                if (tagContent != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        renderTitle(marqueeMod.weight(1f, fill = false))
                        Spacer(modifier = Modifier.width(AudioTrackRowDefaults.TagSpacing))
                        tagContent()
                    }
                } else {
                    renderTitle(marqueeMod)
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
            trailingContent =
                trailingContent ?: {
                    AudioTrackTrailing(
                        durationText = durationText,
                        audioQualityBadge = audioQualityBadge,
                        actionsMenu = if (isSelectionMode) null else actionsMenu,
                    )
                },
            colors =
                ListItemDefaults.colors(
                    containerColor = resolvedContainerColor,
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

@Composable
fun TrackThumbnailLeading(
    coverThumbnailPath: String? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = AudioTrackRowDefaults.ThumbnailSize,
    fallbackIcon: ImageVector = AppIcons.Audiotrack,
    contentDescription: String? = "封面",
) {
    Box(
        modifier = modifier.size(size).clip(MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        CoverThumbnailImage(
            thumbnailPath = coverThumbnailPath,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(AudioTrackRowDefaults.LeadingIconSize),
                    )
                }
            }
        }

        val waveState = EqualizerStateHelper.resolveEqualizerState(isActive = isActive, isPlaying = isPlaying)
        if (waveState != EqualizerWaveState.IDLE) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = AudioTrackRowDefaults.OverlayAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerTrackIndicator(waveState = waveState)
            }
        }
    }
}

@Composable
fun AudioQualityBadgePill(
    badge: AudioQualityBadge,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) =
        AudioTrackRowDefaults.badgeColors(badge.qualityLevel, MaterialTheme.colorScheme)

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

@Composable
fun TrackTagPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

object AudioTrackRowDefaults {
    val ThumbnailSize: Dp = 46.dp
    val LeadingIconSize: Dp = 24.dp
    val OverflowIconSize: Dp = 20.dp
    val OverflowButtonSize: Dp = 36.dp
    val DividerInsetStart: Dp = 72.dp
    val DividerInsetEnd: Dp = 16.dp
    val DividerThickness: Dp = 0.5.dp
    val TrailingSpacing: Dp = 4.dp
    val TagSpacing: Dp = 6.dp
    val OverlayAlpha: Float = 0.72f

    fun formatSubtitle(
        artist: String?,
        album: String? = null,
        durationText: String? = null,
        extra: String? = null,
    ): String? =
        listOfNotNull(
            artist?.trim()?.takeIf { it.isNotEmpty() },
            album?.trim()?.takeIf { it.isNotEmpty() },
            durationText?.trim()?.takeIf { it.isNotEmpty() },
            extra?.trim()?.takeIf { it.isNotEmpty() },
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    fun badgeColors(
        qualityLevel: AudioQualityLevel,
        colorScheme: ColorScheme,
    ): Pair<Color, Color> =
        when (qualityLevel) {
            AudioQualityLevel.LOSSLESS -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
            AudioQualityLevel.HIGH_QUALITY -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
            AudioQualityLevel.STANDARD -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
            AudioQualityLevel.COMPRESSED -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        }

    fun resolveContainerColor(
        isActive: Boolean,
        colorScheme: ColorScheme,
    ): Color = if (isActive) colorScheme.surfaceContainerHigh else Color.Transparent

    fun resolveTitleColor(
        isActive: Boolean,
        colorScheme: ColorScheme,
    ): Color = if (isActive) colorScheme.primary else Color.Unspecified

    fun resolveTitleFontWeight(isActive: Boolean): FontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
}

@Composable
fun AudioTrackTrailing(
    durationText: String?,
    audioQualityBadge: AudioQualityBadge?,
    actionsMenu: (@Composable (dismiss: () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (audioQualityBadge == null && durationText.isNullOrBlank() && actionsMenu == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AudioTrackRowDefaults.TrailingSpacing),
    ) {
        if (audioQualityBadge != null) AudioQualityBadgePill(badge = audioQualityBadge)
        if (!durationText.isNullOrBlank()) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 2.dp),
            )
        }
        if (actionsMenu != null) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(AudioTrackRowDefaults.OverflowButtonSize),
                ) {
                    Icon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AudioTrackRowDefaults.OverflowIconSize),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    actionsMenu { showMenu = false }
                }
            }
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
