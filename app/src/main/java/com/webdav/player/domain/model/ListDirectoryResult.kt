package com.webdav.player.domain.model

sealed interface ListDirectoryResult {
    data class Success(val directory: RemoteDirectory) : ListDirectoryResult
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null
    ) : ListDirectoryResult
}
