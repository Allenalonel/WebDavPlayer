package com.webdav.player.data.lyrics

import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics

object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d+):(\d{2})(?:[.:](\d+))?\]""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:[^\]]*\]$""")

    fun parse(content: String): Lyrics {
        if (content.isBlank()) {
            return Lyrics.EMPTY
        }

        val lines = content.lines()
        var offsetMs = 0L

        // First pass: extract offset if present
        for (line in lines) {
            val offsetMatch = OFFSET_REGEX.find(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                break
            }
        }

        val parsedLines = mutableListOf<LyricLine>()
        val fallbackLines = mutableListOf<String>()
        var foundAnyTimestamp = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            // Check if metadata tag like [ti:...], [ar:...]
            if (METADATA_TAG_REGEX.matches(line)) {
                continue
            }

            val timestampMatches = TIMESTAMP_REGEX.findAll(line).toList()
            if (timestampMatches.isNotEmpty()) {
                foundAnyTimestamp = true
                // Remove all timestamp tags to extract the line's lyric text
                val text = TIMESTAMP_REGEX.replace(line, "").trim()

                for (match in timestampMatches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3]

                    val millis = parseFractionToMillis(fractionStr)
                    val rawTimestampMs = minutes * 60_000L + seconds * 1_000L + millis
                    val adjustedTimestampMs = (rawTimestampMs + offsetMs).coerceAtLeast(0L)

                    parsedLines.add(LyricLine(timestampMs = adjustedTimestampMs, text = text))
                }
            } else {
                // Potential fallback line if no timestamps are present in the entire document
                fallbackLines.add(line)
            }
        }

        return when {
            foundAnyTimestamp -> {
                val sorted = parsedLines.sortedBy { it.timestampMs }
                Lyrics(lines = sorted, isSynchronized = true)
            }
            fallbackLines.isNotEmpty() -> {
                val unsynced = fallbackLines.map { LyricLine(timestampMs = 0L, text = it) }
                Lyrics(lines = unsynced, isSynchronized = false)
            }
            else -> Lyrics.EMPTY
        }
    }

    private fun parseFractionToMillis(fractionStr: String): Long {
        if (fractionStr.isEmpty()) return 0L
        return when (fractionStr.length) {
            1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
            3 -> fractionStr.toLongOrNull() ?: 0L
            else -> fractionStr.take(3).toLongOrNull() ?: 0L
        }
    }
}
