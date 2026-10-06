package com.webdav.player.ui.browser

import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.ui.browser.components.EqualizerStateHelper

data class DirectoryBrowserUiState(
    val isInitializing: Boolean = true,
    val activeServer: WebDavServer? = null,
    val currentPath: String = "/",
    val breadcrumbs: List<Breadcrumb> = listOf(Breadcrumb(name = "根目录", path = "/")),
    val currentDirectory: RemoteDirectory? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val canNavigateUp: Boolean = false,
    val metadataMap: Map<String, TrackMetadata> = emptyMap(),
    val activeTrackPath: String? = null,
    val activeTrackId: String? = null,
    val isPlaying: Boolean = false,
    val cueAlbums: List<CueAlbumItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = currentDirectory?.isEmpty == true

    val subDirectories: List<RemoteDirectory>
        get() = currentDirectory?.subDirectories ?: emptyList()

    val files: List<RemoteFile>
        get() = currentDirectory?.files ?: emptyList()

    val headerTitle: String
        get() =
            if (isInitializing) {
                "媒体库"
            } else if (currentPath == "/") {
                activeServer?.name ?: "远程目录"
            } else {
                currentDirectory?.name
                    ?: currentPath.trimEnd('/').substringAfterLast('/')
            }

    fun isTrackActive(filePath: String): Boolean = EqualizerStateHelper.isTrackActive(filePath, activeTrackPath)

    fun isCueAlbumActive(cueAlbum: CueAlbumItem): Boolean = cueAlbum.isAlbumActive(activeTrackPath)

    fun isVirtualTrackActive(
        cueAlbum: CueAlbumItem,
        track: VirtualTrack,
    ): Boolean = cueAlbum.isVirtualTrackActive(activeTrackPath, activeTrackId, track)
}
