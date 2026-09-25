package com.webdav.player.data.metadata

import com.webdav.player.domain.model.AudioFormat

object AudioMetadataParser {

    fun parse(bytes: ByteArray, formatHint: AudioFormat? = null): ParsedAudioMetadata {
        if (bytes.size < 4) {
            return ParsedAudioMetadata()
        }

        // If format hint is specified, test that specialized parser first
        when (formatHint) {
            AudioFormat.FLAC -> {
                val flac = FlacParser.parse(bytes)
                if (flac != null && flac.hasTags) return flac
            }
            AudioFormat.WAV -> {
                val wav = WavParser.parse(bytes)
                if (wav != null && wav.hasTags) return wav
            }
            AudioFormat.WMA -> {
                val asf = AsfParser.parse(bytes)
                if (asf != null && asf.hasTags) return asf
            }
            AudioFormat.MP3 -> {
                val id3 = Id3v2Parser.parse(bytes)
                if (id3 != null && id3.hasTags) return id3
            }
            else -> {}
        }

        // 1. Direct ID3 header
        if (bytes.size >= 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val id3 = Id3v2Parser.parse(bytes)
            if (id3 != null) return id3
        }

        // 2. Direct FLAC ("fLaC")
        if (bytes.size >= 4 && bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() && bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()) {
            val flac = FlacParser.parse(bytes)
            if (flac != null) return flac
        }

        // 3. Direct RIFF/WAV ("RIFF")
        if (bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
            val wav = WavParser.parse(bytes)
            if (wav != null) return wav
        }

        // 4. Direct ASF/WMA
        if (bytes.size >= 16 && bytes[0] == 0x30.toByte() && bytes[1] == 0x26.toByte() && bytes[2] == 0xB2.toByte() && bytes[3] == 0x75.toByte()) {
            val asf = AsfParser.parse(bytes)
            if (asf != null) return asf
        }

        // 5. Scan first 4KB for "ID3" tag in case of prepended Xing/Info frame or junk bytes
        val scanLimit = (bytes.size - 10).coerceAtMost(4096)
        for (i in 0 until scanLimit) {
            if (bytes[i] == 'I'.code.toByte() && bytes[i + 1] == 'D'.code.toByte() && bytes[i + 2] == '3'.code.toByte()) {
                val parsed = Id3v2Parser.parse(bytes, offset = i)
                if (parsed != null && parsed.hasTags) {
                    return parsed
                }
            }
        }

        return ParsedAudioMetadata()
    }
}
