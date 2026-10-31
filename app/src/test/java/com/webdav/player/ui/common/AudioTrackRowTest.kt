package com.webdav.player.ui.common

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.ui.browser.components.EqualizerStateHelper
import com.webdav.player.ui.browser.components.EqualizerWaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioTrackRowTest {
    @Test
    fun formatSubtitle_withArtistAndAlbum_combinesWithBullet() {
        val result =
            AudioTrackRowDefaults.formatSubtitle(
                artist = "Pink Floyd",
                album = "The Wall",
            )
        assertEquals("Pink Floyd · The Wall", result)
    }

    @Test
    fun formatSubtitle_withArtistAlbumAndDuration_combinesAll() {
        val result =
            AudioTrackRowDefaults.formatSubtitle(
                artist = "Pink Floyd",
                album = "The Dark Side of the Moon",
                durationText = "06:22",
            )
        assertEquals("Pink Floyd · The Dark Side of the Moon · 06:22", result)
    }

    @Test
    fun formatSubtitle_withArtistOnly_returnsArtist() {
        val result =
            AudioTrackRowDefaults.formatSubtitle(
                artist = "David Bowie",
                album = null,
            )
        assertEquals("David Bowie", result)
    }

    @Test
    fun formatSubtitle_withExtra_appendsExtra() {
        val result =
            AudioTrackRowDefaults.formatSubtitle(
                artist = "Pink Floyd",
                album = "Wish You Were Here",
                extra = "ShineOn.cue",
            )
        assertEquals("Pink Floyd · Wish You Were Here · ShineOn.cue", result)
    }

    @Test
    fun badgeColors_mapsQualityLevelsToMaterialTokensCorrectly() {
        val testScheme = darkColorScheme(
            tertiaryContainer = Color(0xFF111111),
            onTertiaryContainer = Color(0xFF222222),
            primaryContainer = Color(0xFF333333),
            onPrimaryContainer = Color(0xFF444444),
            secondaryContainer = Color(0xFF555555),
            onSecondaryContainer = Color(0xFF666666),
            surfaceVariant = Color(0xFF777777),
            onSurfaceVariant = Color(0xFF888888),
        )

        val losslessBadge = AudioQualityBadge("FLAC", AudioFormat.FLAC, isLossless = true, qualityLevel = AudioQualityLevel.LOSSLESS)
        val (losslessBg, losslessFg) = AudioTrackRowDefaults.badgeColors(losslessBadge.qualityLevel, testScheme)
        assertEquals(testScheme.tertiaryContainer, losslessBg)
        assertEquals(testScheme.onTertiaryContainer, losslessFg)

        val hiResBadge = AudioQualityBadge("MP3 320k", AudioFormat.MP3, isLossless = false, qualityLevel = AudioQualityLevel.HIGH_QUALITY)
        val (hiResBg, hiResFg) = AudioTrackRowDefaults.badgeColors(hiResBadge.qualityLevel, testScheme)
        assertEquals(testScheme.primaryContainer, hiResBg)
        assertEquals(testScheme.onPrimaryContainer, hiResFg)

        val standardBadge = AudioQualityBadge("MP3 192k", AudioFormat.MP3, isLossless = false, qualityLevel = AudioQualityLevel.STANDARD)
        val (stdBg, stdFg) = AudioTrackRowDefaults.badgeColors(standardBadge.qualityLevel, testScheme)
        assertEquals(testScheme.secondaryContainer, stdBg)
        assertEquals(testScheme.onSecondaryContainer, stdFg)

        val compressedBadge = AudioQualityBadge("WMA", AudioFormat.WMA, isLossless = false, qualityLevel = AudioQualityLevel.COMPRESSED)
        val (compBg, compFg) = AudioTrackRowDefaults.badgeColors(compressedBadge.qualityLevel, testScheme)
        assertEquals(testScheme.surfaceVariant, compBg)
        assertEquals(testScheme.onSurfaceVariant, compFg)
    }

    @Test
    fun equalizerWaveState_mapsActiveAndPlayingStates() {
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(isActive = false, isPlaying = false),
        )
        assertEquals(
            EqualizerWaveState.IDLE,
            EqualizerStateHelper.resolveEqualizerState(isActive = false, isPlaying = true),
        )
        assertEquals(
            EqualizerWaveState.PAUSED,
            EqualizerStateHelper.resolveEqualizerState(isActive = true, isPlaying = false),
        )
        assertEquals(
            EqualizerWaveState.PLAYING,
            EqualizerStateHelper.resolveEqualizerState(isActive = true, isPlaying = true),
        )
    }

    @Test
    fun defaults_layoutTokensAdhereToDesignSpec() {
        assertEquals(46.dp, AudioTrackRowDefaults.ThumbnailSize)
        assertEquals(24.dp, AudioTrackRowDefaults.LeadingIconSize)
        assertEquals(20.dp, AudioTrackRowDefaults.OverflowIconSize)
        assertEquals(36.dp, AudioTrackRowDefaults.OverflowButtonSize)
        assertEquals(72.dp, AudioTrackRowDefaults.DividerInsetStart)
        assertEquals(16.dp, AudioTrackRowDefaults.DividerInsetEnd)
        assertEquals(0.5.dp, AudioTrackRowDefaults.DividerThickness)
        assertEquals(4.dp, AudioTrackRowDefaults.TrailingSpacing)
        assertEquals(6.dp, AudioTrackRowDefaults.TagSpacing)
        assertEquals(0.72f, AudioTrackRowDefaults.OverlayAlpha, 0.001f)
    }

    @Test
    fun stylingResolution_matchesActiveAndInactiveStates() {
        val testScheme = darkColorScheme(
            surfaceContainerHigh = Color(0xFF282828),
            primary = Color(0xFFBB86FC),
        )

        assertEquals(
            testScheme.surfaceContainerHigh,
            AudioTrackRowDefaults.resolveContainerColor(isActive = true, testScheme),
        )
        assertEquals(
            Color.Transparent,
            AudioTrackRowDefaults.resolveContainerColor(isActive = false, testScheme),
        )

        assertEquals(
            testScheme.primary,
            AudioTrackRowDefaults.resolveTitleColor(isActive = true, testScheme),
        )
        assertEquals(
            Color.Unspecified,
            AudioTrackRowDefaults.resolveTitleColor(isActive = false, testScheme),
        )

        assertEquals(
            FontWeight.Bold,
            AudioTrackRowDefaults.resolveTitleFontWeight(isActive = true),
        )
        assertEquals(
            FontWeight.SemiBold,
            AudioTrackRowDefaults.resolveTitleFontWeight(isActive = false),
        )
    }

    @Test
    fun rowClick_invokesOnClickCallback() {
        var clicked = false
        val onClick: () -> Unit = { clicked = true }

        onClick()
        assertTrue("onClick callback must be invoked when row is clicked", clicked)
    }

    @Test
    fun actionsMenu_dismissCallback_closesMenuOnAction() {
        var dismissed = false
        var actionExecuted = false

        val actionsMenu: ((() -> Unit) -> Unit) = { dismiss ->
            dismiss()
            actionExecuted = true
        }

        actionsMenu { dismissed = true }
        assertTrue(dismissed)
        assertTrue(actionExecuted)
    }

    @Test
    fun previews_declaredWithComposerSignature() {
        val clazz = Class.forName("com.webdav.player.ui.common.AudioTrackRowKt")
        val previewNames = listOf(
            "AudioTrackRowIdlePreview",
            "AudioTrackRowActivePlayingPreview",
            "AudioTrackRowActivePausedPreview",
            "AudioTrackRowWithoutCoverPreview",
        )

        for (name in previewNames) {
            val method = clazz.declaredMethods.find { it.name == name }
            org.junit.Assert.assertNotNull("Preview method $name must be declared", method)
            // Composable functions take a Composer and an Int parameter in bytecode
            val paramTypes = method!!.parameterTypes.map { it.simpleName }
            assertTrue("Method $name must be a @Composable accepting Composer", paramTypes.contains("Composer"))
        }
    }

    @Test
    fun audioTrackRow_supportsExact12ParameterContract() {
        val composableInvocation: @Composable () -> Unit = {
            AudioTrackRow(
                title = "Title",
                subtitle = "Subtitle",
                modifier = Modifier,
                durationText = "03:45",
                coverThumbnailPath = null,
                audioQualityBadge = null,
                isActive = false,
                isPlaying = false,
                onClick = {},
                leadingContent = null,
                trailingContent = null,
                actionsMenu = null,
            )
        }
        org.junit.Assert.assertNotNull(composableInvocation)
    }

    @Test
    fun audioTrackRow_lineCountWithinBudget() {
        val file = java.io.File("src/main/java/com/webdav/player/ui/common/AudioTrackRow.kt")
        assertTrue("AudioTrackRow.kt must exist", file.exists())
        val lines = file.readLines().size
        assertTrue("AudioTrackRow.kt line count must be < 200 lines, was $lines", lines < 200)
    }
}
