package com.webdav.player.domain.metadata

import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer

/**
 * Deep module interface for resolving remote track metadata across the WebDAV network seam.
 *
 * Encapsulates:
 * - Adaptive 512KB HTTP Range chunk fetching and two-stage stitching (up to 8MB)
 * - Audio tag parsing across formats (ID3v2, FLAC, WAV, ASF/WMA)
 * - Dual-source artwork extraction (embedded tag artwork prioritized, fallback to track-specific
 *   and folder-level artwork candidates with folder-scoped concurrency locks)
 * - Complete image validation and local thumbnail storage
 *
 * Returns null on transient network failures to prevent poisoning upstream persistent caches.
 */
interface TrackMetadataResolver {
    suspend fun resolve(
        server: WebDavServer,
        file: RemoteFile,
    ): TrackMetadata?

    /**
     * Clears in-memory caches such as folder artwork resolution states and locks.
     */
    fun clearCache() {}
}
