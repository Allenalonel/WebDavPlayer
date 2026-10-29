package com.webdav.player.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseMigrationTest {
    @Test
    fun migration_4_to_5_createsPlaylistsAndTracksTablesPreservingExistingData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        dbFile.delete()

        val openHelper =
            FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name("test_migration.db")
                    .callback(
                        object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                // Version 4 schema
                                db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `servers` (
                                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `name` TEXT NOT NULL,
                                        `url` TEXT NOT NULL,
                                        `username` TEXT NOT NULL,
                                        `password` TEXT NOT NULL,
                                        `isDefault` INTEGER NOT NULL,
                                        `allowSelfSigned` INTEGER NOT NULL DEFAULT 0
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `track_metadata` (
                                        `serverId` INTEGER NOT NULL,
                                        `remotePath` TEXT NOT NULL,
                                        `title` TEXT,
                                        `artist` TEXT,
                                        `album` TEXT,
                                        `trackNumber` INTEGER,
                                        `durationMs` INTEGER NOT NULL,
                                        `coverThumbnailPath` TEXT,
                                        `lyrics` TEXT,
                                        PRIMARY KEY(`serverId`, `remotePath`)
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `directory_cache` (
                                        `serverId` INTEGER NOT NULL,
                                        `path` TEXT NOT NULL,
                                        `dataJson` TEXT NOT NULL,
                                        `lastUpdatedMs` INTEGER NOT NULL,
                                        PRIMARY KEY(`serverId`, `path`)
                                    )
                                    """.trimIndent(),
                                )
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) {}
                        },
                    ).build(),
            )

        val db = openHelper.writableDatabase

        // Insert test data into v4 tables
        db.execSQL(
            "INSERT INTO servers (id, name, url, username, password, isDefault, allowSelfSigned) VALUES (1, 'NAS', 'http://nas', 'u', 'p', 1, 0)",
        )
        db.execSQL("INSERT INTO directory_cache (serverId, path, dataJson, lastUpdatedMs) VALUES (1, '/music', '{\"files\":[]}', 123456)")

        // Run MIGRATION_4_5
        AppDatabase.MIGRATION_4_5.migrate(db)

        // Verify existing v4 data is intact
        val cursorServers = db.query("SELECT name FROM servers WHERE id = 1")
        assertTrue(cursorServers.moveToFirst())
        assertEquals("NAS", cursorServers.getString(0))
        cursorServers.close()

        val cursorCache = db.query("SELECT path FROM directory_cache WHERE serverId = 1")
        assertTrue(cursorCache.moveToFirst())
        assertEquals("/music", cursorCache.getString(0))
        cursorCache.close()

        // Verify playlists table was created and can be written to
        db.execSQL("INSERT INTO playlists (id, name, createdAtMs, updatedAtMs) VALUES (10, '精选集', 1000, 1000)")
        val cursorPlaylists = db.query("SELECT name FROM playlists WHERE id = 10")
        assertTrue(cursorPlaylists.moveToFirst())
        assertEquals("精选集", cursorPlaylists.getString(0))
        cursorPlaylists.close()

        // Verify playlist_tracks table was created and can be written to
        db.execSQL(
            """
            INSERT INTO playlist_tracks (id, playlistId, serverId, remotePath, sortOrder, title, artist, album, durationMs, size, format, coverThumbnailPath, cuePath, virtualTrackId)
            VALUES (100, 10, 1, '/music/song.flac', 0, 'Song 1', 'Artist', 'Album', 200000, 15000000, 'FLAC', null, null, null)
            """.trimIndent(),
        )
        val cursorTracks = db.query("SELECT title, format FROM playlist_tracks WHERE id = 100")
        assertTrue(cursorTracks.moveToFirst())
        assertEquals("Song 1", cursorTracks.getString(0))
        assertEquals("FLAC", cursorTracks.getString(1))
        cursorTracks.close()

        db.close()
    }
}
