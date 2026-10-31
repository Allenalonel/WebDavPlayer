package com.webdav.player.data.metadata

internal object MpegAudioFrameParser {
    private val MPEG1_L3_BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val MPEG2_L3_BITRATES = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    private val MPEG1_SAMPLE_RATES = intArrayOf(44100, 48000, 32000)
    private val MPEG2_SAMPLE_RATES = intArrayOf(22050, 24000, 16000)
    private val MPEG25_SAMPLE_RATES = intArrayOf(11025, 12000, 8000)

    fun parseDuration(
        bytes: ByteArray,
        offset: Int = 0,
        fileSize: Long = 0L,
    ): Long {
        if (bytes.size - offset < 4) return 0L

        val scanLimit = (bytes.size - 4).coerceAtMost(offset + 32768)
        var i = offset
        while (i <= scanLimit) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF

            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val versionBits = (b1 ushr 3) and 0x03
                val layerBits = (b1 ushr 1) and 0x03

                // Must be valid version (not reserved 01) and Layer III (01)
                if (versionBits != 1 && layerBits == 1) {
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF

                    val bitrateIdx = (b2 ushr 4) and 0x0F
                    val sampleRateIdx = (b2 ushr 2) and 0x03
                    val channelMode = (b3 ushr 6) and 0x03

                    if (bitrateIdx in 1..14 && sampleRateIdx in 0..2) {
                        val sampleRate =
                            when (versionBits) {
                                3 -> MPEG1_SAMPLE_RATES[sampleRateIdx]
                                2 -> MPEG2_SAMPLE_RATES[sampleRateIdx]
                                else -> MPEG25_SAMPLE_RATES[sampleRateIdx]
                            }
                        val bitrateKbps =
                            if (versionBits == 3) {
                                MPEG1_L3_BITRATES[bitrateIdx]
                            } else {
                                MPEG2_L3_BITRATES[bitrateIdx]
                            }

                        val samplesPerFrame = if (versionBits == 3) 1152L else 576L

                        // 1. Check for Xing / Info header
                        val xingOffset =
                            if (versionBits == 3) {
                                if (channelMode == 3) 21 else 36
                            } else {
                                if (channelMode == 3) 13 else 21
                            }

                        val xingPos = i + xingOffset
                        if (xingPos + 8 <= bytes.size) {
                            val isXing =
                                bytes[xingPos] == 'X'.code.toByte() &&
                                    bytes[xingPos + 1] == 'i'.code.toByte() &&
                                    bytes[xingPos + 2] == 'n'.code.toByte() &&
                                    bytes[xingPos + 3] == 'g'.code.toByte()
                            val isInfo =
                                bytes[xingPos] == 'I'.code.toByte() &&
                                    bytes[xingPos + 1] == 'n'.code.toByte() &&
                                    bytes[xingPos + 2] == 'f'.code.toByte() &&
                                    bytes[xingPos + 3] == 'o'.code.toByte()

                            if (isXing || isInfo) {
                                val flags = readInt32Be(bytes, xingPos + 4)
                                if ((flags and 0x01L) != 0L && xingPos + 12 <= bytes.size) {
                                    val frameCount = readInt32Be(bytes, xingPos + 8)
                                    if (frameCount > 0L && sampleRate > 0) {
                                        return (frameCount * samplesPerFrame * 1000L) / sampleRate
                                    }
                                }
                            }
                        }

                        // 2. Check for VBRI header
                        val vbriPos = i + 36
                        if (vbriPos + 18 <= bytes.size) {
                            val isVbri =
                                bytes[vbriPos] == 'V'.code.toByte() &&
                                    bytes[vbriPos + 1] == 'B'.code.toByte() &&
                                    bytes[vbriPos + 2] == 'R'.code.toByte() &&
                                    bytes[vbriPos + 3] == 'I'.code.toByte()
                            if (isVbri) {
                                val frameCount = readInt32Be(bytes, vbriPos + 14)
                                if (frameCount > 0L && sampleRate > 0) {
                                    return (frameCount * 1152L * 1000L) / sampleRate
                                }
                            }
                        }

                        // 3. Fallback to CBR bitrate estimation using total fileSize
                        if (fileSize > 0L && bitrateKbps > 0) {
                            val audioSize = (fileSize - i).coerceAtLeast(0L)
                            val durationMs = (audioSize * 8L * 1000L) / (bitrateKbps * 1000L)
                            if (durationMs > 0L) {
                                return durationMs
                            }
                        }
                    }
                }
            }
            i++
        }

        return 0L
    }

    private fun readInt32Be(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        val b0 = bytes[offset].toLong() and 0xFF
        val b1 = bytes[offset + 1].toLong() and 0xFF
        val b2 = bytes[offset + 2].toLong() and 0xFF
        val b3 = bytes[offset + 3].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}
