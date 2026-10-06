package com.webdav.player.domain.model

/**
 * Domain model representing a CUE sheet album entity in a remote directory.
 * Associates a [.cue] file with its corresponding parent audio file and its parsed virtual tracks.
 */
data class CueAlbumItem(
    val cueFile: RemoteFile,
    val audioFile: RemoteFile? = null,
    val tracks: List<VirtualTrack> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalTracksCount: Int
        get() = tracks.size

    val albumTitle: String
        get() = audioFile?.name?.substringBeforeLast('.') ?: cueFile.name.substringBeforeLast('.')

    fun isAlbumActive(activeTrackPath: String?): Boolean =
        activeTrackPath != null && audioFile != null && activeTrackPath == audioFile.path

    fun isVirtualTrackActive(
        activeTrackPath: String?,
        activeTrackId: String?,
        track: VirtualTrack,
    ): Boolean {
        if (activeTrackPath == null || audioFile == null || activeTrackPath != audioFile.path) return false
        if (activeTrackId == null) return false
        return activeTrackId.endsWith("#cue_${track.trackNumber}")
    }
}
