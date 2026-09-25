package com.webdav.player

import android.app.Application
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorageImpl
import com.webdav.player.data.player.Media3AudioPlayerEngine
import com.webdav.player.data.player.WebDavDataSourceFactory
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.ServerRepositoryImpl
import com.webdav.player.data.repository.TrackMetadataRepositoryImpl
import com.webdav.player.domain.player.AudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.repository.TrackMetadataRepository
import com.webdav.player.domain.session.MusicPlayerAppSession
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl

class WebDavApplication : Application() {

    lateinit var serverRepository: ServerRepository
        private set

    lateinit var trackMetadataRepository: TrackMetadataRepository
        private set

    lateinit var playerEngine: AudioPlayerEngine
        private set

    lateinit var musicPlayerAppSession: MusicPlayerAppSession
        private set

    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getInstance(this)
        serverRepository = ServerRepositoryImpl(database.webDavServerDao())

        val webDavClient = OkHttpWebDavClient()
        val coverArtStorage = CoverArtStorageImpl(this)
        trackMetadataRepository = TrackMetadataRepositoryImpl(
            trackMetadataDao = database.trackMetadataDao(),
            webDavClient = webDavClient,
            coverArtStorage = coverArtStorage
        )

        val dataSourceFactory = WebDavDataSourceFactory(webDavClient)
        playerEngine = Media3AudioPlayerEngine(this, dataSourceFactory)

        musicPlayerAppSession = MusicPlayerAppSessionImpl(
            playerEngine = playerEngine,
            serverRepository = serverRepository,
            trackMetadataRepository = trackMetadataRepository
        )
    }
}
