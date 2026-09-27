package com.webdav.player.domain.model

data class RemoteDirectory(
    val path: String,
    val name: String,
    val subDirectories: List<RemoteDirectory> = emptyList(),
    val files: List<RemoteFile> = emptyList(),
) {
    val audioFiles: List<RemoteFile> get() = files.filter { it.isAudio }
    val isEmpty: Boolean get() = subDirectories.isEmpty() && files.isEmpty()

    companion object {
        fun normalizePath(path: String): String {
            var p = path.replace('\\', '/')
            while (p.contains("//")) {
                p = p.replace("//", "/")
            }
            if (!p.startsWith("/")) p = "/$p"
            if (!p.endsWith("/")) p = "$p/"
            while (p.contains("//")) {
                p = p.replace("//", "/")
            }
            return p
        }

        fun getParentPath(currentPath: String): String? {
            val normalized = normalizePath(currentPath)
            val cleanPath = normalized.trim('/')
            if (cleanPath.isEmpty()) return null

            val segments = cleanPath.split('/').filter { it.isNotBlank() }
            if (segments.size <= 1) return "/"

            return "/" + segments.dropLast(1).joinToString("/") + "/"
        }

        fun isAncestor(
            ancestorPath: String,
            currentPath: String,
        ): Boolean {
            val normAncestor = normalizePath(ancestorPath)
            val normCurrent = normalizePath(currentPath)
            if (normAncestor == normCurrent) return false
            return normCurrent.startsWith(normAncestor)
        }

        fun buildBreadcrumbs(
            server: WebDavServer?,
            path: String,
        ): List<Breadcrumb> {
            val rootName = server?.name?.takeIf { it.isNotBlank() } ?: "根目录"
            val list = mutableListOf(Breadcrumb(name = rootName, path = "/"))

            val normalized = normalizePath(path)
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
    }
}
