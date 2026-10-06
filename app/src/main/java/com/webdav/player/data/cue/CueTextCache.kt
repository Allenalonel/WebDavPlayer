package com.webdav.player.data.cue

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, in-memory cache for raw CUE sheet text content.
 * Keeps CUE content transiently in memory without writing to persistent disk storage,
 * strictly abiding by zero-disk-retention streaming principles.
 */
object CueTextCache {
    private val cache = ConcurrentHashMap<String, String>()

    private fun cacheKey(
        serverId: Long?,
        cuePath: String,
    ): String = "${serverId ?: 0L}:$cuePath"

    fun put(
        serverId: Long?,
        cuePath: String,
        content: String,
    ) {
        if (content.isNotBlank()) {
            cache[cacheKey(serverId, cuePath)] = content
        }
    }

    fun get(
        serverId: Long?,
        cuePath: String,
    ): String? = cache[cacheKey(serverId, cuePath)]

    fun remove(
        serverId: Long?,
        cuePath: String,
    ) {
        cache.remove(cacheKey(serverId, cuePath))
    }

    fun clear() {
        cache.clear()
    }
}
