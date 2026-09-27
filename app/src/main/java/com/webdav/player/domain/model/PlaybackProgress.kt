package com.webdav.player.domain.model

data class PlaybackProgress(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val bufferedFraction: Float
        get() = if (durationMs > 0L) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    companion object {
        val ZERO =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
            )
    }
}
