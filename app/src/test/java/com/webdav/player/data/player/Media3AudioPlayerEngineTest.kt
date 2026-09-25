package com.webdav.player.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
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
}
