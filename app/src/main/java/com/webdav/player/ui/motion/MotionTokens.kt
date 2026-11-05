package com.webdav.player.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive motion physics tokens and transition specifications.
 *
 * Implements Google M3 Expressive Motion standards with centralized spring stiffness and damping
 * parameters, as well as native accessibility support (Reduce Motion / Remove Animations).
 */
object MotionTokens {
    /**
     * Default tactile press scale factor for expressive touch feedback.
     */
    const val DefaultPressScale: Float = 0.965f

    // --- Physics Parameters ---

    /** Spatial spring stiffness (translations, container morphing, size expansion). */
    const val SpatialStiffness: Float = Spring.StiffnessMediumLow // 400f

    /** Spatial spring damping ratio (gentle, low bounce). */
    const val SpatialDampingRatio: Float = Spring.DampingRatioLowBouncy // 0.75f

    /** Effects spring stiffness (alpha, crossfades, color transitions). */
    const val EffectsStiffness: Float = Spring.StiffnessMedium // 1500f

    /** Effects spring damping ratio (critical damping, zero bounce). */
    const val EffectsDampingRatio: Float = Spring.DampingRatioNoBouncy // 1.0f

    /** Fast spatial spring stiffness (press micro-interactions, tactile bounce). */
    const val FastSpatialStiffness: Float = Spring.StiffnessMedium // 1500f

    /** Fast spatial spring damping ratio (tactile, medium bounce). */
    const val FastSpatialDampingRatio: Float = Spring.DampingRatioMediumBouncy // 0.5f

    // --- Standard Springs ---

    val SpatialSpring: SpringSpec<Float> =
        spring(
            dampingRatio = SpatialDampingRatio,
            stiffness = SpatialStiffness,
        )

    val EffectsSpring: SpringSpec<Float> =
        spring(
            dampingRatio = EffectsDampingRatio,
            stiffness = EffectsStiffness,
        )

    val FastSpatialSpring: SpringSpec<Float> =
        spring(
            dampingRatio = FastSpatialDampingRatio,
            stiffness = FastSpatialStiffness,
        )

    /**
     * Generic builder for spatial springs.
     */
    fun <T> spatialSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = SpatialDampingRatio,
            stiffness = SpatialStiffness,
            visibilityThreshold = visibilityThreshold,
        )

    /**
     * Generic builder for effects springs.
     */
    fun <T> effectsSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = EffectsDampingRatio,
            stiffness = EffectsStiffness,
            visibilityThreshold = visibilityThreshold,
        )

    /**
     * Generic builder for fast spatial springs.
     */
    fun <T> fastSpatialSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = FastSpatialDampingRatio,
            stiffness = FastSpatialStiffness,
            visibilityThreshold = visibilityThreshold,
        )

    // --- Accessibility / Reduce Motion Support ---

    /**
     * Checks if the Android system has "Remove animations" or "Reduce motion" enabled.
     * Evaluates [ValueAnimator.areAnimatorsEnabled] (API 26+) and [Settings.Global.ANIMATOR_DURATION_SCALE].
     */
    fun isReduceMotionEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    return true
                }
            }
            val durationScale =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f,
                )
            durationScale <= 0f
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves an animation spec respecting reduce-motion setting.
     * When [reduceMotion] is true, returns a zero-duration [SnapSpec] (0ms instant transition).
     * Otherwise returns the given [springSpec].
     */
    fun <T> resolveSpec(
        springSpec: SpringSpec<T>,
        reduceMotion: Boolean,
    ): FiniteAnimationSpec<T> =
        if (reduceMotion) {
            snap()
        } else {
            springSpec
        }

    /**
     * Resolves an animation spec respecting the system reduce-motion setting from [context].
     */
    fun <T> resolveSpec(
        springSpec: SpringSpec<T>,
        context: Context,
    ): FiniteAnimationSpec<T> = resolveSpec(springSpec, isReduceMotionEnabled(context))

    /**
     * Returns a spatial animation spec that collapses to a 0ms snap when [reduceMotion] is active.
     */
    fun <T> spatialSpec(
        reduceMotion: Boolean = false,
        visibilityThreshold: T? = null,
    ): FiniteAnimationSpec<T> = if (reduceMotion) snap() else spatialSpring(visibilityThreshold)

    /**
     * Returns an effects animation spec that collapses to a 0ms snap when [reduceMotion] is active.
     */
    fun <T> effectsSpec(
        reduceMotion: Boolean = false,
        visibilityThreshold: T? = null,
    ): FiniteAnimationSpec<T> = if (reduceMotion) snap() else effectsSpring(visibilityThreshold)

    /**
     * Returns a fast spatial animation spec that collapses to a 0ms snap when [reduceMotion] is active.
     */
    fun <T> fastSpatialSpec(
        reduceMotion: Boolean = false,
        visibilityThreshold: T? = null,
    ): FiniteAnimationSpec<T> = if (reduceMotion) snap() else fastSpatialSpring(visibilityThreshold)
}

/**
 * CompositionLocal to explicitly override reduce-motion preference in Compose tests or previews.
 */
val LocalReduceMotion = compositionLocalOf<Boolean?> { null }

/**
 * Returns whether reduce-motion is enabled in the current Compose hierarchy.
 * Prioritizes [LocalReduceMotion] override if provided, falling back to system settings via [LocalContext].
 */
@Composable
fun isReduceMotionEnabled(): Boolean {
    LocalReduceMotion.current?.let { return it }
    val context = LocalContext.current
    return MotionTokens.isReduceMotionEnabled(context)
}
