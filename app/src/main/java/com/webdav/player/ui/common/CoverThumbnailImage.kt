package com.webdav.player.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    crossfadeDurationMs: Int = 250,
    fallback: @Composable () -> Unit
) {
    // Fast-path: Check memory cache directly
    val initialCached = remember(thumbnailPath) {
        if (!thumbnailPath.isNullOrBlank()) {
            ThumbnailMemoryCache.get(thumbnailPath)
        } else {
            null
        }
    }

    val imageBitmapState = remember(thumbnailPath) {
        mutableStateOf(initialCached)
    }
    val imageBitmap = imageBitmapState.value

    // Off-main-thread loading if cache missed
    LaunchedEffect(thumbnailPath) {
        if (thumbnailPath.isNullOrBlank()) {
            imageBitmapState.value = null
            return@LaunchedEffect
        }
        if (imageBitmapState.value == null) {
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
                imageBitmapState.value = loaded
            }
        }
    }

    if (crossfadeDurationMs > 0) {
        Crossfade(
            targetState = imageBitmap,
            animationSpec = tween(durationMillis = crossfadeDurationMs),
            label = "CoverThumbnailCrossfade",
            modifier = modifier
        ) { currentBitmap ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    fallback()
                }
            }
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            val currentBitmap = imageBitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                fallback()
            }
        }
    }
}
