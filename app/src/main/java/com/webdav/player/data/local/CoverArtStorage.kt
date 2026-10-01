package com.webdav.player.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.webdav.player.data.metadata.ImageHeaderValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

interface CoverArtStorage {
    suspend fun saveThumbnail(
        serverId: Long,
        remotePath: String,
        artworkBytes: ByteArray
    ): String?

    fun getThumbnailFile(serverId: Long, remotePath: String): File?
    fun deleteThumbnail(serverId: Long, remotePath: String)
    suspend fun deleteServerCovers(serverId: Long)

    /**
     * Validates whether the given file path points to a physically existing,
     * non-empty thumbnail file on disk.
     */
    fun isValidThumbnailFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val file = File(filePath)
            file.exists() && file.isFile && file.length() > 0
        } catch (_: Exception) {
            false
        }
    }
}

class CoverArtStorageImpl(
    private val context: Context,
    private val maxDimension: Int = DEFAULT_MAX_DIMENSION,
    private val maxCacheSizeBytes: Long = DEFAULT_MAX_CACHE_SIZE_BYTES,
    private val customDir: File? = null
) : CoverArtStorage {

    companion object {
        const val DEFAULT_MAX_DIMENSION = 512
        const val DEFAULT_MAX_CACHE_SIZE_BYTES = 50L * 1024 * 1024L // 50 MB
    }

    private val legacyMigrationDone = java.util.concurrent.atomic.AtomicBoolean(false)

    val coversDir: File
        get() = getCoversDir(createIfMissing = false)

    private fun getBaseCoversDirectory(): File {
        return customDir ?: context.cacheDir?.let { File(it, "covers") } ?: File(context.filesDir, "covers")
    }

    private fun getCoversDir(createIfMissing: Boolean = false): File {
        val baseDir = getBaseCoversDirectory()
        if (createIfMissing && !baseDir.exists()) {
            baseDir.mkdirs()
        }
        checkLegacyMigration(baseDir)
        return baseDir
    }

    private fun checkLegacyMigration(targetDir: File) {
        if (legacyMigrationDone.get()) return
        try {
            val filesDir = context.filesDir ?: return
            val legacyDir = File(filesDir, "covers")
            if (legacyDir.exists() && legacyDir.isDirectory && legacyDir.canonicalPath != targetDir.canonicalPath) {
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                migrateLegacyCoversDir(targetDir, legacyDir)
            }
        } catch (_: Exception) {
            // Ignore migration failure to prevent startup crashes
        } finally {
            legacyMigrationDone.set(true)
        }
    }

    private fun migrateLegacyCoversDir(targetDir: File, legacyDir: File) {
        try {
            val files = legacyDir.listFiles() ?: return
            for (file in files) {
                if (file.isFile) {
                    val targetFile = File(targetDir, file.name)
                    if (!targetFile.exists()) {
                        if (!file.renameTo(targetFile)) {
                            file.copyTo(targetFile, overwrite = true)
                            file.delete()
                        }
                    } else {
                        file.delete()
                    }
                }
            }
            legacyDir.delete()
        } catch (_: Exception) {
            // Ignore migration failure to prevent startup crashes
        }
    }

    override suspend fun saveThumbnail(
        serverId: Long,
        remotePath: String,
        artworkBytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        if (artworkBytes.isEmpty()) return@withContext null
        if (ImageHeaderValidator.isRecognizedImage(artworkBytes) && !ImageHeaderValidator.isCompleteImage(artworkBytes)) {
            return@withContext null
        }
        try {
            val coversDir = getCoversDir(createIfMissing = true)
            val fileName = buildFileName(serverId, remotePath)
            val targetFile = File(coversDir, fileName)

            // Ensure parent directory exists before any file write
            val parentDir = targetFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            var bitmapDecoded = false
            try {
                val originalBitmap = BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)
                if (originalBitmap != null) {
                    val width = originalBitmap.width
                    val height = originalBitmap.height

                    val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                        val targetW = (width * ratio).toInt().coerceAtLeast(1)
                        val targetH = (height * ratio).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                    } else {
                        originalBitmap
                    }

                    FileOutputStream(targetFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }

                    if (scaledBitmap !== originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()
                    bitmapDecoded = true
                }
            } catch (e: Throwable) {
                bitmapDecoded = false
            }

            // Fallback if Bitmap decoding fails (e.g. JVM unit tests or raw image format)
            if (!bitmapDecoded) {
                FileOutputStream(targetFile).use { out ->
                    out.write(artworkBytes)
                }
            }

            pruneDiskQuota(maxCacheSizeBytes, justSavedFile = targetFile)

            targetFile.absolutePath
        } catch (e: Exception) {
            try {
                val fileName = buildFileName(serverId, remotePath)
                val baseDir = getBaseCoversDirectory()
                val partialFile = File(baseDir, fileName)
                if (partialFile.exists()) {
                    partialFile.delete()
                }
            } catch (_: Exception) {
            }
            null
        }
    }

    override fun getThumbnailFile(serverId: Long, remotePath: String): File? {
        return try {
            val dir = getCoversDir(createIfMissing = false)
            if (!dir.exists() || !dir.isDirectory) return null
            val fileName = buildFileName(serverId, remotePath)
            val file = File(dir, fileName)
            if (file.exists() && file.isFile && file.length() > 0) {
                try {
                    file.setLastModified(System.currentTimeMillis())
                } catch (_: Exception) {
                    // Ignore failure to update timestamp
                }
                file
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun deleteThumbnail(serverId: Long, remotePath: String) {
        try {
            val dir = getCoversDir(createIfMissing = false)
            if (!dir.exists()) return
            val fileName = buildFileName(serverId, remotePath)
            val file = File(dir, fileName)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            // Ignore deletion errors
        }
    }

    override suspend fun deleteServerCovers(serverId: Long) {
        withContext(Dispatchers.IO) {
            deleteServerCoversSync(serverId)
        }
    }

    fun deleteServerCoversSync(serverId: Long): Int {
        return try {
            val dir = getCoversDir(createIfMissing = false)
            if (!dir.exists() || !dir.isDirectory) return 0
            val prefix = "cover_${serverId}_"
            val files = dir.listFiles { _, name -> name.startsWith(prefix) } ?: return 0
            var count = 0
            for (file in files) {
                if (file.delete()) {
                    count++
                }
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    fun pruneDiskQuota(quotaBytes: Long = maxCacheSizeBytes, justSavedFile: File? = null) {
        try {
            val dir = getCoversDir(createIfMissing = false)
            if (!dir.exists() || !dir.isDirectory) return
            val files = dir.listFiles() ?: return
            var currentSize = files.sumOf { it.length() }
            if (currentSize <= quotaBytes) return

            // Sort candidates: prune files other than the just-saved one first, oldest lastModified first
            val sortedFiles = files.sortedWith(
                compareBy<File> { if (justSavedFile != null && it.absolutePath == justSavedFile.absolutePath) 1 else 0 }
                    .thenBy { it.lastModified() }
                    .thenBy { it.name }
            )

            for (file in sortedFiles) {
                if (currentSize <= quotaBytes) break
                val length = file.length()
                if (file.delete()) {
                    currentSize -= length
                }
            }
        } catch (_: Exception) {
            // Ignore quota pruning errors
        }
    }

    fun getDiskUsageBytes(): Long {
        return try {
            val dir = getCoversDir(createIfMissing = false)
            if (!dir.exists() || !dir.isDirectory) return 0L
            val files = dir.listFiles() ?: return 0L
            files.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    override fun isValidThumbnailFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val file = File(filePath)
            file.exists() && file.isFile && file.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun buildFileName(serverId: Long, remotePath: String): String {
        val key = "$serverId:$remotePath"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(key.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "cover_${serverId}_$hex.jpg"
    }
}
