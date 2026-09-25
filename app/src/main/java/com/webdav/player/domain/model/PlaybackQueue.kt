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

    companion object {
        val EMPTY = PlaybackQueue()
    }
}
