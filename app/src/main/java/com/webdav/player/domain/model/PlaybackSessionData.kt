package com.webdav.player.domain.model

data class PlaybackSessionData(
    val activeServerId: Long?,
    val currentDirectoryPath: String,
    val queueTracks: List<AudioTrack>,
    val currentTrackIndex: Int,
    val positionMs: Long,
    val playbackMode: PlaybackMode
) {
    companion object {
        val EMPTY = PlaybackSessionData(
            activeServerId = null,
            currentDirectoryPath = "/",
            queueTracks = emptyList(),
            currentTrackIndex = -1,
            positionMs = 0L,
            playbackMode = PlaybackMode.LIST_LOOP
        )
    }
}
