package com.webdav.player.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webdav.player.ui.browser.components.EqualizerStateHelper
import com.webdav.player.ui.browser.components.EqualizerWaveState

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
