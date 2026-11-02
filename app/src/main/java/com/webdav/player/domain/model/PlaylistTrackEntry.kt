package com.webdav.player.domain.model

data class PlaylistTrackEntry(
    val id: Long = 0L,
    val playlistId: Long,
    val serverId: Long,
    val remotePath: String,
    val sortOrder: Int,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val size: Long = 0L,
    val format: AudioFormat = AudioFormat.MP3,
    val coverThumbnailPath: String? = null,
    val cuePath: String? = null,
    val virtualTrackId: String? = null,
    val isInactive: Boolean = false,
    val inactiveReason: InactiveReason? = null,
) {
    val isVirtualTrack: Boolean
        get() = virtualTrackId != null || remotePath.contains("#cue_")

    fun toAudioTrack(): AudioTrack {
        val trackId = virtualTrackId ?: "$serverId:$remotePath"
        return AudioTrack(
            id = trackId,
            serverId = serverId,
            remotePath = remotePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            size = size,
            format = format,
            coverThumbnailPath = coverThumbnailPath,
            cuePath = cuePath,
        )
    }

    companion object {
        fun fromAudioTrack(
            playlistId: Long,
            track: AudioTrack,
            sortOrder: Int,
            cuePath: String? = null,
        ): PlaylistTrackEntry =
            PlaylistTrackEntry(
                id = 0L,
                playlistId = playlistId,
                serverId = track.serverId,
                remotePath = track.remotePath,
                sortOrder = sortOrder,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                size = track.size,
                format = track.format,
                coverThumbnailPath = track.coverThumbnailPath,
                cuePath = cuePath ?: track.cuePath,
                virtualTrackId = if (track.isVirtualTrack) track.id else null,
            )
    }
}

enum class InactiveReason {
    SERVER_UNREACHABLE,
    FILE_NOT_FOUND,
    SERVER_CREDENTIAL_INVALID,
}
