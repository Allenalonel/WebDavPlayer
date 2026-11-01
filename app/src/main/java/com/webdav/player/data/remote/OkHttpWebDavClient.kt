package com.webdav.player.data.remote

import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class OkHttpWebDavClient(
    val streamingConnectionPool: ConnectionPool =
        ConnectionPool(
            DEFAULT_STREAMING_MAX_IDLE_CONNECTIONS,
            DEFAULT_STREAMING_KEEP_ALIVE_MINUTES,
            TimeUnit.MINUTES,
        ),
    private val baseOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .dns(FastFallbackDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build(),
    private val streamingBaseOkHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .dns(FastFallbackDns)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectionPool(streamingConnectionPool)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build(),
) : WebDavClient {
    constructor(
        baseOkHttpClient: OkHttpClient,
        streamingBaseOkHttpClient: OkHttpClient,
    ) : this(
        streamingConnectionPool = streamingBaseOkHttpClient.connectionPool,
        baseOkHttpClient = baseOkHttpClient,
        streamingBaseOkHttpClient = streamingBaseOkHttpClient,
    )

    companion object {
        const val DEFAULT_STREAMING_MAX_IDLE_CONNECTIONS: Int = 8
        const val DEFAULT_STREAMING_KEEP_ALIVE_MINUTES: Long = 5L
    }

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()
    private val digestServerHosts = ConcurrentHashMap.newKeySet<String>()

    override suspend fun testConnection(server: WebDavServer): ConnectionResult =
        withContext(Dispatchers.IO) {
            val client = buildClientForServer(server)

            val request =
                Request
                    .Builder()
                    .url(server.endpointUrl)
                    .method("PROPFIND", null)
                    .header("Depth", "0")
                    .build()

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

            val request =
                Request
                    .Builder()
                    .url(fullUrl)
                    .method("PROPFIND", null)
                    .header("Depth", "1")
                    .build()

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

            val request =
                Request
                    .Builder()
                    .url(fileUrl)
                    .get()
                    .header("Range", "bytes=$startByte-$endByte")
                    .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 206 || response.code == 200) {
                        response.body?.bytes()
                    } else if (response.code == 404) {
                        null
                    } else {
                        throw java.io.IOException("HTTP error ${response.code} fetching range for $remotePath")
                    }
                }
            } catch (e: java.io.IOException) {
                throw e
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

            val request =
                Request
                    .Builder()
                    .url(fileUrl)
                    .get()
                    .build()

            try {
                client.newCall(request).execute().use { response ->
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
        return server.resolveFileUrl(if (path.endsWith("/")) path else "$path/")
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
        if (isStreaming) {
            builder.connectionPool(streamingConnectionPool)
            builder.protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        }

        if (server.username.isNotBlank()) {
            val credential = Credentials.basic(server.username, server.password)
            val digestAuthenticator =
                WebDavDigestAuthenticator(
                    username = server.username,
                    password = server.password,
                    onDigestAuthenticated = { digestServerHosts.add(server.hostSummary) },
                )

            builder.addInterceptor { chain ->
                val request = chain.request()
                val shouldPreemptBasic = !digestServerHosts.contains(server.hostSummary)
                if (request.header("Authorization") == null && shouldPreemptBasic) {
                    chain.proceed(request.newBuilder().header("Authorization", credential).build())
                } else {
                    chain.proceed(request)
                }
            }

            builder.authenticator { route, response ->
                // First attempt Digest authentication
                val digestRequest = digestAuthenticator.authenticate(route, response)
                if (digestRequest != null) return@authenticator digestRequest

                // Fallback for servers challenging with Basic (e.g. unencrypted HTTP servers where preemptive basic was suppressed)
                val challenges = response.headers("WWW-Authenticate")
                val basicHeader = challenges.firstOrNull { it.trimStart().startsWith("Basic", ignoreCase = true) }
                if (basicHeader != null && response.request.header("Authorization") == null) {
                    response.request.newBuilder().header("Authorization", credential).build()
                } else {
                    null
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

            val x509TrustManager = trustAllCerts[0] as? X509TrustManager
            if (x509TrustManager != null) {
                builder.sslSocketFactory(sslSocketFactory, x509TrustManager)
            }
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }
}

/**
 * RFC 8305 Happy Eyeballs fast fallback DNS for dual-stack IPv4/IPv6 networks.
 * Sorts reachable IPv4 addresses before IPv6 addresses when resolving dual-stack hostnames,
 * mitigating 10-15 second connection timeouts caused by broken or blackholed IPv6 routes on home NAS setups.
 */
internal object FastFallbackDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.size <= 1) return addresses
        return addresses.sortedWith(
            compareBy { if (it is Inet4Address) 0 else 1 },
        )
    }
}
