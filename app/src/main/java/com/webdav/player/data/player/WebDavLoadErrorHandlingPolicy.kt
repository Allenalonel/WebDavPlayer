package com.webdav.player.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException
import java.io.IOException

@OptIn(UnstableApi::class)
class WebDavLoadErrorHandlingPolicy(
    private val defaultMinRetryCount: Int = 3,
) : DefaultLoadErrorHandlingPolicy(defaultMinRetryCount) {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = defaultMinRetryCount

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception

        if (isNonRetryable(exception)) {
            return C.TIME_UNSET
        }

        if (loadErrorInfo.errorCount > defaultMinRetryCount) {
            return C.TIME_UNSET
        }

        val shift = (loadErrorInfo.errorCount - 1).coerceIn(0, 3)
        val exponentialDelay = 1000L * (1 shl shift)
        return exponentialDelay.coerceAtMost(5000L)
    }

    private fun isNonRetryable(exception: IOException): Boolean {
        return generateSequence<Throwable>(exception) { it.cause }.any { cause ->
            cause is FileNotFoundException ||
                (cause is HttpDataSource.InvalidResponseCodeException &&
                    cause.responseCode in NON_RETRYABLE_STATUS_CODES)
        }
    }

    companion object {
        private val NON_RETRYABLE_STATUS_CODES = setOf(400, 401, 403, 404, 405, 410)
    }
}
