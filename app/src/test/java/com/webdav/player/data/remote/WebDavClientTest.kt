package com.webdav.player.data.remote

import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class WebDavClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    @Before
    fun setUp() {
        server = MockWebServer()
        client = OkHttpWebDavClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testConnection_sendsPropfind_andReturnsSuccessOn207() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody("""<?xml version="1.0" encoding="utf-8"?><multistatus xmlns="DAV:"></multistatus>""")
        )
        server.start()

        val webDavServer = WebDavServer(
            name = "Test Server",
            url = "http://${server.hostName}",
            port = server.port,
            pathPrefix = "/dav"
        )

        val result = client.testConnection(webDavServer)

        assertTrue("Expected Success but got $result", result is ConnectionResult.Success)
        val recordedRequest = server.takeRequest()
        assertEquals("PROPFIND", recordedRequest.method)
        assertEquals("/dav", recordedRequest.path)
        assertEquals("0", recordedRequest.getHeader("Depth"))
    }

    @Test
    fun testConnection_sendsBasicAuthHeader_whenUsernamePasswordProvided() = runTest {
        server.enqueue(MockResponse().setResponseCode(207))
        server.start()

        val webDavServer = WebDavServer(
            name = "Auth Server",
            url = "http://${server.hostName}",
            port = server.port,
            username = "alice",
            password = "secretpassword"
        )

        val result = client.testConnection(webDavServer)

        assertTrue(result is ConnectionResult.Success)
        val recordedRequest = server.takeRequest()
        // base64("alice:secretpassword") = "YWxpY2U6c2VjcmV0cGFzc3dvcmQ="
        assertEquals("Basic YWxpY2U6c2VjcmV0cGFzc3dvcmQ=", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun testConnection_returnsFailure_whenServerReturns401() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        server.start()

        val webDavServer = WebDavServer(
            name = "Unauthorized Server",
            url = "http://${server.hostName}",
            port = server.port,
            username = "wrong",
            password = "bad"
        )

        val result = client.testConnection(webDavServer)

        assertTrue("Expected Failure but got $result", result is ConnectionResult.Failure)
        val failure = result as ConnectionResult.Failure
        assertEquals(401, failure.statusCode)
    }

    @Test
    fun testConnection_withSelfSignedCertificate_succeeds_whenAllowSelfSignedIsTrue() = runTest {
        val localhostCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName(InetAddress.getByName("localhost").canonicalHostName)
            .addSubjectAlternativeName("localhost")
            .build()

        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(localhostCertificate)
            .build()

        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.enqueue(MockResponse().setResponseCode(207))
        server.start()

        val webDavServer = WebDavServer(
            name = "Self Signed Server",
            url = "https://localhost",
            port = server.port,
            allowSelfSigned = true
        )

        val result = client.testConnection(webDavServer)
        assertTrue("Expected Success with self-signed allowed, but got $result", result is ConnectionResult.Success)
    }

    @Test
    fun testConnection_withSelfSignedCertificate_fails_whenAllowSelfSignedIsFalse() = runTest {
        val localhostCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName(InetAddress.getByName("localhost").canonicalHostName)
            .addSubjectAlternativeName("localhost")
            .build()

        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(localhostCertificate)
            .build()

        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.enqueue(MockResponse().setResponseCode(207))
        server.start()

        val webDavServer = WebDavServer(
            name = "Strict SSL Server",
            url = "https://localhost",
            port = server.port,
            allowSelfSigned = false
        )

        val result = client.testConnection(webDavServer)
        assertTrue("Expected Failure when untrusted certificate and allowSelfSigned is false", result is ConnectionResult.Failure)
    }
}
