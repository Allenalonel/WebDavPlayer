package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import com.webdav.player.data.metadata.ByteSliceReader
import com.webdav.player.domain.model.AudioFormat

@OptIn(UnstableApi::class)
class AsfExtractor : Extractor {

    companion object {
        private const val TAG = "AsfExtractor"
        private const val DIRECT_BUFFER_CAPACITY = 65536
        private const val DEFAULT_PACKET_SIZE = 8192

        val ASF_HEADER_GUID = byteArrayOf(
            0x30.toByte(), 0x26.toByte(), 0xB2.toByte(), 0x75.toByte(),
            0x8E.toByte(), 0x66.toByte(), 0xCF.toByte(), 0x11.toByte(),
            0xA6.toByte(), 0xD9.toByte(), 0x00.toByte(), 0xAA.toByte(),
            0x00.toByte(), 0x62.toByte(), 0xCE.toByte(), 0x6C.toByte()
        )

        val STREAM_PROPERTIES_GUID = byteArrayOf(
            0x91.toByte(), 0x07.toByte(), 0xDC.toByte(), 0xB7.toByte(),
            0xB7.toByte(), 0xA9.toByte(), 0xCF.toByte(), 0x11.toByte(),
            0x8E.toByte(), 0xE6.toByte(), 0x00.toByte(), 0xC0.toByte(),
            0x0C.toByte(), 0x20.toByte(), 0x53.toByte(), 0x65.toByte()
        )

        val FILE_PROPERTIES_GUID = byteArrayOf(
            0xA1.toByte(), 0xDC.toByte(), 0xAB.toByte(), 0x8C.toByte(),
            0x47.toByte(), 0xA9.toByte(), 0xCF.toByte(), 0x11.toByte(),
            0x8E.toByte(), 0xE4.toByte(), 0x00.toByte(), 0xC0.toByte(),
            0x0C.toByte(), 0x20.toByte(), 0x53.toByte(), 0x65.toByte()
        )

        val DATA_OBJECT_GUID = byteArrayOf(
            0x36.toByte(), 0x26.toByte(), 0xB2.toByte(), 0x75.toByte(),
            0x8E.toByte(), 0x66.toByte(), 0xCF.toByte(), 0x11.toByte(),
            0xA6.toByte(), 0xD9.toByte(), 0x00.toByte(), 0xAA.toByte(),
            0x00.toByte(), 0x62.toByte(), 0xCE.toByte(), 0x6C.toByte()
        )

        val AUDIO_MEDIA_TYPE_GUID = byteArrayOf(
            0x40.toByte(), 0x9E.toByte(), 0x69.toByte(), 0xF8.toByte(),
            0x4D.toByte(), 0x5B.toByte(), 0xCF.toByte(), 0x11.toByte(),
            0xA8.toByte(), 0xFD.toByte(), 0x00.toByte(), 0x80.toByte(),
            0x5F.toByte(), 0x5C.toByte(), 0x44.toByte(), 0x2B.toByte()
        )
    }

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null
    private var headerParsed = false
    private var useNative = false
    private var nativeHandle = 0L

    private val frameByteArray = ByteArray(DIRECT_BUFFER_CAPACITY)
    private val parsableByteArray = ParsableByteArray()
    private val metaArray = LongArray(3)

    // Fallback and metadata state
    private var audioStreamId = 1
    private var sampleRate = 44100
    private var channelCount = 2
    private var durationUs = C.TIME_UNSET
    private var currentTimeUs = 0L
    private var packetSize = DEFAULT_PACKET_SIZE
    private var totalDataPackets = 0L
    private var extraData: ByteArray? = null
    private var isRealAsf = false
    private var dataStartOffset = 0L

    override fun sniff(input: ExtractorInput): Boolean {
        val header = ByteArray(16)
        val matched = try {
            input.peekFully(header, 0, 16)
            header.contentEquals(ASF_HEADER_GUID)
        } catch (e: Exception) {
            false
        }
        return matched
    }

