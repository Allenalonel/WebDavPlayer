package com.webdav.player.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
