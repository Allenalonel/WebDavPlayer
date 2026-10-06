package com.webdav.player

import android.app.Application
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorageImpl
import com.webdav.player.data.player.DefaultWebDavMediaSourceAdapter
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.DataStorePlaybackSessionStore
import com.webdav.player.data.repository.DirectoryRepositoryImpl
import com.webdav.player.data.repository.LyricsRepositoryImpl
import com.webdav.player.data.repository.ServerRepositoryImpl
import com.webdav.player.data.repository.TrackMetadataRepositoryImpl
import com.webdav.player.data.service.PlaybackSessionHost
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.LyricsRepository
import com.webdav.player.domain.repository.PlaybackSessionStore
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import com.webdav.player.domain.session.MusicPlayerAppSession
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl

class WebDavApplication : Application() {
    lateinit var serverRepository: ServerRepository
        private set

    lateinit var webDavClient: OkHttpWebDavClient
        private set

    lateinit var trackMetadataRepository: TrackMetadataRepository
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    lateinit var sessionStore: PlaybackSessionStore
        private set

    lateinit var directoryRepository: DirectoryRepository
        private set

    lateinit var playerEngine: AudioPlayerEngine
        private set

    lateinit var musicPlayerAppSession: MusicPlayerAppSession
        private set

    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getInstance(this)
        val coverArtStorage = CoverArtStorageImpl(this)
        val webDavClient = OkHttpWebDavClient()
        this.webDavClient = webDavClient
        serverRepository = ServerRepositoryImpl(database.webDavServerDao(), coverArtStorage, webDavClient)

        trackMetadataRepository =
            TrackMetadataRepositoryImpl(
                trackMetadataDao = database.trackMetadataDao(),
                webDavClient = webDavClient,
                coverArtStorage = coverArtStorage,
                webDavServerDao = database.webDavServerDao(),
            )

        lyricsRepository =
            LyricsRepositoryImpl(
                webDavClient = webDavClient,
                trackMetadataRepository = trackMetadataRepository,
            )

        val mediaSourceAdapter = DefaultWebDavMediaSourceAdapter(this, webDavClient)
        val sessionHost = PlaybackSessionHost.getInstance(this, mediaSourceAdapter)
        playerEngine = sessionHost

        val sessionStore = DataStorePlaybackSessionStore(this)
        this.sessionStore = sessionStore

        val directoryRepository =
            DirectoryRepositoryImpl(
                webDavClient = webDavClient,
                directoryCacheDao = database.directoryCacheDao(),
            )
        this.directoryRepository = directoryRepository

        musicPlayerAppSession =
            MusicPlayerAppSessionImpl(
                playerEngine = playerEngine,
                serverRepository = serverRepository,
                trackMetadataRepository = trackMetadataRepository,
                lyricsRepository = lyricsRepository,
                sessionStore = sessionStore,
                webDavClient = webDavClient,
            )
    }
}
