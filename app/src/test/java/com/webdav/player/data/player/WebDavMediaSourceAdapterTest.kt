package com.webdav.player.data.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.WebDavServer
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class WebDavMediaSourceAdapterTest {
    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: WebDavMediaSourceAdapter

    private lateinit var testServer: WebDavServer

    private val testTrack =
        AudioTrack(
            id = "1:/Music/test.mp3",
            serverId = 1L,
            remotePath = "/Music/test.mp3",
            title = "Test MP3",
            artist = "Test Artist",
            album = "Test Album",
            format = AudioFormat.MP3,
            size = 1024L,
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/dav").toString()
        testServer =
            WebDavServer(
                id = 1L,
                name = "Test WebDAV",
                url = baseUrl,
                port = mockWebServer.port,
                pathPrefix = "/dav",
                username = "testuser",
                password = "testpassword",
            )

        adapter = DefaultWebDavMediaSourceAdapter(context)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun createMediaItem_producesValidMediaItem_withCompleteMetadataAndStreamUri() {
        val mediaItem = adapter.createMediaItem(testServer, testTrack)
        assertNotNull(mediaItem)
        assertEquals(testTrack.id, mediaItem.mediaId)
        assertEquals("Test MP3", mediaItem.mediaMetadata.title.toString())
        assertEquals("Test Artist", mediaItem.mediaMetadata.artist.toString())
        assertEquals("Test Album", mediaItem.mediaMetadata.albumTitle.toString())
        assertEquals(AudioFormat.MP3.mimeType, mediaItem.localConfiguration?.mimeType)
        assertEquals(testTrack.streamUrl(testServer), mediaItem.localConfiguration?.uri.toString())
    }

    @Test
    fun createMediaItem_withCoverThumbnail_attachesArtworkUri() {
        val coverPath = "/local/path/cover.jpg"
        val trackWithArt = testTrack.copy(coverThumbnailPath = coverPath)
        val mediaItem = adapter.createMediaItem(testServer, trackWithArt)
        assertNotNull(mediaItem.mediaMetadata.artworkUri)
        assertEquals(Uri.fromFile(File(coverPath)), mediaItem.mediaMetadata.artworkUri)
    }

    @Test
    fun createMediaSource_producesValidMediaSource_withAttachedMetadataAndUri() {
        val mediaSource = adapter.createMediaSource(testServer, testTrack)
        assertNotNull(mediaSource)

        val mediaItem = mediaSource.mediaItem
        assertNotNull(mediaItem)
        assertEquals(testTrack.id, mediaItem.mediaId)
        assertEquals("Test MP3", mediaItem.mediaMetadata.title.toString())
        assertEquals("Test Artist", mediaItem.mediaMetadata.artist.toString())
        assertEquals("Test Album", mediaItem.mediaMetadata.albumTitle.toString())
        assertEquals(AudioFormat.MP3.mimeType, mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun createMediaSource_streamingConnection_hasAtLeast30SecondsReadAndWriteTimeouts() {
        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val streamingClient = defaultAdapter.getStreamingClientForServer(testServer)

        assertTrue(
            "Read timeout must be at least 30 seconds (30000ms), but was ${streamingClient.readTimeoutMillis}ms",
            streamingClient.readTimeoutMillis >= 30_000,
        )
        assertTrue(
            "Write timeout must be at least 30 seconds (30000ms), but was ${streamingClient.writeTimeoutMillis}ms",
            streamingClient.writeTimeoutMillis >= 30_000,
        )
    }

    @Test
    fun createMediaSource_automaticallyInjectsHttpBasicAuthHeader_intoChunkRequests() {
        // Enqueue mock response for audio stream chunk request
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setHeader("Content-Length", "1024")
                .setBody("test audio stream data"),
        )

        val mediaSource = adapter.createMediaSource(testServer, testTrack)
        val player = ExoPlayer.Builder(context).build()
        try {
            player.setMediaSource(mediaSource)
            player.prepare()

            val recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull("Request should reach mock server", recordedRequest)

            val expectedAuth = Credentials.basic("testuser", "testpassword")
            val authHeader = recordedRequest!!.getHeader("Authorization")
            assertEquals("Basic auth header must be attached to chunk request", expectedAuth, authHeader)
        } finally {
            player.release()
        }
    }

    @Test
    fun loadErrorHandlingPolicy_retriesWithExponentialBackoff_onTransientSocketAndNetworkFailures() {
        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val policy = defaultAdapter.loadErrorHandlingPolicy

        val testDataSpec = DataSpec.Builder().setUri(testServer.resolveFileUrl(testTrack.remotePath)).build()
        val socketTimeoutException =
            HttpDataSource.HttpDataSourceException(
                "Read timeout",
                SocketTimeoutException("Read timed out"),
                testDataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_READ,
            )

        // Attempt 1: 1000ms delay
        val info1 =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                socketTimeoutException,
                1,
            )
        assertEquals(1000L, policy.getRetryDelayMsFor(info1))

        // Attempt 2: 2000ms delay
        val info2 =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                socketTimeoutException,
                2,
            )
        assertEquals(2000L, policy.getRetryDelayMsFor(info2))

        // Attempt 3: 4000ms delay
        val info3 =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                socketTimeoutException,
                3,
            )
        assertEquals(4000L, policy.getRetryDelayMsFor(info3))

        // Attempt 4: Exceeded 3 retries -> TIME_UNSET (escalate to unrecoverable error)
        val info4 =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                socketTimeoutException,
                4,
            )
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info4))

        // Verify broken pipe and connection reset also retry
        val brokenPipeException =
            HttpDataSource.HttpDataSourceException(
                "Broken pipe",
                SocketException("Broken pipe"),
                testDataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_READ,
            )
        val brokenPipeInfo =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, testDataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                brokenPipeException,
                1,
            )
        assertEquals(1000L, policy.getRetryDelayMsFor(brokenPipeInfo))
    }

    @Test
    fun loadErrorHandlingPolicy_failsFast_onNonRecoverableHttpErrors() {
        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val policy = defaultAdapter.loadErrorHandlingPolicy

        val testDataSpec = DataSpec.Builder().setUri(testServer.resolveFileUrl(testTrack.remotePath)).build()

        val nonRecoverableCodes = listOf(401, 403, 404, 410)
        for (code in nonRecoverableCodes) {
            val httpException =
                HttpDataSource.InvalidResponseCodeException(
                    code,
                    "HTTP $code Error",
                    null,
                    emptyMap(),
                    testDataSpec,
                    byteArrayOf(),
                )
            val errorInfo =
                LoadErrorHandlingPolicy.LoadErrorInfo(
                    LoadEventInfo(0L, testDataSpec, 0L),
                    MediaLoadData(C.DATA_TYPE_MEDIA),
                    httpException,
                    1,
                )
            assertEquals("HTTP $code must fail fast without retrying", C.TIME_UNSET, policy.getRetryDelayMsFor(errorInfo))
        }
    }

    @Test
    fun streamingChunkRetrieval_readsStreamChunkSuccessfully() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setHeader("Content-Length", "1024")
                .setBody("streaming-chunk-data"),
        )

        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val dataSource = defaultAdapter.getDataSourceFactory(testServer).createDataSource()

        val dataSpec =
            DataSpec
                .Builder()
                .setUri(testServer.resolveFileUrl(testTrack.remotePath))
                .build()

        dataSource.open(dataSpec)
        val buffer = ByteArray(64)
        val bytesRead = dataSource.read(buffer, 0, buffer.size)
        dataSource.close()

        assertTrue("Should read non-zero bytes", bytesRead > 0)
        assertEquals("streaming-chunk-data", String(buffer, 0, bytesRead))

        val recordedRequest = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        val expectedAuth = Credentials.basic("testuser", "testpassword")
        assertEquals(expectedAuth, recordedRequest!!.getHeader("Authorization"))
    }

    @Test
    fun socketTimeout_triggersRetryPolicy_andRecoversStreamingChunk() {
        // Enqueue a response that causes a socket timeout
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        // Enqueue successful response for retry
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setHeader("Content-Length", "1024")
                .setBody("recovered-after-retry"),
        )

        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val dataSpec =
            DataSpec
                .Builder()
                .setUri(testServer.resolveFileUrl(testTrack.remotePath))
                .build()

        // Create a short-timeout client to quickly trigger SocketTimeoutException in test
        val shortTimeoutClient =
            okhttp3.OkHttpClient
                .Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .callTimeout(500, TimeUnit.MILLISECONDS)
                .build()
        val shortTimeoutDataSource =
            androidx.media3.datasource.okhttp.OkHttpDataSource
                .Factory(shortTimeoutClient)
                .createDataSource()

        var caughtTimeoutException: IOException? = null
        try {
            shortTimeoutDataSource.open(dataSpec)
        } catch (e: IOException) {
            caughtTimeoutException = e
        } finally {
            try {
                shortTimeoutDataSource.close()
            } catch (_: Exception) {
            }
        }

        assertNotNull("Should catch socket timeout exception", caughtTimeoutException)

        // Verify adapter's retry policy grants retry with exponential backoff
        val errorInfo =
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(0L, dataSpec, 0L),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                caughtTimeoutException!!,
                1,
            )
        val retryDelayMs = defaultAdapter.loadErrorHandlingPolicy.getRetryDelayMsFor(errorInfo)
        assertNotEquals("Should retry transient socket timeout", C.TIME_UNSET, retryDelayMs)
        assertEquals("First retry delay should be 1000ms", 1000L, retryDelayMs)

        // Perform recovery stream read using adapter's configured data source
        val normalDataSource = defaultAdapter.getDataSourceFactory(testServer).createDataSource()
        normalDataSource.open(dataSpec)
        val buffer = ByteArray(64)
        val bytesRead = normalDataSource.read(buffer, 0, buffer.size)
        normalDataSource.close()

        assertEquals("recovered-after-retry", String(buffer, 0, bytesRead))
    }

    @Test
    fun resolveExtractorsFactory_bindsAsfExtractor_forWmaFormat() {
        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val factory = defaultAdapter.resolveExtractorsFactory(AudioFormat.WMA)
        val extractors = factory.createExtractors()

        assertTrue("WMA extractor array should not be empty", extractors.isNotEmpty())
        assertTrue("WMA format must bind AsfExtractor", extractors[0] is AsfExtractor)
    }

    @Test
    fun resolveExtractorsFactory_bindsFlacExtractor_forFlacFormat() {
        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val factory = defaultAdapter.resolveExtractorsFactory(AudioFormat.FLAC)
        val extractors = factory.createExtractors()

        assertTrue("FLAC extractor array should not be empty", extractors.isNotEmpty())
        assertTrue("FLAC format must bind FlacExtractor", extractors[0] is androidx.media3.extractor.flac.FlacExtractor)
    }

    @Test
    fun resolveExtractorsFactory_bindsMp3Extractor_forMp3Format() {
        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val factory = defaultAdapter.resolveExtractorsFactory(AudioFormat.MP3)
        val extractors = factory.createExtractors()

        assertTrue("MP3 extractor array should not be empty", extractors.isNotEmpty())
        assertTrue("MP3 format must bind Mp3Extractor", extractors[0] is androidx.media3.extractor.mp3.Mp3Extractor)
    }

    @Test
    fun resolveExtractorsFactory_bindsWavExtractor_forWavFormat() {
        val defaultAdapter = adapter as DefaultWebDavMediaSourceAdapter
        val factory = defaultAdapter.resolveExtractorsFactory(AudioFormat.WAV)
        val extractors = factory.createExtractors()

        assertTrue("WAV extractor array should not be empty", extractors.isNotEmpty())
        assertTrue("WAV format must bind WavExtractor", extractors[0] is androidx.media3.extractor.wav.WavExtractor)
    }

    @Test
    fun createMediaSource_wmaTrack_configuresWmaMediaItemAndAsfExtractor() {
        val wmaTrack =
            AudioTrack(
                id = "1:/Music/test.wma",
                serverId = 1L,
                remotePath = "/Music/test.wma",
                title = "Test WMA",
                format = AudioFormat.WMA,
                size = 2048L,
            )
        val mediaSource = adapter.createMediaSource(testServer, wmaTrack)
        assertNotNull(mediaSource)
        assertEquals("audio/x-ms-wma", mediaSource.mediaItem.localConfiguration?.mimeType)
    }
}
