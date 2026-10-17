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
    val isWeakNetwork: Boolean = false,
    val canManualRetry: Boolean = false,
    val isReconnecting: Boolean = false,
    val wasKilledBySystem: Boolean = false,
    val isUnsupportedRange: Boolean = false,
) {
    val currentTrack: AudioTrack? get() = queue.currentTrack
    val isPlaying: Boolean get() = playbackState is PlaybackState.Playing
    val isBuffering: Boolean get() = playbackState is PlaybackState.Buffering
    val isPaused: Boolean get() = playbackState is PlaybackState.Paused
    val isIdle: Boolean get() = playbackState is PlaybackState.Idle
    val hasTrack: Boolean get() = currentTrack != null
    val isSuspendedRetry: Boolean get() = isWeakNetwork && canManualRetry

    companion object {
        const val WEAK_NETWORK_MESSAGE = "网络受阻，正在重连..."
        const val RETRY_BANNER_MESSAGE = "网络连接受阻，点击重试"
        const val RETRY_BUTTON_LABEL = "点击重试"
    }
}
