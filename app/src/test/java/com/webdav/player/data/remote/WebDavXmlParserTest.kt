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
}
