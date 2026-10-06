package com.webdav.player.domain.model

/**
 * Pure Kotlin domain model representing a virtual track partitioned from a parent audio file via CUE sheet.
 * No Android framework dependencies.
 */
data class VirtualTrack(
    val trackNumber: Int,
    val title: String,
    val performer: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val parentAudioPath: String,
) {
    /**
     * Duration in milliseconds if the end timestamp is known. Returns 0L if end time is null or before start time.
     */
    val durationMs: Long
        get() = if (endTimeMs != null && endTimeMs >= startTimeMs) endTimeMs - startTimeMs else 0L
}
