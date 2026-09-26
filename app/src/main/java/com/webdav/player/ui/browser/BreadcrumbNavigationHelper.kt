package com.webdav.player.ui.browser

import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.WebDavServer

object BreadcrumbNavigationHelper {

    fun normalizeDirectoryPath(path: String): String {
        var p = path.replace('\\', '/')
        // Remove redundant consecutive slashes
        while (p.contains("//")) {
            p = p.replace("//", "/")
        }
        if (!p.startsWith("/")) p = "/$p"
        if (!p.endsWith("/")) p = "$p/"
        return p
    }

    fun buildBreadcrumbs(server: WebDavServer?, path: String): List<Breadcrumb> {
        val rootName = server?.name?.takeIf { it.isNotBlank() } ?: "根目录"
        val list = mutableListOf(Breadcrumb(name = rootName, path = "/"))

        val normalized = normalizeDirectoryPath(path)
        val cleanPath = normalized.trim('/')
        if (cleanPath.isEmpty()) return list

        val segments = cleanPath.split('/').filter { it.isNotBlank() }
        var accumulated = ""
        for (segment in segments) {
            accumulated += "/$segment"
            list.add(Breadcrumb(name = segment, path = "$accumulated/"))
        }
        return list
    }

    fun getParentPath(currentPath: String): String? {
        val normalized = normalizeDirectoryPath(currentPath)
        val cleanPath = normalized.trim('/')
        if (cleanPath.isEmpty()) return null

        val segments = cleanPath.split('/').filter { it.isNotBlank() }
        if (segments.size <= 1) return "/"

        return "/" + segments.dropLast(1).joinToString("/") + "/"
    }

    fun isAncestor(ancestorPath: String, currentPath: String): Boolean {
        val normAncestor = normalizeDirectoryPath(ancestorPath)
        val normCurrent = normalizeDirectoryPath(currentPath)
        if (normAncestor == normCurrent) return false
        return normCurrent.startsWith(normAncestor)
    }
}
