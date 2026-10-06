package com.webdav.player.domain.model

data class PlaybackSessionData(
    val activeServerId: Long?,
    val currentDirectoryPath: String,
    val queueTracks: List<AudioTrack>,
    val currentTrackIndex: Int,
    val positionMs: Long,
    val playbackMode: PlaybackMode,
    val serverLastDirectories: Map<Long, String> = emptyMap(),
    val cuePath: String? = null,
    val virtualTrackNumber: Int? = null,
    val virtualPositionMs: Long? = null,
) {
    companion object {
        val EMPTY = PlaybackSessionData(
            activeServerId = null,
            currentDirectoryPath = "/",
            queueTracks = emptyList(),
            currentTrackIndex = -1,
            positionMs = 0L,
            playbackMode = PlaybackMode.LIST_LOOP,
            serverLastDirectories = emptyMap(),
            cuePath = null,
            virtualTrackNumber = null,
            virtualPositionMs = null,
        )
    }
}
