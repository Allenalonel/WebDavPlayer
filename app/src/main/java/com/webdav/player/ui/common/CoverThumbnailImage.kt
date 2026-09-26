package com.webdav.player.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Allocate up to 1/8th of available app memory, capped at 24MB
    private val cacheSize = (maxMemory / 8).coerceIn(4096, 24 * 1024)

    private val cache = object : LruCache<String, ImageBitmap>(cacheSize) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 2) / 1024 // RGB_565 is 2 bytes per pixel
        }
    }

    fun get(path: String): ImageBitmap? {
        return synchronized(cache) {
            cache.get(path)
        }
    }

    fun put(path: String, bitmap: ImageBitmap) {
        synchronized(cache) {
            cache.put(path, bitmap)
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.evictAll()
        }
    }
}

@Composable
fun CoverThumbnailImage(
    thumbnailPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    if (thumbnailPath.isNullOrBlank()) {
        fallback()
        return
    }

    // Fast-path: Check memory cache directly
    val initialCached = remember(thumbnailPath) {
        ThumbnailMemoryCache.get(thumbnailPath)
    }

    var imageBitmap by remember(thumbnailPath) {
        mutableStateOf(initialCached)
    }

    // Off-main-thread loading if cache missed
    if (imageBitmap == null) {
        LaunchedEffect(thumbnailPath) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val file = File(thumbnailPath)
                    if (file.exists() && file.length() > 0) {
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                    } else {
                        null
                    }
                } catch (e: Throwable) {
                    null
                }
            }
            if (loaded != null) {
                ThumbnailMemoryCache.put(thumbnailPath, loaded)
                imageBitmap = loaded
            }
        }
    }

    val currentBitmap = imageBitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        fallback()
    }
}
