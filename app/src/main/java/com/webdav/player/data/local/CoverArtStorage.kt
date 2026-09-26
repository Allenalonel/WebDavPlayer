package com.webdav.player.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    private val coversDir: File by lazy {
        val baseDir = customDir ?: context.cacheDir?.let { File(it, "covers") } ?: File(context.filesDir, "covers")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        migrateLegacyCoversDir(baseDir)
        baseDir
    }

    private fun migrateLegacyCoversDir(targetDir: File) {
        try {
            val filesDir = context.filesDir ?: return
            val legacyDir = File(filesDir, "covers")
            if (legacyDir.exists() && legacyDir.isDirectory && legacyDir.canonicalPath != targetDir.canonicalPath) {
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
            }
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
        try {
            val fileName = buildFileName(serverId, remotePath)
            val targetFile = File(coversDir, fileName)

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
            null
        }
    }

    override fun getThumbnailFile(serverId: Long, remotePath: String): File? {
        val fileName = buildFileName(serverId, remotePath)
        val file = File(coversDir, fileName)
        return if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            file
        } else {
            null
        }
    }

    override fun deleteThumbnail(serverId: Long, remotePath: String) {
        val fileName = buildFileName(serverId, remotePath)
        val file = File(coversDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    override suspend fun deleteServerCovers(serverId: Long) {
        withContext(Dispatchers.IO) {
            deleteServerCoversSync(serverId)
        }
    }

    fun deleteServerCoversSync(serverId: Long): Int {
        val prefix = "cover_${serverId}_"
        val files = coversDir.listFiles { _, name -> name.startsWith(prefix) } ?: return 0
        var count = 0
        for (file in files) {
            if (file.delete()) {
                count++
            }
        }
        return count
    }

    fun pruneDiskQuota(quotaBytes: Long = maxCacheSizeBytes, justSavedFile: File? = null) {
        val files = coversDir.listFiles() ?: return
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
    }

    fun getDiskUsageBytes(): Long {
        val files = coversDir.listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }

    private fun buildFileName(serverId: Long, remotePath: String): String {
        val key = "$serverId:$remotePath"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(key.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "cover_${serverId}_$hex.jpg"
    }
}
