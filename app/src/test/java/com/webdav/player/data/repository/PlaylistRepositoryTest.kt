package com.webdav.player.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.PlaylistDao
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PlaylistDao
    private lateinit var repository: PlaylistRepositoryImpl
    private var simulatedTime: Long = 1_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.playlistDao()
        repository =
            PlaylistRepositoryImpl(
                playlistDao = dao,
                timeProvider = { simulatedTime },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createPlaylist_success_andRetrieveFlow() =
        runBlocking {
            val id = repository.createPlaylist("我的车载精选")
            assertTrue(id > 0)

            val playlist = repository.getPlaylist(id)
            assertNotNull(playlist)
            assertEquals("我的车载精选", playlist?.name)
            assertEquals(0, playlist?.trackCount)
            assertEquals(1_000_000L, playlist?.createdAtMs)
            assertEquals(1_000_000L, playlist?.updatedAtMs)

            val all = repository.getAllPlaylistsFlow().first()
            assertEquals(1, all.size)
            assertEquals("我的车载精选", all[0].name)
        }

    @Test
    fun createPlaylist_emptyName_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.createPlaylist("   ")
            }
        }
    }

    @Test
    fun createPlaylist_duplicateName_throwsException() {
        runBlocking {
            repository.createPlaylist("流行榜单")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.createPlaylist("流行榜单")
                }
            }
        }
    }

    @Test
    fun isPlaylistNameExists_checksDuplicatesAccurately() =
        runBlocking {
            val id = repository.createPlaylist("摇滚专区")
            assertTrue(repository.isPlaylistNameExists("摇滚专区"))
            assertTrue(repository.isPlaylistNameExists("  摇滚专区  "))
            assertFalse(repository.isPlaylistNameExists("古典"))

            // Exclude current playlist id during rename check
            assertFalse(repository.isPlaylistNameExists("摇滚专区", excludePlaylistId = id))
        }

    @Test
    fun renamePlaylist_success_andUpdatesTimestamp() =
        runBlocking {
            val id = repository.createPlaylist("原名称")
            simulatedTime = 2_000_000L

            repository.renamePlaylist(id, "新名称")
            val updated = repository.getPlaylist(id)
            assertNotNull(updated)
            assertEquals("新名称", updated?.name)
            assertEquals(2_000_000L, updated?.updatedAtMs)
            assertEquals(1_000_000L, updated?.createdAtMs)
        }

    @Test
    fun renamePlaylist_duplicateName_throwsException() {
        runBlocking {
            repository.createPlaylist("歌单A")
            val id2 = repository.createPlaylist("歌单B")

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.renamePlaylist(id2, "歌单A")
                }
            }
        }
    }

    @Test
    fun deletePlaylist_cascadesDeleteToTracks() =
        runBlocking {
            val playlistId = repository.createPlaylist("待删除歌单")
            val track1 =
                AudioTrack(
                    id = "1:/track1.flac",
                    serverId = 1L,
                    remotePath = "/track1.flac",
                    title = "Track 1",
                    format = AudioFormat.FLAC,
                )
            repository.addTrackToPlaylist(playlistId, track1)

            val tracksBefore = repository.getTracksForPlaylist(playlistId)
            assertEquals(1, tracksBefore.size)

            repository.deletePlaylist(playlistId)
            assertNull(repository.getPlaylist(playlistId))

            val tracksAfter = repository.getTracksForPlaylist(playlistId)
            assertTrue(tracksAfter.isEmpty())
        }

    @Test
    fun addTrackToPlaylist_duplicateTrackReturnsMinusOneAndDoesNotDuplicate() =
        runBlocking {
            val playlistId = repository.createPlaylist("防重测试歌单")
            val track =
                AudioTrack(
                    id = "1:/i_do.mp3",
                    serverId = 1L,
                    remotePath = "/i_do.mp3",
                    title = "I Do",
                    artist = "911",
                    format = AudioFormat.MP3,
                )

            val firstInsertId = repository.addTrackToPlaylist(playlistId, track)
            assertTrue(firstInsertId > 0L)

            val secondInsertId = repository.addTrackToPlaylist(playlistId, track)
            assertEquals(-1L, secondInsertId)

            val tracks = repository.getTracksForPlaylist(playlistId)
            assertEquals(1, tracks.size)
            assertEquals("I Do", tracks[0].title)
        }

    @Test
    fun addTracksToPlaylist_filtersDuplicates() =
        runBlocking {
            val playlistId = repository.createPlaylist("批量防重歌单")
            val track1 = AudioTrack(id = "1:/1.mp3", serverId = 1L, remotePath = "/1.mp3", title = "T1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/2.mp3", serverId = 1L, remotePath = "/2.mp3", title = "T2", format = AudioFormat.MP3)

            repository.addTrackToPlaylist(playlistId, track1)

            // Batch adding track1 and track2: track1 is duplicate, only track2 should be inserted
            val insertedIds = repository.addTracksToPlaylist(playlistId, listOf(track1, track2))
            assertEquals(1, insertedIds.size)

            val tracks = repository.getTracksForPlaylist(playlistId)
            assertEquals(2, tracks.size)
        }

    @Test
    fun getTracksForPlaylist_selfHealsExistingDuplicates() =
        runBlocking {
            val playlistId = repository.createPlaylist("自愈重复歌单")
            val track = AudioTrack(id = "1:/i_do.mp3", serverId = 1L, remotePath = "/i_do.mp3", title = "I Do", format = AudioFormat.MP3)

            // Manually insert duplicate rows directly into DAO to simulate existing legacy duplicates
            val entity1 =
                com.webdav.player.data.local.PlaylistTrackEntity(
                    playlistId = playlistId,
                    serverId = track.serverId,
                    remotePath = track.remotePath,
                    sortOrder = 0,
                    title = track.title,
                    format = "MP3",
                )
            val entity2 =
                com.webdav.player.data.local.PlaylistTrackEntity(
                    playlistId = playlistId,
                    serverId = track.serverId,
                    remotePath = track.remotePath,
                    sortOrder = 1,
                    title = track.title,
                    format = "MP3",
                )
            database.playlistDao().insertTrack(entity1)
            database.playlistDao().insertTrack(entity2)

            // Direct DAO count has 2
            assertEquals(2, database.playlistDao().getTracksForPlaylist(playlistId).size)

            // Repository query self-heals: filters duplicate in return list and launches background cleanup
            val tracks = repository.getTracksForPlaylist(playlistId)
            assertEquals(1, tracks.size)
            assertEquals("I Do", tracks[0].title)
        }

    @Test
    fun addTrackToPlaylist_crossServerTracksPersistedCorrectly() =
        runBlocking {
            val playlistId = repository.createPlaylist("跨服务器歌单")

            // Server 1 track
            val server1Track =
                AudioTrack(
                    id = "1:/music/nas_song.flac",
                    serverId = 1L,
                    remotePath = "/music/nas_song.flac",
                    title = "NAS 歌曲",
                    artist = "NAS 歌手",
                    album = "NAS 专辑",
                    durationMs = 240000L,
                    size = 35000000L,
                    format = AudioFormat.FLAC,
                    coverThumbnailPath = "/covers/1.jpg",
                )

            // Server 2 track
            val server2Track =
                AudioTrack(
                    id = "2:/cloud/work_song.mp3",
                    serverId = 2L,
                    remotePath = "/cloud/work_song.mp3",
                    title = "办公云盘歌曲",
                    artist = "云端歌手",
                    album = "云端专辑",
                    durationMs = 180000L,
                    size = 8000000L,
                    format = AudioFormat.MP3,
                )

            val id1 = repository.addTrackToPlaylist(playlistId, server1Track)
            val id2 = repository.addTrackToPlaylist(playlistId, server2Track)
            assertTrue(id1 > 0)
            assertTrue(id2 > 0)

            val playlist = repository.getPlaylist(playlistId)
            assertEquals(2, playlist?.trackCount)

            val tracks = repository.getTracksForPlaylist(playlistId)
            assertEquals(2, tracks.size)

            val entry1 = tracks[0]
            assertEquals(1L, entry1.serverId)
            assertEquals("/music/nas_song.flac", entry1.remotePath)
            assertEquals("NAS 歌曲", entry1.title)
            assertEquals("NAS 歌手", entry1.artist)
            assertEquals("NAS 专辑", entry1.album)
            assertEquals(240000L, entry1.durationMs)
            assertEquals(35000000L, entry1.size)
            assertEquals(AudioFormat.FLAC, entry1.format)
            assertEquals(0, entry1.sortOrder)
            assertFalse(entry1.isVirtualTrack)

            val entry2 = tracks[1]
            assertEquals(2L, entry2.serverId)
            assertEquals("/cloud/work_song.mp3", entry2.remotePath)
            assertEquals("办公云盘歌曲", entry2.title)
            assertEquals(AudioFormat.MP3, entry2.format)
            assertEquals(1, entry2.sortOrder)
        }

    @Test
    fun addTrackToPlaylist_virtualTrackPersistedCorrectly() =
        runBlocking {
            val playlistId = repository.createPlaylist("CUE 分轨精选")

            val virtualTrack =
                AudioTrack(
                    id = "1:/music/album.wav#cue_3",
                    serverId = 1L,
                    remotePath = "/music/album.wav",
                    title = "交响乐第三乐章",
                    artist = "乐团",
                    album = "交响曲",
                    durationMs = 310000L,
                    size = 450000000L,
                    format = AudioFormat.WAV,
                )

            repository.addTrackToPlaylist(playlistId, virtualTrack, cuePath = "/music/album.cue")

            val tracks = repository.getTracksForPlaylist(playlistId)
            assertEquals(1, tracks.size)

            val entry = tracks[0]
            assertTrue(entry.isVirtualTrack)
            assertEquals("1:/music/album.wav#cue_3", entry.virtualTrackId)
            assertEquals("/music/album.cue", entry.cuePath)

            val reconstructedAudioTrack = entry.toAudioTrack()
            assertEquals("1:/music/album.wav#cue_3", reconstructedAudioTrack.id)
            assertTrue(reconstructedAudioTrack.isVirtualTrack)
            assertEquals(1L, reconstructedAudioTrack.serverId)
        }

    @Test
    fun addTracksToPlaylist_batchAdditionMaintainsSortOrder() =
        runBlocking {
            val playlistId = repository.createPlaylist("批量添加")

            val tracks =
                (1..5).map { i ->
                    AudioTrack(
                        id = "1:/track_$i.mp3",
                        serverId = 1L,
                        remotePath = "/track_$i.mp3",
                        title = "Track $i",
                        format = AudioFormat.MP3,
                    )
                }

            val ids = repository.addTracksToPlaylist(playlistId, tracks)
            assertEquals(5, ids.size)

            val stored = repository.getTracksForPlaylist(playlistId)
            assertEquals(5, stored.size)
            for (i in 0 until 5) {
                assertEquals(i, stored[i].sortOrder)
                assertEquals("Track ${i + 1}", stored[i].title)
            }
        }

    @Test
    fun removeTrackFromPlaylist_removesEntryAndUpdateCount() =
        runBlocking {
            val playlistId = repository.createPlaylist("测试移除")
            val track1 = AudioTrack(id = "1:/1.mp3", serverId = 1L, remotePath = "/1.mp3", title = "T1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/2.mp3", serverId = 1L, remotePath = "/2.mp3", title = "T2", format = AudioFormat.MP3)

            val entryId1 = repository.addTrackToPlaylist(playlistId, track1)
            repository.addTrackToPlaylist(playlistId, track2)

            assertEquals(2, repository.getPlaylist(playlistId)?.trackCount)

            repository.removeTrackFromPlaylist(entryId1)

            val remaining = repository.getTracksForPlaylist(playlistId)
            assertEquals(1, remaining.size)
            assertEquals("T2", remaining[0].title)
            assertEquals(1, repository.getPlaylist(playlistId)?.trackCount)
        }

    @Test
    fun updateTrackOrder_reordersTracksAtomically() =
        runBlocking {
            val playlistId = repository.createPlaylist("拖拽排序测试")
            val trackA = AudioTrack(id = "1:/a.mp3", serverId = 1L, remotePath = "/a.mp3", title = "A", format = AudioFormat.MP3)
            val trackB = AudioTrack(id = "1:/b.mp3", serverId = 1L, remotePath = "/b.mp3", title = "B", format = AudioFormat.MP3)
            val trackC = AudioTrack(id = "1:/c.mp3", serverId = 1L, remotePath = "/c.mp3", title = "C", format = AudioFormat.MP3)

            repository.addTrackToPlaylist(playlistId, trackA)
            repository.addTrackToPlaylist(playlistId, trackB)
            repository.addTrackToPlaylist(playlistId, trackC)

            val initial = repository.getTracksForPlaylist(playlistId)
            // Reverse order: C, B, A
            val reversed = listOf(initial[2], initial[1], initial[0])

            simulatedTime = 3_000_000L
            repository.updateTrackOrders(playlistId, reversed.map { it.id })

            val updated = repository.getTracksForPlaylist(playlistId)
            assertEquals(3, updated.size)
            assertEquals("C", updated[0].title)
            assertEquals(0, updated[0].sortOrder)
            assertEquals("B", updated[1].title)
            assertEquals(1, updated[1].sortOrder)
            assertEquals("A", updated[2].title)
            assertEquals(2, updated[2].sortOrder)

            assertEquals(3_000_000L, repository.getPlaylist(playlistId)?.updatedAtMs)
        }

    @Test
    fun updateTrackOrders_reordersUsingIdsList() =
        runBlocking {
            val playlistId = repository.createPlaylist("ID列表排序测试")
            val track1 = AudioTrack(id = "1:/1.mp3", serverId = 1L, remotePath = "/1.mp3", title = "T1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/2.mp3", serverId = 1L, remotePath = "/2.mp3", title = "T2", format = AudioFormat.MP3)
            val track3 = AudioTrack(id = "1:/3.mp3", serverId = 1L, remotePath = "/3.mp3", title = "T3", format = AudioFormat.MP3)

            val id1 = repository.addTrackToPlaylist(playlistId, track1)
            val id2 = repository.addTrackToPlaylist(playlistId, track2)
            val id3 = repository.addTrackToPlaylist(playlistId, track3)

            simulatedTime = 4_000_000L
            repository.updateTrackOrders(playlistId, listOf(id3, id1, id2))

            val updated = repository.getTracksForPlaylist(playlistId)
            assertEquals(3, updated.size)
            assertEquals("T3", updated[0].title)
            assertEquals(0, updated[0].sortOrder)
            assertEquals("T1", updated[1].title)
            assertEquals(1, updated[1].sortOrder)
            assertEquals("T2", updated[2].title)
            assertEquals(2, updated[2].sortOrder)

            assertEquals(4_000_000L, repository.getPlaylist(playlistId)?.updatedAtMs)
        }

    @Test
    fun removeTrackFromPlaylist_byIdOnly_removesEntryAndUpdateCount() =
        runBlocking {
            val playlistId = repository.createPlaylist("单ID移除测试")
            val track1 = AudioTrack(id = "1:/1.mp3", serverId = 1L, remotePath = "/1.mp3", title = "T1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/2.mp3", serverId = 1L, remotePath = "/2.mp3", title = "T2", format = AudioFormat.MP3)

            val entryId1 = repository.addTrackToPlaylist(playlistId, track1)
            repository.addTrackToPlaylist(playlistId, track2)
            assertEquals(2, repository.getPlaylist(playlistId)?.trackCount)

            simulatedTime = 5_000_000L
            repository.removeTrackFromPlaylist(entryId1)

            val remaining = repository.getTracksForPlaylist(playlistId)
            assertEquals(1, remaining.size)
            assertEquals("T2", remaining[0].title)
            assertEquals(1, repository.getPlaylist(playlistId)?.trackCount)
            assertEquals(5_000_000L, repository.getPlaylist(playlistId)?.updatedAtMs)
        }

    @Test
    fun markTrackInactive_updatesTracksFlowWithInactiveReason() =
        runBlocking {
            val playlistId = repository.createPlaylist("失效标记测试")
            val track1 = AudioTrack(id = "1:/1.mp3", serverId = 1L, remotePath = "/1.mp3", title = "T1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/2.mp3", serverId = 1L, remotePath = "/2.mp3", title = "T2", format = AudioFormat.MP3)

            repository.addTrackToPlaylist(playlistId, track1)
            repository.addTrackToPlaylist(playlistId, track2)

            repository.markTrackInactive(1L, "/1.mp3", InactiveReason.FILE_NOT_FOUND)

            val tracks = repository.getTracksForPlaylistFlow(playlistId).first()
            assertEquals(2, tracks.size)
            assertTrue(tracks[0].isInactive)
            assertEquals(InactiveReason.FILE_NOT_FOUND, tracks[0].inactiveReason)
            assertFalse(tracks[1].isInactive)
            assertNull(tracks[1].inactiveReason)
        }

    @Test
    fun removeTracksFromPlaylist_batchRemovesEntriesAndUpdateCount() =
        runBlocking {
            val playlistId = repository.createPlaylist("批量移除测试")
            val track1 = AudioTrack(id = "1:/1.mp3", serverId = 1L, remotePath = "/1.mp3", title = "T1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/2.mp3", serverId = 1L, remotePath = "/2.mp3", title = "T2", format = AudioFormat.MP3)
            val track3 = AudioTrack(id = "1:/3.mp3", serverId = 1L, remotePath = "/3.mp3", title = "T3", format = AudioFormat.MP3)

            val id1 = repository.addTrackToPlaylist(playlistId, track1)
            repository.addTrackToPlaylist(playlistId, track2)
            val id3 = repository.addTrackToPlaylist(playlistId, track3)

            assertEquals(3, repository.getPlaylist(playlistId)?.trackCount)

            repository.removeTracksFromPlaylist(playlistId, listOf(id1, id3))

            val remaining = repository.getTracksForPlaylist(playlistId)
            assertEquals(1, remaining.size)
            assertEquals("T2", remaining[0].title)
            assertEquals(1, repository.getPlaylist(playlistId)?.trackCount)
        }

    @Test
    fun markTrackInactive_persistsToDatabaseAndSurvivesRestart() =
        runBlocking {
            val playlistId = repository.createPlaylist("持久化失效测试")
            val track1 = AudioTrack(id = "1:/1.mp3", serverId = 1L, remotePath = "/1.mp3", title = "T1", format = AudioFormat.MP3)
            repository.addTrackToPlaylist(playlistId, track1)

            repository.markTrackInactive(1L, "/1.mp3", InactiveReason.FILE_NOT_FOUND)
            kotlinx.coroutines.delay(100)

            val newRepo = PlaylistRepositoryImpl(database.playlistDao(), timeProvider = { simulatedTime })
            val tracks = newRepo.getTracksForPlaylist(playlistId)
            assertEquals(1, tracks.size)
            assertTrue(tracks[0].isInactive)
            assertEquals(InactiveReason.FILE_NOT_FOUND, tracks[0].inactiveReason)
        }

    private class TestServerRepository(
        initialServers: List<WebDavServer> = emptyList(),
    ) : ServerRepository {
        val serversFlow = MutableStateFlow(initialServers)

        override fun getAllServers(): Flow<List<WebDavServer>> = serversFlow.asStateFlow()

        override fun getActiveServer(): Flow<WebDavServer?> = serversFlow.map { it.firstOrNull() }

        override suspend fun getServerById(id: Long): WebDavServer? = serversFlow.value.firstOrNull { it.id == id }

        override suspend fun saveServer(server: WebDavServer): Long = server.id

        override suspend fun deleteServer(id: Long) {
            serversFlow.update { list -> list.filterNot { it.id == id } }
        }

        override suspend fun setActiveServer(id: Long) {}
    }

    private class TestTrackMetadataRepository : TrackMetadataRepository {
        val cachedMap = mutableMapOf<String, TrackMetadata>()
        val singleResolvedMap = mutableMapOf<String, TrackMetadata>()
        var singleResolveCalls = 0

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> = emptyFlow()

        override fun getMetadataForPathsFlow(
            serverId: Long,
            remotePaths: List<String>,
        ): Flow<List<TrackMetadata>> = emptyFlow()

        override fun getMetadataFlow(
            serverId: Long,
            remotePath: String,
        ): Flow<TrackMetadata?> = emptyFlow()

        override suspend fun getCachedMetadata(
            serverId: Long,
            remotePath: String,
        ): TrackMetadata? = cachedMap["$serverId:$remotePath"]

        override suspend fun resolveMetadata(
            server: WebDavServer,
            files: List<RemoteFile>,
            forceRefresh: Boolean,
        ) {}

        override suspend fun resolveSingleTrackMetadata(
            server: WebDavServer,
            file: RemoteFile,
        ): TrackMetadata {
            singleResolveCalls++
            return singleResolvedMap["${server.id}:${file.path}"] ?: TrackMetadata(serverId = server.id, remotePath = file.path)
        }
    }

    @Test
    fun getTracksForPlaylistFlow_withServerRepository_marksUnreachableServersInactive() =
        runBlocking {
            val server1 = WebDavServer(id = 1L, name = "Active NAS", url = "http://192.168.1.100")
            val serverRepo = TestServerRepository(listOf(server1))

            val repoWithServer =
                PlaylistRepositoryImpl(
                    playlistDao = dao,
                    serverRepository = serverRepo,
                    timeProvider = { simulatedTime },
                )

            val playlistId = repoWithServer.createPlaylist("跨服连通性测试")
            val track1 =
                AudioTrack(id = "1:/online.mp3", serverId = 1L, remotePath = "/online.mp3", title = "Online", format = AudioFormat.MP3)
            val track2 =
                AudioTrack(
                    id = "2:/offline.mp3",
                    serverId = 2L,
                    remotePath = "/offline.mp3",
                    title = "Offline Server",
                    format = AudioFormat.MP3,
                )

            repoWithServer.addTrackToPlaylist(playlistId, track1)
            repoWithServer.addTrackToPlaylist(playlistId, track2)

            val tracks = repoWithServer.getTracksForPlaylistFlow(playlistId).first()
            assertEquals(2, tracks.size)

            val online = tracks.first { it.remotePath == "/online.mp3" }
            assertFalse(online.isInactive)
            assertNull(online.inactiveReason)

            val offline = tracks.first { it.remotePath == "/offline.mp3" }
            assertTrue(offline.isInactive)
            assertEquals(InactiveReason.SERVER_UNREACHABLE, offline.inactiveReason)

            // Also verify suspend getTracksForPlaylist
            val snapshotTracks = repoWithServer.getTracksForPlaylist(playlistId)
            val snapshotOffline = snapshotTracks.first { it.remotePath == "/offline.mp3" }
            assertTrue(snapshotOffline.isInactive)
            assertEquals(InactiveReason.SERVER_UNREACHABLE, snapshotOffline.inactiveReason)
        }

    @Test
    fun getTracksForPlaylistFlow_withTrackMetadataRepository_enrichesCachedCoverAndTriggersSelfHealing() =
        runBlocking {
            val server1 = WebDavServer(id = 1L, name = "Active NAS", url = "http://192.168.1.100")
            val serverRepo = TestServerRepository(listOf(server1))
            val metadataRepo = TestTrackMetadataRepository()

            // /cached.mp3 has cached cover in Room metadata table
            metadataRepo.cachedMap["1:/cached.mp3"] =
                TrackMetadata(
                    serverId = 1L,
                    remotePath = "/cached.mp3",
                    title = "Cached Song",
                    coverThumbnailPath = "/covers/cached_thumb.jpg",
                )

            // /resolve.mp3 needs on-demand resolve
            metadataRepo.singleResolvedMap["1:/resolve.mp3"] =
                TrackMetadata(
                    serverId = 1L,
                    remotePath = "/resolve.mp3",
                    title = "Resolved Song",
                    coverThumbnailPath = "/covers/resolved_thumb.jpg",
                )

            val repo =
                PlaylistRepositoryImpl(
                    playlistDao = dao,
                    serverRepository = serverRepo,
                    trackMetadataRepository = metadataRepo,
                    timeProvider = { simulatedTime },
                )

            val playlistId = repo.createPlaylist("自愈测试")
            val trackCached =
                AudioTrack(id = "1:/cached.mp3", serverId = 1L, remotePath = "/cached.mp3", title = "Cached", format = AudioFormat.MP3)
            val trackResolve =
                AudioTrack(id = "1:/resolve.mp3", serverId = 1L, remotePath = "/resolve.mp3", title = "Resolve", format = AudioFormat.MP3)

            val entryCachedId = repo.addTrackToPlaylist(playlistId, trackCached)
            val entryResolveId = repo.addTrackToPlaylist(playlistId, trackResolve)

            val tracks = repo.getTracksForPlaylistFlow(playlistId).first()
            assertEquals(2, tracks.size)

            val cachedTrack = tracks.first { it.remotePath == "/cached.mp3" }
            assertEquals("/covers/cached_thumb.jpg", cachedTrack.coverThumbnailPath)

            // Wait for background self-healing coroutine to complete
            kotlinx.coroutines.delay(200)

            // Verify the resolved track has been healed and saved to DB
            val updatedResolveEntry = dao.getTrackById(entryResolveId)
            assertEquals("/covers/resolved_thumb.jpg", updatedResolveEntry?.coverThumbnailPath)

            val updatedCachedEntry = dao.getTrackById(entryCachedId)
            assertEquals("/covers/cached_thumb.jpg", updatedCachedEntry?.coverThumbnailPath)
        }

    private class FakeProbeWebDavClient : WebDavClient {
        val testConnectionResults = mutableMapOf<Long, ConnectionResult>()
        var testConnectionCalls = 0

        override suspend fun testConnection(server: WebDavServer): ConnectionResult {
            testConnectionCalls++
            return testConnectionResults[server.id] ?: ConnectionResult.Success
        }

        override suspend fun listDirectory(
            server: WebDavServer,
            path: String,
        ): ListDirectoryResult = ListDirectoryResult.Success(RemoteDirectory(path = path, name = "root", files = emptyList()))

        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long,
        ): ByteArray? = null
    }

    @Test
    fun playlistDao_batchQueriesAndUpdates_forUnreachableServerStatus() =
        runBlocking {
            val playlistId = repository.createPlaylist("DAO批处理测试")
            val t1 = AudioTrack(id = "10:/s1.mp3", serverId = 10L, remotePath = "/s1.mp3", title = "S1", format = AudioFormat.MP3)
            val t2 = AudioTrack(id = "20:/s2.mp3", serverId = 20L, remotePath = "/s2.mp3", title = "S2", format = AudioFormat.MP3)
            val t3 = AudioTrack(id = "30:/s3.mp3", serverId = 30L, remotePath = "/s3.mp3", title = "S3", format = AudioFormat.MP3)

            repository.addTrackToPlaylist(playlistId, t1)
            repository.addTrackToPlaylist(playlistId, t2)
            repository.addTrackToPlaylist(playlistId, t3)

            dao.updateTrackInactiveStatus(10L, "/s1.mp3", true, InactiveReason.SERVER_UNREACHABLE.name)
            dao.updateTrackInactiveStatus(20L, "/s2.mp3", true, InactiveReason.SERVER_UNREACHABLE.name)
            dao.updateTrackInactiveStatus(30L, "/s3.mp3", true, InactiveReason.FILE_NOT_FOUND.name)

            val unreachableServerIds = dao.getDistinctServerIdsForInactiveReason(InactiveReason.SERVER_UNREACHABLE.name)
            assertEquals(2, unreachableServerIds.size)
            assertTrue(unreachableServerIds.contains(10L))
            assertTrue(unreachableServerIds.contains(20L))
            assertFalse(unreachableServerIds.contains(30L))

            // Clear server 10
            val cleared = dao.clearServerUnreachableStatus(10L, InactiveReason.SERVER_UNREACHABLE.name)
            assertEquals(1, cleared)

            val remainingUnreachable = dao.getDistinctServerIdsForInactiveReason(InactiveReason.SERVER_UNREACHABLE.name)
            assertEquals(listOf(20L), remainingUnreachable)

            val track1FromDb = dao.findTrackInPlaylist(playlistId, 10L, "/s1.mp3", null)
            assertNotNull(track1FromDb)
            assertFalse(track1FromDb!!.isInactive)
            assertNull(track1FromDb.inactiveReason)
        }

    @Test
    fun triggerReachabilitySelfHealing_restoresDimmedTracksForSuccessfulServers() =
        runBlocking {
            val server1 = WebDavServer(id = 1L, name = "Online NAS", url = "http://192.168.1.100")
            val server2 = WebDavServer(id = 2L, name = "Offline NAS", url = "http://192.168.1.200")
            val serverRepo = TestServerRepository(listOf(server1, server2))

            val fakeWebDavClient = FakeProbeWebDavClient()
            fakeWebDavClient.testConnectionResults[1L] = ConnectionResult.Success
            fakeWebDavClient.testConnectionResults[2L] = ConnectionResult.Failure("Connection refused")

            val healingRepo =
                PlaylistRepositoryImpl(
                    playlistDao = dao,
                    serverRepository = serverRepo,
                    webDavClient = fakeWebDavClient,
                    timeProvider = { simulatedTime },
                )

            val playlistId = healingRepo.createPlaylist("自愈恢复测试")
            val track1 =
                AudioTrack(id = "1:/online.mp3", serverId = 1L, remotePath = "/online.mp3", title = "Online", format = AudioFormat.MP3)
            val track2 =
                AudioTrack(id = "2:/offline.mp3", serverId = 2L, remotePath = "/offline.mp3", title = "Offline", format = AudioFormat.MP3)

            val e1 = healingRepo.addTrackToPlaylist(playlistId, track1)
            val e2 = healingRepo.addTrackToPlaylist(playlistId, track2)

            // Mark both as SERVER_UNREACHABLE
            healingRepo.markTrackInactive(1L, "/online.mp3", InactiveReason.SERVER_UNREACHABLE)
            healingRepo.markTrackInactive(2L, "/offline.mp3", InactiveReason.SERVER_UNREACHABLE)

            // Verify both tracks are currently dimmed
            val beforeTracks = healingRepo.getTracksForPlaylistFlow(playlistId).first()
            assertTrue(beforeTracks.first { it.id == e1 }.isInactive)
            assertTrue(beforeTracks.first { it.id == e2 }.isInactive)

            // Trigger self-healing
            healingRepo.triggerReachabilitySelfHealing()

            // Verify after healing: Server 1 track restored, Server 2 track remains dimmed
            val afterTracks = healingRepo.getTracksForPlaylistFlow(playlistId).first()
            val restoredTrack1 = afterTracks.first { it.id == e1 }
            assertFalse("Track 1 on Online NAS must be restored", restoredTrack1.isInactive)
            assertNull("Track 1 inactiveReason must be cleared", restoredTrack1.inactiveReason)

            val dimmedTrack2 = afterTracks.first { it.id == e2 }
            assertTrue("Track 2 on Offline NAS must remain inactive", dimmedTrack2.isInactive)
            assertEquals(InactiveReason.SERVER_UNREACHABLE, dimmedTrack2.inactiveReason)

            // Verify DB entity state for track 1
            val dbTrack1 = dao.getTrackById(e1)
            assertNotNull(dbTrack1)
            assertFalse(dbTrack1!!.isInactive)
            assertNull(dbTrack1.inactiveReason)
        }
}
