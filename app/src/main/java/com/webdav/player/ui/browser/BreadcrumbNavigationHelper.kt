package com.webdav.player.ui.browser

import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer

object BreadcrumbNavigationHelper {
    fun normalizeDirectoryPath(path: String): String = RemoteDirectory.normalizePath(path)

    fun buildBreadcrumbs(
        server: WebDavServer?,
        path: String,
    ): List<Breadcrumb> = RemoteDirectory.buildBreadcrumbs(server, path)

    fun getParentPath(currentPath: String): String? = RemoteDirectory.getParentPath(currentPath)

    fun isAncestor(
        ancestorPath: String,
        currentPath: String,
    ): Boolean = RemoteDirectory.isAncestor(ancestorPath, currentPath)
}
