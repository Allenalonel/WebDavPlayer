package com.webdav.player.data.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.WebDavServer
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
interface WebDavMediaSourceAdapter {
    /**
     * Creates a fully configured MediaItem for a single AudioTrack on the given WebDAV server.
     * Encapsulates stream URL, media ID, MIME type, and metadata mapping.
     */
    fun createMediaItem(
        server: WebDavServer,
        track: AudioTrack,
    ): MediaItem

    /**
     * Creates a fully configured MediaSource for a single AudioTrack on the given WebDAV server.
     * Encapsulates authentication, timeouts, retries, and format-specific extractor binding.
     */
    fun createMediaSource(
        server: WebDavServer,
        track: AudioTrack,
    ): MediaSource

    /**
     * Creates a list of fully configured MediaSources for a list of AudioTracks on the given WebDAV server.
     */
    fun createMediaSources(
        server: WebDavServer,
        tracks: List<AudioTrack>,
    ): List<MediaSource>
}

@OptIn(UnstableApi::class)
class DefaultWebDavMediaSourceAdapter(
    private val context: Context,
    val webDavClient: OkHttpWebDavClient = OkHttpWebDavClient(),
    val loadErrorHandlingPolicy: LoadErrorHandlingPolicy = WebDavLoadErrorHandlingPolicy(),
) : WebDavMediaSourceAdapter {
    private val dataSourceFactoryCache = ConcurrentHashMap<String, DataSource.Factory>()

    @VisibleForTesting
    fun getStreamingClientForServer(server: WebDavServer): OkHttpClient = webDavClient.getStreamingClientForServer(server)

    fun getDataSourceFactory(server: WebDavServer): DataSource.Factory = getOrCreateDataSourceFactory(server)

    private fun getOrCreateDataSourceFactory(server: WebDavServer): DataSource.Factory {
        val cacheKey = "${server.id}:${server.endpointUrl}:${server.username}:${server.password}"
        return dataSourceFactoryCache.computeIfAbsent(cacheKey) {
            val client = getStreamingClientForServer(server)
            OkHttpDataSource
                .Factory(client)
                .setUserAgent("WebDavPlayer/1.0")
        }
    }

    override fun createMediaSource(
        server: WebDavServer,
        track: AudioTrack,
    ): MediaSource {
        val mediaItem = createMediaItem(server, track)
        val dataSourceFactory = getOrCreateDataSourceFactory(server)
        val extractorsFactory = resolveExtractorsFactory(track.format)

        return ProgressiveMediaSource
            .Factory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(mediaItem)
    }

    override fun createMediaSources(
        server: WebDavServer,
        tracks: List<AudioTrack>,
    ): List<MediaSource> = tracks.map { track -> createMediaSource(server, track) }

    internal fun resolveExtractorsFactory(format: AudioFormat): ExtractorsFactory =
        when (format) {
            AudioFormat.WMA -> {
                ExtractorsFactory {
                    arrayOf(AsfExtractor())
                }
            }

            AudioFormat.FLAC -> {
                ExtractorsFactory {
                    arrayOf(
                        androidx.media3.extractor.flac
                            .FlacExtractor(),
                        *DefaultExtractorsFactory().createExtractors(),
                    )
                }
            }

            AudioFormat.MP3 -> {
                ExtractorsFactory {
                    arrayOf(
                        androidx.media3.extractor.mp3
                            .Mp3Extractor(),
                        *DefaultExtractorsFactory().createExtractors(),
                    )
                }
            }

            AudioFormat.WAV -> {
                ExtractorsFactory {
                    arrayOf(
                        androidx.media3.extractor.wav
                            .WavExtractor(),
                        *DefaultExtractorsFactory().createExtractors(),
                    )
                }
            }

            else -> {
                ExtractorsFactory {
                    arrayOf(
                        *DefaultExtractorsFactory().createExtractors(),
                        AsfExtractor(),
                    )
                }
            }
        }

    override fun createMediaItem(
        server: WebDavServer,
        track: AudioTrack,
    ): MediaItem {
        val uri = Uri.parse(track.streamUrl(server))
        val metaBuilder =
            MediaMetadata
                .Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)

        track.coverThumbnailPath?.let { path ->
            metaBuilder.setArtworkUri(Uri.fromFile(File(path)))
        }

        return MediaItem
            .Builder()
            .setUri(uri)
            .setMediaId(track.id)
            .setMimeType(track.format.mimeType)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }
}