    override fun init(output: ExtractorOutput) {
        this.extractorOutput = output
        this.trackOutput = output.track(0, C.TRACK_TYPE_AUDIO)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val track = trackOutput ?: return Extractor.RESULT_END_OF_INPUT

        if (!headerParsed) {
            // First peek ASF header parameters for accurate SeekMap calculation
            peekAsfHeaderParameters(input)

            var nativeSuccess = false
            if (FfmpegLibrary.isAvailable()) {
                try {
                    nativeHandle = nativeInit()
                    if (nativeHandle != 0L) {
                        val openResult = nativeOpen(nativeHandle, input)
                        if (openResult == 0) {
                            useNative = true
                            nativeSuccess = true
                            initTracksFromNative(track)
                        } else {
                            Log.w(TAG, "nativeOpen returned $openResult, falling back to JVM extractor")
                            nativeRelease(nativeHandle)
                            nativeHandle = 0L
                        }
                    }
                } catch (e: UnsatisfiedLinkError) {
                    Log.i(TAG, "Native library not available in current environment: ${e.message}")
                    useNative = false
                } catch (t: Throwable) {
                    Log.w(TAG, "Native demuxer initialization failed: ${t.message}")
                    useNative = false
                }
            }

            if (!nativeSuccess) {
                useNative = false
                initTracksFallback(input, track)
            }
            headerParsed = true
        }

        return if (useNative && nativeHandle != 0L) {
            readNativeFrame(input, track)
        } else {
            readFallbackPacket(input, track)
        }
    }

    private fun initTracksFromNative(track: TrackOutput) {
        val output = extractorOutput ?: return

        val sr = nativeGetSampleRate(nativeHandle)
        val cc = nativeGetChannelCount(nativeHandle)
        val durUs = nativeGetDurationUs(nativeHandle)
        val codecName = nativeGetCodecName(nativeHandle) ?: "wmav2"
        val extra = nativeGetExtraData(nativeHandle)
        val br = nativeGetBitrate(nativeHandle)
        val align = nativeGetBlockAlign(nativeHandle)

        if (sr > 0) sampleRate = sr
        if (cc > 0) channelCount = cc
        if (durUs > 0) durationUs = durUs
        if (extra != null && extra.isNotEmpty()) extraData = extra

        val mimeType = when (codecName) {
            "wmav1" -> "audio/x-ms-wmav1"
            "wmav2" -> "audio/x-ms-wma"
            "wmapro" -> "audio/x-ms-wmapro"
            "wmalossless" -> "audio/x-ms-wmalossless"
            else -> AudioFormat.WMA.mimeType
        }

        val formatBuilder = Format.Builder()
            .setId("audio/0")
            .setSampleMimeType(mimeType)
            .setChannelCount(channelCount)
            .setSampleRate(sampleRate)

        if (br > 0) {
            formatBuilder.setAverageBitrate(br)
        }
        if (align > 0) {
            formatBuilder.setMaxInputSize(align)
        }

        extraData?.let {
            formatBuilder.setInitializationData(listOf(it))
        }

        val builtFormat = formatBuilder.build()
        track.format(builtFormat)
        output.endTracks()

        val seekMap = if (durationUs > 0 && packetSize > 0 && totalDataPackets > 0) {
            AsfSeekMap(durationUs, dataStartOffset, packetSize, totalDataPackets)
        } else {
            SeekMap.Unseekable(if (durationUs > 0) durationUs else C.TIME_UNSET)
        }
        output.seekMap(seekMap)
    }

    private fun readNativeFrame(input: ExtractorInput, track: TrackOutput): Int {
        val ret = nativeReadFrame(nativeHandle, input, frameByteArray, metaArray)
        if (ret == 0) {
            val frameSize = metaArray[0].toInt()
            val framePtsUs = metaArray[1]
            val isKey = metaArray[2] != 0L
            val flags = if (isKey) C.BUFFER_FLAG_KEY_FRAME else 0

            parsableByteArray.reset(frameByteArray, frameSize)
            parsableByteArray.position = 0
            track.sampleData(parsableByteArray, frameSize)
            track.sampleMetadata(framePtsUs, flags, frameSize, 0, null)
            return Extractor.RESULT_CONTINUE
        } else {
            return if (ret == 1) Extractor.RESULT_END_OF_INPUT else Extractor.RESULT_CONTINUE
        }
    }

