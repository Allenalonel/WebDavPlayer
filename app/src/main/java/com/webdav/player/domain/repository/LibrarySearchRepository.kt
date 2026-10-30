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

    /**
     * Retrieves recent search history queries ordered by most recent first.
     */
    fun getSearchHistory(): List<String> = emptyList()

    /**
     * Records a submitted search query into persistent history.
     * Trims leading/trailing whitespace, moves existing matching query to the front (LRU),
     * caps history at 20 queries, and returns the updated history list.
     * Empty or blank queries are ignored.
     */
    fun recordSearchQuery(query: String): List<String> = emptyList()

    /**
     * Removes a specific query from search history and returns the updated history list.
     */
    fun removeSearchHistory(query: String): List<String> = emptyList()

    /**
     * Clears all search history queries.
     */
    fun clearSearchHistory() {}
}
