package com.webdav.player.data.remote

import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class OkHttpWebDavClient(
    private val baseOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build(),
    private val streamingBaseOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build(),
) : WebDavClient {
    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    override suspend fun testConnection(server: WebDavServer): ConnectionResult =
        withContext(Dispatchers.IO) {
            val client = buildClientForServer(server)

            val requestBuilder =
                Request
                    .Builder()
                    .url(server.endpointUrl)
                    .method("PROPFIND", null)
                    .header("Depth", "0")

            if (server.username.isNotBlank()) {
                val credential = Credentials.basic(server.username, server.password)
                requestBuilder.header("Authorization", credential)
            }

            val request = requestBuilder.build()

            try {
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200, 207 -> {
                            ConnectionResult.Success
                        }

                        401 -> {
                            ConnectionResult.Failure("Authentication failed: HTTP 401", statusCode = 401)
                        }

                        403 -> {
                            ConnectionResult.Failure("Access forbidden: HTTP 403", statusCode = 403)
                        }

                        else -> {
                            ConnectionResult.Failure(
                                message = "Server returned HTTP ${response.code}: ${response.message.ifBlank { "Error" }}",
                                statusCode = response.code,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                ConnectionResult.Failure(
                    message = e.localizedMessage ?: "Network connection error",
                    cause = e,
                )
            } catch (e: Exception) {
                ConnectionResult.Failure(
                    message = e.localizedMessage ?: "Unexpected error",
                    cause = e,
                )
            }
        }

    override suspend fun listDirectory(
        server: WebDavServer,
        path: String,
    ): ListDirectoryResult =
        withContext(Dispatchers.IO) {
            val client = buildClientForServer(server)
            val fullUrl = buildDirectoryUrl(server, path)

            val requestBuilder =
                Request
                    .Builder()
                    .url(fullUrl)
                    .method("PROPFIND", null)
                    .header("Depth", "1")

            if (server.username.isNotBlank()) {
                val credential = Credentials.basic(server.username, server.password)
                requestBuilder.header("Authorization", credential)
            }

            val request = requestBuilder.build()

            try {
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        207 -> {
                            val body = response.body?.string() ?: ""
                            try {
                                val directory =
                                    WebDavXmlParser.parseMultistatus(
                                        xml = body,
                                        serverPathPrefix = server.pathPrefix,
                                        requestedPath = path,
                                    )
                                ListDirectoryResult.Success(directory)
                            } catch (e: Exception) {
                                ListDirectoryResult.Failure(
                                    message = "Failed to parse WebDAV XML: ${e.localizedMessage}",
                                    statusCode = 207,
                                    cause = e,
                                )
                            }
                        }

                        401 -> {
                            ListDirectoryResult.Failure("Authentication failed: HTTP 401", statusCode = 401)
                        }

                        403 -> {
                            ListDirectoryResult.Failure("Access forbidden: HTTP 403", statusCode = 403)
                        }

                        404 -> {
                            ListDirectoryResult.Failure("Directory not found: HTTP 404", statusCode = 404)
                        }

                        else -> {
                            ListDirectoryResult.Failure(
                                message = "Server returned HTTP ${response.code}: ${response.message.ifBlank { "Error" }}",
                                statusCode = response.code,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                ListDirectoryResult.Failure(
                    message = e.localizedMessage ?: "Network connection error",
                    cause = e,
                )
            } catch (e: Exception) {
                ListDirectoryResult.Failure(
                    message = e.localizedMessage ?: "Unexpected error",
                    cause = e,
                )
            }
        }

    override suspend fun fetchRange(
        server: WebDavServer,
        remotePath: String,
        startByte: Long,
        endByte: Long,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val client = buildClientForServer(server)
            val fileUrl = server.resolveFileUrl(remotePath)

            val requestBuilder =
                Request
                    .Builder()
                    .url(fileUrl)
                    .get()
                    .header("Range", "bytes=$startByte-$endByte")

            if (server.username.isNotBlank()) {
                val credential = Credentials.basic(server.username, server.password)
                requestBuilder.header("Authorization", credential)
            }

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 206 || response.code == 200) {
                        response.body?.bytes()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun fetchText(
        server: WebDavServer,
        remotePath: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val client = buildClientForServer(server)
            val fileUrl = server.resolveFileUrl(remotePath)

            val requestBuilder =
                Request
                    .Builder()
                    .url(fileUrl)
                    .get()

            if (server.username.isNotBlank()) {
                val credential = Credentials.basic(server.username, server.password)
                requestBuilder.header("Authorization", credential)
            }

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 200) {
                        val bytes = response.body?.bytes() ?: return@withContext null
                        decodeTextWithBom(bytes)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun decodeTextWithBom(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, java.nio.charset.StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, java.nio.charset.StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, java.nio.charset.StandardCharsets.UTF_16BE)
        }
        return try {
            val decoder =
                java.nio.charset.StandardCharsets.UTF_8
                    .newDecoder()
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            try {
                String(
                    bytes,
                    java.nio.charset.Charset
                        .forName("GBK"),
                )
            } catch (e2: Exception) {
                String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }

    private fun buildDirectoryUrl(
        server: WebDavServer,
        path: String,
    ): String {
        val baseUrl = server.endpointUrl.trimEnd('/')
        var cleanPath = path.replace('\\', '/')
        if (!cleanPath.startsWith("/")) cleanPath = "/$cleanPath"
        if (!cleanPath.endsWith("/")) cleanPath = "$cleanPath/"
        val segments = cleanPath.split('/')
        val encodedSegments =
            segments.map { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
        val encodedPath = encodedSegments.joinToString("/")
        return "$baseUrl$encodedPath"
    }

    fun getClientForServer(server: WebDavServer): OkHttpClient = buildClientForServer(server)

    fun buildClientForServer(server: WebDavServer): OkHttpClient {
        val cacheKey = buildCacheKey(server)
        val existing = clientCache[cacheKey]
        if (existing != null) {
            return existing
        }
        if (server.id > 0L) {
            invalidateServer(server.id)
        }
        return clientCache.computeIfAbsent(cacheKey) {
            createClient(server, isStreaming = false)
        }
    }

    fun getStreamingClientForServer(server: WebDavServer): OkHttpClient = buildStreamingClientForServer(server)

    fun buildStreamingClientForServer(server: WebDavServer): OkHttpClient {
        val cacheKey = "stream:" + buildCacheKey(server)
        val existing = clientCache[cacheKey]
        if (existing != null) {
            return existing
        }
        if (server.id > 0L) {
            invalidateServer(server.id)
        }
        return clientCache.computeIfAbsent(cacheKey) {
            createClient(server, isStreaming = true)
        }
    }

    fun invalidateServer(serverId: Long) {
        if (serverId <= 0L) return
        val prefix = "$serverId:"
        val streamPrefix = "stream:$serverId:"
        clientCache.keys.removeIf { it.startsWith(prefix) || it.startsWith(streamPrefix) }
    }

    fun clearCache() {
        clientCache.clear()
    }

    val cachedClientCount: Int
        get() = clientCache.size

    private fun buildCacheKey(server: WebDavServer): String =
        "${server.id}:${server.endpointUrl}:${server.username}:${server.password}:${server.allowSelfSigned}"

    private fun createClient(
        server: WebDavServer,
        isStreaming: Boolean = false,
    ): OkHttpClient {
        val base = if (isStreaming) streamingBaseOkHttpClient else baseOkHttpClient
        val builder = base.newBuilder()

        if (server.username.isNotBlank()) {
            val credential = Credentials.basic(server.username, server.password)
            builder.addInterceptor { chain ->
                val request = chain.request()
                if (request.header("Authorization") == null) {
                    chain.proceed(request.newBuilder().header("Authorization", credential).build())
                } else {
                    chain.proceed(request)
                }
            }
            builder.authenticator { _, response ->
                if (response.request.header("Authorization") != null) {
                    // Already tried with authorization and failed, prevent 401 retry loop
                    null
                } else {
                    response.request
                        .newBuilder()
                        .header("Authorization", credential)
                        .build()
                }
            }
        }

        if (server.allowSelfSigned) {
            val trustAllCerts =
                arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?,
                        ) {}

                        override fun checkServerTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?,
                        ) {}

                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    },
                )

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }
}