    private fun peekAsfHeaderParameters(input: ExtractorInput) {
        val peekBuffer = ByteArray(32768)
        val peeked = try {
            input.peek(peekBuffer, 0, peekBuffer.size)
        } catch (e: Exception) {
            0
        }

        if (peeked >= 30) {
            val reader = ByteSliceReader(peekBuffer, 0)
            val guid = reader.readBytes(16)
            if (guid.contentEquals(ASF_HEADER_GUID)) {
                val totalHeaderSize = reader.readLongLe()
                reader.skip(4) // numSubObjects
                reader.skip(2) // reserved

                var filePropertiesFound = false
                var streamPropertiesFound = false

                val headerLimit = totalHeaderSize.toInt().coerceIn(30, peeked)
                while (reader.position() + 24 <= headerLimit) {
                    val subGuid = reader.readBytes(16)
                    val subSize = reader.readLongLe().toInt()
                    if (subSize < 24) break
                    val subEnd = (reader.position() + subSize - 24).coerceAtMost(headerLimit)

                    when {
                        subGuid.contentEquals(FILE_PROPERTIES_GUID) -> {
                            filePropertiesFound = true
                            if (reader.hasRemaining(72)) {
                                reader.skip(24) // file id, file size
                                reader.skip(8)  // creation time
                                totalDataPackets = reader.readLongLe()
                                val playDuration100ns = reader.readLongLe()
                                if (playDuration100ns > 0) {
                                    durationUs = playDuration100ns / 10L
                                }
                                reader.skip(20) // send duration, preroll, flags
                                val minPacket = reader.readIntLe()
                                val maxPacket = reader.readIntLe()
                                if (maxPacket > 0) {
                                    packetSize = maxPacket
                                } else if (minPacket > 0) {
                                    packetSize = minPacket
                                }
                            }
                        }
                        subGuid.contentEquals(STREAM_PROPERTIES_GUID) -> {
                            streamPropertiesFound = true
                            if (reader.hasRemaining(54)) {
                                val streamTypeGuid = reader.readBytes(16)
                                reader.skip(16) // error correction guid
                                reader.skip(8)  // time offset
                                val typeDataLen = reader.readIntLe()
                                reader.skip(4)  // error correction data len
                                val flags = reader.readShortLe()
                                val streamNum = flags and 0x7F
                                reader.skip(4)  // reserved

                                if (streamTypeGuid.contentEquals(AUDIO_MEDIA_TYPE_GUID) && typeDataLen >= 16) {
                                    audioStreamId = streamNum
                                    reader.skip(2) // wFormatTag
                                    val channels = reader.readShortLe()
                                    val samplesPerSec = reader.readIntLe()
                                    reader.skip(4) // avgBytesPerSec
                                    reader.skip(2) // blockAlign
                                    reader.skip(2) // bitsPerSample
                                    var cbSize = 0
                                    if (reader.hasRemaining(2)) {
                                        cbSize = reader.readShortLe()
                                    }

                                    if (channels > 0) channelCount = channels
                                    if (samplesPerSec > 0) sampleRate = samplesPerSec
                                    if (cbSize > 0 && reader.hasRemaining(cbSize)) {
                                        extraData = reader.readBytes(cbSize)
                                    }
                                }
                            }
                        }
                    }

                    if (reader.position() < subEnd) {
                        reader.seek(subEnd)
                    }
                }

                if (filePropertiesFound || streamPropertiesFound) {
                    isRealAsf = true
                    var dataHeaderFound = false
                    if (reader.position() + 24 <= peeked) {
                        val dataGuid = reader.readBytes(16)
                        if (dataGuid.contentEquals(DATA_OBJECT_GUID)) {
                            dataHeaderFound = true
                            reader.skip(34)
                            dataStartOffset = reader.position().toLong()
                        }
                    }
                    if (!dataHeaderFound) {
                        dataStartOffset = (totalHeaderSize + 50).coerceAtMost(peeked.toLong())
                    }
                }
            }
        }
    }

