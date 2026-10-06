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

    /**
     * Converts this virtual track into a playable AudioTrack instance bound to the parent physical audio file.
     */
    fun toAudioTrack(parentTrack: AudioTrack): AudioTrack =
        AudioTrack(
            id = "${parentTrack.id.substringBefore("#cue_")}#cue_$trackNumber",
            serverId = parentTrack.serverId,
            remotePath = parentTrack.remotePath,
            title = title,
            artist = performer?.takeIf { it.isNotBlank() } ?: parentTrack.artist,
            album = parentTrack.album,
            durationMs = durationMs,
            size = parentTrack.size,
            format = parentTrack.format,
            coverThumbnailPath = parentTrack.coverThumbnailPath,
        )
}
