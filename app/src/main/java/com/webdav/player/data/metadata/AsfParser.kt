package com.webdav.player.data.metadata

import java.nio.charset.StandardCharsets

object AsfParser {

    private val ASF_HEADER_GUID = byteArrayOf(
        0x30.toByte(), 0x26.toByte(), 0xB2.toByte(), 0x75.toByte(),
        0x8E.toByte(), 0x66.toByte(), 0xCF.toByte(), 0x11.toByte(),
        0xA6.toByte(), 0xD9.toByte(), 0x00.toByte(), 0xAA.toByte(),
        0x00.toByte(), 0x62.toByte(), 0xCE.toByte(), 0x6C.toByte()
    )

    private val FILE_PROPERTIES_GUID = byteArrayOf(
        0xA1.toByte(), 0xDC.toByte(), 0xAB.toByte(), 0x8C.toByte(),
        0x47.toByte(), 0xA9.toByte(), 0xCF.toByte(), 0x11.toByte(),
        0x8E.toByte(), 0xE4.toByte(), 0x00.toByte(), 0xC0.toByte(),
        0x0C.toByte(), 0x20.toByte(), 0x53.toByte(), 0x65.toByte()
    )

    private val CONTENT_DESCRIPTION_GUID = byteArrayOf(
        0x33.toByte(), 0x26.toByte(), 0xB2.toByte(), 0x75.toByte(),
        0x8E.toByte(), 0x66.toByte(), 0xCF.toByte(), 0x11.toByte(),
        0xA6.toByte(), 0xD9.toByte(), 0x00.toByte(), 0xAA.toByte(),
        0x00.toByte(), 0x62.toByte(), 0xCE.toByte(), 0x6C.toByte()
    )

    private val EXTENDED_CONTENT_DESCRIPTION_GUID = byteArrayOf(
        0x40.toByte(), 0xA4.toByte(), 0xD0.toByte(), 0xD2.toByte(),
        0x07.toByte(), 0xE3.toByte(), 0xD2.toByte(), 0x11.toByte(),
        0x97.toByte(), 0xF0.toByte(), 0x00.toByte(), 0xA0.toByte(),
        0xC9.toByte(), 0x5E.toByte(), 0xA8.toByte(), 0x50.toByte()
    )

