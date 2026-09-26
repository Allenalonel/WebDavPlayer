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
        DirectoryCacheEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun webDavServerDao(): WebDavServerDao
    abstract fun trackMetadataDao(): TrackMetadataDao
    abstract fun directoryCacheDao(): DirectoryCacheDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webdav_player.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
