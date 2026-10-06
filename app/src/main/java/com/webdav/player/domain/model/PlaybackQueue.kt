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

    fun inlineVirtualTracks(
        parentAudioPath: String,
        virtualTracks: List<VirtualTrack>,
    ): PlaybackQueue {
        if (virtualTracks.isEmpty() || tracks.isEmpty()) return this
        val targetIndex = tracks.indexOfFirst {
            it.remotePath == parentAudioPath || it.id.substringBefore("#cue_") == parentAudioPath
        }
        if (targetIndex < 0) return this

        val parentTrack = tracks[targetIndex]
        val inlinedTracks = virtualTracks.map { it.toAudioTrack(parentTrack) }
        val newTracks = buildList {
            addAll(tracks.subList(0, targetIndex))
            addAll(inlinedTracks)
            if (targetIndex + 1 < tracks.size) {
                addAll(tracks.subList(targetIndex + 1, tracks.size))
            }
        }

        val addedCount = inlinedTracks.size - 1
        val newIndex = when {
            currentIndex < targetIndex -> currentIndex
            currentIndex == targetIndex -> targetIndex
            else -> currentIndex + addedCount
        }
        return copy(tracks = newTracks, currentIndex = newIndex)
    }

    companion object {
        val EMPTY = PlaybackQueue()

        fun fromTracks(
            tracks: List<AudioTrack>,
            virtualTracksMap: Map<String, List<VirtualTrack>> = emptyMap(),
            selectedIndex: Int = 0,
        ): PlaybackQueue {
            if (tracks.isEmpty()) return EMPTY
            if (virtualTracksMap.isEmpty()) {
                return PlaybackQueue(
                    tracks = tracks,
                    currentIndex = selectedIndex.coerceIn(tracks.indices),
                )
            }

            val validSelectedIndex = selectedIndex.coerceIn(tracks.indices)
            val mappedCurrentIndex = tracks.take(validSelectedIndex).sumOf { track ->
                val vts = virtualTracksMap[track.remotePath]
                if (!vts.isNullOrEmpty()) vts.size else 1
            }

            val expandedTracks = tracks.flatMap { track ->
                val vts = virtualTracksMap[track.remotePath]
                if (!vts.isNullOrEmpty()) {
                    vts.map { it.toAudioTrack(track) }
                } else {
                    listOf(track)
                }
            }

            return PlaybackQueue(
                tracks = expandedTracks,
                currentIndex = mappedCurrentIndex.coerceIn(expandedTracks.indices),
            )
        }
    }
}
