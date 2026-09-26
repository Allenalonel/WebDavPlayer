package com.webdav.player.domain.model

data class PlaybackQueue(
    val tracks: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1
) {
    val currentTrack: AudioTrack?
        get() = if (currentIndex in tracks.indices) tracks[currentIndex] else null

    val isEmpty: Boolean get() = tracks.isEmpty()
    val isNotEmpty: Boolean get() = tracks.isNotEmpty()
    val size: Int get() = tracks.size

    val hasNext: Boolean get() = currentIndex < tracks.size - 1
    val hasPrevious: Boolean get() = currentIndex > 0

    fun playTrackAt(index: Int): PlaybackQueue {
        if (index !in tracks.indices) return this
        return copy(currentIndex = index)
    }

    fun insertNext(track: AudioTrack): PlaybackQueue {
        if (tracks.isEmpty()) {
            return PlaybackQueue(listOf(track), 0)
        }
        val insertIndex = (currentIndex + 1).coerceIn(0, tracks.size)
        val newTracks = tracks.toMutableList().apply {
            add(insertIndex, track)
        }
        return copy(tracks = newTracks)
    }

    fun removeTrackAt(index: Int): PlaybackQueue {
        if (index !in tracks.indices) return this
        val newTracks = tracks.toMutableList().apply { removeAt(index) }
        if (newTracks.isEmpty()) {
            return EMPTY
        }
        val newIndex = when {
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> {
                if (index < newTracks.size) index else newTracks.lastIndex
            }
            else -> currentIndex
        }
        return copy(tracks = newTracks, currentIndex = newIndex)
    }

    fun getNextIndex(mode: PlaybackMode, shuffleOrder: List<Int>? = null): Int? {
        if (tracks.isEmpty()) return null
        if (tracks.size == 1) return 0
        return when (mode) {
            PlaybackMode.SINGLE_LOOP -> currentIndex.coerceIn(tracks.indices)
            PlaybackMode.LIST_LOOP -> {
                if (currentIndex >= tracks.lastIndex) 0 else currentIndex + 1
            }
            PlaybackMode.SHUFFLE -> {
                if (!shuffleOrder.isNullOrEmpty()) {
                    val currentPos = shuffleOrder.indexOf(currentIndex)
                    if (currentPos in 0 until shuffleOrder.lastIndex) {
                        shuffleOrder[currentPos + 1]
                    } else {
                        shuffleOrder.first()
                    }
                } else {
                    tracks.indices.filter { it != currentIndex }.randomOrNull() ?: 0
                }
            }
        }
    }

    fun getPreviousIndex(mode: PlaybackMode, shuffleOrder: List<Int>? = null): Int? {
        if (tracks.isEmpty()) return null
        if (tracks.size == 1) return 0
        return when (mode) {
            PlaybackMode.SINGLE_LOOP -> currentIndex.coerceIn(tracks.indices)
            PlaybackMode.LIST_LOOP -> {
                if (currentIndex <= 0) tracks.lastIndex else currentIndex - 1
            }
            PlaybackMode.SHUFFLE -> {
                if (!shuffleOrder.isNullOrEmpty()) {
                    val currentPos = shuffleOrder.indexOf(currentIndex)
                    if (currentPos > 0) {
                        shuffleOrder[currentPos - 1]
                    } else {
                        shuffleOrder.last()
                    }
                } else {
                    tracks.indices.filter { it != currentIndex }.randomOrNull() ?: 0
                }
            }
        }
    }

    companion object {
        val EMPTY = PlaybackQueue()
    }
}
