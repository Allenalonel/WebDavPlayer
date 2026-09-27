package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.domain.model.WebDavServer
import okhttp3.OkHttpClient
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
    fun webDavDataSourceFactory_usesStreamingClient() {
        val factory = WebDavDataSourceFactory()
        factory.setServer(testServer)

        val streamingClient = factory.webDavClient.buildStreamingClientForServer(testServer)
        assertEquals(30_000, streamingClient.readTimeoutMillis)
        val dataSource = factory.createDataSource()
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
}
