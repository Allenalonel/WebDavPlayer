package com.webdav.player.domain.cue

import com.webdav.player.domain.model.CueAlbumItem
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.VirtualTrack
import com.webdav.player.domain.model.WebDavServer

/**
 * Deep domain seam for CUE sheet discovery, association heuristics matching with
 * physical parent audio files, AST parsing, and transient in-memory LRU caching.
 *
 * Encapsulates all CUE sheet network operations and parsing pipelines behind a minimal,
 * highly-leveraged domain interface, strictly honoring zero persistent disk retention (ADR-0002).
 */
interface CueSheetResolver {
    /**
     * Discovers, caches, associates, and parses all CUE sheet albums within a remote directory.
     *
     * @param server The target [WebDavServer].
     * @param directory The remote directory containing files.
     * @param parentDurationsByPath Known durations of audio files in milliseconds, keyed by remote path.
     * @return Ordered list of [CueAlbumItem] entities.
     */
    suspend fun resolveDirectoryCueAlbums(
        server: WebDavServer,
        directory: RemoteDirectory,
        parentDurationsByPath: Map<String, Long> = emptyMap(),
    ): List<CueAlbumItem>

    /**
     * Resolves virtual tracks for a specific CUE sheet and parent audio file.
     *
     * @param server The target [WebDavServer].
     * @param cuePath Remote path to the .cue file.
     * @param parentAudioPath Remote path to the corresponding physical audio file.
     * @param totalDurationMs Optional total duration of the parent audio file in milliseconds.
     * @return List of [VirtualTrack] entities, or empty list if resolution fails.
     */
    suspend fun resolveVirtualTracks(
        server: WebDavServer,
        cuePath: String,
        parentAudioPath: String,
        totalDurationMs: Long? = null,
    ): List<VirtualTrack>

    /**
     * Clears transient CUE text from the internal in-memory LRU cache.
     */
    fun clearCache()
}
