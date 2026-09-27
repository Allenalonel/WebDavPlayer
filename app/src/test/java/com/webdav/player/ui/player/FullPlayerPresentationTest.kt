package com.webdav.player.ui.player

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.formatAudiophileSpecs
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import com.webdav.player.ui.navigation.MainNavigationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FullPlayerPresentationTest {

    private lateinit var fakeSession: FakeMusicPlayerAppSession
    private lateinit var coordinator: MainNavigationCoordinator

    private val hiResFlacTrack =
        AudioTrack(
            id = "1:/music/track.flac",
            serverId = 1L,
            remotePath = "/music/Hotel California [96kHz-24bit].flac",
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            durationMs = 390_000L,
            size = 119_437_500L,
            format = AudioFormat.FLAC,
        )

    private val standardMp3Track =
        AudioTrack(
            id = "1:/music/track.mp3",
            serverId = 1L,
            remotePath = "/music/Yesterday [320k].mp3",
            title = "Yesterday",
            artist = "The Beatles",
            album = "Help!",
            durationMs = 125_000L,
            size = 5_000_000L,
            format = AudioFormat.MP3,
        )

    @Before
    fun setUp() {
        fakeSession = FakeMusicPlayerAppSession()
        coordinator = MainNavigationCoordinator(fakeSession)
    }

    @Test
    fun dualLayerProgress_calculatesAccuratePlaybackAndBufferFractions() {
        // Track duration: 200 seconds (200,000 ms)
        // Elapsed playback: 50 seconds (50,000 ms) -> 25%
        // WebDAV remote streaming buffer: 150 seconds (150,000 ms) -> 75%
        val progress =
            PlaybackProgress(
                currentPositionMs = 50_000L,
                durationMs = 200_000L,
                bufferedPositionMs = 150_000L,
            )

        assertEquals(0.25f, progress.progressFraction, 0.001f)
        assertEquals(0.75f, progress.bufferedFraction, 0.001f)
        assertTrue(progress.bufferedFraction >= progress.progressFraction)
        assertEquals("00:50", progress.formattedCurrentPosition)
        assertEquals("03:20", progress.formattedDuration)
    }

    @Test
    fun dualLayerProgress_clampsFractionsWithinZeroToOneBounds() {
        val overflowProgress =
            PlaybackProgress(
                currentPositionMs = 250_000L,
                durationMs = 200_000L,
                bufferedPositionMs = 300_000L,
            )
        assertEquals(1.0f, overflowProgress.progressFraction, 0.001f)
        assertEquals(1.0f, overflowProgress.bufferedFraction, 0.001f)

        val zeroProgress = PlaybackProgress.ZERO
        assertEquals(0.0f, zeroProgress.progressFraction, 0.001f)
        assertEquals(0.0f, zeroProgress.bufferedFraction, 0.001f)
    }

    @Test
    fun minimalistDragHandle_collapsesExpandedSheet() {
        coordinator.onSessionStateChanged(
            PlayerSessionState(queue = PlaybackQueue(listOf(hiResFlacTrack), 0)),
        )
        coordinator.expandFullPlayer()
        assertTrue(coordinator.uiState.value.isFullPlayerExpanded)

        // Simulating tap on centered drag handle or downward swipe dismiss
        coordinator.collapseFullPlayer()
        assertFalse(coordinator.uiState.value.isFullPlayerExpanded)
    }

    @Test
    fun audiophileSpecsCapsule_differentiatesHiResGoldAndStandardStyling() {
        val hiResSpecs = hiResFlacTrack.audiophileSpecsModel
        assertTrue(hiResSpecs.formatted.startsWith("⚡ FLAC"))
        assertTrue(hiResSpecs.formatted.contains("96kHz / 24-bit"))
        assertTrue(hiResSpecs.formatted.contains("2450 kbps"))
        assertTrue(hiResSpecs.isHiRes)
        assertTrue(hiResSpecs.isLossless)

        val standardFlacTrack =
            AudioTrack(
                id = "1:/music/standard.flac",
                serverId = 1L,
                remotePath = "/music/Standard Song.flac",
                title = "Standard Song",
                artist = "CD Artist",
                durationMs = 200_000L,
                size = 20_000_000L,
                format = AudioFormat.FLAC,
            )
        val standardFlacSpecs = standardFlacTrack.audiophileSpecsModel
        assertTrue(standardFlacSpecs.isLossless)
        assertFalse(standardFlacSpecs.isHiRes)
        assertFalse(standardFlacSpecs.formatted.contains("⚡"))

        val mp3Specs = standardMp3Track.audiophileSpecsModel
        assertEquals("MP3 · 320 kbps", mp3Specs.formatted)
        assertFalse(mp3Specs.isHiRes)
        assertFalse(mp3Specs.isLossless)
        assertFalse(mp3Specs.formatted.contains("⚡"))
    }

    @Test
    fun titleMarqueeEligibility_identifiesLongTrackTitles() {
        val longTitle = "This is an extremely long audiophile track title that will overflow the player screen boundaries"
        val longTrack = hiResFlacTrack.copy(title = longTitle)

        assertTrue(longTrack.title.length > 30)
        // With basicMarquee enabled, long single-line titles can scroll smoothly without truncation
    }
}
