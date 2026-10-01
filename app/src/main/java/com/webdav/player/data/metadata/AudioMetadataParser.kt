package com.webdav.player.data.metadata

import com.webdav.player.domain.model.AudioFormat

object AudioMetadataParser {
    fun parse(
        bytes: ByteArray,
        formatHint: AudioFormat? = null,
    ): ParsedAudioMetadata {
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
        if (isId3At(bytes, 0)) {
            val id3 = Id3v2Parser.parse(bytes)
            if (id3 != null) return id3
        }

        // 2. Direct FLAC ("fLaC")
        if (isFlacAt(bytes, 0)) {
            val flac = FlacParser.parse(bytes)
            if (flac != null) return flac
        }

        // 3. Direct RIFF/WAV ("RIFF")
        if (bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            val wav = WavParser.parse(bytes)
            if (wav != null) return wav
        }

        // 4. Direct ASF/WMA
        if (isAsfAt(bytes, 0)) {
            val asf = AsfParser.parse(bytes)
            if (asf != null) return asf
        }

        // 5. Scan first 4KB for "ID3" tag in case of prepended Xing/Info frame or junk bytes
        val id3Offset = findId3HeaderOffset(bytes)
        if (id3Offset != null && id3Offset > 0) {
            val parsed = Id3v2Parser.parse(bytes, offset = id3Offset)
            if (parsed != null && parsed.hasTags) {
                return parsed
            }
        }

        return ParsedAudioMetadata()
    }

    fun hasRecognizedAudioHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val isId3 = isId3At(bytes, 0) || findId3HeaderOffset(bytes) != null
        val isFlac = isFlacAt(bytes, 0)
        return isId3 || isFlac
    }

    fun detectRequiredTagSize(bytes: ByteArray): Long? {
        val id3Offset = findId3HeaderOffset(bytes)
        if (id3Offset != null) {
            val size = extractId3TagSize(bytes, id3Offset)
            if (size != null) return size
        }

        if (isFlacAt(bytes, 0)) {
            return extractFlacTagSize(bytes)
        }

        if (isAsfAt(bytes, 0)) {
            return extractAsfTagSize(bytes)
        }

        return null
    }

    private fun findId3HeaderOffset(
        bytes: ByteArray,
        maxScanLimit: Int = 4096,
    ): Int? {
        if (bytes.size < 10) return null
        if (isId3At(bytes, 0)) return 0
        val scanLimit = (bytes.size - 10).coerceAtMost(maxScanLimit)
        for (i in 1..scanLimit) {
            if (isId3At(bytes, i)) return i
        }
        return null
    }

    private fun isId3At(
        bytes: ByteArray,
        offset: Int,
    ): Boolean =
        bytes.size >= offset + 3 &&
            bytes[offset] == 'I'.code.toByte() &&
            bytes[offset + 1] == 'D'.code.toByte() &&
            bytes[offset + 2] == '3'.code.toByte()

    private fun isFlacAt(
        bytes: ByteArray,
        offset: Int = 0,
    ): Boolean =
        bytes.size >= offset + 4 &&
            bytes[offset] == 0x66.toByte() &&
            bytes[offset + 1] == 0x4C.toByte() &&
            bytes[offset + 2] == 0x61.toByte() &&
            bytes[offset + 3] == 0x43.toByte()

    private fun isAsfAt(
        bytes: ByteArray,
        offset: Int = 0,
    ): Boolean =
        bytes.size >= offset + 16 &&
            bytes[offset] == 0x30.toByte() &&
            bytes[offset + 1] == 0x26.toByte() &&
            bytes[offset + 2] == 0xB2.toByte() &&
            bytes[offset + 3] == 0x75.toByte()

    private fun extractId3TagSize(
        bytes: ByteArray,
        offset: Int,
    ): Long? {
        if (offset + 10 > bytes.size) return null
        val majorVersion = bytes[offset + 3].toInt() and 0xFF
        if (majorVersion !in 2..4) return null

        val flags = bytes[offset + 5].toInt() and 0xFF
        val b6 = bytes[offset + 6].toInt() and 0x7F
        val b7 = bytes[offset + 7].toInt() and 0x7F
        val b8 = bytes[offset + 8].toInt() and 0x7F
        val b9 = bytes[offset + 9].toInt() and 0x7F
        val tagPayloadSize = (b6 shl 21) or (b7 shl 14) or (b8 shl 7) or b9
        if (tagPayloadSize <= 0) return null

        val hasFooter = (majorVersion == 4) && ((flags and 0x10) != 0)
        return offset.toLong() + 10L + tagPayloadSize.toLong() + (if (hasFooter) 10L else 0L)
    }

    private fun extractFlacTagSize(bytes: ByteArray): Long? {
        var pos = 4
        var isLast = false
        while (pos + 4 <= bytes.size && !isLast) {
            val headerByte = bytes[pos].toInt() and 0xFF
            isLast = (headerByte and 0x80) != 0
            val blockLength =
                ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)
            pos += 4 + blockLength
            if (pos > bytes.size) {
                return pos.toLong()
            }
        }
        if (!isLast) {
            return bytes.size + 1048576L // Increment by 1MB to accommodate large picture blocks
        }
        return null
    }

    private fun extractAsfTagSize(bytes: ByteArray): Long? {
        if (bytes.size < 24) return null
        val totalHeaderSize =
            (bytes[16].toLong() and 0xFFL) or
                ((bytes[17].toLong() and 0xFFL) shl 8) or
                ((bytes[18].toLong() and 0xFFL) shl 16) or
                ((bytes[19].toLong() and 0xFFL) shl 24) or
                ((bytes[20].toLong() and 0xFFL) shl 32) or
                ((bytes[21].toLong() and 0xFFL) shl 40) or
                ((bytes[22].toLong() and 0xFFL) shl 48) or
                ((bytes[23].toLong() and 0xFFL) shl 56)
        return if (totalHeaderSize in 30L..16_777_216L) totalHeaderSize else null
    }
}
