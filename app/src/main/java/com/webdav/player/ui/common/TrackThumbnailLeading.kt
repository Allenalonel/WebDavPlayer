package com.webdav.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.webdav.player.ui.browser.components.EqualizerStateHelper
import com.webdav.player.ui.browser.components.EqualizerWaveState
import com.webdav.player.ui.theme.AppIcons

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
        modifier =
            modifier
                .size(size)
                .clip(MaterialTheme.shapes.small),
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
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = AudioTrackRowDefaults.OverlayAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerTrackIndicator(waveState = waveState)
            }
        }
    }
}
