package com.webdav.player.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class Media3AudioPlayerEngineTest {

    private lateinit var context: Context
    private lateinit var engine: Media3AudioPlayerEngine

    private val testServer = WebDavServer(
        id = 1L,
        name = "Local Test",
        url = "http://127.0.0.1:8080/dav",
        port = 8080,
        pathPrefix = "/dav"
    )

    private val track1 = AudioTrack(
        id = "1:/Music/01.mp3",
        serverId = 1L,
        remotePath = "/Music/01.mp3",
        title = "01.mp3",
        format = AudioFormat.MP3,
        size = 1000L
    )

    private val track2 = AudioTrack(
        id = "1:/Music/02.flac",
        serverId = 1L,
        remotePath = "/Music/02.flac",
        title = "02.flac",
        format = AudioFormat.FLAC,
        size = 2000L
    )

    private val track3 = AudioTrack(
        id = "1:/Music/03.wav",
        serverId = 1L,
        remotePath = "/Music/03.wav",
        title = "03.wav",
        format = AudioFormat.WAV,
        size = 3000L
    )

    private val trackWma = AudioTrack(
        id = "1:/Music/04.wma",
        serverId = 1L,
        remotePath = "/Music/04.wma",
        title = "04.wma",
        format = AudioFormat.WMA,
        size = 4000L
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val factory = WebDavDataSourceFactory()
        engine = Media3AudioPlayerEngine(context, factory)
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
        assertEquals(-1, engine.currentTrackIndex.value)
        assertEquals(PlaybackMode.LIST_LOOP, engine.playbackMode.value)
    }

    @Test
    fun playTracks_setsStartIndex_andPreparesPlayer() {
        engine.playTracks(
            server = testServer,
            tracks = listOf(track1, track2),
            startIndex = 1,
            startPositionMs = 0L
        )

        assertEquals(1, engine.currentTrackIndex.value)
        assertEquals(testServer, engine.dataSourceFactory.getCurrentServer())
    }

    @Test
    fun playTracks_withMediaSourceAdapter_preparesExoPlayerMediaSources() {
        val adapter = DefaultWebDavMediaSourceAdapter(context)
        val customEngine = Media3AudioPlayerEngine(context, mediaSourceAdapter = adapter)
        try {
            customEngine.playTracks(
                server = testServer,
                tracks = listOf(track1, trackWma),
                startIndex = 0,
                startPositionMs = 0L
            )
            assertEquals(0, customEngine.currentTrackIndex.value)
            assertEquals(2, customEngine.player.mediaItemCount)
            assertEquals("audio/mpeg", customEngine.player.getMediaItemAt(0).localConfiguration?.mimeType)
            assertEquals("audio/x-ms-wma", customEngine.player.getMediaItemAt(1).localConfiguration?.mimeType)
        } finally {
            customEngine.release()
        }
    }

    @Test
    fun insertTrack_withMediaSourceAdapter_addsMediaSource() {
        val adapter = DefaultWebDavMediaSourceAdapter(context)
        val customEngine = Media3AudioPlayerEngine(context, mediaSourceAdapter = adapter)
        try {
            customEngine.playTracks(
                server = testServer,
                tracks = listOf(track1),
                startIndex = 0
            )
            assertEquals(1, customEngine.player.mediaItemCount)

            customEngine.insertTrack(1, testServer, trackWma)
            assertEquals(2, customEngine.player.mediaItemCount)
            assertEquals("audio/x-ms-wma", customEngine.player.getMediaItemAt(1).localConfiguration?.mimeType)
        } finally {
            customEngine.release()
        }
    }

    @Test
    fun playTracks_withWmaTrack_setsWmaMimeTypeAndPrepares() {
        engine.playTracks(
            server = testServer,
            tracks = listOf(trackWma),
            startIndex = 0
        )

        assertEquals(0, engine.currentTrackIndex.value)
        assertEquals(1, engine.player.mediaItemCount)
        val mediaItem = engine.player.getMediaItemAt(0)
        assertEquals("audio/x-ms-wma", mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun seekTo_andStop_updatesStateCorrectly() {
        engine.playTracks(
            server = testServer,
            tracks = listOf(track1, track2),
            startIndex = 0
        )

        engine.seekTo(3000L)
        assertEquals(3000L, engine.currentPositionMs.value)

        engine.stop()
        assertEquals(PlaybackState.Idle, engine.playbackState.value)
        assertEquals(0L, engine.currentPositionMs.value)
    }

    @Test
    fun setPlaybackMode_updatesState() {
        engine.setPlaybackMode(PlaybackMode.SINGLE_LOOP)
        assertEquals(PlaybackMode.SINGLE_LOOP, engine.playbackMode.value)

        engine.setPlaybackMode(PlaybackMode.SHUFFLE)
        assertEquals(PlaybackMode.SHUFFLE, engine.playbackMode.value)

        engine.setPlaybackMode(PlaybackMode.LIST_LOOP)
        assertEquals(PlaybackMode.LIST_LOOP, engine.playbackMode.value)
    }

    @Test
    fun removeTrack_updatesPlayerAndIndex() {
        engine.playTracks(
            server = testServer,
            tracks = listOf(track1, track2, track3),
            startIndex = 1
        )
        assertEquals(1, engine.currentTrackIndex.value)

        // Remove track 0 (track before current)
        engine.removeTrack(0)
        assertEquals(0, engine.currentTrackIndex.value)

        // Remove remaining track 1
        engine.removeTrack(1)
        assertEquals(0, engine.currentTrackIndex.value)

        // Remove last remaining track
        engine.removeTrack(0)
        assertEquals(PlaybackState.Idle, engine.playbackState.value)
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
    fun updateTrack_updatesMediaItemMetadata() {
        engine.playTracks(
            server = testServer,
            tracks = listOf(track1),
            startIndex = 0
        )

        val updated = track1.copy(title = "Updated Song Title", artist = "Updated Artist")
        engine.updateTrack(0, updated)

        // Verify mediaSession is active and intact
        assertNotNull(engine.mediaSession)
        val item = engine.player.getMediaItemAt(0)
        assertEquals("Updated Song Title", item.mediaMetadata.title?.toString())
        assertEquals("Updated Artist", item.mediaMetadata.artist?.toString())
    }

    @Test
    fun updateTrack_withIdenticalMetadata_doesNotTriggerUnnecessaryUpdates() {
        engine.playTracks(
            server = testServer,
            tracks = listOf(track1),
            startIndex = 0
        )

        // Calling updateTrack with the exact same track should be a no-op
        val initialItem = engine.player.getMediaItemAt(0)
        engine.updateTrack(0, track1)
        val afterItem = engine.player.getMediaItemAt(0)

        // Verifies no exception and player state remains stable
        assertEquals(initialItem.mediaMetadata.title, afterItem.mediaMetadata.title)
    }

    @Test
    fun audioFocusTransientDuck_lowersVolume_andRestoresOnGain() {
        engine.playTracks(server = testServer, tracks = listOf(track1), startIndex = 0)
        engine.setVolume(1.0f)

        engine.audioFocusHandler.handleFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertTrue(engine.audioFocusHandler.isDucked)
        assertEquals(0.2f, engine.getVolume(), 0.01f)

        engine.audioFocusHandler.handleFocusChange(android.media.AudioManager.AUDIOFOCUS_GAIN)
        assertFalse(engine.audioFocusHandler.isDucked)
        assertEquals(1.0f, engine.getVolume(), 0.01f)
    }
}