    private fun initTracksFallback(input: ExtractorInput, track: TrackOutput) {
        val formatBuilder = Format.Builder()
            .setId("1")
            .setSampleMimeType(AudioFormat.WMA.mimeType)
            .setChannelCount(channelCount)
            .setSampleRate(sampleRate)

        extraData?.let {
            formatBuilder.setInitializationData(listOf(it))
        }

        track.format(formatBuilder.build())
        val output = extractorOutput
        if (output != null) {
            output.endTracks()
            val seekMap = if (durationUs > 0 && packetSize > 0 && totalDataPackets > 0) {
                AsfSeekMap(durationUs, dataStartOffset, packetSize, totalDataPackets)
            } else {
                SeekMap.Unseekable(durationUs)
            }
            output.seekMap(seekMap)
        }

        if (isRealAsf && dataStartOffset > 0 && input.position < dataStartOffset) {
            try {
                input.skipFully((dataStartOffset - input.position).toInt())
            } catch (e: Exception) {
                // Ignore fallback skip error
            }
        }
    }

    private fun readFallbackPacket(input: ExtractorInput, track: TrackOutput): Int {
        val targetSize = packetSize.coerceIn(512, 65536)
        val packetBuffer = ByteArray(targetSize)

        var bytesRead = 0
        try {
            while (bytesRead < targetSize) {
                val r = input.read(packetBuffer, bytesRead, targetSize - bytesRead)
                if (r == C.RESULT_END_OF_INPUT) {
                    if (bytesRead == 0) return Extractor.RESULT_END_OF_INPUT
                    break
                }
                bytesRead += r
            }
        } catch (e: Exception) {
            if (bytesRead == 0) return Extractor.RESULT_END_OF_INPUT
        }

        if (bytesRead == 0) return Extractor.RESULT_END_OF_INPUT

        val parsable = ParsableByteArray(packetBuffer, bytesRead)
        parsable.position = 0
        track.sampleData(parsable, bytesRead)
        track.sampleMetadata(
            currentTimeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            bytesRead,
            0,
            null
        )
        val bytesPerSec = sampleRate * channelCount * 2L
        if (bytesPerSec > 0) {
            currentTimeUs += (bytesRead * 1_000_000L) / bytesPerSec
        }

        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        currentTimeUs = timeUs
        if (useNative && nativeHandle != 0L) {
            nativeSeek(nativeHandle, position, timeUs)
        }
    }

    override fun release() {
        if (nativeHandle != 0L) {
            try {
                nativeRelease(nativeHandle)
            } catch (e: Throwable) {
                // Ignore
            }
            nativeHandle = 0L
        }
    }

    class AsfSeekMap(
        private val durationUs: Long,
        private val dataStartOffset: Long,
        private val packetSize: Int,
        private val totalDataPackets: Long
    ) : SeekMap {
        override fun isSeekable(): Boolean = durationUs > 0 && packetSize > 0

        override fun getDurationUs(): Long = durationUs

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            if (durationUs <= 0 || totalDataPackets <= 0 || packetSize <= 0) {
                val point = SeekPoint(0L, dataStartOffset)
                return SeekMap.SeekPoints(point)
            }
            val clampedTime = timeUs.coerceIn(0L, durationUs)
            val packetIndex = ((clampedTime.toDouble() / durationUs.toDouble()) * totalDataPackets).toLong()
                .coerceIn(0L, (totalDataPackets - 1).coerceAtLeast(0L))
            val targetPosition = dataStartOffset + (packetIndex * packetSize)
            val point = SeekPoint(clampedTime, targetPosition)
            return SeekMap.SeekPoints(point)
        }
    }

    private external fun nativeInit(): Long
    private external fun nativeOpen(handle: Long, input: ExtractorInput): Int
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeGetChannelCount(handle: Long): Int
    private external fun nativeGetBitrate(handle: Long): Int
    private external fun nativeGetBlockAlign(handle: Long): Int
    private external fun nativeGetDurationUs(handle: Long): Long
    private external fun nativeGetCodecName(handle: Long): String?
    private external fun nativeGetExtraData(handle: Long): ByteArray?
    private external fun nativeReadFrame(
        handle: Long,
        input: ExtractorInput,
        outputArray: ByteArray,
        outMeta: LongArray
    ): Int
    private external fun nativeSeek(handle: Long, position: Long, timeUs: Long): Int
    private external fun nativeRelease(handle: Long)
}
