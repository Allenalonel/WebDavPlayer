package com.webdav.player.ui.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.WebDavApplication
import com.webdav.player.domain.repository.PlaylistRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.session.MusicPlayerAppSession

class PlaylistDetailViewModelFactory(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository? = null,
    private val musicPlayerAppSession: MusicPlayerAppSession? = null,
    private val serverRepository: ServerRepository? = null,
    private val context: Context? = null,
) : ViewModelProvider.Factory {
    constructor(context: Context, playlistId: Long) : this(
        playlistId = playlistId,
        playlistRepository = null,
        musicPlayerAppSession = null,
        serverRepository = null,
        context = context,
    )

    constructor(
        playlistId: Long,
        playlistRepository: PlaylistRepository,
        musicPlayerAppSession: MusicPlayerAppSession? = null,
        serverRepository: ServerRepository? = null,
    ) : this(
        playlistId = playlistId,
        playlistRepository = playlistRepository,
        musicPlayerAppSession = musicPlayerAppSession,
        serverRepository = serverRepository,
        context = null,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo =
            playlistRepository ?: run {
                val app =
                    context?.applicationContext as? WebDavApplication
                        ?: throw IllegalStateException("Application context or PlaylistRepository required")
                app.playlistRepository
            }
        val session =
            musicPlayerAppSession ?: run {
                val app = context?.applicationContext as? WebDavApplication
                app?.musicPlayerAppSession
            }
        val serverRepo =
            serverRepository ?: run {
                val app = context?.applicationContext as? WebDavApplication
                app?.serverRepository
            }
        return PlaylistDetailViewModel(
            playlistId = playlistId,
            playlistRepository = repo,
            musicPlayerAppSession = session,
            serverRepository = serverRepo,
        ) as T
    }
}
