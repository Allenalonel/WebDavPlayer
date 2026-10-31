@file:JvmName("AudioTrackItemRowKt")
@file:JvmMultifileClass

package com.webdav.player.ui.browser.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata

@Composable
fun FileItemRow(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier,
) = AudioTrackItemRow(file, metadata, isActive, isPlaying, onClick, onPlayNext, onAddToPlaylist, modifier)

@Composable
fun EqualizerTrackIndicator(
    waveState: EqualizerWaveState,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) = com.webdav.player.ui.common
    .EqualizerTrackIndicator(waveState, modifier, barColor)

@Composable
fun EqualizerTrackIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) = com.webdav.player.ui.common
    .EqualizerTrackIndicator(isPlaying, modifier, barColor)

@Composable
fun AudioQualityBadgePill(
    badge: AudioQualityBadge,
    modifier: Modifier = Modifier,
) = com.webdav.player.ui.common
    .AudioQualityBadgePill(badge, modifier)

@Composable
fun AudioBadge(
    format: AudioFormat,
    modifier: Modifier = Modifier,
) = AudioQualityBadgePill(
    AudioQualityBadge(
        format.extension.uppercase(),
        format,
        format == AudioFormat.FLAC || format == AudioFormat.WAV,
        qualityLevel =
            if (format == AudioFormat.FLAC || format == AudioFormat.WAV) {
                AudioQualityLevel.LOSSLESS
            } else if (format == AudioFormat.MP3) {
                AudioQualityLevel.HIGH_QUALITY
            } else {
                AudioQualityLevel.STANDARD
            },
    ),
    modifier,
)

@Composable
fun LyricBadge(modifier: Modifier = Modifier) =
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            "LRC",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }

@Composable
fun OtherBadge(
    extension: String,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    shape = MaterialTheme.shapes.extraSmall,
) {
    Text(
        extension.take(4),
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
