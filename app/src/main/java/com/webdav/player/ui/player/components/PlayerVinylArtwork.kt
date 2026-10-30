package com.webdav.player.ui.player.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.common.CoverThumbnailImage
import com.webdav.player.ui.player.rememberArtworkColors
import com.webdav.player.ui.theme.AppIcons

/**
 * Dynamic atmospheric gradient sampled from cover art with MD3 fallback.
 */
@Composable
fun rememberArtworkBackgroundBrush(
    thumbnailPath: String?,
    defaultSurfaceColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    defaultBackgroundColor: Color = MaterialTheme.colorScheme.surface,
): Brush {
    val artworkColors by rememberArtworkColors(
        thumbnailPath = thumbnailPath,
        defaultSurfaceColor = defaultSurfaceColor,
        defaultBackgroundColor = defaultBackgroundColor,
    )

    val animatedTopColor by animateColorAsState(
        targetValue = artworkColors.backgroundTopColor,
        animationSpec = tween(durationMillis = 400),
        label = "FullPlayerBackgroundTop",
    )
    val animatedBottomColor by animateColorAsState(
        targetValue = artworkColors.backgroundBottomColor,
        animationSpec = tween(durationMillis = 400),
        label = "FullPlayerBackgroundBottom",
    )
    return remember(animatedTopColor, animatedBottomColor) {
        Brush.verticalGradient(
            colors = listOf(animatedTopColor, animatedBottomColor),
        )
    }
}

/**
 * Artwork page displaying album cover artwork, track metadata, and format badges.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtworkPage(
    currentTrack: AudioTrack?,
    sessionState: PlayerSessionState,
    onArtworkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerVinylArtwork(
        currentTrack = currentTrack,
        sessionState = sessionState,
        onArtworkClick = onArtworkClick,
        modifier = modifier,
    )
}

/**
 * Vinyl record and album artwork presentation composable with soft shadow and rounded corners.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerVinylArtwork(
    currentTrack: AudioTrack?,
    sessionState: PlayerSessionState,
    onArtworkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large Album Artwork Card with Soft Shadow
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f)
                    .shadow(elevation = 20.dp, shape = MaterialTheme.shapes.extraLarge)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onArtworkClick() },
            contentAlignment = Alignment.Center,
        ) {
            CoverThumbnailImage(
                thumbnailPath = currentTrack?.coverThumbnailPath,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = AppIcons.Audiotrack,
                    contentDescription = "专辑封面",
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track Title with basicMarquee for long titles
        Text(
            text = currentTrack?.title ?: "无正在播放曲目",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Artist Name or Weak Network Alert Subtitle
        val subtitleText =
            when {
                sessionState.isSuspendedRetry -> PlayerSessionState.RETRY_BANNER_MESSAGE
                sessionState.isWeakNetwork -> PlayerSessionState.WEAK_NETWORK_MESSAGE
                else -> currentTrack?.artist?.trim()?.takeIf { it.isNotBlank() } ?: "未知艺术家"
            }
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (sessionState.isWeakNetwork) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Audiophile Specification Capsule
        val specs = currentTrack?.audiophileSpecsModel
        if (specs != null && specs.formatted.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AudiophileSpecsBadge(specs = specs)
        }
    }
}
