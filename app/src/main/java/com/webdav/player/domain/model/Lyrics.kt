package com.webdav.player.domain.model

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val isSynchronized: Boolean = true
) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val isNotEmpty: Boolean get() = lines.isNotEmpty()

    /**
     * Returns the index of the active lyric line for the given position in milliseconds.
     * Returns -1 if lyrics are empty, unsynchronized, or if position is before the first line.
     */
    fun findActiveLineIndex(positionMs: Long): Int {
        if (lines.isEmpty() || !isSynchronized) return -1

        var low = 0
        var high = lines.lastIndex
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result
    }

    companion object {
        val EMPTY = Lyrics(lines = emptyList(), isSynchronized = false)
    }
}
