package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.ui.browser.components.resolveAudioSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioTrackItemRowTest {
    @Test
    fun resolveAudioSubtitle_withArtistAndAlbum_joinsCorrectly() {
        val file = RemoteFile(name = "01. Time.flac", path = "/Music/01. Time.flac")
        val metadata =
            TrackMetadata(
                serverId = 1L,
                remotePath = file.path,
                title = "Time",
                artist = "Pink Floyd",
                album = "The Dark Side of the Moon",
            )
        val subtitle = resolveAudioSubtitle(file, metadata)
        assertEquals("Pink Floyd · The Dark Side of the Moon", subtitle)
    }

    @Test
    fun resolveAudioSubtitle_withArtistOnly_returnsArtist() {
        val file = RemoteFile(name = "Money.mp3", path = "/Music/Money.mp3")
        val metadata =
            TrackMetadata(
                serverId = 1L,
                remotePath = file.path,
                artist = "Pink Floyd",
                album = null,
            )
        val subtitle = resolveAudioSubtitle(file, metadata)
        assertEquals("Pink Floyd", subtitle)
    }

    @Test
    fun resolveAudioSubtitle_withAlbumOnly_returnsAlbum() {
        val file = RemoteFile(name = "Track01.wav", path = "/Music/Track01.wav")
        val metadata =
            TrackMetadata(
                serverId = 1L,
                remotePath = file.path,
                artist = null,
                album = "Greatest Hits",
            )
        val subtitle = resolveAudioSubtitle(file, metadata)
        assertEquals("Greatest Hits", subtitle)
    }

    @Test
    fun resolveAudioSubtitle_withoutMetadata_returnsUnknown() {
        val file = RemoteFile(name = "Unknown.flac", path = "/Music/Unknown.flac", size = 15_728_640L)
        val subtitle = resolveAudioSubtitle(file, null)
        assertEquals("未知", subtitle)
    }

    @Test
    fun resolveAudioSubtitle_withoutMetadataOrSize_returnsUnknown() {
        val file =
            RemoteFile(
                name = "Unknown.flac",
                path = "/Music/Unknown.flac",
                size = 0L,
                lastModified = "2024-01-15 12:00",
            )
        val subtitle = resolveAudioSubtitle(file, null)
        assertEquals("未知", subtitle)
    }

    @Test
    fun resolveAudioSubtitle_completelyEmpty_returnsUnknown() {
        val file = RemoteFile(name = "Track.flac", path = "/Music/Track.flac", size = 0L)
        val subtitle = resolveAudioSubtitle(file, null)
        assertEquals("未知", subtitle)
    }

    @Test
    fun badgeResolution_integratesWithRemoteFile() {
        val flacFile = RemoteFile(name = "track.flac", path = "/Music/track.flac", size = 20_000_000L)
        val flacBadge = flacFile.resolveBadge(null)
        assertNotNull(flacBadge)
        assertEquals("FLAC", flacBadge!!.label)
        assertEquals(AudioQualityLevel.LOSSLESS, flacBadge.qualityLevel)

        val mp3File = RemoteFile(name = "track.mp3", path = "/Music/track.mp3", size = 7_200_000L)
        val metadata = TrackMetadata(serverId = 1L, remotePath = mp3File.path, durationMs = 180_000L)
        val mp3Badge = mp3File.resolveBadge(metadata)
        assertNotNull(mp3Badge)
        assertEquals("MP3 320k", mp3Badge!!.label)
        assertEquals(320, mp3Badge.estimatedBitrateKbps)
    }
}
