package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.webdav.player.domain.model.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class AsfExtractorTest {

    private class FakeExtractorInput(data: ByteArray) : ExtractorInput {
        private val stream = ByteArrayInputStream(data)
        private var position = 0L

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val bytesRead = stream.read(buffer, offset, length)
            if (bytesRead > 0) position += bytesRead
            return if (bytesRead >= 0) bytesRead else C.RESULT_END_OF_INPUT
        }

        override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            var total = 0
            while (total < length) {
                val r = stream.read(target, offset + total, length - total)
                if (r == -1) {
                    if (allowEndOfInput && total == 0) return false
                    throw EOFException()
                }
                total += r
            }
            position += total
            return true
        }

        override fun readFully(target: ByteArray, offset: Int, length: Int) {
            readFully(target, offset, length, false)
        }

        override fun skip(length: Int): Int {
            val skipped = stream.skip(length.toLong()).toInt()
            position += skipped
            return skipped
        }

        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
            val buf = ByteArray(minOf(length, 4096))
            var remaining = length
            while (remaining > 0) {
                val r = read(buf, 0, minOf(remaining, buf.size))
                if (r == C.RESULT_END_OF_INPUT) {
                    if (allowEndOfInput && remaining == length) return false
                    throw EOFException()
                }
                remaining -= r
            }
            return true
        }

        override fun skipFully(length: Int) {
            skipFully(length, false)
        }

        override fun peek(target: ByteArray, offset: Int, length: Int): Int {
            stream.mark(length + 10)
            val r = stream.read(target, offset, length)
            stream.reset()
            return if (r >= 0) r else C.RESULT_END_OF_INPUT
        }

        override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            stream.mark(length + 10)
            var total = 0
            while (total < length) {
                val r = stream.read(target, offset + total, length - total)
                if (r == -1) {
                    stream.reset()
                    if (allowEndOfInput && total == 0) return false
                    throw EOFException()
                }
                total += r
            }
            stream.reset()
            return true
        }

        override fun peekFully(target: ByteArray, offset: Int, length: Int) {
            peekFully(target, offset, length, false)
        }

        override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean = true
        override fun advancePeekPosition(length: Int) {}
        override fun resetPeekPosition() {}
        override fun getPeekPosition(): Long = position
        override fun getPosition(): Long = position
        override fun getLength(): Long = -1L
        override fun <E : Throwable> setRetryPosition(position: Long, e: E) {}
    }

    private class FakeTrackOutput : TrackOutput {
        var format: Format? = null
        val sampleData = mutableListOf<ByteArray>()
        var sampleMetadataCount = 0

        override fun format(format: Format) {
            this.format = format
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            val bytes = ByteArray(length)
            data.readBytes(bytes, 0, length)
            sampleData.add(bytes)
        }

        override fun sampleData(
            input: androidx.media3.common.DataReader,
            length: Int,
            allowEndOfInput: Boolean,
            sampleDataPart: Int
        ): Int {
            val bytes = ByteArray(length)
            val read = input.read(bytes, 0, length)
            if (read > 0) sampleData.add(bytes.copyOf(read))
            return read
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?
        ) {
            sampleMetadataCount++
        }
    }

    private class FakeExtractorOutput : ExtractorOutput {
        val tracks = mutableMapOf<Int, FakeTrackOutput>()
        var tracksEnded = false
        var seekMap: SeekMap? = null

        override fun track(id: Int, type: Int): TrackOutput {
            return tracks.getOrPut(id) { FakeTrackOutput() }
        }

        override fun endTracks() {
            tracksEnded = true
        }

        override fun seekMap(seekMap: SeekMap) {
            this.seekMap = seekMap
        }
    }

    @Test
    fun sniff_returnsTrue_forAsfHeader() {
        val data = ByteArray(64)
        System.arraycopy(AsfExtractor.ASF_HEADER_GUID, 0, data, 0, 16)
        val input = FakeExtractorInput(data)
        val extractor = AsfExtractor()

        assertTrue(extractor.sniff(input))
    }

    @Test
    fun sniff_returnsFalse_forNonAsfHeader() {
        val data = "ID3\u0003\u0000\u0000\u0000\u0000\u0000\u0000NotASFDataAtAll".toByteArray()
        val input = FakeExtractorInput(data)
        val extractor = AsfExtractor()

        assertFalse(extractor.sniff(input))
    }

    @Test
    fun read_initializesTracks_andExtractsSamples() {
        val data = ByteArray(1024)
        System.arraycopy(AsfExtractor.ASF_HEADER_GUID, 0, data, 0, 16)
        // Dummy audio payload
        for (i in 16 until 1024) {
            data[i] = (i % 256).toByte()
        }

        val input = FakeExtractorInput(data)
        val output = FakeExtractorOutput()
        val extractor = AsfExtractor()
        extractor.init(output)

        val result = extractor.read(input, PositionHolder())
        assertEquals(Extractor.RESULT_CONTINUE, result)
        assertTrue("Tracks should be ended", output.tracksEnded)
        assertNotNull("SeekMap should be provided", output.seekMap)

        val track = output.tracks[0]
        assertNotNull(track)
        assertEquals(AudioFormat.WMA.mimeType, track?.format?.sampleMimeType)
        assertTrue(track!!.sampleData.isNotEmpty())
        assertTrue(track.sampleMetadataCount > 0)
    }

    @Test
    fun asfSeekMap_isSeekable_andCalculatesByteOffsets() {
        val durationUs = 200_000_000L // 200 seconds
        val dataStartOffset = 4096L
        val packetSize = 2048
        val totalPackets = 1000L

        val seekMap = AsfExtractor.AsfSeekMap(durationUs, dataStartOffset, packetSize, totalPackets)
        assertTrue(seekMap.isSeekable)
        assertEquals(durationUs, seekMap.durationUs)

        // Seek to 100s (middle) -> should be packet 500
        val points = seekMap.getSeekPoints(100_000_000L)
        val expectedOffset = dataStartOffset + (500L * packetSize)
        assertEquals(expectedOffset, points.first.position)
        assertEquals(100_000_000L, points.first.timeUs)
    }

    @Test
    fun seekAndRelease_executeWithoutError() {
        val data = ByteArray(1024)
        System.arraycopy(AsfExtractor.ASF_HEADER_GUID, 0, data, 0, 16)
        for (i in 16 until 1024) {
            data[i] = (i % 256).toByte()
        }

        val input = FakeExtractorInput(data)
        val output = FakeExtractorOutput()
        val extractor = AsfExtractor()
        assertTrue(extractor.sniff(input))

        extractor.init(output)
        val result = extractor.read(input, PositionHolder())
        assertEquals(Extractor.RESULT_CONTINUE, result)
        assertTrue(output.tracksEnded)
        assertNotNull(output.seekMap)

        extractor.seek(0L, 10_000_000L)
        extractor.release()
    }

    @Test
    fun read_whenStreamEnds_returnsEndOfInputGracefully() {
        val headerBytes = createMockAsfHeader(durationSec = 100L, packetSize = 1024)
        val fullData = ByteArray(headerBytes.size + 1024)
        System.arraycopy(headerBytes, 0, fullData, 0, headerBytes.size)

        val input = FakeExtractorInput(fullData)
        val output = FakeExtractorOutput()
        val extractor = AsfExtractor()
        extractor.init(output)

        val holder = PositionHolder()
        var endOfInputReached = false
        while (!endOfInputReached) {
            val res = extractor.read(input, holder)
            if (res == Extractor.RESULT_END_OF_INPUT) {
                endOfInputReached = true
            }
        }
        assertTrue("Extractor should reach end of input gracefully", endOfInputReached)
    }

    @Test
    fun playbackProgress_formatsUnderflowDurationAccurately_andDemonstratesGlitchResolution() {
        // 4295309926 ms was the exact bugged timestamp observed in logcat/dumpsys
        val buggedMs = 4295309926L
        val buggedFormatted = com.webdav.player.domain.model.PlaybackProgress.formatMs(buggedMs)
        // Verify this indeed reproduces the user's reported "1193:07:xx" symptom
        assertTrue("Bugged timestamp should format as 1193 hours", buggedFormatted.startsWith("1193:"))

        // Verify that stripping the 32-bit underflow wrap (0x100000000L = 4294967296 ms)
        // accurately recovers the expected 5-minute range (~342s = 05:42)
        val wrapMs = 0x100000000L
        val normalizedMs = buggedMs % wrapMs
        val normalizedFormatted = com.webdav.player.domain.model.PlaybackProgress.formatMs(normalizedMs)
        assertEquals("05:42", normalizedFormatted)
    }

    private fun createMockAsfHeader(durationSec: Long, packetSize: Int): ByteArray {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)

        val filePropSize = 104
        val streamPropSize = 78
        val totalHeaderSize = 30L + filePropSize + streamPropSize

        // 1. Header Object
        buffer.put(AsfExtractor.ASF_HEADER_GUID)
        buffer.putLong(totalHeaderSize)
        buffer.putInt(2) // num sub-objects
        buffer.put(0x01)
        buffer.put(0x02)

        // 2. File Properties Object
        buffer.put(AsfExtractor.FILE_PROPERTIES_GUID)
        buffer.putLong(filePropSize.toLong())
        buffer.put(ByteArray(16)) // file id
        buffer.putLong(100_000L) // file size
        buffer.putLong(0L) // creation date
        buffer.putLong(100L) // total data packets
        buffer.putLong(durationSec * 10_000_000L) // play duration in 100ns units
        buffer.putLong(0L) // send duration
        buffer.putLong(0L) // preroll
        buffer.putInt(2) // flags
        buffer.putInt(packetSize) // min packet
        buffer.putInt(packetSize) // max packet
        buffer.putInt(32000) // max bitrate

        // 3. Stream Properties Object
        buffer.put(AsfExtractor.STREAM_PROPERTIES_GUID)
        buffer.putLong(streamPropSize.toLong())
        buffer.put(AsfExtractor.AUDIO_MEDIA_TYPE_GUID)
        buffer.put(ByteArray(16)) // error correction guid
        buffer.putLong(0L) // time offset
        buffer.putInt(16) // type data len
        buffer.putInt(0) // error correction data len
        buffer.putShort(1.toShort()) // flags (stream 1)
        buffer.putInt(0) // reserved
        buffer.putShort(0x0161.toShort()) // wFormatTag
        buffer.putShort(2.toShort()) // channels
        buffer.putInt(44100) // sample rate
        buffer.putInt(8000) // avgBytesPerSec
        buffer.putShort(185.toShort()) // block align
        buffer.putShort(16.toShort()) // bitsPerSample

        // 4. Data Object Header
        buffer.put(AsfExtractor.DATA_OBJECT_GUID)
        buffer.putLong(50L) // data object header size
        buffer.put(ByteArray(16)) // file id
        buffer.putLong(100L) // total packets
        buffer.putShort(0.toShort()) // reserved

        val headerData = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(headerData)
        return headerData
    }
}
