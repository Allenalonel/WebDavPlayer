package com.webdav.player.data.metadata

data class ParsedAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val durationMs: Long = 0L,
    val artworkData: ByteArray? = null,
    val artworkMimeType: String? = null,
    val lyrics: String? = null
) {
    val hasTags: Boolean
        get() = !title.isNullOrBlank() ||
                !artist.isNullOrBlank() ||
                !album.isNullOrBlank() ||
                trackNumber != null ||
                durationMs > 0L ||
                artworkData != null ||
                !lyrics.isNullOrBlank()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedAudioMetadata

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (trackNumber != other.trackNumber) return false
        if (durationMs != other.durationMs) return false
        if (artworkData != null) {
            if (other.artworkData == null) return false
            if (!artworkData.contentEquals(other.artworkData)) return false
        } else if (other.artworkData != null) return false
        if (artworkMimeType != other.artworkMimeType) return false
        if (lyrics != other.lyrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + (artworkData?.contentHashCode() ?: 0)
        result = 31 * result + (artworkMimeType?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        return result
    }
}
