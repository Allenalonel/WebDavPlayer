package com.webdav.player.domain.model

data class PlaybackQueue(
    val tracks: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val originalTracks: List<AudioTrack> = emptyList(),
    val origin: QueueOrigin = QueueOrigin.DIRECTORY,
) {
    val currentTrack: AudioTrack?
        get() = if (currentIndex in tracks.indices) tracks[currentIndex] else null

    val isEmpty: Boolean get() = tracks.isEmpty()
    val isNotEmpty: Boolean get() = tracks.isNotEmpty()
    val size: Int get() = tracks.size

    val hasNext: Boolean get() = currentIndex < tracks.size - 1
    val hasPrevious: Boolean get() = currentIndex > 0

    val isPlaylistQueue: Boolean get() = origin == QueueOrigin.PLAYLIST
    val isDirectoryQueue: Boolean get() = origin == QueueOrigin.DIRECTORY
    val isAdHocQueue: Boolean get() = origin == QueueOrigin.AD_HOC

    val isShuffled: Boolean get() = originalTracks.isNotEmpty()

    fun playTrackAt(index: Int): PlaybackQueue {
        if (index !in tracks.indices) return this
        return copy(currentIndex = index)
    }

    /**
     * Inserts a track immediately after the active track.
     * If currently shuffled, also inserts the track immediately after the active track in [originalTracks]
     * so that toggling off shuffle preserves the relative inserted position.
     */
    fun insertNext(track: AudioTrack): PlaybackQueue {
        if (tracks.isEmpty()) {
            return PlaybackQueue(listOf(track), 0, origin = QueueOrigin.AD_HOC)
        }
        val insertIndex = (currentIndex + 1).coerceIn(0, tracks.size)
        val newTracks =
            tracks.toMutableList().apply {
                add(insertIndex, track)
            }
        val newOriginal =
            if (isShuffled) {
                val curr = currentTrack
                val origIndex =
                    if (curr != null) {
                        originalTracks.indexOfFirst { it.id == curr.id }.takeIf { it >= 0 } ?: originalTracks.lastIndex
                    } else {
                        originalTracks.lastIndex
                    }
                val targetOrigInsert = (origIndex + 1).coerceIn(0, originalTracks.size)
                originalTracks.toMutableList().apply {
                    add(targetOrigInsert, track)
                }
            } else {
                emptyList()
            }
        return copy(tracks = newTracks, originalTracks = newOriginal)
    }

    /**
     * Removes the track at the specified index.
     * If currently shuffled, also removes the same track from [originalTracks].
     */
    fun removeTrackAt(index: Int): PlaybackQueue {
        if (index !in tracks.indices) return this
        val removedTrack = tracks[index]
        val newTracks = tracks.toMutableList().apply { removeAt(index) }
        if (newTracks.isEmpty()) {
            return EMPTY
        }
        val newIndex =
            when {
                index < currentIndex -> currentIndex - 1
                index == currentIndex -> if (index < newTracks.size) index else newTracks.lastIndex
                else -> currentIndex
            }
        val newOriginal =
            if (isShuffled) {
                originalTracks.filterNot { it.id == removedTrack.id }
            } else {
                emptyList()
            }
        return copy(tracks = newTracks, currentIndex = newIndex, originalTracks = newOriginal)
    }

    /**
     * Converts queue to shuffled mode:
     * - The current track is kept playing at index 0.
     * - The remaining tracks are shuffled.
     * - [originalTracks] preserves the base sequence before shuffling.
     */
    fun toShuffled(random: kotlin.random.Random = kotlin.random.Random.Default): PlaybackQueue {
        if (tracks.isEmpty()) return copy(originalTracks = emptyList())
        val baseOriginal = if (originalTracks.isNotEmpty()) originalTracks else tracks
        val safeIndex = currentIndex.coerceIn(tracks.indices)
        val current = tracks[safeIndex]

        val remaining = tracks.toMutableList().apply { removeAt(safeIndex) }
        remaining.shuffle(random)

        val shuffledTracks = listOf(current) + remaining
        return copy(
            tracks = shuffledTracks,
            currentIndex = 0,
            originalTracks = baseOriginal,
        )
    }

    /**
     * Reshuffles the queue when a full cycle in shuffle mode completes.
     * Shuffles all tracks, placing the newly generated sequence with currentIndex = 0.
     */
    fun reshuffle(random: kotlin.random.Random = kotlin.random.Random.Default): PlaybackQueue {
        if (tracks.isEmpty()) return this
        val baseOriginal = if (originalTracks.isNotEmpty()) originalTracks else tracks
        val shuffledTracks = tracks.toMutableList().apply { shuffle(random) }
        return copy(
            tracks = shuffledTracks,
            currentIndex = 0,
            originalTracks = baseOriginal,
        )
    }

    /**
     * Converts queue from shuffled mode back to original order:
     * Restores [originalTracks] into [tracks], syncing [currentIndex] to the active track's position in original list.
     */
    fun toUnshuffled(): PlaybackQueue {
        if (!isShuffled) return this
        val current = currentTrack
        val restoredTracks = originalTracks
        val restoredIndex =
            if (current != null) {
                restoredTracks.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }
        return copy(
            tracks = restoredTracks,
            currentIndex = restoredIndex,
            originalTracks = emptyList(),
        )
    }

    fun getNextIndex(mode: PlaybackMode): Int? {
        if (tracks.isEmpty()) return null
        if (tracks.size == 1) return 0
        return when (mode) {
            PlaybackMode.SINGLE_LOOP -> {
                currentIndex.coerceIn(tracks.indices)
            }

            PlaybackMode.LIST_LOOP,
            PlaybackMode.SHUFFLE,
            -> {
                if (currentIndex >= tracks.lastIndex) 0 else currentIndex + 1
            }
        }
    }

    fun getPreviousIndex(mode: PlaybackMode): Int? {
        if (tracks.isEmpty()) return null
        if (tracks.size == 1) return 0
        return when (mode) {
            PlaybackMode.SINGLE_LOOP -> {
                currentIndex.coerceIn(tracks.indices)
            }

            PlaybackMode.LIST_LOOP,
            PlaybackMode.SHUFFLE,
            -> {
                if (currentIndex <= 0) tracks.lastIndex else currentIndex - 1
            }
        }
    }

    fun inlineVirtualTracks(
        parentAudioPath: String,
        virtualTracks: List<VirtualTrack>,
    ): PlaybackQueue {
        if (virtualTracks.isEmpty() || tracks.isEmpty()) return this
        val targetIndex =
            tracks.indexOfFirst {
                it.remotePath == parentAudioPath || it.id.substringBefore("#cue_") == parentAudioPath
            }
        if (targetIndex < 0) return this

        val parentTrack = tracks[targetIndex]
        val inlinedTracks = virtualTracks.map { it.toAudioTrack(parentTrack) }
        val newTracks =
            buildList {
                addAll(tracks.subList(0, targetIndex))
                addAll(inlinedTracks)
                if (targetIndex + 1 < tracks.size) {
                    addAll(tracks.subList(targetIndex + 1, tracks.size))
                }
            }

        val addedCount = inlinedTracks.size - 1
        val newIndex =
            when {
                currentIndex < targetIndex -> currentIndex
                currentIndex == targetIndex -> targetIndex
                else -> currentIndex + addedCount
            }

        val newOriginal =
            if (originalTracks.isNotEmpty()) {
                val origTarget =
                    originalTracks.indexOfFirst {
                        it.remotePath == parentAudioPath || it.id.substringBefore("#cue_") == parentAudioPath
                    }
                if (origTarget >= 0) {
                    val origParent = originalTracks[origTarget]
                    val origInlined = virtualTracks.map { it.toAudioTrack(origParent) }
                    buildList {
                        addAll(originalTracks.subList(0, origTarget))
                        addAll(origInlined)
                        if (origTarget + 1 < originalTracks.size) {
                            addAll(originalTracks.subList(origTarget + 1, originalTracks.size))
                        }
                    }
                } else {
                    originalTracks
                }
            } else {
                emptyList()
            }

        return copy(tracks = newTracks, currentIndex = newIndex, originalTracks = newOriginal)
    }

    companion object {
        val EMPTY = PlaybackQueue()

        fun fromTracks(
            tracks: List<AudioTrack>,
            virtualTracksMap: Map<String, List<VirtualTrack>> = emptyMap(),
            selectedIndex: Int = 0,
            originalTracks: List<AudioTrack> = emptyList(),
            origin: QueueOrigin = QueueOrigin.DIRECTORY,
        ): PlaybackQueue {
            if (tracks.isEmpty()) return EMPTY
            if (virtualTracksMap.isEmpty()) {
                val validSelectedIndex = selectedIndex.coerceIn(tracks.indices)
                return PlaybackQueue(
                    tracks = tracks,
                    currentIndex = validSelectedIndex,
                    originalTracks = originalTracks,
                    origin = origin,
                )
            }

            val validSelectedIndex = selectedIndex.coerceIn(tracks.indices)
            val mappedCurrentIndex =
                tracks.take(validSelectedIndex).sumOf { track ->
                    val vts = virtualTracksMap[track.remotePath]
                    if (!vts.isNullOrEmpty()) vts.size else 1
                }

            val expandedTracks =
                tracks.flatMap { track ->
                    val vts = virtualTracksMap[track.remotePath]
                    if (!vts.isNullOrEmpty()) {
                        vts.map { it.toAudioTrack(track) }
                    } else {
                        listOf(track)
                    }
                }

            val targetIndex = mappedCurrentIndex.coerceIn(expandedTracks.indices)

            return PlaybackQueue(
                tracks = expandedTracks,
                currentIndex = targetIndex,
                originalTracks = originalTracks,
                origin = origin,
            )
        }
    }
}
