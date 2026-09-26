package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.TrackMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityBadgeHelperTest {

    @Test
    fun flacTrack_returnsLosslessFlacBadge() {
        val file = RemoteFile(
            name = "hotel_california.flac",
            path = "/Music/hotel_california.flac",
            size = 35_000_000L
        )
        val metadata = TrackMetadata(
            serverId = 1L,
            remotePath = file.path,
            title = "Hotel California",
            durationMs = 390_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, metadata)
        assertNotNull(badge)
        assertEquals("FLAC", badge!!.label)
        assertEquals(AudioFormat.FLAC, badge.format)
        assertTrue(badge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, badge.qualityLevel)
    }

    @Test
    fun wavTrack_returnsLosslessWavBadge() {
        val file = RemoteFile(
            name = "master_tape.wav",
            path = "/Music/master_tape.wav",
            size = 50_000_000L
        )
        val metadata = TrackMetadata(
            serverId = 1L,
            remotePath = file.path,
            title = "Master Tape",
            durationMs = 300_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, metadata)
        assertNotNull(badge)
        assertEquals("WAV", badge!!.label)
        assertEquals(AudioFormat.WAV, badge.format)
        assertTrue(badge.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, badge.qualityLevel)
    }

    @Test
    fun mp3Track_withCalculated320kBitrate_returnsHighQualityBadge() {
        // 9.6 MB in 240 seconds = 320 kbps
        val file = RemoteFile(
            name = "song.mp3",
            path = "/Music/song.mp3",
            size = 9_600_000L
        )
        val metadata = TrackMetadata(
            serverId = 1L,
            remotePath = file.path,
            durationMs = 240_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, metadata)
        assertNotNull(badge)
        assertEquals("MP3 320k", badge!!.label)
        assertEquals(AudioFormat.MP3, badge.format)
        assertFalse(badge.isLossless)
        assertEquals(320, badge.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.HIGH_QUALITY, badge.qualityLevel)
    }

    @Test
    fun mp3Track_withExplicitFilenameBitrateTag_extractsBitrateDirectly() {
        val file = RemoteFile(
            name = "Queen - Bohemian Rhapsody [320k].mp3",
            path = "/Music/Queen - Bohemian Rhapsody [320k].mp3",
            size = 12_000_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, null)
        assertNotNull(badge)
        assertEquals("MP3 320k", badge!!.label)
        assertEquals(320, badge.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.HIGH_QUALITY, badge.qualityLevel)
    }

    @Test
    fun mp3Track_withCalculated192kBitrate_returnsStandardQualityBadge() {
        // 5.76 MB in 240 seconds = 192 kbps
        val file = RemoteFile(
            name = "standard_song.mp3",
            path = "/Music/standard_song.mp3",
            size = 5_760_000L
        )
        val metadata = TrackMetadata(
            serverId = 1L,
            remotePath = file.path,
            durationMs = 240_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, metadata)
        assertNotNull(badge)
        assertEquals("MP3 192k", badge!!.label)
        assertEquals(192, badge.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.STANDARD, badge.qualityLevel)
    }

    @Test
    fun mp3Track_withCalculated128kBitrate_returnsCompressedQualityBadge() {
        // 3.84 MB in 240 seconds = 128 kbps
        val file = RemoteFile(
            name = "radio_rip.mp3",
            path = "/Music/radio_rip.mp3",
            size = 3_840_000L
        )
        val metadata = TrackMetadata(
            serverId = 1L,
            remotePath = file.path,
            durationMs = 240_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, metadata)
        assertNotNull(badge)
        assertEquals("MP3 128k", badge!!.label)
        assertEquals(128, badge.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.COMPRESSED, badge.qualityLevel)
    }

    @Test
    fun mp3Track_withoutBitrateOrDuration_returnsBaseMp3Badge() {
        val file = RemoteFile(
            name = "mystery.mp3",
            path = "/Music/mystery.mp3",
            size = 0L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, null)
        assertNotNull(badge)
        assertEquals("MP3", badge!!.label)
        assertNull(badge.estimatedBitrateKbps)
        assertEquals(AudioQualityLevel.STANDARD, badge.qualityLevel)
    }

    @Test
    fun aacTrack_with256k_returnsHighQualityBadge() {
        val file = RemoteFile(
            name = "itunes_plus.aac",
            path = "/Music/itunes_plus.aac",
            size = 7_680_000L
        )
        val metadata = TrackMetadata(
            serverId = 1L,
            remotePath = file.path,
            durationMs = 240_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, metadata)
        assertNotNull(badge)
        assertEquals("AAC 256k", badge!!.label)
        assertEquals(AudioFormat.AAC, badge.format)
        assertEquals(AudioQualityLevel.HIGH_QUALITY, badge.qualityLevel)
    }

    @Test
    fun wmaTrack_returnsWmaBadge() {
        val file = RemoteFile(
            name = "classic.wma",
            path = "/Music/classic.wma",
            size = 3_000_000L
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, null)
        assertNotNull(badge)
        assertEquals("WMA", badge!!.label)
        assertEquals(AudioFormat.WMA, badge.format)
        assertEquals(AudioQualityLevel.COMPRESSED, badge.qualityLevel)
    }

    @Test
    fun lyricsFile_returnsLrcBadge() {
        val file = RemoteFile(
            name = "queen.lrc",
            path = "/Music/queen.lrc",
            fileType = RemoteFileType.Lyrics
        )

        val badge = AudioQualityBadgeHelper.getBadge(file, null)
        assertNotNull(badge)
        assertEquals("LRC", badge!!.label)
        assertNull(badge.format)
    }

    @Test
    fun formatQualitySummary_providesHumanReadableDescriptions() {
        val flacFile = RemoteFile(name = "song.flac", path = "/song.flac")
        val mp3File320 = RemoteFile(name = "song_320k.mp3", path = "/song_320k.mp3", size = 9_600_000L)
        val metadata320 = TrackMetadata(serverId = 1L, remotePath = "/song_320k.mp3", durationMs = 240_000L)

        val flacSummary = AudioQualityBadgeHelper.formatQualitySummary(flacFile, null)
        val mp3Summary = AudioQualityBadgeHelper.formatQualitySummary(mp3File320, metadata320)

        assertTrue(flacSummary.contains("FLAC") && flacSummary.contains("无损"))
        assertTrue(mp3Summary.contains("MP3") && mp3Summary.contains("320"))
    }
}
