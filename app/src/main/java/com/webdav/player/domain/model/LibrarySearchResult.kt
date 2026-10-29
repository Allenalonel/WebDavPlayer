package com.webdav.player.domain.model

data class LibrarySearchResult(
    val matchedDirectories: List<RemoteDirectory> = emptyList(),
    val matchedTracks: List<AudioTrack> = emptyList(),
) {
    val isEmpty: Boolean
        get() = matchedDirectories.isEmpty() && matchedTracks.isEmpty()

    val totalMatchesCount: Int
        get() = matchedDirectories.size + matchedTracks.size
}
