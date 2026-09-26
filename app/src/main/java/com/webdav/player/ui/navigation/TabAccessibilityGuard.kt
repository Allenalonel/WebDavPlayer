package com.webdav.player.ui.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Guards inactive tab containers from assistive accessibility services (e.g. TalkBack).
 *
 * When [isActive] is false, applies [Modifier.clearAndSetSemantics] with an empty configuration block.
 * This completely hides off-screen UI elements from accessibility focus trees while retaining their
 * composition, scroll position, and ViewModel state.
 */
fun Modifier.tabAccessibilityGuard(isActive: Boolean): Modifier =
    if (isActive) this else this.clearAndSetSemantics { }
