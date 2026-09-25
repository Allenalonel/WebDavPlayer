package com.webdav.player.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackSessionData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStorePlaybackSessionStoreTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private var testScope: TestScope = TestScope(testDispatcher + Job())
    private lateinit var context: Context
    private lateinit var testFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStorePlaybackSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope(testDispatcher + Job())
        testFile = File(context.filesDir, "datastore/test_${java.util.UUID.randomUUID()}.preferences_pb")

        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        repository = DataStorePlaybackSessionStore(dataStore, { testFile })
    }

    @After
    fun tearDown() {
        testScope.coroutineContext[Job]?.cancel()
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun getSavedSession_whenEmpty_returnsNull() = runTest(testDispatcher) {
        val session = repository.getSavedSession()
        assertNull(session)
    }

    @Test
    fun saveSession_and_getSavedSession_roundTripsCorrectly() = runTest(testDispatcher) {
        val track1 = AudioTrack(
            id = "1:/Music/track1.mp3",
            serverId = 1L,
            remotePath = "/Music/track1.mp3",
            title = "Track One",
            artist = "Artist A",
            album = "Album X",
            durationMs = 180000L,
            size = 5000000L,
            format = AudioFormat.MP3,
            coverThumbnailPath = "/cache/covers/1.png"
        )
        val track2 = AudioTrack(
            id = "1:/Music/track2.flac",
            serverId = 1L,
            remotePath = "/Music/track2.flac",
            title = "Track Two",
            artist = "Artist B",
            album = "Album Y",
            durationMs = 240000L,
            size = 25000000L,
            format = AudioFormat.FLAC,
            coverThumbnailPath = null
        )

        val original = PlaybackSessionData(
            activeServerId = 1L,
            currentDirectoryPath = "/Music/Rock/",
            queueTracks = listOf(track1, track2),
            currentTrackIndex = 1,
            positionMs = 65432L,
            playbackMode = PlaybackMode.SHUFFLE
        )

        repository.saveSession(original)

        val restored = repository.getSavedSession()
        assertNotNull(restored)
        assertEquals(1L, restored?.activeServerId)
        assertEquals("/Music/Rock/", restored?.currentDirectoryPath)
        assertEquals(2, restored?.queueTracks?.size)
        assertEquals("Track One", restored?.queueTracks?.get(0)?.title)
        assertEquals("Artist A", restored?.queueTracks?.get(0)?.artist)
        assertEquals(AudioFormat.MP3, restored?.queueTracks?.get(0)?.format)
        assertEquals("/cache/covers/1.png", restored?.queueTracks?.get(0)?.coverThumbnailPath)

        assertEquals("Track Two", restored?.queueTracks?.get(1)?.title)
        assertEquals(AudioFormat.FLAC, restored?.queueTracks?.get(1)?.format)
        assertNull(restored?.queueTracks?.get(1)?.coverThumbnailPath)

        assertEquals(1, restored?.currentTrackIndex)
        assertEquals(65432L, restored?.positionMs)
        assertEquals(PlaybackMode.SHUFFLE, restored?.playbackMode)
    }

    @Test
    fun clearSession_removesAllPersistedData() = runTest(testDispatcher) {
        val original = PlaybackSessionData(
            activeServerId = 2L,
            currentDirectoryPath = "/Jazz/",
            queueTracks = emptyList(),
            currentTrackIndex = -1,
            positionMs = 1000L,
            playbackMode = PlaybackMode.LIST_LOOP
        )

        repository.saveSession(original)
        assertNotNull(repository.getSavedSession())

        repository.clearSession()
        assertNull(repository.getSavedSession())
    }

    @Test
    fun getSavedSession_withCorruptedJson_returnsEmptyTracksGracefully() = runTest(testDispatcher) {
        // First save with corrupted tracks JSON
        repository.saveRawForTesting(
            serverId = 3L,
            dirPath = "/Corrupted/",
            tracksJson = "NOT_A_VALID_JSON_ARRAY{{{",
            trackIndex = 0,
            posMs = 5000L,
            mode = "SINGLE_LOOP"
        )

        val restored = repository.getSavedSession()
        assertNotNull(restored)
        assertEquals(3L, restored?.activeServerId)
        assertEquals("/Corrupted/", restored?.currentDirectoryPath)
        assertTrue(restored?.queueTracks?.isEmpty() == true)
        assertEquals(0, restored?.currentTrackIndex)
        assertEquals(5000L, restored?.positionMs)
        assertEquals(PlaybackMode.SINGLE_LOOP, restored?.playbackMode)
    }

    @Test
    fun saveSession_calledMultipleTimes_updatesStateConsistently() = runTest(testDispatcher) {
        val initial = PlaybackSessionData(
            activeServerId = 5L,
            currentDirectoryPath = "/Music/",
            queueTracks = emptyList(),
            currentTrackIndex = 0,
            positionMs = 1000L,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        repository.saveSession(initial)

        val updated = initial.copy(
            positionMs = 25000L,
            playbackMode = PlaybackMode.SINGLE_LOOP
        )
        repository.saveSession(updated)

        val restored = repository.getSavedSession()
        assertNotNull(restored)
        assertEquals(5L, restored?.activeServerId)
        assertEquals(25000L, restored?.positionMs)
        assertEquals(PlaybackMode.SINGLE_LOOP, restored?.playbackMode)
    }
}
