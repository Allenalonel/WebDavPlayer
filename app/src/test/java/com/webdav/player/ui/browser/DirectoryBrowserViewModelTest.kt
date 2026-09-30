package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackState
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

    private val sampleServer =
        WebDavServer(
            id = 1L,
            name = "My NAS",
            url = "http://nas.local",
            port = 5005,
            isDefault = true,
        )

    private val rootDir =
        RemoteDirectory(
            path = "/",
            name = "根目录",
            subDirectories =
                listOf(
                    RemoteDirectory(path = "/Music/", name = "Music"),
                ),
            files =
                listOf(
                    RemoteFile(name = "root_track.mp3", path = "/root_track.mp3", size = 1000),
                ),
        )

    private val musicDir =
        RemoteDirectory(
            path = "/Music/",
            name = "Music",
            subDirectories =
                listOf(
                    RemoteDirectory(path = "/Music/Rock/", name = "Rock"),
                ),
            files =
                listOf(
                    RemoteFile(name = "song.flac", path = "/Music/song.flac", size = 2000),
                ),
        )

    private val rockDir =
        RemoteDirectory(
            path = "/Music/Rock/",
            name = "Rock",
            subDirectories = emptyList(),
            files =
                listOf(
                    RemoteFile(name = "queen.mp3", path = "/Music/Rock/queen.mp3", size = 3000),
                ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeServerRepository = FakeServerRepository()
        fakeDirectoryRepository = FakeDirectoryRepository()
        fakeMusicPlayerAppSession = FakeMusicPlayerAppSession()
        fakeTrackMetadataRepository = FakeTrackMetadataRepository()
        fakeServerRepository.setActiveServerSync(sampleServer)
        fakeMusicPlayerAppSession.setActiveServer(sampleServer)

        fakeDirectoryRepository.setResult("/", ListDirectoryResult.Success(rootDir))
        fakeDirectoryRepository.setResult("/Music/", ListDirectoryResult.Success(musicDir))
        fakeDirectoryRepository.setResult("/Music/Rock/", ListDirectoryResult.Success(rockDir))

        viewModel =
            DirectoryBrowserViewModel(
                serverRepository = fakeServerRepository,
                directoryRepository = fakeDirectoryRepository,
                musicPlayerAppSession = fakeMusicPlayerAppSession,
                trackMetadataRepository = fakeTrackMetadataRepository,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsRootDirectory_whenActiveServerExists() =
        runTest {
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
    fun onDirectoryClicked_navigatesIntoSubdirectory_andUpdatesBreadcrumbs() =
        runTest {
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
    fun onNavigateUp_returnsToParentDirectory() =
        runTest {
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
    fun onBreadcrumbClicked_navigatesDirectlyToAncestor() =
        runTest {
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
    fun onRefresh_callsRepositoryWithForceRefresh() =
        runTest {
            advanceUntilIdle()

            viewModel.onRefresh()
            advanceUntilIdle()

            assertEquals(1, fakeDirectoryRepository.forceRefreshCount["/"])
            val state = viewModel.uiState.value
            assertFalse(state.isRefreshing)
        }

    @Test
    fun directoryLoadFailure_showsErrorMessage_andCanRetry() =
        runTest {
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
    fun onAudioTrackClicked_dispatchesToMusicPlayerAppSession() =
        runTest {
            advanceUntilIdle()

            val audioFile = rootDir.files.first()
            viewModel.onAudioTrackClicked(audioFile)

            assertEquals(rootDir, fakeMusicPlayerAppSession.lastPlayDirectory)
            assertEquals(audioFile, fakeMusicPlayerAppSession.lastPlaySelectedFile)
        }

    @Test
    fun onAudioTrackClicked_propagatesCachedMetadataToMusicPlayerAppSession() =
        runTest {
            advanceUntilIdle()

            val audioFile = rootDir.files.first()
            val cachedMeta =
                TrackMetadata(
                    serverId = sampleServer.id,
                    remotePath = audioFile.path,
                    title = "Cached Title",
                    artist = "Cached Artist",
                    album = "Cached Album",
                    durationMs = 240000L,
                    coverThumbnailPath = "/covers/root_cover.jpg",
                )
            fakeTrackMetadataRepository.emitMetadata(listOf(cachedMeta))
            advanceUntilIdle()

            assertEquals(cachedMeta, viewModel.uiState.value.metadataMap[audioFile.path])

            viewModel.onAudioTrackClicked(audioFile)

            assertEquals(rootDir, fakeMusicPlayerAppSession.lastPlayDirectory)
            assertEquals(audioFile, fakeMusicPlayerAppSession.lastPlaySelectedFile)
            assertEquals(
                cachedMeta,
                fakeMusicPlayerAppSession.lastPlayInitialMetadata[audioFile.path],
            )
        }

    @Test
    fun onAudioTrackClicked_ignoresNonAudioFiles() =
        runTest {
            advanceUntilIdle()

            val nonAudioFile = RemoteFile(name = "lyrics.lrc", path = "/lyrics.lrc", fileType = RemoteFileType.Lyrics)
            viewModel.onAudioTrackClicked(nonAudioFile)

            assertNull(fakeMusicPlayerAppSession.lastPlayDirectory)
            assertNull(fakeMusicPlayerAppSession.lastPlaySelectedFile)
        }

    @Test
    fun playNext_dispatchesToMusicPlayerAppSession_asAudioTrack() =
        runTest {
            advanceUntilIdle()

            val audioFile = rootDir.files.first() // root_track.mp3
            viewModel.playNext(audioFile)

            val inserted = fakeMusicPlayerAppSession.lastPlayNextTrack
            assertNotNull(inserted)
            assertEquals("/root_track.mp3", inserted?.remotePath)
            assertEquals(AudioFormat.MP3, inserted?.format)
        }

    @Test
    fun playNext_ignoresNonAudioFiles() =
        runTest {
            advanceUntilIdle()

            val nonAudioFile = RemoteFile(name = "lyrics.lrc", path = "/lyrics.lrc", fileType = RemoteFileType.Lyrics)
            viewModel.playNext(nonAudioFile)

            assertNull(fakeMusicPlayerAppSession.lastPlayNextTrack)
        }

    @Test
    fun loadDirectory_subscribesToObserveDirectory_andEmitsCachedThenRemoteUpdate() =
        runTest {
            advanceUntilIdle()

            val cachedDir =
                RemoteDirectory(
                    path = "/StreamTest/",
                    name = "StreamTest",
                    files = listOf(RemoteFile(name = "track1.mp3", path = "/StreamTest/track1.mp3")),
                )
            val remoteUpdatedDir =
                RemoteDirectory(
                    path = "/StreamTest/",
                    name = "StreamTest",
                    files =
                        listOf(
                            RemoteFile(name = "track1.mp3", path = "/StreamTest/track1.mp3"),
                            RemoteFile(name = "track2.mp3", path = "/StreamTest/track2.mp3"),
                        ),
                )

            fakeDirectoryRepository.setCached("/StreamTest/", cachedDir)
            fakeDirectoryRepository.setResult("/StreamTest/", ListDirectoryResult.Success(remoteUpdatedDir))

            viewModel.onDirectoryClicked(RemoteDirectory(path = "/StreamTest/", name = "StreamTest"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("/StreamTest/", state.currentPath)
            assertEquals(remoteUpdatedDir, state.currentDirectory)
            assertEquals(2, state.currentDirectory?.files?.size)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)

            // Verify observeDirectory was called
            val lastObserve = fakeDirectoryRepository.observeCalls.lastOrNull()
            assertNotNull(lastObserve)
            assertEquals("/StreamTest/", lastObserve?.second)
            assertEquals(false, lastObserve?.third)
        }

    @Test
    fun directoryLoad_triggersAsynchronousMetadataResolution_andUpdatesUiStateIncrementally() =
        runTest {
            advanceUntilIdle()

            // Root dir loaded initially, verify resolveMetadata was triggered for root_track.mp3
            assertEquals(1, fakeTrackMetadataRepository.resolveCalls.size)
            assertEquals(
                "/root_track.mp3",
                fakeTrackMetadataRepository.resolveCalls
                    .first()
                    .first()
                    .path,
            )

            // UI initially has empty metadataMap, transient file name is displayed
            assertTrue(
                viewModel.uiState.value.metadataMap
                    .isEmpty(),
            )

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
                        coverThumbnailPath = "/cache/covers/cover_1.jpg",
                    ),
                ),
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
    fun onAudioTrackClicked_doesNotTriggerSecondMetadataResolution() =
        runTest {
            advanceUntilIdle()

            // Root dir loaded initially -> resolveMetadata called once
            assertEquals(1, fakeTrackMetadataRepository.resolveCalls.size)

            // User clicks audio track to play
            val audioFile = rootDir.files.first()
            viewModel.onAudioTrackClicked(audioFile)
            advanceUntilIdle()

            // Still exactly 1 call from directory loading, no duplicate dispatch from playback
            assertEquals(1, fakeTrackMetadataRepository.resolveCalls.size)
            assertEquals(rootDir, fakeMusicPlayerAppSession.lastPlayDirectory)
            assertEquals(audioFile, fakeMusicPlayerAppSession.lastPlaySelectedFile)
        }

    @Test
    fun activeServerChange_inSession_updatesDirectoryBrowserState_unidirectionally() =
        runTest {
            advanceUntilIdle()
            assertEquals(sampleServer, viewModel.uiState.value.activeServer)

            val server2 = WebDavServer(id = 2L, name = "Server 2", url = "http://server2.local", port = 80)
            val server2RootDir = RemoteDirectory(path = "/", name = "Server 2 Root")
            fakeDirectoryRepository.setResult("/", ListDirectoryResult.Success(server2RootDir))

            // Active server changes in the session
            fakeMusicPlayerAppSession.setActiveServer(server2)
            advanceUntilIdle()

            // ViewModel reflects the change unidirectionally from the session
            assertEquals(server2, viewModel.uiState.value.activeServer)
            assertEquals("/", viewModel.uiState.value.currentPath)
            assertEquals(server2RootDir, viewModel.uiState.value.currentDirectory)
        }

    @Test
    fun onAudioTrackClicked_wmaFile_delegatesToSessionWithAudioTracks() =
        runTest {
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

    @Test
    fun init_restoresSavedDirectoryPathFromAppSession() =
        runTest(testDispatcher) {
            val jazzDir =
                RemoteDirectory(
                    path = "/Music/Jazz/",
                    name = "Jazz",
                    files = listOf(RemoteFile(name = "miles.mp3", path = "/Music/Jazz/miles.mp3")),
                )
            fakeDirectoryRepository.setResult("/Music/Jazz/", ListDirectoryResult.Success(jazzDir))

            val sessionWithDir =
                FakeMusicPlayerAppSession(
                    com.webdav.player.domain.model.PlayerSessionState(
                        activeServer = sampleServer,
                        currentDirectoryPath = "/Music/Jazz/",
                    ),
                )
            val vm =
                DirectoryBrowserViewModel(
                    serverRepository = fakeServerRepository,
                    directoryRepository = fakeDirectoryRepository,
                    musicPlayerAppSession = sessionWithDir,
                    trackMetadataRepository = fakeTrackMetadataRepository,
                )
            advanceUntilIdle()

            assertEquals("/Music/Jazz/", vm.uiState.value.currentPath)
            assertEquals(jazzDir, vm.uiState.value.currentDirectory)
            assertTrue(vm.uiState.value.canNavigateUp)
        }

    @Test
    fun init_whenRestoredDirectoryPathFails_gracefullyFallsBackToRoot() =
        runTest(testDispatcher) {
            fakeDirectoryRepository.setResult(
                "/DeletedFolder/",
                ListDirectoryResult.Failure("404 Not Found"),
            )
            fakeDirectoryRepository.setResult("/", ListDirectoryResult.Success(rootDir))

            val sessionWithDir =
                FakeMusicPlayerAppSession(
                    com.webdav.player.domain.model.PlayerSessionState(
                        activeServer = sampleServer,
                        currentDirectoryPath = "/DeletedFolder/",
                    ),
                )
            val vm =
                DirectoryBrowserViewModel(
                    serverRepository = fakeServerRepository,
                    directoryRepository = fakeDirectoryRepository,
                    musicPlayerAppSession = sessionWithDir,
                    trackMetadataRepository = fakeTrackMetadataRepository,
                )
            advanceUntilIdle()

            // Should gracefully fall back to root "/"
            assertEquals("/", vm.uiState.value.currentPath)
            assertEquals(rootDir, vm.uiState.value.currentDirectory)
        }

    @Test
    fun tabSwitching_preservesDirectoryBrowsingDepthWithoutReloading() =
        runTest {
            advanceUntilIdle()

            // User browses deep into /Music/Rock/
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/", name = "Music"))
            advanceUntilIdle()
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/Rock/", name = "Rock"))
            advanceUntilIdle()

            assertEquals("/Music/Rock/", viewModel.uiState.value.currentPath)
            assertEquals(3, viewModel.uiState.value.breadcrumbs.size)
            val initialCalls = fakeDirectoryRepository.listCalls

            // User switches tab to Server List and returns to Directory Browser
            val coordinator =
                com.webdav.player.ui.navigation
                    .MainNavigationCoordinator()
            coordinator.selectDestination(com.webdav.player.ui.navigation.AppDestination.SERVER_LIST)
            coordinator.selectDestination(com.webdav.player.ui.navigation.AppDestination.DIRECTORY_BROWSER)

            // ViewModel state is strictly preserved without reloading
            assertEquals("/Music/Rock/", viewModel.uiState.value.currentPath)
            assertEquals(3, viewModel.uiState.value.breadcrumbs.size)
            assertEquals(
                "Rock",
                viewModel.uiState.value.currentDirectory
                    ?.name,
            )
            assertEquals(initialCalls, fakeDirectoryRepository.listCalls)
        }

    @Test
    fun switchingActiveServer_resetsDirectoryToRoot() =
        runTest {
            advanceUntilIdle()

            // User browses deep into /Music/Rock/ on server 1
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/", name = "Music"))
            advanceUntilIdle()
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/Rock/", name = "Rock"))
            advanceUntilIdle()
            assertEquals("/Music/Rock/", viewModel.uiState.value.currentPath)

            // Switch to a new server 2
            val server2 = WebDavServer(id = 2L, name = "Server 2", url = "http://server2.local", port = 80, isDefault = true)
            val server2RootDir =
                RemoteDirectory(
                    path = "/",
                    name = "Server 2 Root",
                    subDirectories = emptyList(),
                    files = emptyList(),
                )
            fakeDirectoryRepository.setResult("/", ListDirectoryResult.Success(server2RootDir))

            fakeMusicPlayerAppSession.setActiveServer(server2)
            fakeServerRepository.setActiveServerSync(server2)
            advanceUntilIdle()

            assertEquals(server2, viewModel.uiState.value.activeServer)
            assertEquals("/", viewModel.uiState.value.currentPath)
            assertEquals(1, viewModel.uiState.value.breadcrumbs.size)
            assertEquals(server2RootDir, viewModel.uiState.value.currentDirectory)
            assertFalse(viewModel.uiState.value.canNavigateUp)
        }

    @Test
    fun resetToRoot_navigatesDirectlyToRootDirectory() =
        runTest {
            advanceUntilIdle()

            // User browses deep into /Music/Rock/
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/", name = "Music"))
            advanceUntilIdle()
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/Rock/", name = "Rock"))
            advanceUntilIdle()
            assertEquals("/Music/Rock/", viewModel.uiState.value.currentPath)

            viewModel.resetToRoot()
            advanceUntilIdle()

            assertEquals("/", viewModel.uiState.value.currentPath)
            assertEquals(1, viewModel.uiState.value.breadcrumbs.size)
            assertEquals(rootDir, viewModel.uiState.value.currentDirectory)
            assertFalse(viewModel.uiState.value.canNavigateUp)
            assertEquals("/", fakeMusicPlayerAppSession.sessionState.value.currentDirectoryPath)
        }

    @Test
    fun swr_instantCacheHit_rendersImmediatelyAndUpdatesOnNetworkSuccess() =
        runTest {
            advanceUntilIdle()

            val cachedDir =
                RemoteDirectory(
                    path = "/CachedDir/",
                    name = "CachedDir",
                    files = listOf(RemoteFile(name = "old.mp3", path = "/CachedDir/old.mp3")),
                )
            val freshDir =
                RemoteDirectory(
                    path = "/CachedDir/",
                    name = "CachedDir",
                    files =
                        listOf(
                            RemoteFile(name = "old.mp3", path = "/CachedDir/old.mp3"),
                            RemoteFile(name = "new.mp3", path = "/CachedDir/new.mp3"),
                        ),
                )

            fakeDirectoryRepository.setCached("/CachedDir/", cachedDir)
            fakeDirectoryRepository.setResult("/CachedDir/", ListDirectoryResult.Success(freshDir))

            viewModel.onDirectoryClicked(RemoteDirectory(path = "/CachedDir/", name = "CachedDir"))

            // After initial coroutine execution, cache is loaded and network also completes
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("/CachedDir/", state.currentPath)
            assertEquals(2, state.currentDirectory?.files?.size)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
        }

    @Test
    fun swr_gracefulDegradation_preservesCacheWhenNetworkFails() =
        runTest {
            advanceUntilIdle()

            val cachedDir =
                RemoteDirectory(
                    path = "/OfflineDir/",
                    name = "OfflineDir",
                    files = listOf(RemoteFile(name = "local.mp3", path = "/OfflineDir/local.mp3")),
                )
            fakeDirectoryRepository.setCached("/OfflineDir/", cachedDir)
            fakeDirectoryRepository.setResult("/OfflineDir/", ListDirectoryResult.Failure("Network timeout", 408))

            viewModel.onDirectoryClicked(RemoteDirectory(path = "/OfflineDir/", name = "OfflineDir"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            // Cached directory is preserved despite network failure
            assertEquals("/OfflineDir/", state.currentPath)
            assertEquals(cachedDir, state.currentDirectory)
            assertEquals(1, state.currentDirectory?.files?.size)
            assertFalse(state.isLoading)
            // Does not disrupt screen with full page error
            assertNull(state.errorMessage)
        }

    @Test
    fun multiServer_independentDirectoryMemory_switchesSeamlessly() =
        runTest {
            advanceUntilIdle()

            val server1 = sampleServer
            val server2 = WebDavServer(id = 2L, name = "Server 2", url = "http://server2.local", port = 80)

            val server2RootDir = RemoteDirectory(path = "/", name = "Server 2 Root")
            val server2PopDir = RemoteDirectory(path = "/Pop/", name = "Pop")
            fakeDirectoryRepository.setResult("/", ListDirectoryResult.Success(server2RootDir))
            fakeDirectoryRepository.setResult("/Pop/", ListDirectoryResult.Success(server2PopDir))

            // 1. On server 1, navigate to /Music/Rock/
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/", name = "Music"))
            advanceUntilIdle()
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Music/Rock/", name = "Rock"))
            advanceUntilIdle()
            assertEquals("/Music/Rock/", viewModel.uiState.value.currentPath)

            // 2. Switch to server 2 -> enters server 2 root "/"
            fakeServerRepository.setActiveServerSync(server2)
            fakeMusicPlayerAppSession.setActiveServer(server2)
            advanceUntilIdle()
            assertEquals(server2, viewModel.uiState.value.activeServer)
            assertEquals("/", viewModel.uiState.value.currentPath)

            // 3. On server 2, navigate to /Pop/
            viewModel.onDirectoryClicked(RemoteDirectory(path = "/Pop/", name = "Pop"))
            advanceUntilIdle()
            assertEquals("/Pop/", viewModel.uiState.value.currentPath)

            // 4. Switch back to server 1 -> should remember /Music/Rock/!
            fakeServerRepository.setActiveServerSync(server1)
            fakeMusicPlayerAppSession.setActiveServer(server1)
            advanceUntilIdle()
            assertEquals(server1, viewModel.uiState.value.activeServer)
            assertEquals("/Music/Rock/", viewModel.uiState.value.currentPath)

            // 5. Switch back to server 2 -> should remember /Pop/!
            fakeServerRepository.setActiveServerSync(server2)
            fakeMusicPlayerAppSession.setActiveServer(server2)
            advanceUntilIdle()
            assertEquals(server2, viewModel.uiState.value.activeServer)
            assertEquals("/Pop/", viewModel.uiState.value.currentPath)
        }

    @Test
    fun sessionState_whenPlayingTrackOnActiveServer_updatesActiveTrackPathAndIsPlaying() =
        runTest {
            advanceUntilIdle()

            val playingTrack = AudioTrack(
                id = "1:/root_track.mp3",
                serverId = sampleServer.id,
                remotePath = "/root_track.mp3",
                title = "Root Track",
                format = AudioFormat.MP3,
            )

            fakeMusicPlayerAppSession._sessionState.value = fakeMusicPlayerAppSession._sessionState.value.copy(
                queue = PlaybackQueue(tracks = listOf(playingTrack), currentIndex = 0),
                playbackState = PlaybackState.Playing,
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("/root_track.mp3", state.activeTrackPath)
            assertTrue(state.isPlaying)
            assertTrue(state.isTrackActive("/root_track.mp3"))
            assertFalse(state.isTrackActive("/other.mp3"))
        }

    @Test
    fun sessionState_whenPaused_preservesActiveTrackPathWithIsPlayingFalse() =
        runTest {
            advanceUntilIdle()

            val activeTrack = AudioTrack(
                id = "1:/root_track.mp3",
                serverId = sampleServer.id,
                remotePath = "/root_track.mp3",
                title = "Root Track",
                format = AudioFormat.MP3,
            )

            // First playing
            fakeMusicPlayerAppSession._sessionState.value = fakeMusicPlayerAppSession._sessionState.value.copy(
                queue = PlaybackQueue(tracks = listOf(activeTrack), currentIndex = 0),
                playbackState = PlaybackState.Playing,
            )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isPlaying)

            // Then paused
            fakeMusicPlayerAppSession._sessionState.value = fakeMusicPlayerAppSession._sessionState.value.copy(
                playbackState = PlaybackState.Paused,
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("/root_track.mp3", state.activeTrackPath)
            assertFalse(state.isPlaying)
            assertTrue(state.isTrackActive("/root_track.mp3"))
        }

    @Test
    fun sessionState_whenIdleOrStopped_clearsActiveTrackPath() =
        runTest {
            advanceUntilIdle()

            val activeTrack = AudioTrack(
                id = "1:/root_track.mp3",
                serverId = sampleServer.id,
                remotePath = "/root_track.mp3",
                title = "Root Track",
                format = AudioFormat.MP3,
            )

            fakeMusicPlayerAppSession._sessionState.value = fakeMusicPlayerAppSession._sessionState.value.copy(
                queue = PlaybackQueue(tracks = listOf(activeTrack), currentIndex = 0),
                playbackState = PlaybackState.Playing,
            )
            advanceUntilIdle()
            assertEquals("/root_track.mp3", viewModel.uiState.value.activeTrackPath)

            // Track stopped / queue cleared
            fakeMusicPlayerAppSession._sessionState.value = fakeMusicPlayerAppSession._sessionState.value.copy(
                queue = PlaybackQueue.EMPTY,
                playbackState = PlaybackState.Idle,
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.activeTrackPath)
            assertFalse(state.isPlaying)
            assertFalse(state.isTrackActive("/root_track.mp3"))
        }

    @Test
    fun sessionState_whenTrackOnDifferentServer_doesNotSetActiveTrack() =
        runTest {
            advanceUntilIdle()

            // Active track belongs to server id 999, but browser is viewing sampleServer (id = 1)
            val otherServerTrack = AudioTrack(
                id = "999:/root_track.mp3",
                serverId = 999L,
                remotePath = "/root_track.mp3",
                title = "Root Track",
                format = AudioFormat.MP3,
            )

            fakeMusicPlayerAppSession._sessionState.value = fakeMusicPlayerAppSession._sessionState.value.copy(
                queue = PlaybackQueue(tracks = listOf(otherServerTrack), currentIndex = 0),
                playbackState = PlaybackState.Playing,
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.activeTrackPath)
            assertFalse(state.isPlaying)
        }

    @Test
    fun switchingServer_updatesActiveTrackMatchingNewServer() =
        runTest {
            advanceUntilIdle()

            val server2 = WebDavServer(id = 2L, name = "Server 2", url = "http://server2.local", port = 80)
            val server2RootDir = RemoteDirectory(path = "/", name = "Server 2 Root")
            fakeDirectoryRepository.setResult("/", ListDirectoryResult.Success(server2RootDir))

            val server1Track = AudioTrack(
                id = "1:/root_track.mp3",
                serverId = sampleServer.id,
                remotePath = "/root_track.mp3",
                title = "Server 1 Track",
                format = AudioFormat.MP3,
            )

            fakeMusicPlayerAppSession._sessionState.value = fakeMusicPlayerAppSession._sessionState.value.copy(
                queue = PlaybackQueue(tracks = listOf(server1Track), currentIndex = 0),
                playbackState = PlaybackState.Playing,
            )
            advanceUntilIdle()

            // On Server 1, track is active
            assertEquals("/root_track.mp3", viewModel.uiState.value.activeTrackPath)
            assertTrue(viewModel.uiState.value.isPlaying)

            // Switch to Server 2 -> track from Server 1 should no longer be active in browser
            fakeServerRepository.setActiveServerSync(server2)
            fakeMusicPlayerAppSession.setActiveServer(server2)
            advanceUntilIdle()

            assertEquals(server2, viewModel.uiState.value.activeServer)
            assertNull(viewModel.uiState.value.activeTrackPath)
            assertFalse(viewModel.uiState.value.isPlaying)

            // Switch back to Server 1 -> active track is recognized again
            fakeServerRepository.setActiveServerSync(sampleServer)
            fakeMusicPlayerAppSession.setActiveServer(sampleServer)
            advanceUntilIdle()

            assertEquals(sampleServer, viewModel.uiState.value.activeServer)
            assertEquals("/root_track.mp3", viewModel.uiState.value.activeTrackPath)
            assertTrue(viewModel.uiState.value.isPlaying)
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
        private val cachedDirectories = mutableMapOf<String, RemoteDirectory>()
        val forceRefreshCount = mutableMapOf<String, Int>()
        val observeCalls = mutableListOf<Triple<WebDavServer, String, Boolean>>()
        var listCalls = 0

        fun setResult(
            path: String,
            result: ListDirectoryResult,
        ) {
            results[path] = result
        }

        fun setCached(
            path: String,
            directory: RemoteDirectory,
        ) {
            cachedDirectories[path] = directory
        }

        override suspend fun getCachedDirectory(
            server: WebDavServer,
            path: String,
        ): RemoteDirectory? = cachedDirectories[path]

        override suspend fun listDirectory(
            server: WebDavServer,
            path: String,
            forceRefresh: Boolean,
        ): ListDirectoryResult {
            listCalls++
            if (forceRefresh) {
                forceRefreshCount[path] = (forceRefreshCount[path] ?: 0) + 1
            }
            return results[path] ?: ListDirectoryResult.Failure("Not found")
        }

        override suspend fun clearCache() {
            results.clear()
        }

        override fun observeDirectory(
            server: WebDavServer,
            path: String,
            forceRefresh: Boolean,
        ): Flow<ListDirectoryResult> =
            kotlinx.coroutines.flow.flow {
                observeCalls.add(Triple(server, path, forceRefresh))
                var cached: RemoteDirectory? = null
                if (!forceRefresh) {
                    cached = cachedDirectories[path]
                    if (cached != null) {
                        emit(ListDirectoryResult.Success(cached))
                    }
                }
                val result = listDirectory(server, path, forceRefresh)
                when (result) {
                    is ListDirectoryResult.Success -> {
                        if (cached == null || result.directory != cached) {
                            emit(result)
                        }
                    }

                    is ListDirectoryResult.Failure -> {
                        if (cached == null || forceRefresh) {
                            emit(result)
                        }
                    }
                }
            }
    }

    private class FakeTrackMetadataRepository : TrackMetadataRepository {
        val metadataFlow = MutableStateFlow<List<TrackMetadata>>(emptyList())
        val resolveCalls = mutableListOf<List<RemoteFile>>()

        fun emitMetadata(list: List<TrackMetadata>) {
            metadataFlow.value = list
        }

        override fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>> = metadataFlow.asStateFlow()

        override fun getMetadataForPathsFlow(
            serverId: Long,
            remotePaths: List<String>,
        ): Flow<List<TrackMetadata>> = metadataFlow.map { list -> list.filter { it.remotePath in remotePaths } }

        override fun getMetadataFlow(
            serverId: Long,
            remotePath: String,
        ): Flow<TrackMetadata?> = metadataFlow.map { list -> list.firstOrNull { it.remotePath == remotePath } }

        override suspend fun getCachedMetadata(
            serverId: Long,
            remotePath: String,
        ): TrackMetadata? = metadataFlow.value.firstOrNull { it.remotePath == remotePath }

        override suspend fun resolveMetadata(
            server: WebDavServer,
            files: List<RemoteFile>,
        ) {
            resolveCalls.add(files)
        }

        override suspend fun resolveSingleTrackMetadata(
            server: WebDavServer,
            file: RemoteFile,
        ): TrackMetadata = TrackMetadata(serverId = server.id, remotePath = file.path, title = file.name)
    }
}
