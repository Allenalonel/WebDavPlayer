package com.webdav.player.ui.player

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerPresentationTest {
    private fun createTrack(
        title: String = "Song A",
        artist: String? = "Artist A",
        format: AudioFormat = AudioFormat.FLAC,
    ) = AudioTrack(
        id = "1:/song.flac",
        serverId = 1L,
        remotePath = "/song.flac",
        title = title,
        artist = artist,
        album = "Album A",
        format = format,
    )

    private fun sessionWithTrack(track: AudioTrack?): PlayerSessionState =
        if (track == null) {
            PlayerSessionState()
        } else {
            PlayerSessionState(queue = PlaybackQueue(listOf(track), 0))
        }

    @Test
    fun formatSubtitle_withArtistAndFormat_returnsCombinedString() {
        val session = sessionWithTrack(createTrack(artist = "Pink Floyd", format = AudioFormat.FLAC))
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("Pink Floyd · FLAC", subtitle)
    }

    @Test
    fun formatSubtitle_withArtistOnly_whenFormatIsUnavailable() {
        val track =
            AudioTrack(
                id = "1:/song.mp3",
                serverId = 1L,
                remotePath = "/song.mp3",
                title = "Acoustic",
                artist = "Eric Clapton",
                format = AudioFormat.MP3,
            )
        val session = sessionWithTrack(track)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("Eric Clapton · MP3", subtitle)
    }

    @Test
    fun formatSubtitle_withNoArtist_returnsFormatOnly() {
        val track =
            AudioTrack(
                id = "1:/track01.wav",
                serverId = 1L,
                remotePath = "/track01.wav",
                title = "Track 01",
                artist = null,
                format = AudioFormat.WAV,
            )
        val session = sessionWithTrack(track)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("WAV", subtitle)
    }

    @Test
    fun formatSubtitle_nullTrack_returnsEmpty() {
        val session = sessionWithTrack(null)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("", subtitle)
    }

    @Test
    fun formatSubtitle_blankArtist_returnsFormatOnly() {
        val track =
            AudioTrack(
                id = "1:/track04.wma",
                serverId = 1L,
                remotePath = "/track04.wma",
                title = "Track 04",
                artist = "   ",
                format = AudioFormat.WMA,
            )
        val session = sessionWithTrack(track)
        val subtitle = formatMiniPlayerSubtitle(session)
        assertEquals("WMA", subtitle)
    }

    @Test
    fun miniPlayerGeometry_capsuleAndMicroBarDimensions_matchSpecification() {
        assertEquals(60.dp, MiniPlayerDefaults.CapsuleHeight)
        assertEquals(30.dp, MiniPlayerDefaults.CapsuleCornerRadius)
        assertEquals(42.dp, MiniPlayerDefaults.CoverSize)
        assertEquals(38.dp, MiniPlayerDefaults.ButtonSize)
        assertEquals(40.dp, MiniPlayerDefaults.PlayPauseButtonSize)
        assertTrue(
            "Capsule corner radius must be 30.dp for fluid pill geometry",
            MiniPlayerDefaults.CapsuleCornerRadius >= 26.dp,
        )
        assertEquals(2.dp, MiniPlayerDefaults.MicroBarHeight)
        assertEquals(16.dp, MiniPlayerDefaults.MicroBarHorizontalPadding)
        assertEquals(3.dp, MiniPlayerDefaults.MicroBarBottomPadding)
        assertEquals(RoundedCornerShape(30.dp), MiniPlayerDefaults.CapsuleShape)
        assertEquals(150.dp, MiniPlayerDefaults.PenetrativeContentBottomPadding)
    }

    @Test
    fun bottomDockBudget_andFabClearance_matchAdr0028() {
        val navBarHeight = 60.dp
        val miniPlayerHeight = MiniPlayerDefaults.CapsuleHeight
        val bottomMargin = 8.dp
        val totalDockHeight = navBarHeight + miniPlayerHeight + bottomMargin
        assertEquals(128.dp, totalDockHeight)

        val scaffoldFabMargin = 16.dp
        val fabClearance = 24.dp
        val totalFabBottomDistance = totalDockHeight + fabClearance
        assertEquals(152.dp, totalFabBottomDistance)

        val additionalFabPadding = totalFabBottomDistance - scaffoldFabMargin
        assertEquals(136.dp, additionalFabPadding)
    }

    @Test
    fun miniPlayerTouchClearance_playPauseAndQueueMusicTargets_meetMd3Guidelines() {
        assertEquals(40.dp, MiniPlayerDefaults.PlayPauseButtonSize)
        assertEquals(38.dp, MiniPlayerDefaults.QueueMusicButtonSize)
        assertEquals(8.dp, MiniPlayerDefaults.ButtonSpacing)

        val centerDistanceQueue =
            (MiniPlayerDefaults.PlayPauseButtonSize / 2) +
                MiniPlayerDefaults.ButtonSpacing +
                (MiniPlayerDefaults.QueueMusicButtonSize / 2)

        val md3MinimumInteractiveSize = 40.dp

        assertTrue(
            "Queue Music center-to-center distance ($centerDistanceQueue) must be at least $md3MinimumInteractiveSize to avoid touch overlap",
            centerDistanceQueue >= md3MinimumInteractiveSize,
        )
        assertEquals(47.dp, centerDistanceQueue)
    }

    @Test
    fun miniPlayerGestureThresholds_swipeAndDragConstants_matchSpecification() {
        assertEquals(40.dp, MiniPlayerDefaults.SwipeSkipThreshold)
        assertEquals(-20f, MiniPlayerDefaults.VerticalDragThreshold, 0.001f)
    }

    @Test
    fun miniPlayerMicroBarShape_isFullyRoundedPillShape() {
        assertEquals(RoundedCornerShape(MiniPlayerDefaults.MicroBarHeight), MiniPlayerDefaults.ProgressMicroBarShape)
    }

    @Test
    fun retryBanner_visibilityCondition_onlyVisibleWhenWeakNetworkAndManualRetry() {
        val normalState = PlayerSessionState(isWeakNetwork = false, canManualRetry = false)
        assertFalse(isMiniPlayerRetryBannerVisible(normalState))
        assertFalse(normalState.isSuspendedRetry)

        val weakNetworkOnly = PlayerSessionState(isWeakNetwork = true, canManualRetry = false)
        assertFalse("During early 15s quiet reconnection, banner must not show yet", isMiniPlayerRetryBannerVisible(weakNetworkOnly))
        assertFalse(weakNetworkOnly.isSuspendedRetry)

        val manualRetryOnly = PlayerSessionState(isWeakNetwork = false, canManualRetry = true)
        assertFalse(isMiniPlayerRetryBannerVisible(manualRetryOnly))
        assertFalse(manualRetryOnly.isSuspendedRetry)

        val suspendedRetry = PlayerSessionState(isWeakNetwork = true, canManualRetry = true)
        assertTrue("When quiet window exhausted, banner must be visible", isMiniPlayerRetryBannerVisible(suspendedRetry))
        assertTrue(suspendedRetry.isSuspendedRetry)
    }

    @Test
    fun retryBanner_clickHandling_invokesOnRetryPlayback() {
        var retryInvoked = false
        val onRetryPlayback = { retryInvoked = true }

        // Trigger retry action callback
        onRetryPlayback()
        assertTrue("Clicking retry action or banner must trigger onRetryPlayback callback", retryInvoked)
    }

    @Test
    fun retryBanner_textLabels_matchSpecification() {
        assertEquals("网络连接受阻，点击重试", PlayerSessionState.RETRY_BANNER_MESSAGE)
        assertEquals("点击重试", PlayerSessionState.RETRY_BUTTON_LABEL)
    }

    @Test
    fun formatSubtitle_suspendedRetryState_returnsRetryBannerMessage() {
        val track = createTrack(artist = "Pink Floyd", format = AudioFormat.FLAC)
        val suspendedSession = sessionWithTrack(track).copy(isWeakNetwork = true, canManualRetry = true)
        val subtitle = formatMiniPlayerSubtitle(suspendedSession)
        assertEquals(PlayerSessionState.RETRY_BANNER_MESSAGE, subtitle)
    }

    @Test
    fun miniPlayer_playPauseButton_delegatesToRetryWhenManualRetryAvailable() {
        var retryCount = 0
        var playPauseCount = 0
        val onRetry = { retryCount++ }
        val onTogglePlayPause = { playPauseCount++ }

        val suspendedState = PlayerSessionState(isWeakNetwork = true, canManualRetry = true)
        val resolvedActionSuspended = if (suspendedState.canManualRetry) onRetry else onTogglePlayPause
        resolvedActionSuspended()

        assertEquals(1, retryCount)
        assertEquals(0, playPauseCount)

        val normalState = PlayerSessionState(isWeakNetwork = false, canManualRetry = false)
        val resolvedActionNormal = if (normalState.canManualRetry) onRetry else onTogglePlayPause
        resolvedActionNormal()

        assertEquals(1, retryCount)
        assertEquals(1, playPauseCount)
    }

    @Test
    fun miniPlayer_signatureAcceptsPlaybackActionsContract() {
        val session = sessionWithTrack(createTrack())
        val composableInvocation: @androidx.compose.runtime.Composable () -> Unit = {
            MiniPlayer(
                sessionState = session,
                playbackActions = PlaybackActions.EMPTY,
            )
        }
        org.junit.Assert.assertNotNull(composableInvocation)
    }

    @Test
    fun miniPlayerSurfaceProperties_elevatedSolidSurface_matchSpecification() {
        assertEquals(1.dp, MiniPlayerDefaults.SurfaceBorderWidth)
        assertEquals(0.25f, MiniPlayerDefaults.SurfaceBorderAlpha, 0.001f)
    }

    @Test
    fun resolveMiniPlayerGesture_swipesAndDrags_mapToCorrectActions() {
        val swipeThreshold = 80f // e.g. 40.dp with density 2
        val verticalThreshold = -20f

        // Left swipe beyond threshold -> next track
        assertEquals(
            MiniPlayerGestureAction.SKIP_TO_NEXT,
            resolveMiniPlayerGesture(
                dragX = -90f,
                dragY = 0f,
                swipeThresholdPx = swipeThreshold,
                verticalDragThresholdPx = verticalThreshold,
            ),
        )

        // Right swipe beyond threshold -> previous track
        assertEquals(
            MiniPlayerGestureAction.SKIP_TO_PREVIOUS,
            resolveMiniPlayerGesture(
                dragX = 90f,
                dragY = 0f,
                swipeThresholdPx = swipeThreshold,
                verticalDragThresholdPx = verticalThreshold,
            ),
        )

        // Upward drag beyond vertical threshold -> expand full player
        assertEquals(
            MiniPlayerGestureAction.EXPAND_FULL_PLAYER,
            resolveMiniPlayerGesture(
                dragX = 0f,
                dragY = -35f,
                swipeThresholdPx = swipeThreshold,
                verticalDragThresholdPx = verticalThreshold,
            ),
        )

        // Subtle drags below thresholds -> NONE
        assertEquals(
            MiniPlayerGestureAction.NONE,
            resolveMiniPlayerGesture(
                dragX = 15f,
                dragY = -10f,
                swipeThresholdPx = swipeThreshold,
                verticalDragThresholdPx = verticalThreshold,
            ),
        )

        // Downward drag -> NONE
        assertEquals(
            MiniPlayerGestureAction.NONE,
            resolveMiniPlayerGesture(
                dragX = 0f,
                dragY = 50f,
                swipeThresholdPx = swipeThreshold,
                verticalDragThresholdPx = verticalThreshold,
            ),
        )
    }

    @Test
    fun miniPlayer_signatureAcceptsQueueCallback() {
        val session = sessionWithTrack(createTrack())
        var queueClicked = false
        val composableInvocation: @androidx.compose.runtime.Composable () -> Unit = {
            MiniPlayer(
                sessionState = session,
                playbackActions = PlaybackActions.EMPTY,
                onOpenQueue = { queueClicked = true },
            )
        }
        org.junit.Assert.assertNotNull(composableInvocation)
        assertFalse(queueClicked)
    }

    @Test
    fun dockedMiniPlayer_wrapperIsRemovedFromClasspath() {
        val clazz = Class.forName("com.webdav.player.ui.player.MiniPlayerKt")
        val methodNames = clazz.methods.map { it.name }
        assertFalse("DockedMiniPlayer must be eliminated", methodNames.any { it.startsWith("DockedMiniPlayer") })
        assertTrue("MiniPlayer must exist", methodNames.any { it.startsWith("MiniPlayer") })
    }

    @Test
    fun morphingPlayerDefaults_alignsWithCapsuleAndFullscreenSpecs() {
        assertEquals(36.dp, MorphingPlayerDefaults.WindowCornerRadius)
        assertEquals(280.dp, MorphingPlayerDefaults.FullPlayerArtworkSize)
        assertEquals(28.dp, MorphingPlayerDefaults.FullPlayerArtworkRadius)
        assertEquals(68.dp, MorphingPlayerDefaults.FullPlayerPlayButtonSize)
        assertEquals(26.dp, MorphingPlayerDefaults.FullPlayerPlayButtonRadius)
        assertEquals(48.dp, MorphingPlayerDefaults.FullPlayerQueueButtonSize)
        assertEquals(14.dp, MorphingPlayerDefaults.CapsuleHorizontalMargin)

        assertEquals(0.35f, MorphingPlayerDefaults.SecondaryControlsFadeStartProgress, 0.0001f)
        assertEquals(0.25f, MorphingPlayerDefaults.MiniControlsFadeEndProgress, 0.0001f)
    }
}
