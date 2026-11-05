package com.webdav.player.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.webdav.player.domain.model.RemoteDirectory
import kotlin.math.roundToInt

/**
 * Direction of hierarchy navigation for directory browsing and nested containers.
 */
enum class HierarchyNavigationDirection {
    /** Navigating deeper into a child folder (100% push from right, 25% retreat on left). */
    FORWARD,

    /** Navigating up towards parent or ancestor (reverse symmetric transition). */
    BACKWARD,

    /** Cross-dimensional focal landing from search results (scaleIn 0.96f + fadeIn). */
    FOCAL_LANDING,

    /** Same level or unchanged path (no spatial animation). */
    NONE,
}

/**
 * Material 3 Expressive motion transition specifications.
 */
object MotionTransitions {
    /**
     * Default travel distance fraction for top-level tab shared axis navigation (20%).
     */
    const val SharedAxisSlideFraction: Float = 0.20f

    /** Parallax retreat offset fraction for parent views retreating during drilldown (25%). */
    const val ParallaxRetreatFraction: Float = 0.25f

    /** Slight dimming alpha for parent views retreating in hierarchy drilldown. */
    const val ParallaxDimmingAlpha: Float = 0.5f

    /** Focal landing initial scale factor (0.96f). */
    const val FocalLandingScale: Float = 0.96f

    /** Dialog window initial scale factor (0.92f) for gentle spring pop-in. */
    const val DialogInitialScale: Float = 0.92f

