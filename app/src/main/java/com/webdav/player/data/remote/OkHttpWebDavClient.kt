package com.webdav.player.data.remote

import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class OkHttpWebDavClient(
    private val baseOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) : WebDavClient {

    override suspend fun testConnection(server: WebDavServer): ConnectionResult = withContext(Dispatchers.IO) {
        val client = buildClientForServer(server)

        val requestBuilder = Request.Builder()
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
                    200, 207 -> ConnectionResult.Success
                    401 -> ConnectionResult.Failure("Authentication failed: HTTP 401", statusCode = 401)
                    403 -> ConnectionResult.Failure("Access forbidden: HTTP 403", statusCode = 403)
                    else -> ConnectionResult.Failure(
                        message = "Server returned HTTP ${response.code}: ${response.message.ifBlank { "Error" }}",
                        statusCode = response.code
                    )
                }
            }
        } catch (e: IOException) {
            ConnectionResult.Failure(
                message = e.localizedMessage ?: "Network connection error",
                cause = e
            )
        } catch (e: Exception) {
            ConnectionResult.Failure(
                message = e.localizedMessage ?: "Unexpected error",
                cause = e
            )
        }
    }

    override suspend fun listDirectory(server: WebDavServer, path: String): ListDirectoryResult = withContext(Dispatchers.IO) {
        val client = buildClientForServer(server)
        val fullUrl = buildDirectoryUrl(server, path)

        val requestBuilder = Request.Builder()
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
                            val directory = WebDavXmlParser.parseMultistatus(
                                xml = body,
                                serverPathPrefix = server.pathPrefix,
                                requestedPath = path
                            )
                            ListDirectoryResult.Success(directory)
                        } catch (e: Exception) {
                            ListDirectoryResult.Failure(
                                message = "Failed to parse WebDAV XML: ${e.localizedMessage}",
                                statusCode = 207,
                                cause = e
                            )
                        }
                    }
                    401 -> ListDirectoryResult.Failure("Authentication failed: HTTP 401", statusCode = 401)
                    403 -> ListDirectoryResult.Failure("Access forbidden: HTTP 403", statusCode = 403)
                    404 -> ListDirectoryResult.Failure("Directory not found: HTTP 404", statusCode = 404)
                    else -> ListDirectoryResult.Failure(
                        message = "Server returned HTTP ${response.code}: ${response.message.ifBlank { "Error" }}",
                        statusCode = response.code
                    )
                }
            }
        } catch (e: IOException) {
            ListDirectoryResult.Failure(
                message = e.localizedMessage ?: "Network connection error",
                cause = e
            )
        } catch (e: Exception) {
            ListDirectoryResult.Failure(
                message = e.localizedMessage ?: "Unexpected error",
                cause = e
            )
        }
    }

    private fun buildDirectoryUrl(server: WebDavServer, path: String): String {
        val baseUrl = server.endpointUrl.trimEnd('/')
        var cleanPath = path.replace('\\', '/')
        if (!cleanPath.startsWith("/")) cleanPath = "/$cleanPath"
        if (!cleanPath.endsWith("/")) cleanPath = "$cleanPath/"
        val segments = cleanPath.split('/')
        val encodedSegments = segments.map { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        val encodedPath = encodedSegments.joinToString("/")
        return "$baseUrl$encodedPath"
    }

    fun buildClientForServer(server: WebDavServer): OkHttpClient {
        val builder = baseOkHttpClient.newBuilder()

        if (server.username.isNotBlank()) {
            builder.authenticator { _, response ->
                if (response.request.header("Authorization") != null) {
                    // Already tried, prevent endless loop
                    null
                } else {
                    val credential = Credentials.basic(server.username, server.password)
                    response.request.newBuilder().header("Authorization", credential).build()
                }
            }
        }

        if (server.allowSelfSigned) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }
}
