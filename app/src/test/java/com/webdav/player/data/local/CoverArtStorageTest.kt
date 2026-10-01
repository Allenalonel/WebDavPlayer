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

    @Test
    fun saveThumbnail_recreatesDirectory_whenCoversDirDeleted() = runTest {
        val dummyBytes = ByteArray(100) { 0x42 }

        // Initial save works and creates directory
        val initialPath = storage.saveThumbnail(1L, "/music/song1.mp3", dummyBytes)
        assertNotNull(initialPath)
        assertTrue(cacheCoversDir.exists())

        // Simulate OS storage cleanup or user clearing app cache
        assertTrue(cacheCoversDir.deleteRecursively())
        assertFalse(cacheCoversDir.exists())

        // Subsequent save must transparently recreate parent directory and write file
        val recoveredPath = storage.saveThumbnail(1L, "/music/song2.mp3", dummyBytes)
        assertNotNull("Saving thumbnail must succeed even after covers dir was deleted", recoveredPath)
        assertTrue("Covers directory must be automatically recreated", cacheCoversDir.exists())

        val recoveredFile = File(recoveredPath!!)
        assertTrue("Saved thumbnail file must exist", recoveredFile.exists())
        assertTrue("Saved thumbnail file must not be empty", recoveredFile.length() > 0)

        val retrievedFile = storage.getThumbnailFile(1L, "/music/song2.mp3")
        assertNotNull("Retrieved file must not be null", retrievedFile)
        assertTrue(retrievedFile!!.exists())
    }

    @Test
    fun saveThumbnail_recreatesDirectory_whenEntireCacheDirDeleted() = runTest {
        val dummyBytes = ByteArray(100) { 0x77 }

        // Initial save
        storage.saveThumbnail(1L, "/music/song1.mp3", dummyBytes)
        assertTrue(cacheCoversDir.exists())

        // Wipe all files and subdirectories in cache directory
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        assertFalse(cacheCoversDir.exists())

        // Save new thumbnail
        val newPath = storage.saveThumbnail(1L, "/music/song_after_wipe.mp3", dummyBytes)
        assertNotNull("Saving must succeed after entire cacheDir contents are wiped", newPath)
        assertTrue("Cache covers dir must be recreated", cacheCoversDir.exists())

        val file = File(newPath!!)
        assertTrue(file.exists())
        assertEquals(file.absolutePath, storage.getThumbnailFile(1L, "/music/song_after_wipe.mp3")?.absolutePath)
    }

    @Test
    fun getThumbnailFile_returnsNullSafely_whenDirectoryMissing() {
        cacheCoversDir.deleteRecursively()
        assertFalse(cacheCoversDir.exists())

        val file = storage.getThumbnailFile(1L, "/music/non_existent.mp3")
        assertNull(file)
        assertFalse("Querying thumbnail existence must not needlessly create the directory", cacheCoversDir.exists())
    }

    @Test
    fun isValidThumbnailFile_validatesPhysicalFileOnDisk() = runTest {
        val dummyBytes = ByteArray(100) { 0x33 }

        // null or blank paths
        assertFalse(storage.isValidThumbnailFile(null))
        assertFalse(storage.isValidThumbnailFile(""))
        assertFalse(storage.isValidThumbnailFile("   "))

        // non-existent file
        assertFalse(storage.isValidThumbnailFile(File(cacheCoversDir, "ghost.jpg").absolutePath))

        // directory instead of file
        cacheCoversDir.mkdirs()
        assertFalse(storage.isValidThumbnailFile(cacheCoversDir.absolutePath))

        // empty (0-byte) file
        val emptyFile = File(cacheCoversDir, "empty.jpg")
        emptyFile.createNewFile()
        assertTrue(emptyFile.exists())
        assertEquals(0L, emptyFile.length())
        assertFalse("0-byte file must not be considered a valid thumbnail", storage.isValidThumbnailFile(emptyFile.absolutePath))

        // valid thumbnail file
        val validPath = storage.saveThumbnail(1L, "/valid.mp3", dummyBytes)
        assertNotNull(validPath)
        assertTrue("Existing non-empty thumbnail must be valid", storage.isValidThumbnailFile(validPath))

        // after deleting file from disk
        File(validPath!!).delete()
        assertFalse("Deleted thumbnail must no longer be valid", storage.isValidThumbnailFile(validPath))
    }

    @Test
    fun missingDirectory_operationsSafelyHandledWithoutExceptions() {
        cacheCoversDir.deleteRecursively()
        assertFalse(cacheCoversDir.exists())

        // getDiskUsageBytes returns 0
        assertEquals(0L, storage.getDiskUsageBytes())

        // pruneDiskQuota executes without error
        storage.pruneDiskQuota()

        // deleteThumbnail executes without error
        storage.deleteThumbnail(1L, "/music/song.mp3")

        // deleteServerCoversSync returns 0 without error
        assertEquals(0, storage.deleteServerCoversSync(1L))

        // Directory must not have been created by passive read/delete calls
        assertFalse(cacheCoversDir.exists())
    }

    @Test
    fun customDir_recreatesDirectory_whenDeleted() = runTest {
        val customDir = File(context.cacheDir, "custom_covers_test")
        customDir.mkdirs()
        val customStorage = CoverArtStorageImpl(context, customDir = customDir)

        val dummyBytes = ByteArray(100) { 0x11 }
        val path1 = customStorage.saveThumbnail(1L, "/song1.mp3", dummyBytes)
        assertNotNull(path1)
        assertTrue(path1!!.startsWith(customDir.absolutePath))

        // Delete customDir
        customDir.deleteRecursively()
        assertFalse(customDir.exists())

        // Save again
        val path2 = customStorage.saveThumbnail(1L, "/song2.mp3", dummyBytes)
        assertNotNull("Saving to customDir must succeed after deletion", path2)
        assertTrue(customDir.exists())
        assertTrue(File(path2!!).exists())
    }
}
