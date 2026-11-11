package com.nh.hoopscoach.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * High-performance LRU Cache for player sprite layers, matching the Logos.kt architecture.
 * Caches decoded 512x512 ImageBitmaps to eliminate disk I/O and frame drops during scrolling and editing.
 */
object CharacterSprites {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) { // 32MB cache
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val missing = HashSet<String>()

    fun get(ctx: Context, assetPath: String): ImageBitmap? {
        cache.get(assetPath)?.let { return it }
        if (assetPath in missing) return null

        val bmp = try {
            ctx.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }

        if (bmp == null) {
            missing += assetPath
            return null
        }
        return bmp.asImageBitmap().also { cache.put(assetPath, it) }
    }

    fun clear() {
        cache.evictAll()
        missing.clear()
    }
}
