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
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import com.webdav.player.data.metadata.ByteSliceReader
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

        private const val DEFAULT_PACKET_SIZE = 8192
    }

    private var extractorOutput: ExtractorOutput? = null
    private var trackOutput: TrackOutput? = null
    private var headerParsed = false
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
            parseHeaderAndSkipToData(input)
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
                val seekMap = if (isRealAsf && durationUs > 0 && packetSize > 0) {
                    AsfSeekMap(durationUs, dataStartOffset, packetSize, totalDataPackets)
                } else {
                    SeekMap.Unseekable(durationUs)
                }
                output.seekMap(seekMap)
            }
            headerParsed = true
        }

        return readNextPacket(input, track)
    }

    private fun parseHeaderAndSkipToData(input: ExtractorInput) {
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
                                    val wFormatTag = reader.readShortLe()
                                    val channels = reader.readShortLe()
                                    val samplesPerSec = reader.readIntLe()
                                    reader.skip(4) // avgBytesPerSec
                                    val blockAlign = reader.readShortLe()
                                    val bitsPerSample = reader.readShortLe()
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
                    // Check for Data Object header
                    var dataHeaderFound = false
                    if (reader.position() + 24 <= peeked) {
                        val dataGuid = reader.readBytes(16)
                        if (dataGuid.contentEquals(DATA_OBJECT_GUID)) {
                            dataHeaderFound = true
                            reader.skip(34) // 8 size + 16 file guid + 8 total packets + 2 reserved = 34 bytes
                        }
                    }

                    val bytesToSkip = if (dataHeaderFound) {
                        reader.position()
                    } else {
                        (totalHeaderSize.toInt() + 50).coerceAtMost(peeked)
                    }

                    try {
                        input.skipFully(bytesToSkip)
                        dataStartOffset = input.position
                    } catch (e: Exception) {
                        // Fallback if unable to skip fully
                    }
                }
            }
        }
    }

    private fun readNextPacket(input: ExtractorInput, track: TrackOutput): Int {
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
            return Extractor.RESULT_END_OF_INPUT
        }

        if (bytesRead == 0) return Extractor.RESULT_END_OF_INPUT

        val parsable = ParsableByteArray(packetBuffer, bytesRead)

        if (isRealAsf && bytesRead >= 16) {
            var offset = 0

            // Error correction handling
            val firstByte = packetBuffer[0].toInt() and 0xFF
            if ((firstByte and 0x80) != 0) {
                val ecLength = firstByte and 0x0F
                offset += (1 + ecLength).coerceAtMost(bytesRead)
            }

            if (offset + 2 <= bytesRead) {
                val lengthFlags = packetBuffer[offset].toInt() and 0xFF
                val propertyFlags = packetBuffer[offset + 1].toInt() and 0xFF
                offset += 2

                val multiplePayloads = (lengthFlags and 0x01) != 0
                val seqType = (lengthFlags shr 1) and 0x03
                val padType = (lengthFlags shr 3) and 0x03
                val pktLenType = (lengthFlags shr 5) and 0x03

                val repLenType = propertyFlags and 0x03
                val offsetType = (propertyFlags shr 2) and 0x03
                val mediaObjType = (propertyFlags shr 4) and 0x03

                fun readVar(type: Int): Int {
                    return when (type) {
                        1 -> if (offset < bytesRead) packetBuffer[offset++].toInt() and 0xFF else 0
                        2 -> if (offset + 1 < bytesRead) {
                            val v = ((packetBuffer[offset + 1].toInt() and 0xFF) shl 8) or
                                    (packetBuffer[offset].toInt() and 0xFF)
                            offset += 2
                            v
                        } else 0
                        3 -> if (offset + 3 < bytesRead) {
                            val v = ((packetBuffer[offset + 3].toInt() and 0xFF) shl 24) or
                                    ((packetBuffer[offset + 2].toInt() and 0xFF) shl 16) or
                                    ((packetBuffer[offset + 1].toInt() and 0xFF) shl 8) or
                                    (packetBuffer[offset].toInt() and 0xFF)
                            offset += 4
                            v
                        } else 0
                        else -> 0
                    }
                }

                val explicitLen = readVar(pktLenType)
                val effectiveLen = if (explicitLen > 0) explicitLen.coerceAtMost(bytesRead) else bytesRead
                readVar(seqType) // sequence
                val paddingLen = readVar(padType)

                var sendTimeMs = 0L
                if (offset + 4 <= bytesRead) {
                    sendTimeMs = ((packetBuffer[offset + 3].toLong() and 0xFFL) shl 24) or
                            ((packetBuffer[offset + 2].toLong() and 0xFFL) shl 16) or
                            ((packetBuffer[offset + 1].toLong() and 0xFFL) shl 8) or
                            (packetBuffer[offset].toLong() and 0xFFL)
                    offset += 4
                }
                if (offset + 2 <= bytesRead) {
                    offset += 2 // durationMs
                }

                if (sendTimeMs > 0) {
                    currentTimeUs = sendTimeMs * 1000L
                }

                if (multiplePayloads && offset < bytesRead) {
                    val payloadCount = packetBuffer[offset++].toInt() and 0x3F

                    for (p in 0 until payloadCount) {
                        if (offset >= bytesRead) break
                        val streamByte = packetBuffer[offset++].toInt() and 0xFF
                        val streamId = streamByte and 0x7F
                        val isKeyframe = (streamByte and 0x80) != 0

                        readVar(mediaObjType)
                        readVar(offsetType)
                        val repLen = readVar(repLenType)
                        offset = (offset + repLen).coerceAtMost(bytesRead)

                        if (offset + 2 > bytesRead) break
                        val payloadLen = ((packetBuffer[offset + 1].toInt() and 0xFF) shl 8) or
                                (packetBuffer[offset].toInt() and 0xFF)
                        offset += 2

                        val validPayloadLen = payloadLen.coerceAtMost(effectiveLen - offset - paddingLen)
                        if (validPayloadLen > 0 && offset + validPayloadLen <= bytesRead) {
                            if (streamId == audioStreamId || streamId == 1) {
                                parsable.position = offset
                                track.sampleData(parsable, validPayloadLen)
                                track.sampleMetadata(
                                    currentTimeUs,
                                    if (isKeyframe) C.BUFFER_FLAG_KEY_FRAME else 0,
                                    validPayloadLen,
                                    0,
                                    null
                                )
                                val bytesPerSec = sampleRate * channelCount * 2L
                                if (bytesPerSec > 0) {
                                    currentTimeUs += (validPayloadLen * 1_000_000L) / bytesPerSec
                                }
                            }
                            offset += validPayloadLen
                        }
                    }
                    return Extractor.RESULT_CONTINUE
                } else if (!multiplePayloads) {
                    if (offset < bytesRead) {
                        val streamByte = packetBuffer[offset++].toInt() and 0xFF
                        val streamId = streamByte and 0x7F
                        val isKeyframe = (streamByte and 0x80) != 0

                        readVar(mediaObjType)
                        readVar(offsetType)
                        val repLen = readVar(repLenType)
                        offset = (offset + repLen).coerceAtMost(bytesRead)

                        val payloadLen = (effectiveLen - offset - paddingLen).coerceAtLeast(0)
                        if (payloadLen > 0 && offset + payloadLen <= bytesRead) {
                            if (streamId == audioStreamId || streamId == 1) {
                                parsable.position = offset
                                track.sampleData(parsable, payloadLen)
                                track.sampleMetadata(
                                    currentTimeUs,
                                    if (isKeyframe) C.BUFFER_FLAG_KEY_FRAME else 0,
                                    payloadLen,
                                    0,
                                    null
                                )
                                val bytesPerSec = sampleRate * channelCount * 2L
                                if (bytesPerSec > 0) {
                                    currentTimeUs += (payloadLen * 1_000_000L) / bytesPerSec
                                }
                            }
                        }
                    }
                    return Extractor.RESULT_CONTINUE
                }
            }
        }

        // Fallback for raw streaming or synthetic test inputs
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
    }

    override fun release() {
        // No native handles
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
}
