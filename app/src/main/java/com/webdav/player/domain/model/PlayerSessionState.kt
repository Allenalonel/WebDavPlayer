package com.webdav.player.domain.model

data class PlayerSessionState(
    val activeServer: WebDavServer? = null,
    val queue: PlaybackQueue = PlaybackQueue.EMPTY,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val playbackMode: PlaybackMode = PlaybackMode.LIST_LOOP,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val lyrics: Lyrics? = null,
    val isLoadingLyrics: Boolean = false,
    val currentDirectoryPath: String = "/",
) {
    val currentTrack: AudioTrack? get() = queue.currentTrack
    val isPlaying: Boolean get() = playbackState is PlaybackState.Playing
    val isBuffering: Boolean get() = playbackState is PlaybackState.Buffering
    val isPaused: Boolean get() = playbackState is PlaybackState.Paused
    val isIdle: Boolean get() = playbackState is PlaybackState.Idle
    val hasTrack: Boolean get() = currentTrack != null
}
