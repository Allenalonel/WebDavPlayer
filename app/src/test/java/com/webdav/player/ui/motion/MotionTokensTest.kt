package com.webdav.player.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MotionTokensTest {
    @Test
    fun spatialSpring_conformsToM3ExpressiveSpecification() {
        assertEquals(
            "SpatialSpring stiffness must be StiffnessMediumLow (400f)",
            Spring.StiffnessMediumLow,
            MotionTokens.SpatialSpring.stiffness,
            0.001f,
        )
        assertEquals(
            "SpatialSpring damping ratio must be DampingRatioLowBouncy (0.75f)",
            Spring.DampingRatioLowBouncy,
            MotionTokens.SpatialSpring.dampingRatio,
            0.001f,
        )
        assertEquals(
            MotionTokens.SpatialStiffness,
            MotionTokens.SpatialSpring.stiffness,
            0.001f,
        )
        assertEquals(
            MotionTokens.SpatialDampingRatio,
            MotionTokens.SpatialSpring.dampingRatio,
            0.001f,
        )
    }

    @Test
    fun effectsSpring_conformsToM3ExpressiveSpecification() {
        assertEquals(
            "EffectsSpring stiffness must be StiffnessMedium (1500f)",
            Spring.StiffnessMedium,
            MotionTokens.EffectsSpring.stiffness,
            0.001f,
        )
        assertEquals(
            "EffectsSpring damping ratio must be DampingRatioNoBouncy (1.0f)",
            Spring.DampingRatioNoBouncy,
            MotionTokens.EffectsSpring.dampingRatio,
            0.001f,
        )
        assertEquals(
            MotionTokens.EffectsStiffness,
            MotionTokens.EffectsSpring.stiffness,
            0.001f,
        )
        assertEquals(
            MotionTokens.EffectsDampingRatio,
            MotionTokens.EffectsSpring.dampingRatio,
            0.001f,
        )
    }

    @Test
    fun fastSpatialSpring_conformsToM3ExpressiveSpecification() {
        assertEquals(
            "FastSpatialSpring stiffness must be StiffnessMedium (1500f)",
            Spring.StiffnessMedium,
            MotionTokens.FastSpatialSpring.stiffness,
            0.001f,
        )
        assertEquals(
            "FastSpatialSpring damping ratio must be DampingRatioMediumBouncy (0.5f)",
            Spring.DampingRatioMediumBouncy,
            MotionTokens.FastSpatialSpring.dampingRatio,
            0.001f,
        )
        assertEquals(
            MotionTokens.FastSpatialStiffness,
            MotionTokens.FastSpatialSpring.stiffness,
            0.001f,
        )
        assertEquals(
            MotionTokens.FastSpatialDampingRatio,
            MotionTokens.FastSpatialSpring.dampingRatio,
            0.001f,
        )
    }

    @Test
    fun defaultPressScale_conformsToM3ExpressiveTactileSpecification() {
        assertEquals(
            "Default tactile press scale must be 0.965f",
            0.965f,
            MotionTokens.DefaultPressScale,
            0.0001f,
        )
    }

    @Test
    fun genericBuilders_produceMatchingParameters() {
        val spatial = MotionTokens.spatialSpring<Float>()
        assertEquals(Spring.StiffnessMediumLow, spatial.stiffness, 0.001f)
        assertEquals(Spring.DampingRatioLowBouncy, spatial.dampingRatio, 0.001f)

        val effects = MotionTokens.effectsSpring<Float>()
        assertEquals(Spring.StiffnessMedium, effects.stiffness, 0.001f)
        assertEquals(Spring.DampingRatioNoBouncy, effects.dampingRatio, 0.001f)

        val fastSpatial = MotionTokens.fastSpatialSpring<Float>()
        assertEquals(Spring.StiffnessMedium, fastSpatial.stiffness, 0.001f)
        assertEquals(Spring.DampingRatioMediumBouncy, fastSpatial.dampingRatio, 0.001f)
    }

    @Test
    fun resolveSpec_whenReduceMotionDisabled_returnsSpringSpec() {
        val spec = MotionTokens.resolveSpec(MotionTokens.SpatialSpring, reduceMotion = false)
        assertTrue("When reduce-motion is disabled, spec must be SpringSpec", spec is SpringSpec)
        val springSpec = spec as SpringSpec
        assertEquals(Spring.StiffnessMediumLow, springSpec.stiffness, 0.001f)
        assertEquals(Spring.DampingRatioLowBouncy, springSpec.dampingRatio, 0.001f)
    }

    @Test
    fun resolveSpec_whenReduceMotionEnabled_collapsesToZeroDurationSnap() {
        val spec = MotionTokens.resolveSpec(MotionTokens.SpatialSpring, reduceMotion = true)
        assertTrue("When reduce-motion is enabled, spec must collapse to SnapSpec", spec is SnapSpec)
        val snapSpec = spec as SnapSpec
        assertEquals("SnapSpec delay must be 0ms for instant transition", 0, snapSpec.delay)
    }

    @Test
    fun convenienceSpecs_respectReduceMotionFlag() {
        val spatialNormal = MotionTokens.spatialSpec<Float>(reduceMotion = false)
        assertTrue(spatialNormal is SpringSpec)

        val spatialReduced = MotionTokens.spatialSpec<Float>(reduceMotion = true)
        assertTrue(spatialReduced is SnapSpec)
        assertEquals(0, (spatialReduced as SnapSpec).delay)

        val effectsNormal = MotionTokens.effectsSpec<Float>(reduceMotion = false)
        assertTrue(effectsNormal is SpringSpec)

        val effectsReduced = MotionTokens.effectsSpec<Float>(reduceMotion = true)
        assertTrue(effectsReduced is SnapSpec)
        assertEquals(0, (effectsReduced as SnapSpec).delay)

        val fastSpatialNormal = MotionTokens.fastSpatialSpec<Float>(reduceMotion = false)
        assertTrue(fastSpatialNormal is SpringSpec)

        val fastSpatialReduced = MotionTokens.fastSpatialSpec<Float>(reduceMotion = true)
        assertTrue(fastSpatialReduced is SnapSpec)
        assertEquals(0, (fastSpatialReduced as SnapSpec).delay)
    }

    @Test
    fun systemAccessibilitySetting_whenAnimatorDurationZero_collapsesMotionToSnap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver

        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.0f)
        assertTrue(
            "When ANIMATOR_DURATION_SCALE is 0.0, reduce-motion must be active",
            MotionTokens.isReduceMotionEnabled(context),
        )

        val resolved = MotionTokens.resolveSpec(MotionTokens.SpatialSpring, context)
        assertTrue("Resolved spec must be SnapSpec when animations are removed", resolved is SnapSpec)
        assertEquals(0, (resolved as SnapSpec).delay)
    }

    @Test
    fun systemAccessibilitySetting_whenAnimatorDurationNormal_preservesSpringSpec() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver

        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        assertFalse(
            "When ANIMATOR_DURATION_SCALE is 1.0, reduce-motion must not be active",
            MotionTokens.isReduceMotionEnabled(context),
        )

        val resolved = MotionTokens.resolveSpec(MotionTokens.SpatialSpring, context)
        assertTrue("Resolved spec must be SpringSpec when animations are enabled", resolved is SpringSpec)
    }

    @Test
    fun motionModifiers_chainProperlyWithoutException() {
        val modifierWithPress = Modifier.expressivePress()
        assertNotNull(modifierWithPress)

        val modifierWithBouncyClick = Modifier.bouncyClickable { /* click */ }
        assertNotNull(modifierWithBouncyClick)

        val modifierWithBoth =
            Modifier.expressivePress().bouncyClickable(
                onLongClick = { /* long click */ },
                onClick = { /* click */ },
            )
        assertNotNull(modifierWithBoth)
    }
}
