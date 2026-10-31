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
    fun searchTrackItem_composableSignatureExistsOnClasspath() {
        val clazz =
            runCatching {
                Class.forName("com.webdav.player.ui.browser.components.SearchTrackItemKt")
            }.getOrElse {
                Class.forName("com.webdav.player.ui.browser.components.SearchResultsViewsKt")
            }
        val methodNames = clazz.methods.map { it.name }
        assertTrue(
            "SearchTrackItem or SearchAudioTrackItem must exist on classpath",
            methodNames.any { it.startsWith("SearchTrackItem") || it.startsWith("SearchAudioTrackItem") },
        )
    }

    @Test
    fun cueTagBadge_composableSignatureExistsOnClasspath() {
        val clazz = Class.forName("com.webdav.player.ui.browser.components.SearchTrackItemKt")
        val methodNames = clazz.methods.map { it.name }
        assertTrue("CueTagBadge must exist on classpath", methodNames.any { it.startsWith("CueTagBadge") })
    }

    @Test
    fun searchTrack_hasQualityBadge() {
        val flacTrack = createTrack()
        assertEquals("FLAC", flacTrack.qualityBadge.label)
        assertTrue(flacTrack.qualityBadge.isLossless)
    }

    @Test
    fun searchResultsViews_andCollaborators_lineCountWithinBudget() {
        val mainFile = File("src/main/java/com/webdav/player/ui/browser/components/SearchResultsViews.kt")
        assertTrue("SearchResultsViews.kt must exist", mainFile.exists())
        val mainLines = mainFile.readLines().size
        assertTrue(
            "SearchResultsViews.kt line count must be <= 180 lines, but was $mainLines",
            mainLines <= 180,
        )

        val collaboratorFile = File("src/main/java/com/webdav/player/ui/browser/components/SearchTrackItem.kt")
        if (collaboratorFile.exists()) {
            val collabLines = collaboratorFile.readLines().size
            assertTrue(
                "SearchTrackItem.kt line count must be < 200 lines, but was $collabLines",
                collabLines < 200,
            )
        }
    }
}
