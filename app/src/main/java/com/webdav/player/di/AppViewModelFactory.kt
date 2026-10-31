package com.webdav.player.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.ui.browser.DirectoryBrowserViewModel
import com.webdav.player.ui.playlist.PlaylistDetailViewModel
import com.webdav.player.ui.playlist.PlaylistListViewModel
import com.webdav.player.ui.server.ServerManagementViewModel

/**
 * Unified ViewModel factory resolving presentation ViewModels via type-safe dispatch
 * from the centralized [AppContainer].
 */
class AppViewModelFactory(
    private val container: AppContainer,
    private val playlistId: Long? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(DirectoryBrowserViewModel::class.java) -> {
                DirectoryBrowserViewModel(
                    serverRepository = container.serverRepository,
                    directoryRepository = container.directoryRepository,
                    musicPlayerAppSession = container.musicPlayerAppSession,
                    librarySearchRepository = container.librarySearchRepository,
                ) as T
            }

            modelClass.isAssignableFrom(ServerManagementViewModel::class.java) -> {
                ServerManagementViewModel(
                    repository = container.serverRepository,
                    webDavClient = container.webDavClient,
                ) as T
            }

            modelClass.isAssignableFrom(PlaylistListViewModel::class.java) -> {
                PlaylistListViewModel(
                    playlistRepository = container.playlistRepository,
                ) as T
            }

            modelClass.isAssignableFrom(PlaylistDetailViewModel::class.java) -> {
                val id = playlistId ?: throw IllegalArgumentException("playlistId is required to create PlaylistDetailViewModel")
                PlaylistDetailViewModel(
                    playlistId = id,
                    playlistRepository = container.playlistRepository,
                    musicPlayerAppSession = container.musicPlayerAppSession,
                ) as T
            }

            else -> {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
}
