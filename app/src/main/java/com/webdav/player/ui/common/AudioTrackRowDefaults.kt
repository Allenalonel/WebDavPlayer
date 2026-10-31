package com.webdav.player.ui.common

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioQualityLevel

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
