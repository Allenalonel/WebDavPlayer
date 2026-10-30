package com.webdav.player.domain.session

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.LyricsRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Internal collaborator managing reactive track metadata enrichment,
 * lyrics loading, and physical audio duration preservation across queue mutations.
 */
internal class PlaybackMetadataCoordinator(
    private val trackMetadataRepository: TrackMetadataRepository?,
    private val lyricsRepository: LyricsRepository?,
    private val playerEngine: AudioPlayerEngine,
    private val sessionStateFlow: MutableStateFlow<PlayerSessionState>,
    private val coroutineScope: CoroutineScope,
) {
    fun startMetadataObservation() {
        val repo = trackMetadataRepository ?: return
        coroutineScope.launch {
            var currentMetadataJob: Job? = null
            sessionStateFlow
                .map { it.activeServer?.id }
                .distinctUntilChanged()
                .collect { serverId ->
                    currentMetadataJob?.cancel()
                    if (serverId != null) {
                        currentMetadataJob =
                            coroutineScope.launch {
                                repo.getAllMetadataFlow(serverId).collect { metadataList ->
                                    if (metadataList.isNotEmpty()) {
                                        applyMetadataListToQueue(metadataList)
                                    }
                                }
                            }
                    }
                }
        }
    }

    fun startLyricsObservation() {
        val repo = lyricsRepository ?: return
        coroutineScope.launch {
            var lyricJob: Job? = null
            sessionStateFlow
                .map { state ->
                    val server = state.activeServer
                    val track = state.currentTrack
                    if (server != null && track != null) server to track else null
                }.distinctUntilChanged()
                .collect { pair ->
                    lyricJob?.cancel()
                    if (pair == null) {
                        sessionStateFlow.update { it.copy(lyrics = null, isLoadingLyrics = false) }
                    } else {
                        val (server, track) = pair
                        sessionStateFlow.update { it.copy(lyrics = null, isLoadingLyrics = true) }
                        lyricJob =
                            coroutineScope.launch {
                                val resolved = repo.resolveLyrics(server, track)
                                sessionStateFlow.update { it.copy(lyrics = resolved, isLoadingLyrics = false) }
                            }
                    }
                }
        }
    }

    fun enrichQueueTracksFromCache(
        server: WebDavServer,
        currentQueue: PlaybackQueue,
    ) {
        val repo = trackMetadataRepository ?: return
        coroutineScope.launch {
            val unpopulatedPaths = currentQueue.tracks.filter { it.coverThumbnailPath == null }.map { it.remotePath }
            if (unpopulatedPaths.isNotEmpty()) {
                val tracksToUpdate = mutableListOf<Pair<Int, AudioTrack>>()
                sessionStateFlow.update { state ->
                    var anyChanged = false
                    val updated =
                        state.queue.tracks.mapIndexed { index, track ->
                            if (track.coverThumbnailPath == null) {
                                val meta = repo.getCachedMetadata(server.id, track.remotePath)
                                if (meta != null) {
                                    val enriched = enrichTrack(track, meta, index == state.queue.currentIndex)
                                    if (enriched != track) {
                                        anyChanged = true
                                        tracksToUpdate.add(index to enriched)
                                        enriched
                                    } else {
                                        track
                                    }
                                } else {
                                    track
                                }
                            } else {
                                track
                            }
                        }
                    if (anyChanged) state.copy(queue = state.queue.copy(tracks = updated)) else state
                }
                val currentEnriched = tracksToUpdate.firstOrNull { it.first == currentQueue.currentIndex }?.second
                if (currentEnriched != null) {
                    playerEngine.updateTrackMetadata(currentEnriched)
                }
            }
        }
    }

    suspend fun enrichSingleTrackMetadata(
        server: WebDavServer?,
        track: AudioTrack,
    ) {
        val repo = trackMetadataRepository ?: return
        val s = server ?: return
        val cachedMeta = repo.getCachedMetadata(s.id, track.remotePath)
        val meta =
            cachedMeta ?: repo.resolveSingleTrackMetadata(s, RemoteFile(name = track.fileName, path = track.remotePath))
        var enrichedTrack: AudioTrack? = null

        sessionStateFlow.update { current ->
            var changed = false
            val updated =
                current.queue.tracks.map { t ->
                    if (t.id == track.id) {
                        val enriched = enrichTrack(t, meta, isCurrentActiveTrack = true)
                        if (enriched != t) {
                            changed = true
                            enrichedTrack = enriched
                            enriched
                        } else {
                            t
                        }
                    } else {
                        t
                    }
                }
            if (changed) current.copy(queue = current.queue.copy(tracks = updated)) else current
        }

        if (enrichedTrack != null) {
            playerEngine.updateTrackMetadata(enrichedTrack!!)
        }
    }

    fun enrichTrack(
        track: AudioTrack,
        meta: TrackMetadata,
        isCurrentActiveTrack: Boolean,
    ): AudioTrack {
        val enriched = track.withMetadata(meta)
        val physicalDuration =
            if (isCurrentActiveTrack) {
                playerEngine.durationMs.value.takeIf { it > 0L }
                    ?: sessionStateFlow.value.durationMs.takeIf { it > 0L }
            } else {
                null
            }
        return if (physicalDuration != null && physicalDuration > 0L) {
            enriched.copy(durationMs = physicalDuration)
        } else {
            enriched
        }
    }

    private fun applyMetadataListToQueue(metadataList: List<TrackMetadata>) {
        val metaMap = metadataList.associateBy { it.remotePath }
        val tracksToUpdate = mutableListOf<Pair<Int, AudioTrack>>()

        sessionStateFlow.update { current ->
            var anyChanged = false
            val updatedTracks =
                current.queue.tracks.mapIndexed { index, track ->
                    val meta = metaMap[track.remotePath]
                    if (meta != null) {
                        val enriched =
                            enrichTrack(track, meta, isCurrentActiveTrack = (index == current.queue.currentIndex))
                        if (enriched != track) {
                            anyChanged = true
                            tracksToUpdate.add(index to enriched)
                            enriched
                        } else {
                            track
                        }
                    } else {
                        track
                    }
                }
            if (anyChanged) current.copy(queue = current.queue.copy(tracks = updatedTracks)) else current
        }

        val currentQueueIndex = sessionStateFlow.value.queue.currentIndex
        val currentEnriched = tracksToUpdate.firstOrNull { it.first == currentQueueIndex }?.second
        if (currentEnriched != null) {
            playerEngine.updateTrackMetadata(currentEnriched)
        }
    }
}
