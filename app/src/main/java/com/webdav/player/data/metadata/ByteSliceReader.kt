package com.webdav.player.data.metadata

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ByteSliceReader(
    private val bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size
) {
    private val start = offset.coerceIn(0, bytes.size)
    private val limit = (offset + length).coerceIn(start, bytes.size)
    private var pos = start

    fun position(): Int = pos - start
    fun absolutePosition(): Int = pos

    fun remaining(): Int = (limit - pos).coerceAtLeast(0)

    fun hasRemaining(n: Int = 1): Boolean = remaining() >= n

    fun seek(relativePosition: Int) {
        pos = (start + relativePosition).coerceIn(start, limit)
    }

    fun skip(n: Int) {
        pos = (pos + n).coerceIn(start, limit)
    }

    fun readUByte(): Int {
        if (!hasRemaining(1)) return -1
        return bytes[pos++].toInt() and 0xFF
    }

    fun readByte(): Byte {
        if (!hasRemaining(1)) return 0
        return bytes[pos++]
    }

    fun peekUByte(): Int {
        if (!hasRemaining(1)) return -1
        return bytes[pos].toInt() and 0xFF
    }

    fun readShortBe(): Int {
        if (!hasRemaining(2)) return 0
        val b0 = readUByte()
        val b1 = readUByte()
        return (b0 shl 8) or b1
    }

    fun readShortLe(): Int {
        if (!hasRemaining(2)) return 0
        val b0 = readUByte()
        val b1 = readUByte()
        return (b1 shl 8) or b0
    }

    fun read24BitBe(): Int {
        if (!hasRemaining(3)) return 0
        val b0 = readUByte()
        val b1 = readUByte()
        val b2 = readUByte()
        return (b0 shl 16) or (b1 shl 8) or b2
    }

    fun readIntBe(): Int {
        if (!hasRemaining(4)) return 0
        val b0 = readUByte()
        val b1 = readUByte()
        val b2 = readUByte()
        val b3 = readUByte()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readIntLe(): Int {
        if (!hasRemaining(4)) return 0
        val b0 = readUByte()
        val b1 = readUByte()
        val b2 = readUByte()
        val b3 = readUByte()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    fun readUIntLe(): Long {
        return readIntLe().toLong() and 0xFFFFFFFFL
    }

    fun readUIntBe(): Long {
        return readIntBe().toLong() and 0xFFFFFFFFL
    }

    fun readLongLe(): Long {
        if (!hasRemaining(8)) return 0L
        val low = readUIntLe()
        val high = readUIntLe()
        return (high shl 32) or low
    }

    fun readSynchsafeInt(): Int {
        if (!hasRemaining(4)) return 0
        val b0 = readUByte() and 0x7F
        val b1 = readUByte() and 0x7F
        val b2 = readUByte() and 0x7F
        val b3 = readUByte() and 0x7F
        return (b0 shl 21) or (b1 shl 14) or (b2 shl 7) or b3
    }

    fun readBytes(length: Int): ByteArray {
        val count = length.coerceIn(0, remaining())
        val result = ByteArray(count)
        System.arraycopy(bytes, pos, result, 0, count)
        pos += count
        return result
    }

    fun readAsciiId(length: Int = 4): String {
        val data = readBytes(length)
        return String(data, StandardCharsets.US_ASCII)
    }

    fun readString(length: Int, charset: Charset = StandardCharsets.UTF_8): String {
        val data = readBytes(length)
        return String(data, charset).trimEnd('\u0000')
    }

    fun readNullTerminatedString(charset: Charset = StandardCharsets.ISO_8859_1): String {
        val isUtf16 = charset == StandardCharsets.UTF_16 ||
                charset == StandardCharsets.UTF_16LE ||
                charset == StandardCharsets.UTF_16BE

        val startPos = pos
        if (isUtf16) {
            while (pos + 1 < limit) {
                if (bytes[pos] == 0.toByte() && bytes[pos + 1] == 0.toByte()) {
                    val length = pos - startPos
                    val str = String(bytes, startPos, length, charset)
                    pos += 2 // Skip 2 null bytes
                    return str.trimEnd('\u0000', ' ')
                }
                pos += 2
            }
        } else {
            while (pos < limit) {
                if (bytes[pos] == 0.toByte()) {
                    val length = pos - startPos
                    val str = String(bytes, startPos, length, charset)
                    pos += 1 // Skip null byte
                    return str.trimEnd('\u0000', ' ')
                }
                pos++
            }
        }

        // If no null terminator found, take remainder
        val length = limit - startPos
        pos = limit
        return String(bytes, startPos, length, charset).trimEnd('\u0000', ' ')
    }
}
