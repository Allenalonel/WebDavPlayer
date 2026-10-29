package com.webdav.player.ui.playlist

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.PlaylistRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

class DetailFakeServerRepository(
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

class DetailFakePlaylistRepository : PlaylistRepository {
    val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    val tracksMap = mutableMapOf<Long, MutableList<PlaylistTrackEntry>>()
    val tracksFlowMap = mutableMapOf<Long, MutableStateFlow<List<PlaylistTrackEntry>>>()

    private fun getOrCreateTracksFlow(playlistId: Long): MutableStateFlow<List<PlaylistTrackEntry>> =
        tracksFlowMap.getOrPut(playlistId) {
            MutableStateFlow(tracksMap.getOrPut(playlistId) { mutableListOf() }.toList())
        }

    private fun syncTracksFlow(playlistId: Long) {
        val list = tracksMap.getOrPut(playlistId) { mutableListOf() }
        getOrCreateTracksFlow(playlistId).value = list.toList()
    }

    override fun getAllPlaylistsFlow(): Flow<List<Playlist>> = playlistsFlow.asStateFlow()

    override suspend fun getAllPlaylists(): List<Playlist> = playlistsFlow.value

    override fun getPlaylistFlow(playlistId: Long): Flow<Playlist?> = playlistsFlow.map { list -> list.firstOrNull { it.id == playlistId } }

    override suspend fun getPlaylist(playlistId: Long): Playlist? = playlistsFlow.value.firstOrNull { it.id == playlistId }

    override suspend fun createPlaylist(name: String): Long {
        val id = (playlistsFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val p = Playlist(id = id, name = name, createdAtMs = 1000L, updatedAtMs = 1000L, trackCount = 0)
        playlistsFlow.update { it + p }
        return id
    }

    override suspend fun renamePlaylist(
        playlistId: Long,
        newName: String,
    ) {
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(name = newName) else it }
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistsFlow.update { current -> current.filterNot { it.id == playlistId } }
        tracksMap.remove(playlistId)
        syncTracksFlow(playlistId)
    }

    override suspend fun isPlaylistNameExists(
        name: String,
        excludePlaylistId: Long?,
    ): Boolean =
        playlistsFlow.value.any {
            it.name.equals(name.trim(), ignoreCase = true) && (excludePlaylistId == null || it.id != excludePlaylistId)
        }

    override fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<PlaylistTrackEntry>> =
        getOrCreateTracksFlow(playlistId).asStateFlow()

    override suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntry> = tracksMap[playlistId]?.toList() ?: emptyList()

    override suspend fun addTrackToPlaylist(
        playlistId: Long,
        track: AudioTrack,
        cuePath: String?,
    ): Long {
        val list = tracksMap.getOrPut(playlistId) { mutableListOf() }
        val id = (tracksMap.values.flatten().maxOfOrNull { it.id } ?: 0L) + 1L
        val entry = PlaylistTrackEntry.fromAudioTrack(playlistId, track, list.size, cuePath).copy(id = id)
        list.add(entry)
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(trackCount = list.size, updatedAtMs = 2000L) else it }
        }
        syncTracksFlow(playlistId)
        return id
    }

    override suspend fun addTracksToPlaylist(
        playlistId: Long,
        tracks: List<AudioTrack>,
    ): List<Long> = tracks.map { addTrackToPlaylist(playlistId, it) }

    override suspend fun removeTracksFromPlaylist(
        playlistId: Long,
        trackEntryIds: List<Long>,
    ) {
        val list = tracksMap[playlistId] ?: return
        list.removeAll { it.id in trackEntryIds }
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(trackCount = list.size) else it }
        }
        syncTracksFlow(playlistId)
    }

    override suspend fun removeTrackFromPlaylist(trackEntryId: Long) {
        for ((pId, list) in tracksMap) {
            val removed = list.removeAll { it.id == trackEntryId }
            if (removed) {
                playlistsFlow.update { current ->
                    current.map { if (it.id == pId) it.copy(trackCount = list.size) else it }
                }
                syncTracksFlow(pId)
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
        playlistsFlow.update { current ->
            current.map { if (it.id == playlistId) it.copy(updatedAtMs = 3000L) else it }
        }
        syncTracksFlow(playlistId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: DetailFakePlaylistRepository
    private lateinit var fakeSession: FakeMusicPlayerAppSession
    private lateinit var viewModel: PlaylistDetailViewModel

    private val playlistId = 1L
    private val trackA =
        AudioTrack(
            id = "1:/track_a.mp3",
            serverId = 1L,
            remotePath = "/track_a.mp3",
            title = "Alpha",
            artist = "Zoe",
            format = AudioFormat.MP3,
        )
    private val trackB =
        AudioTrack(
            id = "1:/track_b.mp3",
            serverId = 1L,
            remotePath = "/track_b.mp3",
            title = "Charlie",
            artist = "Alice",
            format = AudioFormat.MP3,
        )
    private val trackC =
        AudioTrack(
            id = "2:/track_c.flac",
            serverId = 2L,
            remotePath = "/track_c.flac",
            title = "Bravo",
            artist = "Bob",
            format = AudioFormat.FLAC,
        )

    @Before
    fun setUp() =
        runTest(testDispatcher) {
            Dispatchers.setMain(testDispatcher)
            fakeRepo = DetailFakePlaylistRepository()
            fakeSession = FakeMusicPlayerAppSession()

            fakeRepo.createPlaylist("我的车载精选")
            fakeRepo.addTrackToPlaylist(playlistId, trackA) // id = 1
            fakeRepo.addTrackToPlaylist(playlistId, trackB) // id = 2
            fakeRepo.addTrackToPlaylist(playlistId, trackC) // id = 3

            viewModel =
                PlaylistDetailViewModel(
                    playlistId = playlistId,
                    playlistRepository = fakeRepo,
                    musicPlayerAppSession = fakeSession,
                )
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsPlaylistAndTracksSuccessfully() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            val state = viewModel.uiState.value

            assertFalse(state.isLoading)
            assertNotNull(state.playlist)
            assertEquals("我的车载精选", state.playlist?.name)
            assertEquals(3, state.tracks.size)
            assertEquals("Alpha", state.tracks[0].title)
            assertEquals("Charlie", state.tracks[1].title)
            assertEquals("Bravo", state.tracks[2].title)
            assertFalse(state.isReordering)
            assertEquals(PlaylistSortOption.CUSTOM, state.activeSortOption)
        }

    @Test
    fun moveTrack_reordersLocallyAndSetsIsReordering() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            // Move track from index 0 ("Alpha") to index 2
            viewModel.moveTrack(0, 2)

            val state = viewModel.uiState.value
            assertTrue(state.isReordering)
            assertEquals(3, state.tracks.size)
            assertEquals("Charlie", state.tracks[0].title)
            assertEquals("Bravo", state.tracks[1].title)
            assertEquals("Alpha", state.tracks[2].title)

            // Repository has not been updated yet before onDragEnd
            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals("Alpha", repoTracks[0].title)
        }

    @Test
    fun onDragEnd_persistsReorderedTracksAndClearsIsReordering() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            // Move track from index 0 ("Alpha") to index 2
            viewModel.moveTrack(0, 2)
            viewModel.onDragEnd()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isReordering)

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals(3, repoTracks.size)
            assertEquals("Charlie", repoTracks[0].title)
            assertEquals(0, repoTracks[0].sortOrder)
            assertEquals("Bravo", repoTracks[1].title)
            assertEquals(1, repoTracks[1].sortOrder)
            assertEquals("Alpha", repoTracks[2].title)
            assertEquals(2, repoTracks[2].sortOrder)
        }

    @Test
    fun updateTrackOrders_directlyPersistsNewOrder() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val currentTracks = viewModel.uiState.value.tracks
            // Pass reordered IDs: [trackC (3), trackA (1), trackB (2)]
            val newOrderIds = listOf(currentTracks[2].id, currentTracks[0].id, currentTracks[1].id)

            viewModel.updateTrackOrders(newOrderIds)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Bravo", state.tracks[0].title)
            assertEquals("Alpha", state.tracks[1].title)
            assertEquals("Charlie", state.tracks[2].title)

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals("Bravo", repoTracks[0].title)
            assertEquals(0, repoTracks[0].sortOrder)
        }

    @Test
    fun sortTracks_dateAddedDesc_sortsNewestFirstAndPersists() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.sortTracks(PlaylistSortOption.DATE_ADDED_DESC)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PlaylistSortOption.DATE_ADDED_DESC, state.activeSortOption)
            // Added order was trackA (id 1), trackB (id 2), trackC (id 3)
            // Descending should be trackC, trackB, trackA
            assertEquals(listOf("Bravo", "Charlie", "Alpha"), state.tracks.map { it.title })

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals(listOf("Bravo", "Charlie", "Alpha"), repoTracks.map { it.title })
            assertEquals(0, repoTracks[0].sortOrder)
            assertEquals(1, repoTracks[1].sortOrder)
            assertEquals(2, repoTracks[2].sortOrder)
        }

    @Test
    fun sortTracks_dateAddedAsc_sortsOldestFirstAndPersists() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            // First reverse it
            viewModel.sortTracks(PlaylistSortOption.DATE_ADDED_DESC)
            advanceUntilIdle()

            // Now sort oldest first
            viewModel.sortTracks(PlaylistSortOption.DATE_ADDED_ASC)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PlaylistSortOption.DATE_ADDED_ASC, state.activeSortOption)
            assertEquals(listOf("Alpha", "Charlie", "Bravo"), state.tracks.map { it.title })

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals(listOf("Alpha", "Charlie", "Bravo"), repoTracks.map { it.title })
        }

    @Test
    fun sortTracks_titleAsc_sortsAlphabeticallyAndPersists() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.sortTracks(PlaylistSortOption.TITLE_ASC)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PlaylistSortOption.TITLE_ASC, state.activeSortOption)
            // Alphabetical titles: "Alpha", "Bravo", "Charlie"
            assertEquals(listOf("Alpha", "Bravo", "Charlie"), state.tracks.map { it.title })

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals(listOf("Alpha", "Bravo", "Charlie"), repoTracks.map { it.title })
        }

    @Test
    fun sortTracks_artistAsc_sortsByArtistAndPersists() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.sortTracks(PlaylistSortOption.ARTIST_ASC)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PlaylistSortOption.ARTIST_ASC, state.activeSortOption)
            // Artists: Alice ("Charlie"), Bob ("Bravo"), Zoe ("Alpha")
            assertEquals(listOf("Charlie", "Bravo", "Alpha"), state.tracks.map { it.title })

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals(listOf("Charlie", "Bravo", "Alpha"), repoTracks.map { it.title })
        }

    @Test
    fun removeTrack_removesTrackFromPlaylist() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val trackToRemove = viewModel.uiState.value.tracks[1] // "Charlie", id 2
            viewModel.removeTrack(trackToRemove.id)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.tracks.size)
            assertFalse(state.tracks.any { it.id == trackToRemove.id })

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals(2, repoTracks.size)
            assertFalse(repoTracks.any { it.id == trackToRemove.id })
        }

    @Test
    fun playTrack_delegatesToMusicPlayerAppSession() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val track = viewModel.uiState.value.tracks[0]
            viewModel.playTrack(track)

            assertEquals(playlistId, fakeSession.lastPlayPlaylist?.id)
            assertEquals(0, fakeSession.lastPlayPlaylistStartIndex)
        }

    @Test
    fun playAll_delegatesToMusicPlayerAppSessionWithZeroIndex() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.playAll()

            assertEquals(playlistId, fakeSession.lastPlayPlaylist?.id)
            assertEquals(0, fakeSession.lastPlayPlaylistStartIndex)
        }

    @Test
    fun inactiveTrackDetection_whenServerMissing_marksServerUnreachable() =
        runTest(testDispatcher) {
            val server1 = WebDavServer(id = 1L, name = "Server 1", url = "http://1.1.1.1")
            val serverRepo = DetailFakeServerRepository(listOf(server1))

            // Add track on server 2 (missing from serverRepo)
            val trackOnMissingServer =
                AudioTrack(
                    id = "2:/missing.mp3",
                    serverId = 2L,
                    remotePath = "/missing.mp3",
                    title = "Offline Server Song",
                    format = AudioFormat.MP3,
                )
            fakeRepo.addTrackToPlaylist(playlistId, trackOnMissingServer)

            val vmWithServerRepo =
                PlaylistDetailViewModel(
                    playlistId = playlistId,
                    playlistRepository = fakeRepo,
                    musicPlayerAppSession = fakeSession,
                    serverRepository = serverRepo,
                )
            advanceUntilIdle()

            val tracks = vmWithServerRepo.uiState.value.tracks
            val offlineTrack = tracks.first { it.title == "Offline Server Song" }
            assertTrue(offlineTrack.isInactive)
            assertEquals(InactiveReason.SERVER_UNREACHABLE, offlineTrack.inactiveReason)
        }

    @Test
    fun onTrackClick_inactiveTrack_triggersToastAndBlocksPlayback() =
        runTest(testDispatcher) {
            val server1 = WebDavServer(id = 1L, name = "Server 1", url = "http://1.1.1.1")
            val server2 = WebDavServer(id = 2L, name = "Server 2", url = "http://2.2.2.2")
            val serverRepo = DetailFakeServerRepository(listOf(server1, server2))

            val offlineTrackEntry =
                AudioTrack(
                    id = "999:/offline.mp3",
                    serverId = 999L,
                    remotePath = "/offline.mp3",
                    title = "Unreachable Song",
                    format = AudioFormat.MP3,
                )
            fakeRepo.addTrackToPlaylist(playlistId, offlineTrackEntry)

            val vm =
                PlaylistDetailViewModel(
                    playlistId = playlistId,
                    playlistRepository = fakeRepo,
                    musicPlayerAppSession = fakeSession,
                    serverRepository = serverRepo,
                )
            advanceUntilIdle()

            var emittedToast: String? = null
            val job =
                launch {
                    vm.toastEvent.collect { message ->
                        emittedToast = message
                    }
                }
            advanceUntilIdle()

            val unreachableTrack =
                vm.uiState.value.tracks
                    .first { it.title == "Unreachable Song" }
            fakeSession.lastPlayPlaylist = null

            vm.onTrackClick(unreachableTrack)
            advanceUntilIdle()

            // Toast emitted
            assertEquals("所属服务器无法连接", emittedToast)
            // Playback NOT started
            assertNull(fakeSession.lastPlayPlaylist)

            // Test 404 inactive track
            val notFoundTrack =
                unreachableTrack.copy(
                    isInactive = true,
                    inactiveReason = InactiveReason.FILE_NOT_FOUND,
                )
            vm.onTrackClick(notFoundTrack)
            advanceUntilIdle()

            assertEquals("音频文件已被重命名或删除", emittedToast)
            assertNull(fakeSession.lastPlayPlaylist)

            job.cancel()
        }

    @Test
    fun cleanInactiveTracks_removesAllInactiveTracks() =
        runTest(testDispatcher) {
            val server1 = WebDavServer(id = 1L, name = "Server 1", url = "http://1.1.1.1")
            val server2 = WebDavServer(id = 2L, name = "Server 2", url = "http://2.2.2.2")
            val serverRepo = DetailFakeServerRepository(listOf(server1, server2))

            val offline1 =
                AudioTrack(id = "888:/off1.mp3", serverId = 888L, remotePath = "/off1.mp3", title = "Off 1", format = AudioFormat.MP3)
            val offline2 =
                AudioTrack(id = "999:/off2.mp3", serverId = 999L, remotePath = "/off2.mp3", title = "Off 2", format = AudioFormat.MP3)
            fakeRepo.addTrackToPlaylist(playlistId, offline1)
            fakeRepo.addTrackToPlaylist(playlistId, offline2)

            val vm =
                PlaylistDetailViewModel(
                    playlistId = playlistId,
                    playlistRepository = fakeRepo,
                    musicPlayerAppSession = fakeSession,
                    serverRepository = serverRepo,
                )
            advanceUntilIdle()

            // Initially 3 active + 2 offline = 5 tracks
            assertEquals(5, vm.uiState.value.tracks.size)
            assertEquals(
                2,
                vm.uiState.value.tracks
                    .count { it.isInactive },
            )

            vm.cleanInactiveTracks()
            advanceUntilIdle()

            // After clean, only 3 active tracks remain
            val remaining = vm.uiState.value.tracks
            assertEquals(3, remaining.size)
            assertTrue(remaining.none { it.isInactive })

            val repoTracks = fakeRepo.getTracksForPlaylist(playlistId)
            assertEquals(3, repoTracks.size)
        }

    @Test
    fun playNext_delegatesToMusicPlayerAppSession() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val track = viewModel.uiState.value.tracks[1]
            viewModel.playNext(track)

            assertEquals("Charlie", fakeSession.lastPlayNextTrack?.title)
        }

    @Test
    fun clearError_clearsErrorMessage() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            viewModel.clearError()
            assertNull(viewModel.uiState.value.errorMessage)
        }
}
