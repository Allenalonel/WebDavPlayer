package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioQuality
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.LibrarySearchResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryBrowserPresentationTest {
    private val sampleServer =
        WebDavServer(
            id = 1L,
            name = "Synology NAS",
            url = "https://nas.example.com",
            port = 5006,
            isDefault = true,
        )

    @Test
    fun breadcrumbStrip_generatesHierarchyWithActiveTail() {
        val path = "/Music/Lossless/Pink Floyd/"
        val breadcrumbs = RemoteDirectory.buildBreadcrumbs(sampleServer, path)

        assertEquals(4, breadcrumbs.size)
        assertEquals(Breadcrumb("Synology NAS", "/"), breadcrumbs[0])
        assertEquals(Breadcrumb("Music", "/Music/"), breadcrumbs[1])
        assertEquals(Breadcrumb("Lossless", "/Music/Lossless/"), breadcrumbs[2])
        assertEquals(Breadcrumb("Pink Floyd", "/Music/Lossless/Pink Floyd/"), breadcrumbs[3])

        // Tail path matches current directory path
        assertEquals(path, breadcrumbs.last().path)
    }

    @Test
    fun breadcrumbStrip_tappingAncestorPath_resolvesExpectedAncestor() {
        val currentPath = "/Music/Lossless/Pink Floyd/"
        val ancestorBreadcrumb = Breadcrumb("Music", "/Music/")

        assertTrue(RemoteDirectory.isAncestor(ancestorBreadcrumb.path, currentPath))
        assertEquals("/Music/", ancestorBreadcrumb.path)

        val rootBreadcrumb = Breadcrumb("Synology NAS", "/")
        assertTrue(RemoteDirectory.isAncestor(rootBreadcrumb.path, currentPath))
    }

    @Test
    fun breadcrumbStrip_tappingActiveTail_isNotAncestorOfSelf() {
        val currentPath = "/Music/Lossless/Pink Floyd/"
        val tailBreadcrumb = Breadcrumb("Pink Floyd", "/Music/Lossless/Pink Floyd/")

        assertFalse(RemoteDirectory.isAncestor(tailBreadcrumb.path, currentPath))
    }

    @Test
    fun audioQualityBadge_mapsLosslessFormatsCorrectly() {
        val flacFile = RemoteFile(name = "Time.flac", path = "/Music/Time.flac", size = 45_000_000L)
        val wavFile = RemoteFile(name = "Money.wav", path = "/Music/Money.wav", size = 60_000_000L)
        val apeFile = RemoteFile(name = "UsAndThem.ape", path = "/Music/UsAndThem.ape", size = 40_000_000L)

        val flacBadge = AudioQuality.resolveBadge(flacFile)
        assertNotNull(flacBadge)
        assertEquals("FLAC", flacBadge!!.label)
        assertTrue(flacBadge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, flacBadge.qualityLevel)

        val wavBadge = AudioQuality.resolveBadge(wavFile)
        assertNotNull(wavBadge)
        assertEquals("WAV", wavBadge!!.label)
        assertTrue(wavBadge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, wavBadge.qualityLevel)

        val apeBadge = AudioQuality.resolveBadge(apeFile)
        assertNotNull(apeBadge)
        assertEquals("APE", apeBadge!!.label)
        assertTrue(apeBadge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, apeBadge.qualityLevel)
    }

    @Test
    fun audioQualityBadge_cueFile_mapsCueBadge() {
        val cueFile = RemoteFile(name = "DarkSide.cue", path = "/Music/DarkSide.cue", size = 1024L)
        assertTrue(cueFile.isCue)
        val badge = AudioQuality.resolveBadge(cueFile)
        assertNotNull(badge)
        assertEquals("CUE", badge!!.label)
    }

    @Test
    fun audioQualityBadge_mapsMp3BitratesAccurately() {
        val mp3_320 = RemoteFile(name = "Comfortably Numb.mp3", path = "/Music/Comfortably Numb.mp3", size = 14_400_000L)
        val meta_320 = TrackMetadata(serverId = 1L, remotePath = mp3_320.path, durationMs = 360_000L) // 360s, 14.4MB = 320 kbps

        val badge320 = AudioQuality.resolveBadge(mp3_320, meta_320)
        assertNotNull(badge320)
        assertEquals("MP3 320k", badge320!!.label)
        assertEquals(320, badge320.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.HIGH_QUALITY, badge320.qualityLevel)

        val mp3_192 = RemoteFile(name = "Echoes.mp3", path = "/Music/Echoes.mp3", size = 8_640_000L)
        val meta_192 = TrackMetadata(serverId = 1L, remotePath = mp3_192.path, durationMs = 360_000L) // 360s, 8.64MB = 192 kbps

        val badge192 = AudioQuality.resolveBadge(mp3_192, meta_192)
        assertNotNull(badge192)
        assertEquals("MP3 192k", badge192!!.label)
        assertEquals(192, badge192.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.STANDARD, badge192.qualityLevel)

        val mp3_128 = RemoteFile(name = "Radio.mp3", path = "/Music/Radio.mp3", size = 5_760_000L)
        val meta_128 = TrackMetadata(serverId = 1L, remotePath = mp3_128.path, durationMs = 360_000L) // 360s, 5.76MB = 128 kbps

        val badge128 = AudioQuality.resolveBadge(mp3_128, meta_128)
        assertNotNull(badge128)
        assertEquals("MP3 128k", badge128!!.label)
        assertEquals(128, badge128.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.COMPRESSED, badge128.qualityLevel)
    }

    @Test
    fun audioQualityBadge_filenameBitrateTagHasPrecedence() {
        val taggedFile =
            RemoteFile(
                name = "Eagles - Hotel California (320kbps).mp3",
                path = "/Music/Eagles - Hotel California (320kbps).mp3",
                size = 5_000_000L,
            )

        val badge = AudioQuality.resolveBadge(taggedFile, null)
        assertNotNull(badge)
        assertEquals("MP3 320k", badge!!.label)
        assertEquals(320, badge.estimatedBitrateKbps)
    }

    @Test
    fun folderItem_itemCountHints() {
        val audioDir =
            RemoteDirectory(
                path = "/Music/Rock/",
                name = "Rock",
                subDirectories = listOf(RemoteDirectory(path = "/Music/Rock/Queen/", name = "Queen")),
                files =
                    listOf(
                        RemoteFile(name = "song1.mp3", path = "/Music/Rock/song1.mp3"),
                        RemoteFile(name = "song2.flac", path = "/Music/Rock/song2.flac"),
                    ),
            )

        val emptyDir =
            RemoteDirectory(
                path = "/Music/Empty/",
                name = "Empty",
                subDirectories = emptyList(),
                files = emptyList(),
            )

        assertEquals(2, audioDir.audioFiles.size)
        assertTrue(emptyDir.isEmpty)
    }

    @Test
    fun headerTitle_initializing_returnsMediaLibrary() {
        val state = DirectoryBrowserUiState(isInitializing = true)
        assertEquals("媒体库", state.headerTitle)
    }

    @Test
    fun headerTitle_rootWithActiveServer_returnsServerName() {
        val state =
            DirectoryBrowserUiState(
                isInitializing = false,
                activeServer = sampleServer,
                currentPath = "/",
            )
        assertEquals("Synology NAS", state.headerTitle)
    }

    @Test
    fun headerTitle_rootWithoutActiveServer_returnsFallback() {
        val state =
            DirectoryBrowserUiState(
                isInitializing = false,
                activeServer = null,
                currentPath = "/",
            )
        assertEquals("远程目录", state.headerTitle)
    }

    @Test
    fun headerTitle_subDirectory_returnsDirectoryName() {
        val dir = RemoteDirectory(path = "/Music/Lossless/", name = "Lossless")
        val state =
            DirectoryBrowserUiState(
                isInitializing = false,
                activeServer = sampleServer,
                currentPath = "/Music/Lossless/",
                currentDirectory = dir,
            )
        assertEquals("Lossless", state.headerTitle)
    }

    @Test
    fun headerTitle_subDirectoryWithoutCurrentDirectoryObject_derivesFromPath() {
        val state =
            DirectoryBrowserUiState(
                isInitializing = false,
                activeServer = sampleServer,
                currentPath = "/Music/Jazz/",
                currentDirectory = null,
            )
        assertEquals("Jazz", state.headerTitle)
    }

    @Test
    fun activeTrack_uiState_evaluatesActiveTrackCorrectly() {
        val state =
            DirectoryBrowserUiState(
                isInitializing = false,
                activeServer = sampleServer,
                currentPath = "/Music/",
                activeTrackPath = "/Music/song.flac",
                isPlaying = true,
            )
        assertTrue(state.isTrackActive("/Music/song.flac"))
        assertFalse(state.isTrackActive("/Music/other.mp3"))
        assertTrue(state.isPlaying)
    }

    @Test
    fun activeTrack_uiState_whenNoActiveTrack_returnsFalse() {
        val state =
            DirectoryBrowserUiState(
                isInitializing = false,
                activeServer = sampleServer,
                currentPath = "/Music/",
                activeTrackPath = null,
                isPlaying = false,
            )
        assertFalse(state.isTrackActive("/Music/song.flac"))
        assertFalse(state.isPlaying)
    }

    @Test
    fun cueAlbumItem_presentationState_and_activeTrackEvaluation() {
        val audioFile = RemoteFile(name = "Abbey_Road.flac", path = "/Music/Abbey_Road.flac", size = 300_000_000L)
        val cueFile = RemoteFile(name = "Abbey_Road.cue", path = "/Music/Abbey_Road.cue", size = 2048L)
        val track1 =
            VirtualTrack(trackNumber = 1, title = "Come Together", startTimeMs = 0L, endTimeMs = 260000L, parentAudioPath = audioFile.path)
        val track2 =
            VirtualTrack(trackNumber = 2, title = "Something", startTimeMs = 260000L, endTimeMs = 440000L, parentAudioPath = audioFile.path)

        val album =
            CueAlbumItem(
                cueFile = cueFile,
                audioFile = audioFile,
                tracks = listOf(track1, track2),
            )

        assertEquals("Abbey_Road", album.albumTitle)
        assertEquals(2, album.totalTracksCount)

        val state =
            DirectoryBrowserUiState(
                isInitializing = false,
                activeServer = sampleServer,
                currentPath = "/Music/",
                activeTrackPath = audioFile.path,
                activeTrackId = "1:${audioFile.path}#cue_2",
                isPlaying = true,
                cueAlbums = listOf(album),
            )

        assertTrue(state.isCueAlbumActive(album))
        assertFalse(state.isVirtualTrackActive(album, track1))
        assertTrue(state.isVirtualTrackActive(album, track2))
    }

    @Test
    fun searchResult_presentationModel_calculatesTotalAndEmptyCorrectly() {
        val emptyResult = LibrarySearchResult()
        assertTrue(emptyResult.isEmpty)
        assertEquals(0, emptyResult.totalMatchesCount)

        val populatedResult =
            LibrarySearchResult(
                matchedDirectories = listOf(RemoteDirectory(path = "/Music/Pop/", name = "Pop")),
                matchedTracks =
                    listOf(
                        AudioTrack(id = "1:/s1.mp3", serverId = 1L, remotePath = "/s1.mp3", title = "S1", format = AudioFormat.MP3),
                        AudioTrack(id = "1:/s2.mp3", serverId = 1L, remotePath = "/s2.mp3", title = "S2", format = AudioFormat.MP3),
                    ),
            )
        assertFalse(populatedResult.isEmpty)
        assertEquals(3, populatedResult.totalMatchesCount)
        assertEquals(1, populatedResult.matchedDirectories.size)
        assertEquals(2, populatedResult.matchedTracks.size)
    }

    @Test
    fun searchUiState_tracksQueryAndSearchActiveFlags() {
        val state =
            DirectoryBrowserUiState(
                isSearchActive = true,
                searchQuery = "beethoven",
                isSearching = true,
                searchHistory = listOf("bach", "mozart"),
            )
        assertTrue(state.isSearchActive)
        assertEquals("beethoven", state.searchQuery)
        assertTrue(state.isSearching)
        assertEquals(listOf("bach", "mozart"), state.searchHistory)
    }

    @Test
    fun searchResultTrack_identifiesVirtualTrack_andResolvesDisplayProperties() {
        val standardTrack =
            AudioTrack(
                id = "1:/music/track.flac",
                serverId = 1L,
                remotePath = "/music/track.flac",
                title = "Whole Symphony",
                artist = "Beethoven",
                album = "Symphony No. 9",
                format = AudioFormat.FLAC,
            )
        assertFalse(standardTrack.isVirtualTrack)

        val virtualTrack =
            AudioTrack(
                id = "1:/music/track.flac#cue_1",
                serverId = 1L,
                remotePath = "/music/track.flac",
                title = "Allegro",
                artist = "Herbert von Karajan",
                album = "Symphony No. 9",
                format = AudioFormat.FLAC,
            )
        assertTrue(virtualTrack.isVirtualTrack)
        assertEquals("Allegro", virtualTrack.title)
        assertEquals("Herbert von Karajan", virtualTrack.artist)
    }

    @Test
    fun searchActiveState_togglesCapsuleMorphingState() {
        var state = DirectoryBrowserUiState(isSearchActive = false)
        assertFalse(state.isSearchActive)

        // User taps 38dp magnifying glass button: expands capsule
        state = state.copy(isSearchActive = true)
        assertTrue(state.isSearchActive)

        // User enters query
        state = state.copy(searchQuery = "Pink Floyd")
        assertEquals("Pink Floyd", state.searchQuery)

        // Tapping back arrow in search capsule shrinks it back and clears query
        state = state.copy(isSearchActive = false, searchQuery = "")
        assertFalse(state.isSearchActive)
        assertEquals("", state.searchQuery)
    }

    @Test
    fun searchResultDirectoryClick_resetsSearchActiveForFocalLanding() {
        val targetDirectory = RemoteDirectory(path = "/Music/Lossless/Pink Floyd/", name = "Pink Floyd")
        var state =
            DirectoryBrowserUiState(
                isSearchActive = true,
                searchQuery = "Pink",
            )
        assertTrue(state.isSearchActive)

        // Selecting a folder item dismisses search view and lands on target directory
        state =
            state.copy(
                isSearchActive = false,
                searchQuery = "",
                currentPath = targetDirectory.path,
            )
        assertFalse(state.isSearchActive)
        assertEquals("", state.searchQuery)
        assertEquals("/Music/Lossless/Pink Floyd/", state.currentPath)
    }
}
