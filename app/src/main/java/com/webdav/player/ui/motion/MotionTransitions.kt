package com.webdav.player.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Material 3 Expressive motion transition specifications.
 */
object MotionTransitions {
    /**
     * Default travel distance fraction for top-level tab shared axis navigation (20%).
     */
    const val SharedAxisSlideFraction: Float = 0.20f

    /**
     * Returns a [ContentTransform] implementing M3 Shared Axis X horizontal transition for tab navigation.
     *
     * - Forward navigation: outgoing view slides out to the left with 20% distance and fades out;
     *   incoming view slides in from the right with 20% distance and fades in.
     * - Backward navigation: outgoing view slides out to the right with 20% distance and fades out;
     *   incoming view slides in from the left with 20% distance and fades in.
     *
     * @param isForward True if moving forward (new tab index > old tab index), false if moving backward.
     * @param reduceMotion Whether reduce-motion accessibility preference is active.
     * @param slideFraction Fraction of full container width to slide during the transition (default 0.20f).
     */
    fun sharedAxisX(
        isForward: Boolean,
        reduceMotion: Boolean = false,
        slideFraction: Float = SharedAxisSlideFraction,
    ): ContentTransform {
        val spatialSpec = MotionTokens.spatialSpec<IntOffset>(reduceMotion)
        val effectsSpec = MotionTokens.effectsSpec<Float>(reduceMotion)

        val enter =
            if (isForward) {
                slideInHorizontally(
                    animationSpec = spatialSpec,
                    initialOffsetX = { fullWidth -> (fullWidth * slideFraction).roundToInt() },
                ) + fadeIn(animationSpec = effectsSpec)
            } else {
                slideInHorizontally(
                    animationSpec = spatialSpec,
                    initialOffsetX = { fullWidth -> -(fullWidth * slideFraction).roundToInt() },
                ) + fadeIn(animationSpec = effectsSpec)
            }

        val exit =
            if (isForward) {
                slideOutHorizontally(
                    animationSpec = spatialSpec,
                    targetOffsetX = { fullWidth -> -(fullWidth * slideFraction).roundToInt() },
                ) + fadeOut(animationSpec = effectsSpec)
            } else {
                slideOutHorizontally(
                    animationSpec = spatialSpec,
                    targetOffsetX = { fullWidth -> (fullWidth * slideFraction).roundToInt() },
                ) + fadeOut(animationSpec = effectsSpec)
            }

        return enter togetherWith exit
    }
}
