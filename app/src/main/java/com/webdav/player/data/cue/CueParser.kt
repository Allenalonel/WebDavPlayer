package com.webdav.player.data.cue

import com.webdav.player.domain.model.VirtualTrack
import java.util.Locale

/**
 * Lightweight, in-memory, zero-dependency streaming state machine CUE sheet parser.
 * Parses CUE text into an ordered list of [VirtualTrack] entities with millisecond intervals.
 *
 * Conforms to CUE standard (75 frames/second):
 * 1 frame = 1000 / 75 ms
 * ms = (min * 60 + sec) * 1000 + (frames * 1000 / 75)
 */
object CueParser {

    private const val FRAMES_PER_SECOND = 75L
    private val TIMESTAMP_REGEX = Regex("""^(\d+):(\d{1,2})(?::(\d{1,2}))?$""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private data class IntermediateTrack(
        val file: String,
        val trackNumber: Int,
        val title: String,
        val performer: String?,
        val startTimeMs: Long,
    )

    /**
     * Parses CUE content into a list of [VirtualTrack] models.
     *
     * @param content The raw CUE text.
     * @param parentAudioPath Optional full path to the parent audio file. If supplied and the CUE references
     *                        a single audio file, this path is assigned directly to the tracks.
     * @param totalDurationMs Optional total duration of the parent audio file. If supplied, the last track's
     *                        [VirtualTrack.endTimeMs] is set to this value.
     * @param allowMissingFileTag If false (default), returning an empty list when no FILE directive exists.
     * @return Ordered list of [VirtualTrack], or empty list if malformed/unparseable. Never throws.
     */
    fun parse(
        content: String,
        parentAudioPath: String? = null,
        totalDurationMs: Long? = null,
        allowMissingFileTag: Boolean = false,
    ): List<VirtualTrack> {
        if (content.isBlank()) return emptyList()

        return try {
            val cleanContent = content.removePrefix("\uFEFF")
            val lines = cleanContent.lines()

            var albumPerformer: String? = null
            var currentFile: String? = null
            var hasSeenFileTag = false

            var currentTrackNumber: Int? = null
            var currentTrackTitle: String? = null
            var currentTrackPerformer: String? = null
            var currentIndex01Ms: Long? = null
            var currentIndex00Ms: Long? = null

            val intermediateTracks = mutableListOf<IntermediateTrack>()

            fun flushCurrentTrack() {
                val trackNum = currentTrackNumber ?: return
                val startTime = currentIndex01Ms ?: currentIndex00Ms
                if (startTime != null) {
                    val file = currentFile ?: ""
                    val title = currentTrackTitle?.takeIf { it.isNotBlank() }
                        ?: String.format(Locale.US, "Track %02d", trackNum)
                    val performer = currentTrackPerformer?.takeIf { it.isNotBlank() } ?: albumPerformer

                    intermediateTracks.add(
                        IntermediateTrack(
                            file = file,
                            trackNumber = trackNum,
                            title = title,
                            performer = performer,
                            startTimeMs = startTime.coerceAtLeast(0L),
                        )
                    )
                }

                currentTrackNumber = null
                currentTrackTitle = null
                currentTrackPerformer = null
                currentIndex01Ms = null
                currentIndex00Ms = null
            }

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                // Skip REM comments
                if (isCommand(line, "REM")) {
                    continue
                }

                if (isCommand(line, "FILE")) {
                    flushCurrentTrack()
                    hasSeenFileTag = true
                    currentFile = parseFileDirective(line)
                    continue
                }

                if (isCommand(line, "TRACK")) {
                    flushCurrentTrack()
                    val remainder = line.substring(5).trim()
                    val tokens = remainder.split(WHITESPACE_REGEX)
                    currentTrackNumber = tokens.firstOrNull()?.toIntOrNull() ?: (intermediateTracks.size + 1)
                    continue
                }

                if (isCommand(line, "TITLE")) {
                    val titleVal = extractTextValue(line.substring(5).trim())
                    if (currentTrackNumber != null) {
                        currentTrackTitle = titleVal
                    }
                    continue
                }

                if (isCommand(line, "PERFORMER")) {
                    val performerVal = extractTextValue(line.substring(9).trim())
                    if (currentTrackNumber != null) {
                        currentTrackPerformer = performerVal
                    } else {
                        albumPerformer = performerVal
                    }
                    continue
                }

                if (isCommand(line, "INDEX")) {
                    val remainder = line.substring(5).trim()
                    val tokens = remainder.split(WHITESPACE_REGEX)
                    if (tokens.size >= 2) {
                        val indexNum = tokens[0].toIntOrNull()
                        val timestampMs = parseTimestampToMillis(tokens[1])
                        if (timestampMs != null) {
                            when (indexNum) {
                                1 -> currentIndex01Ms = timestampMs
                                0 -> currentIndex00Ms = timestampMs
                            }
                        }
                    }
                    continue
                }
            }

            // Flush the last track in the file
            flushCurrentTrack()

            if (!hasSeenFileTag && !allowMissingFileTag) {
                return emptyList()
            }

            if (intermediateTracks.isEmpty()) {
                return emptyList()
            }

            // Filter disordered timestamps per file segment while preserving CUE order
            val distinctFiles = intermediateTracks.map { it.file }.distinct()
            val singleFile = distinctFiles.size <= 1

            val monotonicTracks = mutableListOf<IntermediateTrack>()
            var lastFile: String? = null
            var lastValidStartTime = -1L

            for (track in intermediateTracks) {
                if (track.file != lastFile) {
                    lastFile = track.file
                    lastValidStartTime = -1L
                }
                if (track.startTimeMs >= lastValidStartTime) {
                    monotonicTracks.add(track)
                    lastValidStartTime = track.startTimeMs
                }
            }

            if (monotonicTracks.isEmpty()) {
                return emptyList()
            }

            // Calculate intervals and assign resolved audio paths
            val result = mutableListOf<VirtualTrack>()
            for (i in monotonicTracks.indices) {
                val current = monotonicTracks[i]
                val nextInSameFile = monotonicTracks.getOrNull(i + 1)?.takeIf { it.file == current.file }

                val resolvedPath = resolveAudioPath(
                    rawFile = current.file,
                    providedPath = parentAudioPath,
                    singleFile = singleFile,
                )

                val endTimeMs: Long? = if (nextInSameFile != null) {
                    nextInSameFile.startTimeMs
                } else {
                    if (totalDurationMs != null && totalDurationMs > current.startTimeMs) {
                        totalDurationMs
                    } else {
                        null
                    }
                }

                result.add(
                    VirtualTrack(
                        trackNumber = current.trackNumber,
                        title = current.title,
                        performer = current.performer,
                        startTimeMs = current.startTimeMs,
                        endTimeMs = endTimeMs,
                        parentAudioPath = resolvedPath,
                    )
                )
            }

            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun isCommand(line: String, command: String): Boolean {
        if (!line.startsWith(command, ignoreCase = true)) return false
        val len = command.length
        return line.length == len || line[len].isWhitespace()
    }

    private fun parseFileDirective(line: String): String {
        val remainder = line.substring(4).trim()
        if (remainder.startsWith('"')) {
            val closing = remainder.indexOf('"', startIndex = 1)
            return if (closing > 0) {
                remainder.substring(1, closing)
            } else {
                remainder.substring(1).trim()
            }
        }
        if (remainder.startsWith('\'')) {
            val closing = remainder.indexOf('\'', startIndex = 1)
            return if (closing > 0) {
                remainder.substring(1, closing)
            } else {
                remainder.substring(1).trim()
            }
        }
        // Unquoted: e.g. "FILE album.flac WAVE" or "FILE album.flac"
        val tokens = remainder.split(WHITESPACE_REGEX)
        return if (tokens.size > 1) {
            tokens.dropLast(1).joinToString(" ")
        } else {
            tokens.firstOrNull() ?: ""
        }
    }

    private fun extractTextValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith('"')) {
            val closing = trimmed.indexOf('"', startIndex = 1)
            return if (closing > 0) {
                trimmed.substring(1, closing)
            } else {
                trimmed.substring(1).trimEnd('"')
            }
        }
        if (trimmed.startsWith('\'')) {
            val closing = trimmed.indexOf('\'', startIndex = 1)
            return if (closing > 0) {
                trimmed.substring(1, closing)
            } else {
                trimmed.substring(1).trimEnd('\'')
            }
        }
        return trimmed
    }

    /**
     * Converts a mm:ss:ff timestamp string into milliseconds.
     * CUE standard: 75 frames/second -> (min * 60 + sec) * 1000 + (frames * 1000 / 75)
     */
    fun parseTimestampToMillis(timestamp: String): Long? {
        val match = TIMESTAMP_REGEX.matchEntire(timestamp.trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val frames = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L

        return (minutes * 60L + seconds) * 1000L + (frames * 1000L / FRAMES_PER_SECOND)
    }

    private fun resolveAudioPath(
        rawFile: String,
        providedPath: String?,
        singleFile: Boolean,
    ): String {
        if (providedPath.isNullOrBlank()) {
            return rawFile
        }
        if (singleFile) {
            return providedPath
        }

        val dir = when {
            providedPath.contains('/') -> providedPath.substringBeforeLast('/') + "/"
            providedPath.contains('\\') -> providedPath.substringBeforeLast('\\') + "\\"
            else -> ""
        }

        val normalizedRaw = rawFile.replace('\\', '/')
        val isAbsolute = normalizedRaw.startsWith('/') || (normalizedRaw.length > 2 && normalizedRaw[1] == ':')

        return if (dir.isNotEmpty() && !isAbsolute) {
            dir + normalizedRaw
        } else {
            rawFile
        }
    }
}
