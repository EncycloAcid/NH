package com.nh.hoopscoach.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.nh.hoopscoach.assetBytes
import com.nh.hoopscoach.decodeImageBitmap

/**
 * High-performance cache for player sprite layers as ImageBitmaps.
 */
object CharacterSprites {
    private val cache = HashMap<String, ImageBitmap>()
    private val missing = HashSet<String>()
    private var cacheBytes = 0
    private const val MAX_BYTES = 32 * 1024 * 1024

    fun get(assetPath: String): ImageBitmap? {
        cache[assetPath]?.let { return it }
        if (assetPath in missing) return null
        val bytes = assetBytes(assetPath)
        if (bytes == null) {
            missing += assetPath
            return null
        }
        val bmp = runCatching { decodeImageBitmap(bytes) }.getOrNull()
        if (bmp == null) {
            missing += assetPath
            return null
        }
        val size = bmp.width * bmp.height * 4
        if (cacheBytes + size > MAX_BYTES && cache.isNotEmpty()) {
            val victim = cache.keys.first()
            val old = cache.remove(victim)
            if (old != null) cacheBytes -= old.width * old.height * 4
        }
        cache[assetPath] = bmp
        cacheBytes += size
        return bmp
    }

    fun clear() {
        cache.clear()
        missing.clear()
        cacheBytes = 0
    }
}
