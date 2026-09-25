package com.webdav.player.domain.model

data class RemoteFile(
    val name: String,
    val path: String,
    val size: Long = 0L,
    val lastModified: String? = null,
    val contentType: String? = null,
    val fileType: RemoteFileType = RemoteFileType.fromFileName(name)
) {
    val isAudio: Boolean get() = fileType is RemoteFileType.Audio
    val isLyrics: Boolean get() = fileType is RemoteFileType.Lyrics
}
