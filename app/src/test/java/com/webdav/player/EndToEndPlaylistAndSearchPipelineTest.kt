package com.webdav.player

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.cue.CueTextCache
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.data.repository.LibrarySearchRepositoryImpl
import com.webdav.player.data.repository.PlaylistRepositoryImpl
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.QueueOrigin
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.session.FakePlaybackSessionStore
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.internal.http2.StreamResetException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EndToEndPlaylistAndSearchPipelineTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var playlistRepository: PlaylistRepositoryImpl
    private lateinit var searchRepository: LibrarySearchRepositoryImpl
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var fakeStore: FakePlaybackSessionStore
    private lateinit var fakeServerRepo: TestServerRepository
    private lateinit var sessionJob: kotlinx.coroutines.CompletableJob
    private lateinit var sessionScope: kotlinx.coroutines.CoroutineScope
    private lateinit var session: MusicPlayerAppSessionImpl

    private val server1 =
        WebDavServer(
            id = 1L,
            name = "Home Synology NAS",
            url = "https://nas.home:5006/music",
            port = 5006,
            pathPrefix = "/music",
            username = "home_user",
            password = "password1",
            allowSelfSigned = true,
            isDefault = true,
        )

    private val server2 =
        WebDavServer(
            id = 2L,
            name = "Office Nextcloud",
            url = "https://cloud.office:443/remote.php/webdav/music",
            port = 443,
            pathPrefix = "/remote.php/webdav/music",
            username = "office_user",
            password = "password2",
            allowSelfSigned = false,
            isDefault = false,
        )

    private val server1File =
        RemoteFile(
            name = "Beethoven-Symphony9.flac",
            path = "/Classical/Beethoven-Symphony9.flac",
            size = 450_000_000L,
            fileType = RemoteFileType.Audio(AudioFormat.FLAC),
        )

    private val server1CueFile =
        RemoteFile(
            name = "Beethoven-Symphony9.cue",
            path = "/Classical/Beethoven-Symphony9.cue",
            size = 2_000L,
            fileType = RemoteFileType.Cue,
        )

    private val server2File =
        RemoteFile(
            name = "Chopin-Nocturne.mp3",
            path = "/Piano/Chopin-Nocturne.mp3",
            size = 12_000_000L,
            fileType = RemoteFileType.Audio(AudioFormat.MP3),
        )

    private val cueContent =
        """
        PERFORMER "Herbert von Karajan"
        TITLE "Symphony No. 9"
        FILE "Beethoven-Symphony9.flac" WAVE
          TRACK 01 AUDIO
            TITLE "I. Allegro ma non troppo"
            PERFORMER "Karajan & BPO"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "II. Molto vivace"
            PERFORMER "Karajan & BPO"
            INDEX 01 15:30:00
        """.trimIndent()

    private class TestServerRepository(
        initialServers: List<WebDavServer>,
    ) : ServerRepository {
        private val serversFlow = MutableStateFlow(initialServers)
        private val activeServerFlow = MutableStateFlow(initialServers.firstOrNull { it.isDefault } ?: initialServers.firstOrNull())

        override fun getAllServers(): Flow<List<WebDavServer>> = serversFlow

        override fun getActiveServer(): Flow<WebDavServer?> = activeServerFlow

        override suspend fun getServerById(id: Long): WebDavServer? = serversFlow.value.find { it.id == id }

        override suspend fun saveServer(server: WebDavServer): Long = server.id

        override suspend fun deleteServer(id: Long) {
            serversFlow.value = serversFlow.value.filter { it.id != id }
        }

        override suspend fun setActiveServer(id: Long) {
            activeServerFlow.value = serversFlow.value.find { it.id == id }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        playlistRepository = PlaylistRepositoryImpl(database.playlistDao())
        searchRepository =
            LibrarySearchRepositoryImpl(
                directoryCacheDao = database.directoryCacheDao(),
                trackMetadataDao = database.trackMetadataDao(),
            )

        fakeEngine = FakeAudioPlayerEngine()
        fakeStore = FakePlaybackSessionStore()
        fakeServerRepo = TestServerRepository(listOf(server1, server2))

        sessionJob = SupervisorJob()
        sessionScope = kotlinx.coroutines.CoroutineScope(testDispatcher + sessionJob)

        session =
            MusicPlayerAppSessionImpl(
                playerEngine = fakeEngine,
                serverRepository = fakeServerRepo,
                sessionStore = fakeStore,
                playlistRepository = playlistRepository,
                coroutineScope = sessionScope,
            )

        // Populate Room directory cache
        val dir1 =
            RemoteDirectory(
                path = "/Classical/",
                name = "Classical",
                subDirectories = emptyList(),
                files = listOf(server1File, server1CueFile),
            )
        kotlinx.coroutines.runBlocking {
            database.directoryCacheDao().insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = server1.id,
                    path = dir1.path,
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir1),
                    lastUpdatedMs = System.currentTimeMillis(),
                ),
            )

            val dir2 =
                RemoteDirectory(
                    path = "/Piano/",
                    name = "Piano",
                    subDirectories = emptyList(),
                    files = listOf(server2File),
                )
            database.directoryCacheDao().insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = server2.id,
                    path = dir2.path,
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir2),
                    lastUpdatedMs = System.currentTimeMillis(),
                ),
            )

            // Populate track metadata
            database.trackMetadataDao().insertOrUpdate(
                TrackMetadataEntity(
                    serverId = server1.id,
                    remotePath = server1File.path,
                    title = "Beethoven: Symphony No. 9 in D minor",
                    artist = "Karajan",
                    album = "Beethoven Complete",
                    trackNumber = 1,
                    durationMs = 3900000L,
                    coverThumbnailPath = null,
                ),
            )
            database.trackMetadataDao().insertOrUpdate(
                TrackMetadataEntity(
                    serverId = server2.id,
                    remotePath = server2File.path,
                    title = "Nocturne in E-flat major, Op. 9 No. 2",
                    artist = "Chopin",
                    album = "Romantic Piano Works",
                    trackNumber = 2,
                    durationMs = 280000L,
                    coverThumbnailPath = null,
                ),
            )
        }

        // Cache CUE content
        CueTextCache.put(server1.id, server1CueFile.path, cueContent)
    }

    @After
    fun tearDown() {
        CueTextCache.clear()
        session.release()
        sessionJob.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun e2e_createPlaylist_searchAcrossServers_andAddToPlaylist() =
        runTest(testDispatcher) {
            // 1. Create a cross-server playlist
            val playlistId = playlistRepository.createPlaylist("古典与钢琴精选")
            assertTrue("Playlist id should be > 0", playlistId > 0L)

            // 2. Search library for "Symphony" across servers
            val searchResult = searchRepository.search("Symphony", serverId = null).first { it.matchedTracks.isNotEmpty() }
            assertTrue("Search matched directories or tracks", searchResult.matchedTracks.isNotEmpty())

            // Should match track from Server 1 (and CUE virtual track)
            val matchedBeethoven = searchResult.matchedTracks.find { it.serverId == server1.id && !it.isVirtualTrack }
            assertNotNull("Matched parent physical track", matchedBeethoven)

            val matchedVirtualTrack = searchResult.matchedTracks.find { it.serverId == server1.id && it.isVirtualTrack }
            assertNotNull("Matched CUE virtual track", matchedVirtualTrack)

            // 3. Search for "Chopin" on Server 2
            val chopinResult = searchRepository.search("Chopin", serverId = null).first { it.matchedTracks.isNotEmpty() }
            val matchedChopin = chopinResult.matchedTracks.find { it.serverId == server2.id }
            assertNotNull("Matched Chopin track from Server 2", matchedChopin)

            // 4. Add tracks to playlist: track from Server 1, virtual track from Server 1, track from Server 2
            val trackEntry1Id = playlistRepository.addTrackToPlaylist(playlistId, matchedBeethoven!!)
            val trackEntry2Id = playlistRepository.addTrackToPlaylist(playlistId, matchedVirtualTrack!!, cuePath = server1CueFile.path)
            val trackEntry3Id = playlistRepository.addTrackToPlaylist(playlistId, matchedChopin!!)

            assertTrue(trackEntry1Id > 0L)
            assertTrue(trackEntry2Id > 0L)
            assertTrue(trackEntry3Id > 0L)

            // 5. Verify playlist contents
            val tracks = playlistRepository.getTracksForPlaylist(playlistId)
            assertEquals(3, tracks.size)
            assertEquals("Beethoven: Symphony No. 9 in D minor", tracks[0].title)
            assertEquals(server1.id, tracks[0].serverId)
            assertFalse(tracks[0].isVirtualTrack)

            assertEquals("I. Allegro ma non troppo", tracks[1].title)
            assertEquals(server1.id, tracks[1].serverId)
            assertTrue(tracks[1].isVirtualTrack)
            assertTrue("Virtual track ID should contain cue_1", tracks[1].virtualTrackId?.endsWith("1") == true)

            assertEquals("Nocturne in E-flat major, Op. 9 No. 2", tracks[2].title)
            assertEquals(server2.id, tracks[2].serverId)
        }

    @Test
    fun e2e_dragReorderTracks_andVerifyPersistence() =
        runTest(testDispatcher) {
            val playlistId = playlistRepository.createPlaylist("拖拽重排测试")
            val track1 = AudioTrack.fromRemoteFile(server1, server1File)!!
            val track2 = AudioTrack.fromRemoteFile(server2, server2File)!!

            val id1 = playlistRepository.addTrackToPlaylist(playlistId, track1)
            val id2 = playlistRepository.addTrackToPlaylist(playlistId, track2)

            var list = playlistRepository.getTracksForPlaylist(playlistId)
            assertEquals(listOf(id1, id2), list.map { it.id })

            // Reorder: reverse the order
            playlistRepository.updateTrackOrders(playlistId, listOf(id2, id1))
            list = playlistRepository.getTracksForPlaylist(playlistId)

            assertEquals(listOf(id2, id1), list.map { it.id })
            assertEquals(0, list[0].sortOrder)
            assertEquals(1, list[1].sortOrder)
        }

    @Test
    fun e2e_crossServerPlayback_seamlessProgressionAndDynamicContext() =
        runTest(testDispatcher) {
            val playlistId = playlistRepository.createPlaylist("跨服连播测试")
            val track1 = AudioTrack.fromRemoteFile(server1, server1File)!!
            val track2 = AudioTrack.fromRemoteFile(server2, server2File)!!

            playlistRepository.addTrackToPlaylist(playlistId, track1)
            playlistRepository.addTrackToPlaylist(playlistId, track2)

            val playlist = playlistRepository.getPlaylist(playlistId)!!

            // Start playing the playlist at track index 0 (Server 1)
            session.playPlaylist(playlist, startIndex = 0)
            val state = session.sessionState.first { it.queue.isPlaylistQueue }

            // Verify queue origin is PLAYLIST
            val queue = state.queue
            assertEquals(QueueOrigin.PLAYLIST, queue.origin)
            assertTrue(queue.isPlaylistQueue)
            assertEquals(2, queue.size)
            assertEquals(0, queue.currentIndex)

            // Verify Engine received Server 1 context
            assertEquals(server1.id, fakeEngine.lastServer?.id)
            assertEquals("Beethoven-Symphony9.flac", fakeEngine.loadedTrack?.title)

            // Skip to next track (Server 2)
            session.skipToNext()
            val stateAfterSkip = session.sessionState.first { it.queue.currentIndex == 1 && it.activeServer?.id == server2.id }

            // Verify Engine dynamically received Server 2 context!
            assertEquals(server2.id, fakeEngine.lastServer?.id)
            assertEquals("Chopin-Nocturne.mp3", fakeEngine.loadedTrack?.title)
            assertEquals(1, stateAfterSkip.queue.currentIndex)
        }

    @Test
    fun e2e_inactiveTrack_faultToleranceAndSelfHealing() =
        runTest(testDispatcher) {
            val playlistId = playlistRepository.createPlaylist("容错自愈测试")
            val track1 = AudioTrack.fromRemoteFile(server1, server1File)!!
            val track2 = AudioTrack.fromRemoteFile(server2, server2File)!!

            val id1 = playlistRepository.addTrackToPlaylist(playlistId, track1)
            val id2 = playlistRepository.addTrackToPlaylist(playlistId, track2)

            val playlist = playlistRepository.getPlaylist(playlistId)!!

            session.playPlaylist(playlist, startIndex = 0)
            session.sessionState.first { it.queue.isPlaylistQueue }

            assertEquals("Beethoven-Symphony9.flac", fakeEngine.loadedTrack?.title)

            // Simulate stream 404 failure on track 1
            fakeEngine._playbackState.value =
                PlaybackState.Error("HTTP 404 error", FileNotFoundException("HTTP 404: Remote file not found"))
            
            // Session catches 404, marks track inactive, and automatically self-heals by advancing to track 2!
            val stateAfterHealing = session.sessionState.first { it.queue.currentIndex == 1 && it.activeServer?.id == server2.id }
            assertEquals(1, stateAfterHealing.queue.currentIndex)
            assertEquals("Chopin-Nocturne.mp3", stateAfterHealing.currentTrack?.title)
            assertEquals(server2.id, fakeEngine.lastServer?.id)

            // Clean inactive tracks from playlist
            playlistRepository.removeTrackFromPlaylist(id1)

            val remainingTracks = playlistRepository.getTracksForPlaylist(playlistId)
            assertEquals(1, remainingTracks.size)
            assertEquals("Chopin-Nocturne.mp3", remainingTracks[0].title)
            assertEquals(id2, remainingTracks[0].id)
        }


    @Test
    fun e2e_sessionPersistenceAndColdStartRestoration_withPlaylistQueue() =
        runTest(testDispatcher) {
            val playlistId = playlistRepository.createPlaylist("断点恢复测试")
            val track1 = AudioTrack.fromRemoteFile(server1, server1File)!!
            val track2 = AudioTrack.fromRemoteFile(server2, server2File)!!

            playlistRepository.addTrackToPlaylist(playlistId, track1)
            playlistRepository.addTrackToPlaylist(playlistId, track2)

            val playlist = playlistRepository.getPlaylist(playlistId)!!

            session.playPlaylist(playlist, startIndex = 1)
            session.sessionState.first { it.queue.isPlaylistQueue && it.queue.currentIndex == 1 }

            // Flush session to store
            session.flushSession()
            advanceUntilIdle()

            val savedData = fakeStore.getSavedSession()
            assertNotNull("Saved session must not be null", savedData)
            assertEquals(server2.id, savedData?.activeServerId)
            assertEquals(1, savedData?.currentTrackIndex)
            assertEquals(2, savedData?.queueTracks?.size)

            // Simulate cold start: instantiate a fresh session and restore
            val newEngine = FakeAudioPlayerEngine()
            val newJob = SupervisorJob()
            val newScope = kotlinx.coroutines.CoroutineScope(testDispatcher + newJob)

            val restoredSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = newEngine,
                    serverRepository = fakeServerRepo,
                    sessionStore = fakeStore,
                    playlistRepository = playlistRepository,
                    coroutineScope = newScope,
                )

            restoredSession.restoreSession()
            advanceUntilIdle()

            val restoredState = restoredSession.sessionState.value
            assertTrue("Restored session has track", restoredState.hasTrack)
            assertEquals(server2.id, restoredState.activeServer?.id)
            assertEquals(1, restoredState.queue.currentIndex)
            assertEquals("Chopin-Nocturne.mp3", restoredState.currentTrack?.title)

            restoredSession.release()
            newJob.cancel()
        }
}
