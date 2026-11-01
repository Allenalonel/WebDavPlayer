package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.ui.browser.components.resolveSearchTrackSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchTrackItemTest {
    private fun createTrack(
        id: String = "track_1",
        title: String = "Comfortably Numb",
        artist: String? = "Pink Floyd",
        album: String? = "The Wall",
        remotePath: String = "/Music/Rock/Pink Floyd/The Wall/01.flac",
    ): AudioTrack =
        AudioTrack(
            id = id,
            serverId = 1L,
            remotePath = remotePath,
            title = title,
            artist = artist,
            album = album,
            format = AudioFormat.FLAC,
        )

    @Test
    fun resolveSearchTrackSubtitle_withArtistAndAlbum_joinsCorrectly() {
        val track = createTrack(artist = "Pink Floyd", album = "The Wall")
        val subtitle = resolveSearchTrackSubtitle(track)
        assertEquals("Pink Floyd · The Wall", subtitle)
    }

    @Test
    fun resolveSearchTrackSubtitle_withArtistOnly_returnsArtist() {
        val track = createTrack(artist = "David Bowie", album = null)
        val subtitle = resolveSearchTrackSubtitle(track)
        assertEquals("David Bowie", subtitle)
    }

    @Test
    fun resolveSearchTrackSubtitle_withAlbumOnly_defaultsToUnknownArtist() {
        val track = createTrack(artist = null, album = "Greatest Hits")
        val subtitle = resolveSearchTrackSubtitle(track)
        assertEquals("未知艺术家 · Greatest Hits", subtitle)
    }

    @Test
    fun resolveSearchTrackSubtitle_withBlankArtist_defaultsToUnknownArtist() {
        val track = createTrack(artist = "   ", album = null)
        val subtitle = resolveSearchTrackSubtitle(track)
        assertEquals("未知艺术家", subtitle)
    }

    @Test
    fun resolveSearchTrackSubtitle_forVirtualTrack_appendsParentFileName() {
        val track =
            createTrack(
                id = "album.cue#cue_1",
                title = "Speak to Me",
                artist = "Pink Floyd",
                album = "The Dark Side of the Moon",
                remotePath = "/Music/Pink Floyd/dsotm.flac",
            )
        val subtitle = resolveSearchTrackSubtitle(track)
        assertEquals("Pink Floyd · The Dark Side of the Moon · dsotm.flac", subtitle)
    }

    @Test
    fun searchTrack_hasQualityBadge() {
        val flacTrack = createTrack()
        assertEquals("FLAC", flacTrack.qualityBadge.label)
        assertTrue(flacTrack.qualityBadge.isLossless)
    }
}
