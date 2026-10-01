package com.webdav.player.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverArtStorageTest {

    private lateinit var context: Context
    private lateinit var cacheCoversDir: File
    private lateinit var legacyFilesCoversDir: File
    private lateinit var storage: CoverArtStorageImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheCoversDir = File(context.cacheDir, "covers")
        legacyFilesCoversDir = File(context.filesDir, "covers")

        cacheCoversDir.deleteRecursively()
        legacyFilesCoversDir.deleteRecursively()

        storage = CoverArtStorageImpl(context)
    }

    @After
    fun tearDown() {
        cacheCoversDir.deleteRecursively()
        legacyFilesCoversDir.deleteRecursively()
    }

    @Test
    fun saveThumbnail_storesInCacheDir_andCanBeRetrieved() = runTest {
        val dummyBytes = ByteArray(100) { 0x42 }
        val path = storage.saveThumbnail(
            serverId = 1L,
            remotePath = "/music/song1.mp3",
            artworkBytes = dummyBytes
        )

        assertNotNull(path)
        assertTrue(path!!.startsWith(cacheCoversDir.absolutePath))

        val retrievedFile = storage.getThumbnailFile(1L, "/music/song1.mp3")
        assertNotNull(retrievedFile)
        assertTrue(retrievedFile!!.exists())
        assertTrue(retrievedFile.length() > 0)
        assertEquals(File(path).length(), retrievedFile.length())
    }

    @Test
    fun saveThumbnail_emptyArtwork_returnsNull() = runTest {
        val path = storage.saveThumbnail(
            serverId = 1L,
            remotePath = "/music/empty.mp3",
            artworkBytes = ByteArray(0)
        )
        assertNull(path)
        assertNull(storage.getThumbnailFile(1L, "/music/empty.mp3"))
    }

    @Test
    fun saveThumbnail_truncatedJpegArtwork_returnsNull() = runTest {
        // Truncated JPEG (>32 bytes, starts with FF D8, but missing FF D9 EOI)
        val truncatedJpeg = ByteArray(500).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xD8.toByte()
            this[2] = 0xFF.toByte()
            this[3] = 0xE0.toByte()
        }
        val path = storage.saveThumbnail(
            serverId = 1L,
            remotePath = "/music/truncated.mp3",
            artworkBytes = truncatedJpeg
        )
        assertNull("Truncated JPEG image must not be saved to disk as a thumbnail", path)
        assertNull(storage.getThumbnailFile(1L, "/music/truncated.mp3"))
    }

    @Test
    fun saveThumbnail_completeJpegArtwork_storesInCacheDir() = runTest {
        val completeJpeg = ByteArray(500).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xD8.toByte()
            this[2] = 0xFF.toByte()
            this[3] = 0xE0.toByte()
            this[this.size - 2] = 0xFF.toByte()
            this[this.size - 1] = 0xD9.toByte()
        }
        val path = storage.saveThumbnail(
            serverId = 1L,
            remotePath = "/music/complete.mp3",
            artworkBytes = completeJpeg
        )
        assertNotNull(path)
        val file = storage.getThumbnailFile(1L, "/music/complete.mp3")
        assertNotNull(file)
        assertTrue(file!!.exists())
    }

    @Test
    fun getThumbnailFile_nonExistent_returnsNull() {
        val file = storage.getThumbnailFile(999L, "/non/existent.mp3")
        assertNull(file)
    }

    @Test
    fun deleteThumbnail_removesExistingFile() = runTest {
        val dummyBytes = ByteArray(50) { 1 }
        storage.saveThumbnail(1L, "/music/to_delete.mp3", dummyBytes)
        val file = storage.getThumbnailFile(1L, "/music/to_delete.mp3")
        assertNotNull(file)
        assertTrue(file!!.exists())

        storage.deleteThumbnail(1L, "/music/to_delete.mp3")
        assertNull(storage.getThumbnailFile(1L, "/music/to_delete.mp3"))
        assertFalse(file.exists())
    }

    @Test
    fun deleteServerCovers_removesOnlyTargetServerFiles() = runTest {
        val bytes = ByteArray(64) { 2 }
        storage.saveThumbnail(1L, "/song1.mp3", bytes)
        storage.saveThumbnail(1L, "/song2.mp3", bytes)
        storage.saveThumbnail(2L, "/songA.mp3", bytes)
        storage.saveThumbnail(2L, "/songB.mp3", bytes)

        assertNotNull(storage.getThumbnailFile(1L, "/song1.mp3"))
        assertNotNull(storage.getThumbnailFile(1L, "/song2.mp3"))
        assertNotNull(storage.getThumbnailFile(2L, "/songA.mp3"))
        assertNotNull(storage.getThumbnailFile(2L, "/songB.mp3"))

        storage.deleteServerCovers(1L)

        assertNull(storage.getThumbnailFile(1L, "/song1.mp3"))
        assertNull(storage.getThumbnailFile(1L, "/song2.mp3"))
        assertNotNull(storage.getThumbnailFile(2L, "/songA.mp3"))
        assertNotNull(storage.getThumbnailFile(2L, "/songB.mp3"))
    }

    @Test
    fun legacyMigration_movesFilesFromFilesDirToCacheDir() = runTest {
        // Pre-populate legacy directory
        legacyFilesCoversDir.mkdirs()
        val legacyFile = File(legacyFilesCoversDir, "cover_10_legacy.jpg")
        legacyFile.writeBytes(ByteArray(256) { 7 })
        assertTrue(legacyFile.exists())

        // Initialize storage; lazy getter triggers migration
        val newStorage = CoverArtStorageImpl(context)
        val usage = newStorage.getDiskUsageBytes()

        // Verify the file was moved to cache directory
        val migratedFile = File(cacheCoversDir, "cover_10_legacy.jpg")
        assertTrue(migratedFile.exists())
        assertEquals(256L, usage)
        assertFalse(legacyFile.exists())
    }

    @Test
    fun diskQuotaManagement_prunesOldestFilesWhenQuotaExceeded() = runTest {
        val dummyBytes = ByteArray(100) { 0x55 }

        // First find out single file size produced by storage
        val samplePath = storage.saveThumbnail(99L, "/sample.mp3", dummyBytes)!!
        val singleFileSize = File(samplePath).length()
        storage.deleteThumbnail(99L, "/sample.mp3")

        // Set quota to hold at most 2 files
        val quotaLimit = singleFileSize * 2 + 50
        val quotaStorage = CoverArtStorageImpl(
            context = context,
            maxCacheSizeBytes = quotaLimit
        )

        // Save file 1
        quotaStorage.saveThumbnail(1L, "/song1.mp3", dummyBytes)
        val file1 = quotaStorage.getThumbnailFile(1L, "/song1.mp3")!!
        file1.setLastModified(1000L)

        // Save file 2
        quotaStorage.saveThumbnail(1L, "/song2.mp3", dummyBytes)
        val file2 = quotaStorage.getThumbnailFile(1L, "/song2.mp3")!!
        file2.setLastModified(2000L)

        assertEquals(singleFileSize * 2, quotaStorage.getDiskUsageBytes())
        assertTrue(file1.exists())
        assertTrue(file2.exists())

        // Save file 3 -> total would be 3 * singleFileSize > quotaLimit
        quotaStorage.saveThumbnail(1L, "/song3.mp3", dummyBytes)
        val file3 = quotaStorage.getThumbnailFile(1L, "/song3.mp3")!!

        // Oldest file (file1) should be pruned
        assertFalse("Oldest file1 should have been pruned", file1.exists())
        assertTrue("File2 should still exist", file2.exists())
        assertTrue("File3 should still exist", file3.exists())
        assertTrue(quotaStorage.getDiskUsageBytes() <= quotaLimit)
        assertEquals(singleFileSize * 2, quotaStorage.getDiskUsageBytes())
    }
}
