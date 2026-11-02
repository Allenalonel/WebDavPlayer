package com.webdav.player.di

import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.cue.CueSheetResolver
import com.webdav.player.domain.network.NetworkConnectivityObserver
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.LibrarySearchRepository
import com.webdav.player.domain.repository.PlaybackSessionStore
import com.webdav.player.domain.repository.PlaylistRepository
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import com.webdav.player.domain.session.MusicPlayerAppSession

/**
 * Unified application-level dependency container providing clean access to core singletons
 * across ViewModels, session hosts, and platform components.
 */
interface AppContainer {
    val serverRepository: ServerRepository
    val directoryRepository: DirectoryRepository
    val librarySearchRepository: LibrarySearchRepository
    val playlistRepository: PlaylistRepository
    val trackMetadataRepository: TrackMetadataRepository
    val cueSheetResolver: CueSheetResolver
    val playerEngine: AudioPlayerEngine
    val musicPlayerAppSession: MusicPlayerAppSession
    val sessionStore: PlaybackSessionStore
    val webDavClient: OkHttpWebDavClient
    val networkConnectivityObserver: NetworkConnectivityObserver
}
