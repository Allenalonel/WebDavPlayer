package com.webdav.player.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DirectoryCacheDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: DirectoryCacheDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.directoryCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryDirectoryCache_byServerIdAndPath() = runTest {
        val entity = DirectoryCacheEntity(
            serverId = 1L,
            path = "/Music/Rock/",
            dataJson = "{\"path\":\"/Music/Rock/\",\"name\":\"Rock\",\"subDirectories\":[],\"files\":[]}",
            lastUpdatedMs = 1700000000000L
        )

        dao.insertOrUpdate(entity)

        val retrieved = dao.getCache(1L, "/Music/Rock/")
        assertNotNull(retrieved)
        assertEquals("/Music/Rock/", retrieved?.path)
        assertEquals(1700000000000L, retrieved?.lastUpdatedMs)

        // Non-existent path returns null
        val missing = dao.getCache(1L, "/Music/Pop/")
        assertNull(missing)
    }

    @Test
    fun deleteCache_byServerAndPath() = runTest {
        val entity = DirectoryCacheEntity(
            serverId = 1L,
            path = "/Music/Jazz/",
            dataJson = "{}",
            lastUpdatedMs = 1000L
        )
        dao.insertOrUpdate(entity)
        assertNotNull(dao.getCache(1L, "/Music/Jazz/"))

        dao.deleteCache(1L, "/Music/Jazz/")
        assertNull(dao.getCache(1L, "/Music/Jazz/"))
    }

    @Test
    fun deleteCacheByServerId_cleansOnlyTargetServer() = runTest {
        dao.insertOrUpdate(DirectoryCacheEntity(1L, "/dir1/", "{}", 100L))
        dao.insertOrUpdate(DirectoryCacheEntity(1L, "/dir2/", "{}", 200L))
        dao.insertOrUpdate(DirectoryCacheEntity(2L, "/dir1/", "{}", 300L))

        dao.deleteCacheByServerId(1L)

        assertNull(dao.getCache(1L, "/dir1/"))
        assertNull(dao.getCache(1L, "/dir2/"))
        assertNotNull(dao.getCache(2L, "/dir1/"))
    }

    @Test
    fun clearAll_emptiesEntireCacheTable() = runTest {
        dao.insertOrUpdate(DirectoryCacheEntity(1L, "/a/", "{}", 100L))
        dao.insertOrUpdate(DirectoryCacheEntity(2L, "/b/", "{}", 200L))

        dao.clearAll()

        assertNull(dao.getCache(1L, "/a/"))
        assertNull(dao.getCache(2L, "/b/"))
    }
}
