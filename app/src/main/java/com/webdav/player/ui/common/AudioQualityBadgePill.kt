package com.webdav.player.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioQualityBadge

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
