package com.webdav.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WebDavServerEntity::class,
        TrackMetadataEntity::class,
        DirectoryCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun webDavServerDao(): WebDavServerDao
    abstract fun trackMetadataDao(): TrackMetadataDao
    abstract fun directoryCacheDao(): DirectoryCacheDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `directory_cache` (
                        `serverId` INTEGER NOT NULL,
                        `path` TEXT NOT NULL,
                        `dataJson` TEXT NOT NULL,
                        `lastUpdatedMs` INTEGER NOT NULL,
                        PRIMARY KEY(`serverId`, `path`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_directory_cache_serverId` ON `directory_cache` (`serverId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_directory_cache_serverId_path` ON `directory_cache` (`serverId`, `path`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `serverId` INTEGER NOT NULL,
                        `remotePath` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT,
                        `album` TEXT,
                        `durationMs` INTEGER NOT NULL,
                        `size` INTEGER NOT NULL,
                        `format` TEXT NOT NULL,
                        `coverThumbnailPath` TEXT,
                        `cuePath` TEXT,
                        `virtualTrackId` TEXT,
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` ON `playlist_tracks` (`playlistId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_sortOrder` ON `playlist_tracks` (`playlistId`, `sortOrder`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webdav_player.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
