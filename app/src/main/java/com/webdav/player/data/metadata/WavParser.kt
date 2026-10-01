package com.webdav.player.data.metadata

import java.nio.charset.StandardCharsets

object WavParser {

    fun parse(bytes: ByteArray, offset: Int = 0): ParsedAudioMetadata? {
        val reader = ByteSliceReader(bytes, offset)
        if (!reader.hasRemaining(12)) return null

        val riff = reader.readAsciiId(4)
        if (riff != "RIFF") return null

        reader.skip(4) // Skip RIFF size
        val wave = reader.readAsciiId(4)
        if (wave != "WAVE") return null

        var byteRate = 0L
        var dataSize = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber: Int? = null
        var artworkData: ByteArray? = null
        var artworkMimeType: String? = null
        var lyrics: String? = null

        while (reader.hasRemaining(8)) {
            val chunkId = reader.readAsciiId(4)
            val chunkSize = reader.readUIntLe()
            val chunkEnd = (reader.position() + chunkSize.toInt()).coerceAtMost(reader.position() + reader.remaining())

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= 16 && reader.hasRemaining(16)) {
                        reader.skip(8) // skip format (2), channels (2), sampleRate (4)
                        byteRate = reader.readUIntLe()
                    }
                }
                "data" -> {
                    dataSize = chunkSize
                }
                "LIST" -> {
                    if (reader.hasRemaining(4)) {
                        val listType = reader.readAsciiId(4)
                        if (listType == "INFO") {
                            while (reader.position() + 8 <= chunkEnd) {
                                val infoId = reader.readAsciiId(4)
                                val infoSize = reader.readUIntLe().toInt().coerceIn(0, (chunkEnd - reader.position()).coerceAtLeast(0))
                                val value = reader.readString(infoSize, StandardCharsets.UTF_8)
                                // Word-aligned padding: if infoSize is odd, 1 padding byte
                                if ((infoSize and 1) != 0 && reader.position() < chunkEnd) {
                                    reader.skip(1)
                                }
                                when (infoId) {
                                    "INAM" -> if (title == null) title = value
                                    "IART" -> if (artist == null) artist = value
                                    "IPRD" -> if (album == null) album = value
                                    "ITRK" -> if (trackNumber == null) {
                                        val digits = value.takeWhile { it.isDigit() }
                                        trackNumber = digits.toIntOrNull()
                                    }
                                }
                            }
                        }
                    }
                }
                "id3 ", "ID3 " -> {
                    if (chunkSize > 0 && reader.hasRemaining(chunkSize.toInt())) {
                        val id3Data = reader.readBytes(chunkSize.toInt())
                        val parsedId3 = Id3v2Parser.parse(id3Data)
                        if (parsedId3 != null) {
                            if (title == null) title = parsedId3.title
                            if (artist == null) artist = parsedId3.artist
                            if (album == null) album = parsedId3.album
                            if (trackNumber == null) trackNumber = parsedId3.trackNumber
                            if (artworkData == null) {
                                artworkData = parsedId3.artworkData
                                artworkMimeType = parsedId3.artworkMimeType
                            }
                            if (lyrics == null) lyrics = parsedId3.lyrics
                        }
                    } else {
                        reader.skip((chunkEnd - reader.position()).coerceAtLeast(0))
                    }
                }
            }

            // Move to chunkEnd with 2-byte word padding
            val paddedEnd = if ((chunkSize and 1L) != 0L) chunkEnd + 1 else chunkEnd
            if (reader.position() < paddedEnd) {
                reader.seek(paddedEnd)
            }
        }

        val durationMs = if (byteRate > 0 && dataSize > 0) {
            (dataSize * 1000L) / byteRate
        } else {
            0L
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
