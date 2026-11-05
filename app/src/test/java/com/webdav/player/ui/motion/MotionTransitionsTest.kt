package com.webdav.player.ui.motion

import androidx.compose.animation.togetherWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MotionTransitionsTest {
    @Test
    fun sharedAxisSlideFraction_isTwentyPercent() {
        assertEquals(0.20f, MotionTransitions.SharedAxisSlideFraction, 0.001f)
    }

    @Test
    fun sharedAxisX_forwardTransition_returnsValidContentTransform() {
        val transform = MotionTransitions.sharedAxisX(isForward = true, reduceMotion = false)
        assertNotNull(transform)
    }

    @Test
    fun sharedAxisX_backwardTransition_returnsValidContentTransform() {
        val transform = MotionTransitions.sharedAxisX(isForward = false, reduceMotion = false)
        assertNotNull(transform)
    }

    @Test
    fun sharedAxisX_reduceMotionActive_returnsValidContentTransform() {
        val transformForward = MotionTransitions.sharedAxisX(isForward = true, reduceMotion = true)
        val transformBackward = MotionTransitions.sharedAxisX(isForward = false, reduceMotion = true)
        assertNotNull(transformForward)
        assertNotNull(transformBackward)
    }

    @Test
    fun constants_conformToMaterial3ExpressiveSpecification() {
        assertEquals(0.25f, MotionTransitions.ParallaxRetreatFraction, 0.001f)
        assertEquals(0.5f, MotionTransitions.ParallaxDimmingAlpha, 0.001f)
        assertEquals(0.96f, MotionTransitions.FocalLandingScale, 0.001f)
        assertEquals(0.92f, MotionTransitions.DialogInitialScale, 0.001f)
    }

    @Test
    fun dialogTransitions_returnValidEnterAndExitTransitions() {
        val enterNormal = MotionTransitions.dialogEnter(reduceMotion = false)
        val exitNormal = MotionTransitions.dialogExit(reduceMotion = false)
        assertNotNull(enterNormal)
        assertNotNull(exitNormal)

        val enterReduced = MotionTransitions.dialogEnter(reduceMotion = true)
        val exitReduced = MotionTransitions.dialogExit(reduceMotion = true)
        assertNotNull(enterReduced)
        assertNotNull(exitReduced)
    }

    @Test
    fun resolveHierarchyDirection_drilldownIntoSubfolder_resolvesForward() {
        val direction =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Music/",
                targetPath = "/Music/Lossless/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.FORWARD, direction)

        val rootToSub =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/",
                targetPath = "/Music/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.FORWARD, rootToSub)
    }

    @Test
    fun resolveHierarchyDirection_navigateBackToParent_resolvesBackward() {
        val direction =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Music/Lossless/",
                targetPath = "/Music/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.BACKWARD, direction)
    }

    @Test
    fun resolveHierarchyDirection_navigateBackToRoot_resolvesBackward() {
        val direction =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Music/Lossless/Pink Floyd/",
                targetPath = "/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.BACKWARD, direction)
    }

    @Test
    fun resolveHierarchyDirection_focalLandingFromSearch_resolvesFocalLanding() {
        val direction =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Music/",
                targetPath = "/Audiobooks/SciFi/",
                isFocalLanding = true,
            )
        assertEquals(HierarchyNavigationDirection.FOCAL_LANDING, direction)
    }

    @Test
    fun resolveHierarchyDirection_samePathOrNormalizedIdentical_resolvesNone() {
        val samePath =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Music/",
                targetPath = "/Music/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.NONE, samePath)

        val normalizedSame =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Music",
                targetPath = "/Music/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.NONE, normalizedSame)

        val emptyInitial =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "",
                targetPath = "/Music/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.NONE, emptyInitial)
    }

    @Test
    fun resolveHierarchyDirection_siblingPathDepthComparison() {
        // Going from deeper sibling to shallower directory
        val shallowing =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Classical/Beethoven/Symphonies/",
                targetPath = "/Jazz/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.BACKWARD, shallowing)

        // Going from shallow to deeper sibling
        val deepening =
            MotionTransitions.resolveHierarchyDirection(
                initialPath = "/Jazz/",
                targetPath = "/Classical/Beethoven/Symphonies/",
                isFocalLanding = false,
            )
        assertEquals(HierarchyNavigationDirection.FORWARD, deepening)
    }

    @Test
    fun hierarchyContentTransform_constructsValidTransforms() {
        val forwardTransform =
            MotionTransitions.hierarchyContentTransform(
                direction = HierarchyNavigationDirection.FORWARD,
                reduceMotion = false,
            )
        assertNotNull(forwardTransform)
        assertEquals(1f, forwardTransform.targetContentZIndex, 0.001f)

        val backwardTransform =
            MotionTransitions.hierarchyContentTransform(
                direction = HierarchyNavigationDirection.BACKWARD,
                reduceMotion = false,
            )
        assertNotNull(backwardTransform)
        assertEquals(-1f, backwardTransform.targetContentZIndex, 0.001f)

        val focalTransform =
            MotionTransitions.hierarchyContentTransform(
                direction = HierarchyNavigationDirection.FOCAL_LANDING,
                reduceMotion = false,
            )
        assertNotNull(focalTransform)

        val noneTransform =
            MotionTransitions.hierarchyContentTransform(
                direction = HierarchyNavigationDirection.NONE,
                reduceMotion = false,
            )
        assertNotNull(noneTransform)
    }

    @Test
    fun transitions_produceSpecsUnderReduceMotion() {
        // Verify reduceMotion paths do not throw and instantiate properly
        val pushEnter = MotionTransitions.hierarchyPushEnter(reduceMotion = true)
        val pushExit = MotionTransitions.hierarchyPushExit(reduceMotion = true)
        val popEnter = MotionTransitions.hierarchyPopEnter(reduceMotion = true)
        val popExit = MotionTransitions.hierarchyPopExit(reduceMotion = true)
        val focalEnter = MotionTransitions.focalLandingEnter(reduceMotion = true)
        val focalExit = MotionTransitions.focalLandingExit(reduceMotion = true)
        val searchEnter = MotionTransitions.searchOverlayEnter(reduceMotion = true)
        val searchExit = MotionTransitions.searchOverlayExit(reduceMotion = true)

        assertNotNull(pushEnter)
        assertNotNull(pushExit)
        assertNotNull(popEnter)
        assertNotNull(popExit)
        assertNotNull(focalEnter)
        assertNotNull(focalExit)
        assertNotNull(searchEnter)
        assertNotNull(searchExit)
    }

    @Test
    fun playlistDrilldownTransitions_composeValidContentTransforms() {
        val pushDrilldown =
            MotionTransitions.hierarchyPushEnter(reduceMotion = false) togetherWith
                MotionTransitions.hierarchyPushExit(reduceMotion = false)
        assertNotNull(pushDrilldown)

        val popDrilldown =
            MotionTransitions.hierarchyPopEnter(reduceMotion = false) togetherWith
                MotionTransitions.hierarchyPopExit(reduceMotion = false)
        assertNotNull(popDrilldown)

        val pushReduced =
            MotionTransitions.hierarchyPushEnter(reduceMotion = true) togetherWith
                MotionTransitions.hierarchyPushExit(reduceMotion = true)
        assertNotNull(pushReduced)

        val popReduced =
            MotionTransitions.hierarchyPopEnter(reduceMotion = true) togetherWith
                MotionTransitions.hierarchyPopExit(reduceMotion = true)
        assertNotNull(popReduced)
    }
}
