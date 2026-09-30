package com.webdav.player.data.lyrics

import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics

object LrcParser {

    private const val NEARBY_TIMESTAMP_WINDOW_MS = 300L
    private val TIMESTAMP_REGEX = Regex("""\[(\d+):(\d{2})(?:[.:](\d+))?\]""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:[^\]]*\]$""")
    private val INLINE_TIMESTAMP_REGEX = Regex("""\[(?:\d+:)?\d{2}(?:[.:]\d+)?\]|<(?:\d+:)?\d{2}(?:[.:]\d+)?>""")

    private data class RawEntry(
        val timestampMs: Long,
        val text: String,
        val originalOrder: Int
    )

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

        val rawEntries = mutableListOf<RawEntry>()
        val fallbackLines = mutableListOf<String>()
        var foundAnyTimestamp = false

        for ((lineIndex, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            // Check if metadata tag like [ti:...], [ar:...]
            if (METADATA_TAG_REGEX.matches(line)) {
                continue
            }

            // Extract all leading timestamps
            var currentIndex = 0
            val leadingTimestamps = mutableListOf<Long>()

            while (currentIndex < line.length) {
                while (currentIndex < line.length && line[currentIndex].isWhitespace()) {
                    currentIndex++
                }
                if (currentIndex >= line.length) break

                val match = TIMESTAMP_REGEX.find(line, currentIndex)
                if (match != null && match.range.first == currentIndex) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3]

                    val millis = parseFractionToMillis(fractionStr)
                    val rawTimestampMs = minutes * 60_000L + seconds * 1_000L + millis
                    val adjustedTimestampMs = (rawTimestampMs + offsetMs).coerceAtLeast(0L)

                    leadingTimestamps.add(adjustedTimestampMs)
                    currentIndex = match.range.last + 1
                } else {
                    break
                }
            }

            if (leadingTimestamps.isNotEmpty()) {
                foundAnyTimestamp = true
                val rawBody = line.substring(currentIndex)
                val cleanedText = INLINE_TIMESTAMP_REGEX.replace(rawBody, "")
                    .replace(Regex("""[ \t]{2,}"""), " ")
                    .trim()

                for (ts in leadingTimestamps) {
                    rawEntries.add(RawEntry(timestampMs = ts, text = cleanedText, originalOrder = lineIndex))
                }
            } else {
                // Potential fallback line if no timestamps are present in the entire document
                fallbackLines.add(line)
            }
        }

        return when {
            foundAnyTimestamp -> {
                // 1. Sort entries chronologically, maintaining stable original document order
                val sorted = rawEntries.sortedWith(
                    compareBy({ it.timestampMs }, { it.originalOrder })
                )

                // 2. Deduplicate: remove consecutive identical text lines within a 300ms window
                val deduplicated = mutableListOf<RawEntry>()
                for (entry in sorted) {
                    if (deduplicated.isEmpty()) {
                        deduplicated.add(entry)
                    } else {
                        val last = deduplicated.last()
                        val isDuplicate = (entry.timestampMs - last.timestampMs < NEARBY_TIMESTAMP_WINDOW_MS) &&
                            (entry.text == last.text)
                        if (!isDuplicate) {
                            deduplicated.add(entry)
                        }
                    }
                }

                // 3. Bilingual lyric merging: when consecutive lines have identical or nearly identical (<300ms) timestamps,
                // merge the second line as the translation of the first rather than treating it as a disjoint line.
                val merged = mutableListOf<LyricLine>()
                for (entry in deduplicated) {
                    if (merged.isEmpty()) {
                        merged.add(LyricLine(timestampMs = entry.timestampMs, text = entry.text, translation = null))
                    } else {
                        val last = merged.last()
                        val canMergeAsTranslation = last.translation == null &&
                            last.text.isNotBlank() &&
                            entry.text.isNotBlank() &&
                            (entry.timestampMs - last.timestampMs < NEARBY_TIMESTAMP_WINDOW_MS) &&
                            entry.text != last.text

                        if (canMergeAsTranslation) {
                            merged[merged.lastIndex] = last.copy(translation = entry.text)
                        } else {
                            merged.add(LyricLine(timestampMs = entry.timestampMs, text = entry.text, translation = null))
                        }
                    }
                }

                Lyrics(lines = merged, isSynchronized = true)
            }
            fallbackLines.isNotEmpty() -> {
                val unsynced = fallbackLines.map { LyricLine(timestampMs = 0L, text = it) }
                Lyrics(lines = unsynced, isSynchronized = false)
            }
            else -> Lyrics.EMPTY
        }
    }

    private fun parseFractionToMillis(fractionStr: String?): Long {
        if (fractionStr.isNullOrEmpty()) return 0L
        return when (fractionStr.length) {
            1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
            3 -> fractionStr.toLongOrNull() ?: 0L
            else -> fractionStr.take(3).toLongOrNull() ?: 0L
        }
    }
}
