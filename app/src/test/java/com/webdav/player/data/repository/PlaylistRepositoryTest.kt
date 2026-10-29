package com.webdav.player.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.PlaylistDao
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.PlaylistTrackEntry
import kotlinx.coroutines.flow.first
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
            val id2 = repository.addTrackToPlaylist(playlistId, track2)
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
}
