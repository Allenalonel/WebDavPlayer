package com.webdav.player.data.metadata

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object Id3v2Parser {

    fun parse(bytes: ByteArray, offset: Int = 0): ParsedAudioMetadata? {
        val reader = ByteSliceReader(bytes, offset)
        if (!reader.hasRemaining(10)) return null

        val b0 = reader.readUByte()
        val b1 = reader.readUByte()
        val b2 = reader.readUByte()

        if (b0 != 'I'.code || b1 != 'D'.code || b2 != '3'.code) {
            return null
        }

        val majorVersion = reader.readUByte()
        reader.skip(1) // Skip revision byte
        val flags = reader.readUByte()
        val tagSize = reader.readSynchsafeInt()

        if (majorVersion !in 2..4 || tagSize <= 0) {
            return null
        }

        val tagEndPos = reader.position() + tagSize

        // Handle extended header if present
        val hasExtendedHeader = (flags and 0x40) != 0
        if (hasExtendedHeader) {
            if (majorVersion == 3) {
                val extSize = reader.readIntBe()
                reader.skip(extSize)
            } else if (majorVersion == 4) {
                val extSize = reader.readSynchsafeInt()
                reader.skip((extSize - 4).coerceAtLeast(0))
            }
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber: Int? = null
        var durationMs = 0L
        var artworkData: ByteArray? = null
        var artworkMimeType: String? = null

        while (reader.hasRemaining(if (majorVersion == 2) 6 else 10) && reader.position() < tagEndPos) {
            val (frameId, frameSize) = if (majorVersion == 2) {
                val id = reader.readAsciiId(3)
                val size = reader.read24BitBe()
                id to size
            } else {
                val id = reader.readAsciiId(4)
                val size = if (majorVersion == 4) {
                    reader.readSynchsafeInt()
                } else {
                    reader.readIntBe()
                }
                reader.skip(2) // Skip 2 flags bytes
                id to size
            }

            // Check if padding or invalid frame
            if (frameId.isBlank() || frameId.startsWith('\u0000') || frameSize <= 0) {
                break
            }

            val frameLimit = (reader.position() + frameSize).coerceAtMost(tagEndPos)
            val actualPayloadSize = (frameLimit - reader.position()).coerceAtLeast(0)

            when (frameId) {
                "TIT2", "TT2" -> {
                    if (title == null) {
                        title = readTextFrame(reader, actualPayloadSize)
                    } else {
                        reader.skip(actualPayloadSize)
                    }
                }
                "TPE1", "TP1" -> {
                    if (artist == null) {
                        artist = readTextFrame(reader, actualPayloadSize)
                    } else {
                        reader.skip(actualPayloadSize)
                    }
                }
                "TALB", "TAL" -> {
                    if (album == null) {
                        album = readTextFrame(reader, actualPayloadSize)
                    } else {
                        reader.skip(actualPayloadSize)
                    }
                }
                "TRCK", "TRK" -> {
                    if (trackNumber == null) {
                        val text = readTextFrame(reader, actualPayloadSize)
                        trackNumber = parseTrackNumber(text)
                    } else {
                        reader.skip(actualPayloadSize)
                    }
                }
                "TLEN", "TLE" -> {
                    if (durationMs == 0L) {
                        val text = readTextFrame(reader, actualPayloadSize)
                        durationMs = text.toLongOrNull() ?: 0L
                    } else {
                        reader.skip(actualPayloadSize)
                    }
                }
                "APIC" -> {
                    if (artworkData == null) {
                        val (data, mime) = readApicFrame(reader, actualPayloadSize)
                        if (data != null) {
                            artworkData = data
                            artworkMimeType = mime
                        }
                    } else {
                        reader.skip(actualPayloadSize)
                    }
                }
                "PIC" -> {
                    if (artworkData == null) {
                        val (data, mime) = readPicFrame(reader, actualPayloadSize)
                        if (data != null) {
                            artworkData = data
                            artworkMimeType = mime
                        }
                    } else {
                        reader.skip(actualPayloadSize)
                    }
                }
                else -> {
                    reader.skip(actualPayloadSize)
                }
            }

            // Ensure reader is at frameLimit
            if (reader.position() < frameLimit) {
                reader.seek(frameLimit)
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

    private fun readTextFrame(reader: ByteSliceReader, size: Int): String {
        if (size <= 1) {
            reader.skip(size)
            return ""
        }
        val encodingByte = reader.readUByte()
        val textLength = size - 1
        val charset = charsetForEncoding(encodingByte)
        return reader.readString(textLength, charset)
    }

    private fun parseTrackNumber(raw: String): Int? {
        val clean = raw.trim().takeWhile { it.isDigit() }
        return clean.toIntOrNull()
    }

    private fun charsetForEncoding(encodingByte: Int): Charset = when (encodingByte) {
        0 -> StandardCharsets.ISO_8859_1
        1 -> StandardCharsets.UTF_16
        2 -> StandardCharsets.UTF_16BE
        3 -> StandardCharsets.UTF_8
        else -> StandardCharsets.ISO_8859_1
    }

    private fun readApicFrame(reader: ByteSliceReader, size: Int): Pair<ByteArray?, String?> {
        val startPos = reader.position()
        val endPos = startPos + size
        if (size <= 5) {
            reader.skip(size)
            return null to null
        }

        val encodingByte = reader.readUByte()
        val mimeType = reader.readNullTerminatedString(StandardCharsets.ISO_8859_1)
        reader.skip(1) // Skip pictureType byte
        val descCharset = charsetForEncoding(encodingByte)
        reader.readNullTerminatedString(descCharset) // Skip description string

        val remainingSize = (endPos - reader.position()).coerceAtLeast(0)
        if (remainingSize <= 0) {
            return null to null
        }

        val imageBytes = reader.readBytes(remainingSize)
        val normalizedMime = when {
            mimeType.isNotBlank() && mimeType != "-->" -> mimeType
            imageBytes.size >= 3 && imageBytes[0] == 0xFF.toByte() && imageBytes[1] == 0xD8.toByte() -> "image/jpeg"
            imageBytes.size >= 8 && imageBytes[0] == 0x89.toByte() && imageBytes[1] == 0x50.toByte() -> "image/png"
            else -> "image/jpeg"
        }

        return imageBytes to normalizedMime
    }

    private fun readPicFrame(reader: ByteSliceReader, size: Int): Pair<ByteArray?, String?> {
        val startPos = reader.position()
        val endPos = startPos + size
        if (size <= 5) {
            reader.skip(size)
            return null to null
        }

        val encodingByte = reader.readUByte()
        val format = reader.readString(3, StandardCharsets.US_ASCII)
        reader.skip(1) // Skip pictureType byte
        val descCharset = charsetForEncoding(encodingByte)
        reader.readNullTerminatedString(descCharset) // Skip description string

        val remainingSize = (endPos - reader.position()).coerceAtLeast(0)
        if (remainingSize <= 0) {
            return null to null
        }

        val imageBytes = reader.readBytes(remainingSize)
        val mime = if (format.equals("PNG", ignoreCase = true)) "image/png" else "image/jpeg"
        return imageBytes to mime
    }
}
