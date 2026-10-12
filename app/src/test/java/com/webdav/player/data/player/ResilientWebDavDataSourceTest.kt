package com.webdav.player.data.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class ResilientWebDavDataSourceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var baseHttpDataSourceFactory: HttpDataSource.Factory
    private val recordedBackoffs = CopyOnWriteArrayList<Long>()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient =
            OkHttpClient
                .Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .connectTimeout(200, TimeUnit.MILLISECONDS)
                .build()

        baseHttpDataSourceFactory =
            OkHttpDataSource
                .Factory(okHttpClient)
                .setUserAgent("TestAgent/1.0")
        recordedBackoffs.clear()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun calculateBackoffDelay_followsExponentialGrowthAndCapsAtMaxBackoff() {
        val dataSource =
            ResilientWebDavDataSource(
                underlyingFactory = baseHttpDataSourceFactory,
                initialBackoffMs = 1000L,
                maxBackoffMs = 6000L,
                backoffMultiplier = 2.0,
            )

        assertEquals(1000L, dataSource.calculateBackoffDelay(0))
        assertEquals(2000L, dataSource.calculateBackoffDelay(1))
        assertEquals(4000L, dataSource.calculateBackoffDelay(2))
        assertEquals(6000L, dataSource.calculateBackoffDelay(3))
        assertEquals(6000L, dataSource.calculateBackoffDelay(4))
    }

    @Test
    fun transparentReconnection_onNetworkTimeoutDuringRead_resumesWithCorrectRange() {
        val fullData = "ABCDEFGHIJ0123456789".toByteArray()
        val part1 = fullData.copyOfRange(0, 5) // "ABCDE"
        val remaining = fullData.copyOfRange(5, fullData.size) // "FGHIJ0123456789"

        // First response delivers 5 bytes and then disconnects mid-stream
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(part1))
                .setHeader("Content-Length", fullData.size.toString())
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )

        // Second response accepts Range: bytes=5- and returns remaining bytes
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setBody(okio.Buffer().write(remaining))
                .setHeader("Content-Range", "bytes 5-${fullData.size - 1}/${fullData.size}")
                .setHeader("Content-Length", remaining.size.toString()),
        )

        val reconnectingEvents = CopyOnWriteArrayList<Boolean>()
        val resilientSource =
            ResilientWebDavDataSource(
                underlyingFactory = baseHttpDataSourceFactory,
                maxRetryAttempts = 3,
                sleeper = { recordedBackoffs.add(it) },
                onReconnectingListener = { reconnectingEvents.add(it) },
            )

        val dataSpec =
            DataSpec
                .Builder()
                .setUri(mockWebServer.url("/music.mp3").toString())
                .build()

        resilientSource.open(dataSpec)

        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4)
        var bytesRead: Int
        while (true) {
            bytesRead = resilientSource.read(buffer, 0, buffer.size)
            if (bytesRead == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, bytesRead)
        }
        resilientSource.close()

        val resultBytes = out.toByteArray()
        assertArrayEquals(fullData, resultBytes)
        assertTrue("Backoff must be recorded during reconnection", recordedBackoffs.isNotEmpty())
        assertEquals(1000L, recordedBackoffs[0])

        // Verify that the second request sent Range: bytes=5-
        val req1 = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(req1)
        val req2 = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(req2)
        assertEquals("bytes=5-19", req2!!.getHeader("Range"))
    }

    @Test
    fun contentLengthGuard_triggersRangeReconnectionWhenServerTruncatesStreamPrematurely() {
        val totalExpected = 20
        val prematureBytes = "ABCDE12345".toByteArray() // only 10 bytes
        val remainderBytes = "67890FGHIJ".toByteArray() // 10 bytes

        // First response declares Content-Length: 20 but prematurely finishes after 10 bytes
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(prematureBytes))
                .setHeader("Content-Length", totalExpected.toString())
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )

        // Resumed Range response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setBody(okio.Buffer().write(remainderBytes))
                .setHeader("Content-Range", "bytes 10-19/20")
                .setHeader("Content-Length", remainderBytes.size.toString()),
        )

        val resilientSource =
            ResilientWebDavDataSource(
                underlyingFactory = baseHttpDataSourceFactory,
                maxRetryAttempts = 3,
                sleeper = { recordedBackoffs.add(it) },
            )

        val dataSpec =
            DataSpec
                .Builder()
                .setUri(mockWebServer.url("/audio.flac").toString())
                .build()

        resilientSource.open(dataSpec)

        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16)
        var bytesRead: Int
        while (true) {
            bytesRead = resilientSource.read(buffer, 0, buffer.size)
            if (bytesRead == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, bytesRead)
        }
        resilientSource.close()

        val combined = out.toByteArray()
        assertEquals(20, combined.size)
        assertEquals("ABCDE1234567890FGHIJ", String(combined))

        // Verify request 2 resumed from byte 10
        mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        val req2 = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(req2)
        assertEquals("bytes=10-19", req2!!.getHeader("Range"))
    }

    @Test
    fun nonRetryableHttpStatus_failsFastWithoutLooping() {
        val nonRetryableCodes = listOf(401, 403, 404, 416)
        for (code in nonRetryableCodes) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(code)
                    .setBody("Error $code"),
            )

            val resilientSource =
                ResilientWebDavDataSource(
                    underlyingFactory = baseHttpDataSourceFactory,
                    maxRetryAttempts = 5,
                    sleeper = { recordedBackoffs.add(it) },
                )

            val dataSpec =
                DataSpec
                    .Builder()
                    .setUri(mockWebServer.url("/notfound.mp3").toString())
                    .build()

            try {
                resilientSource.open(dataSpec)
                fail("Expected HttpDataSource.InvalidResponseCodeException for code $code")
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                assertEquals(code, e.responseCode)
            } finally {
                resilientSource.close()
            }
        }
        assertEquals("Non-retryable errors must not trigger backoff sleep", 0, recordedBackoffs.size)
    }

    @Test
    fun rangeNotSatisfiable_duringRead_returnsEndOfInputWhenDataAlreadyRead() {
        // First chunk returns 5 bytes then cuts connection
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write("12345".toByteArray()))
                .setHeader("Content-Length", "5")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )
        // Reconnect range request gets 416
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setBody("Requested Range Not Satisfiable"),
        )

        val resilientSource =
            ResilientWebDavDataSource(
                underlyingFactory = baseHttpDataSourceFactory,
                maxRetryAttempts = 3,
                sleeper = { recordedBackoffs.add(it) },
            )

        val dataSpec =
            DataSpec
                .Builder()
                .setUri(mockWebServer.url("/stream.mp3").toString())
                .build()

        resilientSource.open(dataSpec)

        val buffer = ByteArray(10)
        val read1 = resilientSource.read(buffer, 0, 5)
        assertEquals(5, read1)

        val read2 = resilientSource.read(buffer, 0, 5)
        assertEquals(C.RESULT_END_OF_INPUT, read2)

        resilientSource.close()
    }

    @Test
    fun close_instantlyPreemptsAndInterruptsRetryingDataSource() {
        val resilientSource =
            ResilientWebDavDataSource(
                underlyingFactory = baseHttpDataSourceFactory,
                maxRetryAttempts = 10,
                sleeper = { Thread.sleep(it) },
            )

        // Close before read
        resilientSource.close()
        try {
            resilientSource.read(ByteArray(10), 0, 10)
            fail("Expected InterruptedIOException on closed data source")
        } catch (e: InterruptedIOException) {
            assertTrue(e.message?.contains("closed", ignoreCase = true) == true)
        }
    }
}
