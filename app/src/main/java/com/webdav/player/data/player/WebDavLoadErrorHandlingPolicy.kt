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
        if (exception is FileNotFoundException) return true
        if (exception is HttpDataSource.InvalidResponseCodeException) {
            val code = exception.responseCode
            return code == 401 || code == 403 || code == 404 || code == 410
        }
        return false
    }
}
