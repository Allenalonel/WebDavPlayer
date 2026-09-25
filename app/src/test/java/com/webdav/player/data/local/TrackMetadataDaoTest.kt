package com.webdav.player.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackMetadataDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TrackMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.trackMetadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryMetadata_byServerIdAndPath() = runTest {
        val entity = TrackMetadataEntity(
            serverId = 1L,
            remotePath = "/music/song1.mp3",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            trackNumber = 11,
            durationMs = 354000L,
            coverThumbnailPath = "/cache/covers/cover_1.jpg"
        )

        dao.insertOrUpdate(entity)

        val retrieved = dao.getMetadata(1L, "/music/song1.mp3")
        assertNotNull(retrieved)
        assertEquals("Bohemian Rhapsody", retrieved?.title)
        assertEquals("Queen", retrieved?.artist)
        assertEquals("A Night at the Opera", retrieved?.album)
        assertEquals(11, retrieved?.trackNumber)
        assertEquals(354000L, retrieved?.durationMs)
        assertEquals("/cache/covers/cover_1.jpg", retrieved?.coverThumbnailPath)

        // Query non-existent
        val missing = dao.getMetadata(1L, "/music/song2.mp3")
        assertNull(missing)
    }

    @Test
    fun getMetadataForPathsFlow_updatesReactively() = runTest {
        val paths = listOf("/music/song1.mp3", "/music/song2.flac")
        val initial = dao.getMetadataForPaths(1L, paths)
        assertTrue(initial.isEmpty())

        dao.insertOrUpdateAll(
            listOf(
                TrackMetadataEntity(
                    serverId = 1L,
                    remotePath = "/music/song1.mp3",
                    title = "Song 1",
                    artist = "Artist 1",
                    album = "Album 1",
                    trackNumber = 1,
                    durationMs = 180000L,
                    coverThumbnailPath = null
                ),
                TrackMetadataEntity(
                    serverId = 1L,
                    remotePath = "/music/song2.flac",
                    title = "Song 2",
                    artist = "Artist 2",
                    album = "Album 2",
                    trackNumber = 2,
                    durationMs = 240000L,
                    coverThumbnailPath = "/cache/covers/cover_2.jpg"
                )
            )
        )

        val updated = dao.getMetadataForPathsFlow(1L, paths).first()
        assertEquals(2, updated.size)
    }

    @Test
    fun deleteMetadata_removesCorrectItem() = runTest {
        val entity = TrackMetadataEntity(
            serverId = 1L,
            remotePath = "/music/song.mp3",
            title = "Track",
            artist = "Artist",
            album = null,
            trackNumber = null,
            durationMs = 120000L,
            coverThumbnailPath = null
        )
        dao.insertOrUpdate(entity)
        assertNotNull(dao.getMetadata(1L, "/music/song.mp3"))

        dao.deleteMetadata(1L, "/music/song.mp3")
        assertNull(dao.getMetadata(1L, "/music/song.mp3"))
    }
}
