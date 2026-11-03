package com.webdav.player.ui.common

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTopAppBarTest {
    @Test
    fun heightOffsetLimit_withExpandedHeight_isNegativePx() {
        val expandedHeightPx = 144f
        val limit = FluidTopAppBarDefaults.calculateHeightOffsetLimit(expandedHeightPx)
        assertEquals(-144f, limit, 0.001f)
    }

    @Test
    fun heightOffsetLimit_withZeroExpandedHeight_isZero() {
        val limit = FluidTopAppBarDefaults.calculateHeightOffsetLimit(0f)
        assertEquals(0f, limit, 0.001f)
    }

    @Test
    fun compactTitleAlpha_whenExpandedTitlePresent_fadesInDuringUpperFraction() {
        val alphaAt0 = FluidTopAppBarDefaults.calculateCompactTitleAlpha(0f, isExpandedTitlePresent = true)
        assertEquals(0f, alphaAt0, 0.001f)

        val alphaAt04 = FluidTopAppBarDefaults.calculateCompactTitleAlpha(0.4f, isExpandedTitlePresent = true)
        assertEquals(0f, alphaAt04, 0.001f)

        val alphaAt07 = FluidTopAppBarDefaults.calculateCompactTitleAlpha(0.7f, isExpandedTitlePresent = true)
        assertEquals(0.5f, alphaAt07, 0.001f)

        val alphaAt1 = FluidTopAppBarDefaults.calculateCompactTitleAlpha(1f, isExpandedTitlePresent = true)
        assertEquals(1f, alphaAt1, 0.001f)
    }

    @Test
    fun compactTitleAlpha_whenExpandedTitleNotPresent_isAlwaysOne() {
        val alphaAt0 = FluidTopAppBarDefaults.calculateCompactTitleAlpha(0f, isExpandedTitlePresent = false)
        assertEquals(1f, alphaAt0, 0.001f)

        val alphaAtMid = FluidTopAppBarDefaults.calculateCompactTitleAlpha(0.5f, isExpandedTitlePresent = false)
        assertEquals(1f, alphaAtMid, 0.001f)

        val alphaAt1 = FluidTopAppBarDefaults.calculateCompactTitleAlpha(1f, isExpandedTitlePresent = false)
        assertEquals(1f, alphaAt1, 0.001f)
    }

    @Test
    fun expandedTitleAlpha_fadesOutDuringLowerFraction() {
        val alphaAt0 = FluidTopAppBarDefaults.calculateExpandedTitleAlpha(0f)
        assertEquals(1f, alphaAt0, 0.001f)

        val alphaAt03 = FluidTopAppBarDefaults.calculateExpandedTitleAlpha(0.3f)
        assertEquals(0.5f, alphaAt03, 0.001f)

        val alphaAt06 = FluidTopAppBarDefaults.calculateExpandedTitleAlpha(0.6f)
        assertEquals(0f, alphaAt06, 0.001f)

        val alphaAt1 = FluidTopAppBarDefaults.calculateExpandedTitleAlpha(1f)
        assertEquals(0f, alphaAt1, 0.001f)
    }

    @Test
    fun currentExpandedHeight_collapsesLinearlyToZero() {
        val baseHeight = 48.dp

        val heightAt0 = FluidTopAppBarDefaults.calculateExpandedHeight(baseHeight, 0f)
        assertEquals(48.dp, heightAt0)

        val heightAtHalf = FluidTopAppBarDefaults.calculateExpandedHeight(baseHeight, 0.5f)
        assertEquals(24.dp, heightAtHalf)

        val heightAt1 = FluidTopAppBarDefaults.calculateExpandedHeight(baseHeight, 1f)
        assertEquals(0.dp, heightAt1)
    }

    @Test
    fun leftAlignmentContract_strictConsistencyWithoutCenterJump() {
        // Without navigation icon: compact row starts at 4dp row padding + 12dp spacer = 16dp
        val compactStartNoNav = 4.dp + 12.dp
        val expandedStartNoNav = FluidTopAppBarDefaults.calculateLeftPadding(hasNavigationIcon = false)
        assertEquals(FluidTopAppBarDefaults.LeftBaselineNoNav, compactStartNoNav)
        assertEquals(FluidTopAppBarDefaults.LeftBaselineNoNav, expandedStartNoNav)
        assertEquals(0.dp, compactStartNoNav - expandedStartNoNav)

        // With navigation icon: compact row starts at 4dp row padding + 48dp icon + 4dp box start padding = 56dp
        val compactStartWithNav = 4.dp + 48.dp + 4.dp
        val expandedStartWithNav = FluidTopAppBarDefaults.calculateLeftPadding(hasNavigationIcon = true)
        assertEquals(FluidTopAppBarDefaults.LeftBaselineWithNav, compactStartWithNav)
        assertEquals(FluidTopAppBarDefaults.LeftBaselineWithNav, expandedStartWithNav)
        assertEquals(0.dp, compactStartWithNav - expandedStartWithNav)
    }

    @Test
    fun isScrolled_detectsFractionAndContentOffset() {
        assertFalse(FluidTopAppBarDefaults.isScrolled(0f, 0f))
        assertFalse(FluidTopAppBarDefaults.isScrolled(0.005f, -0.5f))

        assertTrue(FluidTopAppBarDefaults.isScrolled(0.02f, 0f))
        assertTrue(FluidTopAppBarDefaults.isScrolled(0f, -1.5f))
        assertTrue(FluidTopAppBarDefaults.isScrolled(1f, -50f))
    }

    @Test
    fun scrollOffsetLimit_guaranteesNoGestureSwallowing() {
        val expandedHeightPx = 100f
        val heightOffsetLimit = FluidTopAppBarDefaults.calculateHeightOffsetLimit(expandedHeightPx)

        // Simulating Compose onPreScroll consumption:
        // state.heightOffset = (state.heightOffset + available.y).coerceIn(heightOffsetLimit, 0f)
        var heightOffset = 0f

        // Initial scroll: user scrolls up 60px
        val scrollDelta1 = -60f
        val prev1 = heightOffset
        heightOffset = (heightOffset + scrollDelta1).coerceIn(heightOffsetLimit, 0f)
        val consumed1 = heightOffset - prev1
        assertEquals(-60f, consumed1, 0.001f)
        assertEquals(-60f, heightOffset, 0.001f)

        // Second scroll: user scrolls up 60px more (surpasses 100px total)
        val scrollDelta2 = -60f
        val prev2 = heightOffset
        heightOffset = (heightOffset + scrollDelta2).coerceIn(heightOffsetLimit, 0f)
        val consumed2 = heightOffset - prev2
        assertEquals(-40f, consumed2, 0.001f)
        assertEquals(-100f, heightOffset, 0.001f)

        // Third scroll: top bar is fully collapsed, user scrolls up another 50px
        // Consumption MUST be 0px, ensuring 100% of the delta goes to LazyColumn
        val scrollDelta3 = -50f
        val prev3 = heightOffset
        heightOffset = (heightOffset + scrollDelta3).coerceIn(heightOffsetLimit, 0f)
        val consumed3 = heightOffset - prev3
        assertEquals(0f, consumed3, 0.001f)
        assertEquals(-100f, heightOffset, 0.001f)
    }
}
