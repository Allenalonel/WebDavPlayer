package com.webdav.player.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpegAudioFrameParserTest {
    @Test
    fun parseDuration_emptyOrSmallBytes_returnsZero() {
        assertEquals(0L, MpegAudioFrameParser.parseDuration(ByteArray(0)))
        assertEquals(0L, MpegAudioFrameParser.parseDuration(ByteArray(3)))
    }

    @Test
    fun parseDuration_cbrMpeg1Layer3_calculatesAccurateDuration() {
        // Construct a synthetic MPEG 1 Layer 3 frame header:
        // Byte 0: 0xFF
        // Byte 1: 0xFB (sync 0xE0, version 11 [MPEG 1], layer 01 [Layer III], no CRC 1)
        // Byte 2: 0x90 (bitrate index 1001 = 9 -> 128 kbps, sample rate 00 -> 44100 Hz, no padding)
        // Byte 3: 0x00 (Stereo)
        val frameBytes = ByteArray(1024)
        frameBytes[0] = 0xFF.toByte()
        frameBytes[1] = 0xFB.toByte()
        frameBytes[2] = 0x90.toByte()
        frameBytes[3] = 0x00.toByte()

        // 128 kbps = 16,000 bytes per second
        // If file size is 160,000 bytes, expected duration is 10,000 ms (10 seconds)
        val duration = MpegAudioFrameParser.parseDuration(frameBytes, offset = 0, fileSize = 160000L)
        assertEquals(10000L, duration)
    }

    @Test
    fun parseDuration_xingHeader_calculatesDurationFromFrameCount() {
        val frameBytes = ByteArray(1024)
        // MPEG 1 Layer 3, 44100 Hz, Stereo
        frameBytes[0] = 0xFF.toByte()
        frameBytes[1] = 0xFB.toByte()
        frameBytes[2] = 0x90.toByte()
        frameBytes[3] = 0x00.toByte()

        // Xing offset for stereo is 36
        val xingOffset = 36
        frameBytes[xingOffset] = 'X'.code.toByte()
        frameBytes[xingOffset + 1] = 'i'.code.toByte()
        frameBytes[xingOffset + 2] = 'n'.code.toByte()
        frameBytes[xingOffset + 3] = 'g'.code.toByte()

        // Flags = 0x01 (frames field present)
        frameBytes[xingOffset + 7] = 0x01.toByte()

        // Frame count = 100 frames
        // In MPEG 1 Layer 3: 100 * 1152 samples = 115200 samples
        // 115200 * 1000 / 44100 = 2612 ms
        frameBytes[xingOffset + 11] = 100.toByte()

        val duration = MpegAudioFrameParser.parseDuration(frameBytes, offset = 0, fileSize = 0L)
        assertEquals(2612L, duration)
    }
}
