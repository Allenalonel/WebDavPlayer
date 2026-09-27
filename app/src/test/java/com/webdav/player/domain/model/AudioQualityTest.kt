package com.webdav.player.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityTest {
    private val sampleServer =
        WebDavServer(
            id = 1L,
            name = "NAS",
            url = "https://nas.example.com",
            port = 5006,
            isDefault = true,
        )

    @Test
    fun audioQualityLevel_hasExpectedEnumerations() {
        val levels = AudioQualityLevel.entries
        assertTrue(levels.contains(AudioQualityLevel.LOSSLESS))
        assertTrue(levels.contains(AudioQualityLevel.HIGH_QUALITY))
        assertTrue(levels.contains(AudioQualityLevel.STANDARD))
        assertTrue(levels.contains(AudioQualityLevel.COMPRESSED))
    }

    @Test
    fun estimateBitrateKbps_fromFilenamePatterns() {
        assertEquals(320, estimateBitrateKbps("Artist - Song [320k].mp3", 0L, 0L))
        assertEquals(256, estimateBitrateKbps("Artist - Song (256kbps).mp3", 0L, 0L))
        assertEquals(128, estimateBitrateKbps("Track 128k.m4a", 0L, 0L))
        assertNull(estimateBitrateKbps("Artist - Song.mp3", 0L, 0L))
        assertNull(estimateBitrateKbps("Song [5k].mp3", 0L, 0L))
    }

    @Test
    fun estimateBitrateKbps_fromFileSizeAndDuration() {
        // 9.6 MB in 240 seconds = 320 kbps
        assertEquals(320, estimateBitrateKbps("song.mp3", 9_600_000L, 240_000L))
        // 7.68 MB in 240 seconds = 256 kbps
        assertEquals(256, estimateBitrateKbps("song.mp3", 7_680_000L, 240_000L))
        // 6.72 MB in 240 seconds = 224 kbps
        assertEquals(224, estimateBitrateKbps("song.mp3", 6_720_000L, 240_000L))
        // 5.76 MB in 240 seconds = 192 kbps
        assertEquals(192, estimateBitrateKbps("song.mp3", 5_760_000L, 240_000L))
        // 4.80 MB in 240 seconds = 160 kbps
        assertEquals(160, estimateBitrateKbps("song.mp3", 4_800_000L, 240_000L))
        // 3.84 MB in 240 seconds = 128 kbps
        assertEquals(128, estimateBitrateKbps("song.mp3", 3_840_000L, 240_000L))
        // 2.88 MB in 240 seconds = 96 kbps
        assertEquals(96, estimateBitrateKbps("song.mp3", 2_880_000L, 240_000L))
        // 1.92 MB in 240 seconds = 64 kbps
        assertEquals(64, estimateBitrateKbps("song.mp3", 1_920_000L, 240_000L))

        // Duration too short
        assertNull(estimateBitrateKbps("song.mp3", 9_600_000L, 500L))
        // File size zero
        assertNull(estimateBitrateKbps("song.mp3", 0L, 240_000L))
    }

    @Test
    fun flacTrack_evaluatesAsLossless() {
        val file =
            RemoteFile(
                name = "hotel_california.flac",
                path = "/Music/hotel_california.flac",
                size = 35_000_000L,
            )
        val metadata =
            TrackMetadata(
                serverId = 1L,
                remotePath = file.path,
                title = "Hotel California",
                durationMs = 390_000L,
            )

        val track = AudioTrack.fromRemoteFile(sampleServer, file, metadata)
        assertNotNull(track)
        assertEquals("FLAC", track!!.badge.label)
        assertEquals(AudioFormat.FLAC, track.badge.format)
        assertTrue(track.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, track.qualityLevel)
        assertNull(track.estimatedBitrateKbps)
        assertTrue(track.qualitySummary.contains("FLAC") && track.qualitySummary.contains("无损"))
        assertEquals(track.badge, track.resolveBadge())
        assertEquals(track.qualitySummary, track.formatQualitySummary())

        // RemoteFile extension methods
        val fileBadge = file.resolveBadge(metadata)
        assertNotNull(fileBadge)
        assertEquals(track.badge, fileBadge)
        assertEquals(track.qualitySummary, file.formatQualitySummary(metadata))
    }

    @Test
    fun wavTrack_evaluatesAsLossless() {
        val file =
            RemoteFile(
                name = "session_master.wav",
                path = "/Studio/session_master.wav",
                size = 60_000_000L,
            )
        val track =
            AudioTrack(
                id = "1:${file.path}",
                serverId = 1L,
                remotePath = file.path,
                title = "Session Master",
                durationMs = 300_000L,
                size = file.size,
                format = AudioFormat.WAV,
            )

        assertTrue(track.isLossless)
        assertEquals(AudioQualityLevel.LOSSLESS, track.qualityLevel)
        assertEquals("WAV", track.badge.label)
        assertTrue(track.qualitySummary.contains("WAV") && track.qualitySummary.contains("无损"))
    }

    @Test
    fun mp3Track_highQualityEvaluation() {
        val track =
            AudioTrack(
                id = "1:/Music/bohemian_rhapsody.mp3",
                serverId = 1L,
                remotePath = "/Music/bohemian_rhapsody.mp3",
                title = "Bohemian Rhapsody",
                durationMs = 240_000L,
                size = 9_600_000L,
                format = AudioFormat.MP3,
            )

        assertFalse(track.isLossless)
        assertEquals(AudioQualityLevel.HIGH_QUALITY, track.qualityLevel)
        assertEquals(320, track.estimatedBitrateKbps)
        assertEquals("MP3 320k", track.badge.label)
        assertTrue(track.qualitySummary.contains("MP3") && track.qualitySummary.contains("320"))
    }

    @Test
    fun mp3Track_standardAndCompressedTiers() {
        val standardTrack =
            AudioTrack(
                id = "1:/standard.mp3",
                serverId = 1L,
                remotePath = "/standard.mp3",
                title = "Standard",
                durationMs = 240_000L,
                size = 5_760_000L,
                format = AudioFormat.MP3,
            )
        assertEquals(AudioQualityLevel.STANDARD, standardTrack.qualityLevel)
        assertEquals(192, standardTrack.estimatedBitrateKbps)
        assertEquals("MP3 192k", standardTrack.badge.label)

        val compressedTrack =
            AudioTrack(
                id = "1:/compressed.mp3",
                serverId = 1L,
                remotePath = "/compressed.mp3",
                title = "Compressed",
                durationMs = 240_000L,
                size = 3_840_000L,
                format = AudioFormat.MP3,
            )
        assertEquals(AudioQualityLevel.COMPRESSED, compressedTrack.qualityLevel)
        assertEquals(128, compressedTrack.estimatedBitrateKbps)
        assertEquals("MP3 128k", compressedTrack.badge.label)
    }

    @Test
    fun mp3Track_withoutDurationOrBitrate() {
        val track =
            AudioTrack(
                id = "1:/unknown.mp3",
                serverId = 1L,
                remotePath = "/unknown.mp3",
                title = "Unknown",
                durationMs = 0L,
                size = 0L,
                format = AudioFormat.MP3,
            )
        assertEquals(AudioQualityLevel.STANDARD, track.qualityLevel)
        assertNull(track.estimatedBitrateKbps)
        assertEquals("MP3", track.badge.label)
    }

    @Test
    fun otherFormats_aacOggM4aWma() {
        val aacTrack =
            AudioTrack(
                id = "1:/song.aac",
                serverId = 1L,
                remotePath = "/song.aac",
                title = "AAC Song",
                durationMs = 240_000L,
                size = 7_680_000L,
                format = AudioFormat.AAC,
            )
        assertEquals(AudioQualityLevel.HIGH_QUALITY, aacTrack.qualityLevel)
        assertEquals("AAC 256k", aacTrack.badge.label)

        val oggTrack =
            AudioTrack(
                id = "1:/song.ogg",
                serverId = 1L,
                remotePath = "/song.ogg",
                title = "OGG Song",
                durationMs = 240_000L,
                size = 5_760_000L,
                format = AudioFormat.OGG,
            )
        assertEquals(AudioQualityLevel.STANDARD, oggTrack.qualityLevel)
        assertEquals("OGG 192k", oggTrack.badge.label)

        val m4aTrack =
            AudioTrack(
                id = "1:/song.m4a",
                serverId = 1L,
                remotePath = "/song.m4a",
                title = "M4A Song",
                durationMs = 240_000L,
                size = 7_680_000L,
                format = AudioFormat.M4A,
            )
        assertEquals(AudioQualityLevel.STANDARD, m4aTrack.qualityLevel)
        assertEquals("M4A 256k", m4aTrack.badge.label)

        val wmaTrack =
            AudioTrack(
                id = "1:/song.wma",
                serverId = 1L,
                remotePath = "/song.wma",
                title = "WMA Song",
                durationMs = 0L,
                size = 0L,
                format = AudioFormat.WMA,
            )
        assertEquals(AudioQualityLevel.COMPRESSED, wmaTrack.qualityLevel)
        assertEquals("WMA", wmaTrack.badge.label)
    }

    @Test
    fun remoteFile_lyricsAndOtherFiles() {
        val lrcFile = RemoteFile(name = "lyrics.lrc", path = "/lyrics.lrc", fileType = RemoteFileType.Lyrics)
        val lrcBadge = lrcFile.resolveBadge()
        assertNotNull(lrcBadge)
        assertEquals("LRC", lrcBadge!!.label)
        assertNull(lrcBadge.format)
        assertEquals("LRC 歌词文件", lrcFile.formatQualitySummary())

        val pdfFile = RemoteFile(name = "booklet.pdf", path = "/booklet.pdf", fileType = RemoteFileType.Other)
        val pdfBadge = pdfFile.resolveBadge()
        assertNotNull(pdfBadge)
        assertEquals("PDF", pdfBadge!!.label)
        assertEquals("PDF 文件", pdfFile.formatQualitySummary())

        val unknownFile = RemoteFile(name = "no_extension", path = "/no_extension", fileType = RemoteFileType.Other)
        assertNull(unknownFile.resolveBadge())
        assertEquals("未知文件", unknownFile.formatQualitySummary())
    }

    @Test
    fun audioTrack_withMetadata_recalculatesQuality() {
        val baseTrack =
            AudioTrack(
                id = "1:/song.mp3",
                serverId = 1L,
                remotePath = "/song.mp3",
                title = "Song",
                durationMs = 0L,
                size = 9_600_000L,
                format = AudioFormat.MP3,
            )
        assertEquals(AudioQualityLevel.STANDARD, baseTrack.qualityLevel)
        assertNull(baseTrack.estimatedBitrateKbps)

        val updatedTrack =
            baseTrack.withMetadata(
                TrackMetadata(
                    serverId = 1L,
                    remotePath = "/song.mp3",
                    durationMs = 240_000L,
                ),
            )
        assertEquals(AudioQualityLevel.HIGH_QUALITY, updatedTrack.qualityLevel)
        assertEquals(320, updatedTrack.estimatedBitrateKbps)
        assertEquals("MP3 320k", updatedTrack.badge.label)
    }
}
