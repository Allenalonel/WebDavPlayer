package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryBrowserPresentationTest {

    private val sampleServer = WebDavServer(
        id = 1L,
        name = "Synology NAS",
        url = "https://nas.example.com",
        port = 5006,
        isDefault = true
    )

    @Test
    fun breadcrumbStrip_generatesHierarchyWithActiveTail() {
        val path = "/Music/Lossless/Pink Floyd/"
        val breadcrumbs = RemoteDirectory.buildBreadcrumbs(sampleServer, path)
        val helperBreadcrumbs = BreadcrumbNavigationHelper.buildBreadcrumbs(sampleServer, path)
        assertEquals(breadcrumbs, helperBreadcrumbs)

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
        assertTrue(BreadcrumbNavigationHelper.isAncestor(ancestorBreadcrumb.path, currentPath))
        assertEquals("/Music/", ancestorBreadcrumb.path)

        val rootBreadcrumb = Breadcrumb("Synology NAS", "/")
        assertTrue(RemoteDirectory.isAncestor(rootBreadcrumb.path, currentPath))
        assertTrue(BreadcrumbNavigationHelper.isAncestor(rootBreadcrumb.path, currentPath))
    }

    @Test
    fun breadcrumbStrip_tappingActiveTail_isNotAncestorOfSelf() {
        val currentPath = "/Music/Lossless/Pink Floyd/"
        val tailBreadcrumb = Breadcrumb("Pink Floyd", "/Music/Lossless/Pink Floyd/")

        assertFalse(RemoteDirectory.isAncestor(tailBreadcrumb.path, currentPath))
        assertFalse(BreadcrumbNavigationHelper.isAncestor(tailBreadcrumb.path, currentPath))
    }

    @Test
    fun audioQualityBadge_mapsLosslessFormatsCorrectly() {
        val flacFile = RemoteFile(name = "Time.flac", path = "/Music/Time.flac", size = 45_000_000L)
        val wavFile = RemoteFile(name = "Money.wav", path = "/Music/Money.wav", size = 60_000_000L)

        val flacBadge = AudioQualityBadgeHelper.getBadge(flacFile)
        assertNotNull(flacBadge)
        assertEquals("FLAC", flacBadge!!.label)
        assertTrue(flacBadge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, flacBadge.qualityLevel)

        val wavBadge = AudioQualityBadgeHelper.getBadge(wavFile)
        assertNotNull(wavBadge)
        assertEquals("WAV", wavBadge!!.label)
        assertTrue(wavBadge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, wavBadge.qualityLevel)
    }

    @Test
    fun audioQualityBadge_mapsMp3BitratesAccurately() {
        val mp3_320 = RemoteFile(name = "Comfortably Numb.mp3", path = "/Music/Comfortably Numb.mp3", size = 14_400_000L)
        val meta_320 = TrackMetadata(serverId = 1L, remotePath = mp3_320.path, durationMs = 360_000L) // 360s, 14.4MB = 320 kbps

        val badge320 = AudioQualityBadgeHelper.getBadge(mp3_320, meta_320)
        assertNotNull(badge320)
        assertEquals("MP3 320k", badge320!!.label)
        assertEquals(320, badge320.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.HIGH_QUALITY, badge320.qualityLevel)

        val mp3_192 = RemoteFile(name = "Echoes.mp3", path = "/Music/Echoes.mp3", size = 8_640_000L)
        val meta_192 = TrackMetadata(serverId = 1L, remotePath = mp3_192.path, durationMs = 360_000L) // 360s, 8.64MB = 192 kbps

        val badge192 = AudioQualityBadgeHelper.getBadge(mp3_192, meta_192)
        assertNotNull(badge192)
        assertEquals("MP3 192k", badge192!!.label)
        assertEquals(192, badge192.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.STANDARD, badge192.qualityLevel)

        val mp3_128 = RemoteFile(name = "Radio.mp3", path = "/Music/Radio.mp3", size = 5_760_000L)
        val meta_128 = TrackMetadata(serverId = 1L, remotePath = mp3_128.path, durationMs = 360_000L) // 360s, 5.76MB = 128 kbps

        val badge128 = AudioQualityBadgeHelper.getBadge(mp3_128, meta_128)
        assertNotNull(badge128)
        assertEquals("MP3 128k", badge128!!.label)
        assertEquals(128, badge128.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.COMPRESSED, badge128.qualityLevel)
    }

    @Test
    fun audioQualityBadge_filenameBitrateTagHasPrecedence() {
        val taggedFile = RemoteFile(
            name = "Eagles - Hotel California (320kbps).mp3",
            path = "/Music/Eagles - Hotel California (320kbps).mp3",
            size = 5_000_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(taggedFile, null)
        assertNotNull(badge)
        assertEquals("MP3 320k", badge!!.label)
        assertEquals(320, badge.estimatedBitrateKbps)
    }

    @Test
    fun folderItem_itemCountHints() {
        val audioDir = RemoteDirectory(
            path = "/Music/Rock/",
            name = "Rock",
            subDirectories = listOf(RemoteDirectory(path = "/Music/Rock/Queen/", name = "Queen")),
            files = listOf(
                RemoteFile(name = "song1.mp3", path = "/Music/Rock/song1.mp3"),
                RemoteFile(name = "song2.flac", path = "/Music/Rock/song2.flac")
            )
        )

        val emptyDir = RemoteDirectory(
            path = "/Music/Empty/",
            name = "Empty",
            subDirectories = emptyList(),
            files = emptyList()
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
        val state = DirectoryBrowserUiState(
            isInitializing = false,
            activeServer = sampleServer,
            currentPath = "/"
        )
        assertEquals("Synology NAS", state.headerTitle)
    }

    @Test
    fun headerTitle_rootWithoutActiveServer_returnsFallback() {
        val state = DirectoryBrowserUiState(
            isInitializing = false,
            activeServer = null,
            currentPath = "/"
        )
        assertEquals("远程目录", state.headerTitle)
    }

    @Test
    fun headerTitle_subDirectory_returnsDirectoryName() {
        val dir = RemoteDirectory(path = "/Music/Lossless/", name = "Lossless")
        val state = DirectoryBrowserUiState(
            isInitializing = false,
            activeServer = sampleServer,
            currentPath = "/Music/Lossless/",
            currentDirectory = dir
        )
        assertEquals("Lossless", state.headerTitle)
    }

    @Test
    fun headerTitle_subDirectoryWithoutCurrentDirectoryObject_derivesFromPath() {
        val state = DirectoryBrowserUiState(
            isInitializing = false,
            activeServer = sampleServer,
            currentPath = "/Music/Jazz/",
            currentDirectory = null
        )
        assertEquals("Jazz", state.headerTitle)
    }

    @Test
    fun activeTrack_uiState_evaluatesActiveTrackCorrectly() {
        val state = DirectoryBrowserUiState(
            isInitializing = false,
            activeServer = sampleServer,
            currentPath = "/Music/",
            activeTrackPath = "/Music/song.flac",
            isPlaying = true
        )
        assertTrue(state.isTrackActive("/Music/song.flac"))
        assertFalse(state.isTrackActive("/Music/other.mp3"))
        assertTrue(state.isPlaying)
    }

    @Test
    fun activeTrack_uiState_whenNoActiveTrack_returnsFalse() {
        val state = DirectoryBrowserUiState(
            isInitializing = false,
            activeServer = sampleServer,
            currentPath = "/Music/",
            activeTrackPath = null,
            isPlaying = false
        )
        assertFalse(state.isTrackActive("/Music/song.flac"))
        assertFalse(state.isPlaying)
    }
}

