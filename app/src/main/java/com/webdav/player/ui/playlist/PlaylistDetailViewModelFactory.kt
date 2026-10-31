package com.webdav.player.ui.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.WebDavApplication
import com.webdav.player.di.AppViewModelFactory
import com.webdav.player.domain.repository.PlaylistRepository
import com.webdav.player.domain.session.MusicPlayerAppSession

class PlaylistDetailViewModelFactory(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository? = null,
    private val musicPlayerAppSession: MusicPlayerAppSession? = null,
    private val context: Context? = null,
) : ViewModelProvider.Factory {
    constructor(context: Context, playlistId: Long) : this(
        playlistId = playlistId,
        playlistRepository = null,
        musicPlayerAppSession = null,
        context = context,
    )

    constructor(
        playlistId: Long,
        playlistRepository: PlaylistRepository,
        musicPlayerAppSession: MusicPlayerAppSession? = null,
    ) : this(
        playlistId = playlistId,
        playlistRepository = playlistRepository,
        musicPlayerAppSession = musicPlayerAppSession,
        context = null,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (context != null) {
            val app =
                requireNotNull(context.applicationContext as? WebDavApplication) {
                    "Application context must be an instance of WebDavApplication"
                }
            return AppViewModelFactory(app, playlistId).create(modelClass)
        }
        val repo =
            requireNotNull(playlistRepository) {
                "PlaylistRepository or Context required"
            }
        return PlaylistDetailViewModel(
            playlistId = playlistId,
            playlistRepository = repo,
            musicPlayerAppSession = musicPlayerAppSession,
        ) as T
    }
}
