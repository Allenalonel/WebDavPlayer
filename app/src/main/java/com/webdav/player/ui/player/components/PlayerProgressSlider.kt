package com.webdav.player.ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.PlaybackProgress

/**
 * Dual-layer buffered progress slider with elapsed and remaining timestamps.
 *
 * Supports free dragging and tap-to-seek across the entire track hitbox.
 */
@Composable
internal fun PlayerProgressSlider(
    playbackProgress: PlaybackProgress,
    sessionDurationMs: Long,
    displayPositionMs: Long,
    isScrubbing: Boolean,
    onScrubbingChange: (Boolean, Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemainingTime by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }

    val maxDuration =
        if (playbackProgress.durationMs > 0L) {
            playbackProgress.durationMs
        } else {
            sessionDurationMs.coerceAtLeast(0L)
        }

    val currentPosition = if (isDragging) dragPositionMs else displayPositionMs
    val progressFraction =
        if (maxDuration > 0L) {
            (currentPosition.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val bufferedFraction = playbackProgress.bufferedFraction.coerceIn(0f, 1f)

    val activeTrackColor = MaterialTheme.colorScheme.primary
    val inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val bufferedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val thumbColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .semantics {
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                current = currentPosition.toFloat(),
                                range = 0f..(if (maxDuration > 0L) maxDuration.toFloat() else 1f),
                            )
                    }.pointerInput(maxDuration) {
                        if (maxDuration <= 0L) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val trackWidth = size.width
                            if (trackWidth <= 0) return@awaitEachGesture

                            var frac = (down.position.x / trackWidth).coerceIn(0f, 1f)
                            var seekMs = (frac * maxDuration).toLong().coerceIn(0L, maxDuration)
                            isDragging = true
                            dragPositionMs = seekMs
                            onScrubbingChange(true, seekMs)
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    isDragging = false
                                    onSeek(seekMs)
                                    onScrubbingChange(false, seekMs)
                                    break
                                }
                                frac = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                seekMs = (frac * maxDuration).toLong().coerceIn(0L, maxDuration)
                                dragPositionMs = seekMs
                                onScrubbingChange(true, seekMs)
                                change.consume()
                            }
                        }
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp),
            ) {
                val trackY = size.height / 2f
                val barHeight = if (isDragging) 6.dp.toPx() else 4.dp.toPx()
                val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
                val cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)

                // 1. Inactive background track
                drawRoundRect(
                    color = inactiveTrackColor,
                    topLeft = Offset(0f, trackY - barHeight / 2f),
                    size = Size(size.width, barHeight),
                    cornerRadius = cornerRadius,
                )

                // 2. Buffered progress track
                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = bufferedTrackColor,
                        topLeft = Offset(0f, trackY - barHeight / 2f),
                        size = Size(size.width * bufferedFraction, barHeight),
                        cornerRadius = cornerRadius,
                    )
                }

                // 3. Active playback track
                if (progressFraction > 0f) {
                    drawRoundRect(
                        color = activeTrackColor,
                        topLeft = Offset(0f, trackY - barHeight / 2f),
                        size = Size(size.width * progressFraction, barHeight),
                        cornerRadius = cornerRadius,
                    )
                }

                // 4. Thumb Knob with dragging halo
                if (maxDuration > 0L) {
                    val thumbX = (size.width * progressFraction).coerceIn(thumbRadius, size.width - thumbRadius)
                    if (isDragging) {
                        drawCircle(
                            color = activeTrackColor.copy(alpha = 0.22f),
                            radius = thumbRadius + 5.dp.toPx(),
                            center = Offset(thumbX, trackY),
                        )
                    }
                    drawCircle(
                        color = thumbColor,
                        radius = thumbRadius,
                        center = Offset(thumbX, trackY),
                    )
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    if (isDragging || isScrubbing) {
                        PlaybackProgress.formatMs(currentPosition)
                    } else {
                        playbackProgress.formattedCurrentPosition
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    if (showRemainingTime && maxDuration > currentPosition) {
                        "-${PlaybackProgress.formatMs(maxDuration - currentPosition)}"
                    } else if (!isDragging && !isScrubbing) {
                        playbackProgress.formattedDuration
                    } else {
                        PlaybackProgress.formatMs(maxDuration)
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.clickable {
                        showRemainingTime = !showRemainingTime
                    },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerProgressSliderPreview() {
    MaterialTheme {
        PlayerProgressSlider(
            playbackProgress = PlaybackProgress(currentPositionMs = 60000L, durationMs = 180000L, bufferedPositionMs = 120000L),
            sessionDurationMs = 180000L,
            displayPositionMs = 60000L,
            isScrubbing = false,
            onScrubbingChange = { _, _ -> },
            onSeek = {},
        )
    }
}
