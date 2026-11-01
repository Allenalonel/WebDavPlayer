package com.webdav.player.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrackTagPillTest {
    @Test
    fun trackTagPill_composableSignatureExistsOnClasspath() {
        val clazz = Class.forName("com.webdav.player.ui.common.AudioTrackRowKt")
        val methodNames = clazz.methods.map { it.name }
        assertTrue("TrackTagPill must exist on classpath", methodNames.any { it.startsWith("TrackTagPill") })
    }

    @Test
    fun audioTrackRow_consolidatedLineCountWithinBudget() {
        val file = File("src/main/java/com/webdav/player/ui/common/AudioTrackRow.kt")
        assertTrue("AudioTrackRow.kt must exist", file.exists())
        val lines = file.readLines().size
        assertTrue("AudioTrackRow.kt line count must be <= 380 lines, was $lines", lines <= 380)
    }
}
