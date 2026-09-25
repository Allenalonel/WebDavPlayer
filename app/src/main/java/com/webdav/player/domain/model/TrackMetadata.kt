package com.webdav.player.domain.model

data class TrackMetadata(
    val serverId: Long,
    val remotePath: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val durationMs: Long = 0L,
    val coverThumbnailPath: String? = null
) {
    /**
     * Returns a displayable title, falling back to clean file name if title is null or blank.
     */
    fun displayTitle(fallbackFileName: String): String {
        return if (!title.isNullOrBlank()) {
            title
        } else {
            fallbackFileName.substringBeforeLast('.')
        }
    }

    /**
     * Returns a displayable artist, or null if empty/blank.
     */
    fun displayArtist(): String? {
        return artist?.takeIf { it.isNotBlank() }
    }
}
