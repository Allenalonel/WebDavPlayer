package com.webdav.player.data.remote

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.RemoteFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavXmlParserTest {

    @Test
    fun parseMultistatus_parsesUnicodeAndFullUrlHrefs() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>https://nas.local:5005/dav/%E9%9F%B3%E4%B9%90/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>https://nas.local:5005/dav/%E9%9F%B3%E4%B9%90/%E5%91%A8%E6%9D%B0%E4%BC%A6/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>https://nas.local:5005/dav/%E9%9F%B3%E4%B9%90/%E6%99%B4%E5%A4%A9.flac</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength>31457280</d:getcontentlength>
                    <d:getlastmodified>Wed, 14 Jan 2026 08:30:00 GMT</d:getlastmodified>
                    <d:getcontenttype>audio/flac</d:getcontenttype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val directory = WebDavXmlParser.parseMultistatus(
            xml = xml,
            serverPathPrefix = "/dav",
            requestedPath = "/音乐/"
        )

        assertEquals("音乐", directory.name)
        assertEquals("/音乐/", directory.path)

        assertEquals(1, directory.subDirectories.size)
        assertEquals("周杰伦", directory.subDirectories[0].name)
        assertEquals("/音乐/周杰伦/", directory.subDirectories[0].path)

        assertEquals(1, directory.files.size)
        val file = directory.files[0]
        assertEquals("晴天.flac", file.name)
        assertEquals("/音乐/晴天.flac", file.path)
        assertEquals(31457280L, file.size)
        assertEquals("Wed, 14 Jan 2026 08:30:00 GMT", file.lastModified)
        assertEquals(RemoteFileType.Audio(AudioFormat.FLAC), file.fileType)
        assertTrue(file.isAudio)
    }

    @Test
    fun parseMultistatus_identifiesAllAudioFormatsAndLyrics() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/root/</href>
                <propstat><prop><resourcetype><collection/></resourcetype></prop><status>HTTP/1.1 200 OK</status></propstat>
              </response>
              <response><href>/root/1.mp3</href><propstat><prop/></propstat></response>
              <response><href>/root/2.flac</href><propstat><prop/></propstat></response>
              <response><href>/root/3.wav</href><propstat><prop/></propstat></response>
              <response><href>/root/4.wma</href><propstat><prop/></propstat></response>
              <response><href>/root/5.aac</href><propstat><prop/></propstat></response>
              <response><href>/root/6.ogg</href><propstat><prop/></propstat></response>
              <response><href>/root/7.m4a</href><propstat><prop/></propstat></response>
              <response><href>/root/song.lrc</href><propstat><prop/></propstat></response>
              <response><href>/root/image.png</href><propstat><prop/></propstat></response>
              <response><href>/root/document.pdf</href><propstat><prop/></propstat></response>
            </multistatus>
        """.trimIndent()

        val directory = WebDavXmlParser.parseMultistatus(
            xml = xml,
            serverPathPrefix = "/",
            requestedPath = "/root/"
        )

        assertEquals(10, directory.files.size)
        val audioFiles = directory.audioFiles
        assertEquals(7, audioFiles.size)

        val lrc = directory.files.first { it.name == "song.lrc" }
        assertTrue(lrc.isLyrics)
        assertFalse(lrc.isAudio)

        val png = directory.files.first { it.name == "image.png" }
        assertEquals(RemoteFileType.Other, png.fileType)
        assertFalse(png.isLyrics)
        assertFalse(png.isAudio)
    }

    @Test
    fun parseMultistatus_preservesLiteralPlusAndPercentEncodedSpaces() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/Pop+Rock/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/Pop+Rock/1+1.mp3</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength>1024</d:getcontentlength>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/Pop+Rock/Track%20(Bonus)+Mix.flac</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength>2048</d:getcontentlength>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/Pop+Rock/AC+DC%20-%20TNT.mp3</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength>4096</d:getcontentlength>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val directory = WebDavXmlParser.parseMultistatus(
            xml = xml,
            serverPathPrefix = "/dav",
            requestedPath = "/Pop+Rock/"
        )

        assertEquals("Pop+Rock", directory.name)
        assertEquals("/Pop+Rock/", directory.path)
        assertEquals(3, directory.files.size)

        val file1 = directory.files.first { it.name == "1+1.mp3" }
        assertEquals("/Pop+Rock/1+1.mp3", file1.path)

        val file2 = directory.files.first { it.name == "Track (Bonus)+Mix.flac" }
        assertEquals("/Pop+Rock/Track (Bonus)+Mix.flac", file2.path)

        val file3 = directory.files.first { it.name == "AC+DC - TNT.mp3" }
        assertEquals("/Pop+Rock/AC+DC - TNT.mp3", file3.path)
    }

    @Test
    fun parseMultistatus_preservesChineseCharactersAndParentheses() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/%E5%91%A8%E6%9D%B0%E4%BC%A6%20(%E7%89%B9%E5%88%AB%E7%89%88)/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/%E5%91%A8%E6%9D%B0%E4%BC%A6%20(%E7%89%B9%E5%88%AB%E7%89%88)/%E6%99%B4%E5%A4%A9+Bonus.flac</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength>5120</d:getcontentlength>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val directory = WebDavXmlParser.parseMultistatus(
            xml = xml,
            serverPathPrefix = "/dav",
            requestedPath = "/周杰伦 (特别版)/"
        )

        assertEquals("周杰伦 (特别版)", directory.name)
        assertEquals(1, directory.files.size)
        assertEquals("晴天+Bonus.flac", directory.files[0].name)
        assertEquals("/周杰伦 (特别版)/晴天+Bonus.flac", directory.files[0].path)
    }

    @Test
    fun decodeRfc3986Path_handlesMalformedSequencesSafely() {
        assertEquals("100%_Love.mp3", WebDavXmlParser.decodeRfc3986Path("100%_Love.mp3"))
        assertEquals("Track%2", WebDavXmlParser.decodeRfc3986Path("Track%2"))
        assertEquals("Test%ZZ", WebDavXmlParser.decodeRfc3986Path("Test%ZZ"))
        assertEquals("A+B C+ D", WebDavXmlParser.decodeRfc3986Path("A+B%20C%2B%20D"))
        assertEquals("", WebDavXmlParser.decodeRfc3986Path(""))
    }
}
