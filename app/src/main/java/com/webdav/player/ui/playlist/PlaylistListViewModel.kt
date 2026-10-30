package com.webdav.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Playlist
import com.webdav.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val playlistToRename: Playlist? = null,
    val playlistToDelete: Playlist? = null,
    val errorMessage: String? = null,
)

class PlaylistListViewModel(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistListUiState(isLoading = true))
    val uiState: StateFlow<PlaylistListUiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        playlistRepository
            .getAllPlaylistsFlow()
            .onEach { playlists ->
                _uiState.update { it.copy(playlists = playlists, isLoading = false) }
            }.catch { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "加载歌单失败", isLoading = false) }
            }.launchIn(viewModelScope)
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, errorMessage = null) }
    }

    fun closeCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, errorMessage = null) }
    }

    fun createPlaylist(
        name: String,
        onSuccess: ((Long) -> Unit)? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "歌单名称不能为空") }
            return
        }

        viewModelScope.launch {
            try {
                if (playlistRepository.isPlaylistNameExists(trimmed)) {
                    _uiState.update { it.copy(errorMessage = "歌单 “$trimmed” 已存在") }
                    return@launch
                }
                val newId = playlistRepository.createPlaylist(trimmed)
                _uiState.update { it.copy(showCreateDialog = false, errorMessage = null) }
                onSuccess?.invoke(newId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "创建歌单失败") }
            }
        }
    }

    fun createPlaylistAndAddTrack(
        name: String,
        track: AudioTrack,
        cuePath: String? = null,
        onSuccess: (() -> Unit)? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "歌单名称不能为空") }
            return
        }

        viewModelScope.launch {
            try {
                if (playlistRepository.isPlaylistNameExists(trimmed)) {
                    _uiState.update { it.copy(errorMessage = "歌单 “$trimmed” 已存在") }
                    return@launch
                }
                val newId = playlistRepository.createPlaylist(trimmed)
                val effectiveCue = cuePath ?: track.cuePath
                playlistRepository.addTrackToPlaylist(newId, track, cuePath = effectiveCue)
                _uiState.update { it.copy(showCreateDialog = false, errorMessage = null) }
                onSuccess?.invoke()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "创建并添加失败") }
            }
        }
    }

    fun addTrackToPlaylist(
        playlistId: Long,
        track: AudioTrack,
        cuePath: String? = null,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                val effectiveCue = cuePath ?: track.cuePath
                val insertedId = playlistRepository.addTrackToPlaylist(playlistId, track, cuePath = effectiveCue)
                onResult?.invoke(insertedId > 0L)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "添加歌曲失败") }
            }
        }
    }

    fun openRenameDialog(playlist: Playlist) {
        _uiState.update { it.copy(playlistToRename = playlist, errorMessage = null) }
    }

    fun closeRenameDialog() {
        _uiState.update { it.copy(playlistToRename = null, errorMessage = null) }
    }

    fun renamePlaylist(
        playlistId: Long,
        newName: String,
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "歌单名称不能为空") }
            return
        }

        viewModelScope.launch {
            try {
                if (playlistRepository.isPlaylistNameExists(trimmed, excludePlaylistId = playlistId)) {
                    _uiState.update { it.copy(errorMessage = "歌单 “$trimmed” 已存在") }
                    return@launch
                }
                playlistRepository.renamePlaylist(playlistId, trimmed)
                _uiState.update { it.copy(playlistToRename = null, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "重命名失败") }
            }
        }
    }

    fun openDeleteDialog(playlist: Playlist) {
        _uiState.update { it.copy(playlistToDelete = playlist, errorMessage = null) }
    }

    fun closeDeleteDialog() {
        _uiState.update { it.copy(playlistToDelete = null, errorMessage = null) }
    }

    fun confirmDeletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.deletePlaylist(playlistId)
                _uiState.update { it.copy(playlistToDelete = null, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "删除歌单失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
