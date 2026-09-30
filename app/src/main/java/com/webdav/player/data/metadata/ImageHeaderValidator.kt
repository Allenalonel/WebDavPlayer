package com.webdav.player.data.metadata

object ImageHeaderValidator {
    fun isCompleteImage(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false

        // JPEG: starts with SOI (0xFF, 0xD8), ends with EOI (0xFF, 0xD9)
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            if (bytes.size < 32) return true
            val scanStart = (bytes.size - 256).coerceAtLeast(2)
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
            val scanStart = (bytes.size - 64).coerceAtLeast(4)
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

        return false
    }
}
