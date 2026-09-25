package com.webdav.player.data.remote

import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
