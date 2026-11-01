package com.webdav.player.ui.common

import androidx.compose.runtime.Composable
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrackTagPillTest {
    @Test
    fun trackTagPill_composableSignatureValid() {
        val invocation: @Composable () -> Unit = {
            TrackTagPill(text = "CUE")
        }
        assertNotNull(invocation)
    }
}
