package com.webdav.player.domain.model

data class RemoteDirectory(
    val path: String,
    val name: String,
    val subDirectories: List<RemoteDirectory> = emptyList(),
    val files: List<RemoteFile> = emptyList()
) {
    val audioFiles: List<RemoteFile> get() = files.filter { it.isAudio }
    val isEmpty: Boolean get() = subDirectories.isEmpty() && files.isEmpty()
}
