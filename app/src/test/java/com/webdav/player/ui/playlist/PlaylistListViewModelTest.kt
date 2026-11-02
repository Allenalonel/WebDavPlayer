package com.webdav.player.ui.playlist

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlaylistRepository : PlaylistRepository {
    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val tracksMap = mutableMapOf<Long, MutableList<PlaylistTrackEntry>>()
    private var nextPlaylistId = 1L
    private var nextTrackId = 1L

    override fun getAllPlaylistsFlow(): Flow<List<Playlist>> = playlistsFlow.asStateFlow()

    override suspend fun getAllPlaylists(): List<Playlist> = playlistsFlow.value

    override fun getPlaylistFlow(playlistId: Long): Flow<Playlist?> = playlistsFlow.map { list -> list.firstOrNull { it.id == playlistId } }

    override suspend fun getPlaylist(playlistId: Long): Playlist? = playlistsFlow.value.firstOrNull { it.id == playlistId }

    override suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        require(playlistsFlow.value.none { it.name.equals(trimmed, ignoreCase = true) })
        val id = nextPlaylistId++
        val p = Playlist(id = id, name = trimmed, createdAtMs = 1000L, updatedAtMs = 1000L, trackCount = 0)
        playlistsFlow.update { it + p }
        tracksMap[id] = mutableListOf()
        return id
    }

    override suspend fun renamePlaylist(
        playlistId: Long,
        newName: String,
    ) {
        val trimmed = newName.trim()
        playlistsFlow.update { list ->
            list.map {
                if (it.id == playlistId) it.copy(name = trimmed, updatedAtMs = 2000L) else it
            }
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistsFlow.update { list -> list.filterNot { it.id == playlistId } }
        tracksMap.remove(playlistId)
    }

    override suspend fun isPlaylistNameExists(
        name: String,
        excludePlaylistId: Long?,
    ): Boolean {
        val trimmed = name.trim()
        return playlistsFlow.value.any { it.name.equals(trimmed, ignoreCase = true) && it.id != excludePlaylistId }
    }

    override fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<PlaylistTrackEntry>> =
        MutableStateFlow(tracksMap[playlistId] ?: emptyList())

    override suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntry> = tracksMap[playlistId]?.toList() ?: emptyList()

    override suspend fun addTrackToPlaylist(
        playlistId: Long,
        track: AudioTrack,
        cuePath: String?,
    ): Long {
        val list = tracksMap.getOrPut(playlistId) { mutableListOf() }
        val vId = if (track.isVirtualTrack) track.id else null
        if (list.any { it.serverId == track.serverId && it.remotePath == track.remotePath && it.virtualTrackId == vId }) {
            return -1L
        }
        val id = nextTrackId++
        val entry = PlaylistTrackEntry.fromAudioTrack(playlistId, track, list.size, cuePath).copy(id = id)
        list.add(entry)
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(trackCount = list.size, updatedAtMs = 3000L) else it }
        }
        return id
    }

    override suspend fun addTracksToPlaylist(
        playlistId: Long,
        tracks: List<AudioTrack>,
    ): List<Long> =
        tracks
            .map {
                addTrackToPlaylist(playlistId, it)
            }.filter { it > 0L }

    override suspend fun removeTracksFromPlaylist(
        playlistId: Long,
        trackEntryIds: List<Long>,
    ) {
        val list = tracksMap[playlistId] ?: return
        list.removeAll { it.id in trackEntryIds }
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(trackCount = list.size) else it }
        }
    }

    override suspend fun removeTrackFromPlaylist(trackEntryId: Long) {
        for ((pId, list) in tracksMap) {
            val removed = list.removeAll { it.id == trackEntryId }
            if (removed) {
                playlistsFlow.update { current ->
                    current.map { if (it.id == pId) it.copy(trackCount = list.size) else it }
                }
                break
            }
        }
    }

    override suspend fun updateTrackOrders(
        playlistId: Long,
        orderedTrackIds: List<Long>,
    ) {
        val list = tracksMap[playlistId] ?: return
        val map = list.associateBy { it.id }
        val reordered = orderedTrackIds.mapIndexedNotNull { idx, id -> map[id]?.copy(sortOrder = idx) }
        list.clear()
        list.addAll(reordered)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakePlaylistRepository
    private lateinit var viewModel: PlaylistListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakePlaylistRepository()
        viewModel = PlaylistListViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsPlaylistsFlow() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.playlists.isEmpty())
        }

    @Test
    fun createPlaylist_success_updatesStateAndClosesDialog() =
        runTest(testDispatcher) {
            viewModel.openCreateDialog()
            assertTrue(viewModel.uiState.value.showCreateDialog)

            var createdId = -1L
            viewModel.createPlaylist("我的新歌单") { createdId = it }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.showCreateDialog)
            assertNull(state.errorMessage)
            assertEquals(1, state.playlists.size)
            assertEquals("我的新歌单", state.playlists[0].name)
            assertTrue(createdId > 0)
        }

    @Test
    fun createPlaylist_emptyName_setsErrorMessage() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("   ")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("歌单名称不能为空", state.errorMessage)
        }

    @Test
    fun createPlaylist_duplicateName_setsErrorMessage() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("流行榜")
            advanceUntilIdle()

            viewModel.createPlaylist("流行榜")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("歌单 “流行榜” 已存在", state.errorMessage)
        }

    @Test
    fun renamePlaylist_success_updatesPlaylistName() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("旧歌单名")
            advanceUntilIdle()
            val playlist =
                viewModel.uiState.value.playlists
                    .first()

            viewModel.openRenameDialog(playlist)
            assertEquals(playlist, viewModel.uiState.value.playlistToRename)

            viewModel.renamePlaylist(playlist.id, "更新后的歌单名")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.playlistToRename)
            assertNull(state.errorMessage)
            assertEquals("更新后的歌单名", state.playlists.first().name)
        }

    @Test
    fun renamePlaylist_duplicateName_setsError() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("歌单A")
            viewModel.createPlaylist("歌单B")
            advanceUntilIdle()

            val playlistB =
                viewModel.uiState.value.playlists
                    .first { it.name == "歌单B" }
            viewModel.renamePlaylist(playlistB.id, "歌单A")
            advanceUntilIdle()

            assertEquals("歌单 “歌单A” 已存在", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun deletePlaylist_confirmsAndDelete() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("待删歌单")
            advanceUntilIdle()
            val playlist =
                viewModel.uiState.value.playlists
                    .first()

            viewModel.openDeleteDialog(playlist)
            assertEquals(playlist, viewModel.uiState.value.playlistToDelete)

            viewModel.confirmDeletePlaylist(playlist.id)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.playlistToDelete)
            assertTrue(state.playlists.isEmpty())
        }

    @Test
    fun createPlaylistAndAddTrack_createsAndAddsTrack() =
        runTest(testDispatcher) {
            val sampleTrack =
                AudioTrack(
                    id = "1:/song.flac",
                    serverId = 1L,
                    remotePath = "/song.flac",
                    title = "Song",
                    format = AudioFormat.FLAC,
                )

            var successCalled = false
            viewModel.createPlaylistAndAddTrack("夜曲歌单", sampleTrack) {
                successCalled = true
            }
            advanceUntilIdle()

            assertTrue(successCalled)
            val state = viewModel.uiState.value
            assertEquals(1, state.playlists.size)
            assertEquals("夜曲歌单", state.playlists[0].name)
            assertEquals(1, state.playlists[0].trackCount)
        }

    @Test
    fun addTrackToPlaylist_addsTrackSuccessfully() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("已有歌单")
            advanceUntilIdle()
            val playlist =
                viewModel.uiState.value.playlists
                    .first()

            val sampleTrack =
                AudioTrack(
                    id = "1:/song.flac",
                    serverId = 1L,
                    remotePath = "/song.flac",
                    title = "Song",
                    format = AudioFormat.FLAC,
                )

            var successCalled = false
            viewModel.addTrackToPlaylist(playlist.id, sampleTrack) {
                successCalled = it
            }
            advanceUntilIdle()

            assertTrue(successCalled)
            val state = viewModel.uiState.value
            assertEquals(1, state.playlists[0].trackCount)
        }

    @Test
    fun addTrackToPlaylist_duplicateTrack_returnsFalseAndDoesNotIncrementCount() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("已有歌单")
            advanceUntilIdle()
            val playlist =
                viewModel.uiState.value.playlists
                    .first()
            val sampleTrack =
                AudioTrack(
                    id = "1:/song.flac",
                    serverId = 1L,
                    remotePath = "/song.flac",
                    title = "Song",
                    format = AudioFormat.FLAC,
                )

            var firstAdded: Boolean? = null
            viewModel.addTrackToPlaylist(playlist.id, sampleTrack) { firstAdded = it }
            advanceUntilIdle()
            assertEquals(true, firstAdded)
            assertEquals(
                1,
                viewModel.uiState.value.playlists[0]
                    .trackCount,
            )

            var secondAdded: Boolean? = null
            viewModel.addTrackToPlaylist(playlist.id, sampleTrack) { secondAdded = it }
            advanceUntilIdle()
            assertEquals(false, secondAdded)
            assertEquals(
                1,
                viewModel.uiState.value.playlists[0]
                    .trackCount,
            )
        }

    @Test
    fun addTracksToPlaylist_batchAddsAndDeduplicates_returnsCorrectCounts() =
        runTest(testDispatcher) {
            viewModel.createPlaylist("批量歌单")
            advanceUntilIdle()
            val playlist =
                viewModel.uiState.value.playlists
                    .first()

            val track1 = AudioTrack(id = "1:/s1.mp3", serverId = 1L, remotePath = "/s1.mp3", title = "S1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/s2.mp3", serverId = 1L, remotePath = "/s2.mp3", title = "S2", format = AudioFormat.MP3)
            val track3 = AudioTrack(id = "1:/s3.mp3", serverId = 1L, remotePath = "/s3.mp3", title = "S3", format = AudioFormat.MP3)

            // First add track1
            viewModel.addTrackToPlaylist(playlist.id, track1)
            advanceUntilIdle()

            // Now batch add track1 (duplicate), track2 (new), track3 (new)
            var added: Int? = null
            var skipped: Int? = null
            viewModel.addTracksToPlaylist(playlist.id, listOf(track1, track2, track3)) { a, s ->
                added = a
                skipped = s
            }
            advanceUntilIdle()

            assertEquals(2, added)
            assertEquals(1, skipped)
            assertEquals(
                3,
                viewModel.uiState.value.playlists[0]
                    .trackCount,
            )
        }

    @Test
    fun createPlaylistAndAddTracks_createsNewPlaylistAndBatchAdds() =
        runTest(testDispatcher) {
            val track1 = AudioTrack(id = "1:/s1.mp3", serverId = 1L, remotePath = "/s1.mp3", title = "S1", format = AudioFormat.MP3)
            val track2 = AudioTrack(id = "1:/s2.mp3", serverId = 1L, remotePath = "/s2.mp3", title = "S2", format = AudioFormat.MP3)

            var addedCount: Int? = null
            viewModel.createPlaylistAndAddTracks("新华语精选", listOf(track1, track2)) {
                addedCount = it
            }
            advanceUntilIdle()

            assertEquals(2, addedCount)
            val state = viewModel.uiState.value
            assertEquals(1, state.playlists.size)
            assertEquals("新华语精选", state.playlists[0].name)
            assertEquals(2, state.playlists[0].trackCount)
        }
}
