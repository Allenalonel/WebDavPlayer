package com.webdav.player.domain.model

data class AudioTrack(
    val id: String,
    val serverId: Long,
    val remotePath: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val size: Long = 0L,
    val format: AudioFormat
) {
    fun streamUrl(server: WebDavServer): String {
        return server.resolveFileUrl(remotePath)
    }

    companion object {
        fun fromRemoteFile(server: WebDavServer, file: RemoteFile): AudioTrack? {
            val format = (file.fileType as? RemoteFileType.Audio)?.format ?: return null
            return AudioTrack(
                id = "${server.id}:${file.path}",
                serverId = server.id,
                remotePath = file.path,
                title = file.name,
                artist = null,
                album = null,
                durationMs = 0L,
                size = file.size,
                format = format
            )
        }
    }
}
