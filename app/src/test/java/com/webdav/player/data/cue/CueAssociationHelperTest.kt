package com.webdav.player.data.cue

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CueAssociationHelperTest {
    private fun createAudioFile(
        name: String,
        path: String = "/$name",
        format: AudioFormat = AudioFormat.FLAC,
    ): RemoteFile =
        RemoteFile(
            name = name,
            path = path,
            size = 50_000_000L,
            fileType = RemoteFileType.Audio(format),
        )

    private fun createCueFile(
        name: String,
        path: String = "/$name",
    ): RemoteFile =
        RemoteFile(
            name = name,
            path = path,
            size = 1024L,
            fileType = RemoteFileType.Cue,
        )

    @Test
    fun exactBaseNameMatch_associatesCorrectAudioFile() {
        val cue = createCueFile("Hotel_California.cue")
        val audioFiles =
            listOf(
                createAudioFile("Hotel_California.flac"),
                createAudioFile("Bonus_Track.mp3"),
            )

        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue,
                audioFiles = audioFiles,
                totalCueFilesCount = 1,
            )

        assertNotNull(result)
        assertEquals("Hotel_California.flac", result?.name)
    }

    @Test
    fun exactBaseNameMatch_isCaseInsensitive() {
        val cue = createCueFile("ALBUM.CUE")
        val audioFiles =
            listOf(
                createAudioFile("album.flac"),
            )

        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue,
                audioFiles = audioFiles,
                totalCueFilesCount = 1,
            )

        assertNotNull(result)
        assertEquals("album.flac", result?.name)
    }

    @Test
    fun referencedFileExactMatch_matchesEvenWhenCueNameDiffers() {
        val cue = createCueFile("disc1.cue")
        val audioFiles =
            listOf(
                createAudioFile("The_Wall_CD1.flac"),
                createAudioFile("The_Wall_CD2.flac"),
            )

        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue,
                audioFiles = audioFiles,
                referencedFileNames = listOf("The_Wall_CD1.flac"),
                totalCueFilesCount = 2,
            )

        assertNotNull(result)
        assertEquals("The_Wall_CD1.flac", result?.name)
    }

    @Test
    fun referencedFile_handlesDifferentExtensionWhenTranscoded() {
        val cue = createCueFile("CDImage.cue")
        val audioFiles =
            listOf(
                createAudioFile("CDImage.flac"),
            )

        // CUE references WAV, but actual audio was ripped/transcoded to FLAC
        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue,
                audioFiles = audioFiles,
                referencedFileNames = listOf("CDImage.wav"),
                totalCueFilesCount = 1,
            )

        assertNotNull(result)
        assertEquals("CDImage.flac", result?.name)
    }

    @Test
    fun singlePairFallback_associatesWhenOnlyOneCueAndOneAudioFileExist() {
        val cue = createCueFile("playlist.cue")
        val audioFiles =
            listOf(
                createAudioFile("entire_concert.wav", format = AudioFormat.WAV),
            )

        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue,
                audioFiles = audioFiles,
                totalCueFilesCount = 1,
            )

        assertNotNull(result)
        assertEquals("entire_concert.wav", result?.name)
    }

    @Test
    fun multipleCuesAndAudios_matchedIndividuallyByBaseName() {
        val cue1 = createCueFile("CD1.cue")
        val cue2 = createCueFile("CD2.cue")
        val audioFiles =
            listOf(
                createAudioFile("CD1.flac"),
                createAudioFile("CD2.flac"),
            )

        val match1 =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue1,
                audioFiles = audioFiles,
                totalCueFilesCount = 2,
            )
        val match2 =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue2,
                audioFiles = audioFiles,
                totalCueFilesCount = 2,
            )

        assertEquals("CD1.flac", match1?.name)
        assertEquals("CD2.flac", match2?.name)
    }

    @Test
    fun apeAudioFormat_isCorrectlyMatched() {
        val cue = createCueFile("Beethoven_Sym9.cue")
        val audioFiles =
            listOf(
                createAudioFile("Beethoven_Sym9.ape", format = AudioFormat.APE),
            )

        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue,
                audioFiles = audioFiles,
                totalCueFilesCount = 1,
            )

        assertNotNull(result)
        assertEquals("Beethoven_Sym9.ape", result?.name)
        assertEquals(AudioFormat.APE, (result?.fileType as? RemoteFileType.Audio)?.format)
    }

    @Test
    fun noAudioFiles_returnsNull() {
        val cue = createCueFile("orphan.cue")
        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue,
                audioFiles = emptyList(),
                totalCueFilesCount = 1,
            )

        assertNull(result)
    }

    @Test
    fun ambiguousMultipleCuesWithoutNameMatch_returnsNull() {
        val cue1 = createCueFile("alpha.cue")
        val audioFiles =
            listOf(
                createAudioFile("unrelated_1.flac"),
                createAudioFile("unrelated_2.flac"),
            )

        val result =
            CueAssociationHelper.findMatchingAudioFile(
                cueFile = cue1,
                audioFiles = audioFiles,
                totalCueFilesCount = 2,
            )

        assertNull(result)
    }
}
