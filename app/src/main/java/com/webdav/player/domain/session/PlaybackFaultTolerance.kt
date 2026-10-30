package com.webdav.player.domain.session

import com.webdav.player.data.player.UnsupportedRangeReconnectionException
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

fun Throwable?.is404Exception(): Boolean {
    if (this == null) return false
    val causes = generateSequence(this) { it.cause }
    return causes.any { cause ->
        (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && cause.responseCode == 404) ||
            (cause is java.io.FileNotFoundException) ||
            (cause.message?.contains("404") == true)
    } || (message?.contains("404") == true)
}

/**
 * Internal collaborator managing stream buffering watchdog timers, error classification,
 * dead-track skip handling, premature EOF arbitration, and unsupported Range replay dialogues.
 */
internal class PlaybackFaultTolerance(
    private val sessionStateFlow: MutableStateFlow<PlayerSessionState>,
    private val playerEngine: AudioPlayerEngine,
    private val playlistRepository: PlaylistRepository?,
    private val queueManager: PlaybackQueueManager,
    private val coroutineScope: CoroutineScope,
) {
    val bufferingWatchdogJobRef = AtomicReference<Job?>(null)
    val lastBufferingTimestampMs = AtomicLong(0L)

    fun onBufferingStarted() {
        lastBufferingTimestampMs.set(System.currentTimeMillis())
        if (bufferingWatchdogJobRef.get()?.isActive != true) {
            val job =
                coroutineScope.launch {
                    delay(WATCHDOG_WEAK_NETWORK_TIMEOUT_MS)
                    if (isActive) {
                        sessionStateFlow.update {
                            it.copy(isWeakNetwork = true, canManualRetry = false, isReconnecting = true)
                        }
                        delay(WATCHDOG_AUTO_PAUSE_TIMEOUT_MS)
                        if (isActive) {
                            playerEngine.pause()
                            sessionStateFlow.update {
                                it.copy(
                                    playbackState = PlaybackState.Paused,
                                    isWeakNetwork = true,
                                    canManualRetry = true,
                                    isReconnecting = true,
                                )
                            }
                        }
                    }
                }
            bufferingWatchdogJobRef.set(job)
        }
    }

    fun onPlayingStarted() {
        cancelWatchdog()
        val current = sessionStateFlow.value
        if (current.isWeakNetwork || current.canManualRetry || current.isReconnecting) {
            sessionStateFlow.update {
                it.copy(isWeakNetwork = false, canManualRetry = false, isReconnecting = false)
            }
        }
    }

    fun cancelWatchdog() {
        bufferingWatchdogJobRef.getAndSet(null)?.cancel()
    }

    fun isHealthyNetwork(quietPeriodMs: Long = HEALTHY_NETWORK_QUIET_PERIOD_MS): Boolean =
        (System.currentTimeMillis() - lastBufferingTimestampMs.get()) >= quietPeriodMs

    fun setWeakNetworkState(
        isWeakNetwork: Boolean,
        canManualRetry: Boolean,
    ) {
        sessionStateFlow.update {
            it.copy(isWeakNetwork = isWeakNetwork, canManualRetry = canManualRetry, isReconnecting = isWeakNetwork)
        }
    }

    fun isAuthException(e: Throwable): Boolean {
        val causes = generateSequence(e) { it.cause }
        return causes.any { c ->
            (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && (c.responseCode == 401 || c.responseCode == 403)) ||
                c.message?.contains("401") == true ||
                c.message?.contains("403") == true ||
                c.message?.contains("Unauthorized", ignoreCase = true) == true ||
                c.message?.contains("Forbidden", ignoreCase = true) == true
        }
    }

    fun isServerUnreachableException(e: Throwable): Boolean {
        val causes = generateSequence(e) { it.cause }
        return causes.any { c ->
            c is java.net.ConnectException ||
                c is java.net.UnknownHostException ||
                c is java.net.SocketTimeoutException ||
                c.message?.contains("Connection refused", ignoreCase = true) == true ||
                c.message?.contains("Failed to connect", ignoreCase = true) == true
        }
    }

    fun is404Error(state: PlaybackState.Error): Boolean = state.cause.is404Exception() || state.message.contains("404")

    fun isAuthError(state: PlaybackState.Error): Boolean {
        val cause = state.cause
        if (cause != null && isAuthException(cause)) return true
        return state.message.contains("401") ||
            state.message.contains("403") ||
            state.message.contains("Unauthorized", ignoreCase = true) ||
            state.message.contains("Forbidden", ignoreCase = true)
    }

    fun isServerUnreachableError(state: PlaybackState.Error): Boolean {
        val cause = state.cause
        if (cause != null && isServerUnreachableException(cause)) return true
        return state.message.contains("ConnectException") ||
            state.message.contains("Connection refused", ignoreCase = true)
    }

    fun isUnsupportedRangeError(state: PlaybackState.Error): Boolean {
        val hasTypedCause =
            generateSequence<Throwable>(state.cause) { it.cause }.any {
                it is UnsupportedRangeReconnectionException
            }
        if (hasTypedCause) return true
        return state.message.contains("UnsupportedRangeReconnectionException", ignoreCase = true)
    }

    fun handle404Failure(
        track: AudioTrack?,
        errorMessage: String? = null,
        onSkipToNext: () -> Unit,
    ) {
        cancelWatchdog()
        val queueSize = sessionStateFlow.value.queue.size
        val shouldSkip =
            if (track != null) {
                playlistRepository?.markTrackInactive(track.serverId, track.remotePath, InactiveReason.FILE_NOT_FOUND)
                queueManager.recordFailedTrack(track.id, queueSize)
            } else {
                false
            }
        if (shouldSkip) {
            onSkipToNext()
        } else {
            val msg = errorMessage ?: "音频文件已被重命名或删除"
            sessionStateFlow.update {
                it.copy(
                    playbackState = PlaybackState.Error(msg),
                    errorMessage = msg,
                    canManualRetry = false,
                    isWeakNetwork = false,
                )
            }
        }
    }

    fun handleAuthFailure(
        track: AudioTrack?,
        errorMessage: String? = null,
        onSkipToNext: () -> Unit,
    ) {
        cancelWatchdog()
        val queueSize = sessionStateFlow.value.queue.size
        val shouldSkip =
            if (track != null) {
                playlistRepository?.markTrackInactive(track.serverId, track.remotePath, InactiveReason.SERVER_CREDENTIAL_INVALID)
                queueManager.recordFailedTrack(track.id, queueSize)
            } else {
                false
            }
        if (shouldSkip) {
            onSkipToNext()
        } else {
            val msg = errorMessage ?: "服务器认证凭据失效"
            sessionStateFlow.update {
                it.copy(
                    playbackState = PlaybackState.Error(msg),
                    errorMessage = msg,
                    canManualRetry = false,
                    isWeakNetwork = false,
                )
            }
        }
    }

    fun handleServerUnavailable(
        track: AudioTrack,
        onSkipToNext: () -> Unit,
    ) {
        cancelWatchdog()
        playlistRepository?.markTrackInactive(track.serverId, track.remotePath, InactiveReason.SERVER_UNREACHABLE)
        val queueSize = sessionStateFlow.value.queue.size
        if (queueManager.recordFailedTrack(track.id, queueSize)) {
            onSkipToNext()
        } else {
            sessionStateFlow.update {
                it.copy(
                    playbackState = PlaybackState.Error("所属服务器无法连接"),
                    errorMessage = "所属服务器无法连接",
                    canManualRetry = false,
                    isWeakNetwork = false,
                )
            }
        }
    }

    fun handlePlaybackError(
        state: PlaybackState.Error,
        currentTrack: AudioTrack?,
        onUnsupportedRange: () -> Unit,
        onSkipToNext: () -> Unit,
    ): Boolean {
        if (isUnsupportedRangeError(state)) {
            sessionStateFlow.update { it.copy(playbackState = PlaybackState.Paused, isUnsupportedRange = true) }
            onUnsupportedRange()
            return true
        } else if (is404Error(state)) {
            handle404Failure(currentTrack, state.message, onSkipToNext)
            return true
        } else if (isAuthError(state)) {
            handleAuthFailure(currentTrack, state.message, onSkipToNext)
            return true
        } else if (isServerUnreachableError(state)) {
            if (currentTrack != null) {
                handleServerUnavailable(currentTrack, onSkipToNext)
            }
            return true
        } else {
            sessionStateFlow.update {
                it.copy(
                    playbackState = state,
                    errorMessage = state.message,
                    isWeakNetwork = true,
                    canManualRetry = true,
                    isReconnecting = false,
                )
            }
            return false
        }
    }

    fun handleLoadException(
        e: Throwable,
        track: AudioTrack,
        onSkipToNext: () -> Unit,
    ) {
        if (e.is404Exception()) {
            handle404Failure(track, onSkipToNext = onSkipToNext)
        } else if (isAuthException(e)) {
            handleAuthFailure(track, onSkipToNext = onSkipToNext)
        } else if (isServerUnreachableException(e)) {
            handleServerUnavailable(track, onSkipToNext = onSkipToNext)
        } else {
            sessionStateFlow.update {
                it.copy(
                    playbackState = PlaybackState.Error(e.message ?: "加载音频失败"),
                    errorMessage = e.message ?: "加载音频失败",
                    canManualRetry = false,
                    isWeakNetwork = false,
                )
            }
        }
    }

    fun dismissUnsupportedRange() {
        sessionStateFlow.update { it.copy(isUnsupportedRange = false) }
    }

    fun confirmUnsupportedRangeReplay(onReplay: () -> Unit) {
        sessionStateFlow.update { it.copy(isUnsupportedRange = false, errorMessage = null) }
        onReplay()
    }

    fun isPrematureEof(
        currentPos: Long,
        duration: Long,
    ): Boolean {
        val isPhysicalMatch = duration <= 0L || currentPos >= (duration - DURATION_COMPLETION_TOLERANCE_MS)
        val isProgressMatch =
            duration > 0L && (currentPos.toDouble() / duration.toDouble()) >= COMPLETION_PROGRESS_THRESHOLD
        return !isPhysicalMatch && !isProgressMatch
    }

    fun checkAndTriggerAdaptivePreroll(
        progress: com.webdav.player.domain.model.PlaybackProgress,
        queue: com.webdav.player.domain.model.PlaybackQueue,
        playbackMode: com.webdav.player.domain.model.PlaybackMode,
        activeServer: com.webdav.player.domain.model.WebDavServer?,
        currentTrackId: String?,
        isPrerollAppended: java.util.concurrent.atomic.AtomicBoolean,
        serverRepository: com.webdav.player.domain.repository.ServerRepository?,
        onPreroll: (server: com.webdav.player.domain.model.WebDavServer, track: AudioTrack) -> Unit,
    ) {
        if (isPrerollAppended.get() || queue.tracks.size <= 1) return
        val remainingMs = progress.durationMs - progress.currentPositionMs

        if (remainingMs in 1..PREROLL_TRIGGER_WINDOW_MS) {
            val isPlaying = sessionStateFlow.value.playbackState is PlaybackState.Playing
            if (isPlaying && isHealthyNetwork(HEALTHY_NETWORK_QUIET_PERIOD_MS)) {
                if (isPrerollAppended.compareAndSet(false, true)) {
                    val server =
                        activeServer ?: run {
                            isPrerollAppended.set(false)
                            return
                        }
                    val nextIndex = queueManager.calculateNextQueueIndex(queue, playbackMode)
                    val nextTrack = queue.tracks[nextIndex]
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        if (isPrerollAppended.get() &&
                            sessionStateFlow.value.playbackState is PlaybackState.Playing &&
                            sessionStateFlow.value.currentTrack?.id == currentTrackId
                        ) {
                            val nextServer =
                                if (server.id == nextTrack.serverId) {
                                    server
                                } else {
                                    serverRepository?.getServerById(nextTrack.serverId) ?: server
                                }
                            onPreroll(nextServer, nextTrack)
                        } else {
                            isPrerollAppended.set(false)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val PREROLL_TRIGGER_WINDOW_MS: Long = 10_000L
        const val WATCHDOG_WEAK_NETWORK_TIMEOUT_MS: Long = 15_000L
        const val WATCHDOG_AUTO_PAUSE_TIMEOUT_MS: Long = 15_000L
        const val DURATION_COMPLETION_TOLERANCE_MS: Long = 5000L
        const val COMPLETION_PROGRESS_THRESHOLD: Double = 0.95
        const val HEALTHY_NETWORK_QUIET_PERIOD_MS: Long = 30_000L
    }
}
