package com.webdav.player.data.metadata

object ImageHeaderValidator {
    fun isRecognizedImage(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false
        val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val isPng =
            bytes.size >= 4 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val isWebp =
            bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        return isJpeg || isPng || isWebp
    }

    fun isCompleteImage(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false

        // JPEG: starts with SOI (0xFF, 0xD8), ends with EOI (0xFF, 0xD9)
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            if (bytes.size < 32) return true
            val scanStart = (bytes.size - 4096).coerceAtLeast(2)
            for (i in bytes.size - 2 downTo scanStart) {
                if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
                    return true
                }
            }
            return false
        }

        // PNG: starts with 89 50 4E 47, ends with IEND chunk (49 45 4E 44)
        val isPng =
            bytes.size >= 4 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        if (isPng) {
            if (bytes.size < 32) return true
            val scanStart = (bytes.size - 1024).coerceAtLeast(4)
            for (i in bytes.size - 4 downTo scanStart) {
                if (bytes[i] == 'I'.code.toByte() &&
                    bytes[i + 1] == 'E'.code.toByte() &&
                    bytes[i + 2] == 'N'.code.toByte() &&
                    bytes[i + 3] == 'D'.code.toByte()
                ) {
                    return true
                }
            }
            return false
        }

        // WebP: starts with RIFF, bytes 8..11 are WEBP
        val isWebp =
            bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        if (isWebp) {
            val riffPayload =
                (bytes[4].toLong() and 0xFFL) or
                    ((bytes[5].toLong() and 0xFFL) shl 8) or
                    ((bytes[6].toLong() and 0xFFL) shl 16) or
                    ((bytes[7].toLong() and 0xFFL) shl 24)
            return bytes.size >= (8L + riffPayload)
        }

        return false
    }
}