    /**
     * Canonical Material 3 Expressive dialog enter transition specification.
     *
     * Scales in from [DialogInitialScale] (0.92f) using [MotionTokens.spatialSpec] and fades in
     * using [MotionTokens.effectsSpec]. Used as the canonical transition specification for
     * [androidx.compose.animation.AnimatedVisibility] dialog containers alongside [expressiveDialog]
     * (which provides equivalent spring scaling for window-level dialogs like [androidx.compose.material3.AlertDialog]).
     *
     * @param reduceMotion Whether reduce-motion accessibility preference is active.
     */
    fun dialogEnter(reduceMotion: Boolean = false): EnterTransition =
        scaleIn(
            initialScale = DialogInitialScale,
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
        ) +
            fadeIn(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

    /**
     * Canonical Material 3 Expressive dialog exit transition specification.
     *
     * Scales out to [DialogInitialScale] (0.92f) using [MotionTokens.spatialSpec] and fades out
     * using [MotionTokens.effectsSpec]. Used as the canonical transition specification for
     * [androidx.compose.animation.AnimatedVisibility] dialog containers alongside [expressiveDialog]
     * (which provides equivalent spring scaling for window-level dialogs like [androidx.compose.material3.AlertDialog]).
     *
     * @param reduceMotion Whether reduce-motion accessibility preference is active.
     */
    fun dialogExit(reduceMotion: Boolean = false): ExitTransition =
        scaleOut(
            targetScale = DialogInitialScale,
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
        ) +
            fadeOut(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

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

    // --- Hierarchy Drilldown (Push / Forward) ---

    /**
     * Subfolder file list pushes in 100% from the right edge with [MotionTokens.SpatialSpring].
     */
    fun hierarchyPushEnter(reduceMotion: Boolean = false): EnterTransition =
        slideInHorizontally(
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
            initialOffsetX = { fullWidth -> fullWidth },
        ) +
            fadeIn(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

    /**
     * Parent list retreats to the left with 25% parallax offset and slight dimming.
     */
    fun hierarchyPushExit(reduceMotion: Boolean = false): ExitTransition =
        slideOutHorizontally(
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
            targetOffsetX = { fullWidth -> -(fullWidth * ParallaxRetreatFraction).toInt() },
        ) +
            fadeOut(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
                targetAlpha = ParallaxDimmingAlpha,
            )

    // --- Hierarchy Pop (Back / Reverse) ---

    /**
     * Parent list glides back from 25% parallax offset on the left.
     */
    fun hierarchyPopEnter(reduceMotion: Boolean = false): EnterTransition =
        slideInHorizontally(
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
            initialOffsetX = { fullWidth -> -(fullWidth * ParallaxRetreatFraction).toInt() },
        ) +
            fadeIn(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

    /**
     * Subfolder file list slides out 100% to the right.
     */
    fun hierarchyPopExit(reduceMotion: Boolean = false): ExitTransition =
        slideOutHorizontally(
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
            targetOffsetX = { fullWidth -> fullWidth },
        ) +
            fadeOut(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

    // --- Focal Landing (Search result -> Directory) ---

    /**
     * Subtle focal zoom-in and fade transition landing on target folder.
     */
    fun focalLandingEnter(reduceMotion: Boolean = false): EnterTransition =
        scaleIn(
            initialScale = FocalLandingScale,
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
        ) +
            fadeIn(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

    fun focalLandingExit(reduceMotion: Boolean = false): ExitTransition =
        fadeOut(
            animationSpec = MotionTokens.effectsSpec(reduceMotion),
        )

    // --- Combined ContentTransforms ---

    /**
     * Resolves the full [ContentTransform] for hierarchy navigation.
     */
    fun hierarchyContentTransform(
        direction: HierarchyNavigationDirection,
        reduceMotion: Boolean = false,
    ): ContentTransform =
        when (direction) {
            HierarchyNavigationDirection.FORWARD -> {
                (hierarchyPushEnter(reduceMotion) togetherWith hierarchyPushExit(reduceMotion)).apply {
                    targetContentZIndex = 1f
                }
            }

            HierarchyNavigationDirection.BACKWARD -> {
                (hierarchyPopEnter(reduceMotion) togetherWith hierarchyPopExit(reduceMotion)).apply {
                    targetContentZIndex = -1f
                }
            }

            HierarchyNavigationDirection.FOCAL_LANDING -> {
                focalLandingEnter(reduceMotion) togetherWith focalLandingExit(reduceMotion)
            }

            HierarchyNavigationDirection.NONE -> {
                EnterTransition.None togetherWith ExitTransition.None
            }
        }

    // --- Search View Overlay Transitions ---

    /**
     * Full-screen search view overlay slides down smoothly from underneath the search pill.
     */
    fun searchOverlayEnter(reduceMotion: Boolean = false): EnterTransition =
        slideInVertically(
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
            initialOffsetY = { -it / 4 },
        ) +
            fadeIn(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

    /**
     * Full-screen search view overlay slides back up underneath the search pill upon collapse.
     */
    fun searchOverlayExit(reduceMotion: Boolean = false): ExitTransition =
        slideOutVertically(
            animationSpec = MotionTokens.spatialSpec(reduceMotion),
            targetOffsetY = { -it / 4 },
        ) +
            fadeOut(
                animationSpec = MotionTokens.effectsSpec(reduceMotion),
            )

    /**
     * Resolves the hierarchy navigation direction between two directory paths.
     */
    fun resolveHierarchyDirection(
        initialPath: String,
        targetPath: String,
        isFocalLanding: Boolean,
    ): HierarchyNavigationDirection {
        if (isFocalLanding) return HierarchyNavigationDirection.FOCAL_LANDING
        if (initialPath == targetPath || initialPath.isEmpty()) return HierarchyNavigationDirection.NONE

        val normInitial = RemoteDirectory.normalizePath(initialPath)
        val normTarget = RemoteDirectory.normalizePath(targetPath)
        if (normInitial == normTarget) return HierarchyNavigationDirection.NONE

        if (RemoteDirectory.isAncestor(normInitial, normTarget)) {
            return HierarchyNavigationDirection.FORWARD
        }
        if (RemoteDirectory.isAncestor(normTarget, normInitial)) {
            return HierarchyNavigationDirection.BACKWARD
        }

        val initialDepth =
            normInitial
                .trim('/')
                .split('/')
                .filter { it.isNotEmpty() }
                .size
        val targetDepth =
            normTarget
                .trim('/')
                .split('/')
                .filter { it.isNotEmpty() }
                .size
        return when {
            targetDepth > initialDepth -> HierarchyNavigationDirection.FORWARD
            targetDepth < initialDepth -> HierarchyNavigationDirection.BACKWARD
            else -> HierarchyNavigationDirection.NONE
        }
    }
}
