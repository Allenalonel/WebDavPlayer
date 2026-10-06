package com.webdav.player.data.remote

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    @Test
    fun listDirectory_sendsPropfindDepth1_andParsesSubDirectoriesAndFiles() = runTest {
        val xmlResponse = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/Music/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/Rock/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype><D:collection/></D:resourcetype>
                    <D:getlastmodified>Mon, 12 Jan 2026 12:00:00 GMT</D:getlastmodified>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/song1.mp3</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength>5242880</D:getcontentlength>
                    <D:getlastmodified>Mon, 12 Jan 2026 12:05:00 GMT</D:getlastmodified>
                    <D:getcontenttype>audio/mpeg</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/track2.flac</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength>25000000</D:getcontentlength>
                    <D:getcontenttype>audio/flac</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/audio.wav</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>30000000</D:getcontentlength>
                    <D:getcontenttype>audio/wav</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/audio.wma</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>12000000</D:getcontentlength>
                    <D:getcontenttype>audio/x-ms-wma</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/audio.aac</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>4000000</D:getcontentlength>
                    <D:getcontenttype>audio/aac</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/song1.lrc</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>1500</D:getcontentlength>
                    <D:getcontenttype>text/plain</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/Music/cover.jpg</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>80000</D:getcontentlength>
                    <D:getcontenttype>image/jpeg</D:getcontenttype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(207).setBody(xmlResponse))
        server.start()

        val webDavServer = WebDavServer(
            name = "Music Server",
            url = "http://${server.hostName}",
            port = server.port,
            pathPrefix = "/dav"
        )

        val result = client.listDirectory(webDavServer, "/Music/")

        assertTrue("Expected Success, got $result", result is ListDirectoryResult.Success)
        val success = result as ListDirectoryResult.Success
        val dir = success.directory

        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertEquals("/dav/Music/", recorded.path)
        assertEquals("1", recorded.getHeader("Depth"))

        // Directory properties
        assertEquals("/Music/", dir.path)
        assertEquals("Music", dir.name)

        // Subdirectories
        assertEquals(1, dir.subDirectories.size)
        val subDir = dir.subDirectories.first()
        assertEquals("Rock", subDir.name)
        assertEquals("/Music/Rock/", subDir.path)

        // Files
        assertEquals(7, dir.files.size)

        val mp3 = dir.files.first { it.name == "song1.mp3" }
        assertEquals(5242880L, mp3.size)
        assertEquals("audio/mpeg", mp3.contentType)
        assertEquals(RemoteFileType.Audio(AudioFormat.MP3), mp3.fileType)
        assertTrue(mp3.isAudio)

        val flac = dir.files.first { it.name == "track2.flac" }
        assertEquals(RemoteFileType.Audio(AudioFormat.FLAC), flac.fileType)

        val wav = dir.files.first { it.name == "audio.wav" }
        assertEquals(RemoteFileType.Audio(AudioFormat.WAV), wav.fileType)

        val wma = dir.files.first { it.name == "audio.wma" }
        assertEquals(RemoteFileType.Audio(AudioFormat.WMA), wma.fileType)

        val aac = dir.files.first { it.name == "audio.aac" }
        assertEquals(RemoteFileType.Audio(AudioFormat.AAC), aac.fileType)

        val lrc = dir.files.first { it.name == "song1.lrc" }
        assertEquals(RemoteFileType.Lyrics, lrc.fileType)
        assertTrue(lrc.isLyrics)

        val jpg = dir.files.first { it.name == "cover.jpg" }
        assertEquals(RemoteFileType.Other, jpg.fileType)
        org.junit.Assert.assertFalse(jpg.isAudio)
        org.junit.Assert.assertFalse(jpg.isLyrics)
    }

    @Test
    fun listDirectory_handlesUrlEncodingAndNamespaceVariants() = runTest {
        val xmlResponse = """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/My%20Music/</href>
                <propstat>
                  <prop><resourcetype><collection/></resourcetype></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
              <response>
                <href>/dav/My%20Music/Rock%20%26%20Roll/</href>
                <propstat>
                  <prop><resourcetype><collection/></resourcetype></prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
              <response>
                <href>/dav/My%20Music/Track%2001%20-%20Hello%20World.mp3</href>
                <propstat>
                  <prop>
                    <getcontentlength>12345</getcontentlength>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(207).setBody(xmlResponse))
        server.start()

        val webDavServer = WebDavServer(
            name = "Music Server",
            url = "http://${server.hostName}",
            port = server.port,
            pathPrefix = "/dav"
        )

        val result = client.listDirectory(webDavServer, "/My Music/")
        assertTrue(result is ListDirectoryResult.Success)
        val dir = (result as ListDirectoryResult.Success).directory

        assertEquals(1, dir.subDirectories.size)
        assertEquals("Rock & Roll", dir.subDirectories[0].name)
        assertEquals("/My Music/Rock & Roll/", dir.subDirectories[0].path)

        assertEquals(1, dir.files.size)
        assertEquals("Track 01 - Hello World.mp3", dir.files[0].name)
        assertEquals("/My Music/Track 01 - Hello World.mp3", dir.files[0].path)
    }

    @Test
    fun listDirectory_returnsFailure_whenServerReturns404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        server.start()

        val webDavServer = WebDavServer(
            name = "Test",
            url = "http://${server.hostName}",
            port = server.port
        )

        val result = client.listDirectory(webDavServer, "/nonexistent/")
        assertTrue(result is ListDirectoryResult.Failure)
        assertEquals(404, (result as ListDirectoryResult.Failure).statusCode)
    }

    @Test
    fun listDirectory_returnsEmptyDirectory_whenNoChildrenReturned() = runTest {
        val xmlResponse = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/EmptyFolder/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(207).setBody(xmlResponse))
        server.start()

        val webDavServer = WebDavServer(
            name = "Test",
            url = "http://${server.hostName}",
            port = server.port,
            pathPrefix = "/dav"
        )

        val result = client.listDirectory(webDavServer, "/EmptyFolder/")
        assertTrue(result is ListDirectoryResult.Success)
        val dir = (result as ListDirectoryResult.Success).directory
        assertTrue(dir.isEmpty)
        assertEquals(0, dir.subDirectories.size)
        assertEquals(0, dir.files.size)
    }

    @Test
    fun listDirectory_returnsFailure_whenXmlMalformed() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody("<multistatus>unclosed tag"))
        server.start()

        val webDavServer = WebDavServer(
            name = "Test",
            url = "http://${server.hostName}",
            port = server.port
        )

        val result = client.listDirectory(webDavServer, "/")
        assertTrue(result is ListDirectoryResult.Failure)
    }

    @Test
    fun buildClientForServer_withSameConfiguration_reusesCachedInstance() {
        val okHttpClient = OkHttpWebDavClient()
        val webDavServer = WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "https://nas.example.com",
            port = 443,
            pathPrefix = "/dav",
            username = "alice",
            password = "secretpassword",
            allowSelfSigned = true
        )

        val client1 = okHttpClient.buildClientForServer(webDavServer)
        val client2 = okHttpClient.buildClientForServer(webDavServer)

        assertSame(
            "Expected OkHttpClient instances to be identical across calls with identical configuration",
            client1,
            client2
        )
    }

    @Test
    fun buildClientForServer_whenParametersChange_createsNewClientInstance() {
        val okHttpClient = OkHttpWebDavClient()
        val baseServer = WebDavServer(
            id = 1L,
            name = "Test Server",
            url = "https://nas.example.com",
            port = 443,
            pathPrefix = "/dav",
            username = "alice",
            password = "secretpassword",
            allowSelfSigned = false
        )

        val initialClient = okHttpClient.buildClientForServer(baseServer)

        // 1. Username changed
        val userChangedServer = baseServer.copy(username = "bob")
        val userChangedClient = okHttpClient.buildClientForServer(userChangedServer)
        assertNotSame("Changing username must create a new OkHttpClient", initialClient, userChangedClient)

        // 2. Password changed
        val pwdChangedServer = baseServer.copy(password = "newpassword")
        val pwdChangedClient = okHttpClient.buildClientForServer(pwdChangedServer)
        assertNotSame("Changing password must create a new OkHttpClient", initialClient, pwdChangedClient)

        // 3. Endpoint URL (port/address) changed
        val addressChangedServer = baseServer.copy(port = 8443)
        val addressChangedClient = okHttpClient.buildClientForServer(addressChangedServer)
        assertNotSame("Changing address/port must create a new OkHttpClient", initialClient, addressChangedClient)

        // 4. allowSelfSigned changed
        val sslChangedServer = baseServer.copy(allowSelfSigned = true)
        val sslChangedClient = okHttpClient.buildClientForServer(sslChangedServer)
        assertNotSame("Changing allowSelfSigned must create a new OkHttpClient", initialClient, sslChangedClient)
    }

    @Test
    fun clearCache_and_invalidateServer_evictsCachedClients() {
        val okHttpClient = OkHttpWebDavClient()
        val server1 = WebDavServer(
            id = 1L,
            name = "Server 1",
            url = "https://server1.com",
            port = 443
        )
        val server2 = WebDavServer(
            id = 2L,
            name = "Server 2",
            url = "https://server2.com",
            port = 443
        )

        val client1A = okHttpClient.buildClientForServer(server1)
        val client2A = okHttpClient.buildClientForServer(server2)
        assertEquals(2, okHttpClient.cachedClientCount)

        // Invalidate single server
        okHttpClient.invalidateServer(1L)
        assertEquals(1, okHttpClient.cachedClientCount)

        val client1B = okHttpClient.buildClientForServer(server1)
        val client2B = okHttpClient.buildClientForServer(server2)
        assertNotSame("Server 1 client must be re-created after invalidateServer", client1A, client1B)
        assertSame("Server 2 client must remain cached", client2A, client2B)

        // Clear all cache
        okHttpClient.clearCache()
        assertEquals(0, okHttpClient.cachedClientCount)

        val client1C = okHttpClient.buildClientForServer(server1)
        assertNotSame("Server 1 client must be re-created after clearCache", client1B, client1C)
    }

    @Test
    fun operations_testConnection_listDirectory_fetchRange_fetchText_reuseSameCachedClient() = runTest {
        val okHttpClient = OkHttpWebDavClient()
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<?xml version="1.0" encoding="utf-8"?><multistatus xmlns="DAV:"></multistatus>"""))
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:"></D:multistatus>"""))
        server.enqueue(MockResponse().setResponseCode(206).setBody("audio-chunk"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("test-lyrics"))
        server.start()

        val webDavServer = WebDavServer(
            id = 42L,
            name = "Reuse Server",
            url = "http://${server.hostName}",
            port = server.port,
            pathPrefix = "/dav"
        )

        assertEquals(0, okHttpClient.cachedClientCount)

        // 1. testConnection
        val connResult = okHttpClient.testConnection(webDavServer)
        assertTrue(connResult is ConnectionResult.Success)
        assertEquals(1, okHttpClient.cachedClientCount)
        val initialClient = okHttpClient.buildClientForServer(webDavServer)

        // 2. listDirectory
        val listResult = okHttpClient.listDirectory(webDavServer, "/")
        assertTrue(listResult is ListDirectoryResult.Success)
        assertEquals(1, okHttpClient.cachedClientCount)
        assertSame(initialClient, okHttpClient.buildClientForServer(webDavServer))

        // 3. fetchRange
        val rangeBytes = okHttpClient.fetchRange(webDavServer, "/test.mp3", 0L, 10L)
        org.junit.Assert.assertNotNull(rangeBytes)
        assertEquals(1, okHttpClient.cachedClientCount)
        assertSame(initialClient, okHttpClient.buildClientForServer(webDavServer))

        // 4. fetchText
        val text = okHttpClient.fetchText(webDavServer, "/test.lrc")
        assertEquals("test-lyrics", text)
        assertEquals(1, okHttpClient.cachedClientCount)
        assertSame(initialClient, okHttpClient.buildClientForServer(webDavServer))
    }

    @Test
    fun buildClientForServer_isThreadSafeUnderConcurrentAccess() = runTest {
        val okHttpClient = OkHttpWebDavClient()
        val serverConfig = WebDavServer(
            id = 99L,
            name = "Concurrent Server",
            url = "https://concurrent.example.com",
            port = 443,
            allowSelfSigned = true
        )

        val dispatcher = kotlinx.coroutines.Dispatchers.Default
        val deferredClients = (1..20).map {
            async(dispatcher) {
                okHttpClient.buildClientForServer(serverConfig)
            }
        }

        val clients = deferredClients.awaitAll()
        val firstClient = clients.first()

        assertEquals(1, okHttpClient.cachedClientCount)
        clients.forEach { client ->
            assertSame(
                "All concurrent callers must receive the exact same OkHttpClient instance",
                firstClient,
                client
            )
        }
    }

    @Test
    fun streamingConnectionPool_parametersAndSharing_verified() {
        val client = OkHttpWebDavClient()
        assertEquals(8, OkHttpWebDavClient.DEFAULT_STREAMING_MAX_IDLE_CONNECTIONS)
        assertTrue(OkHttpWebDavClient.DEFAULT_STREAMING_MAX_IDLE_CONNECTIONS >= 4)
        assertEquals(5L, OkHttpWebDavClient.DEFAULT_STREAMING_KEEP_ALIVE_MINUTES)

        val server1 = WebDavServer(id = 1L, name = "S1", url = "http://192.168.1.1:8080/dav")
        val server2 = WebDavServer(id = 2L, name = "S2", url = "http://192.168.1.2:8080/dav")

        val streamClient1 = client.buildStreamingClientForServer(server1)
        val streamClient2 = client.buildStreamingClientForServer(server2)

        assertSame(
            "All streaming clients must reuse the dedicated streaming connection pool",
            client.streamingConnectionPool,
            streamClient1.connectionPool
        )
        assertSame(
            "Streaming clients for different servers must share the same connection pool",
            streamClient1.connectionPool,
            streamClient2.connectionPool
        )
    }
}
