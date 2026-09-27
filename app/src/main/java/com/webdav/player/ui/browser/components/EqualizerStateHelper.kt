package com.webdav.player.ui.browser.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class EqualizerWaveState {
    IDLE,
    PLAYING,
    PAUSED,
}

object EqualizerStateHelper {
    val FrozenBar1Height: Dp = 6.dp
    val FrozenBar2Height: Dp = 14.dp
    val FrozenBar3Height: Dp = 8.dp

    val MinAnimatedBarHeight: Dp = 4.dp
    val MaxAnimatedBarHeight: Dp = 20.dp

    fun resolveEqualizerState(
        isActive: Boolean,
        isPlaying: Boolean,
    ): EqualizerWaveState =
        when {
            !isActive -> EqualizerWaveState.IDLE
            isPlaying -> EqualizerWaveState.PLAYING
            else -> EqualizerWaveState.PAUSED
        }

    fun resolveEqualizerState(
        trackPath: String,
        activeTrackPath: String?,
        isPlaying: Boolean,
    ): EqualizerWaveState =
        resolveEqualizerState(
            isActive = isTrackActive(trackPath, activeTrackPath),
            isPlaying = isPlaying,
        )

    fun isTrackActive(
        trackPath: String,
        activeTrackPath: String?,
    ): Boolean = activeTrackPath != null && trackPath == activeTrackPath
}
