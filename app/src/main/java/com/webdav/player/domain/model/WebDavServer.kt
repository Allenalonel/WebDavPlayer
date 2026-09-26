package com.webdav.player.domain.model

data class WebDavServer(
    val id: Long = 0L,
    val name: String,
    val url: String,
    val port: Int = 80,
    val pathPrefix: String = "/",
    val username: String = "",
    val password: String = "",
    val allowSelfSigned: Boolean = false,
    val isDefault: Boolean = false
) {
    /**
     * Resolves the full WebDAV endpoint URL.
     */
    val endpointUrl: String
        get() {
            var rawUrl = url.trim()
            val scheme = if (rawUrl.startsWith("https://", ignoreCase = true)) {
                "https"
            } else if (rawUrl.startsWith("http://", ignoreCase = true)) {
                "http"
            } else {
                if (port == 443) "https" else "http"
            }

            rawUrl = rawUrl.removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
            // If host has a path, separate host from path
            val slashIndex = rawUrl.indexOf('/')
            val hostAndMaybePort = if (slashIndex != -1) rawUrl.substring(0, slashIndex) else rawUrl
            val host = if (hostAndMaybePort.contains(":")) hostAndMaybePort.substringBefore(":") else hostAndMaybePort

            val cleanPrefix = if (pathPrefix.isBlank()) "/" else {
                var p = pathPrefix.trim()
                if (!p.startsWith("/")) p = "/$p"
                p
            }

            val isStandardPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            return if (isStandardPort) {
                "$scheme://$host$cleanPrefix"
            } else {
                "$scheme://$host:$port$cleanPrefix"
            }
        }

    /**
     * Whether this server uses secure HTTPS protocol.
     */
    val isHttps: Boolean
        get() = endpointUrl.startsWith("https://", ignoreCase = true)

    /**
     * Protocol badge string ("HTTP" or "HTTPS").
     */
    val protocol: String
        get() = if (isHttps) "HTTPS" else "HTTP"

    /**
     * Concise host summary for modern MD3 card presentation (host, optional port, and prefix).
     */
    val hostSummary: String
        get() {
            val endpoint = endpointUrl
            val withoutScheme = endpoint.substringAfter("://")
            return withoutScheme.trimEnd('/')
        }

    /**
     * Resolves the full URL for a remote file path on this server.
     */
    fun resolveFileUrl(filePath: String): String {
        val baseUrl = endpointUrl.trimEnd('/')
        var cleanPath = filePath.replace('\\', '/')
        if (!cleanPath.startsWith("/")) cleanPath = "/$cleanPath"
        val segments = cleanPath.split('/').filter { it.isNotEmpty() }
        val encodedPath = segments.joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return "$baseUrl/$encodedPath"
    }
}
