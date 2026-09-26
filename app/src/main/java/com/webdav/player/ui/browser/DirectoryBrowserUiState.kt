package com.webdav.player.ui.browser

import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer

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
    val metadataMap: Map<String, TrackMetadata> = emptyMap()
) {
    val isEmpty: Boolean
        get() = currentDirectory?.isEmpty == true

    val subDirectories: List<RemoteDirectory>
        get() = currentDirectory?.subDirectories ?: emptyList()

    val files: List<RemoteFile>
        get() = currentDirectory?.files ?: emptyList()
}
