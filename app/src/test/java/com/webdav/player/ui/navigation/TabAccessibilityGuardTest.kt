package com.webdav.player.ui.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsModifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabAccessibilityGuardTest {

    private fun Modifier.isClearingAccessibilitySemantics(): Boolean {
        var clearing = false
        foldIn(Unit) { _, element ->
            if (element is SemanticsModifier && element.semanticsConfiguration.isClearingSemantics) {
                clearing = true
            }
        }
        return clearing
    }

    @Test
    fun tabAccessibilityGuard_whenActive_doesNotClearSemantics() {
        val baseModifier = Modifier
        val guardedModifier = baseModifier.tabAccessibilityGuard(isActive = true)

        assertFalse(
            "Active tab must not clear accessibility semantics",
            guardedModifier.isClearingAccessibilitySemantics()
        )
    }

    @Test
    fun tabAccessibilityGuard_whenInactive_clearsSemanticsSubtree() {
        val baseModifier = Modifier
        val guardedModifier = baseModifier.tabAccessibilityGuard(isActive = false)

        assertTrue(
            "Inactive tab must clear accessibility semantics to prevent TalkBack focus traps",
            guardedModifier.isClearingAccessibilitySemantics()
        )
    }

    @Test
    fun tabAccessibilityGuard_whenSwitchingActiveState_togglesSemanticsClearing() {
        var isTabActive = true
        var modifier = Modifier.tabAccessibilityGuard(isActive = isTabActive)
        assertFalse(modifier.isClearingAccessibilitySemantics())

        isTabActive = false
        modifier = Modifier.tabAccessibilityGuard(isActive = isTabActive)
        assertTrue(modifier.isClearingAccessibilitySemantics())

        isTabActive = true
        modifier = Modifier.tabAccessibilityGuard(isActive = isTabActive)
        assertFalse(modifier.isClearingAccessibilitySemantics())
    }
}
