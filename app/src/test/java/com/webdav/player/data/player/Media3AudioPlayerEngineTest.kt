package com.webdav.player.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.service.WebDavMediaSessionCallback
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import com.webdav.player.domain.session.MusicPlayerAppSession
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

    private class RecordingAppSession : MusicPlayerAppSession by FakeMusicPlayerAppSession() {
        var skipToNextCount = 0
        var skipToPreviousCount = 0

        override fun skipToNext() {
            skipToNextCount++
        }

        override fun skipToPrevious() {
            skipToPreviousCount++
        }
    }

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

        val updated = track1.copy(title = "Updated Song Title", artist = "Updated Artist")
        engine.updateTrackMetadata(updated)

        // Verify mediaSession is active and intact
        assertNotNull(engine.mediaSession)

        // Active playing MediaItem is NOT replaced to avoid decoder destruction / rebuffering glitches
        val currentItem = engine.player.getMediaItemAt(0)
        assertEquals("01.mp3", currentItem.mediaMetadata.title?.toString())
        assertEquals(originalItem.mediaMetadata.title, currentItem.mediaMetadata.title)

        // Position is preserved and not reset
        assertEquals(5000L, engine.currentPositionMs.value)
        assertTrue(engine.playbackState.value is PlaybackState.Playing || engine.playbackState.value is PlaybackState.Buffering)

        // Playlist and mediaSession mediaMetadata must be updated non-destructively for external presentation flows
        assertEquals(
            "Updated Song Title",
            engine.mediaSession.player.mediaMetadata.title
                ?.toString(),
        )
        assertEquals(
            "Updated Artist",
            engine.mediaSession.player.mediaMetadata.artist
                ?.toString(),
        )

        // Notification constructed from mediaSession must reflect updated metadata
        val notificationProvider =
            com.webdav.player.data.service
                .WebDavNotificationProvider(context)
        val notification = notificationProvider.buildNotification(engine.mediaSession, engine.playbackState.value)
        assertEquals("Updated Song Title", notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString())
        assertEquals("Updated Artist", notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString())
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
    fun reactiveSession_receivesEnrichedMetadata_whileActiveTimelineRemainsIntact() {
        val appSession =
            com.webdav.player.domain.session.MusicPlayerAppSessionImpl(
                playerEngine = engine,
                coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
            )
        appSession.setActiveServer(testServer)
        val file1 =
            com.webdav.player.domain.model
                .RemoteFile(name = "01.mp3", path = "/Music/01.mp3")
        val file2 =
            com.webdav.player.domain.model
                .RemoteFile(name = "02.flac", path = "/Music/02.flac")
        val dir =
            com.webdav.player.domain.model
                .RemoteDirectory(path = "/Music/", name = "Music", files = listOf(file1, file2))
        appSession.playDirectoryTrack(dir, file1)

        assertEquals(
            "01.mp3",
            appSession.sessionState.value.currentTrack
                ?.title,
        )

        // Enrich playing track
        val enrichedTrack1 = track1.copy(title = "Enriched Title 1", artist = "Artist 1")
        engine.updateTrackMetadata(enrichedTrack1)

        // Active item timeline is continuous and not replaced
        val activeItem = engine.player.getMediaItemAt(0)
        assertEquals("01.mp3", activeItem.mediaMetadata.title?.toString())
        assertEquals(
            "Enriched Title 1",
            engine.player.playlistMetadata.title
                ?.toString(),
        )

        assertEquals(1, engine.player.mediaItemCount)

        appSession.release()
    }

    @Test
    fun audioFocusTransientDuck_lowersVolume_andRestoresOnGain() {
        engine.loadTrack(server = testServer, track = track1)
        engine.setVolume(1.0f)

        engine.audioFocusHandler.handleFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue(engine.audioFocusHandler.isDucked)
        assertEquals(0.2f, engine.getVolume(), 0.01f)

        engine.audioFocusHandler.handleFocusChange(android.media.AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(engine.audioFocusHandler.isDucked)
        assertEquals(1.0f, engine.getVolume(), 0.01f)
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
            engine.sessionPlayer.mediaMetadata.title
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
    fun sessionCallback_andSessionPlayerDelegatesSkip() {
        val appSession = RecordingAppSession()
        engine.sessionCallback = WebDavMediaSessionCallback(appSession)
        engine.onSkipToNext = { appSession.skipToNext() }
        engine.onSkipToPrevious = { appSession.skipToPrevious() }

        engine.loadTrack(server = testServer, track = track1)
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()

        engine.sessionPlayer.seekToNext()
        assertEquals(1, appSession.skipToNextCount)

        engine.sessionPlayer.seekToPrevious()
        assertEquals(1, appSession.skipToPreviousCount)
    }

    @Test
    fun virtualTrackSingleItem_enablesNextPreviousCommands_andDelegatesToAppSession() {
        engine.loadTrack(
            server = testServer,
            track = track1,
        )

        // With 1 item, standard ExoPlayer disables SEEK_TO_NEXT_MEDIA_ITEM / SEEK_TO_PREVIOUS_MEDIA_ITEM
        // sessionPlayer must expose them as available so notification / MediaSession controls remain enabled
        assertTrue(engine.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(engine.sessionPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(engine.sessionPlayer.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(engine.sessionPlayer.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

        val appSession = RecordingAppSession()
        engine.sessionCallback = WebDavMediaSessionCallback(appSession)
        engine.onSkipToNext = { appSession.skipToNext() }
        engine.onSkipToPrevious = { appSession.skipToPrevious() }

        // Invoking seekToNextMediaItem / seekToPreviousMediaItem on sessionPlayer (as MediaSession does)
        engine.sessionPlayer.seekToNextMediaItem()
        assertEquals(1, appSession.skipToNextCount)

        engine.sessionPlayer.seekToPreviousMediaItem()
        assertEquals(1, appSession.skipToPreviousCount)
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

        // MediaSession player mediaMetadata is updated
        assertEquals(
            "Enriched Stream Title",
            engine.mediaSession.player.mediaMetadata.title
                ?.toString(),
        )
        assertEquals(
            "Enriched Artist",
            engine.mediaSession.player.mediaMetadata.artist
                ?.toString(),
        )

        // Notification constructed from mediaSession reflects updated metadata
        val notificationProvider =
            com.webdav.player.data.service
                .WebDavNotificationProvider(context)
        val notification = notificationProvider.buildNotification(engine.mediaSession, engine.playbackState.value)
        assertEquals(
            "Enriched Stream Title",
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
        )
        assertEquals(
            "Enriched Artist",
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
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

        fun notifyTimelineChanged(reason: Int = Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
            for (listener in customListeners) {
                listener.onTimelineChanged(currentTimeline, reason)
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
}
