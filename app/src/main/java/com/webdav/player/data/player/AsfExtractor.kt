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
import com.webdav.player.data.metadata.AsfParser
import com.webdav.player.domain.model.AudioFormat

@OptIn(UnstableApi::class)
class AsfExtractor : Extractor {

    companion object {
        val ASF_HEADER_GUID = byteArrayOf(
            0x30.toByte(), 0x26.toByte(), 0xB2.toByte(), 0x75.toByte(),
            0x8E.toByte(), 0x66.toByte(), 0xCF.toByte(), 0x11.toByte(),
            0xA6.toByte(), 0xD9.toByte(), 0x00.toByte(), 0xAA.toByte(),
            0x00.toByte(), 0x62.toByte(), 0xCE.toByte(), 0x6C.toByte()
        )
        private const val BUFFER_SIZE = 8192
    }

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null
    private var headerParsed = false
    private var sampleRate = 44100
    private var channelCount = 2
    private var durationUs = C.TIME_UNSET
    private var currentTimeUs = 0L
    private val scratch = ParsableByteArray(BUFFER_SIZE)

    override fun sniff(input: ExtractorInput): Boolean {
        val header = ByteArray(16)
        return try {
            input.peekFully(header, 0, 16)
            header.contentEquals(ASF_HEADER_GUID)
        } catch (e: Exception) {
            false
        }
    }

    override fun init(output: ExtractorOutput) {
        this.extractorOutput = output
        val track = output.track(0, C.TRACK_TYPE_AUDIO)
        this.trackOutput = track
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val track = trackOutput ?: return Extractor.RESULT_END_OF_INPUT

        if (!headerParsed) {
            val peekHeader = ByteArray(4096)
            val peeked = input.peek(peekHeader, 0, peekHeader.size)
            if (peeked > 0) {
                val metadata = AsfParser.parse(peekHeader, 0)
                if (metadata != null && metadata.durationMs > 0) {
                    durationUs = metadata.durationMs * 1000L
                }
            }

            val format = Format.Builder()
                .setId("1")
                .setSampleMimeType(AudioFormat.WMA.mimeType)
                .setChannelCount(channelCount)
                .setSampleRate(sampleRate)
                .build()

            track.format(format)
            val output = extractorOutput
            if (output != null) {
                output.endTracks()
                output.seekMap(SeekMap.Unseekable(durationUs))
            }
            headerParsed = true
        }

        scratch.reset(BUFFER_SIZE)
        val bytesRead = input.read(scratch.data, 0, BUFFER_SIZE)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            return Extractor.RESULT_END_OF_INPUT
        }

        track.sampleData(scratch, bytesRead)
        track.sampleMetadata(
            currentTimeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            bytesRead,
            /* offset = */ 0,
            /* cryptoData = */ null
        )

        val bytesPerSec = sampleRate * channelCount * 2L
        if (bytesPerSec > 0) {
            currentTimeUs += (bytesRead * 1_000_000L) / bytesPerSec
        }

        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        currentTimeUs = timeUs
    }

    override fun release() {
        // No native handles to release
    }
}
