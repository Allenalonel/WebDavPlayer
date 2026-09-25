package com.webdav.player.data.metadata

import java.nio.charset.StandardCharsets

object FlacParser {

    fun parse(bytes: ByteArray, offset: Int = 0): ParsedAudioMetadata? {
        val reader = ByteSliceReader(bytes, offset)
        if (!reader.hasRemaining(4)) return null

        val b0 = reader.readUByte()
        val b1 = reader.readUByte()
        val b2 = reader.readUByte()
        val b3 = reader.readUByte()

        if (b0 != 0x66 || b1 != 0x4C || b2 != 0x61 || b3 != 0x43) { // "fLaC"
            return null
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber: Int? = null
        var durationMs = 0L
        var artworkData: ByteArray? = null
        var artworkMimeType: String? = null

        var isLast = false

        while (!isLast && reader.hasRemaining(4)) {
            val headerByte = reader.readUByte()
            isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F
            val blockLength = reader.read24BitBe()

            val blockEnd = (reader.position() + blockLength).coerceAtMost(reader.position() + reader.remaining())

            when (blockType) {
                0 -> { // STREAMINFO
                    if (blockLength >= 18 && reader.hasRemaining(18)) {
                        // Skip min/max blocksize (4 bytes) and min/max framesize (6 bytes)
                        reader.skip(10)
                        val b10 = reader.readUByte()
                        val b11 = reader.readUByte()
                        val b12 = reader.readUByte()
                        val b13 = reader.readUByte()
                        val b14 = reader.readUByte()
                        val b15 = reader.readUByte()
                        val b16 = reader.readUByte()
                        val b17 = reader.readUByte()

                        val sampleRate = (b10 shl 12) or (b11 shl 4) or ((b12 and 0xF0) ushr 4)
                        val totalSamples = (((b13.toLong() and 0x0F) shl 32)
                                or ((b14.toLong() and 0xFF) shl 24)
                                or ((b15.toLong() and 0xFF) shl 16)
                                or ((b16.toLong() and 0xFF) shl 8)
                                or (b17.toLong() and 0xFF))

                        if (sampleRate > 0 && totalSamples > 0L) {
                            durationMs = (totalSamples * 1000L) / sampleRate
                        }
                    }
                }
                4 -> { // VORBIS_COMMENT
                    val vendorLength = reader.readUIntLe().toInt().coerceAtLeast(0)
                    if (reader.hasRemaining(vendorLength)) {
                        reader.skip(vendorLength)
                    }
                    val commentCount = reader.readUIntLe().toInt().coerceIn(0, 1000)
                    for (i in 0 until commentCount) {
                        if (!reader.hasRemaining(4)) break
                        val commentLen = reader.readUIntLe().toInt().coerceIn(0, reader.remaining())
                        val comment = reader.readString(commentLen, StandardCharsets.UTF_8)
                        val eqIndex = comment.indexOf('=')
                        if (eqIndex > 0) {
                            val key = comment.substring(0, eqIndex).trim().uppercase()
                            val value = comment.substring(eqIndex + 1).trim()
                            when (key) {
                                "TITLE" -> if (title == null) title = value
                                "ARTIST" -> if (artist == null) artist = value
                                "ALBUM" -> if (album == null) album = value
                                "TRACKNUMBER", "TRACK" -> if (trackNumber == null) {
                                    val digits = value.takeWhile { it.isDigit() }
                                    trackNumber = digits.toIntOrNull()
                                }
                            }
                        }
                    }
                }
                6 -> { // PICTURE
                    if (artworkData == null && reader.hasRemaining(32)) {
                        reader.skip(4) // Skip pictureType (4 bytes)
                        val mimeLength = reader.readIntBe().coerceIn(0, reader.remaining())
                        val mime = reader.readString(mimeLength, StandardCharsets.US_ASCII)
                        val descLength = reader.readIntBe().coerceIn(0, reader.remaining())
                        reader.skip(descLength) // skip description
                        reader.skip(16) // skip width, height, depth, colors (4 * 4 = 16 bytes)
                        val dataLength = reader.readIntBe().coerceIn(0, reader.remaining())
                        if (dataLength > 0 && reader.hasRemaining(dataLength)) {
                            artworkData = reader.readBytes(dataLength)
                            artworkMimeType = if (mime.isNotBlank()) mime else "image/jpeg"
                        }
                    }
                }
            }

            // Move to end of current block
            if (reader.position() < blockEnd) {
                reader.seek(blockEnd)
            }
        }

        return ParsedAudioMetadata(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            durationMs = durationMs,
            artworkData = artworkData,
            artworkMimeType = artworkMimeType
        )
    }
}
