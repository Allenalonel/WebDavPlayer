package com.webdav.player.domain.model

enum class QueueOrigin {
    DIRECTORY,
    PLAYLIST,
    AD_HOC,
    ;

    companion object {
        val DirectoryQueue = DIRECTORY
        val PlaylistQueue = PLAYLIST
        val AdHocQueue = AD_HOC
    }
}
