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
}

class CoverArtStorageImpl(
    private val context: Context,
    private val maxDimension: Int = 512
) : CoverArtStorage {

    private val coversDir: File by lazy {
        val dir = File(context.filesDir, "covers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
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

            targetFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override fun getThumbnailFile(serverId: Long, remotePath: String): File? {
        val fileName = buildFileName(serverId, remotePath)
        val file = File(coversDir, fileName)
        return if (file.exists()) file else null
    }

    override fun deleteThumbnail(serverId: Long, remotePath: String) {
        val file = getThumbnailFile(serverId, remotePath)
        file?.delete()
    }

    private fun buildFileName(serverId: Long, remotePath: String): String {
        val key = "$serverId:$remotePath"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(key.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "cover_${serverId}_$hex.jpg"
    }
}
