package com.webdav.player.data.remote

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavDigestAuthenticatorTest {
    @Test
    fun calculateDigestResponse_matchesRfc2617TestVector() {
        // Official RFC 2617 Section 3.5 sample test vector
        val response =
            WebDavDigestAuthenticator.calculateDigestResponse(
                username = "Mufasa",
                password = "Circle Of Life",
                realm = "testrealm@host.com",
                nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093",
                nc = "00000001",
                cnonce = "0a4f113b",
                qop = "auth",
                method = "GET",
                uri = "/dir/index.html",
                algorithm = "MD5",
            )

        assertEquals("6629fae49393a05397450978507c4ef1", response)
    }

    @Test
    fun calculateDigestResponse_legacyWithoutQop() {
        // Without qop: response = MD5(HA1:nonce:HA2)
        val ha1 = WebDavDigestAuthenticator.md5Hex("user:realm1:pass")
        val ha2 = WebDavDigestAuthenticator.md5Hex("PROPFIND:/dav/music/")
        val expected = WebDavDigestAuthenticator.md5Hex("$ha1:nonce999:$ha2")

        val response =
            WebDavDigestAuthenticator.calculateDigestResponse(
                username = "user",
                password = "pass",
                realm = "realm1",
                nonce = "nonce999",
                nc = null,
                cnonce = null,
                qop = null,
                method = "PROPFIND",
                uri = "/dav/music/",
                algorithm = "MD5",
            )

        assertEquals(expected, response)
    }

    @Test
    fun parseDigestChallenge_extractsDirectivesAccurately() {
        val authenticator = WebDavDigestAuthenticator("user", "pass")
        val header = """Digest realm="WebDAV Storage", nonce="a1b2c3d4", qop="auth", opaque="opq987", algorithm=MD5"""
        val challenge = authenticator.parseDigestChallenge(header)

        assertNotNull(challenge)
        assertEquals("WebDAV Storage", challenge?.realm)
        assertEquals("a1b2c3d4", challenge?.nonce)
        assertEquals("auth", challenge?.qop)
        assertEquals("opq987", challenge?.opaque)
        assertEquals("MD5", challenge?.algorithm)
    }

    @Test
    fun parseDigestChallenge_handlesUnquotedDirectivesAndSpacing() {
        val authenticator = WebDavDigestAuthenticator("user", "pass")
        val header = "Digest realm=SynologyNAS, nonce=xyz789, qop=auth, algorithm=MD5"
        val challenge = authenticator.parseDigestChallenge(header)

        assertNotNull(challenge)
        assertEquals("SynologyNAS", challenge?.realm)
        assertEquals("xyz789", challenge?.nonce)
        assertEquals("auth", challenge?.qop)
        assertEquals("MD5", challenge?.algorithm)
    }

    @Test
    fun authenticate_terminatesAfterOneFailedAttempt_preventingLoop() {
        val authenticator = WebDavDigestAuthenticator("user", "wrong_password")
        val request =
            Request
                .Builder()
                .url("http://nas.local/dav")
                .header("Authorization", """Digest username="user", realm="Nas", nonce="nonce1", response="badhash"""")
                .build()

        val response =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .header("WWW-Authenticate", """Digest realm="Nas", nonce="nonce1", qop="auth"""")
                .build()

        // Same nonce with existing Digest header must return null to stop retry loop
        val retryRequest = authenticator.authenticate(null, response)
        assertNull("Authenticator must terminate retry loop when Digest with same nonce fails", retryRequest)
    }

    @Test
    fun buildDigestAuthorization_includesAllRequiredFields() {
        val authenticator = WebDavDigestAuthenticator("myuser", "mypass")
        val request =
            Request
                .Builder()
                .url("http://example.com/audio/song.flac")
                .method("GET", null)
                .build()
        val challenge =
            DigestChallenge(
                realm = "MusicRealm",
                nonce = "nc12345",
                qop = "auth",
                opaque = "opaqueVal",
                algorithm = "MD5",
            )

        val header = authenticator.buildDigestAuthorization(request, challenge, fixedCnonce = "clientnonce42")
        assertTrue(header.startsWith("Digest "))
        assertTrue(header.contains("""username="myuser""""))
        assertTrue(header.contains("""realm="MusicRealm""""))
        assertTrue(header.contains("""nonce="nc12345""""))
        assertTrue(header.contains("""uri="/audio/song.flac""""))
        assertTrue(header.contains("""qop=auth"""))
        assertTrue(header.contains("""nc=00000001"""))
        assertTrue(header.contains("""cnonce="clientnonce42""""))
        assertTrue(header.contains("""opaque="opaqueVal""""))
        assertTrue(header.contains("algorithm=MD5"))
        assertTrue(header.contains("response="))
    }
}
