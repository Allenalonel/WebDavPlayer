package com.webdav.player.domain.repository

import com.webdav.player.domain.model.LibrarySearchResult
import kotlinx.coroutines.flow.Flow

interface LibrarySearchRepository {
    /**
     * Searches locally cached directory snapshots and track metadata.
     *
     * @param query Search keywords (case-insensitive fuzzy/prefix matching across name, title, artist, album).
     * @param serverId Optional filter by WebDAV server ID. If null, searches across all cached servers.
     * @return Flow emitting [LibrarySearchResult] containing matching directories and audio tracks.
     */
    fun search(
        query: String,
        serverId: Long? = null,
    ): Flow<LibrarySearchResult>
}
