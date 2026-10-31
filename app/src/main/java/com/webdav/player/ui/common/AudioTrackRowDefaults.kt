package com.webdav.player.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.ui.theme.AppIcons

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
    ): String? {
        val parts = mutableListOf<String>()
        if (!artist.isNullOrBlank()) parts.add(artist.trim())
        if (!album.isNullOrBlank()) parts.add(album.trim())
        if (!durationText.isNullOrBlank()) parts.add(durationText.trim())
        if (!extra.isNullOrBlank()) parts.add(extra.trim())
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    fun badgeColors(
        qualityLevel: AudioQualityLevel,
        colorScheme: ColorScheme,
    ): Pair<Color, Color> =
        when (qualityLevel) {
            AudioQualityLevel.LOSSLESS -> {
                colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
            }

            AudioQualityLevel.HIGH_QUALITY -> {
                colorScheme.primaryContainer to colorScheme.onPrimaryContainer
            }

            AudioQualityLevel.STANDARD -> {
                colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
            }

            AudioQualityLevel.COMPRESSED -> {
                colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
            }
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
    actionsMenu: (@Composable (() -> Unit) -> Unit)?,
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
