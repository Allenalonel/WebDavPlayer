package com.webdav.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.domain.model.InactiveReason
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.model.PlaylistTrackEntry
import com.webdav.player.domain.repository.PlaylistRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.session.MusicPlayerAppSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaylistSortOption(
    val displayName: String,
) {
    CUSTOM("自定义排序"),
    DATE_ADDED_DESC("添加时间（最新优先）"),
    DATE_ADDED_ASC("添加时间（最早优先）"),
    TITLE_ASC("歌曲标题（A-Z）"),
    ARTIST_ASC("艺术家（A-Z）"),
}

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<PlaylistTrackEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isReordering: Boolean = false,
    val activeSortOption: PlaylistSortOption = PlaylistSortOption.CUSTOM,
    val errorMessage: String? = null,
)

class PlaylistDetailViewModel(
    val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val musicPlayerAppSession: MusicPlayerAppSession? = null,
    private val serverRepository: ServerRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistDetailUiState(isLoading = true))
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        playlistRepository
            .getPlaylistFlow(playlistId)
            .onEach { playlist ->
                _uiState.update { it.copy(playlist = playlist) }
            }.catch { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "加载歌单失败") }
            }.launchIn(viewModelScope)

        val rawTracksFlow = playlistRepository.getTracksForPlaylistFlow(playlistId)
        val tracksFlow: Flow<List<PlaylistTrackEntry>> =
            if (serverRepository != null) {
                combine(rawTracksFlow, serverRepository.getAllServers()) { tracks, servers ->
                    val serverMap = servers.associateBy { it.id }
                    tracks.map { track ->
                        val server = serverMap[track.serverId]
                        when {
                            server == null || server.url.isBlank() -> {
                                track.copy(isInactive = true, inactiveReason = InactiveReason.SERVER_UNREACHABLE)
                            }
                            track.isInactive -> {
                                track
                            }
                            else -> {
                                track
                            }
                        }
                    }
                }
            } else {
                rawTracksFlow
            }

        tracksFlow
            .onEach { tracks ->
                _uiState.update { current ->
                    if (current.isReordering) {
                        current.copy(isLoading = false)
                    } else {
                        current.copy(tracks = tracks, isLoading = false)
                    }
                }
            }.catch { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "加载歌曲失败", isLoading = false) }
            }.launchIn(viewModelScope)
    }

    fun moveTrack(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val currentTracks = _uiState.value.tracks.toMutableList()
        if (fromIndex !in currentTracks.indices || toIndex !in currentTracks.indices || fromIndex == toIndex) {
            return
        }
        val moved = currentTracks.removeAt(fromIndex)
        currentTracks.add(toIndex, moved)
        _uiState.update {
            it.copy(
                tracks = currentTracks,
                isReordering = true,
                activeSortOption = PlaylistSortOption.CUSTOM,
            )
        }
    }

    fun onDragEnd() {
        val currentTracks = _uiState.value.tracks
        _uiState.update { it.copy(isReordering = false) }
        viewModelScope.launch {
            try {
                val orderedIds = currentTracks.map { it.id }
                playlistRepository.updateTrackOrders(playlistId, orderedIds)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "保存排序失败") }
            }
        }
    }

    fun updateTrackOrders(orderedTrackIds: List<Long>) {
        val currentMap = _uiState.value.tracks.associateBy { it.id }
        val reordered =
            orderedTrackIds.mapIndexedNotNull { index, id ->
                currentMap[id]?.copy(sortOrder = index)
            }
        _uiState.update {
            it.copy(
                tracks = reordered,
                isReordering = false,
                activeSortOption = PlaylistSortOption.CUSTOM,
            )
        }
        viewModelScope.launch {
            try {
                playlistRepository.updateTrackOrders(playlistId, orderedTrackIds)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "保存排序失败") }
            }
        }
    }

    fun sortTracks(sortOption: PlaylistSortOption) {
        val currentTracks = _uiState.value.tracks
        if (currentTracks.isEmpty()) {
            _uiState.update { it.copy(activeSortOption = sortOption) }
            return
        }

        val sortedTracks =
            when (sortOption) {
                PlaylistSortOption.CUSTOM -> {
                    currentTracks
                }

                PlaylistSortOption.DATE_ADDED_DESC -> {
                    currentTracks.sortedWith(
                        compareByDescending<PlaylistTrackEntry> { it.id },
                    )
                }

                PlaylistSortOption.DATE_ADDED_ASC -> {
                    currentTracks.sortedWith(
                        compareBy<PlaylistTrackEntry> { it.id },
                    )
                }

                PlaylistSortOption.TITLE_ASC -> {
                    currentTracks.sortedWith(
                        compareBy<PlaylistTrackEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                            .thenBy { it.id },
                    )
                }

                PlaylistSortOption.ARTIST_ASC -> {
                    currentTracks.sortedWith(
                        compareBy<PlaylistTrackEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.orEmpty() }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                            .thenBy { it.id },
                    )
                }
            }

        val reindexed = sortedTracks.mapIndexed { index, track -> track.copy(sortOrder = index) }
        _uiState.update {
            it.copy(
                tracks = reindexed,
                isReordering = false,
                activeSortOption = sortOption,
            )
        }

        viewModelScope.launch {
            try {
                playlistRepository.updateTrackOrders(playlistId, reindexed.map { it.id })
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "更新排序失败") }
            }
        }
    }

    fun removeTrack(trackId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.removeTrackFromPlaylist(trackId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "移除歌曲失败") }
            }
        }
    }

    fun playTrack(track: PlaylistTrackEntry) {
        val index = _uiState.value.tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        onTrackClick(track, index)
    }

    fun playAll() {
        val playlist = _uiState.value.playlist ?: return
        if (_uiState.value.tracks.isEmpty()) return
        musicPlayerAppSession?.playPlaylist(playlist, 0)
    }

    fun onTrackClick(
        track: PlaylistTrackEntry,
        index: Int = _uiState.value.tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
    ) {
        if (track.isInactive) {
            val message =
                when (track.inactiveReason) {
                    InactiveReason.SERVER_UNREACHABLE -> "所属服务器无法连接"
                    InactiveReason.FILE_NOT_FOUND -> "音频文件已被重命名或删除"
                    InactiveReason.SERVER_CREDENTIAL_INVALID -> "所属服务器无法连接"
                    null -> "音频文件已被重命名或删除"
                }
            _toastEvent.tryEmit(message)
            return
        }

        val playlist = _uiState.value.playlist
        if (playlist != null) {
            musicPlayerAppSession?.playPlaylist(playlist, index)
        } else {
            musicPlayerAppSession?.playTrack(track.toAudioTrack())
        }
    }

    fun cleanInactiveTracks() {
        val inactiveTrackIds = _uiState.value.tracks.filter { it.isInactive }.map { it.id }
        if (inactiveTrackIds.isEmpty()) return
        viewModelScope.launch {
            try {
                playlistRepository.removeTracksFromPlaylist(playlistId, inactiveTrackIds)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "清理失效歌曲失败") }
            }
        }
    }

    fun playNext(track: PlaylistTrackEntry) {
        musicPlayerAppSession?.playNext(track.toAudioTrack())
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
