package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import com.webdav.player.domain.session.FakeMusicPlayerAppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeServerRepository: FakeServerRepository
    private lateinit var fakeDirectoryRepository: FakeDirectoryRepository
    private lateinit var fakeMusicPlayerAppSession: FakeMusicPlayerAppSession
    private lateinit var fakeTrackMetadataRepository: FakeTrackMetadataRepository
    private lateinit var viewModel: DirectoryBrowserViewModel

    private val sampleServer = WebDavServer(
        id = 1L,
        name = "My NAS",
        url = "http://nas.local",
        port = 5005,
        isDefault = true
    )

    private val rootDir = RemoteDirectory(
        path = "/",
        name = "根目录",
        subDirectories = listOf(
            RemoteDirectory(path = "/Music/", name = "Music")
        ),
        files = listOf(
            RemoteFile(name = "root_track.mp3", path = "/root_track.mp3", size = 1000)
        )
    )

    private val musicDir = RemoteDirectory(
        path = "/Music/",
        name = "Music",
        subDirectories = listOf(
            RemoteDirectory(path = "/Music/Rock/", name = "Rock")
        ),
        files = listOf(
            RemoteFile(name = "song.flac", path = "/Music/song.flac", size = 2000)
        )
    )

    private val rockDir = RemoteDirectory(
        path = "/Music/Rock/",
        name = "Rock",
        subDirectories = emptyList(),
        files = listOf(
            RemoteFile(name = "queen.mp3", path = "/Music/Rock/queen.mp3", size = 3000)
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeServerRepository = FakeServerRepository()
        fakeDirectoryRepository = FakeDirectoryRepository()
        fakeMusicPlayerAppSession = FakeMusicPlayerAppSession()
        fakeTrackMetadataRepository = FakeTrackMetadataRepository()
        fakeServerRepository.setActiveServerSync(sampleServer)

        fakeDirectoryRepository.setResult("/", ListDirectoryResult.Success(rootDir))
        fakeDirectoryRepository.setResult("/Music/", ListDirectoryResult.Success(musicDir))
        fakeDirectoryRepository.setResult("/Music/Rock/", ListDirectoryResult.Success(rockDir))

        viewModel = DirectoryBrowserViewModel(
            serverRepository = fakeServerRepository,
            directoryRepository = fakeDirectoryRepository,
            musicPlayerAppSession = fakeMusicPlayerAppSession,
            trackMetadataRepository = fakeTrackMetadataRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsRootDirectory_whenActiveServerExists() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(sampleServer, state.activeServer)
        assertEquals("/", state.currentPath)
        assertEquals(1, state.breadcrumbs.size)
        assertEquals("/", state.breadcrumbs.first().path)
        assertFalse(state.canNavigateUp)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)

        assertEquals(1, state.subDirectories.size)
        assertEquals("Music", state.subDirectories.first().name)
        assertEquals(1, state.files.size)
        assertEquals("root_track.mp3", state.files.first().name)
    }

    @Test
    fun onDirectoryClicked_navigatesIntoSubdirectory_andUpdatesBreadcrumbs() = runTest {
        advanceUntilIdle()

        viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/", name = "Music"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/Music/", state.currentPath)
        assertEquals(2, state.breadcrumbs.size)
        assertEquals("/", state.breadcrumbs[0].path)
        assertEquals("/Music/", state.breadcrumbs[1].path)
        assertEquals("Music", state.breadcrumbs[1].name)
        assertTrue(state.canNavigateUp)

        assertEquals(1, state.subDirectories.size)
        assertEquals("Rock", state.subDirectories.first().name)
        assertEquals(1, state.files.size)
        assertEquals("song.flac", state.files.first().name)
    }

    @Test
    fun onNavigateUp_returnsToParentDirectory() = runTest {
        advanceUntilIdle()

        viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/", name = "Music"))
        advanceUntilIdle()

        viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/Rock/", name = "Rock"))
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals("/Music/Rock/", state.currentPath)
        assertEquals(3, state.breadcrumbs.size)
        assertTrue(state.canNavigateUp)

        // Navigate up to /Music/
        val handled1 = viewModel.onNavigateUp()
        advanceUntilIdle()
        assertTrue(handled1)

        state = viewModel.uiState.value
        assertEquals("/Music/", state.currentPath)
        assertEquals(2, state.breadcrumbs.size)
        assertTrue(state.canNavigateUp)

        // Navigate up to root /
        val handled2 = viewModel.onNavigateUp()
        advanceUntilIdle()
        assertTrue(handled2)

        state = viewModel.uiState.value
        assertEquals("/", state.currentPath)
        assertEquals(1, state.breadcrumbs.size)
        assertFalse(state.canNavigateUp)

        // Cannot navigate up past root
        val handled3 = viewModel.onNavigateUp()
        assertFalse(handled3)
    }

    @Test
    fun onBreadcrumbClicked_navigatesDirectlyToAncestor() = runTest {
        advanceUntilIdle()

        viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/", name = "Music"))
        advanceUntilIdle()
        viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/Rock/", name = "Rock"))
        advanceUntilIdle()

        // Jump back to Music via breadcrumb
        viewModel.onBreadcrumbClicked(Breadcrumb(name = "Music", path = "/Music/"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/Music/", state.currentPath)
        assertEquals(2, state.breadcrumbs.size)
        assertEquals("Rock", state.subDirectories.first().name)
    }

    @Test
    fun onRefresh_callsRepositoryWithForceRefresh() = runTest {
        advanceUntilIdle()

        viewModel.onRefresh()
        advanceUntilIdle()

        assertEquals(1, fakeDirectoryRepository.forceRefreshCount["/"])
        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
    }

    @Test
    fun directoryLoadFailure_showsErrorMessage_andCanRetry() = runTest {
        advanceUntilIdle()

        fakeDirectoryRepository.setResult("/Unreachable/", ListDirectoryResult.Failure("HTTP 404: Not Found", 404))

        viewModel.onDirectoryClicked(RemoteDirectory(path = "/Unreachable/", name = "Unreachable"))
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals("HTTP 404: Not Found", state.errorMessage)
        assertFalse(state.isLoading)

        // Now fix the result and retry
        val recoveredDir = RemoteDirectory(path = "/Unreachable/", name = "Unreachable")
        fakeDirectoryRepository.setResult("/Unreachable/", ListDirectoryResult.Success(recoveredDir))

        viewModel.onRetry()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals(recoveredDir, state.currentDirectory)
    }

    @Test
    fun onAudioTrackClicked_dispatchesToMusicPlayerAppSession() = runTest {
        advanceUntilIdle()

        val audioFile = rootDir.files.first()
        viewModel.onAudioTrackClicked(audioFile)

        assertEquals(rootDir, fakeMusicPlayerAppSession.lastPlayDirectory)
        assertEquals(audioFile, fakeMusicPlayerAppSession.lastPlaySelectedFile)
    }

    @Test
    fun onAudioTrackClicked_ignoresNonAudioFiles() = runTest {
        advanceUntilIdle()

        val nonAudioFile = RemoteFile(name = "lyrics.lrc", path = "/lyrics.lrc", fileType = RemoteFileType.Lyrics)
        viewModel.onAudioTrackClicked(nonAudioFile)

        assertNull(fakeMusicPlayerAppSession.lastPlayDirectory)
        assertNull(fakeMusicPlayerAppSession.lastPlaySelectedFile)
    }

    @Test
    fun togglePlayPause_dispatchesToMusicPlayerAppSession() = runTest {
        advanceUntilIdle()

        viewModel.togglePlayPause()

        assertEquals(1, fakeMusicPlayerAppSession.togglePlayPauseCount)
    }

    @Test
    fun playerControls_dispatchToMusicPlayerAppSession() = runTest {
        advanceUntilIdle()

        viewModel.seekTo(12345L)
        assertEquals(12345L, fakeMusicPlayerAppSession.lastSeekPosition)

        viewModel.skipToNext()
        assertEquals(1, fakeMusicPlayerAppSession.skipNextCount)

        viewModel.skipToPrevious()
        assertEquals(1, fakeMusicPlayerAppSession.skipPreviousCount)

        viewModel.cyclePlaybackMode()
        assertEquals(PlaybackMode.SINGLE_LOOP, fakeMusicPlayerAppSession.sessionState.value.playbackMode)

        viewModel.playQueueIndex(3)
        assertEquals(3, fakeMusicPlayerAppSession.lastPlayedQueueIndex)

        viewModel.removeQueueTrack(2)
        assertEquals(listOf(2), fakeMusicPlayerAppSession.removedQueueIndices)
    }

    @Test
    fun directoryLoad_triggersAsynchronousMetadataResolution_andUpdatesUiStateIncrementally() = runTest {
        advanceUntilIdle()

        // Root dir loaded initially, verify resolveMetadata was triggered for root_track.mp3
        assertEquals(1, fakeTrackMetadataRepository.resolveCalls.size)
        assertEquals("/root_track.mp3", fakeTrackMetadataRepository.resolveCalls.first().first().path)

        // UI initially has empty metadataMap, transient file name is displayed
        assertTrue(viewModel.uiState.value.metadataMap.isEmpty())

        // Metadata arrives asynchronously from background resolution / Room
        fakeTrackMetadataRepository.emitMetadata(
            listOf(
                TrackMetadata(
                    serverId = 1L,
                    remotePath = "/root_track.mp3",
                    title = "Rich Root Track Title",
                    artist = "Famous Artist",
                    album = "Debut Album",
                    trackNumber = 1,
                    durationMs = 210000L,
                    coverThumbnailPath = "/cache/covers/cover_1.jpg"
                )
            )
        )
        advanceUntilIdle()

        val updatedMap = viewModel.uiState.value.metadataMap
        assertEquals(1, updatedMap.size)
        val meta = updatedMap["/root_track.mp3"]
        assertNotNull(meta)
        assertEquals("Rich Root Track Title", meta?.title)
        assertEquals("Famous Artist", meta?.artist)
        assertEquals("Debut Album", meta?.album)
        assertEquals(210000L, meta?.durationMs)
        assertEquals("/cache/covers/cover_1.jpg", meta?.coverThumbnailPath)
    }

    @Test
    fun onAudioTrackClicked_wmaFile_delegatesToSessionWithAudioTracks() = runTest {
        advanceUntilIdle()

        val wmaFile = RemoteFile(name = "classic.wma", path = "/Music/classic.wma", size = 5000)
        val mp3File = RemoteFile(name = "song.mp3", path = "/Music/song.mp3", size = 4000)
        val dir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(wmaFile, mp3File))
        fakeDirectoryRepository.setResult("/Music/", ListDirectoryResult.Success(dir))

        viewModel.onDirectoryClicked(dir)
        advanceUntilIdle()

        assertTrue(wmaFile.isAudio)
        assertEquals(RemoteFileType.Audio(AudioFormat.WMA), wmaFile.fileType)

        viewModel.onAudioTrackClicked(wmaFile)
        advanceUntilIdle()

        assertEquals(dir, fakeMusicPlayerAppSession.lastPlayDirectory)
        assertEquals(wmaFile, fakeMusicPlayerAppSession.lastPlaySelectedFile)
    }

    private class FakeServerRepository : ServerRepository {
        private val serversFlow = MutableStateFlow<List<WebDavServer>>(emptyList())
        private val activeServerFlow = MutableStateFlow<WebDavServer?>(null)

        fun setActiveServerSync(server: WebDavServer?) {
            activeServerFlow.value = server
            if (server != null) {
                serversFlow.value = listOf(server)
            }
        }

        override fun getAllServers(): Flow<List<WebDavServer>> = serversFlow.asStateFlow()
        override fun getActiveServer(): Flow<WebDavServer?> = activeServerFlow.asStateFlow()
        override suspend fun getServerById(id: Long): WebDavServer? = serversFlow.value.firstOrNull { it.id == id }
        override suspend fun saveServer(server: WebDavServer): Long = 1L
        override suspend fun deleteServer(id: Long) {}
        override suspend fun setActiveServer(id: Long) {}
    }

    private class FakeDirectoryRepository : DirectoryRepository {
        private val results = mutableMapOf<String, ListDirectoryResult>()
        val forceRefreshCount = mutableMapOf<String, Int>()

        fun setResult(path: String, result: ListDirectoryResult) {
            results[path] = result
        }

        override suspend fun listDirectory(
            server: WebDavServer,
            path: String,
            forceRefresh: Boolean
        ): ListDirectoryResult {
            if (forceRefresh) {
                forceRefreshCount[path] = (forceRefreshCount[path] ?: 0) + 1
            }
            return results[path] ?: ListDirectoryResult.Failure("Not found")
        }

        override fun clearCache() {
            results.clear()
        }
    }

    private class FakeTrackMetadataRepository : TrackMetadataRepository {
        val metadataFlow = MutableStateFlow<List<TrackMetadata>>(emptyList())
        val resolveCalls = mutableListOf<List<RemoteFile>>()

        fun emitMetadata(list: List<TrackMetadata>) {
            metadataFlow.value = list
        }

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> = metadataFlow.asStateFlow()

        override fun getMetadataForPathsFlow(serverId: Long, remotePaths: List<String>): Flow<List<TrackMetadata>> =
            metadataFlow.map { list -> list.filter { it.remotePath in remotePaths } }

        override fun getMetadataFlow(serverId: Long, remotePath: String): Flow<TrackMetadata?> =
            metadataFlow.map { list -> list.firstOrNull { it.remotePath == remotePath } }

        override suspend fun getCachedMetadata(serverId: Long, remotePath: String): TrackMetadata? =
            metadataFlow.value.firstOrNull { it.remotePath == remotePath }

        override suspend fun resolveMetadata(server: WebDavServer, files: List<RemoteFile>) {
            resolveCalls.add(files)
        }

        override suspend fun resolveSingleTrackMetadata(server: WebDavServer, file: RemoteFile): TrackMetadata {
            return TrackMetadata(serverId = server.id, remotePath = file.path, title = file.name)
        }
    }
}