    fun parse(bytes: ByteArray, offset: Int = 0): ParsedAudioMetadata? {
        val reader = ByteSliceReader(bytes, offset)
        if (!reader.hasRemaining(30)) return null

        val guid = reader.readBytes(16)
        if (!guid.contentEquals(ASF_HEADER_GUID)) {
            return null
        }

        val totalHeaderSize = reader.readLongLe()
        reader.skip(4) // Skip numSubObjects
        reader.skip(2) // Skip 2 reserved bytes

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber: Int? = null
        var durationMs = 0L
        var artworkData: ByteArray? = null
        var artworkMimeType: String? = null
        var lyrics: String? = null

        val headerEnd = (reader.position() + totalHeaderSize.toInt() - 30).coerceAtMost(reader.remaining() + reader.position())

        while (reader.position() + 24 <= headerEnd) {
            val subObjGuid = reader.readBytes(16)
            val subObjSize = reader.readLongLe()
            if (subObjSize < 24) break

            val subObjEnd = (reader.position() + (subObjSize - 24).toInt()).coerceAtMost(reader.remaining() + reader.position())

            when {
                subObjGuid.contentEquals(FILE_PROPERTIES_GUID) -> {
                    // Offset 40 in File Properties is playDuration (100ns units)
                    // We've already read 24 bytes header, so 16 bytes more to reach offset 40
                    if (reader.hasRemaining(48)) {
                        reader.skip(16) // skip file id (16)
                        reader.skip(8)  // skip file size (8)
                        reader.skip(8)  // skip creation time (8)
                        reader.skip(8)  // skip data packets count (8)
                        val playDuration100ns = reader.readLongLe()
                        if (playDuration100ns > 0) {
                            durationMs = playDuration100ns / 10000L
                        }
                    }
                }
                subObjGuid.contentEquals(CONTENT_DESCRIPTION_GUID) -> {
                    if (reader.hasRemaining(10)) {
                        val titleLen = reader.readShortLe()
                        val authorLen = reader.readShortLe()
                        reader.skip(6) // Skip copyrightLen (2), descLen (2), ratingLen (2)

                        if (titleLen > 0 && reader.hasRemaining(titleLen)) {
                            val rawTitle = reader.readString(titleLen, StandardCharsets.UTF_16LE)
                            if (rawTitle.isNotBlank()) title = rawTitle
                        }
                        if (authorLen > 0 && reader.hasRemaining(authorLen)) {
                            val rawAuthor = reader.readString(authorLen, StandardCharsets.UTF_16LE)
                            if (rawAuthor.isNotBlank()) artist = rawAuthor
                        }
                    }
                }
                subObjGuid.contentEquals(EXTENDED_CONTENT_DESCRIPTION_GUID) -> {
                    if (reader.hasRemaining(2)) {
                        val descriptorCount = reader.readShortLe().coerceIn(0, 1000)
                        for (i in 0 until descriptorCount) {
                            if (!reader.hasRemaining(4)) break
                            val nameLen = reader.readShortLe()
                            val name = reader.readString(nameLen, StandardCharsets.UTF_16LE)
                            val valType = reader.readShortLe()
                            val valLen = reader.readShortLe().coerceIn(0, reader.remaining())

                            when (name) {
                                "WM/AlbumTitle" -> {
                                    if (album == null && valType == 0) {
                                        album = reader.readString(valLen, StandardCharsets.UTF_16LE)
                                    } else {
                                        reader.skip(valLen)
                                    }
                                }
                                "WM/TrackNumber" -> {
                                    if (trackNumber == null) {
                                        when (valType) {
                                            0 -> { // Unicode string
                                                val str = reader.readString(valLen, StandardCharsets.UTF_16LE)
                                                trackNumber = str.takeWhile { it.isDigit() }.toIntOrNull()
                                            }
                                            3 -> { // DWORD
                                                trackNumber = reader.readIntLe()
                                                reader.skip((valLen - 4).coerceAtLeast(0))
                                            }
                                            5 -> { // WORD
                                                trackNumber = reader.readShortLe()
                                                reader.skip((valLen - 2).coerceAtLeast(0))
                                            }
                                            else -> reader.skip(valLen)
                                        }
                                    } else {
                                        reader.skip(valLen)
                                    }
                                }
                                "WM/Picture" -> {
                                    if (artworkData == null && valType == 1 && valLen > 5) {
                                        val picReader = ByteSliceReader(reader.readBytes(valLen))
                                        picReader.skip(1) // Skip picType
                                        val dataLen = picReader.readIntLe()
                                        val mime = picReader.readNullTerminatedString(StandardCharsets.UTF_16LE)
                                        picReader.readNullTerminatedString(StandardCharsets.UTF_16LE) // Skip desc
                                        if (dataLen > 0 && picReader.hasRemaining(dataLen)) {
                                            artworkData = picReader.readBytes(dataLen)
                                            artworkMimeType = if (mime.isNotBlank()) mime else "image/jpeg"
                                        }
                                    } else {
                                        reader.skip(valLen)
                                    }
                                }
                                "WM/Lyrics", "WM/Lyrics_Synchronised" -> {
                                    if (lyrics == null && valType == 0) {
                                        lyrics = reader.readString(valLen, StandardCharsets.UTF_16LE).takeIf { it.isNotBlank() }
                                    } else {
                                        reader.skip(valLen)
                                    }
                                }
                                else -> {
                                    reader.skip(valLen)
                                }
                            }
                        }
                    }
                }
            }

            if (reader.position() < subObjEnd) {
                reader.seek(subObjEnd)
            }
        }

        return ParsedAudioMetadata(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            durationMs = durationMs,
            artworkData = artworkData,
            artworkMimeType = artworkMimeType,
            lyrics = lyrics
        )
    }
}
