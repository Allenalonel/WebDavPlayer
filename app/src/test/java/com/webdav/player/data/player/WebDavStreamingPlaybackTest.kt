package com.webdav.player.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.WebDavServer
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class WebDavStreamingPlaybackTest {
    private lateinit var server: MockWebServer

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "http://127.0.0.1:8080/dav",
            port = 8080,
            pathPrefix = "/dav",
            username = "user1",
            password = "pwd",
        )

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamingClient_readTimeout_isAtLeast30SecondsForStreaming() {
        val webDavClient = OkHttpWebDavClient()
        val streamingClient = webDavClient.buildStreamingClientForServer(testServer)

        assertTrue(
            "Stream read timeout must be >= 30,000ms to avoid playback stall, but was ${streamingClient.readTimeoutMillis}ms",
            streamingClient.readTimeoutMillis >= 30_000,
        )
    }

    @Test
    fun webDavMediaSourceAdapter_usesStreamingClient() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = DefaultWebDavMediaSourceAdapter(context)

        val streamingClient = adapter.getStreamingClientForServer(testServer)
        assertEquals(30_000, streamingClient.readTimeoutMillis)
        val dataSource = adapter.getDataSourceFactory(testServer).createDataSource()
        assertNotNull(dataSource)
    }

    @Test
    fun webDavLoadErrorHandlingPolicy_retriesOnSocketTimeout() {
        val policy = WebDavLoadErrorHandlingPolicy(defaultMinRetryCount = 3)

        val testDataSpec = DataSpec.Builder().setUri("http://localhost/test.mp3").build()

        val timeoutException =
            HttpDataSource.HttpDataSourceException(
                "timeout",
                IOException(ExecutionException(SocketTimeoutException("timeout"))),
                testDataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )

        val loadErrorInfo =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                timeoutException,
                // errorCount =
                1,
            )

        val retryDelayMs = policy.getRetryDelayMsFor(loadErrorInfo)
        assertNotEquals("Should retry on SocketTimeoutException", C.TIME_UNSET, retryDelayMs)
        assertTrue("Retry delay should be > 0", retryDelayMs > 0)
        assertEquals(3, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    @Test
    fun webDavLoadErrorHandlingPolicy_doesNotRetryOnAuthOrNotFound() {
        val policy = WebDavLoadErrorHandlingPolicy()

        val testDataSpec = DataSpec.Builder().setUri("http://localhost/test.mp3").build()

        val notFoundException =
            HttpDataSource.InvalidResponseCodeException(
                404,
                "Not Found",
                null,
                emptyMap(),
                testDataSpec,
                byteArrayOf(),
            )

        val authException =
            HttpDataSource.InvalidResponseCodeException(
                401,
                "Unauthorized",
                null,
                emptyMap(),
                testDataSpec,
                byteArrayOf(),
            )

        val notFoundInfo =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                notFoundException,
                // errorCount =
                1,
            )

        val authInfo =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                authException,
                // errorCount =
                1,
            )

        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(notFoundInfo))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(authInfo))
    }

    @Test
    fun webDavDataSource_socketTimeout_causesHttpDataSourceException() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()

        val shortTimeoutClient =
            OkHttpClient
                .Builder()
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .callTimeout(1000, TimeUnit.MILLISECONDS)
                .build()

        val dataSourceFactory =
            androidx.media3.datasource.okhttp.OkHttpDataSource
                .Factory(shortTimeoutClient)
        val dataSource = dataSourceFactory.createDataSource()

        var caughtException: Throwable? = null
        try {
            val dataSpec =
                DataSpec
                    .Builder()
                    .setUri(server.url("/dav/test.wma").toString())
                    .build()
            dataSource.open(dataSpec)
        } catch (e: Throwable) {
            caughtException = e
        } finally {
            try {
                dataSource.close()
            } catch (_: Exception) {
            }
        }

        assertTrue("Expected HttpDataSourceException but got: $caughtException", caughtException is HttpDataSource.HttpDataSourceException)
        var cause: Throwable? = caughtException
        var foundSocketTimeout = false
        while (cause != null) {
            if (cause is SocketTimeoutException) {
                foundSocketTimeout = true
                break
            }
            cause = cause.cause
        }
        assertTrue("Cause chain should contain SocketTimeoutException", foundSocketTimeout)
    }

    @Test
    fun engineDelegatingDataSourceFactory_usesActiveServerCredentials_andAvoidsHttp401() {
        val expectedAuth = Credentials.basic("user1", "pwd")
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val authHeader = request.getHeader("Authorization")
                    return if (authHeader == expectedAuth) {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "audio/mpeg")
                            .setHeader("Content-Length", "1024")
                            .setBody("authenticated audio stream chunk")
                    } else {
                        MockResponse()
                            .setResponseCode(401)
                            .setHeader("WWW-Authenticate", "Basic realm=\"WebDAV\"")
                            .setBody("Unauthorized: Invalid or missing credentials")
                    }
                }
            }
        server.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = Media3AudioPlayerEngine(context)

        try {
            val streamUrl = server.url("/dav/track.mp3").toString()
            val dataSpec = DataSpec.Builder().setUri(streamUrl).build()

            // 1. Before playTracks, activeServer is null. Delegating factory falls back to DefaultDataSource,
            // emitting a request without Basic Auth which is rejected by the server with HTTP 401.
            val unauthenticatedDataSource = engine.delegatingDataSourceFactory.createDataSource()
            var caught401 = false
            try {
                unauthenticatedDataSource.open(dataSpec)
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                if (e.responseCode == 401) {
                    caught401 = true
                }
            } finally {
                try {
                    unauthenticatedDataSource.close()
                } catch (_: Exception) {
                }
            }
            assertTrue("Unauthenticated request must fail with HTTP 401 Unauthorized", caught401)

            // 2. Play tracks with active authenticated server context
            val authServer =
                testServer.copy(
                    url = server.url("/dav").toString(),
                    port = server.port,
                )
            val track =
                AudioTrack(
                    id = "1:/dav/track.mp3",
                    serverId = 1L,
                    remotePath = "/dav/track.mp3",
                    title = "track.mp3",
                    format = AudioFormat.MP3,
                    size = 1024L,
                )
            engine.playTracks(authServer, listOf(track), startIndex = 0)
            assertEquals(authServer, engine.activeServer)

            // 3. Delegating factory now dynamically provisions OkHttpDataSource with active server credentials
            val authenticatedDataSource = engine.delegatingDataSourceFactory.createDataSource()
            val bytesOpened = authenticatedDataSource.open(dataSpec)
            assertTrue(
                "Data source opened successfully against authenticated endpoint",
                bytesOpened > 0 || bytesOpened == C.LENGTH_UNSET.toLong(),
            )

            val buffer = ByteArray(256)
            val bytesRead = authenticatedDataSource.read(buffer, 0, buffer.size)
            assertTrue("Should read stream bytes without HTTP 401", bytesRead > 0)
            authenticatedDataSource.close()

            // 4. Update track metadata (triggers player.replaceMediaItem and ExoPlayer media source rebuild)
            val updatedTrack = track.copy(title = "Updated Track Title", artist = "Updated Artist")
            engine.updateTrack(0, updatedTrack)
            assertEquals(authServer, engine.activeServer)

            // Delegating data source factory continues to retain active credentials for rebuilt media sources
            val rebuiltDataSource = engine.delegatingDataSourceFactory.createDataSource()
            val rebuiltBytesOpened = rebuiltDataSource.open(dataSpec)
            assertTrue(
                "Rebuilt data source retains credentials and does not fail with 401",
                rebuiltBytesOpened > 0 || rebuiltBytesOpened == C.LENGTH_UNSET.toLong(),
            )
            rebuiltDataSource.close()
        } finally {
            engine.release()
        }
    }

    @Test
    fun engineMediaSourceRebuild_inheritsCredentials_inExoPlayer() {
        val expectedAuth = Credentials.basic("user1", "pwd")
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val authHeader = request.getHeader("Authorization")
                    return if (authHeader == expectedAuth) {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "audio/mpeg")
                            .setHeader("Content-Length", "1024")
                            .setBody("test audio stream content")
                    } else {
                        MockResponse()
                            .setResponseCode(401)
                            .setHeader("WWW-Authenticate", "Basic realm=\"WebDAV\"")
                            .setBody("Unauthorized")
                    }
                }
            }
        server.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = Media3AudioPlayerEngine(context)

        try {
            val authServer =
                testServer.copy(
                    url = server.url("/dav").toString(),
                    port = server.port,
                )
            val track1 =
                AudioTrack(
                    id = "1:/dav/test1.mp3",
                    serverId = 1L,
                    remotePath = "/dav/test1.mp3",
                    title = "test1.mp3",
                    format = AudioFormat.MP3,
                    size = 1024L,
                )
            val track2 =
                AudioTrack(
                    id = "1:/dav/test2.mp3",
                    serverId = 1L,
                    remotePath = "/dav/test2.mp3",
                    title = "test2.mp3",
                    format = AudioFormat.MP3,
                    size = 1024L,
                )

            // Play track1 on engine with track2 queued
            engine.playTracks(authServer, listOf(track1, track2), startIndex = 0)

            // Initial chunk request for track1 arrives with Basic auth
            val request1 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull("Initial chunk request should reach server", request1)
            assertEquals("Basic auth must be present on initial request", expectedAuth, request1!!.getHeader("Authorization"))

            // Trigger track metadata update on queued non-active track2, which explicitly executes player.replaceMediaItem(1, ...)
            val updatedTrack2 = track2.copy(title = "Updated Metadata Title 2")
            engine.updateTrack(1, updatedTrack2)

            // Verify item was replaced in ExoPlayer playlist
            val replacedItem = engine.player.getMediaItemAt(1)
            assertEquals("Updated Metadata Title 2", replacedItem.mediaMetadata.title?.toString())

            // Skip to track2: ExoPlayer uses the rebuilt MediaSource from DefaultMediaSourceFactory
            engine.skipToNext()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            // Request for track2 must inherit active server Basic Auth via delegatingDataSourceFactory
            val request2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull("Rebuilt media item request should reach server upon transition", request2)
            assertEquals("Basic auth must be present on rebuilt media item request", expectedAuth, request2!!.getHeader("Authorization"))

            // Verify player has not encountered an authentication error (HTTP 401)
            assertTrue(
                "Player should not encounter an error, but was: ${engine.playbackState.value}",
                engine.playbackState.value !is PlaybackState.Error,
            )
            assertEquals(authServer, engine.activeServer)
            assertEquals(1, engine.currentTrackIndex.value)
        } finally {
            engine.release()
        }
    }
}
