package com.webdav.player.data.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioRouting
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class Media3AudioPlayerEngineTest {
    private lateinit var context: Context
    private lateinit var engine: Media3AudioPlayerEngine

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Local Test",
            url = "http://127.0.0.1:8080/dav",
            port = 8080,
            pathPrefix = "/dav",
        )

    private val track1 =
        AudioTrack(
            id = "1:/Music/01.mp3",
            serverId = 1L,
            remotePath = "/Music/01.mp3",
            title = "01.mp3",
            format = AudioFormat.MP3,
            size = 1000L,
        )

    private val track2 =
        AudioTrack(
            id = "1:/Music/02.flac",
            serverId = 1L,
            remotePath = "/Music/02.flac",
            title = "02.flac",
            format = AudioFormat.FLAC,
            size = 2000L,
        )

    private val track3 =
        AudioTrack(
            id = "1:/Music/03.wav",
            serverId = 1L,
            remotePath = "/Music/03.wav",
            title = "03.wav",
            format = AudioFormat.WAV,
            size = 3000L,
        )

    private val trackWma =
        AudioTrack(
            id = "1:/Music/04.wma",
            serverId = 1L,
            remotePath = "/Music/04.wma",
            title = "04.wma",
            format = AudioFormat.WMA,
            size = 4000L,
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val adapter = DefaultWebDavMediaSourceAdapter(context)
        engine = Media3AudioPlayerEngine(context, mediaSourceAdapter = adapter)
    }

    @After
    fun tearDown() {
        engine.release()
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(PlaybackState.Idle, engine.playbackState.value)
        assertEquals(0L, engine.currentPositionMs.value)
        assertEquals(0L, engine.durationMs.value)
    }

    @Test
    fun loadTrack_setsStartPosition_andPreparesPlayer() {
        engine.loadTrack(
            server = testServer,
            track = track2,
            startPositionMs = 0L,
        )

        assertEquals(1, engine.player.mediaItemCount)
    }

    @Test
    fun loadTrack_withMediaSourceAdapter_preparesExoPlayerMediaSources() {
        val adapter = DefaultWebDavMediaSourceAdapter(context)
        val customEngine = Media3AudioPlayerEngine(context, mediaSourceAdapter = adapter)
        try {
            customEngine.loadTrack(
                server = testServer,
                track = track1,
                startPositionMs = 0L,
            )
            assertEquals(1, customEngine.player.mediaItemCount)
            assertEquals(
                "audio/mpeg",
                customEngine.player
                    .getMediaItemAt(0)
                    .localConfiguration
                    ?.mimeType,
            )
        } finally {
            customEngine.release()
        }
    }

    @Test
    fun prerollTrack_withMediaSourceAdapter_addsMediaSource() {
        val adapter = DefaultWebDavMediaSourceAdapter(context)
        val customEngine = Media3AudioPlayerEngine(context, mediaSourceAdapter = adapter)
        try {
            customEngine.loadTrack(
                server = testServer,
                track = track1,
            )
            assertEquals(1, customEngine.player.mediaItemCount)

            customEngine.prerollTrack(testServer, trackWma)
            assertEquals(2, customEngine.player.mediaItemCount)
            assertEquals(
                "audio/x-ms-wma",
                customEngine.player
                    .getMediaItemAt(1)
                    .localConfiguration
                    ?.mimeType,
            )
        } finally {
            customEngine.release()
        }
    }

    @Test
    fun loadTrack_withWmaTrack_setsWmaMimeTypeAndPrepares() {
        engine.loadTrack(
            server = testServer,
            track = trackWma,
        )

        assertEquals(1, engine.player.mediaItemCount)
        val mediaItem = engine.player.getMediaItemAt(0)
        assertEquals("audio/x-ms-wma", mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun seekTo_andStop_updatesStateCorrectly() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )

        engine.seekTo(3000L)
        assertEquals(3000L, engine.currentPositionMs.value)

        engine.stop()
        assertEquals(PlaybackState.Idle, engine.playbackState.value)
        assertEquals(0L, engine.currentPositionMs.value)
    }

    @Test
    fun setVolume_andGetVolume_updatesPlayerVolume() {
        engine.setVolume(0.5f)
        assertEquals(0.5f, engine.getVolume(), 0.01f)

        engine.setVolume(0.2f)
        assertEquals(0.2f, engine.getVolume(), 0.01f)

        engine.setVolume(1.0f)
        assertEquals(1.0f, engine.getVolume(), 0.01f)
    }

    @Test
    fun updateTrack_onActivelyPlayingTrack_doesNotCallReplaceMediaItem_andPreservesPositionAndTimeline() {
        engine.loadTrack(
            server = testServer,
            track = track1,
            startPositionMs = 5000L,
        )

        val originalItem = engine.player.getMediaItemAt(0)
        assertEquals(5000L, engine.currentPositionMs.value)

        var observedPlaylistMeta: MediaMetadata? = null
        val listener =
            object : Player.Listener {
                override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
                    observedPlaylistMeta = mediaMetadata
                }
            }
        engine.player.addListener(listener)

        val updated = track1.copy(title = "Updated Song Title", artist = "Updated Artist", album = "Updated Album")
        engine.updateTrackMetadata(updated)

        // Active playing MediaItem is NOT replaced to avoid decoder destruction / rebuffering glitches
        val currentItem = engine.player.getMediaItemAt(0)
        assertEquals("01.mp3", currentItem.mediaMetadata.title?.toString())
        assertEquals(originalItem.mediaMetadata.title, currentItem.mediaMetadata.title)
        assertEquals(1, engine.player.mediaItemCount)

        // Position is preserved and not reset
        assertEquals(5000L, engine.currentPositionMs.value)
        assertTrue(engine.playbackState.value is PlaybackState.Playing || engine.playbackState.value is PlaybackState.Buffering)

        // Playlist metadata on player must be updated non-destructively for external presentation flows
        assertEquals(
            "Updated Song Title",
            engine.player.playlistMetadata.title
                ?.toString(),
        )
        assertEquals(
            "Updated Artist",
            engine.player.playlistMetadata.artist
                ?.toString(),
        )
        assertEquals(
            "Updated Album",
            engine.player.playlistMetadata.albumTitle
                ?.toString(),
        )
        assertEquals("Updated Song Title", observedPlaylistMeta?.title?.toString())
        assertEquals("Updated Artist", observedPlaylistMeta?.artist?.toString())
        assertEquals("Updated Album", observedPlaylistMeta?.albumTitle?.toString())
        engine.player.removeListener(listener)
    }

    @Test
    fun updateTrack_withIdenticalMetadata_doesNotTriggerUnnecessaryUpdates() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )

        val updated = track1.copy(title = "Updated Title")
        engine.updateTrackMetadata(updated)
        assertEquals(
            "Updated Title",
            engine.player.playlistMetadata.title
                ?.toString(),
        )

        // Calling updateTrackMetadata with the exact same track should be a no-op
        engine.updateTrackMetadata(updated)
        assertEquals(
            "Updated Title",
            engine.player.playlistMetadata.title
                ?.toString(),
        )
    }

    @Test
    fun activeServer_initiallyNull() {
        assertNull(engine.activeServer)
    }

    @Test
    fun loadTrack_tracksActiveServer() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )
        assertEquals(testServer, engine.activeServer)
    }

    @Test
    fun loadTrack_whenActiveServerNull_tracksServer() {
        assertNull(engine.activeServer)
        engine.loadTrack(testServer, track1)
        assertEquals(testServer, engine.activeServer)
    }

    @Test
    fun delegatingDataSourceFactory_whenActiveServerNull_fallsBackToDefaultDataSource() {
        assertNull(engine.activeServer)
        val dataSource = engine.delegatingDataSourceFactory.createDataSource()
        assertNotNull(dataSource)
        assertTrue(
            "Expected DefaultDataSource when activeServer is null, but was ${dataSource::class.java.name}",
            dataSource is DefaultDataSource,
        )
    }

    @Test
    fun delegatingDataSourceFactory_whenActiveServerSet_delegatesToMediaSourceAdapter() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )
        val dataSource = engine.delegatingDataSourceFactory.createDataSource()
        assertNotNull(dataSource)
        assertTrue(
            "Expected ResilientWebDavDataSource when activeServer is set, but was ${dataSource::class.java.name}",
            dataSource is ResilientWebDavDataSource,
        )
    }

    @Test
    fun updateTrack_replacesMediaItem_andRetainsActiveServerContext() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )
        assertEquals(testServer, engine.activeServer)

        val updated = track1.copy(title = "Rebuilt Title")
        engine.updateTrackMetadata(updated)

        // Verifies activeServer remains preserved
        assertEquals(testServer, engine.activeServer)
        assertEquals(
            "Rebuilt Title",
            engine.player.playlistMetadata.title
                ?.toString(),
        )

        // Delegating factory provides active server's ResilientWebDavDataSource
        val dataSource = engine.delegatingDataSourceFactory.createDataSource()
        assertTrue(dataSource is ResilientWebDavDataSource)
    }

    @Test
    fun loadControl_bufferThresholds_andBackBuffer_verified() {
        val defaultConfig = engine.loadControlConfig
        assertEquals(10_000, defaultConfig.backBufferDurationMs)
        assertEquals(false, defaultConfig.retainBackBufferFromKeyframe)
        assertEquals(15_000, defaultConfig.minBufferMs)
        assertEquals(50_000, defaultConfig.maxBufferMs)
        assertEquals(1_500, defaultConfig.bufferForPlaybackMs)
        assertEquals(3_000, defaultConfig.bufferForPlaybackAfterRebufferMs)
        assertEquals(true, defaultConfig.prioritizeTimeOverSizeThresholds)

        val weakNetworkConfig = StreamingLoadControlConfig.createWeakNetworkResilientConfig()
        assertEquals(20_000, weakNetworkConfig.minBufferMs)
        assertEquals(90_000, weakNetworkConfig.maxBufferMs)

        val customConfig =
            StreamingLoadControlConfig(
                backBufferDurationMs = 20_000,
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
            )
        val customEngine =
            Media3AudioPlayerEngine(
                context = context,
                loadControlConfig = customConfig,
            )
        assertEquals(20_000, customEngine.loadControlConfig.backBufferDurationMs)
        assertEquals(30_000, customEngine.loadControlConfig.minBufferMs)
        assertEquals(60_000, customEngine.loadControlConfig.maxBufferMs)
        customEngine.release()
    }

    @Test
    fun engine_doesNotExposeMediaSessionOrSessionPlayerOrReverseCallbackHooks() {
        val declaredMethods =
            Media3AudioPlayerEngine::class.java.declaredMethods
                .map { it.name }
                .toSet()
        val declaredFields =
            Media3AudioPlayerEngine::class.java.declaredFields
                .map { it.name }
                .toSet()

        assertFalse("Media3AudioPlayerEngine must not expose getMediaSession", "getMediaSession" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not expose getSessionPlayer", "getSessionPlayer" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not expose getSessionCallback", "getSessionCallback" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not expose setSessionCallback", "setSessionCallback" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not expose getOnSkipToNext", "getOnSkipToNext" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not expose setOnSkipToNext", "setOnSkipToNext" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not expose getOnSkipToPrevious", "getOnSkipToPrevious" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not expose setOnSkipToPrevious", "setOnSkipToPrevious" in declaredMethods)
        assertFalse("Media3AudioPlayerEngine must not retain mediaSession field", "mediaSession" in declaredFields)
        assertFalse("Media3AudioPlayerEngine must not retain sessionPlayer field", "sessionPlayer" in declaredFields)
        assertFalse("Media3AudioPlayerEngine must not retain sessionCallback field", "sessionCallback" in declaredFields)
        assertFalse("Media3AudioPlayerEngine must not retain onSkipToNext field", "onSkipToNext" in declaredFields)
        assertFalse("Media3AudioPlayerEngine must not retain onSkipToPrevious field", "onSkipToPrevious" in declaredFields)
    }

    @Test
    fun backgroundThread_prerollAndOperations_doNotThrowWrongThreadException() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        assertEquals(1, engine.player.mediaItemCount)

        var error: Throwable? = null
        val workerThread =
            Thread {
                try {
                    engine.prerollTrack(testServer, track2)
                    engine.setVolume(0.8f)
                    val vol = engine.getVolume()
                    assertEquals(0.8f, vol, 0.01f)
                    engine.updateTrackMetadata(track1.copy(title = "BG Updated"))
                } catch (t: Throwable) {
                    error = t
                }
            }
        workerThread.start()
        workerThread.join()

        assertNull("Operations invoked from background thread must not throw Wrong Thread exceptions", error)

        // Drain the application looper queue
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        assertEquals(2, engine.player.mediaItemCount)
        assertEquals(0.8f, engine.getVolume(), 0.01f)

        // Test background clearPrerollTrack
        val clearThread =
            Thread {
                try {
                    engine.clearPrerollTrack()
                } catch (t: Throwable) {
                    error = t
                }
            }
        clearThread.start()
        clearThread.join()

        assertNull("clearPrerollTrack on worker thread must not throw", error)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        assertEquals(1, engine.player.mediaItemCount)
    }

    @Test
    fun loadTrack_startsPlaybackOfSingleTrack_atSpecifiedPosition() {
        engine.loadTrack(
            server = testServer,
            track = track1,
            startPositionMs = 3500L,
        )

        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        assertEquals(1, engine.player.mediaItemCount)
        assertEquals(3500L, engine.currentPositionMs.value)
        assertTrue(
            engine.playbackState.value is PlaybackState.Playing ||
                engine.playbackState.value is PlaybackState.Buffering,
        )
        assertEquals(
            "audio/mpeg",
            engine.player
                .getMediaItemAt(0)
                .localConfiguration
                ?.mimeType,
        )
    }

    @Test
    fun prerollTrack_and_clearPrerollTrack_properlyAddAndRemovePrerollSource() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        assertEquals(1, engine.player.mediaItemCount)

        // Preroll next track
        engine.prerollTrack(testServer, track2)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        assertEquals(2, engine.player.mediaItemCount)
        assertEquals(
            "audio/flac",
            engine.player
                .getMediaItemAt(1)
                .localConfiguration
                ?.mimeType,
        )

        // Preroll when item count already >= 2 does not add duplicate
        engine.prerollTrack(testServer, track3)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        assertEquals(2, engine.player.mediaItemCount)

        // Clear preroll track
        engine.clearPrerollTrack()
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        assertEquals(1, engine.player.mediaItemCount)

        // Redundant clearPrerollTrack is safe
        engine.clearPrerollTrack()
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        assertEquals(1, engine.player.mediaItemCount)
    }

    @Test
    fun updateTrackMetadata_updatesMetadataNonDisruptively() {
        engine.loadTrack(
            server = testServer,
            track = track1,
            startPositionMs = 5000L,
        )
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        val originalItem = engine.player.getMediaItemAt(0)
        assertEquals(5000L, engine.currentPositionMs.value)

        val updated = track1.copy(title = "Enriched Stream Title", artist = "Enriched Artist")
        engine.updateTrackMetadata(updated)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        // Active playing MediaItem is NOT replaced to avoid decoder destruction / rebuffering glitches
        val currentItem = engine.player.getMediaItemAt(0)
        assertEquals("01.mp3", currentItem.mediaMetadata.title?.toString())
        assertEquals(originalItem.mediaMetadata.title, currentItem.mediaMetadata.title)

        // Position is preserved
        assertEquals(5000L, engine.currentPositionMs.value)
        assertTrue(
            engine.playbackState.value is PlaybackState.Playing ||
                engine.playbackState.value is PlaybackState.Buffering,
        )

        // player playlistMetadata is updated
        assertEquals(
            "Enriched Stream Title",
            engine.player.playlistMetadata.title
                ?.toString(),
        )
        assertEquals(
            "Enriched Artist",
            engine.player.playlistMetadata.artist
                ?.toString(),
        )
    }

    private class TestControllablePlayer(
        wrapped: Player,
    ) : ForwardingPlayer(wrapped) {
        var customPlaybackState: Int = Player.STATE_IDLE
        var customCurrentPosition: Long = 0L
        var customDuration: Long = C.TIME_UNSET
        var customBufferedPosition: Long = 0L
        var customIsPlaying: Boolean = false
        var customPlayWhenReady: Boolean = false
        val customListeners = CopyOnWriteArrayList<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            customListeners.add(listener)
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            customListeners.remove(listener)
            super.removeListener(listener)
        }

        override fun getPlaybackState(): Int = customPlaybackState

        override fun getCurrentPosition(): Long = customCurrentPosition

        override fun getDuration(): Long = customDuration

        override fun getBufferedPosition(): Long = customBufferedPosition

        override fun isPlaying(): Boolean = customIsPlaying

        override fun getPlayWhenReady(): Boolean = customPlayWhenReady

        fun notifyPlaybackState(state: Int) {
            customPlaybackState = state
            for (listener in customListeners) {
                listener.onPlaybackStateChanged(state)
            }
        }

        fun notifyIsPlaying(isPlaying: Boolean) {
            customIsPlaying = isPlaying
            for (listener in customListeners) {
                listener.onIsPlayingChanged(isPlaying)
            }
        }

        fun notifyPositionDiscontinuity(
            oldPos: Long,
            newPos: Long,
            reason: Int = Player.DISCONTINUITY_REASON_SEEK,
        ) {
            customCurrentPosition = newPos
            val oldInfo = Player.PositionInfo(null, 0, null, null, 0, oldPos, oldPos, -1, -1)
            val newInfo = Player.PositionInfo(null, 0, null, null, 0, newPos, newPos, -1, -1)
            for (listener in customListeners) {
                listener.onPositionDiscontinuity(oldInfo, newInfo, reason)
            }
        }

        fun notifyPlayWhenReady(
            playWhenReady: Boolean,
            reason: Int = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        ) {
            customPlayWhenReady = playWhenReady
            for (listener in customListeners) {
                listener.onPlayWhenReadyChanged(playWhenReady, reason)
            }
        }

        fun notifyTimelineChanged(reason: Int = Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
            for (listener in customListeners) {
                listener.onTimelineChanged(currentTimeline, reason)
            }
        }

        fun notifyPlayerError(error: androidx.media3.common.PlaybackException) {
            for (listener in customListeners) {
                listener.onPlayerError(error)
            }
        }
    }

    @Test
    fun onPlaybackStateChanged_stateEnded_refreshesPositionAndDurationBeforeEmittingEndedState() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                controllablePlayer.customPlaybackState = Player.STATE_READY
                controllablePlayer.customPlayWhenReady = true
                controllablePlayer.customDuration = 180_000L
                controllablePlayer.customCurrentPosition = 175_000L
                controllablePlayer.notifyPlaybackState(Player.STATE_READY)

                var observedPositionAtEnded: Long? = null
                var observedDurationAtEnded: Long? = null
                val collectJob =
                    launch {
                        testEngine.playbackState.collect { state ->
                            if (state is PlaybackState.Ended) {
                                observedPositionAtEnded = testEngine.currentPositionMs.value
                                observedDurationAtEnded = testEngine.durationMs.value
                            }
                        }
                    }
                testScheduler.runCurrent()

                // Simulate stream natural completion
                controllablePlayer.customCurrentPosition = 180_000L
                controllablePlayer.notifyPlaybackState(Player.STATE_ENDED)
                ShadowLooper.idleMainLooper()
                testScheduler.runCurrent()

                assertEquals(180_000L, observedPositionAtEnded)
                assertEquals(180_000L, observedDurationAtEnded)
                assertEquals(PlaybackState.Ended, testEngine.playbackState.value)
                collectJob.cancel()
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun onIsPlayingChanged_refreshesPositionAndDurationBeforeEmittingState() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                controllablePlayer.customPlaybackState = Player.STATE_READY
                controllablePlayer.customPlayWhenReady = true
                controllablePlayer.customDuration = 180_000L
                controllablePlayer.customCurrentPosition = 50_000L
                controllablePlayer.notifyIsPlaying(true)
                ShadowLooper.idleMainLooper()

                // Pause occurs at 52_000L
                controllablePlayer.customPlayWhenReady = false
                controllablePlayer.customCurrentPosition = 52_000L

                var observedPositionAtPaused: Long? = null
                val collectJob =
                    launch {
                        testEngine.playbackState.collect { state ->
                            if (state is PlaybackState.Paused) {
                                observedPositionAtPaused = testEngine.currentPositionMs.value
                            }
                        }
                    }
                testScheduler.runCurrent()

                controllablePlayer.notifyIsPlaying(false)
                ShadowLooper.idleMainLooper()
                testScheduler.runCurrent()

                assertEquals(52_000L, observedPositionAtPaused)
                assertEquals(52_000L, testEngine.currentPositionMs.value)
                collectJob.cancel()
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun onPositionDiscontinuity_refreshesPositionAndDuration() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                controllablePlayer.customDuration = 240_000L
                controllablePlayer.customCurrentPosition = 30_000L
                controllablePlayer.notifyPlaybackState(Player.STATE_READY)

                // Seek occurs to 90_000L
                controllablePlayer.notifyPositionDiscontinuity(oldPos = 30_000L, newPos = 90_000L)
                ShadowLooper.idleMainLooper()

                assertEquals(90_000L, testEngine.currentPositionMs.value)
                assertEquals(240_000L, testEngine.durationMs.value)
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun physicalDuration_takesPrecedenceOverStaticMetadataDuration_onceProbed() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                // Initial track has inflated metadata duration of 210_000L
                val trackWithMeta = track1.copy(durationMs = 210_000L)
                testEngine.loadTrack(testServer, trackWithMeta, startPositionMs = 0L)
                ShadowLooper.idleMainLooper()

                // Metadata duration is initially established
                assertEquals(210_000L, testEngine.durationMs.value)

                // Decoder probes physical stream and establishes true duration of 200_000L
                controllablePlayer.customDuration = 200_000L
                controllablePlayer.notifyTimelineChanged()
                ShadowLooper.idleMainLooper()

                // Physical duration overrides metadata duration
                assertEquals(200_000L, testEngine.durationMs.value)

                // Also verify on playback state change
                controllablePlayer.notifyPlaybackState(Player.STATE_READY)
                ShadowLooper.idleMainLooper()
                assertEquals(200_000L, testEngine.durationMs.value)
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun stateEnded_probesActualClockPosition_withoutSyntheticClamping() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                controllablePlayer.customDuration = 180_000L
                controllablePlayer.customCurrentPosition = 179_950L

                var capturedPosition: Long? = null
                var capturedDuration: Long? = null
                val collectJob =
                    launch {
                        testEngine.playbackState.collect { state ->
                            if (state is PlaybackState.Ended) {
                                capturedPosition = testEngine.currentPositionMs.value
                                capturedDuration = testEngine.durationMs.value
                            }
                        }
                    }
                testScheduler.runCurrent()

                controllablePlayer.notifyPlaybackState(Player.STATE_ENDED)
                ShadowLooper.idleMainLooper()
                testScheduler.runCurrent()

                // Spec: Synchronously probe actual clock position, no synthetic clamping
                assertEquals(179_950L, capturedPosition)
                assertEquals(180_000L, capturedDuration)
                assertEquals(179_950L, testEngine.currentPositionMs.value)
                assertEquals(180_000L, testEngine.durationMs.value)
                collectJob.cancel()
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun stateEnded_preservesActualClockPosition_forPrematureEofDetection() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                controllablePlayer.customDuration = 180_000L
                // Premature EOF at 50,000 ms (< 95% of duration)
                controllablePlayer.customCurrentPosition = 50_000L

                var capturedPosition: Long? = null
                val collectJob =
                    launch {
                        testEngine.playbackState.collect { state ->
                            if (state is PlaybackState.Ended) {
                                capturedPosition = testEngine.currentPositionMs.value
                            }
                        }
                    }
                testScheduler.runCurrent()

                controllablePlayer.notifyPlaybackState(Player.STATE_ENDED)
                ShadowLooper.idleMainLooper()
                testScheduler.runCurrent()

                // Must preserve 50_000L so Tier 3 premature EOF arbitration can trigger
                assertEquals(50_000L, capturedPosition)
                assertEquals(50_000L, testEngine.currentPositionMs.value)
                collectJob.cancel()
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun toPlaybackError_mapsPlaybackExceptionToPlaybackStateError() {
        val basePlayer = ExoPlayer.Builder(context).build()
        val testEngine = Media3AudioPlayerEngine(context, customPlayer = basePlayer)
        try {
            val unsupportedEx = UnsupportedRangeReconnectionException("HTTP 200 on Range")
            val wrappedEx =
                androidx.media3.common.PlaybackException(
                    "Wrapper error",
                    java.io.IOException("Nested IO", unsupportedEx),
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                )

            val errorState = testEngine.toPlaybackError(wrappedEx)
            assertEquals("HTTP 200 on Range", errorState.message)
            assertEquals(wrappedEx, errorState.cause)
        } finally {
            testEngine.release()
        }
    }

    @Test
    fun stop_resetsPositionAndDurationToZero() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                controllablePlayer.customDuration = 180_000L
                controllablePlayer.customCurrentPosition = 120_000L
                controllablePlayer.notifyPlaybackState(Player.STATE_READY)
                ShadowLooper.idleMainLooper()

                assertEquals(120_000L, testEngine.currentPositionMs.value)
                assertEquals(180_000L, testEngine.durationMs.value)

                testEngine.stop()
                ShadowLooper.idleMainLooper()

                assertEquals(0L, testEngine.currentPositionMs.value)
                assertEquals(0L, testEngine.durationMs.value)
                assertEquals(PlaybackState.Idle, testEngine.playbackState.value)
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun audioRoutingObserver_startsOnEngineCreation_andStopsOnEngineRelease() {
        val testEngine = Media3AudioPlayerEngine(context)
        try {
            assertTrue(testEngine.audioRoutingObserver.isObserving)
        } finally {
            testEngine.release()
        }
        assertFalse(testEngine.audioRoutingObserver.isObserving)
    }

    @Test
    fun onAudioRoutingChanged_flushesActiveAudioSink_andMaintainsPlaybackStability() =
        runTest {
            var flushCount = 0
            val underlyingSink = DefaultAudioSink.Builder(context).build()
            val trackingSink =
                object : ForwardingAudioSink(underlyingSink) {
                    override fun flush() {
                        flushCount++
                        super.flush()
                    }
                }

            val testEngine = Media3AudioPlayerEngine(context, customAudioSink = trackingSink)
            try {
                assertEquals(trackingSink, testEngine.activeAudioSink)
                assertEquals(0, flushCount)

                // Dispatch routing changed event
                testEngine.audioRoutingObserver.dispatchRoutingChanged()
                ShadowLooper.idleMainLooper()

                assertEquals(1, flushCount)
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun onAudioRoutingChanged_duringActivePlayback_preservesPlaybackPositionAndState() =
        runTest {
            var flushCount = 0
            val underlyingSink = DefaultAudioSink.Builder(context).build()
            val trackingSink =
                object : ForwardingAudioSink(underlyingSink) {
                    override fun flush() {
                        flushCount++
                        super.flush()
                    }
                }

            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine =
                Media3AudioPlayerEngine(
                    context,
                    customPlayer = controllablePlayer,
                    customAudioSink = trackingSink,
                )
            try {
                controllablePlayer.customDuration = 300_000L
                controllablePlayer.customCurrentPosition = 45_000L
                controllablePlayer.customPlayWhenReady = true
                controllablePlayer.customIsPlaying = true
                controllablePlayer.notifyPlaybackState(Player.STATE_READY)
                ShadowLooper.idleMainLooper()

                assertEquals(PlaybackState.Playing, testEngine.playbackState.value)
                assertEquals(45_000L, testEngine.currentPositionMs.value)

                // Route switch happens (e.g. bluetooth headset box opened)
                testEngine.onAudioRoutingChanged()
                ShadowLooper.idleMainLooper()

                assertEquals(1, flushCount)
                assertEquals(PlaybackState.Playing, testEngine.playbackState.value)
                assertEquals(45_000L, testEngine.currentPositionMs.value)
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun onAudioRoutingChanged_flushesCanonicalProcessorAndActiveAudioSink() =
        runTest {
            var sinkFlushCount = 0
            val underlyingSink = DefaultAudioSink.Builder(context).build()
            val trackingSink =
                object : ForwardingAudioSink(underlyingSink) {
                    override fun flush() {
                        sinkFlushCount++
                        super.flush()
                    }
                }

            val testEngine = Media3AudioPlayerEngine(context, customAudioSink = trackingSink)
            try {
                val lowRateFormat =
                    androidx.media3.common.audio.AudioProcessor.AudioFormat(
                        22050,
                        2,
                        C.ENCODING_PCM_16BIT,
                    )
                val outFormat = testEngine.canonicalAudioProcessor.configure(lowRateFormat)
                assertTrue(testEngine.canonicalAudioProcessor.isActive)
                assertEquals(44100, outFormat.sampleRate)
                assertEquals(0, sinkFlushCount)

                // Dispatch dynamic audio routing change
                testEngine.onAudioRoutingChanged()
                ShadowLooper.idleMainLooper()

                assertEquals(1, sinkFlushCount)
                assertTrue(testEngine.canonicalAudioProcessor.isActive)
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun canonicalAudioProcessor_upsamplesLowSampleRateStreamsTo44100HzStereo_andPassthroughStandardRates() {
        val processor = engine.canonicalAudioProcessor

        // 1. Sub-44100Hz mono (e.g. 16000Hz voice/audiobook) -> upsampled to 44100Hz stereo
        val mono16kFormat =
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                16000,
                1,
                C.ENCODING_PCM_16BIT,
            )
        val monoOut = processor.configure(mono16kFormat)
        processor.flush()
        assertTrue(processor.isActive)
        assertEquals(44100, monoOut.sampleRate)
        assertEquals(2, monoOut.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, monoOut.encoding)

        val inputBuffer =
            java.nio.ByteBuffer
                .allocateDirect(512)
                .order(java.nio.ByteOrder.nativeOrder())
        while (inputBuffer.hasRemaining()) {
            inputBuffer.putShort(200)
        }
        inputBuffer.flip()
        processor.queueInput(inputBuffer)
        val resampledOutput = processor.output
        assertTrue("Resampled stereo output must contain PCM bytes", resampledOutput.remaining() > 0)

        // 2. Sub-44100Hz stereo (22050Hz) -> upsampled to 44100Hz stereo
        val stereo22kFormat =
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                22050,
                2,
                C.ENCODING_PCM_16BIT,
            )
        val stereoOut = processor.configure(stereo22kFormat)
        processor.flush()
        assertTrue(processor.isActive)
        assertEquals(44100, stereoOut.sampleRate)
        assertEquals(2, stereoOut.channelCount)

        // 3. Standard/Hi-Res (>= 44100Hz) -> passthrough (isActive = false)
        val standard44kFormat =
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                44100,
                2,
                C.ENCODING_PCM_16BIT,
            )
        val passthroughOut = processor.configure(standard44kFormat)
        assertFalse(processor.isActive)
        assertEquals(androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET, passthroughOut)
    }

    @Test
    fun audioRoutingObserver_deviceConnectDisconnectAndRoutingChange_flushesAudioPipelineInternally() =
        runTest {
            var flushCount = 0
            val underlyingSink = DefaultAudioSink.Builder(context).build()
            val trackingSink =
                object : ForwardingAudioSink(underlyingSink) {
                    override fun flush() {
                        flushCount++
                        super.flush()
                    }
                }

            val testEngine = Media3AudioPlayerEngine(context, customAudioSink = trackingSink)
            try {
                val callback = requireNotNull(testEngine.audioRoutingObserver.internalCallback)

                fun createSinkDevice(type: Int): AudioDeviceInfo {
                    val device =
                        org.robolectric.shadows.AudioDeviceInfoBuilder
                            .newBuilder()
                            .setType(type)
                            .build()
                    val port =
                        org.robolectric.util.ReflectionHelpers
                            .getField<Any>(device, "mPort")
                    org.robolectric.util.ReflectionHelpers
                        .setField(port, "mRole", 2)
                    return device
                }

                // 1. Bluetooth sink connected -> flushes pipeline internally
                val bluetoothSink = createSinkDevice(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                callback.onAudioDevicesAdded(arrayOf(bluetoothSink))
                ShadowLooper.idleMainLooper()
                assertEquals(1, flushCount)

                // 2. Wired headset unplugged -> flushes pipeline internally
                val wiredHeadset = createSinkDevice(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                callback.onAudioDevicesRemoved(arrayOf(wiredHeadset))
                ShadowLooper.idleMainLooper()
                assertEquals(2, flushCount)

                // 3. Dynamic AudioRouting change -> flushes pipeline internally
                val fakeRouting = TestAudioRouting()
                testEngine.attachAudioRouting(fakeRouting)
                fakeRouting.addedListener?.onRoutingChanged(fakeRouting)
                ShadowLooper.idleMainLooper()
                assertEquals(3, flushCount)
            } finally {
                testEngine.release()
            }
        }

    private class TestAudioRouting : AudioRouting {
        var addedListener: AudioRouting.OnRoutingChangedListener? = null

        override fun addOnRoutingChangedListener(
            listener: AudioRouting.OnRoutingChangedListener,
            handler: Handler?,
        ) {
            addedListener = listener
        }

        override fun removeOnRoutingChangedListener(listener: AudioRouting.OnRoutingChangedListener) {}

        override fun getRoutedDevice(): AudioDeviceInfo? = null

        override fun setPreferredDevice(deviceInfo: AudioDeviceInfo?): Boolean = true

        override fun getPreferredDevice(): AudioDeviceInfo? = null
    }

    @Test
    fun attachAndDetachAudioRouting_delegatesToAudioRoutingObserver() {
        val testEngine = Media3AudioPlayerEngine(context)
        try {
            val fakeRouting = TestAudioRouting()

            testEngine.attachAudioRouting(fakeRouting)
            assertEquals(fakeRouting, testEngine.audioRoutingObserver.activeAudioRouting)

            testEngine.detachAudioRouting()
            assertEquals(null, testEngine.audioRoutingObserver.activeAudioRouting)
        } finally {
            testEngine.release()
        }
    }

    @Test
    fun bufferingState_withPlayWhenReadyTrue_emitsBuffering() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                controllablePlayer.customPlaybackState = Player.STATE_BUFFERING
                controllablePlayer.customPlayWhenReady = true
                controllablePlayer.notifyPlaybackState(Player.STATE_BUFFERING)
                ShadowLooper.idleMainLooper()

                assertEquals(PlaybackState.Buffering, testEngine.playbackState.value)
            } finally {
                testEngine.release()
            }
        }

    @Test
    fun bufferingState_whenPaused_emitsPaused_andBufferReadyRespectsPausedState() =
        runTest {
            val basePlayer = ExoPlayer.Builder(context).build()
            val controllablePlayer = TestControllablePlayer(basePlayer)
            val testEngine = Media3AudioPlayerEngine(context, customPlayer = controllablePlayer)
            try {
                // Initial buffering with playWhenReady = true
                controllablePlayer.customPlaybackState = Player.STATE_BUFFERING
                controllablePlayer.customPlayWhenReady = true
                controllablePlayer.notifyPlaybackState(Player.STATE_BUFFERING)
                ShadowLooper.idleMainLooper()

                assertEquals(PlaybackState.Buffering, testEngine.playbackState.value)

                // User or BecomingNoisy pauses during buffering
                controllablePlayer.notifyPlayWhenReady(false)
                ShadowLooper.idleMainLooper()

                assertEquals(PlaybackState.Paused, testEngine.playbackState.value)

                // Stream finished buffering -> transitions to STATE_READY
                controllablePlayer.notifyPlaybackState(Player.STATE_READY)
                ShadowLooper.idleMainLooper()

                // Must stay Paused and not auto-play
                assertEquals(PlaybackState.Paused, testEngine.playbackState.value)
            } finally {
                testEngine.release()
            }
        }
}
