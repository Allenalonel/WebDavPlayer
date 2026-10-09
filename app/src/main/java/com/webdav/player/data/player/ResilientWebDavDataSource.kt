package com.webdav.player.data.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CopyOnWriteArrayList

class UnexpectedEndOfStreamException(
    message: String,
    val bytesRead: Long = 0L,
    val expectedBytes: Long = 0L,
) : IOException(message)

@OptIn(UnstableApi::class)
class ResilientWebDavDataSource(
    private val underlyingFactory: HttpDataSource.Factory,
    val maxRetryAttempts: Int = DEFAULT_MAX_RETRIES,
    val initialBackoffMs: Long = DEFAULT_INITIAL_BACKOFF_MS,
    val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
    val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
    val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val onReconnectingListener: ((Boolean) -> Unit)? = null,
) : HttpDataSource {
    companion object {
        const val DEFAULT_MAX_RETRIES: Int = Int.MAX_VALUE
        const val DEFAULT_INITIAL_BACKOFF_MS: Long = 1000L
        const val DEFAULT_MAX_BACKOFF_MS: Long = 6000L
        const val DEFAULT_BACKOFF_MULTIPLIER: Double = 2.0
        val NON_RETRYABLE_STATUS_CODES = setOf(400, 401, 403, 404, 405, 410)
    }

    class Factory(
        private val underlyingFactory: HttpDataSource.Factory,
        private val maxRetryAttempts: Int = DEFAULT_MAX_RETRIES,
        private val initialBackoffMs: Long = DEFAULT_INITIAL_BACKOFF_MS,
        private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
        private val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
        private val onReconnectingListener: ((Boolean) -> Unit)? = null,
    ) : HttpDataSource.Factory {
        override fun createDataSource(): HttpDataSource =
            ResilientWebDavDataSource(
                underlyingFactory = underlyingFactory,
                maxRetryAttempts = maxRetryAttempts,
                initialBackoffMs = initialBackoffMs,
                maxBackoffMs = maxBackoffMs,
                backoffMultiplier = backoffMultiplier,
                sleeper = sleeper,
                onReconnectingListener = onReconnectingListener,
            )

        override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
            underlyingFactory.setDefaultRequestProperties(defaultRequestProperties)
            return this
        }
    }

    private val transferListeners = CopyOnWriteArrayList<TransferListener>()
    private val requestProperties = mutableMapOf<String, String>()

    @Volatile
    private var currentDataSource: HttpDataSource? = null

    @Volatile
    private var isSourceOpen = false

    @Volatile
    private var isClosed = false

    @Volatile
    var isReconnecting: Boolean = false
        private set

    @Volatile
    var totalBytesRead: Long = 0L
        private set

    @Volatile
    var expectedContentLength: Long = C.LENGTH_UNSET.toLong()
        private set

    @Volatile
    var retryCount: Int = 0
        private set

    private var originalDataSpec: DataSpec? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        currentDataSource?.addTransferListener(transferListener)
    }

    override fun setRequestProperty(
        name: String,
        value: String,
    ) {
        requestProperties[name] = value
        currentDataSource?.setRequestProperty(name, value)
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
        currentDataSource?.clearRequestProperty(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
        currentDataSource?.clearAllRequestProperties()
    }

    override fun open(dataSpec: DataSpec): Long {
        originalDataSpec = dataSpec
        totalBytesRead = 0L
        retryCount = 0
        isClosed = false
        isSourceOpen = false
        isReconnecting = false

        while (!isClosed && !Thread.currentThread().isInterrupted) {
            try {
                val source = underlyingFactory.createDataSource()
                transferListeners.forEach { source.addTransferListener(it) }
                requestProperties.forEach { (k, v) -> source.setRequestProperty(k, v) }
                currentDataSource = source

                val bytesToRead = source.open(dataSpec)
                isSourceOpen = true
                expectedContentLength =
                    when {
                        dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                        bytesToRead != C.LENGTH_UNSET.toLong() -> bytesToRead
                        else -> parseContentLengthFromHeaders(source.responseHeaders)
                    }
                isReconnecting = false
                onReconnectingListener?.invoke(false)
                return bytesToRead
            } catch (e: Exception) {
                isSourceOpen = false
                if (isClosed || Thread.currentThread().isInterrupted || isNonRetryable(e)) {
                    throw e
                }
                if (retryCount >= maxRetryAttempts) {
                    throw e
                }
                isReconnecting = true
                onReconnectingListener?.invoke(true)
                performBackoff(e)
            }
        }
        throw InterruptedIOException("Open cancelled")
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (isClosed || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("DataSource closed or interrupted")
        }

        while (!isClosed && !Thread.currentThread().isInterrupted) {
            try {
                if (!isSourceOpen) {
                    openRangeSource()
                    isSourceOpen = true
                }

                val source = currentDataSource ?: throw IOException("DataSource is null")
                val readCount = source.read(buffer, offset, length)

                if (readCount == C.RESULT_END_OF_INPUT) {
                    if (expectedContentLength > 0L && totalBytesRead < expectedContentLength) {
                        throw UnexpectedEndOfStreamException(
                            "Premature EOF: received $totalBytesRead bytes but expected $expectedContentLength bytes",
                            bytesRead = totalBytesRead,
                            expectedBytes = expectedContentLength,
                        )
                    }
                    return C.RESULT_END_OF_INPUT
                }

                if (readCount > 0) {
                    totalBytesRead += readCount
                    retryCount = 0
                    if (isReconnecting) {
                        isReconnecting = false
                        onReconnectingListener?.invoke(false)
                    }
                }
                return readCount
            } catch (e: Exception) {
                isSourceOpen = false
                if (isClosed || Thread.currentThread().isInterrupted || isNonRetryable(e)) {
                    throw e
                }
                if (retryCount >= maxRetryAttempts) {
                    throw e
                }
                isReconnecting = true
                onReconnectingListener?.invoke(true)
                performBackoff(e)
            }
        }
        throw InterruptedIOException("Read cancelled")
    }

    private fun openRangeSource() {
        try {
            currentDataSource?.close()
        } catch (_: Exception) {
        }

        val original = originalDataSpec ?: throw IOException("No original DataSpec available for reconnect")
        val currentOffset = original.position + totalBytesRead
        val remainingLength =
            when {
                expectedContentLength != C.LENGTH_UNSET.toLong() -> {
                    (expectedContentLength - totalBytesRead).coerceAtLeast(0L)
                }

                original.length != C.LENGTH_UNSET.toLong() -> {
                    (original.length - totalBytesRead).coerceAtLeast(0L)
                }

                else -> {
                    C.LENGTH_UNSET.toLong()
                }
            }

        val rangeSpec =
            original
                .buildUpon()
                .setPosition(currentOffset)
                .setLength(remainingLength)
                .build()

        val nextSource = underlyingFactory.createDataSource()
        transferListeners.forEach { nextSource.addTransferListener(it) }
        requestProperties.forEach { (k, v) -> nextSource.setRequestProperty(k, v) }
        currentDataSource = nextSource
        nextSource.open(rangeSpec)
    }

    private fun performBackoff(cause: Exception) {
        val delayMs = calculateBackoffDelay(retryCount)
        retryCount++
        try {
            sleeper(delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Reconnection backoff interrupted: ${e.message}")
        }
    }

    @VisibleForTesting
    fun calculateBackoffDelay(attempt: Int): Long {
        val multiplier = Math.pow(backoffMultiplier, attempt.toDouble())
        val delay = (initialBackoffMs * multiplier).toLong()
        return delay.coerceIn(initialBackoffMs, maxBackoffMs)
    }

    override fun close() {
        isClosed = true
        isSourceOpen = false
        try {
            currentDataSource?.close()
        } finally {
            currentDataSource = null
            isReconnecting = false
            onReconnectingListener?.invoke(false)
        }
    }

    override fun getUri(): Uri? = currentDataSource?.uri ?: originalDataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = currentDataSource?.responseHeaders ?: emptyMap()

    override fun getResponseCode(): Int = (currentDataSource as? HttpDataSource)?.responseCode ?: 200

    private fun parseContentLengthFromHeaders(headers: Map<String, List<String>>): Long {
        headers["Content-Range"]?.firstOrNull()?.let { contentRange ->
            val slashIndex = contentRange.lastIndexOf('/')
            if (slashIndex != -1 && slashIndex + 1 < contentRange.length) {
                val totalStr = contentRange.substring(slashIndex + 1).trim()
                totalStr.toLongOrNull()?.let { if (it > 0L) return it }
            }
        }
        headers["Content-Length"]?.firstOrNull()?.let { contentLength ->
            contentLength.trim().toLongOrNull()?.let { if (it > 0L) return it }
        }
        return C.LENGTH_UNSET.toLong()
    }

    fun isNonRetryable(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }.any { cause ->
            cause is FileNotFoundException ||
                (
                    cause is HttpDataSource.InvalidResponseCodeException &&
                        cause.responseCode in NON_RETRYABLE_STATUS_CODES
                )
        }
}
