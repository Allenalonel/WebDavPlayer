package com.webdav.player.domain.repository

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Lyrics
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.flow.Flow

interface TrackMetadataRepository {
    /**
     * Observes all resolved metadata for a given server.
     */
    fun getAllMetadataFlow(serverId: Long): Flow<List<TrackMetadata>>

    /**
     * Observes resolved metadata for a list of remote paths on a given server.
     */
    fun getMetadataForPathsFlow(
        serverId: Long,
        remotePaths: List<String>,
    ): Flow<List<TrackMetadata>>

    /**
     * Observes resolved metadata for a specific path on a given server.
     */
    fun getMetadataFlow(
        serverId: Long,
        remotePath: String,
    ): Flow<TrackMetadata?>

    /**
     * Retrieves cached metadata directly from the local Room database, or null if not resolved.
     */
    suspend fun getCachedMetadata(
        serverId: Long,
        remotePath: String,
    ): TrackMetadata?

    /**
     * Resolves metadata for remote audio files in the background with bounded concurrency.
     * Uses Room cache to skip previously parsed files, and executes HTTP Range requests for new ones.
     */
    suspend fun resolveMetadata(
        server: WebDavServer,
        files: List<RemoteFile>,
        forceRefresh: Boolean = false,
    )

    /**
     * Resolves metadata for a single audio file on demand.
     */
    suspend fun resolveSingleTrackMetadata(
        server: WebDavServer,
        file: RemoteFile,
    ): TrackMetadata

    /**
     * Resolves lyrics for an audio track using dual-source priority:
     * 1. Probes remote directory for ${baseName}.lrc via WebDAV GET.
     * 2. If remote .lrc is not found (404/error), falls back to embedded metadata
     *    (ID3 USLT/SYLT, Vorbis comment, ASF WM/Lyrics).
     * 3. Returns Lyrics.EMPTY if neither source provides lyrics.
     */
    suspend fun resolveLyrics(
        server: WebDavServer,
        track: AudioTrack,
    ): Lyrics = Lyrics.EMPTY

    /**
     * Clears all in-memory metadata and lyrics caches.
     */
    fun clearCache() {}
}
