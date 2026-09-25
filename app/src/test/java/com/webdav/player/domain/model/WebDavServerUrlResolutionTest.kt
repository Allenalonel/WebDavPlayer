package com.webdav.player.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavServerUrlResolutionTest {

    @Test
    fun resolveFileUrl_httpStandardPort() {
        val server = WebDavServer(
            id = 1L,
            name = "Test HTTP",
            url = "http://my-nas.local",
            port = 80,
            pathPrefix = "/webdav"
        )
        val url = server.resolveFileUrl("/Music/Album/track01.mp3")
        assertEquals("http://my-nas.local/webdav/Music/Album/track01.mp3", url)
    }

    @Test
    fun resolveFileUrl_httpCustomPort() {
        val server = WebDavServer(
            id = 1L,
            name = "Test HTTP Port",
            url = "http://my-nas.local",
            port = 5005,
            pathPrefix = "/webdav"
        )
        val url = server.resolveFileUrl("/Music/track01.flac")
        assertEquals("http://my-nas.local:5005/webdav/Music/track01.flac", url)
    }

    @Test
    fun resolveFileUrl_httpsStandardPort() {
        val server = WebDavServer(
            id = 1L,
            name = "Test HTTPS",
            url = "https://secure-nas.local",
            port = 443,
            pathPrefix = "/remote.php/webdav"
        )
        val url = server.resolveFileUrl("/Audio/song.wav")
        assertEquals("https://secure-nas.local/remote.php/webdav/Audio/song.wav", url)
    }

    @Test
    fun resolveFileUrl_httpsCustomPort() {
        val server = WebDavServer(
            id = 1L,
            name = "Test HTTPS Port",
            url = "https://secure-nas.local",
            port = 8443,
            pathPrefix = "/dav"
        )
        val url = server.resolveFileUrl("/aac_track.aac")
        assertEquals("https://secure-nas.local:8443/dav/aac_track.aac", url)
    }

    @Test
    fun resolveFileUrl_encodesSpacesSpecialCharsAndUnicode() {
        val server = WebDavServer(
            id = 1L,
            name = "Special Chars",
            url = "http://192.168.1.50:8080",
            port = 8080,
            pathPrefix = "/dav"
        )
        val url = server.resolveFileUrl("/Rock & Roll/周杰伦 - 晴天.mp3")
        assertEquals("http://192.168.1.50:8080/dav/Rock%20%26%20Roll/%E5%91%A8%E6%9D%B0%E4%BC%A6%20-%20%E6%99%B4%E5%A4%A9.mp3", url)
    }

    @Test
    fun audioTrack_fromRemoteFile_andStreamUrl() {
        val server = WebDavServer(
            id = 1L,
            name = "Server",
            url = "http://nas:8080",
            port = 8080,
            pathPrefix = "/"
        )
        val mp3File = RemoteFile(name = "track.mp3", path = "/Music/track.mp3", size = 12345L)
        val track = AudioTrack.fromRemoteFile(server, mp3File)

        assertNotNull(track)
        assertEquals("1:/Music/track.mp3", track!!.id)
        assertEquals("track.mp3", track.title)
        assertEquals(AudioFormat.MP3, track.format)
        assertEquals(12345L, track.size)
        assertEquals("http://nas:8080/Music/track.mp3", track.streamUrl(server))

        val nonAudioFile = RemoteFile(name = "lyrics.lrc", path = "/Music/lyrics.lrc")
        val nullTrack = AudioTrack.fromRemoteFile(server, nonAudioFile)
        assertNull(nullTrack)
    }
}
