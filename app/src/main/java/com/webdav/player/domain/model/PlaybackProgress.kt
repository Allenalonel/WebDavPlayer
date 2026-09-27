package com.webdav.player.domain.model

import java.util.Locale

data class PlaybackProgress(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val bufferedFraction: Float
        get() = if (durationMs > 0L) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedCurrentPosition: String
        get() = formatMs(currentPositionMs)

    val formattedDuration: String
        get() = formatMs(durationMs)

    companion object {
        val ZERO =
            PlaybackProgress(
                currentPositionMs = 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
            )

        fun formatMs(durationMs: Long): String {
            if (durationMs <= 0L) return "00:00"
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}
