package com.webdav.player.ui.common

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.WebDavApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThumbnailMemoryCacheTest {
    @Before
    fun setUp() {
        ThumbnailMemoryCache.clear()
    }

    @After
    fun tearDown() {
        ThumbnailMemoryCache.clear()
    }

    @Test
    fun get_unregisteredPath_returnsNull() {
        assertNull(ThumbnailMemoryCache.get("/non/existent/path.jpg"))
    }

    @Test
    fun putAndGet_storesAndRetrievesImageBitmapSynchronously() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val imageBitmap = bitmap.asImageBitmap()
        val path = "/local/cache/covers/1_cover.jpg"

        ThumbnailMemoryCache.put(path, imageBitmap)

        val retrieved = ThumbnailMemoryCache.get(path)
        assertNotNull(retrieved)
        assertEquals(16, retrieved!!.width)
        assertEquals(16, retrieved.height)
    }

    @Test
    fun clear_removesAllEntriesFromMemory() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val imageBitmap = bitmap.asImageBitmap()
        val path1 = "/local/cache/covers/track1.jpg"
        val path2 = "/local/cache/covers/track2.jpg"

        ThumbnailMemoryCache.put(path1, imageBitmap)
        ThumbnailMemoryCache.put(path2, imageBitmap)

        assertNotNull(ThumbnailMemoryCache.get(path1))
        assertNotNull(ThumbnailMemoryCache.get(path2))

        ThumbnailMemoryCache.clear()

        assertNull(ThumbnailMemoryCache.get(path1))
        assertNull(ThumbnailMemoryCache.get(path2))
    }

    @Test
    fun overwrite_updatesCachedBitmapForSamePath() {
        val bitmap1 = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val path = "/local/cache/covers/track_updated.jpg"

        ThumbnailMemoryCache.put(path, bitmap1.asImageBitmap())
        assertEquals(8, ThumbnailMemoryCache.get(path)!!.width)

        ThumbnailMemoryCache.put(path, bitmap2.asImageBitmap())
        assertEquals(32, ThumbnailMemoryCache.get(path)!!.width)
    }

    @Test
    fun application_onTrimMemory_clearsCacheOnUiHidden() {
        val app = ApplicationProvider.getApplicationContext<WebDavApplication>()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val path = "/local/cache/covers/track_trim.jpg"
        ThumbnailMemoryCache.put(path, bitmap.asImageBitmap())
        assertNotNull(ThumbnailMemoryCache.get(path))

        // Level below TRIM_MEMORY_UI_HIDDEN should not clear
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertNotNull(ThumbnailMemoryCache.get(path))

        // Level at or above TRIM_MEMORY_UI_HIDDEN clears memory
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertNull(ThumbnailMemoryCache.get(path))
    }
}
