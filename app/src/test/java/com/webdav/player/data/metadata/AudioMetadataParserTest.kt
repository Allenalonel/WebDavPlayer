package com.webdav.player.data.metadata

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class AudioMetadataParserTest {
    @Test
    fun testParseId3v2Mp3Header_extractsAllTagsAndArtwork() {
        val stream = ByteArrayOutputStream()

        // ID3 Header
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0) // revision
        stream.write(0) // flags

        // Frame body stream
        val bodyStream = ByteArrayOutputStream()

        // TIT2 - Title
        writeId3Frame(bodyStream, "TIT2", "Bohemian Rhapsody")
        // TPE1 - Artist
        writeId3Frame(bodyStream, "TPE1", "Queen")
        // TALB - Album
        writeId3Frame(bodyStream, "TALB", "A Night at the Opera")
        // TRCK - Track Number
        writeId3Frame(bodyStream, "TRCK", "11/12")
        // TLEN - Duration in ms
        writeId3Frame(bodyStream, "TLEN", "354000")

        // APIC - Picture frame
        val apicBody = ByteArrayOutputStream()
        apicBody.write(0) // Latin1
        apicBody.write("image/jpeg\u0000".toByteArray(StandardCharsets.ISO_8859_1))
        apicBody.write(3) // Cover front
        apicBody.write("Cover\u0000".toByteArray(StandardCharsets.ISO_8859_1))
        val sampleJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x12, 0x34)
        apicBody.write(sampleJpeg)

        writeRawId3Frame(bodyStream, "APIC", apicBody.toByteArray())

        val bodyBytes = bodyStream.toByteArray()

        // Write synchsafe integer tag size
        val size = bodyBytes.size
        stream.write((size shr 21) and 0x7F)
        stream.write((size shr 14) and 0x7F)
        stream.write((size shr 7) and 0x7F)
        stream.write(size and 0x7F)

        stream.write(bodyBytes)

        val fullBytes = stream.toByteArray()
        val metadata = AudioMetadataParser.parse(fullBytes)

        assertEquals("Bohemian Rhapsody", metadata.title)
        assertEquals("Queen", metadata.artist)
        assertEquals("A Night at the Opera", metadata.album)
        assertEquals(11, metadata.trackNumber)
        assertEquals(354000L, metadata.durationMs)
        assertNotNull(metadata.artworkData)
        assertArrayEquals(sampleJpeg, metadata.artworkData)
        assertEquals("image/jpeg", metadata.artworkMimeType)
    }

    @Test
    fun testParseFlacHeader_extractsVorbisCommentsAndStreamInfoAndPicture() {
        val stream = ByteArrayOutputStream()
        stream.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

        // Block 0: STREAMINFO (isLast = false, type = 0, length = 34)
        stream.write(0x00) // isLast = 0, type = 0
        stream.write(0x00) // len MSB
        stream.write(0x00)
        stream.write(34) // len LSB (34 bytes)

        val streamInfo = ByteArray(34)
        // Set sample rate to 44100 (0x0AC44) and total samples to 44100 * 60 = 2,646,000 (0x285FA0)
        // sampleRate (20 bits): bits 10, 11, top 4 of 12
        streamInfo[10] = 0x0A.toByte()
        streamInfo[11] = 0xC4.toByte()
        streamInfo[12] = 0x40.toByte() // 4 in top nibble, rest 0
        // total samples (36 bits): bottom 4 of 13, 14, 15, 16, 17
        streamInfo[13] = 0x00.toByte()
        streamInfo[14] = 0x00.toByte()
        streamInfo[15] = 0x28.toByte()
        streamInfo[16] = 0x5F.toByte()
        streamInfo[17] = 0xF0.toByte()
        stream.write(streamInfo)

        // Block 4: VORBIS_COMMENT (isLast = false, type = 4)
        val vorbisStream = ByteArrayOutputStream()
        val vendor = "reference libFLAC".toByteArray(StandardCharsets.UTF_8)
        writeIntLe(vorbisStream, vendor.size)
        vorbisStream.write(vendor)

        val comments =
            listOf(
                "TITLE=Stairway to Heaven",
                "ARTIST=Led Zeppelin",
                "ALBUM=Led Zeppelin IV",
                "TRACKNUMBER=4",
            )
        writeIntLe(vorbisStream, comments.size)
        for (c in comments) {
            val bytes = c.toByteArray(StandardCharsets.UTF_8)
            writeIntLe(vorbisStream, bytes.size)
            vorbisStream.write(bytes)
        }

        val vorbisBytes = vorbisStream.toByteArray()
        stream.write(0x04) // type 4, isLast = 0
        write24BitBe(stream, vorbisBytes.size)
        stream.write(vorbisBytes)

        // Block 6: PICTURE (isLast = true -> 0x80 or 6 = 0x86)
        val picStream = ByteArrayOutputStream()
        writeIntBe(picStream, 3) // Picture type: Cover Front
        val mime = "image/png".toByteArray(StandardCharsets.US_ASCII)
        writeIntBe(picStream, mime.size)
        picStream.write(mime)
        writeIntBe(picStream, 0) // Description length 0
        writeIntBe(picStream, 500) // Width
        writeIntBe(picStream, 500) // Height
        writeIntBe(picStream, 24) // Depth
        writeIntBe(picStream, 0) // Colors
        val samplePng = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A)
        writeIntBe(picStream, samplePng.size)
        picStream.write(samplePng)

        val picBytes = picStream.toByteArray()
        stream.write(0x86) // type 6, isLast = 1
        write24BitBe(stream, picBytes.size)
        stream.write(picBytes)

        val metadata = AudioMetadataParser.parse(stream.toByteArray())

        assertEquals("Stairway to Heaven", metadata.title)
        assertEquals("Led Zeppelin", metadata.artist)
        assertEquals("Led Zeppelin IV", metadata.album)
        assertEquals(4, metadata.trackNumber)
        assertEquals(60000L, metadata.durationMs) // 60 seconds
        assertNotNull(metadata.artworkData)
        assertArrayEquals(samplePng, metadata.artworkData)
        assertEquals("image/png", metadata.artworkMimeType)
    }

    @Test
    fun testParseWavHeader_extractsFormatAndListInfo() {
        val stream = ByteArrayOutputStream()
        stream.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        writeIntLe(stream, 0) // Dummy size
        stream.write("WAVE".toByteArray(StandardCharsets.US_ASCII))

        // fmt chunk (16 bytes payload)
        stream.write("fmt ".toByteArray(StandardCharsets.US_ASCII))
        writeIntLe(stream, 16)
        writeShortLe(stream, 1) // PCM format
        writeShortLe(stream, 2) // Stereo
        writeIntLe(stream, 44100) // Sample rate
        writeIntLe(stream, 176400) // Byte rate (44100 * 2 channels * 2 bytes/sample)
        writeShortLe(stream, 4) // Block align
        writeShortLe(stream, 16) // Bits per sample

        // LIST INFO chunk
        val infoStream = ByteArrayOutputStream()
        infoStream.write("INFO".toByteArray(StandardCharsets.US_ASCII))

        writeWavInfoSubchunk(infoStream, "INAM", "Hotel California")
        writeWavInfoSubchunk(infoStream, "IART", "Eagles")
        writeWavInfoSubchunk(infoStream, "IPRD", "Hotel California Album")
        writeWavInfoSubchunk(infoStream, "ITRK", "1")

        val infoBytes = infoStream.toByteArray()
        stream.write("LIST".toByteArray(StandardCharsets.US_ASCII))
        writeIntLe(stream, infoBytes.size)
        stream.write(infoBytes)

        // data chunk: 176400 * 30 bytes = 30 seconds = 30000 ms
        stream.write("data".toByteArray(StandardCharsets.US_ASCII))
        writeIntLe(stream, 176400 * 30)

        val metadata = AudioMetadataParser.parse(stream.toByteArray())

        assertEquals("Hotel California", metadata.title)
        assertEquals("Eagles", metadata.artist)
        assertEquals("Hotel California Album", metadata.album)
        assertEquals(1, metadata.trackNumber)
        assertEquals(30000L, metadata.durationMs)
    }

    @Test
    fun testParseAsfWmaHeader_extractsTitleArtistAndDuration() {
        val stream = ByteArrayOutputStream()

        // ASF Header GUID (16 bytes)
        val asfHeaderGuid =
            byteArrayOf(
                0x30.toByte(),
                0x26.toByte(),
                0xB2.toByte(),
                0x75.toByte(),
                0x8E.toByte(),
                0x66.toByte(),
                0xCF.toByte(),
                0x11.toByte(),
                0xA6.toByte(),
                0xD9.toByte(),
                0x00.toByte(),
                0xAA.toByte(),
                0x00.toByte(),
                0x62.toByte(),
                0xCE.toByte(),
                0x6C.toByte(),
            )
        stream.write(asfHeaderGuid)
        val headerBodyStream = ByteArrayOutputStream()

        // Sub-object 1: File Properties
        val filePropsGuid =
            byteArrayOf(
                0xA1.toByte(),
                0xDC.toByte(),
                0xAB.toByte(),
                0x8C.toByte(),
                0x47.toByte(),
                0xA9.toByte(),
                0xCF.toByte(),
                0x11.toByte(),
                0x8E.toByte(),
                0xE4.toByte(),
                0x00.toByte(),
                0xC0.toByte(),
                0x0C.toByte(),
                0x20.toByte(),
                0x53.toByte(),
                0x65.toByte(),
            )
        headerBodyStream.write(filePropsGuid)
        val filePropsBody = ByteArrayOutputStream()
        filePropsBody.write(ByteArray(16)) // file id
        filePropsBody.write(ByteArray(8)) // file size
        filePropsBody.write(ByteArray(8)) // creation time
        filePropsBody.write(ByteArray(8)) // packets count
        // Play duration in 100ns units: 120,000 ms = 120,000 * 10,000 = 1,200,000,000L
        val duration100ns = 120000L * 10000L
        for (i in 0..7) {
            filePropsBody.write(((duration100ns shr (i * 8)) and 0xFF).toInt())
        }
        val filePropsBytes = filePropsBody.toByteArray()
        val totalFilePropsSize = (24 + filePropsBytes.size).toLong()
        for (i in 0..7) {
            headerBodyStream.write(((totalFilePropsSize shr (i * 8)) and 0xFF).toInt())
        }
        headerBodyStream.write(filePropsBytes)

        // Sub-object 2: Content Description
        val contentDescGuid =
            byteArrayOf(
                0x33.toByte(),
                0x26.toByte(),
                0xB2.toByte(),
                0x75.toByte(),
                0x8E.toByte(),
                0x66.toByte(),
                0xCF.toByte(),
                0x11.toByte(),
                0xA6.toByte(),
                0xD9.toByte(),
                0x00.toByte(),
                0xAA.toByte(),
                0x00.toByte(),
                0x62.toByte(),
                0xCE.toByte(),
                0x6C.toByte(),
            )
        headerBodyStream.write(contentDescGuid)
        val titleBytes = "Yesterday".toByteArray(StandardCharsets.UTF_16LE)
        val authorBytes = "The Beatles".toByteArray(StandardCharsets.UTF_16LE)

        val contentDescBody = ByteArrayOutputStream()
        writeShortLe(contentDescBody, titleBytes.size)
        writeShortLe(contentDescBody, authorBytes.size)
        writeShortLe(contentDescBody, 0)
        writeShortLe(contentDescBody, 0)
        writeShortLe(contentDescBody, 0)
        contentDescBody.write(titleBytes)
        contentDescBody.write(authorBytes)

        val contentDescBytes = contentDescBody.toByteArray()
        val totalContentDescSize = (24 + contentDescBytes.size).toLong()
        for (i in 0..7) {
            headerBodyStream.write(((totalContentDescSize shr (i * 8)) and 0xFF).toInt())
        }
        headerBodyStream.write(contentDescBytes)

        val headerBody = headerBodyStream.toByteArray()
        val totalHeaderSize = (30 + headerBody.size).toLong()
        for (i in 0..7) {
            stream.write(((totalHeaderSize shr (i * 8)) and 0xFF).toInt())
        }
        writeIntLe(stream, 2) // num sub-objects
        stream.write(1) // reserved 1
        stream.write(2) // reserved 2
        stream.write(headerBody)

        val metadata = AudioMetadataParser.parse(stream.toByteArray())

        assertEquals("Yesterday", metadata.title)
        assertEquals("The Beatles", metadata.artist)
        assertEquals(120000L, metadata.durationMs)
    }

    @Test
    fun testMalformedOrTruncatedByteStream_returnsCleanEmptyMetadataWithoutCrashing() {
        // Random truncated bytes
        val truncatedId3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00, 0x00)
        val result1 = AudioMetadataParser.parse(truncatedId3)
        assertFalse(result1.hasTags)

        // Corrupt FLAC
        val truncatedFlac = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x20)
        val result2 = AudioMetadataParser.parse(truncatedFlac)
        assertFalse(result2.hasTags)

        // Corrupt WAV
        val truncatedWav = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), 0x10)
        val result3 = AudioMetadataParser.parse(truncatedWav)
        assertFalse(result3.hasTags)

        // Empty bytes
        val emptyResult = AudioMetadataParser.parse(ByteArray(0))
        assertFalse(emptyResult.hasTags)
    }

    @Test
    fun testParseId3v2_extractsUsltLyrics() {
        val stream = ByteArrayOutputStream()
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0)
        stream.write(0)

        val bodyStream = ByteArrayOutputStream()
        writeId3Frame(bodyStream, "TIT2", "Song with lyrics")

        // USLT frame
        // Payload: 1 byte encoding (3 = UTF-8), 3 bytes language ("eng"), null-terminated desc ("\u0000"), text
        val usltBody = ByteArrayOutputStream()
        usltBody.write(3) // UTF-8
        usltBody.write("eng".toByteArray(StandardCharsets.US_ASCII))
        usltBody.write("\u0000".toByteArray(StandardCharsets.UTF_8)) // empty descriptor + null terminator
        val lyricText = "[00:01.00]Line 1\n[00:05.00]Line 2"
        usltBody.write(lyricText.toByteArray(StandardCharsets.UTF_8))

        writeRawId3Frame(bodyStream, "USLT", usltBody.toByteArray())

        val bodyBytes = bodyStream.toByteArray()
        val size = bodyBytes.size
        stream.write((size shr 21) and 0x7F)
        stream.write((size shr 14) and 0x7F)
        stream.write((size shr 7) and 0x7F)
        stream.write(size and 0x7F)
        stream.write(bodyBytes)

        val metadata = AudioMetadataParser.parse(stream.toByteArray())
        assertEquals("Song with lyrics", metadata.title)
        assertEquals(lyricText, metadata.lyrics)
    }

    @Test
    fun testParseFlac_extractsVorbisCommentLyrics() {
        val stream = ByteArrayOutputStream()
        stream.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

        // Block 4: VORBIS_COMMENT (isLast = true, type = 4)
        val vorbisStream = ByteArrayOutputStream()
        val vendor = "reference libFLAC".toByteArray(StandardCharsets.UTF_8)
        writeIntLe(vorbisStream, vendor.size)
        vorbisStream.write(vendor)

        val comments =
            listOf(
                "TITLE=Flac Song",
                "LYRICS=[00:10.00]Flac lyric line",
            )
        writeIntLe(vorbisStream, comments.size)
        for (c in comments) {
            val cb = c.toByteArray(StandardCharsets.UTF_8)
            writeIntLe(vorbisStream, cb.size)
            vorbisStream.write(cb)
        }

        val vorbisBytes = vorbisStream.toByteArray()
        stream.write(0x84) // isLast = 1, type = 4
        write24BitBe(stream, vorbisBytes.size)
        stream.write(vorbisBytes)

        val metadata = AudioMetadataParser.parse(stream.toByteArray())
        assertEquals("Flac Song", metadata.title)
        assertEquals("[00:10.00]Flac lyric line", metadata.lyrics)
    }

    @Test
    fun testParseAsf_extractsWmLyrics() {
        val stream = ByteArrayOutputStream()
        val asfHeaderGuid =
            byteArrayOf(
                0x30.toByte(),
                0x26.toByte(),
                0xB2.toByte(),
                0x75.toByte(),
                0x8E.toByte(),
                0x66.toByte(),
                0xCF.toByte(),
                0x11.toByte(),
                0xA6.toByte(),
                0xD9.toByte(),
                0x00.toByte(),
                0xAA.toByte(),
                0x00.toByte(),
                0x62.toByte(),
                0xCE.toByte(),
                0x6C.toByte(),
            )
        stream.write(asfHeaderGuid)
        val headerBodyStream = ByteArrayOutputStream()

        // Extended Content Description Guid
        val extDescGuid =
            byteArrayOf(
                0x40.toByte(),
                0xA4.toByte(),
                0xD0.toByte(),
                0xD2.toByte(),
                0x07.toByte(),
                0xE3.toByte(),
                0xD2.toByte(),
                0x11.toByte(),
                0x97.toByte(),
                0xF0.toByte(),
                0x00.toByte(),
                0xA0.toByte(),
                0xC9.toByte(),
                0x5E.toByte(),
                0xA8.toByte(),
                0x50.toByte(),
            )
        headerBodyStream.write(extDescGuid)

        val extDescBody = ByteArrayOutputStream()
        writeShortLe(extDescBody, 1) // 1 descriptor
        val nameBytes = "WM/Lyrics\u0000".toByteArray(StandardCharsets.UTF_16LE)
        writeShortLe(extDescBody, nameBytes.size)
        extDescBody.write(nameBytes)
        writeShortLe(extDescBody, 0) // valType = Unicode string
        val valBytes = "Asf embedded lyrics text".toByteArray(StandardCharsets.UTF_16LE)
        writeShortLe(extDescBody, valBytes.size)
        extDescBody.write(valBytes)

        val extBytes = extDescBody.toByteArray()
        val totalSize = (24 + extBytes.size).toLong()
        for (i in 0..7) {
            headerBodyStream.write(((totalSize shr (i * 8)) and 0xFF).toInt())
        }
        headerBodyStream.write(extBytes)

        val headerBody = headerBodyStream.toByteArray()
        val totalHeaderSize = (30 + headerBody.size).toLong()
        for (i in 0..7) {
            stream.write(((totalHeaderSize shr (i * 8)) and 0xFF).toInt())
        }
        writeIntLe(stream, 1) // 1 sub-object
        stream.write(0)
        stream.write(0)
        stream.write(headerBody)

        val metadata = AudioMetadataParser.parse(stream.toByteArray())
        assertEquals("Asf embedded lyrics text", metadata.lyrics)
    }

    @Test
    fun testDetectRequiredTagSize_flacWithLargePicture_calculatesAccurateTagSize() {
        val stream = ByteArrayOutputStream()
        stream.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

        // Block 0: STREAMINFO (type 0, length 34, isLast = false)
        stream.write(0x00)
        stream.write(0x00)
        stream.write(0x00)
        stream.write(34)
        stream.write(ByteArray(34))

        // Block 6: PICTURE (type 6, length 800,000, isLast = true)
        val picLength = 800000
        stream.write(0x86)
        stream.write((picLength shr 16) and 0xFF)
        stream.write((picLength shr 8) and 0xFF)
        stream.write(picLength and 0xFF)
        // Only write 100 bytes of body in initial buffer
        stream.write(ByteArray(100))

        val initialBuffer = stream.toByteArray()
        val detected = AudioMetadataParser.detectRequiredTagSize(initialBuffer)

        assertNotNull("Tag size for FLAC with large PICTURE block must be detected", detected)
        assertTrue(
            "Detected size ($detected) must be large enough to hold the 800,000-byte PICTURE block (expected >= 800042)",
            detected!! >= 800042L,
        )
    }

    @Test
    fun testDetectRequiredTagSize_id3v2WithLargeTag_calculatesAccurateTagSize() {
        val stream = ByteArrayOutputStream()
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0)
        stream.write(0)

        // Tag payload size: 750,000 bytes (synchsafe)
        val tagPayload = 750000
        stream.write((tagPayload shr 21) and 0x7F)
        stream.write((tagPayload shr 14) and 0x7F)
        stream.write((tagPayload shr 7) and 0x7F)
        stream.write(tagPayload and 0x7F)
        // Partial body in initial buffer
        stream.write(ByteArray(100))

        val initialBuffer = stream.toByteArray()
        val detected = AudioMetadataParser.detectRequiredTagSize(initialBuffer)

        assertNotNull(detected)
        assertEquals(750010L, detected)
    }

    @Test
    fun testHasRecognizedAudioHeader_offsetId3Header_returnsTrue() {
        val stream = ByteArrayOutputStream()
        // Prepend 128 bytes of padding/junk
        stream.write(ByteArray(128) { 0x55.toByte() })
        // ID3 header
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0)
        stream.write(0)
        // tag size (synchsafe)
        stream.write(0)
        stream.write(0)
        stream.write(1)
        stream.write(0)

        val bytes = stream.toByteArray()
        assertTrue(
            "hasRecognizedAudioHeader must return true for ID3 header located at non-zero offset within scan limit",
            AudioMetadataParser.hasRecognizedAudioHeader(bytes),
        )
    }

    @Test
    fun testParseId3_truncatedApicPayload_doesNotReturnTruncatedArtwork() {
        val stream = ByteArrayOutputStream()
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0)
        stream.write(0)

        // Tag payload size: 1000 bytes
        val tagPayload = 1000
        stream.write((tagPayload shr 21) and 0x7F)
        stream.write((tagPayload shr 14) and 0x7F)
        stream.write((tagPayload shr 7) and 0x7F)
        stream.write(tagPayload and 0x7F)

        // APIC frame header: says size is 500 bytes
        stream.write("APIC".toByteArray(StandardCharsets.US_ASCII))
        val frameSize = 500
        stream.write((frameSize shr 24) and 0xFF)
        stream.write((frameSize shr 16) and 0xFF)
        stream.write((frameSize shr 8) and 0xFF)
        stream.write(frameSize and 0xFF)
        stream.write(0) // flag 1
        stream.write(0) // flag 2

        // But we only provide 30 bytes of body before the buffer ends!
        val apicBody = ByteArrayOutputStream()
        apicBody.write(0)
        apicBody.write("image/jpeg\u0000".toByteArray(StandardCharsets.ISO_8859_1))
        apicBody.write(3)
        apicBody.write("cover\u0000".toByteArray(StandardCharsets.ISO_8859_1))
        apicBody.write(ByteArray(15) { 0x55.toByte() })
        stream.write(apicBody.toByteArray())

        val bytes = stream.toByteArray()
        val parsed = Id3v2Parser.parse(bytes)
        assertNotNull(parsed)
        assertNull("Truncated APIC frame must not return partial/truncated artwork", parsed?.artworkData)
    }

    @Test
    fun testParseFlac_truncatedPictureBlock_doesNotReturnTruncatedArtwork() {
        val stream = ByteArrayOutputStream()
        stream.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

        // Block 0: STREAMINFO (type 0, length 34, isLast = false)
        stream.write(0x00)
        stream.write(0x00)
        stream.write(0x00)
        stream.write(34)
        stream.write(ByteArray(34))

        // Block 6: PICTURE (type 6, length 10000, isLast = true)
        val picLength = 10000
        stream.write(0x86)
        stream.write((picLength shr 16) and 0xFF)
        stream.write((picLength shr 8) and 0xFF)
        stream.write(picLength and 0xFF)

        // Write partial picture header where dataLength is 5000, but only 100 bytes of data are present
        val picBody = ByteArrayOutputStream()
        picBody.write(0)
        picBody.write(0)
        picBody.write(0)
        picBody.write(3) // pictureType
        val mime = "image/jpeg".toByteArray(StandardCharsets.US_ASCII)
        writeIntBe(picBody, mime.size)
        picBody.write(mime)
        writeIntBe(picBody, 0) // descLength
        for (i in 0 until 16) picBody.write(0) // width, height, etc.
        writeIntBe(picBody, 5000) // dataLength = 5000!
        picBody.write(ByteArray(100) { 0xFF.toByte() }) // only 100 bytes!

        stream.write(picBody.toByteArray())

        val bytes = stream.toByteArray()
        val parsed = FlacParser.parse(bytes)
        assertNotNull(parsed)
        assertNull("Truncated FLAC PICTURE must not return partial artwork", parsed?.artworkData)
    }

    @Test
    fun testDetectRequiredTagSize_wavWithEmbeddedId3_detectsRequiredTagSize() {
        val stream = ByteArrayOutputStream()
        stream.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
        writeIntLe(stream, 2000000) // 2MB RIFF chunk
        stream.write("WAVE".toByteArray(StandardCharsets.US_ASCII))

        // "fmt " chunk
        stream.write("fmt ".toByteArray(StandardCharsets.US_ASCII))
        writeIntLe(stream, 16)
        stream.write(ByteArray(16))

        // "id3 " chunk with oversized tag of 750,000 bytes
        stream.write("id3 ".toByteArray(StandardCharsets.US_ASCII))
        writeIntLe(stream, 750010)

        // ID3 header inside "id3 " chunk
        stream.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        stream.write(3) // v2.3
        stream.write(0)
        stream.write(0)
        val tagPayload = 750000
        stream.write((tagPayload shr 21) and 0x7F)
        stream.write((tagPayload shr 14) and 0x7F)
        stream.write((tagPayload shr 7) and 0x7F)
        stream.write(tagPayload and 0x7F)

        // Truncated buffer simulating 512KB initial fetch
        val initialBytes = stream.toByteArray()
        val detected = AudioMetadataParser.detectRequiredTagSize(initialBytes)

        assertNotNull("Tag size for WAV with embedded ID3 must be detected", detected)
        assertTrue(
            "Detected size ($detected) must cover the embedded ID3 chunk (expected >= 750010)",
            detected!! >= 750010L,
        )
    }

    // Helper functions
    private fun writeId3Frame(
        stream: ByteArrayOutputStream,
        frameId: String,
        text: String,
    ) {
        val payload = ByteArrayOutputStream()
        payload.write(3) // UTF-8 encoding
        payload.write(text.toByteArray(StandardCharsets.UTF_8))
        writeRawId3Frame(stream, frameId, payload.toByteArray())
    }

    private fun writeRawId3Frame(
        stream: ByteArrayOutputStream,
        frameId: String,
        payload: ByteArray,
    ) {
        stream.write(frameId.toByteArray(StandardCharsets.US_ASCII))
        writeIntBe(stream, payload.size)
        stream.write(0) // flag 1
        stream.write(0) // flag 2
        stream.write(payload)
    }

    private fun writeWavInfoSubchunk(
        stream: ByteArrayOutputStream,
        subId: String,
        text: String,
    ) {
        stream.write(subId.toByteArray(StandardCharsets.US_ASCII))
        val textBytes = (text + "\u0000").toByteArray(StandardCharsets.UTF_8)
        writeIntLe(stream, textBytes.size)
        stream.write(textBytes)
        // Word padding
        if ((textBytes.size and 1) != 0) {
            stream.write(0)
        }
    }

    private fun write24BitBe(
        stream: ByteArrayOutputStream,
        value: Int,
    ) {
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write(value and 0xFF)
    }

    private fun writeIntBe(
        stream: ByteArrayOutputStream,
        value: Int,
    ) {
        stream.write((value shr 24) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write(value and 0xFF)
    }

    private fun writeIntLe(
        stream: ByteArrayOutputStream,
        value: Int,
    ) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 24) and 0xFF)
    }

    private fun writeShortLe(
        stream: ByteArrayOutputStream,
        value: Int,
    ) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
    }
}
